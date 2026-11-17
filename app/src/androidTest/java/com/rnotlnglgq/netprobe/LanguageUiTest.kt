package com.rnotlnglgq.netprobe

import androidx.compose.foundation.verticalScroll
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class LanguageUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun resourcesSwitchWithoutChangingObservationAndFallbackToEnglish() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        var tag by mutableStateOf("en")
        val field=readField("defaultNetwork.linkAddresses","LinkProperties",sensitive=true) {
            listOf(AddressRecord("2001:db8::42","IPv6",64,"global-unicast",flagsSource="test",flagsStatus=FieldStatus.unsupported_api))
        }
        compose.setContent {
            val config=Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized=app.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config) {
                MaterialTheme { DataFieldCard(field,true,{}) }
            }
        }
        compose.onNodeWithText("Link addresses").assertExists()
        compose.onNodeWithText("2001:db8::42").assertExists()
        compose.runOnIdle { tag="zh-Hans" }
        compose.onNodeWithText("链路地址").assertExists()
        compose.onNodeWithText("2001:db8::42").assertExists()
        compose.runOnIdle { tag="fr" }
        compose.onNodeWithText("Link addresses").assertExists()
        assertEquals("2001:db8::42",(field.value!!.first()).address)
    }
    @Test fun pseudoLocalesAndLargeFontKeepAddressCopyUsable() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        var tag by mutableStateOf("en-XA")
        val field=readField("defaultNetwork.linkAddresses","test",sensitive=true) {
            listOf(AddressRecord("2001:db8::42","IPv6",64,"global-unicast",flagsSource="test",flagsStatus=FieldStatus.unsupported_api))
        }
        compose.setContent {
            val config=Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag));fontScale=1.6f }
            val localized=app.createConfigurationContext(config)
            val density=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.6f),
                androidx.compose.ui.platform.LocalLayoutDirection provides if (config.layoutDirection==1) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                MaterialTheme { androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.then(
                    androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))) { DataFieldCard(field,true,{}) } }
            }
        }
        for (language in listOf("en-XA","ar-XB")) {
            compose.runOnIdle { tag=language }
            val config=Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val label=app.createConfigurationContext(config).resources.getString(R.string.ui_copy_cidr)
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
            val clipboard=app.getSystemService(android.content.ClipboardManager::class.java)
            assertEquals("2001:db8::42/64",clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
    }
    @Test fun quantityResourcesAndPseudoLocalesHaveCompleteDefaults() {
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        for (tag in listOf("en","zh-Hans","fr","en-XA","ar-XB")) {
            val config=Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)); fontScale=1.5f }
            val resources=app.createConfigurationContext(config).resources
            assertTrue(resources.getQuantityString(R.plurals.record_count,1,1).isNotBlank())
            assertTrue(resources.getQuantityString(R.plurals.record_count,2,2).isNotBlank())
            assertTrue(resources.getString(R.string.ui_refresh).isNotBlank())
        }
    }
}
