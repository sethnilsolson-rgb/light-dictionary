package com.thelightphone.dictionary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.dictionary.data.DictionaryRepository
import com.thelightphone.dictionary.db.DictionaryDatabase
import com.thelightphone.dictionary.db.RecentSearchEntity
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.buildDatabase
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextInputEditor
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

class HomeScreenViewModel(
    private val repository: DictionaryRepository,
) : LightViewModel<Unit>() {

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _recentSearches = MutableStateFlow<List<RecentSearchEntity>>(emptyList())
    val recentSearches: StateFlow<List<RecentSearchEntity>> = _recentSearches.asStateFlow()

    private val _bookmarkCount = MutableStateFlow(0)
    val bookmarkCount: StateFlow<Int> = _bookmarkCount.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            repository.recentSearches.collect { list ->
                _recentSearches.value = list
            }
        }
        viewModelScope.launch {
            repository.bookmarkCount.collect { count ->
                _bookmarkCount.value = count
            }
        }
    }

    fun startSearch() {
        _isSearching.value = true
    }

    fun stopSearch() {
        _isSearching.value = false
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            repository.clearRecentSearches()
        }
    }

    fun deleteRecentSearch(word: String) {
        viewModelScope.launch {
            repository.recordRecentSearch(word) // dao has deleteRecentSearch
        }
    }
}

@InitialScreen
class HomeScreen(
    sealedActivity: SealedLightActivity,
) : LightScreen<Unit, HomeScreenViewModel>(sealedActivity) {

    private val repository by lazy {
        DictionaryRepository.getInstance {
            lightContext.buildDatabase(
                DictionaryDatabase::class.java,
                DictionaryRepository.DATABASE_NAME
            ) {
                createFromAsset("offline_dictionary.db")
                fallbackToDestructiveMigration()
            }
        }
    }

    override val viewModelClass: Class<HomeScreenViewModel>
        get() = HomeScreenViewModel::class.java

    override fun createViewModel() = HomeScreenViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val isSearching by viewModel.isSearching.collectAsState()
        val recentSearches by viewModel.recentSearches.collectAsState()
        val bookmarkCount by viewModel.bookmarkCount.collectAsState()

        val searchFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
            ) {
                if (isSearching) {
                    LightTextInputEditor(
                        title = "Search Word",
                        state = searchFieldState,
                        keyboardOptionsFlow = keyboardOptionsFlow,
                        submitIcon = LightIcons.SEARCH,
                        singleLine = true,
                        onSubmit = { charSeq ->
                            val query = charSeq.toString().trim()
                            if (query.isNotBlank()) {
                                viewModel.stopSearch()
                                navigateTo(screenFactory = { act ->
                                    WordDetailScreen(act, query, repository)
                                })
                            }
                        },
                        onBack = {
                            viewModel.stopSearch()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LightTopBar(
                            center = LightTopBarCenter.Text("Dictionary"),
                            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp())
                        )

                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 1.5f.gridUnitsAsDp())
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // Search bar launcher
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 1.5f.gridUnitsAsDp())
                            ) {
                                LightTextField(
                                    label = "",
                                    value = "",
                                    placeholder = "Search a word...",
                                    onClick = { viewModel.startSearch() },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Navigation Actions
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 1.5f.gridUnitsAsDp())
                            ) {
                                // Browse A-Z button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable {
                                            navigateTo(screenFactory = { act ->
                                                BrowseScreen(act, repository)
                                            })
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        LightText("Browse Words (A–Z)", variant = LightTextVariant.Paragraph)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        LightText("Alphabetical index from A to Z", variant = LightTextVariant.Detail)
                                    }
                                    LightText("→", variant = LightTextVariant.Detail)
                                }

                                // Saved words button
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .lightClickable {
                                            navigateTo(screenFactory = { act ->
                                                BookmarksScreen(act, repository)
                                            })
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        LightText("Saved Words", variant = LightTextVariant.Paragraph)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val sub = if (bookmarkCount == 1) "1 word saved" else "$bookmarkCount words saved"
                                        LightText(sub, variant = LightTextVariant.Detail)
                                    }
                                    LightText("→", variant = LightTextVariant.Detail)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Recent Lookups
                            if (recentSearches.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 1.5f.gridUnitsAsDp()),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LightText("RECENT", variant = LightTextVariant.Detail)
                                    Spacer(modifier = Modifier.weight(1f))
                                    LightText(
                                        text = "Clear",
                                        variant = LightTextVariant.Detail,
                                        modifier = Modifier.lightClickable {
                                            viewModel.clearRecentSearches()
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                recentSearches.forEach { search ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 1.5f.gridUnitsAsDp())
                                            .lightClickable {
                                                navigateTo(screenFactory = { act ->
                                                    WordDetailScreen(act, search.word, repository)
                                                })
                                            }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        LightText(
                                            text = search.word,
                                            variant = LightTextVariant.Paragraph,
                                            modifier = Modifier.weight(1f)
                                        )
                                        LightText(
                                            text = "→",
                                            variant = LightTextVariant.Detail
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
}
