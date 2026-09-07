package com.example.ohmyssh.net

/**
 * What to call [adapter] is, and the Wi-Fi name when the OS will give one up.
 * Never throws and never blocks on a permission prompt: an OS that hides the
 * SSID answers with the kind alone, and the fingerprint carries on without.
 */
expect suspend fun detectNetworkLink(adapter: LanInterface?): NetworkLink

/** Whether this OS will name the Wi-Fi, and whether asking it would help. */
enum class SsidAccess {
    /** Nothing to ask for: this OS either names the network or never will. */
    NOT_NEEDED,

    /** A prompt would decide it — macOS gates the SSID behind Location. */
    ASKABLE,

    GRANTED,

    DENIED,
}

expect fun ssidAccess(): SsidAccess

/**
 * Puts the permission prompt on screen and waits for an answer, up to
 * [timeoutMs]. Only ever called from something the person just tapped: a
 * location prompt nobody asked for is how an SSH client gets uninstalled.
 */
expect suspend fun requestSsidAccess(timeoutMs: Long = 10000): SsidAccess
