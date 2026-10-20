package com.xike.app.outdoor

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceNameLocalizationTest {
    @Test
    fun `Chinese region dictionary resolves additional polyphonic place names`() {
        assertEquals(
            "Luan · Panyu · Leshan",
            localizePlaceName("六安 · 番禺 · 乐山", Locale.ENGLISH),
        )
    }

    @Test
    fun `English retains umlaut spelling while leaving existing Latin letters intact`() {
        assertEquals(
            "Lüliang · Lüshun · Nüer Building V",
            localizePlaceName("吕梁 · 旅顺 · 女儿 Building V", Locale.ENGLISH),
        )
    }

    @Test
    fun `English resolves polyphonic city names in simplified and traditional Chinese`() {
        for ((address, expected) in
            listOf(
                "重庆" to "Chongqing",
                "重慶" to "Chongqing",
                "长沙" to "Changsha",
                "長沙" to "Changsha",
                "厦门" to "Xiamen",
                "廈門" to "Xiamen",
            )) {
            assertEquals(address, expected, localizePlaceName(address, Locale.ENGLISH))
            assertEquals(address, address, localizePlaceName(address, Locale.SIMPLIFIED_CHINESE))
        }
    }

    @Test
    fun `city readings apply inside full addresses and preserve surrounding text`() {
        assertEquals(
            "Chongqingshi · Changshalu18Hao, Xiamendasha Building A",
            localizePlaceName("重庆市 · 长沙路18号, 厦门大厦 Building A", Locale.ENGLISH),
        )
        assertEquals(
            "Chongqingshichangshaluxiamendasha",
            localizePlaceName("重慶市長沙路廈門大厦", Locale.ENGLISH),
        )
    }

    @Test
    fun `phrase lookup preserves other readings of the same polyphonic characters`() {
        assertEquals(
            "Zhangda · Zhongliang · Dasha · Yinhanglu",
            localizePlaceName("长大 · 重量 · 大厦 · 银行路", Locale.ENGLISH),
        )
    }

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
        assertEquals(
            "${address}chongqing${address}changsha${address}xiamen",
            localizePlaceName("${address}重庆${address}长沙${address}厦门", Locale.ENGLISH),
        )
    }
}
