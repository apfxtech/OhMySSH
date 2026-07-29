package com.example.ohmyssh.serial

import com.example.ohmyssh.data.SerialDevice
import com.example.ohmyssh.session.SessionError

actual fun createSerialScanner(): SerialScanner = UnsupportedSerialScanner

private object UnsupportedSerialScanner : SerialScanner {
    override val isSupported: Boolean = false

    override suspend fun scan(): List<SerialPortInfo> = emptyList()
}

actual suspend fun openSerialLink(device: SerialDevice, port: SerialPortInfo): SerialLink =
    throw SessionError("Serial ports are not reachable on iOS")
