package com.example.ohmyssh.services

import platform.Foundation.NSLog

internal actual fun writeStderrLine(line: String) {
    NSLog("%@", line)
}
