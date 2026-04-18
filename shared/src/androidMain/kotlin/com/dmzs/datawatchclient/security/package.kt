/**
 * Android-only security primitives: Keystore wrapping for master key generation and
 * EncryptedSharedPreferences-backed per-profile token storage.
 *
 * **Design invariants** (enforced by the types here + review):
 * - No plaintext bearer token is ever persisted outside [TokenVault].
 * - The SQLCipher master key is never exported from the Keystore; it's used only
 *   to derive downstream keys via Keystore-backed HMAC.
 * - No accessor returns a mutable collection of secrets; callers see only the
 *   specific alias they asked for.
 *
 * See `docs/security-model.md` § "Key hierarchy" for the full derivation tree.
 */
package com.dmzs.datawatchclient.security
