package com.nars.maplibre.utils

fun Double.formatDecimal(digits: Int) = "%.${digits}f".format(this)
