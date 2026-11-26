package com.domivega.gps_car.ui

import androidx.compose.runtime.Composable
import com.domivega.gps_car.fuel.FuelClass
import com.domivega.gps_car.fuel.FuelTypePreset
import com.domivega.gps_car.generated.resources.Res
import com.domivega.gps_car.generated.resources.fuel_class_diesel
import com.domivega.gps_car.generated.resources.fuel_class_electric
import com.domivega.gps_car.generated.resources.fuel_class_gasoline
import com.domivega.gps_car.generated.resources.fuel_class_hybrid
import com.domivega.gps_car.generated.resources.fuel_type_b7
import com.domivega.gps_car.generated.resources.fuel_type_custom
import com.domivega.gps_car.generated.resources.fuel_type_e0
import com.domivega.gps_car.generated.resources.fuel_type_e100
import com.domivega.gps_car.generated.resources.fuel_type_e27
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * User-facing text produced outside Compose (ViewModels, pure helpers), resolved
 * to the current locale only when shown. Keeps ViewModels free of Android context
 * and unit tests independent of the device language.
 */
sealed interface UiText {
    /** Already final text, e.g. a server error message. */
    data class Plain(val value: String) : UiText

    /** A string resource; [args] may themselves be [UiText]. */
    data class Res(val res: StringResource, val args: List<Any> = emptyList()) : UiText

    /** A plural resource selected by [count]; [args] fill the format. */
    data class Plural(val res: PluralStringResource, val count: Int, val args: List<Any> = emptyList()) : UiText

    fun isBlank(): Boolean = this is Plain && value.isBlank()

    companion object {
        val Empty: UiText = Plain("")
    }
}

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Res -> stringResource(res, *args.map { it.resolveArg() }.toTypedArray())
    is UiText.Plural -> pluralStringResource(res, count, *args.map { it.resolveArg() }.toTypedArray())
}

/** Resolve outside composition (toasts, callbacks). */
suspend fun UiText.resolveAsync(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Res -> getString(res, *args.map { it.resolveArgAsync() }.toTypedArray())
    is UiText.Plural -> getPluralString(res, count, *args.map { it.resolveArgAsync() }.toTypedArray())
}

private suspend fun Any.resolveArgAsync(): Any = if (this is UiText) resolveAsync() else this

@Composable
private fun Any.resolveArg(): Any = if (this is UiText) resolve() else this

@Composable
fun FuelClass.label(): String = when (this) {
    FuelClass.GASOLINE -> stringResource(Res.string.fuel_class_gasoline)
    FuelClass.DIESEL -> stringResource(Res.string.fuel_class_diesel)
    FuelClass.HYBRID -> stringResource(Res.string.fuel_class_hybrid)
    FuelClass.FULL_ELECTRIC -> stringResource(Res.string.fuel_class_electric)
}

@Composable
fun FuelTypePreset.label(): String = when (this) {
    FuelTypePreset.E0 -> stringResource(Res.string.fuel_type_e0)
    FuelTypePreset.E27 -> stringResource(Res.string.fuel_type_e27)
    FuelTypePreset.E100 -> stringResource(Res.string.fuel_type_e100)
    FuelTypePreset.B7 -> stringResource(Res.string.fuel_type_b7)
    FuelTypePreset.CUSTOM -> stringResource(Res.string.fuel_type_custom)
    else -> displayName
}
