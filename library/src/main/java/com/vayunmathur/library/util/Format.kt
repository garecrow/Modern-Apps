package com.vayunmathur.library.util

import java.text.NumberFormat
import java.util.Locale


fun Double.toStringDigits(digits: Int): String {
    return "%.${digits}f".format(this)
}

fun Long.toStringCommas(): String {
    return NumberFormat.getInstance(Locale.US).format(this)
}