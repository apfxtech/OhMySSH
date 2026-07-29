package com.example.ohmyssh.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE = "com.example.ohmyssh"

@OptIn(ExperimentalForeignApi::class)
actual object SecureStorage {
    actual suspend fun write(key: String, value: String) {
        delete(key)

        val retained = mutableListOf<CFTypeRef?>()
        val query = query(key, retained)
        val data = CFBridgingRetain(value.toNSData()).also { retained.add(it) }
        CFDictionaryAddValue(query, kSecValueData, data)
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)

        val status = SecItemAdd(query, null)
        release(query, retained)
        if (status != errSecSuccess) {
            throw IllegalStateException("Keychain write failed ($status)")
        }
    }

    actual suspend fun read(key: String): String? = memScoped {
        val retained = mutableListOf<CFTypeRef?>()
        val query = query(key, retained)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)

        val holder = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, holder.ptr)
        release(query, retained)

        when (status) {
            errSecSuccess -> {
                val data = CFBridgingRelease(holder.value) as? NSData ?: return@memScoped null
                NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
            }
            errSecItemNotFound -> null
            else -> throw IllegalStateException("Keychain read failed ($status)")
        }
    }

    actual suspend fun delete(key: String) {
        val retained = mutableListOf<CFTypeRef?>()
        val query = query(key, retained)
        val status = SecItemDelete(query)
        release(query, retained)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw IllegalStateException("Keychain delete failed ($status)")
        }
    }

    actual suspend fun containsKey(key: String): Boolean = read(key) != null

    private fun query(key: String, retained: MutableList<CFTypeRef?>): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(
            query,
            kSecAttrService,
            CFBridgingRetain(SERVICE as NSString).also { retained.add(it) },
        )
        CFDictionaryAddValue(
            query,
            kSecAttrAccount,
            CFBridgingRetain(key as NSString).also { retained.add(it) },
        )
        return query
    }

    private fun release(query: CFMutableDictionaryRef?, retained: List<CFTypeRef?>) {
        CFRelease(query)
        for (item in retained) CFRelease(item)
    }

    private fun String.toNSData(): NSData {
        val bytes = encodeToByteArray()
        if (bytes.isEmpty()) return NSData()
        return bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
    }
}
