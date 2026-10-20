package com.xike.app.outdoor

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceNameLocalizationTest {
    @Test
    fun `English fills missing street spelling without changing existing English`() {
        assertEquals(
            "Hangzhou Puyan BinJiang Hechanglu",
            localizePlaceName("Hangzhou Puyan BinJiang 和畅路", Locale.ENGLISH),
        )
    }

    @Test
    fun `Chinese keeps the complete original address`() {
        val address = "Hangzhou Puyan BinJiang 和畅路 18号"
        assertEquals(address, localizePlaceName(address, Locale.SIMPLIFIED_CHINESE))
    }

    @Test
    fun `English keeps numbers punctuation and existing accented names`() {
        assertEquals(
            "Hangzhou · Hechanglu18Hao, Building A",
            localizePlaceName("Hangzhou · 和畅路18号, Building A", Locale.US),
        )
        val address = "18 Rue de l’Église, Montréal"
        assertEquals(address, localizePlaceName(address, Locale.ENGLISH))
        assertEquals("", localizePlaceName("", Locale.ENGLISH))
    }

    @Test
    fun `English also handles entirely Chinese region labels`() {
        assertEquals("Hangzhou", localizePlaceName("杭州", Locale.UK))
        assertEquals("Hechanglu", localizePlaceName("和暢路", Locale.ENGLISH))
    }

    @Test
    fun `each Han run joins independently and preserves original whitespace and punctuation`() {
        assertEquals(
            "Hangzhou  Binjiang\tHechanglu,18Hao · Building A",
            localizePlaceName("Hangzhou  滨江\t和畅路,18号 · Building A", Locale.ENGLISH),
        )
        assertEquals("AHechanglu18", localizePlaceName("A和畅路18", Locale.ENGLISH))
    }

    @Test
    fun `unsupported supplementary Han characters remain intact`() {
        val address = "\uD840\uDC00"
        assertEquals(address, localizePlaceName(address, Locale.ENGLISH))
    }
}
