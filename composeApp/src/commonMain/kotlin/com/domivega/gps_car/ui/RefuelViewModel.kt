package com.domivega.gps_car.ui

import com.domivega.gps_car.generated.resources.Res
import com.domivega.gps_car.generated.resources.msg_refuel_reading
import com.domivega.gps_car.generated.resources.msg_refuel_check
import com.domivega.gps_car.generated.resources.msg_refuel_manual
import com.domivega.gps_car.generated.resources.msg_refuel_read_failed
import com.domivega.gps_car.generated.resources.msg_refuel_invalid
import com.domivega.gps_car.generated.resources.common_saving
import com.domivega.gps_car.generated.resources.msg_refuel_saved
import com.domivega.gps_car.generated.resources.msg_refuel_not_saved

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
        _uiState.update { transform(it).copy(message = UiText.Empty, isError = false) }
    }

    fun readTicket(photoJpeg: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(reading = true, message = UiText.Res(Res.string.msg_refuel_reading), isError = false) }
            val outcome = runCatching { backend.readTicket(photoJpeg) }
                .getOrElse { TicketReadOutcome.Failed(it.message ?: "read failed") }
            _uiState.update {
                when (outcome) {
                    is TicketReadOutcome.Suggested -> it.withSuggestion(outcome.suggestion).copy(
                        receiptText = outcome.text,
                        message = UiText.Res(Res.string.msg_refuel_check),
                        isError = false,
                    )
                    is TicketReadOutcome.NotAvailable -> it.copy(
                        message = UiText.Res(Res.string.msg_refuel_manual, listOf(outcome.message)),
                        isError = true,
                    )
                    is TicketReadOutcome.Failed -> it.copy(
                        message = UiText.Res(Res.string.msg_refuel_read_failed, listOf(outcome.detail)),
                        isError = true,
                    )
                }.copy(reading = false)
            }
        }
    }

    fun photoFailed(detail: String) {
        _uiState.update { it.copy(message = UiText.Plain(detail), isError = true) }
    }

    fun save() {
        val entry = _uiState.value.toEntry().getOrElse { e ->
            val text = (e as? RefuelUiState.InvalidEntry)?.text
                ?: e.message?.let { UiText.Plain(it) }
                ?: UiText.Res(Res.string.msg_refuel_invalid)
            _uiState.update { it.copy(message = text, isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = UiText.Res(Res.string.common_saving), isError = false) }
            val result = runCatching { backend.save(entry).getOrThrow() }
            _uiState.update {
                result.fold(
                    onSuccess = { _ ->
                        RefuelUiState(filledAt = nowLocal(), unit = it.unit, message = UiText.Res(Res.string.msg_refuel_saved))
                    },
                    onFailure = { e ->
                        it.copy(saving = false, message = UiText.Res(Res.string.msg_refuel_not_saved, listOf(e.message.orEmpty())), isError = true)
                    },
                )
            }
        }
    }
}
