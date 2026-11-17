package com.rnotlnglgq.netprobe

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import com.rnotlnglgq.netprobe.ui.ProbeViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LanguageSettingsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun open() {
        compose.onNodeWithTag("menu-more").performClick()
        compose.onNodeWithTag("settings-language").performClick()
    }
    private fun select(tag: String) {
        val previous=compose.activity
        var current=""
        compose.runOnUiThread { current=AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        compose.onNodeWithTag("language-choice-${tag.ifEmpty { "system" }}").performClick()
        if (current != tag) compose.waitUntil(10000) { runCatching { compose.activity !== previous }.getOrDefault(false) }
        compose.waitForIdle()
    }
    @Test fun menuSwitchesLanguagesPersistsAndCanFollowSystem() {
        var original=LocaleListCompat.getEmptyLocaleList()
        lateinit var model: ProbeViewModel
        compose.runOnUiThread {
            original=AppCompatDelegate.getApplicationLocales()
            model=ViewModelProvider(compose.activity)[ProbeViewModel::class.java]
        }
        try {
            open();select("zh-Hans")
            compose.waitForIdle()
            open();compose.onNodeWithTag("language-choice-zh-Hans").assertIsSelected()
            compose.onNodeWithText("跟随系统").assertExists()
            select("en")
            compose.waitForIdle()
            compose.onNodeWithText("More").assertExists()
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.runOnUiThread {
                assertEquals("en",AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertSame(model,ViewModelProvider(compose.activity)[ProbeViewModel::class.java])
            }
            open();compose.onNodeWithTag("language-choice-en").assertIsSelected()
            select("")
            compose.waitForIdle()
            compose.runOnUiThread { assertTrue(AppCompatDelegate.getApplicationLocales().isEmpty) }
        } finally {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(original) }
            compose.waitForIdle()
        }
    }
}
