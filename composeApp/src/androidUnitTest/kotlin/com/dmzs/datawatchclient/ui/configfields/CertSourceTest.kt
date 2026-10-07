package com.dmzs.datawatchclient.ui.configfields

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** BL413 — web v8.62 certificate-source selector in Settings › Comms › Web Server. */
class CertSourceTest {
    private val fields = ConfigFieldSchemas.WebServer.fields

    private fun shown(
        key: String,
        values: Map<String, String>,
    ): Boolean = isShown(fields.first { it.key == key }, fields, values)

    @Test
    fun `mode follows the web rule`() {
        assertEquals("acme", certSourceOf(mapOf("acme.enabled" to "true", "server.tls_auto_generate" to "true")))
        assertEquals("selfsigned", certSourceOf(mapOf("acme.enabled" to "false", "server.tls_auto_generate" to "true")))
        assertEquals("custom", certSourceOf(mapOf("acme.enabled" to "", "server.tls_auto_generate" to "false")))
    }

    @Test
    fun `old separate tls rows are gone`() {
        assertFalse(fields.any { it is ConfigField.Toggle && it.key == "server.tls_auto_generate" })
        assertTrue(fields.any { it is ConfigField.CertSource })
    }

    @Test
    fun `conditional blocks show like the web`() {
        val custom = mapOf(ConfigFieldSchemas.CERT_SOURCE_KEY to "custom")
        val acme = mapOf(ConfigFieldSchemas.CERT_SOURCE_KEY to "acme")
        assertTrue(shown("server.tls_cert", custom))
        assertFalse(shown("server.tls_cert", acme))
        assertTrue(shown("acme.domains", acme))
        assertFalse(shown("acme.domains", custom))
        // Method defaults to http01 → DNS-01 fields hidden until chosen.
        assertFalse(shown("acme.dns01.zone_id", acme))
        assertTrue(shown("acme.dns01.zone_id", acme + ("acme.method" to "dns01")))
        // dns01 left over from earlier stays hidden outside Let's Encrypt mode.
        assertFalse(shown("acme.dns01.zone_id", custom + ("acme.method" to "dns01")))
    }

    @Test
    fun `switching to lets encrypt patches only the two booleans`() {
        val loaded =
            mapOf(
                "server.tls_auto_generate" to "true",
                "acme.enabled" to "false",
                ConfigFieldSchemas.CERT_SOURCE_KEY to "selfsigned",
            )
        val values =
            loaded +
                mapOf(
                    "server.tls_auto_generate" to "false",
                    "acme.enabled" to "true",
                    ConfigFieldSchemas.CERT_SOURCE_KEY to "acme",
                )
        val patch = buildDotPatch(fields, values, loaded)!!
        assertEquals(setOf("server.tls_auto_generate", "acme.enabled"), patch.keys)
        assertEquals(JsonPrimitive(true), patch["acme.enabled"])
        assertEquals(JsonPrimitive(false), patch["server.tls_auto_generate"])
    }

    @Test
    fun `domains read as comma list and save as the string the server splits`() {
        val cfg = Json.parseToJsonElement("""{"acme":{"domains":["a.example","b.example"]}}""").jsonObject
        assertEquals("a.example, b.example", readDottedAsCsv(cfg, "acme.domains"))
        val patch = buildDotPatch(fields, mapOf("acme.domains" to "a.example, c.example"), mapOf("acme.domains" to "a.example, b.example"))!!
        assertEquals(JsonPrimitive("a.example, c.example"), patch["acme.domains"])
    }

    @Test
    fun `blank provider token keeps the stored secret`() {
        assertNull(buildDotPatch(fields, mapOf("acme.dns01.token_secret" to ""), mapOf("acme.dns01.token_secret" to "***")))
    }
}
