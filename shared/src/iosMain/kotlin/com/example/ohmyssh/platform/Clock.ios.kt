package com.example.ohmyssh.platform

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.localTimeZone

actual fun localOffsetSeconds(epochMillis: Long): Int {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    return NSTimeZone.localTimeZone.secondsFromGMTForDate(date).toInt()
}
