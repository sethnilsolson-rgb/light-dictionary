package com.thelightphone.dictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.dictionary.data.DictionaryRepository
import com.thelightphone.dictionary.data.WebsterParser
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
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuickLookupPopup(
    val query: String,
    val isLoading: Boolean = true,
    val entry: WordEntry? = null,
)

class WordDetailViewModel(
    initialWord: String,
    private val repository: DictionaryRepository,
) : LightViewModel<Unit>() {

    private val _currentWord = MutableStateFlow(initialWord.trim())
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

    private val _similarWords = MutableStateFlow<List<String>>(emptyList())
    val similarWords: StateFlow<List<String>> = _similarWords.asStateFlow()

    private val _isScrabbleLegal = MutableStateFlow(false)
    val isScrabbleLegal: StateFlow<Boolean> = _isScrabbleLegal.asStateFlow()

    private val _scrabbleScore = MutableStateFlow(0)
    val scrabbleScore: StateFlow<Int> = _scrabbleScore.asStateFlow()

    // Stack of press-and-hold quick definitions so users can even press-and-hold inside a popup
    private val _popupStack = MutableStateFlow<List<QuickLookupPopup>>(emptyList())
    val popupStack: StateFlow<List<QuickLookupPopup>> = _popupStack.asStateFlow()

    private var loadJob: Job? = null
    private var bookmarkJob: Job? = null
    private var adjacentJob: Job? = null
    private var quickLookupJob: Job? = null
    private var hasInitialized = false

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        if (!hasInitialized) {
            hasInitialized = true
            loadWord(_currentWord.value)
        }
    }

    fun loadWord(word: String) {
        val cleanWord = word.trim()
        loadJob?.cancel()
        bookmarkJob?.cancel()
        adjacentJob?.cancel()
        dismissQuickLookup()

        _currentWord.value = cleanWord
        _entry.value = null
        _suggestions.value = emptyList()
        _similarWords.value = emptyList()
        _isScrabbleLegal.value = false
        _scrabbleScore.value = 0
        _isLoading.value = true

        bookmarkJob = viewModelScope.launch {
            runCatching {
                repository.isWordBookmarked(cleanWord).collect { bookmarked ->
                    _isBookmarked.value = bookmarked
                }
            }
        }

        adjacentJob = viewModelScope.launch {
            _adjacentWords.value = runCatching {
                repository.getAdjacentWords(cleanWord)
            }.getOrDefault(Pair(null, null))
        }

        loadJob = viewModelScope.launch {
            try {
                var emittedValidEntry = false
                repository.getWordFlow(cleanWord).collect { result ->
                    if (result != null) {
                        emittedValidEntry = true
                        _entry.value = result
                        _suggestions.value = emptyList()
                        val legal = runCatching { repository.isScrabbleLegal(cleanWord) }.getOrDefault(false)
                        _isScrabbleLegal.value = legal
                        _scrabbleScore.value = if (legal) WebsterParser.calculateScrabbleScore(cleanWord) else 0
                        _isLoading.value = false
                        // Fetch similar words without delaying main definition display
                        _similarWords.value = runCatching { repository.getSimilarWords(cleanWord) }.getOrDefault(emptyList())
                    } else if (!emittedValidEntry) {
                        _entry.value = null
                        _similarWords.value = emptyList()
                        _isScrabbleLegal.value = false
                        _scrabbleScore.value = 0
                        _suggestions.value = runCatching { repository.getSuggestions(cleanWord) }.getOrDefault(emptyList())
                        _isLoading.value = false
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                if (_entry.value == null) {
                    _suggestions.value = runCatching { repository.getSuggestions(cleanWord) }.getOrDefault(emptyList())
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun quickLookupAt(text: String, charIndex: Int) {
        val extracted = DictionaryRepository.extractWordAt(text, charIndex) ?: return
        val normalized = extracted.lowercase()
        // Avoid pushing duplicate consecutive popups for the exact same word
        val top = _popupStack.value.lastOrNull()
        if (top != null && top.query.equals(normalized, ignoreCase = true) && !top.isLoading) {
            return
        }

        quickLookupJob?.cancel()
        val loadingCard = QuickLookupPopup(query = extracted, isLoading = true, entry = null)
        // Cap nesting depth at 6 so the trail stays crisp
        val baseStack = _popupStack.value.takeLast(5)
        _popupStack.value = baseStack + loadingCard

        quickLookupJob = viewModelScope.launch {
            val found = runCatching { repository.quickLookupWord(extracted) }.getOrNull()
            val updated = _popupStack.value.toMutableList()
            if (updated.isNotEmpty() && updated.last().query == extracted) {
                updated[updated.lastIndex] = QuickLookupPopup(
                    query = extracted,
                    isLoading = false,
                    entry = found,
                )
                _popupStack.value = updated
            }
        }
    }

    fun popQuickLookup() {
        quickLookupJob?.cancel()
        val current = _popupStack.value
        _popupStack.value = if (current.size > 1) current.dropLast(1) else emptyList()
    }

    fun dismissQuickLookup() {
        quickLookupJob?.cancel()
        _popupStack.value = emptyList()
    }

    fun openPopupFullEntry(word: String) {
        dismissQuickLookup()
        loadWord(word)
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
        val similarWords by viewModel.similarWords.collectAsState()
        val isScrabbleLegal by viewModel.isScrabbleLegal.collectAsState()
        val scrabbleScore by viewModel.scrabbleScore.collectAsState()
        val popupStack by viewModel.popupStack.collectAsState()

        val activePopup = popupStack.lastOrNull()
        val isPopupOpen = activePopup != null
        val haptics = LocalHapticFeedback.current

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

        // Reset origin dropdown cleanly whenever switching to a new word
        var originExpanded by remember(currentWord) { mutableStateOf(false) }

        val onHoldWord: (String, Int) -> Unit = { sourceText, offset ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.quickLookupAt(sourceText, offset)
        }

        LightTheme(colors = themeColors) {
            val swipeModifier = if (isPopupOpen) {
                Modifier
            } else {
                Modifier.horizontalWordSwipe(
                    onPrevWord = prevWord?.let { p -> { viewModel.loadWord(p) } },
                    onNextWord = nextWord?.let { n -> { viewModel.loadWord(n) } },
                    onBack = { goBack() },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .then(swipeModifier)
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

                                Spacer(modifier = Modifier.height(8.dp))

                                // Scrabble legal badge
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = LightThemeTokens.colors.content.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    LightText(
                                        text = if (isScrabbleLegal) "SCRABBLE LEGAL • $scrabbleScore PTS" else "NOT SCRABBLE LEGAL",
                                        variant = LightTextVariant.Detail
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Origin / Etymology dropdown (if available)
                            if (wordEntry.origin.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 1.5f.gridUnitsAsDp())
                                        .border(
                                            width = 1.dp,
                                            color = LightThemeTokens.colors.content.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(3.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .lightClickable { originExpanded = !originExpanded }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LightText("ORIGIN / WORD ROOT", variant = LightTextVariant.Detail)
                                        Spacer(modifier = Modifier.weight(1f))
                                        LightText(
                                            text = if (originExpanded) "▲" else "▼",
                                            variant = LightTextVariant.Detail
                                        )
                                    }

                                    if (originExpanded) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LookupableDictionaryText(
                                            text = wordEntry.origin,
                                            variant = LightTextVariant.Paragraph,
                                            onLongPressChar = { offset -> onHoldWord(wordEntry.origin, offset) },
                                            modifier = Modifier.padding(bottom = 4.dp),
                                        )
                                    }
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
                                            LookupableDictionaryText(
                                                text = def.definition,
                                                variant = LightTextVariant.Paragraph,
                                                onLongPressChar = { offset -> onHoldWord(def.definition, offset) },
                                            )

                                            def.example?.let { example ->
                                                val quoted = "“$example”"
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LookupableDictionaryText(
                                                    text = quoted,
                                                    variant = LightTextVariant.Detail,
                                                    lighten = true,
                                                    onLongPressChar = { offset -> onHoldWord(quoted, offset) },
                                                )
                                            }

                                            if (def.synonyms.isNotEmpty()) {
                                                val synText = "Synonyms: ${def.synonyms.take(4).joinToString(", ")}"
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LookupableDictionaryText(
                                                    text = synText,
                                                    variant = LightTextVariant.Detail,
                                                    lighten = true,
                                                    onLongPressChar = { offset -> onHoldWord(synText, offset) },
                                                )
                                            }

                                            if (def.antonyms.isNotEmpty()) {
                                                val antText = "Antonyms: ${def.antonyms.take(4).joinToString(", ")}"
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LookupableDictionaryText(
                                                    text = antText,
                                                    variant = LightTextVariant.Detail,
                                                    lighten = true,
                                                    onLongPressChar = { offset -> onHoldWord(antText, offset) },
                                                )
                                            }
                                        }
                                    }
                                }

                                if (meaning.synonyms.isNotEmpty() && meaning.definitions.none { it.synonyms.isNotEmpty() }) {
                                    val synText = "Synonyms: ${meaning.synonyms.take(5).joinToString(", ")}"
                                    LookupableDictionaryText(
                                        text = synText,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        onLongPressChar = { offset -> onHoldWord(synText, offset) },
                                        modifier = Modifier.padding(end = 1.5f.gridUnitsAsDp(), bottom = 8.dp),
                                    )
                                }

                                if (meaning.antonyms.isNotEmpty() && meaning.definitions.none { it.antonyms.isNotEmpty() }) {
                                    val antText = "Antonyms: ${meaning.antonyms.take(5).joinToString(", ")}"
                                    LookupableDictionaryText(
                                        text = antText,
                                        variant = LightTextVariant.Detail,
                                        lighten = true,
                                        onLongPressChar = { offset -> onHoldWord(antText, offset) },
                                        modifier = Modifier.padding(end = 1.5f.gridUnitsAsDp(), bottom = 8.dp),
                                    )
                                }

                                if (mIdx < wordEntry.meanings.size - 1) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // Subtle tip for press-and-hold inline lookup
                            LightText(
                                text = "Tip: Press & hold any word in a definition to look it up.",
                                variant = LightTextVariant.Detail,
                                lighten = true,
                                modifier = Modifier.padding(end = 1.5f.gridUnitsAsDp(), top = 4.dp),
                            )

                            // Similar words section
                            if (similarWords.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(18.dp))
                                LightText("SIMILAR WORDS", variant = LightTextVariant.Detail)
                                Spacer(modifier = Modifier.height(8.dp))

                                similarWords.forEach { similar ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 1.5f.gridUnitsAsDp())
                                            .lightClickable { viewModel.loadWord(similar) }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            LightText(
                                                text = similar,
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
                                            .padding(end = 1.5f.gridUnitsAsDp())
                                            .height(1.dp)
                                            .background(LightThemeTokens.colors.content.copy(alpha = 0.2f))
                                    )
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

                // Press-and-Hold Inline Quick-Definition Popup Card
                if (activePopup != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(LightThemeTokens.colors.background.copy(alpha = 0.6f))
                            .pointerInput(Unit) {
                                detectTapGestures { viewModel.dismissQuickLookup() }
                            },
                    ) {
                        QuickDefinitionOverlayCard(
                            popup = activePopup,
                            canStepBack = popupStack.size > 1,
                            onStepBack = { viewModel.popQuickLookup() },
                            onDismiss = { viewModel.dismissQuickLookup() },
                            onOpenFullEntry = { targetWord -> viewModel.openPopupFullEntry(targetWord) },
                            onLongPressNestedWord = onHoldWord,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                                .pointerInput(Unit) { detectTapGestures {} },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LookupableDictionaryText(
    text: String,
    variant: LightTextVariant,
    onLongPressChar: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lighten: Boolean = false,
) {
    val style = scaledDictionaryStyle(variant)
    val colors = LightThemeTokens.colors
    val color = if (lighten) colors.contentSecondary else colors.content
    var layoutResult by remember(text, variant) { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        color = color,
        style = style,
        onTextLayout = { layoutResult = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures(
                onLongPress = { pos ->
                    layoutResult?.let { layout ->
                        onLongPressChar(layout.getOffsetForPosition(pos))
                    }
                },
            )
        },
    )
}

@Composable
private fun scaledDictionaryStyle(variant: LightTextVariant): TextStyle {
    val t = LightThemeTokens.typography
    val base = when (variant) {
        LightTextVariant.Detail -> t.detail
        LightTextVariant.Copy -> t.copy
        LightTextVariant.Subheading -> t.subheading
        else -> t.paragraph
    }
    return base.copy(
        fontSize = base.fontSize.scaledUnit(),
        lineHeight = base.lineHeight.scaledUnit(),
        letterSpacing = base.letterSpacing.scaledUnit(),
    )
}

@Composable
private fun TextUnit.scaledUnit(): TextUnit {
    if (this == TextUnit.Unspecified) return this
    return value.designVerticalPxToSp()
}

@Composable
private fun QuickDefinitionOverlayCard(
    popup: QuickLookupPopup,
    canStepBack: Boolean,
    onStepBack: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFullEntry: (String) -> Unit,
    onLongPressNestedWord: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LightThemeTokens.colors
    Column(
        modifier = modifier
            .background(colors.background, RoundedCornerShape(6.dp))
            .border(width = 1.5.dp, color = colors.content, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .heightIn(max = 360.dp),
    ) {
        if (popup.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightText(
                    text = "Looking up \"${popup.query}\"...",
                    variant = LightTextVariant.Subheading,
                    modifier = Modifier.weight(1f),
                )
                LightText(
                    text = "✕",
                    variant = LightTextVariant.Subheading,
                    modifier = Modifier.lightClickable { onDismiss() },
                )
            }
            return@Column
        }

        val found = popup.entry
        val headerWord = found?.word ?: popup.query

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canStepBack) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .border(1.dp, colors.content.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .lightClickable { onStepBack() }
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    LightText(text = "←", variant = LightTextVariant.Detail)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                LightText(text = headerWord, variant = LightTextVariant.Subheading)
                val subtitle = found?.phonetic?.takeIf { it.isNotBlank() }
                    ?: if (found != null && !found.word.equals(popup.query, ignoreCase = true)) {
                        "from \"${popup.query}\""
                    } else {
                        null
                    }
                if (subtitle != null) {
                    LightText(text = subtitle, variant = LightTextVariant.Detail, lighten = true)
                }
            }

            LightText(
                text = "✕",
                variant = LightTextVariant.Subheading,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .lightClickable { onDismiss() },
            )
        }

        HorizontalDivider(
            color = colors.content.copy(alpha = 0.25f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (found == null) {
                LightText(
                    text = "No quick definition found for \"${popup.query}\".",
                    variant = LightTextVariant.Paragraph,
                )
            } else {
                found.meanings.take(3).forEachIndexed { idx, meaning ->
                    val firstDef = meaning.definitions.firstOrNull() ?: return@forEachIndexed
                    val defLine = buildString {
                        if (meaning.partOfSpeech.isNotBlank()) {
                            append("(${meaning.partOfSpeech}) ")
                        }
                        append(firstDef.definition)
                    }
                    LookupableDictionaryText(
                        text = defLine,
                        variant = LightTextVariant.Paragraph,
                        onLongPressChar = { offset -> onLongPressNestedWord(defLine, offset) },
                        modifier = Modifier.padding(bottom = if (firstDef.example != null) 3.dp else 8.dp),
                    )
                    firstDef.example?.let { ex ->
                        val exText = "“$ex”"
                        LookupableDictionaryText(
                            text = exText,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            onLongPressChar = { offset -> onLongPressNestedWord(exText, offset) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (idx < minOf(found.meanings.size, 3) - 1) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(colors.content, RoundedCornerShape(4.dp))
                    .border(1.dp, colors.content, RoundedCornerShape(4.dp))
                    .lightClickable { onOpenFullEntry(headerWord) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                LightText(
                    text = "Open Full Entry →",
                    variant = LightTextVariant.Detail,
                    color = colors.background,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .border(1.dp, colors.content.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .lightClickable { onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                LightText(
                    text = "Close",
                    variant = LightTextVariant.Detail,
                )
            }
        }
    }
}
