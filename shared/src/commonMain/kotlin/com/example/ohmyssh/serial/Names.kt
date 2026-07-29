package com.example.ohmyssh.serial

fun serialPortName(path: String): String {
    val cut = path.lastIndexOf('/')
    return if (cut < 0) path else path.substring(cut + 1)
}

fun formatUsbId(id: Int): String = id.toString(16).padStart(4, '0')
