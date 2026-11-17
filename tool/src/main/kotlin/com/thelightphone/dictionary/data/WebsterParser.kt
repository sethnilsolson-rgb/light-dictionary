package com.thelightphone.dictionary.data

object WebsterParser {

    fun parse(word: String, raw: String): WordEntry {
        val paragraphs = raw.split("\n\n").map { it.trim() }.filter { it.isNotBlank() }
        val meanings = mutableListOf<WordMeaning>()

        paragraphs.forEach { para ->
            var pos = "definition"
            var cleanPara = para
            val lower = para.lowercase()
            when {
                lower.startsWith("n.") || lower.contains("(n.)") -> {
                    pos = "noun"
                    cleanPara = cleanPara.replaceFirst(Regex("^n\\.\\s*|\\(n\\.\\)\\s*"), "")
                }
                lower.startsWith("v.") || lower.contains("(v. t.)") || lower.contains("(v. i.)") || lower.contains("(v.)") -> {
                    pos = "verb"
                    cleanPara = cleanPara.replaceFirst(Regex("^v\\.\\s*|\\(v\\.\\w*\\.?\\)\\s*"), "")
                }
                lower.startsWith("a.") || lower.contains("(a.)") || lower.contains("(adj.)") -> {
                    pos = "adjective"
                    cleanPara = cleanPara.replaceFirst(Regex("^a\\.\\s*|\\(a\\.\\)\\s*|\\(adj\\.\\)\\s*"), "")
                }
                lower.startsWith("adv.") || lower.contains("(adv.)") -> {
                    pos = "adverb"
                    cleanPara = cleanPara.replaceFirst(Regex("^adv\\.\\s*|\\(adv\\.\\)\\s*"), "")
                }
            }

            var synonymsList = emptyList<String>()
            val synSplit = cleanPara.split(Regex("Syn\\.\\s*--|Synonyms?:", RegexOption.IGNORE_CASE))
            if (synSplit.size > 1) {
                cleanPara = synSplit[0].trim()
                val synText = synSplit[1].trim()
                synonymsList = synText.split(Regex("[,;\\.]"))
                    .map { it.trim().trim('-', ' ') }
                    .filter { it.isNotBlank() && it.length > 1 && !it.contains(" ") }
                    .take(6)
            }

            val numMatches = Regex("(\\d+\\.)\\s+").findAll(cleanPara).toList()
            val defs = mutableListOf<WordDefinition>()

            if (numMatches.size > 1) {
                for (i in numMatches.indices) {
                    val start = numMatches[i].range.last + 1
                    val end = if (i < numMatches.lastIndex) numMatches[i + 1].range.first else cleanPara.length
                    val defSnippet = cleanPara.substring(start, end).trim()
                    if (defSnippet.isNotBlank()) {
                        defs.add(WordDefinition(definition = defSnippet))
                    }
                }
            } else {
                defs.add(WordDefinition(definition = cleanPara))
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

        if (meanings.isEmpty()) {
            meanings.add(
                WordMeaning(
                    partOfSpeech = "definition",
                    definitions = listOf(WordDefinition(definition = raw))
                )
            )
        }

        return WordEntry(
            word = word,
            phonetic = "",
            origin = "",
            meanings = meanings
        )
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
        for (ch in word.uppercase()) {
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
