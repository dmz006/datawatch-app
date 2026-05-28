package com.dmzs.datawatchclient.security

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS Keychain-backed token store. Mirrors [TokenVault] on Android.
 *
 * Tokens are stored as generic password items in the iOS Keychain:
 *   kSecAttrService  = "datawatch"
 *   kSecAttrAccount  = <alias>  (same opaque ref as Android's bearerTokenRef)
 *   kSecAttrAccessible = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
 *
 * kSecAttrAccessibleWhenUnlockedThisDeviceOnly:
 *   - Inaccessible when the device is locked.
 *   - Not migrated to new devices via iCloud Backup or device transfer.
 *   - Deleted on factory reset / uninstall.
 *   Matches Android's EncryptedSharedPreferences + device-bound Keystore key guarantee.
 *
 * Wired into the iOS ServiceLocator in Story 4 (Auth). The bearerTokenRef stored in
 * ServerProfile is the same opaque alias string as on Android.
 *
 * See docs/security-model.md § "Token storage".
 */
@OptIn(ExperimentalForeignApi::class)
public class IosTokenStore {

    /** Store the bearer token for a given server profile. Returns the opaque alias. */
    public fun put(profileId: String, token: String): String {
        val alias = aliasFor(profileId)
        deleteItem(SERVICE, alias)

        val tokenBytes = token.encodeToByteArray()
        memScoped {
            val serviceRef = cfString(SERVICE)
            val aliasRef = cfString(alias)
            val dataRef = tokenBytes.toCFData()
            val dict = newDict(4)

            try {
                CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(dict, kSecAttrService, serviceRef)
                CFDictionarySetValue(dict, kSecAttrAccount, aliasRef)
                CFDictionarySetValue(dict, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)
                CFDictionarySetValue(dict, kSecValueData, dataRef)
                SecItemAdd(dict, null)
            } finally {
                CFRelease(dict)
                dataRef?.let { CFRelease(it) }
                CFRelease(aliasRef)
                CFRelease(serviceRef)
            }
        }
        return alias
    }

    /** Retrieve the bearer token for a given alias; null if missing or device locked. */
    public fun get(alias: String): String? = memScoped {
        val serviceRef = cfString(SERVICE)
        val aliasRef = cfString(alias)
        val dict = newDict(5)
        var resultDataRef: CFDataRef? = null
        try {
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, serviceRef)
            CFDictionarySetValue(dict, kSecAttrAccount, aliasRef)
            CFDictionarySetValue(dict, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(dict, kSecMatchLimit, kSecMatchLimitOne)

            val resultRef = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(dict, resultRef.ptr)
            if (status != errSecSuccess) return@memScoped null

            // SecItemCopyMatching returns a +1 retained object — must release.
            resultDataRef = resultRef.value as? CFDataRef ?: return@memScoped null
            val length = CFDataGetLength(resultDataRef).toInt()
            val ptr = CFDataGetBytePtr(resultDataRef) ?: return@memScoped null
            ByteArray(length) { ptr[it].toByte() }.decodeToString()
        } finally {
            resultDataRef?.let { CFRelease(it) }
            CFRelease(dict)
            CFRelease(aliasRef)
            CFRelease(serviceRef)
        }
    }

    /** Remove the bearer token for a given alias. */
    public fun remove(alias: String): Unit = deleteItem(SERVICE, alias)

    /** Remove all datawatch tokens from the Keychain (full reset). */
    public fun clear() = memScoped {
        val serviceRef = cfString(SERVICE)
        val dict = newDict(2)
        try {
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, serviceRef)
            SecItemDelete(dict)
        } finally {
            CFRelease(dict)
            CFRelease(serviceRef)
        }
    }

    private fun deleteItem(service: String, account: String) = memScoped {
        val serviceRef = cfString(service)
        val accountRef = cfString(account)
        val dict = newDict(3)
        try {
            CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(dict, kSecAttrService, serviceRef)
            CFDictionarySetValue(dict, kSecAttrAccount, accountRef)
            val status = SecItemDelete(dict)
            check(status == errSecSuccess || status == errSecItemNotFound) {
                "SecItemDelete failed with status $status"
            }
        } finally {
            CFRelease(dict)
            CFRelease(accountRef)
            CFRelease(serviceRef)
        }
    }

    public companion object {
        public const val SERVICE: String = "datawatch"
        public const val ALIAS_PREFIX: String = "dw.profile."

        public fun aliasFor(profileId: String): String = "$ALIAS_PREFIX$profileId"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun cfString(value: String) =
    CFStringCreateWithCString(kCFAllocatorDefault, value, kCFStringEncodingUTF8)

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toCFData() = usePinned { pinned ->
    CFDataCreate(kCFAllocatorDefault, pinned.addressOf(0).reinterpret(), size.convert())
}

@OptIn(ExperimentalForeignApi::class)
private fun newDict(capacity: Int) =
    CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        capacity.convert(),
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    )!!
