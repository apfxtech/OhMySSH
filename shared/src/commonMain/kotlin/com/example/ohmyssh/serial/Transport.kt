package com.example.ohmyssh.serial

import com.example.ohmyssh.data.SerialDevice
import kotlinx.coroutines.flow.Flow

interface SerialLink {
    val input: Flow<ByteArray>

    fun write(data: ByteArray)

    suspend fun close()
}

interface SerialScanner {
    val isSupported: Boolean

    suspend fun scan(): List<SerialPortInfo>

    val hasHotplugEvents: Boolean get() = false

    fun watchHotplug(onEvent: () -> Unit): AutoCloseable? = null

    fun nodeExists(path: String): Boolean = false
}

expect fun createSerialScanner(): SerialScanner

expect suspend fun openSerialLink(device: SerialDevice, port: SerialPortInfo): SerialLink
