package com.domivega.gps_car.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.domivega.gps_car.data.FuelLogBackend
import com.domivega.gps_car.data.TicketReadOutcome
import com.domivega.gps_car.ui.state.RefuelUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RefuelViewModel(
    private val backend: FuelLogBackend,
    /** Current local time as `yyyy-MM-dd HH:mm`. */
    private val nowLocal: () -> String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RefuelUiState(filledAt = nowLocal()))
    val uiState: StateFlow<RefuelUiState> = _uiState.asStateFlow()

    /** Defaults for an untouched form: the car's unit and the live OBD odometer. */
    fun prefill(unit: String, odometerKm: Double?) {
        _uiState.update {
            it.copy(
                unit = if (it.quantity.isBlank()) unit else it.unit,
                odometerKm = it.odometerKm.ifBlank { odometerKm?.toLong()?.toString().orEmpty() },
            )
        }
    }

    fun edit(transform: (RefuelUiState) -> RefuelUiState) {
        _uiState.update { transform(it).copy(message = "", isError = false) }
    }

    fun readTicket(photoJpeg: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(reading = true, message = "Reading receipt…", isError = false) }
            val outcome = runCatching { backend.readTicket(photoJpeg) }
                .getOrElse { TicketReadOutcome.Failed(it.message ?: "read failed") }
            _uiState.update {
                when (outcome) {
                    is TicketReadOutcome.Suggested -> it.withSuggestion(outcome.suggestion).copy(
                        receiptText = outcome.text,
                        message = "Check the values against the receipt, then save",
                        isError = false,
                    )
                    is TicketReadOutcome.NotAvailable -> it.copy(
                        message = "${outcome.message}. Enter the values by hand.",
                        isError = true,
                    )
                    is TicketReadOutcome.Failed -> it.copy(
                        message = "Could not read the receipt: ${outcome.detail}",
                        isError = true,
                    )
                }.copy(reading = false)
            }
        }
    }

    fun photoFailed(detail: String) {
        _uiState.update { it.copy(message = detail, isError = true) }
    }

    fun save() {
        val entry = _uiState.value.toEntry().getOrElse { e ->
            _uiState.update { it.copy(message = e.message ?: "Invalid entry", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = "Saving…", isError = false) }
            val result = runCatching { backend.save(entry).getOrThrow() }
            _uiState.update {
                result.fold(
                    onSuccess = { _ ->
                        RefuelUiState(filledAt = nowLocal(), unit = it.unit, message = "Refuel saved")
                    },
                    onFailure = { e ->
                        it.copy(saving = false, message = "Not saved: ${e.message}", isError = true)
                    },
                )
            }
        }
    }
}
