package com.xike.app.outdoor

import org.junit.Assert.*
import org.junit.Test

class AmapSettingsTest {
    @Test
    fun `only known SDK diagnostic tags leave raw network details`() {
        assertEquals(
            AmapNetworkDiagnostic.TRANSPORT,
            AmapNetworkDiagnostic.fromDetail("network failure #0402"),
        )
        assertEquals(
            AmapNetworkDiagnostic.HTTP_RESPONSE,
            AmapNetworkDiagnostic.fromDetail("response #0404"),
        )
        assertNull(AmapNetworkDiagnostic.fromDetail("unrecognized detail"))
        assertNull(AmapNetworkDiagnostic.fromDetail("credential-value-12345"))
        assertEquals(
            AmapNetworkDiagnostic.RESPONSE_NULL_REFERENCE,
            AmapNetworkDiagnostic.fromDetail(
                "check response exception ex is null object reference #0403"
            ),
        )
        assertEquals(
            AmapNetworkDiagnostic.RESPONSE_EXCEPTION,
            AmapNetworkDiagnostic.fromDetail("check response exception ex is unavailable #0403"),
        )
        assertEquals(
            AmapNetworkDiagnostic.RESPONSE,
            AmapNetworkDiagnostic.fromDetail("empty server response #0403"),
        )
    }

    @Test
    fun `network failures are distinct from key authentication failures`() {
        assertEquals(AmapFailureKind.NETWORK, amapFailureKind(4))
        assertEquals(AmapFailureKind.AUTH, amapFailureKind(7))
        assertEquals(AmapFailureKind.PERMISSION, amapFailureKind(12))
        assertEquals(AmapFailureKind.PERMISSION, amapFailureKind(20))
        assertEquals(AmapFailureKind.TIMEOUT, amapFailureKind(-2))
        assertEquals(AmapFailureKind.OTHER, amapFailureKind(999))
    }

    @Test
    fun `three fast taps open once and a new sequence needs three more`() {
        val gate = TripleTapGate()
        assertFalse(gate.tap(100))
        assertFalse(gate.tap(200))
        assertTrue(gate.tap(300))
        assertFalse(gate.tap(400))
        assertFalse(gate.tap(500))
        assertTrue(gate.tap(600))
    }

    @Test
    fun `slow taps and a clock reset do not open the configuration`() {
        val gate = TripleTapGate()
        assertFalse(gate.tap(100))
        assertFalse(gate.tap(1500))
        assertFalse(gate.tap(2200))
        assertFalse(gate.tap(2300))
        assertFalse(gate.tap(10))
        assertFalse(gate.tap(20))
        assertTrue(gate.tap(30))
    }

    @Test
    fun `disabled is the default and enabling requires both a key and consent`() {
        assertTrue(AmapSettings().isValid())
        assertFalse(AmapSettings().canUseAmap)
        assertFalse(AmapSettings(enabled = true).isValid())
        assertFalse(AmapSettings(apiKey = "configured", enabled = true).isValid())
        assertFalse(AmapSettings(enabled = true, privacyAccepted = true).isValid())
        assertTrue(AmapSettings("configured", true, true).canUseAmap)
        assertFalse(AmapSettings("configured", false, true).canUseAmap)
    }

    @Test
    fun `saving trims surrounding whitespace and disabling keeps the key`() {
        val settings = AmapSettings("  configured  ", true, true).normalized()
        assertEquals("configured", settings.apiKey)
        assertTrue(settings.isValid())
        assertEquals(settings.apiKey, settings.copy(enabled = false).apiKey)
        assertFalse(settings.copy(apiKey = " ").isValid())
        assertFalse(settings.copy(apiKey = "x".repeat(257)).isValid())
    }

    @Test
    fun `diagnostic representations never expose the key`() {
        assertFalse(
            AmapSettings("sensitive-value", true, true).toString().contains("sensitive-value")
        )
    }

    @Test
    fun `coarse place labels exclude street and detailed address`() {
        val labels = amapPlaceLabels("中国", "浙江省", "杭州市", "滨江区", "和畅路", "和畅路18号", false)!!
        assertEquals("杭州市 · 滨江区", labels.label)
        assertEquals("中国 · 浙江省 · 杭州市 · 滨江区", labels.fullLabel)
        assertFalse(labels.fullLabel.contains("18"))
    }

    @Test
    fun `precise labels include the street and preserve the complete address`() {
        val labels = amapPlaceLabels("中国", "浙江省", "杭州市", "滨江区", "和畅路", "和畅路18号", true)!!
        assertEquals("杭州市 · 滨江区 · 和畅路", labels.label)
        assertEquals("和畅路18号", labels.fullLabel)
    }

    @Test
    fun `street survives when the detailed address is unavailable`() {
        val labels = amapPlaceLabels("中国", "浙江省", "杭州市", "滨江区", "和畅路", "", true)!!
        assertTrue(labels.fullLabel.endsWith("和畅路"))
    }

    @Test
    fun `empty addresses fail without substituting coordinates`() {
        assertNull(amapPlaceLabels("", "", "", "", "", "", false))
        val labels = amapPlaceLabels("中国", "重庆市", "重庆市", "", "", "", false)!!
        assertEquals("中国 · 重庆市", labels.fullLabel)
    }
}
