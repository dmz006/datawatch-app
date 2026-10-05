package com.dmzs.datawatchclient.ui.autonomous

import kotlin.test.Test
import kotlin.test.assertEquals

/** PWA openPRDInstantiateModal `k=v,k=v` parsing. */
class PrdTemplateVarsTest {
    @Test
    fun `parses pairs, trims and keeps values containing equals`() {
        assertEquals(
            mapOf("repo" to "acme/api", "query" to "a=b"),
            parsePrdTemplateVars(" repo = acme/api , query=a=b"),
        )
    }

    @Test
    fun `drops blank keys and malformed entries`() {
        assertEquals(emptyMap(), parsePrdTemplateVars(""))
        assertEquals(mapOf("x" to ""), parsePrdTemplateVars("=v, nokey ,x="))
    }
}
