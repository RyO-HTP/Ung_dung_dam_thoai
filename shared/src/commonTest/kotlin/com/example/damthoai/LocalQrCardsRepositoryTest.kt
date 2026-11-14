package com.example.damthoai

import com.example.damthoai.data.qr.LocalQrCardsRepository
import com.example.damthoai.data.qr.QrParseResult
import com.example.damthoai.data.qr.QrTokenParser
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class LocalQrCardsRepositoryTest {

    @Test
    fun getRoleCards_generatesFiveDepartmentCardsAndParsesValid() = runTest {
        val repo = LocalQrCardsRepository()
        val cards = repo.getRoleCards(889900L)
        assertEquals(5, cards.size)

        cards.forEach { card ->
            val parseResult = QrTokenParser.parse(card.deepLinkUri)
            assertTrue(parseResult is QrParseResult.Valid, "Token for ${card.deptCode} should be valid")
            val valid = parseResult as QrParseResult.Valid
            assertEquals(card.deptCode, valid.payload.deptCode)
            assertEquals(card.userLevel, valid.payload.userLevel)
        }
    }
}
