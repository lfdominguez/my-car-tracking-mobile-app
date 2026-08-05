package com.domivega.gps_car.obd

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.domivega.gps_car.fuel.FuelCalcConfig
import com.domivega.gps_car.fuel.FuelCalcSensors
import com.domivega.gps_car.fuel.FuelConsumptionCalculator
import com.domivega.gps_car.settings.AppSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

data class BleDeviceInfo(
    val name: String?,
    val address: String,
)

/**
 * Native BLE ELM327 manager: scan/connect GATT UART, AT session, PID poll loop.
 *
 * Call [initialize] once from Application. Observe [pidValues] / [ecuConnected].
 */
object ObdBleManager {
    private const val TAG = "ObdBleMgr"

    private val NORDIC_UART_SERVICE = uuid("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val NORDIC_UART_RX = uuid("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // write
    private val NORDIC_UART_TX = uuid("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // notify

    private val FFE0_SERVICE = uuid("0000FFE0-0000-1000-8000-00805F9B34FB")
    private val FFE1_CHAR = uuid("0000FFE1-0000-1000-8000-00805F9B34FB")

    private val CCCD = uuid("00002902-0000-1000-8000-00805F9B34FB")

    private const val COMMAND_TIMEOUT_MS = 4_000L
    private const val ATZ_TIMEOUT_MS = 6_000L
    private const val ECU_PROBE_TIMEOUT_MS = 8_000L
    private const val SCAN_PERIOD_MS = 12_000L
    private const val RECONNECT_INTERVAL_MS = 7_000L
    /** Wall-clock interval for VW MQB UDS cluster odometer probes. */
    private const val VW_ODO_INTERVAL_MS = 45_000L
    private const val UDS_COMMAND_TIMEOUT_MS = 6_000L
    private const val CHARSET_NAME = "US-ASCII"

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
        "49" to "Accelerator Pedal",
        "46" to "Ambient Air Temp",
        "a6" to "Odometer",
        "31" to "Distance since codes cleared",
        "05" to "Engine Coolant Temperature",
        "0b" to "Manifold Pressure",
        "42" to "Control Module Voltage",
        "1f" to "Engine ON time",
        "10" to "Mass Air Flow",
        "44" to "Fuel/Air Ratio",
        "33" to "Barometric Pressure",
        "0f" to "Intake Air Temp",
    )

    /** High-dynamics PIDs polled every round. */
    private val HOT_PIDS = listOf(
        "0c", // RPM
        "0d", // Speed
        "04", // Engine load
        "43", // Absolute load
        "49", // Accelerator
        "10", // MAF
        "44", // Lambda
        "0b", // MAP
        "06", // STFT
        "07", // LTFT
    )

    /** Slow-changing PIDs polled every [SLOW_EVERY]th round. */
    private val SLOW_PIDS = listOf(
        "2f", // Fuel level
        "a6", // Vehicle odometer (SAE PID A6)
        "31", // Distance since codes cleared (not dash odometer)
        "05", // Coolant
        "46", // Ambient
        "42", // Module voltage
        "1f", // Engine run time
        "33", // Barometric
        "0f", // IAT (MAP fuel fallback)
    )

    private const val SLOW_EVERY = 5

    /** Mode 01 PIDs reported supported by the ECU (empty = not discovered yet). Log-only. */
    @Volatile
    private var supportedMode01Pids: Set<Int> = emptySet()

    /** First miss/NO DATA log per PID per session (does not disable or clear values). */
    private val noDataLogged = ConcurrentHashMap.newKeySet<String>()

    /** First successful UDS odometer DID this session (prefer on later probes). */
    @Volatile
    private var sessionWinningDid: Int? = null

    /** Last VW UDS odometer attempt wall clock (rate limit). */
    @Volatile
    private var lastVwOdoAtMs: Long = 0L

    private val loggedVwUdsStart = AtomicBoolean(false)
    private val loggedVwUdsSuccess = AtomicBoolean(false)
    private val loggedVwUdsFail = AtomicBoolean(false)

    /**
     * Set when header restore after UDS fails critically.
     * If Mode 01 then times out, force session re-init (avoid permanent bus stuck on 714).
     */
    @Volatile
    private var udsHeaderRestoreFailed = false

    private val parser = Elm327Parser()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()

    private lateinit var appContext: Context
    private lateinit var settings: AppSettings

    @Volatile
    private var isInitialized = false

    private val _pidValues = MutableStateFlow<Map<String, Double>>(emptyMap())
    val pidValues: StateFlow<Map<String, Double>> = _pidValues.asStateFlow()

    private val _ecuConnected = MutableStateFlow(false)
    val ecuConnected: StateFlow<Boolean> = _ecuConnected.asStateFlow()

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
    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    private val responseLock = Any()
    private val responseBuffer = StringBuilder()
    private var responseDeferred: CompletableDeferred<String>? = null

    private val connecting = AtomicBoolean(false)
    private val sessionReady = AtomicBoolean(false)
    private val scanning = AtomicBoolean(false)

    private var pollJob: Job? = null
    private var reconnectJob: Job? = null
    private var scanTimeoutJob: Job? = null
    private var connectCompletion: CompletableDeferred<Boolean>? = null

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
            protocol = runCatching { ObdProtocol.valueOf(settings.obdProtocol) }
                .getOrDefault(ObdProtocol.DEFAULT)

            isInitialized = true
            _connectionStatus.value = when {
                bluetoothAdapter == null -> "Bluetooth not available"
                selectedAddress != null -> "Ready (saved ${selectedName ?: selectedAddress})"
                else -> "Ready (no device selected)"
            }
            logI(
                "Manager initialized; device=${selectedAddress ?: "none"}, " +
                    "protocol=${protocol.name}"
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

        @SuppressLint("MissingPermission")
        try {
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                scanning.set(false)
                setStatus("BLE scanner unavailable")
                return
            }
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, scanCallback)
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
        if (!scanning.getAndSet(false)) return
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        @SuppressLint("MissingPermission")
        try {
            if (hasScanPermission()) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stopScan failed", t)
        }
        val count = _scannedDevices.value.size
        if (gatt == null && !sessionReady.get()) {
            setStatus(if (count > 0) "Scan done ($count devices)" else "Scan done (no devices)")
        }
    }

    fun selectDevice(address: String, name: String?) {
        ensureInit()
        selectedAddress = address
        selectedName = name
        settings.bleDeviceAddress = address
        settings.bleDeviceName = name.orEmpty()
        setStatus("Selected ${name ?: address}")
        Log.i(TAG, "Selected device $address ($name)")
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

    fun connect() {
        ensureInit()
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
        if (sessionReady.get() && gatt != null) {
            Log.d(TAG, "Already connected")
            return
        }
        if (!connecting.compareAndSet(false, true)) {
            Log.d(TAG, "Connect already in progress")
            return
        }

        stopScan()
        scope.launch {
            try {
                logI("Connect attempt ${selectedName ?: address} ($address)")
                setStatus("Connecting to ${selectedName ?: address}…")
                _ecuConnected.value = false
                sessionReady.set(false)
                loggedFirstPollOk.set(false)

                closeGattInternal()

                val device = adapter.getRemoteDevice(address)
                val completed = CompletableDeferred<Boolean>()
                connectCompletion = completed

                @SuppressLint("MissingPermission")
                val newGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    @Suppress("DEPRECATION")
                    device.connectGatt(appContext, false, gattCallback)
                }
                gatt = newGatt

                val ok = withTimeoutOrNull(15_000L) { completed.await() } == true
                if (!ok) {
                    logW("Connect timeout (GATT/notify setup)")
                    setStatus("Connect timeout")
                    closeGattInternal()
                    connecting.set(false)
                    return@launch
                }

                setStatus("Initializing ELM…")
                logI("Initializing ELM…")
                val initOk = runInitSequence()
                if (!initOk) {
                    // Status already set with failed step inside runInitSequence when known
                    if (!_connectionStatus.value.startsWith("ELM init failed")) {
                        setStatus("ELM init failed")
                    }
                    logW("Reconnect will retry (init failed)")
                    _ecuConnected.value = false
                    closeGattInternal()
                    connecting.set(false)
                    return@launch
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
                closeGattInternal()
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
            _pidValues.value = emptyMap()
            resetPidDiscoveryState()
            closeGattInternal()
            connecting.set(false)
            loggedFirstPollOk.set(false)
            setStatus("Disconnected")
            logI("Disconnected")
        }
    }

    /** Retry last saved device when not connected (every ~7s). */
    fun startAutoReconnect() {
        ensureInit()
        if (reconnectJob?.isActive == true) return
        logI("Auto-reconnect loop started")
        reconnectJob = scope.launch {
            while (isActive) {
                try {
                    val address = selectedAddress
                    val adapter = bluetoothAdapter
                    val canTry = !address.isNullOrBlank() &&
                        adapter != null &&
                        adapter.isEnabled &&
                        hasConnectPermission() &&
                        !sessionReady.get() &&
                        !connecting.get() &&
                        gatt == null

                    if (canTry) {
                        logI("Auto-reconnect → $address")
                        connect()
                    } else if (!hasConnectPermission() && !address.isNullOrBlank()) {
                        // Avoid spamming; status already set on manual connect.
                    }
                } catch (t: Throwable) {
                    logW("Auto-reconnect loop error: ${t.message}")
                    Log.w(TAG, "Auto-reconnect loop error", t)
                }
                delay(RECONNECT_INTERVAL_MS)
            }
        }
    }

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

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val address = device.address ?: return
            val name = try {
                device.name ?: result.scanRecord?.deviceName
            } catch (_: SecurityException) {
                result.scanRecord?.deviceName
            }
            val info = BleDeviceInfo(name = name, address = address)
            scanResults[address] = info
            _scannedDevices.value = scanResults.values.sortedBy { it.name ?: it.address }
        }

        override fun onScanFailed(errorCode: Int) {
            scanning.set(false)
            setStatus("Scan failed (code $errorCode)")
            Log.w(TAG, "Scan failed: $errorCode")
        }
    }

    @Suppress("DEPRECATION")
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            val stateLabel = when (newState) {
                BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
                BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
                BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
                BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
                else -> "state=$newState"
            }
            logI("GATT $stateLabel status=$status")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                setStatus("GATT connected, discovering…")
                if (hasConnectPermission()) {
                    g.discoverServices()
                } else {
                    logW("GATT connected but missing BLUETOOTH_CONNECT")
                    connectCompletion?.complete(false)
                    connectCompletion = null
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                val wasReady = sessionReady.getAndSet(false)
                stopPollLoop()
                _ecuConnected.value = false
                loggedFirstPollOk.set(false)
                writeChar = null
                notifyChar = null
                failPendingResponse("disconnected")
                if (connecting.get()) {
                    logW("GATT dropped during connect (status=$status)")
                    connectCompletion?.complete(false)
                    connectCompletion = null
                    connecting.set(false)
                }
                if (gatt === g) {
                    try {
                        if (hasConnectPermission()) g.close()
                    } catch (_: Throwable) {
                    }
                    gatt = null
                }
                if (wasReady) {
                    setStatus("Disconnected from adapter")
                    logW("Disconnected from adapter (status=$status) — will auto-retry")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logE("Service discovery failed: $status")
                connectCompletion?.complete(false)
                connectCompletion = null
                return
            }
            val found = selectUartCharacteristics(g)
            if (!found) {
                logE("No UART characteristic found on adapter")
                setStatus("No UART characteristic found")
                connectCompletion?.complete(false)
                connectCompletion = null
                return
            }
            enableNotifications(g, notifyChar!!)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            onNotifyBytes(value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onNotifyBytes(value)
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid == CCCD) {
                val ok = status == BluetoothGatt.GATT_SUCCESS
                if (ok) {
                    logI("Notify enabled (CCCD ok)")
                } else {
                    logE("Notify enable failed (CCCD status=$status)")
                    setStatus("Notify enable failed")
                }
                connectCompletion?.complete(ok)
                connectCompletion = null
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logW("Characteristic write failed: status=$status")
            }
        }
    }

    private fun selectUartCharacteristics(g: BluetoothGatt): Boolean {
        // Prefer Nordic UART
        val nordic = g.getService(NORDIC_UART_SERVICE)
        if (nordic != null) {
            val rx = nordic.getCharacteristic(NORDIC_UART_RX)
            val tx = nordic.getCharacteristic(NORDIC_UART_TX)
            if (rx != null && tx != null) {
                writeChar = rx
                notifyChar = tx
                logI("UART: Nordic NUS")
                return true
            }
        }

        // FFE0 / FFE1 style
        val ffe0 = g.getService(FFE0_SERVICE)
        if (ffe0 != null) {
            val ffe1 = ffe0.getCharacteristic(FFE1_CHAR)
            if (ffe1 != null && isWritable(ffe1) && canNotify(ffe1)) {
                writeChar = ffe1
                notifyChar = ffe1
                logI("UART: FFE0/FFE1")
                return true
            }
            // Sometimes separate chars under FFE0
            val writable = ffe0.characteristics?.firstOrNull { isWritable(it) }
            val notifiable = ffe0.characteristics?.firstOrNull { canNotify(it) }
            if (writable != null && notifiable != null) {
                writeChar = writable
                notifyChar = notifiable
                logI("UART: FFE0 writable+notify pair")
                return true
            }
        }

        // Generic fallback: first service with writable + notify pair
        for (service in g.services.orEmpty()) {
            val chars = service.characteristics.orEmpty()
            val writable = chars.firstOrNull { isWritable(it) }
            val notifiable = chars.firstOrNull { canNotify(it) }
            if (writable != null && notifiable != null) {
                writeChar = writable
                notifyChar = notifiable
                logI("UART: fallback on ${service.uuid}")
                return true
            }
        }
        return false
    }

    private fun isWritable(c: BluetoothGattCharacteristic): Boolean {
        val p = c.properties
        return (p and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
            (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
    }

    private fun canNotify(c: BluetoothGattCharacteristic): Boolean {
        val p = c.properties
        return (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
            (p and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        if (!hasConnectPermission()) {
            connectCompletion?.complete(false)
            connectCompletion = null
            return
        }
        g.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD)
        if (descriptor == null) {
            // Some stacks still deliver notifications without CCCD write
            logW("No CCCD; assuming notifications active")
            connectCompletion?.complete(true)
            connectCompletion = null
            return
        }
        val enableValue =
            if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = g.writeDescriptor(descriptor, enableValue)
            if (result != BluetoothStatusCodes.SUCCESS) {
                logW("writeDescriptor returned $result; trying legacy path")
                // Fall through — older path may still work on some devices
                @Suppress("DEPRECATION")
                descriptor.value = enableValue
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = enableValue
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    private fun onNotifyBytes(value: ByteArray) {
        val chunk = try {
            String(value, Charset.forName(CHARSET_NAME))
        } catch (_: Throwable) {
            String(value)
        }
        synchronized(responseLock) {
            responseBuffer.append(chunk)
            val current = responseBuffer.toString()
            if (current.contains('>')) {
                val complete = current
                responseBuffer.setLength(0)
                val deferred = responseDeferred
                responseDeferred = null
                deferred?.complete(complete)
            }
        }
    }

    private fun failPendingResponse(reason: String) {
        synchronized(responseLock) {
            responseBuffer.setLength(0)
            val deferred = responseDeferred
            responseDeferred = null
            deferred?.completeExceptionally(IllegalStateException(reason))
        }
    }

    private suspend fun runInitSequence(): Boolean {
        return try {
            resetPidDiscoveryState()
            // Clear noise after link-up; adapters often need a beat after GATT notify.
            delay(500)
            drainBuffer()

            val baseSteps = listOf(
                "ATZ" to ATZ_TIMEOUT_MS,
                "ATE0" to COMMAND_TIMEOUT_MS,
                "ATL0" to COMMAND_TIMEOUT_MS,
                "ATS0" to COMMAND_TIMEOUT_MS,
                "ATH0" to COMMAND_TIMEOUT_MS,
                "ATAL" to COMMAND_TIMEOUT_MS, // long/multi-frame (e.g. odometer A6)
                "ATAT1" to COMMAND_TIMEOUT_MS,
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

            // Preferred protocol, then ATSP0 fallback if ECU never answers 0100.
            val protocolsToTry = buildList {
                add(protocol.atspCommand)
                if (!protocol.atspCommand.equals("ATSP0", ignoreCase = true)) {
                    add("ATSP0")
                }
            }.distinct()

            var ecuRaw: String? = null
            for ((index, atsp) in protocolsToTry.withIndex()) {
                val sp = sendCommandLogged(atsp, COMMAND_TIMEOUT_MS, isInit = true)
                if (sp == null) {
                    logW("Protocol $atsp failed to respond")
                    continue
                }
                delay(400)
                ecuRaw = probeEcuSupportedPids()
                if (ecuRaw != null) {
                    if (index > 0) logI("ECU contact OK after fallback protocol $atsp")
                    break
                }
                logW("No ECU data after $atsp; trying next protocol if any")
            }

            if (ecuRaw == null) {
                logE("ELM init failed at 0100 (no ECU response)")
                setStatus("ELM init failed (0100)")
                _ecuConnected.value = false
                return false
            }

            discoverSupportedPids(first0100 = ecuRaw)
            _ecuConnected.value = true
            logI(
                "ELM init OK — mode01 support=${supportedMode01Pids.size} " +
                    "hot=${HOT_PIDS.size} slow=${SLOW_PIDS.size}",
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

    /** Retry 0100 a few times; return raw response when ECU answers with a 41 bitmap. */
    private suspend fun probeEcuSupportedPids(): String? {
        repeat(3) { attempt ->
            val raw = sendCommandLogged("0100", ECU_PROBE_TIMEOUT_MS, isInit = true)
            if (isSuccessfulMode01Response(raw, expectPid = 0x00)) {
                return raw
            }
            logW("0100 probe attempt ${attempt + 1}/3 failed: ${raw?.take(80) ?: "null"}")
            delay(350)
            drainBuffer()
        }
        return null
    }

    private fun isSuccessfulMode01Response(raw: String?, expectPid: Int): Boolean {
        if (raw == null) return false
        val u = raw.uppercase()
        if (u.contains("NO DATA") || u.contains("UNABLE") || u.contains("ERROR") || u.contains("?")) {
            return false
        }
        val marker = "41" + expectPid.toString(16).uppercase().padStart(2, '0')
        return u.replace(" ", "").contains(marker)
    }

    private suspend fun discoverSupportedPids(first0100: String) {
        val all = linkedSetOf<Int>()
        all += PidSupport.parseSupportBitmap(0x00, first0100)
        var next = PidSupport.nextSupportCommandPid(0x00, first0100)
        var guard = 0
        while (next != null && guard < 8) {
            guard += 1
            val cmdPid = next
            val cmd = "01" + cmdPid.toString(16).uppercase().padStart(2, '0')
            val raw = sendCommandLogged(cmd, ECU_PROBE_TIMEOUT_MS, isInit = true)
            if (!isSuccessfulMode01Response(raw, expectPid = cmdPid) || raw == null) {
                logW("Support query $cmd failed; stopping discovery")
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
        lastVwOdoAtMs = 0L
        loggedVwUdsStart.set(false)
        loggedVwUdsSuccess.set(false)
        loggedVwUdsFail.set(false)
        udsHeaderRestoreFailed = false
    }

    /** Log first miss per PID per session; never disable or clear last-good values. */
    private fun notePidMiss(pid: String, rawHint: String?) {
        if (!noDataLogged.add(pid)) return
        val hint = rawHint?.replace('\n', ' ')?.trim()?.take(80) ?: "empty/timeout"
        logW("PID $pid miss ($hint) — holding last-good if any")
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
            null
        }
    }

    private fun drainBuffer() {
        synchronized(responseLock) {
            responseBuffer.setLength(0)
        }
    }

    private suspend fun sendCommand(cmd: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): String {
        return commandMutex.withLock {
            withContext(Dispatchers.IO) {
                val g = gatt ?: throw IllegalStateException("Not connected")
                val wc = writeChar ?: throw IllegalStateException("No write characteristic")
                if (!hasConnectPermission()) throw SecurityException("Missing BLUETOOTH_CONNECT")

                val deferred = CompletableDeferred<String>()
                synchronized(responseLock) {
                    responseBuffer.setLength(0)
                    responseDeferred = deferred
                }

                val payload = (cmd.trim() + "\r").toByteArray(Charset.forName(CHARSET_NAME))
                val written = writeCharacteristic(g, wc, payload)
                if (!written) {
                    synchronized(responseLock) {
                        responseDeferred = null
                    }
                    throw IllegalStateException("Write failed for $cmd")
                }

                val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
                if (result == null) {
                    synchronized(responseLock) {
                        val partial = responseBuffer.toString()
                        responseBuffer.setLength(0)
                        responseDeferred = null
                        if (partial.isNotBlank()) return@withContext partial
                    }
                    throw IllegalStateException("Timeout waiting for response to $cmd")
                }
                result
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ): Boolean {
        val props = characteristic.properties
        val writeType =
            if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 &&
                (props and BluetoothGattCharacteristic.PROPERTY_WRITE) == 0
            ) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val code = g.writeCharacteristic(characteristic, payload, writeType)
            code == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            g.writeCharacteristic(characteristic)
        }
    }

    private fun recomputeFuelRate(into: MutableMap<String, Double>) {
        val fuelLh = FuelConsumptionCalculator.litersPerHour(
            FuelCalcConfig(
                stoichAfr = settings.fuelStoichAfr,
                densityGl = settings.fuelDensityGl,
                displacementL = settings.engineDisplacementL,
                ve = settings.engineVe,
            ),
            FuelCalcSensors(
                mafGs = into["10"],
                lambda = into["44"],
                mapKpa = into["0b"],
                rpm = into["0c"],
                iatC = into["0f"],
            ),
        )
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
                )
                try {
                    for (pid in pids) {
                        if (!sessionReady.get()) break
                        val cmd = "01${pid.uppercase()}"
                        val raw = sendCommandLogged(cmd, isInit = false)
                        if (raw == null) {
                            if (!sessionReady.get() || gatt == null) break
                            if (udsHeaderRestoreFailed) {
                                logE(
                                    "Mode 01 timeout after UDS header restore failure " +
                                        "— forcing session re-init",
                                )
                                sessionReady.set(false)
                                _ecuConnected.value = false
                                setStatus("OBD headers unhealthy — reconnecting")
                                // Drop GATT so auto-reconnect can re-run full ELM init.
                                closeGattInternal()
                                break
                            }
                            notePidMiss(pid, null)
                            continue
                        }
                        val value = parser.decodePid(pid, raw)
                        if (value == null) {
                            notePidMiss(pid, raw)
                            continue
                        }
                        if (!isValidPidValue(pid, value)) {
                            logW("Invalid value for PID $pid: $value")
                            continue
                        }

                        val updated = LinkedHashMap(
                            PidPollPolicy.afterSuccess(_pidValues.value, pid, value),
                        )
                        val fuelInputs = setOf("10", "44", "0b", "0c", "0f")
                        if (pid in fuelInputs) {
                            recomputeFuelRate(updated)
                        }
                        _pidValues.value = updated.toMap()
                        pidsOkWindow++

                        if (!_ecuConnected.value) {
                            _ecuConnected.value = true
                            setStatus("ECU online")
                        }
                        if (loggedFirstPollOk.compareAndSet(false, true)) {
                            logI(
                                "Poll started (hot=${HOT_PIDS.size}, " +
                                    "slow=${SLOW_PIDS.size}, slowEvery=$SLOW_EVERY)",
                            )
                        }
                    }
                } catch (t: Throwable) {
                    logW("Poll cycle error: ${t.message}")
                    Log.w(TAG, "Poll cycle error", t)
                }

                if (sessionReady.get()) {
                    maybePollVwOdometer()
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
     * VW MQB only: rare UDS ReadDID to instrument cluster for dash odometer.
     * Serialized via [sendCommand] mutex; always restores OBD headers afterward.
     */
    private suspend fun maybePollVwOdometer() {
        if (!sessionReady.get() || gatt == null) return
        val profile = VehicleObdProfile.fromName(settings.vehicleObdProfile)
        if (profile != VehicleObdProfile.VwMqb) return

        val now = System.currentTimeMillis()
        if (lastVwOdoAtMs != 0L && now - lastVwOdoAtMs < VW_ODO_INTERVAL_MS) return
        lastVwOdoAtMs = now

        pollVwOdometerOnce()
    }

    private suspend fun pollVwOdometerOnce() {
        if (loggedVwUdsStart.compareAndSet(false, true)) {
            logI("VW MQB UDS odometer probe (ATSH714 / service 22)")
        }
        var gotReading = false
        try {
            // Physical addressing to instrument cluster (11-bit CAN).
            val sh = sendCommandLogged("ATSH714", COMMAND_TIMEOUT_MS, isInit = false)
            if (sh == null) {
                if (loggedVwUdsFail.compareAndSet(false, true)) {
                    logW("VW UDS: ATSH714 failed/timeout")
                }
                return
            }
            // Best-effort receive filter / flow control; ignore adapter rejections.
            sendCommandLogged("ATCRA77E", COMMAND_TIMEOUT_MS, isInit = false)
            sendCommandLogged("ATFCSM1", COMMAND_TIMEOUT_MS, isInit = false)

            val dids = sessionWinningDid?.let { listOf(it) }
                ?: UdsReadDid.candidateDids(settings.vwOdometerDid)

            for (did in dids) {
                if (!sessionReady.get() || gatt == null) break
                val cmd = "22" + did.toString(16).uppercase().padStart(4, '0')
                val raw = sendCommandLogged(cmd, UDS_COMMAND_TIMEOUT_MS, isInit = false)
                    ?: continue
                val payload = UdsReadDid.parsePositiveReadDid(raw, did) ?: continue
                val km = UdsReadDid.decodeOdometerKm(payload) ?: continue
                if (!isValidPidValue(OdometerReading.UDS_KM_KEY, km)) {
                    logW("VW UDS: invalid odometer decode $km for DID ${did.toString(16)}")
                    continue
                }

                val updated = LinkedHashMap(
                    PidPollPolicy.afterSuccess(
                        _pidValues.value,
                        OdometerReading.UDS_KM_KEY,
                        km,
                    ),
                )
                _pidValues.value = updated.toMap()
                sessionWinningDid = did
                gotReading = true
                if (loggedVwUdsSuccess.compareAndSet(false, true)) {
                    logI(
                        "VW UDS odometer OK: DID=${did.toString(16).uppercase().padStart(4, '0')} " +
                            "km=$km",
                    )
                }
                break
            }

            if (!gotReading && loggedVwUdsFail.compareAndSet(false, true)) {
                logW("VW UDS odometer: no positive DID response this session (yet)")
            }
        } catch (t: Throwable) {
            if (loggedVwUdsFail.compareAndSet(false, true)) {
                logW("VW UDS odometer error: ${t.message}")
            }
            Log.w(TAG, "pollVwOdometerOnce", t)
        } finally {
            restoreObdHeaders()
        }
    }

    /**
     * Restore functional OBD-II addressing after a UDS cluster probe.
     * Must always run after [pollVwOdometerOnce] header switch.
     */
    private suspend fun restoreObdHeaders() {
        // Clear any CAN receive address filter, then restore functional header 7DF.
        val atar = sendCommandLogged("ATAR", COMMAND_TIMEOUT_MS, isInit = false)
        val atsh = sendCommandLogged("ATSH7DF", COMMAND_TIMEOUT_MS, isInit = false)

        val atarOk = atar != null && !isElmErrorResponse(atar)
        val atshOk = atsh != null && !isElmErrorResponse(atsh)

        if (atarOk || atshOk) {
            if (udsHeaderRestoreFailed) {
                udsHeaderRestoreFailed = false
                logI("OBD headers restored after prior UDS restore failure")
            }
            return
        }

        udsHeaderRestoreFailed = true
        logE(
            "UDS header restore failed (ATAR/ATSH7DF) — " +
                "Mode 01 may be unhealthy until reconnect",
        )
    }

    private fun isElmErrorResponse(raw: String): Boolean {
        val u = raw.uppercase()
        return u.contains("ERROR") || u.contains("UNABLE") ||
            u.contains("BUS INIT") || u.contains("STOPPED") ||
            (u.contains("?") && !u.contains("NO DATA") && !u.contains("OK"))
    }

    private fun stopPollLoop() {
        pollJob?.cancel()
        pollJob = null
    }

    @SuppressLint("MissingPermission")
    private fun closeGattInternal() {
        val g = gatt
        gatt = null
        writeChar = null
        notifyChar = null
        failPendingResponse("closed")
        if (g != null) {
            try {
                if (hasConnectPermission()) {
                    g.disconnect()
                    g.close()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "closeGatt failed", t)
            }
        }
    }

    private fun isValidPidValue(pid: String, value: Double): Boolean {
        if (value.isNaN() || value.isInfinite()) return false
        return when (pid) {
            "0d" -> value in 0.0..400.0
            "0c" -> value in 0.0..16383.0
            "2f" -> value in 0.0..100.0
            "04", "43", "45", "49" -> value in 0.0..100.0
            "10" -> value in 0.0..655.35
            "ff125a" -> value in 0.0..200.0
            "42" -> value in 0.0..20.0
            "a6", OdometerReading.UDS_KM_KEY -> value in 0.0..2_000_000.0
            "31" -> value in 0.0..65535.0
            else -> true
        }
    }

    private fun uuid(s: String): UUID = UUID.fromString(s)
}

// Local alias so we can reference BluetoothStatusCodes without API lint noise on older compile paths
private object BluetoothStatusCodes {
    const val SUCCESS = 0
}
