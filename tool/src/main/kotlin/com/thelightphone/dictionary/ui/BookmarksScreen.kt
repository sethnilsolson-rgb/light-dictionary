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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.thelightphone.dictionary.data.DictionaryRepository
import com.thelightphone.dictionary.db.BookmarkWordEntity
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

class BookmarksViewModel(
    private val repository: DictionaryRepository,
) : LightViewModel<Unit>() {

    private val _bookmarks = MutableStateFlow<List<BookmarkWordEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkWordEntity>> = _bookmarks.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            repository.bookmarks.collect { list ->
                _bookmarks.value = list
            }
        }
    }

    fun removeBookmark(bookmark: BookmarkWordEntity) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }
}

class BookmarksScreen(
    sealedActivity: SealedLightActivity,
    private val repository: DictionaryRepository,
) : LightScreen<Unit, BookmarksViewModel>(sealedActivity) {

    override val viewModelClass: Class<BookmarksViewModel>
        get() = BookmarksViewModel::class.java

    override fun createViewModel() = BookmarksViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val bookmarks by viewModel.bookmarks.collectAsState()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background)
                    .swipeToBack { goBack() }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    LightTopBar(
                        leftButton = LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { goBack() },
                        ),
                        center = LightTopBarCenter.Text("Saved Words"),
                        modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp())
                    )

                    if (bookmarks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(2f.gridUnitsAsDp()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LightText("No saved words yet.", variant = LightTextVariant.Paragraph)
                                Spacer(modifier = Modifier.height(6.dp))
                                LightText("Tap the bookmark ribbon on any word to save it.", variant = LightTextVariant.Detail)
                            }
                        }
                    } else {
                        LightScrollView(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(start = 1.5f.gridUnitsAsDp())
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))

                            bookmarks.forEach { bookmark ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 1.5f.gridUnitsAsDp())
                                        .lightClickable {
                                            navigateTo(screenFactory = { act ->
                                                WordDetailScreen(act, bookmark.word, repository)
                                            })
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        LightText(
                                            text = bookmark.word,
                                            variant = LightTextVariant.Subheading,
                                        )

                                        if (bookmark.partOfSpeech.isNotBlank() || bookmark.snippet.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val subtitle = if (bookmark.partOfSpeech.isNotBlank() && bookmark.snippet.isNotBlank()) {
                                                "${bookmark.partOfSpeech} • ${bookmark.snippet}"
                                            } else {
                                                bookmark.partOfSpeech.ifBlank { bookmark.snippet }
                                            }
                                            LightText(
                                                text = subtitle,
                                                variant = LightTextVariant.Detail,
                                            )
                                        }
                                    }

                                    LightText(
                                        text = "✕",
                                        variant = LightTextVariant.Detail,
                                        modifier = Modifier
                                            .padding(start = 12.dp, end = 4.dp)
                                            .lightClickable {
                                                viewModel.removeBookmark(bookmark)
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
