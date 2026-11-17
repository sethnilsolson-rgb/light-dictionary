package com.thelightphone.dictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.dictionary.data.DictionaryRepository
import com.thelightphone.dictionary.data.WordEntry
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WordDetailViewModel(
    initialWord: String,
    private val repository: DictionaryRepository,
) : LightViewModel<Unit>() {

    private val _currentWord = MutableStateFlow(initialWord)
    val currentWord: StateFlow<String> = _currentWord.asStateFlow()

    private val _entry = MutableStateFlow<WordEntry?>(null)
    val entry: StateFlow<WordEntry?> = _entry.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _adjacentWords = MutableStateFlow<Pair<String?, String?>>(Pair(null, null))
    val adjacentWords: StateFlow<Pair<String?, String?>> = _adjacentWords.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        loadWord(_currentWord.value)
    }

    fun loadWord(word: String) {
        _currentWord.value = word
        _isLoading.value = true

        viewModelScope.launch {
            repository.isWordBookmarked(word).collect { bookmarked ->
                _isBookmarked.value = bookmarked
            }
        }

        viewModelScope.launch {
            val result = repository.getWord(word)
            _entry.value = result
            if (result == null) {
                _suggestions.value = repository.getSuggestions(word)
            } else {
                _suggestions.value = emptyList()
            }
            _isLoading.value = false

            val adjacent = repository.getAdjacentWords(word)
            _adjacentWords.value = adjacent
        }
    }

    fun toggleBookmark() {
        val currentEntry = _entry.value ?: return
        viewModelScope.launch {
            repository.toggleBookmark(currentEntry)
        }
    }
}

class WordDetailScreen(
    sealedActivity: SealedLightActivity,
    private val word: String,
    private val repository: DictionaryRepository,
) : LightScreen<Unit, WordDetailViewModel>(sealedActivity) {

    override val viewModelClass: Class<WordDetailViewModel>
        get() = WordDetailViewModel::class.java

    override fun createViewModel() = WordDetailViewModel(word, repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val entry by viewModel.entry.collectAsState()
        val isLoading by viewModel.isLoading.collectAsState()
        val isBookmarked by viewModel.isBookmarked.collectAsState()
        val adjacentWords by viewModel.adjacentWords.collectAsState()
        val currentWord by viewModel.currentWord.collectAsState()
        val suggestions by viewModel.suggestions.collectAsState()

        val contentColor = LightThemeTokens.colors.content
        val bookmarkPainter = rememberVectorPainter(
            remember(isBookmarked, contentColor) {
                if (isBookmarked) {
                    createBookmarkFilledVector(contentColor)
                } else {
                    createBookmarkOutlineVector(contentColor)
                }
            }
        )

        val prevWord = adjacentWords.first
        val nextWord = adjacentWords.second

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .horizontalWordSwipe(
                        onPrevWord = prevWord?.let { p -> { viewModel.loadWord(p) } },
                        onNextWord = nextWord?.let { n -> { viewModel.loadWord(n) } },
                        onBack = { goBack() },
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack() },
                        ),
                        center = LightTopBarCenter.Text(currentWord.lowercase()),
                        rightButton = LightBarButton.Icon(
                            painter = bookmarkPainter,
                            contentDescription = if (isBookmarked) "Bookmarked" else "Bookmark",
                            onClick = { viewModel.toggleBookmark() },
                        ),
                        modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp())
                    )

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightText("Looking up...", variant = LightTextVariant.Paragraph)
                        }
                    } else if (entry == null) {
                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 1.5f.gridUnitsAsDp())
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))

                            LightText(
                                text = "No definition found for \"$currentWord\".",
                                variant = LightTextVariant.Heading
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (suggestions.isNotEmpty()) {
                                LightText(
                                    text = "DID YOU MEAN?",
                                    variant = LightTextVariant.Detail
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                suggestions.forEach { suggestion ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .lightClickable { viewModel.loadWord(suggestion) }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            LightText(
                                                text = suggestion,
                                                variant = LightTextVariant.Paragraph
                                            )
                                        }
                                        LightText(
                                            text = "→",
                                            variant = LightTextVariant.Detail
                                        )
                                    }
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(LightThemeTokens.colors.content.copy(alpha = 0.2f))
                                    )
                                }
                            } else {
                                LightText(
                                    text = "Check spelling or search for another word.",
                                    variant = LightTextVariant.Detail
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    } else {
                        val wordEntry = entry!!

                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 1.5f.gridUnitsAsDp())
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Headword & Phonetic
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 1.5f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = wordEntry.word,
                                    variant = LightTextVariant.Heading,
                                )

                                if (wordEntry.phonetic.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    LightText(
                                        text = wordEntry.phonetic,
                                        variant = LightTextVariant.Detail,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Origin / Etymology (if available)
                            if (wordEntry.origin.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 1.5f.gridUnitsAsDp())
                                ) {
                                    LightText("ORIGIN", variant = LightTextVariant.Detail)
                                    Spacer(modifier = Modifier.height(3.dp))
                                    LightText(
                                        text = wordEntry.origin,
                                        variant = LightTextVariant.Paragraph,
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Meanings grouped by part of speech
                            wordEntry.meanings.forEachIndexed { mIdx, meaning ->
                                if (meaning.partOfSpeech.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .border(
                                                width = 1.dp,
                                                color = LightThemeTokens.colors.content,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        LightText(
                                            text = meaning.partOfSpeech.uppercase(),
                                            variant = LightTextVariant.Detail,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                meaning.definitions.forEachIndexed { dIdx, def ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 1.5f.gridUnitsAsDp(), bottom = 10.dp)
                                    ) {
                                        if (meaning.definitions.size > 1) {
                                            LightText(
                                                text = "${dIdx + 1}. ",
                                                variant = LightTextVariant.Paragraph,
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            LightText(
                                                text = def.definition,
                                                variant = LightTextVariant.Paragraph,
                                            )

                                            def.example?.let { example ->
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LightText(
                                                    text = "“$example”",
                                                    variant = LightTextVariant.Detail,
                                                )
                                            }

                                            if (def.synonyms.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LightText(
                                                    text = "Synonyms: ${def.synonyms.take(4).joinToString(", ")}",
                                                    variant = LightTextVariant.Detail,
                                                )
                                            }

                                            if (def.antonyms.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LightText(
                                                    text = "Antonyms: ${def.antonyms.take(4).joinToString(", ")}",
                                                    variant = LightTextVariant.Detail,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (meaning.synonyms.isNotEmpty() && meaning.definitions.none { it.synonyms.isNotEmpty() }) {
                                    LightText(
                                        text = "Synonyms: ${meaning.synonyms.take(5).joinToString(", ")}",
                                        variant = LightTextVariant.Detail,
                                        modifier = Modifier.padding(end = 1.5f.gridUnitsAsDp(), bottom = 8.dp)
                                    )
                                }

                                if (meaning.antonyms.isNotEmpty() && meaning.definitions.none { it.antonyms.isNotEmpty() }) {
                                    LightText(
                                        text = "Antonyms: ${meaning.antonyms.take(5).joinToString(", ")}",
                                        variant = LightTextVariant.Detail,
                                        modifier = Modifier.padding(end = 1.5f.gridUnitsAsDp(), bottom = 8.dp)
                                    )
                                }

                                if (mIdx < wordEntry.meanings.size - 1) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Alphabetical navigation indicators
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 1.5f.gridUnitsAsDp(), bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (prevWord != null) {
                                    LightText(
                                        text = "← $prevWord",
                                        variant = LightTextVariant.Detail,
                                        modifier = Modifier.lightClickable {
                                            viewModel.loadWord(prevWord)
                                        }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                if (nextWord != null) {
                                    LightText(
                                        text = "$nextWord →",
                                        variant = LightTextVariant.Detail,
                                        modifier = Modifier.lightClickable {
                                            viewModel.loadWord(nextWord)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
