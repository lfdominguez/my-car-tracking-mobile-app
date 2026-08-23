package com.domivega.gps_car.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.domivega.gps_car.ForegroundTrackingService
import com.domivega.gps_car.ObdEnableGate
import com.domivega.gps_car.WaitingFgsGate
import com.domivega.gps_car.fuel.FuelCalcConfig
import com.domivega.gps_car.fuel.FuelCalcSensors
import com.domivega.gps_car.fuel.FuelClass
import com.domivega.gps_car.fuel.FuelConsumptionCalculator
import com.domivega.gps_car.settings.AppSettings
import com.domivega.gps_car.startForegroundServiceCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class BleDeviceInfo(
    val name: String?,
    val address: String,
)

/**
 * ELM327 OBD session over BLE GATT or Classic Bluetooth SPP.
 *
 * Call [initialize] once from Application. Observe [pidValues] / [ecuConnected] / [vehicleOn].
 * [ecuConnected] is true only after a live decode of RPM (`0c`), speed (`0d`), or
 * module voltage (`42`) — not from ELM init / bus contact alone.
 * [vehicleOn] is the trip watchdog ([VehicleOnPolicy]): after RPM>0, rest-battery
 * voltage / RPM=0 no longer keep a trip open. Transport via [setBluetoothTransport] / settings.
 */
object ObdBleManager {
    private const val TAG = "ObdBleMgr"

    private const val COMMAND_TIMEOUT_MS = 4_000L
    private const val ATZ_TIMEOUT_MS = 6_000L
    /** Per-attempt wait for 0100 during init (keep short — cold ECU is handled by retries). */
    private const val ECU_PROBE_TIMEOUT_MS = 2_500L
    private const val ECU_PROBE_ATTEMPTS = 8
    /** Retries for 0120/0140/… after 0100 already proved the ECU is awake. */
    private const val SUPPORT_QUERY_ATTEMPTS = 4
    private const val SCAN_PERIOD_MS = 12_000L
    /** Do not run cluster UDS until engine stack has produced this many successful decodes. */
    private const val VW_ODO_MIN_ENGINE_OK = 8
    /** Continuous OBD speed ~0 before a once-per-stop cluster hop. */
    private const val VW_ODO_STOP_DWELL_MS = 5_000L
    private const val UDS_COMMAND_TIMEOUT_MS = 6_000L
    private const val MODE01_HEALTH_TIMEOUT_MS = 4_000L
    private const val WWH_HEALTH_TIMEOUT_MS = 4_000L
    /** Settle after cluster header restore before Mode 01 health. */
    private const val UDS_RESTORE_SETTLE_MS = 150L
    private const val CHARSET_NAME = "US-ASCII"

    /** MAP/RPM/IAT speed-density air mass when Mode 01 PID 0x10 is missing. */
    const val ESTIMATED_MAF_KEY = "ffmafg"

    /** PIDs map for ForegroundTrackingService. */
    val pidsMap: Map<String, String> = linkedMapOf(
        "0c" to "Engine RPM",
        "0d" to "Vehicle Speed",
        "ff125a" to "Fuel Consumption Rate",
        "04" to "Engine Load",
        "43" to "Absolute Engine Load",
        "06" to "STFT",
        "07" to "LTFT",
        "2f" to "Fuel Level",
        VwClusterDids.KEY_FUEL_PCT to "Fuel Level (cluster)",
        VwClusterDids.KEY_OIL_C to "Oil Temperature",
        VwClusterDids.KEY_DOORS to "Door Status",
        OdometerReading.UDS_KM_KEY to "Odometer (cluster)",
        "49" to "Accelerator Pedal",
        "46" to "Ambient Air Temp",
        "a6" to "Odometer",
        "31" to "Distance since codes cleared",
        "05" to "Engine Coolant Temperature",
        "0b" to "Manifold Pressure",
        "42" to "Control Module Voltage",
        "1f" to "Engine ON time",
        "10" to "Mass Air Flow",
        ESTIMATED_MAF_KEY to "Mass Air Flow (estimated)",
        "44" to "Fuel/Air Ratio",
        "5e" to "Engine Fuel Rate",
        "5b" to "HV Battery SoC",
        "9a" to "HV Remaining Charge",
        "33" to "Barometric Pressure",
        "0f" to "Intake Air Temp",
    )

    /** High-dynamics PIDs polled every round (tracking gates + fuel rate inputs). */
    private val HOT_PIDS = listOf(
        "0c", // RPM
        "0d", // Speed
        "42", // Module voltage (EV-friendly live signal)
        "1f", // Engine run time (crank vs parked-off)
        "10", // MAF (fuel rate primary input)
        "0b", // MAP (fuel rate fallback when MAF absent)
    )

    /** Slow-changing PIDs polled every [SLOW_EVERY]th round. */
    private val SLOW_PIDS = listOf(
        "04", // Engine load
        "43", // Absolute load
        "49", // Accelerator
        "44", // Lambda
        "06", // STFT
        "07", // LTFT
        "2f", // Fuel level
        "5e", // Engine fuel rate L/h (prefer over estimated ff125a when live)
        "5b", // Hybrid/EV battery SoC
        "9a", // Hybrid/EV remaining charge (fallback SoC)
        "a6", // Vehicle odometer (SAE PID A6)
        "31", // Distance since codes cleared (not dash odometer)
        "05", // Coolant
        "46", // Ambient
        "33", // Barometric
        "0f", // IAT (MAP fuel fallback)
    )

    private const val SLOW_EVERY = 5

    /** Mode 01 PIDs reported supported by the ECU (empty = not discovered yet). */
    @Volatile
    private var supportedMode01Pids: Set<Int> = emptySet()

    /** First miss/NO DATA log per PID per session (does not disable or clear values). */
    private val noDataLogged = ConcurrentHashMap.newKeySet<String>()

    /** First successful UDS odometer DID this session (prefer on later probes). */
    @Volatile
    private var sessionWinningDid: Int? = null

    /** Initial + once-per-stop cluster UDS schedule until odometer lock (no moving timer). */
    private val vwOdoSchedule = VwOdoSchedule(
        engineOkMin = VW_ODO_MIN_ENGINE_OK,
        stopDwellMs = VW_ODO_STOP_DWELL_MS,
        stopSpeedMaxKmh = 0.5,
    )

    /** Baseline cluster km + Mode 01 PID 31 delta after first successful UDS odo. */
    private val sessionOdometerTracker = SessionOdometerTracker()

    /** Successful engine decodes this session (Mode 01 or WWH; gates first cluster UDS). */
    @Volatile
    private var engineOkCount: Int = 0

    /** Session uses WWH-OBD 22F4xx instead of classic Mode 01. */
    @Volatile
    private var wwhEngineOnly: Boolean = false

    /** Engine request header hex without ATSH prefix, e.g. 7DF or 7E0. */
    @Volatile
    private var sessionEngineHeader: String = "7DF"

    /** True if the current/last UDS probe issued ATCRA (needs ATAR to clear). */
    @Volatile
    private var udsReceiveFilterWasSet: Boolean = false

    private val loggedVwUdsStart = AtomicBoolean(false)
    private val loggedVwUdsSuccess = AtomicBoolean(false)
    private val loggedVwUdsFail = AtomicBoolean(false)
    /** First success log per cluster extra metric key this session. */
    private val loggedVwClusterExtraOk = ConcurrentHashMap.newKeySet<String>()

    /**
     * Set when header restore after UDS fails health check.
     * Soft: back off further UDS; hard GATT re-init only after sustained Mode 01 timeouts.
     */
    @Volatile
    private var udsHeaderRestoreFailed = false

    /** Wall clock: do not run cluster UDS before this (0 = no extra backoff). */
    @Volatile
    private var vwUdsNotBeforeMs: Long = 0L

    /** Consecutive engine command timeouts (null raw) in the poll loop. */
    @Volatile
    private var consecutiveEngineTimeouts: Int = 0

    /** Consecutive live-PID (0c/0d/42) misses — gates ecuConnected offline. */
    @Volatile
    private var consecutiveLiveMisses: Int = 0

    private val parser = Elm327Parser()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var settings: AppSettings

    @Volatile
    private var isInitialized = false

    private val _pidValues = MutableStateFlow<Map<String, Double>>(emptyMap())
    val pidValues: StateFlow<Map<String, Double>> = _pidValues.asStateFlow()
    private val pidSeenAtMs = ConcurrentHashMap<String, Long>()

    private val _ecuConnected = MutableStateFlow(false)
    val ecuConnected: StateFlow<Boolean> = _ecuConnected.asStateFlow()

    private val vehicleOnLock = Any()
    private var vehicleOnState = VehicleOnPolicy.State()
    private val _vehicleOn = MutableStateFlow(false)
    val vehicleOn: StateFlow<Boolean> = _vehicleOn.asStateFlow()

    /** Process-local: OBD PID 0d decoded at least once (gates GPS speed proofs). */
    @Volatile
    private var obdSpeedDecoded: Boolean = false

    /** Wall clock until idle reconnect may wake a parked dongle again. */
    @Volatile
    private var parkedSleepUntilMs: Long? = null
    private val parkedReleaseRequested = AtomicBoolean(false)

    private val _connectionStatus = MutableStateFlow("Not initialized")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    /** In-app OBD event log (errors + init/lifecycle). */
    val debugLog: StateFlow<List<ObdLogEntry>> = ObdDebugLog.entries

    fun clearDebugLog() = ObdDebugLog.clear()

    private val _scannedDevices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val scannedDevices: StateFlow<List<BleDeviceInfo>> = _scannedDevices.asStateFlow()

    private val scanResults = ConcurrentHashMap<String, BleDeviceInfo>()

    @Volatile
    private var selectedAddress: String? = null

    @Volatile
    private var selectedName: String? = null

    @Volatile
    private var protocol: ObdProtocol = ObdProtocol.DEFAULT

    private var bluetoothAdapter: BluetoothAdapter? = null

    @Volatile
    private var bluetoothTransport: BluetoothTransport = BluetoothTransport.DEFAULT

    private var transport: ElmTransport = BleElmTransport()

    private val responseLock = Any()
    private val responseFramer = ElmResponseFramer()
    private var responseDeferred: CompletableDeferred<String>? = null

    private val connecting = AtomicBoolean(false)
    private val sessionReady = AtomicBoolean(false)
    private val scanning = AtomicBoolean(false)

    private var pollJob: Job? = null
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null

    /** One INFO after first successful poll cycle per connection. */
    private val loggedFirstPollOk = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            settings = AppSettings(appContext)
            val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = manager?.adapter

            selectedAddress = settings.bleDeviceAddress.ifBlank { null }
            selectedName = settings.bleDeviceName.ifBlank { null }
            loadVehicleOnFlag()
            protocol = runCatching { ObdProtocol.valueOf(settings.obdProtocol) }
                .getOrDefault(ObdProtocol.DEFAULT)
            bluetoothTransport = BluetoothTransport.fromName(settings.bluetoothTransport)
            transport = createTransport(bluetoothTransport)

            isInitialized = true
            _connectionStatus.value = when {
                bluetoothAdapter == null -> "Bluetooth not available"
                selectedAddress != null -> "Ready (saved ${selectedName ?: selectedAddress})"
                else -> "Ready (no device selected)"
            }
            logI(
                "Manager initialized; device=${selectedAddress ?: "none"}, " +
                    "protocol=${protocol.name}, transport=${bluetoothTransport.name}"
            )
        }
    }

    fun startScan() {
        ensureInit()
        if (!hasScanPermission()) {
            setStatus("Missing Bluetooth scan permission")
            Log.w(TAG, "startScan: missing permission")
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            setStatus("Bluetooth not available")
            return
        }
        if (!adapter.isEnabled) {
            setStatus("Bluetooth is off")
            return
        }
        if (!scanning.compareAndSet(false, true)) {
            Log.d(TAG, "Scan already running")
            return
        }

        scanResults.clear()
        _scannedDevices.value = emptyList()
        setStatus("Scanning…")

        try {
            (transport as? SppElmTransport)?.attachContext(appContext)
            transport.startScan(
                adapter = adapter,
                onDevice = { info ->
                    scanResults[info.address] = info
                    _scannedDevices.value = scanResults.values.sortedBy { it.name ?: it.address }
                },
                onStatus = { msg ->
                    if (!sessionReady.get() && !transport.isLinked) {
                        setStatus(msg)
                    }
                    logW(msg)
                },
            )
            scanTimeoutJob?.cancel()
            scanTimeoutJob = scope.launch {
                delay(SCAN_PERIOD_MS)
                stopScan()
            }
        } catch (t: Throwable) {
            scanning.set(false)
            setStatus("Scan failed: ${t.message}")
            Log.w(TAG, "startScan failed", t)
        }
    }

    fun stopScan() {
        if (!isInitialized) return
        val wasScanning = scanning.getAndSet(false)
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        try {
            transport.stopScan(bluetoothAdapter)
        } catch (t: Throwable) {
            Log.w(TAG, "stopScan failed", t)
        }
        if (!wasScanning) return
        val count = _scannedDevices.value.size
        if (!transport.isLinked && !sessionReady.get()) {
            setStatus(if (count > 0) "Scan done ($count devices)" else "Scan done (no devices)")
        }
    }

    fun selectDevice(address: String, name: String?) {
        ensureInit()
        val previousAddress = selectedAddress.orEmpty()
        selectedAddress = address
        selectedName = name
        settings.bleDeviceAddress = address
        settings.bleDeviceName = name.orEmpty()
        setStatus("Selected ${name ?: address}")
        Log.i(TAG, "Selected device $address ($name)")
        // Re-arm idle wait (presence or cheap fallback) for the new target.
        runCatching { ObdPresenceController.arm(appContext) }
            .onFailure { Log.w(TAG, "ObdPresenceController.arm after selectDevice failed", it) }
        // Fresh install / first pick never hit GpsCarApp's cold-start WAITING path.
        if (WaitingFgsGate.shouldEnsureWaiting(address)) {
            appContext.startForegroundServiceCompat(ForegroundTrackingService.ACTION_START_WAITING)
        }
        if (!VehicleOnPersist.shouldKeepFlag(previousAddress, address)) {
            clearSawPositiveRpm()
        } else {
            persistVehicleOnFlag(vehicleOnState.sawPositiveRpm, address)
        }
    }

    fun setProtocol(protocol: ObdProtocol) {
        ensureInit()
        this.protocol = protocol
        settings.obdProtocol = protocol.name
        Log.i(TAG, "Protocol set to ${protocol.name}")
        if (sessionReady.get()) {
            scope.launch {
                runCatching {
                    sendCommand(protocol.atspCommand)
                    setStatus("Protocol ${protocol.displayName}")
                }.onFailure {
                    Log.w(TAG, "Failed to apply protocol", it)
                }
            }
        }
    }


    fun setBluetoothTransport(transportKind: BluetoothTransport) {
        ensureInit()
        if (bluetoothTransport == transportKind) {
            settings.bluetoothTransport = transportKind.name
            return
        }
        logI("Bluetooth transport ${bluetoothTransport.name} → ${transportKind.name}")
        stopScan()
        scope.launch {
            stopPollLoop()
            sessionReady.set(false)
            _ecuConnected.value = false
            closeLinkInternal()
            connecting.set(false)
            bluetoothTransport = transportKind
            settings.bluetoothTransport = transportKind.name
            transport = createTransport(transportKind)
            setStatus("Transport: ${transportKind.displayName}")
            runCatching { ObdPresenceController.arm(appContext) }
                .onFailure { Log.w(TAG, "ObdPresenceController.arm after transport change failed", it) }
        }
    }

    fun connect() {
        ensureInit()
        if (!ObdEnableGate.mayConnect(settings.obdEnabled)) {
            logI("connect skipped: OBD disabled")
            setStatus("OBD disabled")
            return
        }
        val address = selectedAddress
        if (address.isNullOrBlank()) {
            setStatus("No device selected")
            return
        }
        if (!hasConnectPermission()) {
            setStatus("Missing Bluetooth connect permission")
            logW("connect: missing Bluetooth permission")
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            setStatus("Bluetooth not available")
            return
        }
        if (!adapter.isEnabled) {
            setStatus("Bluetooth is off")
            return
        }
        if (sessionReady.get() && transport.isLinked) {
            Log.d(TAG, "Already connected")
            return
        }
        parkedSleepUntilMs = null
        parkedReleaseRequested.set(false)
        if (!connecting.compareAndSet(false, true)) {
            Log.d(TAG, "Connect already in progress")
            return
        }

        stopScan()
        scope.launch {
            try {
                logI(
                    "Connect attempt ${selectedName ?: address} ($address) " +
                        "via ${bluetoothTransport.name}",
                )
                setStatus("Connecting to ${selectedName ?: address}…")
                sessionReady.set(false)
                loggedFirstPollOk.set(false)

                closeLinkInternal()

                val device = adapter.getRemoteDevice(address)
                (transport as? SppElmTransport)?.attachContext(appContext)
                val ok = transport.connect(
                    context = appContext,
                    adapter = adapter,
                    device = device,
                    onBytes = { bytes -> onTransportBytes(bytes) },
                    onLinkLost = { reason ->
                        scope.launch { handleLinkLost(reason) }
                    },
                )
                if (!ok) {
                    val hint = if (bluetoothTransport == BluetoothTransport.ClassicSpp) {
                        " (pair in system settings PIN 1234/0000; close other OBD apps)"
                    } else {
                        ""
                    }
                    logW("Connect failed/timeout$hint")
                    setStatus("Connect failed$hint")
                    closeLinkInternal()
                    connecting.set(false)
                    return@launch
                }

                setStatus("Initializing ELM…")
                logI("Initializing ELM…")
                val initOk = runInitSequence()
                if (!initOk) {
                    if (!_connectionStatus.value.startsWith("ELM init failed")) {
                        setStatus("ELM init failed")
                    }
                    logW("Reconnect will retry (init failed)")
                    _ecuConnected.value = false
                    closeLinkInternal()
                    connecting.set(false)
                    return@launch
                }

                val profile = VehicleObdProfile.fromName(settings.vehicleObdProfile)
                if (VwOdoFirstGate.requiresOdometerBeforeMode01(profile)) {
                    val locked = awaitVwClusterOdometerLock()
                    if (!locked) {
                        logW("VW odometer gate aborted before Mode 01 (link lost or cancelled)")
                        _ecuConnected.value = false
                        closeLinkInternal()
                        connecting.set(false)
                        return@launch
                    }
                }

                sessionReady.set(true)
                connecting.set(false)
                setStatus("ECU online")
                logI("ECU online — starting PID poll")
                startPollLoop()
            } catch (t: Throwable) {
                logE("Connect error: ${t.message}")
                Log.w(TAG, "connect failed", t)
                setStatus("Connect error: ${t.message}")
                _ecuConnected.value = false
                sessionReady.set(false)
                closeLinkInternal()
                connecting.set(false)
            }
        }
    }

    fun disconnect() {
        if (!isInitialized) return
        scope.launch {
            stopPollLoop()
            sessionReady.set(false)
            _ecuConnected.value = false
            clearPidCache()
            resetPidDiscoveryState()
            closeLinkInternal()
            connecting.set(false)
            loggedFirstPollOk.set(false)
            synchronized(vehicleOnLock) {
                vehicleOnState = VehicleOnPolicy.onLinkLost(vehicleOnState)
                publishVehicleOn(System.currentTimeMillis())
            }
            setStatus("Disconnected")
            logI("Disconnected")
        }
    }

    /**
     * Car is parked (rest battery + stale idle RPM). Drop BLE so the adapter
     * can sleep, and back off idle reconnect so we do not keep waking it.
     */
    fun releaseAdapterForParkedSleep() {
        if (!isInitialized) return
        if (!parkedReleaseRequested.compareAndSet(false, true)) return
        parkedSleepUntilMs = IdleReconnectPolicy.parkedSleepUntilMs(System.currentTimeMillis())
        logI(
            "Parked engine-off — disconnect adapter for " +
                "${IdleReconnectPolicy.PARKED_BACKOFF_MS / 1000}s sleep",
        )
        disconnect()
    }

    /**
     * Idle reconnect: try connect, then wait [IdleReconnectPolicy.INTERVAL_MS].
     */
    fun startAutoReconnect() {
        ensureInit()
        if (reconnectJob?.isActive == true) return
        logI("Idle reconnect loop started (${IdleReconnectPolicy.INTERVAL_MS / 1000}s)")
        reconnectJob = scope.launch {
            while (isActive) {
                try {
                    val address = selectedAddress
                    val adapter = bluetoothAdapter
                    val now = System.currentTimeMillis()
                    val canTry = !address.isNullOrBlank() &&
                        adapter != null &&
                        adapter.isEnabled &&
                        hasConnectPermission() &&
                        !sessionReady.get() &&
                        !connecting.get() &&
                        !transport.isLinked &&
                        IdleReconnectPolicy.shouldAttemptConnect(parkedSleepUntilMs, now)

                    if (canTry) {
                        logI("Idle reconnect → $address")
                        connect()
                    } else if (!hasConnectPermission() && !address.isNullOrBlank()) {
                        // Avoid spamming; status already set on manual connect.
                    }
                } catch (t: Throwable) {
                    logW("Auto-reconnect loop error: ${t.message}")
                    Log.w(TAG, "Auto-reconnect loop error", t)
                }
                delay(
                    IdleReconnectPolicy.nextDelayMs(
                        kotlin.random.Random.nextDouble(),
                        parkedSleepUntilMs,
                        System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun isSessionReady(): Boolean = sessionReady.get()

    fun isTransportLinked(): Boolean = transport.isLinked

    fun isConnecting(): Boolean = connecting.get()

    fun selectedDeviceAddress(): String? = selectedAddress

    fun currentBluetoothTransport(): BluetoothTransport = bluetoothTransport

    fun stopAutoReconnect() {
        if (reconnectJob != null) {
            logI("Auto-reconnect loop stopped")
        }
        reconnectJob?.cancel()
        reconnectJob = null
    }

    // --- internals ---

    private fun ensureInit() {
        check(isInitialized) { "ObdBleManager not initialized. Call initialize() first." }
    }

    private fun setStatus(msg: String) {
        _connectionStatus.value = msg
    }

    private fun logI(msg: String) = ObdDebugLog.info(msg)

    private fun logW(msg: String) = ObdDebugLog.warn(msg)

    private fun logE(msg: String) = ObdDebugLog.error(msg)

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun createTransport(kind: BluetoothTransport): ElmTransport {
        return when (kind) {
            BluetoothTransport.Ble -> BleElmTransport()
            BluetoothTransport.ClassicSpp -> SppElmTransport().also {
                if (isInitialized) it.attachContext(appContext)
            }
        }
    }

    private fun onTransportBytes(value: ByteArray) {
        val chunk = try {
            String(value, Charset.forName(CHARSET_NAME))
        } catch (_: Throwable) {
            String(value)
        }
        synchronized(responseLock) {
            val complete = responseFramer.append(chunk)
            if (complete != null) {
                val deferred = responseDeferred
                responseDeferred = null
                deferred?.complete(complete)
            }
        }
    }

    private fun failPendingResponse(reason: String) {
        synchronized(responseLock) {
            responseFramer.clear()
            val deferred = responseDeferred
            responseDeferred = null
            deferred?.completeExceptionally(IllegalStateException(reason))
        }
    }

    private fun handleLinkLost(reason: String) {
        val wasReady = sessionReady.getAndSet(false)
        stopPollLoop()
        consecutiveLiveMisses = 0
        _ecuConnected.value = false
        clearPidCache()
        synchronized(vehicleOnLock) {
            vehicleOnState = VehicleOnPolicy.onLinkLost(vehicleOnState)
            publishVehicleOn(System.currentTimeMillis())
        }
        loggedFirstPollOk.set(false)
        failPendingResponse(reason)
        connecting.set(false)
        if (wasReady) {
            setStatus("Disconnected from adapter")
            logW("Link lost ($reason) — will auto-retry")
        }
    }

    private suspend fun runInitSequence(): Boolean {
        return try {
            resetPidDiscoveryState()
            // Clear noise after link-up; adapters often need a beat after GATT notify.
            delay(500)
            drainBuffer()

            val performance = settings.obdPerformanceMode
            val baseSteps = listOf(
                "ATZ" to ATZ_TIMEOUT_MS,
                "ATE0" to COMMAND_TIMEOUT_MS,
                "ATL0" to COMMAND_TIMEOUT_MS,
                "ATS0" to COMMAND_TIMEOUT_MS,
                "ATH0" to COMMAND_TIMEOUT_MS,
                "ATAL" to COMMAND_TIMEOUT_MS, // long/multi-frame (e.g. odometer A6)
                ElmPerformanceMode.adaptiveTimingCommand(performance) to COMMAND_TIMEOUT_MS,
            )
            for ((cmd, timeout) in baseSteps) {
                val resp = sendCommandLogged(cmd, timeout, isInit = true)
                if (resp == null) {
                    logE("ELM init failed at $cmd (timeout/error)")
                    setStatus("ELM init failed ($cmd)")
                    _ecuConnected.value = false
                    return false
                }
                if (cmd == "ATZ") delay(500)
            }

            wwhEngineOnly = settings.wwhObdOnly
            sessionEngineHeader = "7DF"

            // Always use the configured protocol only (ATSP0 only when user picks Automatic).
            // Do not fall back to auto-search if a fixed protocol is set — early 0100
            // misses are usually a cold ECU, not a wrong protocol.
            val atsp = protocol.atspCommand
            logI("Using OBD protocol ${protocol.name} ($atsp) — no auto fallback")

            val sp = sendCommandLogged(atsp, COMMAND_TIMEOUT_MS, isInit = true)
            if (sp == null) {
                logE("ELM init failed at $atsp (timeout/error)")
                setStatus("ELM init failed ($atsp)")
                _ecuConnected.value = false
                return false
            }
            delay(500)

            if (wwhEngineOnly) {
                if (!probeWwhEngineLive(attempts = ECU_PROBE_ATTEMPTS)) {
                    logE("ELM init failed at WWH 22F40C (no ECU response)")
                    setStatus("WWH-OBD init failed (no 22F40C)")
                    _ecuConnected.value = false
                    return false
                }
                // ecuConnected only after live PID decode (probe may have stored 0c).
                logI(
                    "ELM init OK — WWH-OBD only header=$sessionEngineHeader " +
                        "hot=${HOT_PIDS.size} slow=${SLOW_PIDS.size} " +
                        "performance=$performance ecuLive=${_ecuConnected.value}",
                )
                return true
            }

            // Functional OBD header before first 0100 (best-effort).
            // Skip on Golf mk4 TDI: ISO 9141 K-line must not use CAN ATSH7DF.
            val profile = VehicleObdProfile.fromName(settings.vehicleObdProfile)
            if (ElmInitPolicy.sendCanFunctionalHeader(profile)) {
                sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = true)
                sendCommandLogged("ATSH7DF", COMMAND_TIMEOUT_MS, isInit = true)
                sessionEngineHeader = "7DF"
            }

            val ecuRaw = probeEcuSupportedPids(attempts = ECU_PROBE_ATTEMPTS)
            if (ecuRaw == null) {
                logE("ELM init failed at 0100 (no ECU response on $atsp)")
                setStatus("ELM init failed (0100)")
                _ecuConnected.value = false
                return false
            }

            if (isSuccessfulMode01Response(ecuRaw, expectPid = 0x00)) {
                discoverSupportedPids(first0100 = ecuRaw)
            } else {
                // Live via 010C only — do not treat RPM payload as a support bitmap.
                supportedMode01Pids = emptySet()
                logI("Support discovery skipped (ECU proven via 010C)")
                // Contact via 010C does not decode RPM here; poll loop will mark live.
            }
            // Do not set ecuConnected from bus contact alone — wait for live PID decode.
            logI(
                "ELM init OK — mode01 support=${supportedMode01Pids.size} " +
                    "protocol=${protocol.name} hot=${HOT_PIDS.size} slow=${SLOW_PIDS.size} " +
                    "performance=$performance (tracking waits for live 0c/0d/42)",
            )
            true
        } catch (t: Throwable) {
            logE("ELM init exception: ${t.message}")
            Log.w(TAG, "Init sequence failed", t)
            setStatus("ELM init failed")
            _ecuConnected.value = false
            false
        }
    }

    /**
     * Retry 0100 until ECU answers with a 41 bitmap.
     * Short per-try timeout + modest backoff (cold ECU), same fixed protocol only.
     * If 0100 never lands, accept a live 010C as contact and continue without bitmap.
     */
    private suspend fun probeEcuSupportedPids(attempts: Int = ECU_PROBE_ATTEMPTS): String? {
        repeat(attempts) { attempt ->
            val raw = sendCommandLogged("0100", ECU_PROBE_TIMEOUT_MS, isInit = true)
            if (isSuccessfulMode01Response(raw, expectPid = 0x00)) {
                if (attempt > 0) {
                    logI("0100 OK on attempt ${attempt + 1}/$attempts")
                }
                return raw
            }
            // Quick alternate wake: some ECUs answer RPM before support bitmap.
            if (attempt >= 2) {
                val rpmRaw = sendCommandLogged("010C", ECU_PROBE_TIMEOUT_MS, isInit = true)
                if (isSuccessfulMode01Response(rpmRaw, expectPid = 0x0C)) {
                    logI(
                        "ECU live via 010C on attempt ${attempt + 1}/$attempts " +
                            "(0100 still pending — support discovery may be empty)",
                    )
                    val rpm = rpmRaw?.let { parser.decodePid("0c", it) }
                    if (rpm != null && isValidPidValue("0c", rpm)) {
                        val updated = LinkedHashMap(
                            PidPollPolicy.afterSuccess(_pidValues.value, "0c", rpm),
                        )
                        recomputeFuelRate(updated)
                        commitPidMap(updated, touchedPid = "0c")
                        engineOkCount += 1
                        applyLiveSuccessToConnection()
                        applyVehicleOnPid("0c", rpm)
                    }
                    return rpmRaw
                }
            }
            logW(
                "0100 probe attempt ${attempt + 1}/$attempts failed: " +
                    (raw?.take(80) ?: "timeout/null"),
            )
            delay(200L + attempt * 150L)
            drainBuffer()
        }
        return null
    }

    /**
     * Prove WWH-OBD live with 22F40C (RPM). Tries functional 7DF then physical 7E0.
     * No Mode 01 fallback.
     */
    private suspend fun probeWwhEngineLive(attempts: Int): Boolean {
        val headers = listOf("7DF", "7E0")
        for (header in headers) {
            sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = true)
            val sh = sendCommandLogged("ATSH$header", COMMAND_TIMEOUT_MS, isInit = true)
            if (sh == null || !ElmHeaderRestore.isAcceptableAtResponse(sh)) {
                logW("WWH init: ATSH$header failed")
                continue
            }
            repeat(attempts) { attempt ->
                val raw = sendCommandLogged(
                    WwhObd.commandForPidHex("0c"),
                    ECU_PROBE_TIMEOUT_MS,
                    isInit = true,
                )
                if (WwhObd.isPositiveRead(raw, expectPid = 0x0C)) {
                    sessionEngineHeader = header
                    val shim = WwhObd.mode01CompatibleResponse("0c", raw)
                    val rpm = shim?.let { parser.decodePid("0c", it) }
                    if (rpm != null && isValidPidValue("0c", rpm)) {
                        val updated = LinkedHashMap(
                            PidPollPolicy.afterSuccess(_pidValues.value, "0c", rpm),
                        )
                        recomputeFuelRate(updated)
                        commitPidMap(updated, touchedPid = "0c")
                        engineOkCount += 1
                        applyLiveSuccessToConnection()
                        applyVehicleOnPid("0c", rpm)
                    }
                    if (attempt > 0 || header != "7DF") {
                        logI("WWH 22F40C OK header=$header attempt ${attempt + 1}/$attempts")
                    }
                    return true
                }
                logW(
                    "WWH 22F40C attempt ${attempt + 1}/$attempts header=$header failed: " +
                        (raw?.take(80) ?: "null"),
                )
                delay(400L + attempt * 300L)
                drainBuffer()
            }
        }
        return false
    }

    private fun isSuccessfulMode01Response(raw: String?, expectPid: Int): Boolean =
        ElmHeaderRestore.isMode01Live(raw, expectPid)

    private suspend fun discoverSupportedPids(first0100: String) {
        val all = linkedSetOf<Int>()
        all += PidSupport.parseSupportBitmap(0x00, first0100)
        var next = PidSupport.nextSupportCommandPid(0x00, first0100)
        var guard = 0
        while (next != null && guard < 8) {
            guard += 1
            val cmdPid = next
            val cmd = "01" + cmdPid.toString(16).uppercase().padStart(2, '0')
            var raw: String? = null
            var ok = false
            for (attempt in 0 until SUPPORT_QUERY_ATTEMPTS) {
                raw = sendCommandLogged(cmd, ECU_PROBE_TIMEOUT_MS, isInit = true)
                if (isSuccessfulMode01Response(raw, expectPid = cmdPid) && raw != null) {
                    if (attempt > 0) {
                        logI("$cmd OK on attempt ${attempt + 1}/$SUPPORT_QUERY_ATTEMPTS")
                    }
                    ok = true
                    break
                }
                logW(
                    "Support query $cmd attempt ${attempt + 1}/$SUPPORT_QUERY_ATTEMPTS failed: " +
                        (raw?.take(80) ?: "timeout/null"),
                )
                delay(200L + attempt * 150L)
                drainBuffer()
            }
            if (!ok || raw == null) {
                logW("Support query $cmd failed; incomplete bitmap (higher PIDs still polled)")
                break
            }
            all += PidSupport.parseSupportBitmap(cmdPid, raw)
            next = PidSupport.nextSupportCommandPid(cmdPid, raw)
        }
        supportedMode01Pids = all.toSet()
        val sample = all.sorted().take(24).joinToString(",") { "%02X".format(it) }
        logI("Discovered Mode 01 PIDs (${all.size}): $sample${if (all.size > 24) "…" else ""}")
    }

    private fun resetPidDiscoveryState() {
        supportedMode01Pids = emptySet()
        noDataLogged.clear()
        sessionWinningDid = null
        vwOdoSchedule.reset()
        sessionOdometerTracker.reset()
        engineOkCount = 0
        wwhEngineOnly = false
        sessionEngineHeader = "7DF"
        udsReceiveFilterWasSet = false
        loggedVwUdsStart.set(false)
        loggedVwUdsSuccess.set(false)
        loggedVwUdsFail.set(false)
        loggedVwClusterExtraOk.clear()
        udsHeaderRestoreFailed = false
        vwUdsNotBeforeMs = 0L
        consecutiveEngineTimeouts = 0
        consecutiveLiveMisses = 0
        // Drop stale metrics (e.g. prior-trip engine run time PID 1F) before a new session.
        clearPidCache()
        resetVehicleOnProofClock()
    }

    private fun applyLiveSuccessToConnection() {
        val next = LiveObdConnectionPolicy.onLiveSuccess(
            currentlyConnected = _ecuConnected.value,
            consecutiveMisses = consecutiveLiveMisses,
        )
        consecutiveLiveMisses = next.consecutiveMisses
        if (!_ecuConnected.value && next.ecuConnected) {
            setStatus("ECU online")
        }
        _ecuConnected.value = next.ecuConnected
    }

    private fun trackingPrefs() =
        appContext.getSharedPreferences(VehicleOnPersist.PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadVehicleOnFlag() {
        val prefs = trackingPrefs()
        val storedDongle = prefs.getString(VehicleOnPersist.KEY_FLAG_DONGLE, "") ?: ""
        val address = selectedAddress.orEmpty()
        val keep = VehicleOnPersist.shouldKeepFlag(storedDongle, address)
        val saw = keep && prefs.getBoolean(VehicleOnPersist.KEY_SAW_POSITIVE_RPM, false)
        synchronized(vehicleOnLock) {
            vehicleOnState = VehicleOnPolicy.State(sawPositiveRpm = saw, lastProofAtMs = null)
            _vehicleOn.value = false
        }
        if (!keep) persistVehicleOnFlag(false, address)
    }

    private fun persistVehicleOnFlag(
        saw: Boolean,
        address: String = selectedAddress.orEmpty(),
    ) {
        trackingPrefs().edit()
            .putBoolean(VehicleOnPersist.KEY_SAW_POSITIVE_RPM, saw)
            .putString(VehicleOnPersist.KEY_FLAG_DONGLE, address)
            .apply()
    }

    private fun publishVehicleOn(nowMs: Long) {
        _vehicleOn.value = VehicleOnPolicy.isVehicleOn(vehicleOnState, nowMs)
    }

    private fun powertrainKind(): PowertrainKind =
        when (FuelClass.fromName(settings.fuelClass)) {
            FuelClass.HYBRID -> PowertrainKind.HYBRID
            FuelClass.FULL_ELECTRIC -> PowertrainKind.ELECTRIC
            else -> PowertrainKind.ICE
        }

    private fun applyVehicleOnPid(pid: String, value: Double) {
        val now = System.currentTimeMillis()
        val shouldRelease: Boolean
        synchronized(vehicleOnLock) {
            val prev = vehicleOnState.sawPositiveRpm
            vehicleOnState = VehicleOnPolicy.applyFreshPid(
                vehicleOnState,
                pid,
                value,
                now,
                powertrainKind(),
            )
            if (pid.equals("0d", ignoreCase = true)) {
                obdSpeedDecoded = true
            }
            if (vehicleOnState.sawPositiveRpm != prev) {
                persistVehicleOnFlag(vehicleOnState.sawPositiveRpm)
            }
            publishVehicleOn(now)
            shouldRelease = VehicleOnPolicy.shouldReleaseAdapter(
                vehicleOnState,
                now,
                powertrain = powertrainKind(),
            )
        }
        if (shouldRelease) {
            releaseAdapterForParkedSleep()
        }
    }

    fun noteGpsSpeed(speedKph: Double, nowMs: Long = System.currentTimeMillis()) {
        if (!isInitialized) return
        if (!speedKph.isFinite() || speedKph < 0.0) return
        synchronized(vehicleOnLock) {
            if (!VehicleOnPolicy.shouldAcceptGpsSpeed(obdSpeedDecoded, _ecuConnected.value)) return
            vehicleOnState = VehicleOnPolicy.onSpeed(
                vehicleOnState,
                speedKph,
                nowMs,
                powertrainKind(),
            )
            publishVehicleOn(nowMs)
        }
    }

    fun clearSawPositiveRpm() {
        if (!isInitialized) return
        synchronized(vehicleOnLock) {
            persistVehicleOnFlag(VehicleOnPersist.flagAfterShutdown())
            vehicleOnState = VehicleOnPolicy.onSessionReset(false)
            obdSpeedDecoded = false
            _vehicleOn.value = false
        }
    }

    fun refreshVehicleOn(nowMs: Long = System.currentTimeMillis()) {
        if (!isInitialized) return
        publishPidValues(_pidValues.value, nowMs)
        val shouldRelease: Boolean
        synchronized(vehicleOnLock) {
            publishVehicleOn(nowMs)
            shouldRelease = VehicleOnPolicy.shouldReleaseAdapter(
                vehicleOnState,
                nowMs,
                powertrain = powertrainKind(),
            )
        }
        if (shouldRelease) {
            releaseAdapterForParkedSleep()
        }
    }

    private fun resetVehicleOnProofClock() {
        synchronized(vehicleOnLock) {
            vehicleOnState = VehicleOnPolicy.onSessionReset(vehicleOnState.sawPositiveRpm)
            obdSpeedDecoded = false
            publishVehicleOn(System.currentTimeMillis())
        }
    }

    private fun applyLiveMissToConnection() {
        val next = LiveObdConnectionPolicy.onLiveMiss(
            currentlyConnected = _ecuConnected.value,
            consecutiveMisses = consecutiveLiveMisses,
        )
        consecutiveLiveMisses = next.consecutiveMisses
        if (_ecuConnected.value && !next.ecuConnected) {
            logW(
                "Live OBD miss streak=${next.consecutiveMisses} (0c/0d/42) — " +
                    "ECU offline for tracking (stale last-good PIDs expire)",
            )
            setStatus("ECU offline (no live PIDs)")
        }
        _ecuConnected.value = next.ecuConnected
    }

    /** Log first miss per PID per session; drop last-good RPM/speed immediately. */
    private fun notePidMiss(pid: String, rawHint: String?) {
        applyPidMiss(pid)
        if (!noDataLogged.add(pid)) return
        val hint = rawHint?.replace('\n', ' ')?.trim()?.take(80) ?: "empty/timeout"
        val dropNote =
            if (pid.lowercase() in PidPollPolicy.DROP_ON_MISS) " — last-good RPM/speed dropped" else ""
        logW("PID $pid miss ($hint)$dropNote")
    }

    private fun markPidSeen(pid: String, nowMs: Long) {
        pidSeenAtMs[pid.lowercase()] = nowMs
    }

    private fun clearPidCache() {
        pidSeenAtMs.clear()
        _pidValues.value = emptyMap()
    }

    private fun publishPidValues(
        values: Map<String, Double>,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val fresh = PidPollPolicy.expireOlderThan(values, pidSeenAtMs, nowMs)
        if (fresh.size < values.size) {
            val keep = fresh.keys.map { it.lowercase() }.toSet()
            pidSeenAtMs.keys.removeAll { it.lowercase() !in keep }
        }
        _pidValues.value = fresh
    }

    private fun commitPidMap(updated: Map<String, Double>, touchedPid: String) {
        val now = System.currentTimeMillis()
        markPidSeen(touchedPid, now)
        if (updated.containsKey("ff125a")) markPidSeen("ff125a", now)
        if (updated.containsKey(ESTIMATED_MAF_KEY)) markPidSeen(ESTIMATED_MAF_KEY, now)
        publishPidValues(updated, now)
    }

    private fun applyPidMiss(pid: String) {
        if (pid.lowercase() in PidPollPolicy.DROP_ON_MISS) {
            pidSeenAtMs.remove(pid.lowercase())
        }
        publishPidValues(PidPollPolicy.afterMiss(_pidValues.value, pid))
    }

    /**
     * Wraps [sendCommand] with debug logging.
     * Init: log every TX/RX. Poll: log failures only.
     * @return response or null on timeout/error
     */
    private suspend fun sendCommandLogged(
        cmd: String,
        timeoutMs: Long = COMMAND_TIMEOUT_MS,
        isInit: Boolean,
    ): String? {
        return try {
            if (isInit) logI(">> $cmd")
            val resp = sendCommand(cmd, timeoutMs)
            val compact = resp.replace('\n', ' ').trim().take(200)
            if (isInit) {
                logI("<< $compact")
            } else {
                val u = resp.uppercase()
                // Plain NO DATA is logged once per PID in notePidMiss.
                if (u.contains("ERROR") || u.contains("UNABLE") ||
                    u.contains("BUS INIT") || u.contains("STOPPED") ||
                    (u.contains("?") && !u.contains("NO DATA"))
                ) {
                    logW("PID/cmd $cmd → $compact")
                }
            }
            resp
        } catch (t: Throwable) {
            logW("${if (isInit) "Init" else "Cmd"} $cmd failed: ${t.message}")
            if (ElmCommandFailure.isFatalWrite(t.message)) throw t
            null
        }
    }

    private fun drainBuffer() {
        synchronized(responseLock) {
            responseFramer.clear()
        }
    }

    private suspend fun sendCommand(cmd: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): String {
        return commandMutex.withLock {
            withContext(Dispatchers.IO) {
                if (!transport.isLinked) throw IllegalStateException("Not connected")
                if (!hasConnectPermission()) throw SecurityException("Missing BLUETOOTH_CONNECT")

                val deferred = CompletableDeferred<String>()
                synchronized(responseLock) {
                    responseFramer.clear()
                    responseDeferred = deferred
                }

                val payload = (cmd.trim() + "\r").toByteArray(Charset.forName(CHARSET_NAME))
                val written = transport.write(payload)
                if (!written) {
                    synchronized(responseLock) {
                        responseDeferred = null
                    }
                    throw IllegalStateException("Write failed for $cmd")
                }

                val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
                if (result == null) {
                    synchronized(responseLock) {
                        val partial = responseFramer.drainPartial()
                        responseDeferred = null
                        if (partial.isNotBlank()) return@withContext partial
                    }
                    throw IllegalStateException("Timeout waiting for response to $cmd")
                }
                result
            }
        }
    }

    private fun recomputeFuelRate(into: MutableMap<String, Double>) {
        val fuelClass = FuelClass.fromName(settings.fuelClass)
        val config = FuelCalcConfig(
            stoichAfr = settings.fuelStoichAfr,
            densityGl = settings.fuelDensityGl,
            displacementL = settings.engineDisplacementL,
            ve = settings.engineVe,
            isDiesel = fuelClass == FuelClass.DIESEL,
            fuelClass = fuelClass,
        )
        val sensors = FuelCalcSensors(
            mafGs = into["10"],
            lambda = into["44"],
            mapKpa = into["0b"],
            rpm = into["0c"],
            iatC = into["0f"],
            stftPct = into["06"],
            ltftPct = into["07"],
            ecuFuelRateLh = into["5e"],
            speedKph = into["0d"],
            calculatedLoadPct = into["04"],
        )
        // Publish estimated air mass when ECU has no MAF, or MAF is peak-air idle fraud.
        // Samples prefer ESTIMATED_MAF_KEY over raw PID 0x10 so stored mass_air_flow is sane.
        val trustedMaf = FuelConsumptionCalculator.isTrustedSensorMaf(config, sensors)
        if (!trustedMaf) {
            val estimated = FuelConsumptionCalculator.airMassGs(config, sensors)
            if (estimated != null && isValidPidValue(ESTIMATED_MAF_KEY, estimated)) {
                into[ESTIMATED_MAF_KEY] = estimated
            } else {
                into.remove(ESTIMATED_MAF_KEY)
            }
        } else {
            into.remove(ESTIMATED_MAF_KEY)
        }
        val fuelLh = FuelConsumptionCalculator.litersPerHour(config, sensors)
        if (fuelLh != null && isValidPidValue("ff125a", fuelLh)) {
            into["ff125a"] = fuelLh
        }
    }

    private fun startPollLoop() {
        stopPollLoop()
        pollJob = scope.launch {
            var round = 0
            var pidsOkWindow = 0
            var windowStartMs = System.currentTimeMillis()
            while (isActive && sessionReady.get()) {
                round++
                val pids = ObdPollSchedule.pidsForRound(
                    round,
                    HOT_PIDS,
                    SLOW_PIDS,
                    SLOW_EVERY,
                    supportedMode01 = supportedMode01Pids,
                )
                var liveDecodedThisRound = false
                var liveAttemptedThisRound = false
                try {
                    for (pid in pids) {
                        if (!sessionReady.get()) break
                        val cmd = if (wwhEngineOnly) {
                            WwhObd.commandForPidHex(pid)
                        } else {
                            ElmPerformanceMode.mode01PollCommand(
                                pid,
                                settings.obdPerformanceMode,
                            )
                        }
                        val raw = sendCommandLogged(cmd, isInit = false)
                        if (raw == null) {
                            if (
                                ElmCommandFailure.shouldAbortPoll(
                                    isLinked = transport.isLinked,
                                    writeFailed = false,
                                ) || !sessionReady.get()
                            ) {
                                break
                            }
                            consecutiveEngineTimeouts += 1
                            if (
                                UdsRestorePolicy.shouldHardRecoverSession(
                                    udsRestoreUnhealthy = udsHeaderRestoreFailed,
                                    consecutiveEngineTimeouts = consecutiveEngineTimeouts,
                                )
                            ) {
                                logE(
                                    "Engine timeouts after UDS restore " +
                                        "(streak=$consecutiveEngineTimeouts) — session re-init",
                                )
                                sessionReady.set(false)
                                consecutiveLiveMisses = 0
                                _ecuConnected.value = false
                                setStatus("OBD headers unhealthy — reconnecting")
                                closeLinkInternal()
                                break
                            }
                            notePidMiss(pid, null)
                            if (
                                LiveObdConnectionPolicy.shouldCountLiveMiss(
                                    pid,
                                    supportedMode01Pids,
                                )
                            ) {
                                liveAttemptedThisRound = true
                            }
                            continue
                        }
                        consecutiveEngineTimeouts = 0
                        if (udsHeaderRestoreFailed) {
                            // Mode 01 answering again — clear soft unhealthy flag.
                            udsHeaderRestoreFailed = false
                            logI("Engine stack healthy again after UDS restore blip")
                        }
                        val value = if (wwhEngineOnly) {
                            val shim = WwhObd.mode01CompatibleResponse(pid, raw)
                            shim?.let { parser.decodePid(pid, it) }
                        } else {
                            parser.decodePid(pid, raw)
                        }
                        if (value == null) {
                            notePidMiss(pid, raw)
                            if (
                                LiveObdConnectionPolicy.shouldCountLiveMiss(
                                    pid,
                                    supportedMode01Pids,
                                )
                            ) {
                                liveAttemptedThisRound = true
                            }
                            continue
                        }
                        if (!isValidPidValue(pid, value)) {
                            logW("Invalid value for PID $pid: $value")
                            applyPidMiss(pid)
                            if (
                                LiveObdConnectionPolicy.shouldCountLiveMiss(
                                    pid,
                                    supportedMode01Pids,
                                )
                            ) {
                                liveAttemptedThisRound = true
                            }
                            continue
                        }

                        val updated = LinkedHashMap(
                            PidPollPolicy.afterSuccess(_pidValues.value, pid, value),
                        )
                        val fuelInputs = setOf("10", "44", "0b", "0c", "0f", "06", "07", "5e")
                        if (pid in fuelInputs) {
                            recomputeFuelRate(updated)
                        }
                        if (pid.equals("31", ignoreCase = true) && sessionOdometerTracker.isLocked) {
                            sessionOdometerTracker.currentKm(value)?.let { derived ->
                                if (isValidPidValue(OdometerReading.UDS_KM_KEY, derived)) {
                                    updated[OdometerReading.UDS_KM_KEY] = derived
                                }
                            }
                        }
                        commitPidMap(updated, touchedPid = pid)
                        pidsOkWindow++
                        engineOkCount += 1

                        if (LiveObdConnectionPolicy.isLivePid(pid)) {
                            liveDecodedThisRound = true
                            applyLiveSuccessToConnection()
                        }
                        applyVehicleOnPid(pid, value)
                        if (loggedFirstPollOk.compareAndSet(false, true)) {
                            val stack = if (wwhEngineOnly) "WWH" else "Mode01"
                            logI(
                                "Poll started stack=$stack (hot=${HOT_PIDS.size}, " +
                                    "slow=${SLOW_PIDS.size}, slowEvery=$SLOW_EVERY)",
                            )
                        }
                    }
                    if (liveAttemptedThisRound && !liveDecodedThisRound) {
                        applyLiveMissToConnection()
                    }
                } catch (t: Throwable) {
                    if (ElmCommandFailure.isFatalWrite(t.message)) {
                        logW("Adapter write failed — stopping poll (link dead?)")
                        handleLinkLost("write failed")
                        break
                    }
                    logW("Poll cycle error: ${t.message}")
                    Log.w(TAG, "Poll cycle error", t)
                }

                // Light rate log ~every 10s (not per PID)
                val now = System.currentTimeMillis()
                if (now - windowStartMs >= 10_000L) {
                    val secs = (now - windowStartMs).coerceAtLeast(1) / 1000.0
                    val rate = pidsOkWindow / secs
                    logI("OBD poll ~${"%.1f".format(rate)} PID/s (round=$round)")
                    pidsOkWindow = 0
                    windowStartMs = now
                }

                // No 1s pad — yield so cancellation can run between rounds
                yield()
            }
        }
    }

    /**
     * VW MQB only: block Mode 01 until one successful cluster odometer lock.
     * Retries with backoff while the link is up; never starts the poll loop without a lock.
     * @return true if [sessionOdometerTracker] locked.
     */
    private suspend fun awaitVwClusterOdometerLock(): Boolean {
        var attempt = 0
        while (transport.isLinked && currentCoroutineContext().isActive) {
            if (sessionOdometerTracker.isLocked) return true
            setStatus("Reading VW odometer…")
            logI("VW UDS cluster hop (pre-Mode01 attempt=${attempt + 1})")
            val got = pollVwOdometerOnce()
            if (got || sessionOdometerTracker.isLocked) {
                logI("VW odometer locked — starting Mode 01")
                return true
            }
            val delayMs = VwOdoFirstGate.retryDelayMs(attempt)
            attempt += 1
            delay(delayMs)
        }
        return sessionOdometerTracker.isLocked
    }

    /** @return true if cluster odometer km was published this hop. */
    private suspend fun pollVwOdometerOnce(): Boolean {
        if (loggedVwUdsStart.compareAndSet(false, true)) {
            logI(
                "VW MQB UDS cluster probe (ATSH${VwClusterHopPolicy.REQUEST_HEADER_HEX} / " +
                    "service 22, CRA${VwClusterHopPolicy.RESPONSE_FILTER_HEX} hop-local, " +
                    "plain first then ATFC on any miss)",
            )
        }
        var gotReading = false
        var lastMissReason: String? = null
        var lastRawForRetry: String? = null
        udsReceiveFilterWasSet = false
        try {
            // Physical addressing to instrument cluster (11-bit CAN).
            val shCmd = "ATSH${VwClusterHopPolicy.REQUEST_HEADER_HEX}"
            val sh = sendCommandLogged(shCmd, COMMAND_TIMEOUT_MS, isInit = false)
            if (sh == null || !ElmHeaderRestore.isAcceptableAtResponse(sh)) {
                lastMissReason = "$shCmd failed/timeout"
                logW("VW UDS: $lastMissReason")
                return false
            }

            // Hop-local receive filter (cluster response 77E). Required on the path
            // that once returned DID 2203 km. Must ATAR-clear in restoreObdHeaders —
            // sticky CRA makes Mode 01 (7E8) silent.
            if (VwClusterHopPolicy.USE_RECEIVE_FILTER) {
                val craCmd = "ATCRA${VwClusterHopPolicy.RESPONSE_FILTER_HEX}"
                val cra = sendCommandLogged(craCmd, COMMAND_TIMEOUT_MS, isInit = false)
                if (ElmHeaderRestore.isAcceptableAtResponse(cra)) {
                    udsReceiveFilterWasSet = true
                } else {
                    logW("VW UDS: $craCmd not accepted — continuing without receive filter")
                }
            }

            // Clean slate: never enter the hop with a sticky custom FC mode from a
            // prior attempt. ATCFC1 = adapter-automatic ISO-TP FC (ELM default).
            sendCommandLogged("ATFCSM0", COMMAND_TIMEOUT_MS, isInit = false)
            sendCommandLogged("ATCFC1", COMMAND_TIMEOUT_MS, isInit = false)
            delay(80)

            val candidates = UdsReadDid.candidateDids(settings.vwOdometerDid)
            // Ignore stale session winner outside current candidates (e.g. old 22B0 false-win).
            val dids = sessionWinningDid
                ?.takeIf { it in candidates }
                ?.let { listOf(it) }
                ?: candidates.also {
                    if (sessionWinningDid != null && sessionWinningDid !in candidates) {
                        logW(
                            "VW UDS: dropping stale winning DID=" +
                                "${didHex(sessionWinningDid!!)} (not in candidates)",
                        )
                        sessionWinningDid = null
                    }
                }

            // Pass 1: adapter-default ISO-TP FC (no ATFCSM1).
            // Link-only gate: pre-Mode01 odo runs while sessionReady is still false.
            for (did in dids) {
                if (!transport.isLinked) break
                val outcome = tryReadClusterOdometerDid(did)
                if (outcome.km != null) {
                    publishClusterOdometer(did, outcome.km)
                    gotReading = true
                    break
                }
                lastRawForRetry = outcome.raw
                val snippet = outcome.raw
                    ?.replace('\n', ' ')
                    ?.replace('\r', ' ')
                    ?.trim()
                    ?.take(100)
                    ?: "timeout/empty"
                lastMissReason = if (UdsReadDid.suggestsIsoTpFlowControlRetry(outcome.raw)) {
                    "incomplete multi-frame DID ${didHex(did)}: $snippet"
                } else {
                    "no 62${didHex(did)} parse in: $snippet"
                }
            }

            // Pass 2: hop-local custom FC after ANY plain miss (including NO DATA).
            // Nivus 2203 is multi-frame; many clones never emit a first-frame fragment
            // without tester FC — gating only on incomplete multi-frame skipped the retry.
            if (
                VwClusterHopPolicy.shouldRetryWithCustomFlowControl(
                    gotOdometerKm = gotReading,
                    lastRaw = lastRawForRetry,
                )
            ) {
                val hint = lastRawForRetry
                    ?.replace('\n', ' ')
                    ?.replace('\r', ' ')
                    ?.trim()
                    ?.take(80)
                    ?: lastMissReason
                    ?: "?"
                logI("VW UDS: retry DIDs with hop-local ATFC (plain miss: $hint)")
                if (enableClusterFlowControl()) {
                    delay(80)
                    for (did in dids) {
                        if (!transport.isLinked) break
                        val outcome = tryReadClusterOdometerDid(did)
                        if (outcome.km != null) {
                            publishClusterOdometer(did, outcome.km)
                            gotReading = true
                            break
                        }
                        val snippet = outcome.raw
                            ?.replace('\n', ' ')
                            ?.replace('\r', ' ')
                            ?.trim()
                            ?.take(100)
                            ?: "timeout/empty"
                        lastMissReason =
                            "ATFC retry no 62${didHex(did)} parse in: $snippet"
                    }
                } else {
                    lastMissReason = "ATFCSM1 not accepted after plain miss"
                }
            }

            if (!gotReading) {
                val reason = lastMissReason ?: "no positive DID response"
                // Log every miss hop until first success so road tests are diagnosable.
                if (!loggedVwUdsSuccess.get()) {
                    logW("VW UDS odometer miss: $reason")
                    loggedVwUdsFail.set(true)
                }
            }

            // Same hop: cluster extras only after odo success — failed hops must restore ASAP.
            if (VwClusterHopPolicy.shouldPollClusterExtras(gotOdometerKm = gotReading)) {
                pollVwClusterExtras()
            }
        } catch (t: Throwable) {
            logW("VW UDS cluster error: ${t.message}")
            Log.w(TAG, "pollVwOdometerOnce", t)
        } finally {
            // Always clear custom FC — even if enable failed partway or a prior hop stuck.
            sendCommandLogged("ATFCSM0", COMMAND_TIMEOUT_MS, isInit = false)
            sendCommandLogged("ATCFC1", COMMAND_TIMEOUT_MS, isInit = false)
            restoreObdHeaders()
        }
        return gotReading
    }

    private data class ClusterOdoRead(val raw: String?, val km: Double?)

    private suspend fun tryReadClusterOdometerDid(did: Int): ClusterOdoRead {
        val cmd = "22" + did.toString(16).uppercase().padStart(4, '0')
        val raw = sendCommandLogged(cmd, UDS_COMMAND_TIMEOUT_MS, isInit = false)
        if (raw == null) return ClusterOdoRead(null, null)
        val payload = UdsReadDid.parsePositiveReadDid(raw, did) ?: return ClusterOdoRead(raw, null)
        val km = UdsReadDid.decodeOdometerKm(payload)
        if (km == null || !isValidPidValue(OdometerReading.UDS_KM_KEY, km)) {
            return ClusterOdoRead(raw, null)
        }
        return ClusterOdoRead(raw, km)
    }

    private fun publishClusterOdometer(did: Int, km: Double) {
        val pid31 = _pidValues.value["31"] ?: _pidValues.value["31".uppercase()]
        sessionOdometerTracker.lockBaseline(km, pid31)
        vwOdoSchedule.markLocked()
        val derived = sessionOdometerTracker.currentKm(pid31) ?: km
        val updated = LinkedHashMap(
            PidPollPolicy.afterSuccess(
                _pidValues.value,
                OdometerReading.UDS_KM_KEY,
                derived,
            ),
        )
        commitPidMap(updated, touchedPid = OdometerReading.UDS_KM_KEY)
        sessionWinningDid = did
        if (loggedVwUdsSuccess.compareAndSet(false, true)) {
            logI(
                "VW UDS odometer OK: DID=${didHex(did)} km=$km " +
                    "(locked; advancing via PID 31 delta)",
            )
        } else {
            logI("VW UDS odometer refresh: DID=${didHex(did)} km=$km")
        }
    }

    /**
     * Best-effort ISO-TP flow control for cluster multi-frame ($22) responses.
     * Returns true if ATFCSM1 was accepted (caller must clear in finally).
     * Used after plain pass miss per [VwClusterHopPolicy.shouldRetryWithCustomFlowControl].
     */
    private suspend fun enableClusterFlowControl(): Boolean {
        // FC TX header = request ID (714); classic FC data 30 00 00.
        val hdr = VwClusterHopPolicy.REQUEST_HEADER_HEX
        sendCommandLogged("ATFCSH$hdr", COMMAND_TIMEOUT_MS, isInit = false)
        sendCommandLogged("ATFCSD300000", COMMAND_TIMEOUT_MS, isInit = false)
        val sm = sendCommandLogged("ATFCSM1", COMMAND_TIMEOUT_MS, isInit = false)
        val ok = ElmHeaderRestore.isAcceptableAtResponse(sm)
        if (!ok) {
            logW("VW UDS: ATFCSM1 not accepted — multi-frame odo may fail on this adapter")
        }
        return ok
    }

    /**
     * Read fixed cluster DIDs while still on ATSH714. Misses do not fail the hop.
     * Call only from [pollVwOdometerOnce] before header restore.
     */
    private suspend fun pollVwClusterExtras() {
        for (did in VwClusterDids.EXTRA_DIDS) {
            if (!transport.isLinked) break
            val key = VwClusterDids.keyForDid(did) ?: continue
            val cmd = "22" + did.toString(16).uppercase().padStart(4, '0')
            val raw = sendCommandLogged(cmd, UDS_COMMAND_TIMEOUT_MS, isInit = false)
                ?: continue
            val payload = UdsReadDid.parsePositiveReadDid(raw, did) ?: continue
            val value = VwClusterDids.decodeValue(did, payload) ?: continue
            if (!isValidPidValue(key, value)) continue

            val updated = LinkedHashMap(
                PidPollPolicy.afterSuccess(_pidValues.value, key, value),
            )
            commitPidMap(updated, touchedPid = key)
            if (loggedVwClusterExtraOk.add(key)) {
                logI(
                    "VW UDS cluster OK: DID=${did.toString(16).uppercase().padStart(4, '0')} " +
                        "$key=$value",
                )
            }
        }
    }

    /**
     * Restore session engine addressing after a UDS cluster probe.
     * Must always run after [pollVwOdometerOnce] header switch.
     * Health-checks with Mode 01 010C or WWH 22F40C depending on stack.
     * On health miss: soft-backoff further UDS; do not kill the trip immediately.
     */
    private suspend fun restoreObdHeaders() {
        val filterWasSet = udsReceiveFilterWasSet
        val header = sessionEngineHeader.ifBlank { "7DF" }
        drainBuffer()
        // Belt-and-suspenders: leave cluster hop without custom ISO-TP FC.
        sendCommandLogged("ATFCSM0", COMMAND_TIMEOUT_MS, isInit = false)
        sendCommandLogged("ATCFC1", COMMAND_TIMEOUT_MS, isInit = false)
        // Clear receive filter (if any) then restore session engine header.
        // Double ATAR: some clones no-op the first clear after ATCRA77E.
        val atar = sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = false)
        if (filterWasSet) {
            sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = false)
        }
        val atsh = sendCommandLogged("ATSH$header", COMMAND_TIMEOUT_MS, isInit = false)

        val cmdsOk = ElmHeaderRestore.commandsSucceeded(
            atarRaw = atar,
            atshRaw = atsh,
            receiveFilterWasSet = filterWasSet,
        )
        if (!cmdsOk) {
            markUdsRestoreUnhealthy("ATAR/ATSH$header rejected")
            // Best-effort second shot at functional header + engine RX filter.
            sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = false)
            if (filterWasSet) {
                sendCommandLogged(
                    "ATCRA${ElmHeaderRestore.ENGINE_RECEIVE_FILTER_HEX}",
                    COMMAND_TIMEOUT_MS,
                    isInit = false,
                )
            }
            sendCommandLogged("ATSH7DF", COMMAND_TIMEOUT_MS, isInit = false)
            sessionEngineHeader = "7DF"
            return
        }

        delay(UDS_RESTORE_SETTLE_MS)
        drainBuffer()

        if (applyEngineHealthProbe()) {
            onUdsRestoreHealthy()
            return
        }

        // Retry same header, then pin RX to engine 7E8 if cluster CRA may still be sticky.
        logW("Engine not live after UDS restore — retrying ATAR/ATSH$header")
        sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = false)
        sendCommandLogged("ATSH$header", COMMAND_TIMEOUT_MS, isInit = false)
        delay(UDS_RESTORE_SETTLE_MS)
        drainBuffer()
        if (applyEngineHealthProbe()) {
            onUdsRestoreHealthy()
            logI("Engine recovered after UDS restore retry")
            return
        }

        if (
            ElmHeaderRestore.shouldForceEngineReceiveFilter(
                receiveFilterWasSet = filterWasSet,
                healthOkAfterAtar = false,
            )
        ) {
            logW(
                "Engine not live after ATAR — forcing ATCRA" +
                    "${ElmHeaderRestore.ENGINE_RECEIVE_FILTER_HEX} (sticky cluster filter?)",
            )
            val craEng = sendCommandLogged(
                "ATCRA${ElmHeaderRestore.ENGINE_RECEIVE_FILTER_HEX}",
                COMMAND_TIMEOUT_MS,
                isInit = false,
            )
            if (ElmHeaderRestore.isAcceptableAtResponse(craEng)) {
                sendCommandLogged("ATSH$header", COMMAND_TIMEOUT_MS, isInit = false)
                delay(UDS_RESTORE_SETTLE_MS)
                drainBuffer()
                if (applyEngineHealthProbe()) {
                    // Leave CRA7E8 — better Mode 01 than silent bus. ATAR next hop still tried.
                    udsReceiveFilterWasSet = false
                    onUdsRestoreHealthy()
                    logI(
                        "Engine recovered after ATCRA" +
                            "${ElmHeaderRestore.ENGINE_RECEIVE_FILTER_HEX}",
                    )
                    return
                }
                // Physical engine header with engine RX filter.
                if (!header.equals("7E0", ignoreCase = true)) {
                    val sh7e0 = sendCommandLogged("ATSH7E0", COMMAND_TIMEOUT_MS, isInit = false)
                    if (ElmHeaderRestore.isAcceptableAtResponse(sh7e0)) {
                        delay(UDS_RESTORE_SETTLE_MS)
                        drainBuffer()
                        if (applyEngineHealthProbe()) {
                            sessionEngineHeader = "7E0"
                            udsReceiveFilterWasSet = false
                            onUdsRestoreHealthy()
                            logI(
                                "Engine recovered on 7E0 + ATCRA" +
                                    "${ElmHeaderRestore.ENGINE_RECEIVE_FILTER_HEX}",
                            )
                            return
                        }
                    }
                    sendCommandLogged("ATSH7DF", COMMAND_TIMEOUT_MS, isInit = false)
                    sessionEngineHeader = "7DF"
                }
            }
        } else if (!header.equals("7E0", ignoreCase = true)) {
            logW("Engine not live on $header after UDS — trying ATSH7E0")
            sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = false)
            val sh7e0 = sendCommandLogged("ATSH7E0", COMMAND_TIMEOUT_MS, isInit = false)
            if (ElmHeaderRestore.isAcceptableAtResponse(sh7e0)) {
                delay(UDS_RESTORE_SETTLE_MS)
                drainBuffer()
                if (applyEngineHealthProbe()) {
                    sessionEngineHeader = "7E0"
                    onUdsRestoreHealthy()
                    logI("Engine recovered on physical 7E0 after cluster UDS")
                    return
                }
            }
            // Prefer functional for ongoing Mode 01 if 7E0 did not help.
            sendCommandLogged("ATSH7DF", COMMAND_TIMEOUT_MS, isInit = false)
            sessionEngineHeader = "7DF"
        }

        markUdsRestoreUnhealthy("health 010C/22F40C failed after retries")
    }

    private fun onUdsRestoreHealthy() {
        if (udsHeaderRestoreFailed) {
            logI("OBD headers restored after prior UDS restore failure")
        }
        udsHeaderRestoreFailed = false
        udsReceiveFilterWasSet = false
        consecutiveEngineTimeouts = 0
        vwUdsNotBeforeMs = UdsRestorePolicy.nextUdsAllowedAtMs(
            nowMs = System.currentTimeMillis(),
            restoreHealthOk = true,
        )
    }

    private fun markUdsRestoreUnhealthy(reason: String) {
        udsHeaderRestoreFailed = true
        val now = System.currentTimeMillis()
        vwUdsNotBeforeMs = UdsRestorePolicy.nextUdsAllowedAtMs(
            nowMs = now,
            restoreHealthOk = false,
        )
        logE(
            "UDS restore unhealthy ($reason) — backing off cluster UDS " +
                "${UdsRestorePolicy.DEFAULT_BACKOFF_MS / 1000}s; Mode 01 keeps polling",
        )
    }

    /** Health probe after header restore; updates RPM if decoded. */
    private suspend fun applyEngineHealthProbe(): Boolean {
        return if (wwhEngineOnly) {
            val live = sendCommandLogged(
                WwhObd.commandForPidHex("0c"),
                WWH_HEALTH_TIMEOUT_MS,
                isInit = false,
            )
            if (!WwhObd.isPositiveRead(live, expectPid = 0x0C)) return false
            val shim = WwhObd.mode01CompatibleResponse("0c", live)
            val rpm = shim?.let { parser.decodePid("0c", it) }
            if (rpm != null && isValidPidValue("0c", rpm)) {
                val updated = LinkedHashMap(
                    PidPollPolicy.afterSuccess(_pidValues.value, "0c", rpm),
                )
                recomputeFuelRate(updated)
                commitPidMap(updated, touchedPid = "0c")
                engineOkCount += 1
                applyLiveSuccessToConnection()
                applyVehicleOnPid("0c", rpm)
            }
            true
        } else {
            val live = sendCommandLogged(
                ElmPerformanceMode.mode01PollCommand("0c", settings.obdPerformanceMode),
                MODE01_HEALTH_TIMEOUT_MS,
                isInit = false,
            )
            if (!ElmHeaderRestore.isMode01Live(live, expectPid = 0x0C)) return false
            val rpm = live?.let { parser.decodePid("0c", it) }
            if (rpm != null && isValidPidValue("0c", rpm)) {
                val updated = LinkedHashMap(
                    PidPollPolicy.afterSuccess(_pidValues.value, "0c", rpm),
                )
                recomputeFuelRate(updated)
                commitPidMap(updated, touchedPid = "0c")
                engineOkCount += 1
                applyLiveSuccessToConnection()
                applyVehicleOnPid("0c", rpm)
            }
            true
        }
    }

    private fun stopPollLoop() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun closeLinkInternal() {
        failPendingResponse("closed")
        try {
            transport.close()
        } catch (t: Throwable) {
            Log.w(TAG, "closeLink failed", t)
        }
        transport = createTransport(bluetoothTransport)
    }

    private fun isValidPidValue(pid: String, value: Double): Boolean {
        if (value.isNaN() || value.isInfinite()) return false
        return when (pid) {
            "0d" -> value in 0.0..400.0
            "0c" -> value in 0.0..16383.0
            "2f", VwClusterDids.KEY_FUEL_PCT -> value in 0.0..100.0
            "04", "43", "45", "49" -> value in 0.0..100.0
            "10", ESTIMATED_MAF_KEY -> value in 0.0..655.35
            "5e" -> value in 0.0..100.0
            "5b", "9a" -> value in 0.0..100.0
            "ff125a" -> value in 0.0..200.0
            "42" -> value in 0.0..20.0
            "a6", OdometerReading.UDS_KM_KEY -> value in 0.0..2_000_000.0
            VwClusterDids.KEY_OIL_C -> value in -40.0..200.0
            VwClusterDids.KEY_DOORS -> value in 0.0..4_294_967_295.0
            "31" -> value in 0.0..65535.0
            else -> true
        }
    }

    private fun didHex(did: Int): String =
        did.toString(16).uppercase().padStart(4, '0')
}
