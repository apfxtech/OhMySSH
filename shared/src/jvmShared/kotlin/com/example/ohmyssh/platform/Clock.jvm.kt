package com.example.ohmyssh.platform

import java.util.TimeZone

actual fun localOffsetSeconds(epochMillis: Long): Int =
    TimeZone.getDefault().getOffset(epochMillis) / 1000
