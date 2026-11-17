package com.thelightphone.dictionary

import com.thelightphone.dictionary.data.BuiltInDictionary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DictionaryTest {

    @Test
    fun testBuiltInWordLookup() {
        val entry = BuiltInDictionary.find("epiphany")
        assertNotNull(entry)
        assertEquals("epiphany", entry.word)
        assertEquals("/ɪˈpɪf.ən.i/", entry.phonetic)
        assertTrue(entry.origin.contains("Greek epiphaneia"))
        assertTrue(entry.meanings.isNotEmpty())

        val nounMeaning = entry.meanings.first()
        assertEquals("noun", nounMeaning.partOfSpeech)
        assertTrue(nounMeaning.definitions.isNotEmpty())

        val firstDef = nounMeaning.definitions.first()
        assertTrue(firstDef.definition.contains("sudden revelation"))
        assertNotNull(firstDef.example)
        assertTrue(firstDef.example!!.contains("strolling quietly"))
    }

    @Test
    fun testSearchWords() {
        val results = BuiltInDictionary.search("epi")
        assertTrue(results.isNotEmpty())
        assertTrue("epiphany" in results)

        val subResults = BuiltInDictionary.search("sub")
        assertTrue("sublime" in subResults)
    }

    @Test
    fun testAlphabeticalOrder() {
        val allWords = BuiltInDictionary.getAlphabeticalIndex()
        assertTrue(allWords.size >= 30)

        // Verify sorted order
        for (i in 0 until allWords.size - 1) {
            assertTrue(
                allWords[i] < allWords[i + 1],
                "Word ${allWords[i]} should precede ${allWords[i + 1]} alphabetically"
            )
        }
    }

    @Test
    fun testOriginEtymologyPresence() {
        val wordsWithOrigin = BuiltInDictionary.entries.filter { it.origin.isNotBlank() }
        assertEquals(BuiltInDictionary.entries.size, wordsWithOrigin.size)
    }

    @Test
    fun testExampleSentenceUsagePresence() {
        val wordsWithExamples = BuiltInDictionary.entries.filter { entry ->
            entry.meanings.any { m -> m.definitions.any { !it.example.isNullOrBlank() } }
        }
        assertTrue(wordsWithExamples.size >= 30)
    }

    @Test
    fun testOnlineFetchHate() = kotlinx.coroutines.runBlocking {
        val result = com.thelightphone.dictionary.data.DictionaryService.fetchWordOnline("hate")
        assertTrue(result.isSuccess, "fetchWordOnline('hate') failed: ${result.exceptionOrNull()}")
        val entry = result.getOrNull()
        assertNotNull(entry)
        assertEquals("hate", entry.word)
        assertTrue(entry.meanings.isNotEmpty())
    }
}
