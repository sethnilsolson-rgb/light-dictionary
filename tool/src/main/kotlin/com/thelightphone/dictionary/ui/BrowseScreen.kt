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

class BrowseViewModel(
    private val repository: DictionaryRepository,
) : LightViewModel<Unit>() {

    private val _words = MutableStateFlow<List<String>>(emptyList())
    val words: StateFlow<List<String>> = _words.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            _words.value = repository.getAllKnownWordsAlphabetical()
        }
    }
}

class BrowseScreen(
    sealedActivity: SealedLightActivity,
    private val repository: DictionaryRepository,
) : LightScreen<Unit, BrowseViewModel>(sealedActivity) {

    override val viewModelClass: Class<BrowseViewModel>
        get() = BrowseViewModel::class.java

    override fun createViewModel() = BrowseViewModel(repository)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val allWords by viewModel.words.collectAsState()

        val groupedWords = allWords.groupBy { it.firstOrNull()?.uppercaseChar() ?: '#' }

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
                        center = LightTopBarCenter.Text("Browse A–Z"),
                        modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp())
                    )

                    LightScrollView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 1.5f.gridUnitsAsDp())
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))

                        groupedWords.forEach { (letter, wordsForLetter) ->
                            // Section header
                            LightText(
                                text = letter.toString(),
                                variant = LightTextVariant.Detail,
                                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                            )

                            wordsForLetter.forEach { word ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 1.5f.gridUnitsAsDp())
                                        .lightClickable {
                                            navigateTo(screenFactory = { act ->
                                                WordDetailScreen(act, word, repository)
                                            })
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LightText(
                                        text = word,
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
