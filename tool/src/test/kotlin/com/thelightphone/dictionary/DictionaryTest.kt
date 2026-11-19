package com.thelightphone.dictionary

import com.thelightphone.dictionary.data.ApiWordEntry
import com.thelightphone.dictionary.data.BuiltInDictionary
import com.thelightphone.dictionary.data.WebsterParser
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun testWebsterParserPosAndInflectionCleanup() {
        val raw = "() of Run\n\n(v. t.) To move swiftly; as, to run a race.\n\n(n.) The act of running."
        val entry = WebsterParser.parse("run", raw)
        assertEquals("run", entry.word)
        assertEquals(2, entry.meanings.size)
        assertEquals("verb", entry.meanings[0].partOfSpeech)
        assertEquals("To move swiftly", entry.meanings[0].definitions.first().definition)
        assertEquals("to run a race.", entry.meanings[0].definitions.first().example)
        assertEquals("noun", entry.meanings[1].partOfSpeech)
        assertFalse(entry.meanings[0].definitions.first().definition.contains("(v. t.)"))

        val prepEntry = WebsterParser.parse("above", "(prep.) In or to a higher place.")
        assertEquals("preposition", prepEntry.meanings.first().partOfSpeech)
        assertEquals("In or to a higher place.", prepEntry.meanings.first().definitions.first().definition)
    }

    @Test
    fun testScrabbleScoringAndLevenshtein() {
        // Standard English Scrabble tile values
        assertEquals(11, WebsterParser.calculateScrabbleScore("qi"))        // Q(10)+I(1)
        assertEquals(11, WebsterParser.calculateScrabbleScore("za"))        // Z(10)+A(1)
        assertEquals(15, WebsterParser.calculateScrabbleScore("alphabet"))  // A1+L1+P3+H4+A1+B3+E1+T1
        assertEquals(26, WebsterParser.calculateScrabbleScore("quixotic"))  // Q10+U1+I1+X8+O1+T1+I1+C3
        assertEquals(17, WebsterParser.calculateScrabbleScore("serendipity")) // S1+E1+R1+E1+N1+D2+I1+P3+I1+T1+Y4
        assertEquals(7, WebsterParser.calculateScrabbleScore("hate"))       // H4+A1+T1+E1

        assertEquals(2, WebsterParser.levenshteinDistance("alfabet", "alphabet"))
        assertEquals(2, WebsterParser.levenshteinDistance("brail", "braille"))
    }

    @Test
    fun testApiJsonParsing() {
        val jsonStr = """[{"word":"love","phonetic":"/lʌv/","phonetics":[{"text":"/lʌv/","audio":""}],"meanings":[{"partOfSpeech":"noun","definitions":[{"definition":"Strong affection.","synonyms":["affection"],"antonyms":["hate"]}],"synonyms":[],"antonyms":[]}]}]"""
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val entries = json.decodeFromString<List<ApiWordEntry>>(jsonStr)
        assertEquals(1, entries.size)
        assertEquals("love", entries.first().word)
        assertEquals("/lʌv/", entries.first().phonetic)
    }

    @Test
    fun testOfflineDatabaseAssetExistsAndUnderGitHubLimit() {
        val dbFile = File("src/main/assets/offline_dictionary.db")
        assertTrue(dbFile.exists(), "offline_dictionary.db must exist")
        val sizeMb = dbFile.length() / (1024.0 * 1024.0)
        assertTrue(sizeMb > 10.0, "offline_dictionary.db should contain full lexicon, got $sizeMb MB")
        assertTrue(sizeMb < 50.0, "offline_dictionary.db must stay under 50MB for clean GitHub pushes, got $sizeMb MB")
    }
}
