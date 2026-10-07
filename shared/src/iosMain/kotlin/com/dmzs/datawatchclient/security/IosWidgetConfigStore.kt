package com.dmzs.datawatchclient.security

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The widget configuration (enabled servers, active server id, Keychain aliases
 * of the tokens — never a token) kept in the Keychain so the app and its widget
 * extension can both read it. No App Group is needed: both targets list the
 * same `keychain-access-groups` entry (`$(AppIdentifierPrefix)com.dmzs.datawatchclient`),
 * and items written without an explicit access group land in that first group.
 *
 * Accessible after first unlock (this device only) so a widget refresh while
 * the phone is locked can still read which server to show. The bearer tokens
 * stay in [IosTokenStore] (when-unlocked only); a locked refresh reports
 * "locked" instead of reading them.
 */
@OptIn(ExperimentalForeignApi::class)
public class IosWidgetConfigStore {
    public fun put(value: String) {
        remove()
        val bytes = value.encodeToByteArray()
        memScoped {
            val serviceRef = cfString(SERVICE)
            val accountRef = cfString(ACCOUNT)
            val dataRef = bytes.toCFData()
            val dict = newDict(5)
            try {
                CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(dict, kSecAttrService, serviceRef)
                CFDictionarySetValue(dict, kSecAttrAccount, accountRef)
                CFDictionarySetValue(dict, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                CFDictionarySetValue(dict, kSecValueData, dataRef)
                SecItemAdd(dict, null)
            } finally {
                CFRelease(dict)
                dataRef?.let { CFRelease(it) }
                CFRelease(accountRef)
                CFRelease(serviceRef)
            }
        }
    }

    public fun get(): String? =
        memScoped {
            val serviceRef = cfString(SERVICE)
            val accountRef = cfString(ACCOUNT)
            val dict = newDict(5)
            var resultDataRef: CFDataRef? = null
            try {
                CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(dict, kSecAttrService, serviceRef)
                CFDictionarySetValue(dict, kSecAttrAccount, accountRef)
                CFDictionarySetValue(dict, kSecReturnData, kCFBooleanTrue)
                CFDictionarySetValue(dict, kSecMatchLimit, kSecMatchLimitOne)
                val resultRef = alloc<CFTypeRefVar>()
                if (SecItemCopyMatching(dict, resultRef.ptr) != errSecSuccess) return@memScoped null
                resultDataRef = resultRef.value as? CFDataRef ?: return@memScoped null
                val length = CFDataGetLength(resultDataRef).toInt()
                val ptr = CFDataGetBytePtr(resultDataRef) ?: return@memScoped null
                ByteArray(length) { ptr[it].toByte() }.decodeToString()
            } finally {
                resultDataRef?.let { CFRelease(it) }
                CFRelease(dict)
                CFRelease(accountRef)
                CFRelease(serviceRef)
            }
        }

    public fun remove() {
        memScoped {
            val serviceRef = cfString(SERVICE)
            val accountRef = cfString(ACCOUNT)
            val dict = newDict(3)
            try {
                CFDictionarySetValue(dict, kSecClass, kSecClassGenericPassword)
                CFDictionarySetValue(dict, kSecAttrService, serviceRef)
                CFDictionarySetValue(dict, kSecAttrAccount, accountRef)
                SecItemDelete(dict)
            } finally {
                CFRelease(dict)
                CFRelease(accountRef)
                CFRelease(serviceRef)
            }
        }
    }

    public companion object {
        public const val SERVICE: String = "datawatch.widget"
        public const val ACCOUNT: String = "config"
    }
}
