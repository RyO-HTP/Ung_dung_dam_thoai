package com.example.damthoai.data

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogCodecTest {
    @Test
    fun catalogSurvivesSaveAndLoad() {
        val catalog = listOf(
            Channel(id = "ch_02", name = "Sân khấu"),
            Channel(id = "ch_ab12cd34", name = "Hậu cần – An ninh"),
            Channel(id = "ch_x", name = "Tên\tcó tab")
        )
        val decoded = AppStartPrefs.decodeCatalog(AppStartPrefs.encodeCatalog(catalog))
        assertEquals(listOf("ch_02", "ch_ab12cd34", "ch_x"), decoded.map { it.id })
        assertEquals(listOf("Sân khấu", "Hậu cần – An ninh", "Tên có tab"), decoded.map { it.name })
    }

    @Test
    fun emptyCatalogStaysEmpty() {
        assertEquals(emptyList(), AppStartPrefs.decodeCatalog(AppStartPrefs.encodeCatalog(emptyList())))
    }
}
