package com.nars.maplibre.utils

import java.util.Locale

fun Double.formatDecimal(digits: Int) = String.format(Locale.US, "%.${digits}f", this)
