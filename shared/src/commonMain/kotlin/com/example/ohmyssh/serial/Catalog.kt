package com.example.ohmyssh.serial

enum class SerialKind(val label: String, val noun: String) {
    BRIDGE("USB-UART bridge", "USB-UART"),
    BOARD("Microcontroller", "board"),
    PROBE("Debug probe", "probe"),
    CDC("USB serial device", "USB serial"),
    MODEM("Modem", "modem"),
    BLUETOOTH("Bluetooth serial", "RFCOMM"),
    UART("Board UART", "UART"),
    ONBOARD("Motherboard port", "legacy port"),
    VIRTUAL("Virtual port", "virtual port"),
    UNKNOWN("Serial port", "port");

    val isExternal: Boolean
        get() = when (this) {
            BRIDGE, BOARD, PROBE, CDC, MODEM, BLUETOOTH, UART -> true
            ONBOARD, VIRTUAL, UNKNOWN -> false
        }
}

class UsbVendor(
    val vendorName: String,
    val kind: SerialKind,
    val products: Map<Int, String> = emptyMap(),
)

val kUsbSerialVendors: Map<Int, UsbVendor> = mapOf(
    0x0403 to UsbVendor(
        "FTDI",
        SerialKind.BRIDGE,
        mapOf(
            0x6001 to "FT232R",
            0x6010 to "FT2232",
            0x6011 to "FT4232H",
            0x6014 to "FT232H",
            0x6015 to "FT231X",
            0x601C to "FT4222H",
        ),
    ),
    0x10C4 to UsbVendor(
        "Silicon Labs",
        SerialKind.BRIDGE,
        mapOf(
            0xEA60 to "CP2102",
            0xEA61 to "CP2102",
            0xEA63 to "CP2102N",
            0xEA70 to "CP2105",
            0xEA71 to "CP2108",
        ),
    ),
    0x1A86 to UsbVendor(
        "WCH",
        SerialKind.BRIDGE,
        mapOf(
            0x5523 to "CH341",
            0x7522 to "CH340K",
            0x7523 to "CH340",
            0x55D3 to "CH343",
            0x55D4 to "CH9102",
        ),
    ),
    0x4348 to UsbVendor("WCH", SerialKind.BRIDGE, mapOf(0x5523 to "CH341")),
    0x067B to UsbVendor(
        "Prolific",
        SerialKind.BRIDGE,
        mapOf(
            0x2303 to "PL2303",
            0x23A3 to "PL2303G",
            0x23C3 to "PL2303G",
            0x23D3 to "PL2303G",
            0x23E3 to "PL2303G",
            0x23F3 to "PL2303G",
        ),
    ),
    0x04E2 to UsbVendor(
        "Exar",
        SerialKind.BRIDGE,
        mapOf(
            0x1410 to "XR21V1410",
            0x1411 to "XR21V1411",
            0x1412 to "XR21V1412",
            0x1414 to "XR21V1414",
        ),
    ),
    0x9710 to UsbVendor("MosChip", SerialKind.BRIDGE, mapOf(0x7715 to "MCS7715", 0x7820 to "MCS7820", 0x7840 to "MCS7840")),
    0x0557 to UsbVendor("ATEN", SerialKind.BRIDGE, mapOf(0x2008 to "UC-232A")),
    0x6547 to UsbVendor("Arkmicro", SerialKind.BRIDGE, mapOf(0x0232 to "ARK3116")),
    0x04B4 to UsbVendor("Cypress", SerialKind.BRIDGE, mapOf(0x0003 to "USB-Serial")),
    0x0665 to UsbVendor("Cypress", SerialKind.BRIDGE, mapOf(0x5161 to "UPS adapter")),
    0x04D8 to UsbVendor("Microchip", SerialKind.BRIDGE, mapOf(0x000A to "MCP2200", 0x00DD to "MCP2221", 0x00DE to "MCP2221A")),
    0x0525 to UsbVendor("Linux gadget", SerialKind.CDC, mapOf(0xA4A7 to "gadget serial")),
    0x2341 to UsbVendor(
        "Arduino",
        SerialKind.BOARD,
        mapOf(
            0x0001 to "Uno",
            0x0010 to "Mega 2560",
            0x0036 to "Leonardo (bootloader)",
            0x003D to "Due",
            0x003E to "Due (native)",
            0x0042 to "Mega 2560 R3",
            0x0043 to "Uno R3",
            0x0058 to "Nano Every",
            0x8036 to "Leonardo",
            0x8057 to "Nano 33 IoT",
            0x805A to "Nano 33 BLE",
        ),
    ),
    0x2A03 to UsbVendor("Arduino", SerialKind.BOARD, mapOf(0x0042 to "Mega 2560", 0x0043 to "Uno", 0x8036 to "Leonardo")),
    0x303A to UsbVendor(
        "Espressif",
        SerialKind.BOARD,
        mapOf(
            0x0002 to "ESP32-S2",
            0x1001 to "ESP32 (USB CDC)",
            0x4001 to "ESP32-C3",
        ),
    ),
    0x2E8A to UsbVendor(
        "Raspberry Pi",
        SerialKind.BOARD,
        mapOf(
            0x0003 to "RP2040 (BOOTSEL)",
            0x0005 to "Pico",
            0x000A to "Pico (CDC)",
            0x000C to "Debug Probe",
        ),
    ),
    0x16C0 to UsbVendor("PJRC", SerialKind.BOARD, mapOf(0x0478 to "Teensy (bootloader)", 0x0483 to "Teensy")),
    0x239A to UsbVendor("Adafruit", SerialKind.BOARD),
    0x1B4F to UsbVendor("SparkFun", SerialKind.BOARD),
    0x2886 to UsbVendor("Seeed", SerialKind.BOARD),
    0x1209 to UsbVendor("pid.codes", SerialKind.BOARD),
    0x2B04 to UsbVendor("Particle", SerialKind.BOARD),
    0x1FFB to UsbVendor("Pololu", SerialKind.BOARD),
    0x1915 to UsbVendor("Nordic", SerialKind.BOARD),
    0x1FC9 to UsbVendor("NXP", SerialKind.BOARD),
    0x03EB to UsbVendor("Atmel", SerialKind.BOARD, mapOf(0x2404 to "EDBG")),
    0x2C99 to UsbVendor("Prusa", SerialKind.BOARD),
    0x1CF1 to UsbVendor("Dresden Elektronik", SerialKind.BOARD, mapOf(0x0030 to "ConBee II")),
    0x0483 to UsbVendor(
        "STMicroelectronics",
        SerialKind.PROBE,
        mapOf(
            0x3748 to "ST-Link/V2",
            0x374B to "ST-Link/V2-1",
            0x374E to "ST-Link/V3",
            0x374F to "ST-Link/V3",
            0x5740 to "STM32 Virtual COM",
        ),
    ),
    0x1366 to UsbVendor("SEGGER", SerialKind.PROBE, mapOf(0x0105 to "J-Link CDC")),
    0x0D28 to UsbVendor("mbed", SerialKind.PROBE, mapOf(0x0204 to "CMSIS-DAP")),
    0x1D50 to UsbVendor("OpenMoko", SerialKind.PROBE, mapOf(0x6015 to "Black Magic GDB", 0x6018 to "Black Magic UART")),
    0x1CBE to UsbVendor("Texas Instruments", SerialKind.PROBE, mapOf(0x00FD to "ICDI")),
    0x0451 to UsbVendor("Texas Instruments", SerialKind.PROBE, mapOf(0xBEF3 to "XDS110")),
    0x2047 to UsbVendor("Texas Instruments", SerialKind.PROBE, mapOf(0x0200 to "MSP430 UART")),
    0x12D1 to UsbVendor("Huawei", SerialKind.MODEM),
    0x19D2 to UsbVendor("ZTE", SerialKind.MODEM),
    0x1E0E to UsbVendor("SIMCom", SerialKind.MODEM),
    0x2C7C to UsbVendor("Quectel", SerialKind.MODEM),
    0x1199 to UsbVendor("Sierra Wireless", SerialKind.MODEM),
    0x1BC7 to UsbVendor("Telit", SerialKind.MODEM),
    0x2CB7 to UsbVendor("Fibocom", SerialKind.MODEM),
    0x1546 to UsbVendor("u-blox", SerialKind.MODEM),
    0x05C6 to UsbVendor("Qualcomm", SerialKind.MODEM),
    0x0F3D to UsbVendor("AirPrime", SerialKind.MODEM),
)

val kSerialDrivers: Map<String, SerialKind> = mapOf(
    "ftdi_sio" to SerialKind.BRIDGE,
    "cp210x" to SerialKind.BRIDGE,
    "ch341" to SerialKind.BRIDGE,
    "ch343" to SerialKind.BRIDGE,
    "ch9344" to SerialKind.BRIDGE,
    "pl2303" to SerialKind.BRIDGE,
    "ark3116" to SerialKind.BRIDGE,
    "f81232" to SerialKind.BRIDGE,
    "f81534" to SerialKind.BRIDGE,
    "mos7840" to SerialKind.BRIDGE,
    "mos7720" to SerialKind.BRIDGE,
    "oti6858" to SerialKind.BRIDGE,
    "spcp8x5" to SerialKind.BRIDGE,
    "ssu100" to SerialKind.BRIDGE,
    "ti_usb_3410_5052" to SerialKind.BRIDGE,
    "upd78f0730" to SerialKind.BRIDGE,
    "xr_serial" to SerialKind.BRIDGE,
    "keyspan" to SerialKind.BRIDGE,
    "whiteheat" to SerialKind.BRIDGE,
    "io_edgeport" to SerialKind.BRIDGE,
    "usbserial_generic" to SerialKind.BRIDGE,
    "cdc_acm" to SerialKind.CDC,
    "option" to SerialKind.MODEM,
    "qcserial" to SerialKind.MODEM,
    "sierra" to SerialKind.MODEM,
    "usb_wwan" to SerialKind.MODEM,
    "cdc_mbim" to SerialKind.MODEM,
    // The 8250 line is the motherboard's own UART; on an SBC the SoC driver
    // has a name of its own, which is why only this one is pinned to onboard.
    "serial8250" to SerialKind.ONBOARD,
)

val kDescriptionHints: Map<String, SerialKind> = mapOf(
    "usb serial" to SerialKind.BRIDGE,
    "usb-serial" to SerialKind.BRIDGE,
    "usb uart" to SerialKind.BRIDGE,
    "usb-uart" to SerialKind.BRIDGE,
    "usb to uart" to SerialKind.BRIDGE,
    "usb2.0-serial" to SerialKind.BRIDGE,
    "ftdi" to SerialKind.BRIDGE,
    "ft232" to SerialKind.BRIDGE,
    "cp210" to SerialKind.BRIDGE,
    "ch340" to SerialKind.BRIDGE,
    "ch341" to SerialKind.BRIDGE,
    "ch9102" to SerialKind.BRIDGE,
    "pl2303" to SerialKind.BRIDGE,
    "prolific" to SerialKind.BRIDGE,
    "silicon labs" to SerialKind.BRIDGE,
    "silabs" to SerialKind.BRIDGE,
    "arduino" to SerialKind.BOARD,
    "esp32" to SerialKind.BOARD,
    "espressif" to SerialKind.BOARD,
    "raspberry pi pico" to SerialKind.BOARD,
    "rp2040" to SerialKind.BOARD,
    "teensy" to SerialKind.BOARD,
    "micro:bit" to SerialKind.BOARD,
    "stm32" to SerialKind.BOARD,
    "st-link" to SerialKind.PROBE,
    "j-link" to SerialKind.PROBE,
    "cmsis-dap" to SerialKind.PROBE,
    "black magic" to SerialKind.PROBE,
    "usb modem" to SerialKind.MODEM,
    "communications port" to SerialKind.ONBOARD,
    "standard serial over bluetooth" to SerialKind.BLUETOOTH,
    "bluetooth" to SerialKind.BLUETOOTH,
)

fun usbVendor(vendorId: Int?): UsbVendor? = vendorId?.let { kUsbSerialVendors[it] }

fun describeUsbDevice(vendorId: Int?, productId: Int?): String? {
    val vendor = usbVendor(vendorId) ?: return null
    val product = productId?.let { vendor.products[it] }
    return "${vendor.vendorName} ${product ?: vendor.kind.noun}"
}

fun kindFromDescription(description: String?): SerialKind? {
    if (description == null) return null
    val haystack = description.lowercase()
    for ((needle, kind) in kDescriptionHints) {
        if (haystack.contains(needle)) return kind
    }
    return null
}
