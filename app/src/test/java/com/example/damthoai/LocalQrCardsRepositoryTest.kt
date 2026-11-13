package com.example.damthoai

import com.example.damthoai.data.qr.LocalQrCardsRepository
import com.example.damthoai.data.qr.QrParseResult
import com.example.damthoai.data.qr.QrTokenParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalQrCardsRepositoryTest {

    @Test
    fun getRoleCards_generatesFiveDepartmentCardsAndParsesValid() = runBlocking {
        val repo = LocalQrCardsRepository()
        val cards = repo.getRoleCards(889900L)
        assertEquals(5, cards.size)

        cards.forEach { card ->
            val parseResult = QrTokenParser.parse(card.deepLinkUri)
            assertTrue("Token for ${card.deptCode} should be valid", parseResult is QrParseResult.Valid)
            val valid = parseResult as QrParseResult.Valid
            assertEquals(card.deptCode, valid.payload.deptCode)
            assertEquals(card.userLevel, valid.payload.userLevel)
        }
    }
}
