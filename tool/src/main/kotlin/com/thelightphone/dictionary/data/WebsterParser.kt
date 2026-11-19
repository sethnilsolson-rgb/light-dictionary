package com.thelightphone.dictionary.data

object WebsterParser {

    private val leadingTagRegex = Regex("""^\(([^)]*)\)\s*""")

    fun parse(word: String, raw: String): WordEntry {
        val paragraphs = raw.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        val meanings = mutableListOf<WordMeaning>()

        paragraphs.forEach { para ->
            var pos = "definition"
            var cleanPara = para

            val tagMatch = leadingTagRegex.find(cleanPara)
            if (tagMatch != null) {
                val rawTag = tagMatch.groupValues[1].trim().lowercase()
                pos = mapPosTag(rawTag)
                cleanPara = cleanPara.substring(tagMatch.range.last + 1).trim()
            } else {
                val lower = cleanPara.lowercase()
                when {
                    lower.startsWith("n. ") -> {
                        pos = "noun"
                        cleanPara = cleanPara.removeRange(0, 3).trim()
                    }
                    lower.startsWith("v. t. ") || lower.startsWith("v. i. ") -> {
                        pos = "verb"
                        cleanPara = cleanPara.removeRange(0, 6).trim()
                    }
                    lower.startsWith("v. ") -> {
                        pos = "verb"
                        cleanPara = cleanPara.removeRange(0, 3).trim()
                    }
                    lower.startsWith("adj. ") -> {
                        pos = "adjective"
                        cleanPara = cleanPara.removeRange(0, 5).trim()
                    }
                    lower.startsWith("a. ") -> {
                        pos = "adjective"
                        cleanPara = cleanPara.removeRange(0, 3).trim()
                    }
                    lower.startsWith("adv. ") -> {
                        pos = "adverb"
                        cleanPara = cleanPara.removeRange(0, 5).trim()
                    }
                }
            }

            // Skip bare inflection stubs like "of Run" when richer definitions exist
            if (cleanPara.isBlank()) return@forEach

            var synonymsList = emptyList<String>()
            val synSplit = cleanPara.split(Regex("""Syn\.\s*--|Synonyms?:""", RegexOption.IGNORE_CASE))
            if (synSplit.size > 1) {
                cleanPara = synSplit[0].trim()
                val synText = synSplit[1].trim()
                synonymsList = synText.split(Regex("""[,;\.]"""))
                    .map { it.trim().trim('-', ' ') }
                    .filter { it.isNotBlank() && it.length > 1 && !it.contains(" ") }
                    .take(6)
            }

            val numMatches = Regex("""(\d+\.)\s+""").findAll(cleanPara).toList()
            val defs = mutableListOf<WordDefinition>()

            if (numMatches.size > 1) {
                for (i in numMatches.indices) {
                    val start = numMatches[i].range.last + 1
                    val end = if (i < numMatches.lastIndex) numMatches[i + 1].range.first else cleanPara.length
                    val defSnippet = cleanPara.substring(start, end).trim()
                    if (defSnippet.isNotBlank()) {
                        val (extractedDef, extractedExample) = extractExample(defSnippet)
                        defs.add(WordDefinition(definition = extractedDef, example = extractedExample))
                    }
                }
            } else {
                val (extractedDef, extractedExample) = extractExample(cleanPara)
                if (extractedDef.isNotBlank()) {
                    defs.add(WordDefinition(definition = extractedDef, example = extractedExample))
                }
            }

            if (defs.isNotEmpty()) {
                meanings.add(
                    WordMeaning(
                        partOfSpeech = pos,
                        definitions = defs,
                        synonyms = synonymsList
                    )
                )
            }
        }

        // If we have full definitions alongside bare "of Word" inflection stubs, filter out the stubs
        val substantiveMeanings = meanings.filterNot { m ->
            m.definitions.size == 1 && Regex("""^of\s+\S+$""", RegexOption.IGNORE_CASE)
                .matches(m.definitions.first().definition.trim())
        }
        val finalMeanings = substantiveMeanings.ifEmpty { meanings }

        if (finalMeanings.isEmpty()) {
            return WordEntry(
                word = word,
                phonetic = "",
                origin = "",
                meanings = listOf(
                    WordMeaning(
                        partOfSpeech = "definition",
                        definitions = listOf(WordDefinition(definition = raw.trim()))
                    )
                )
            )
        }

        return WordEntry(
            word = word,
            phonetic = "",
            origin = "",
            meanings = finalMeanings
        )
    }

    private fun mapPosTag(rawTag: String): String {
        return when {
            rawTag.isBlank() -> "definition"
            rawTag == "n." || rawTag == "n" || rawTag.startsWith("n.") || rawTag == "pl." -> "noun"
            rawTag.startsWith("v.") || rawTag == "v" || rawTag == "imp." || rawTag.startsWith("p. p") || rawTag.startsWith("p. pr") -> "verb"
            rawTag == "a." || rawTag == "a" || rawTag.startsWith("adj") || rawTag.startsWith("superl") || rawTag.startsWith("comp") -> "adjective"
            rawTag.startsWith("adv") -> "adverb"
            rawTag.startsWith("prep") -> "preposition"
            rawTag.startsWith("conj") -> "conjunction"
            rawTag.startsWith("interj") -> "interjection"
            rawTag.startsWith("pron") -> "pronoun"
            rawTag.startsWith("pref") -> "prefix"
            rawTag.startsWith("suf") -> "suffix"
            else -> "definition"
        }
    }

    private fun extractExample(snippet: String): Pair<String, String?> {
        val markers = listOf("; as, ", "; as ", ": as, ")
        for (marker in markers) {
            val idx = snippet.indexOf(marker)
            if (idx != -1) {
                val def = snippet.substring(0, idx).trim()
                val ex = snippet.substring(idx + marker.length).trim().trim('"')
                if (def.isNotBlank()) {
                    return Pair(def, ex.takeIf { it.isNotBlank() })
                }
            }
        }
        val quoteRegex = Regex("""^(.*?)\s*"([^"]+)"\s*$""")
        val match = quoteRegex.find(snippet)
        if (match != null && match.groupValues[1].trim().isNotBlank()) {
            return Pair(match.groupValues[1].trim(), match.groupValues[2].trim())
        }
        return Pair(snippet, null)
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        if (s1 == s2) return 0
        if (s1.isEmpty()) return s2.length
        if (s2.isEmpty()) return s1.length

        val len1 = s1.length
        val len2 = s2.length
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 0 until len1) {
            curr[0] = i + 1
            val c1 = s1[i]
            for (j in 0 until len2) {
                val cost = if (c1 == s2[j]) 0 else 1
                curr[j + 1] = minOf(
                    curr[j] + 1,
                    prev[j + 1] + 1,
                    prev[j] + cost
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }

    fun calculateScrabbleScore(word: String): Int {
        var score = 0
        for (ch in word.trim().uppercase()) {
            score += when (ch) {
                'A', 'E', 'I', 'L', 'N', 'O', 'R', 'S', 'T', 'U' -> 1
                'D', 'G' -> 2
                'B', 'C', 'M', 'P' -> 3
                'F', 'H', 'V', 'W', 'Y' -> 4
                'K' -> 5
                'J', 'X' -> 8
                'Q', 'Z' -> 10
                else -> 0
            }
        }
        return score
    }
}
