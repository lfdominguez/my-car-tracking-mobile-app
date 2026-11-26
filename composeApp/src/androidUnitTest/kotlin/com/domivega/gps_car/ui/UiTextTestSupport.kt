package com.domivega.gps_car.ui

/** Resource key of a [UiText] (resources need an Android context to render). */
val UiText.key: String
    get() = when (this) {
        is UiText.Plain -> ""
        is UiText.Res -> res.key
        is UiText.Plural -> res.key
    }

/** Format arguments, nested [UiText.Plain] unwrapped. */
val UiText.argStrings: List<String>
    get() = when (this) {
        is UiText.Plain -> listOf(value)
        is UiText.Res -> args.map { (it as? UiText.Plain)?.value ?: it.toString() }
        is UiText.Plural -> args.map { (it as? UiText.Plain)?.value ?: it.toString() }
    }
