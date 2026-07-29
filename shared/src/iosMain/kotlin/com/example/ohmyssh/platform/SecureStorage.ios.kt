package com.example.ohmyssh.platform

import kotlinx.cinterop.CFTypeRefVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
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
        memScoped {
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)
            CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(query, kSecAttrService, retained(SERVICE))
            CFDictionaryAddValue(query, kSecAttrAccount, retained(key))
            CFDictionaryAddValue(query, kSecValueData, retainedData(value))
            CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
            val status = SecItemAdd(query, null)
            CFRelease(query)
            if (status != errSecSuccess) {
                throw IllegalStateException("Keychain write failed ($status)")
            }
        }
    }

    actual suspend fun read(key: String): String? = memScoped {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 4, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, retained(SERVICE))
        CFDictionaryAddValue(query, kSecAttrAccount, retained(key))
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        when (status) {
            errSecSuccess -> {
                val data = CFBridgingRelease(result.value) as? NSData ?: return null
                NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
            }
            errSecItemNotFound -> null
            else -> throw IllegalStateException("Keychain read failed ($status)")
        }
    }

    actual suspend fun delete(key: String) {
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, retained(SERVICE))
        CFDictionaryAddValue(query, kSecAttrAccount, retained(key))
        val status = SecItemDelete(query)
        CFRelease(query)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            throw IllegalStateException("Keychain delete failed ($status)")
        }
    }

    actual suspend fun containsKey(key: String): Boolean = read(key) != null

    private fun retained(value: String): CFTypeRef? = CFBridgingRetain(value as NSString)

    private fun retainedData(value: String): CFTypeRef? {
        val bytes = value.encodeToByteArray()
        val data = if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
            }
        }
        return CFBridgingRetain(data)
    }
}
