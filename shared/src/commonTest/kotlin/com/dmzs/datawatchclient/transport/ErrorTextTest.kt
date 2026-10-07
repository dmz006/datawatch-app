package com.dmzs.datawatchclient.transport

import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorTextTest {
    @Test
    fun serverErrorFieldWins() {
        val m = "503: Server error(GET https://h/api/tailscale/status: 503 Service Unavailable. Text: \"{\"error\":\"tailscale not configured\"}\""
        assertEquals("tailscale not configured", ErrorText.of(RuntimeException(m), "Failed to load."))
    }

    @Test
    fun rawNotFoundBecomesVersionHint() {
        val m = "Client request(GET https://h/api/x) invalid: 404 Not Found. Text: \"404 page not found\""
        assertEquals("Not available on this server version.", ErrorText.of(RuntimeException(m), "Failed to load."))
    }

    @Test
    fun plainMessageKeptAndNullFallsBack() {
        assertEquals("timeout", ErrorText.of(RuntimeException("timeout"), "Failed to load."))
        assertEquals("Failed to load.", ErrorText.of(null, "Failed to load."))
    }
}
