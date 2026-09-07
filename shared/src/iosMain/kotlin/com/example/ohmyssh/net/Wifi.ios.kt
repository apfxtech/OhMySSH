package com.example.ohmyssh.net

// iOS hands out the SSID only to an app carrying the hotspot entitlement, and
// the kind only through NWPathMonitor, neither of which is wired up yet.
actual suspend fun detectNetworkLink(adapter: LanInterface?): NetworkLink =
    NetworkLink(NetworkKind.UNKNOWN)

actual fun ssidAccess(): SsidAccess = SsidAccess.NOT_NEEDED

actual suspend fun requestSsidAccess(timeoutMs: Long): SsidAccess = SsidAccess.NOT_NEEDED
