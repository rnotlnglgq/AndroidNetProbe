package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.R

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.rnotlnglgq.netprobe.probe.*
import com.rnotlnglgq.netprobe.ui.SystemPropertiesSection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SystemPropertiesUiTest {
    private val strings get() = com.rnotlnglgq.netprobe.ui.AndroidUiStrings(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources)
    @get:Rule val compose = createComposeRule()
    @Test fun propertiesAreManualAndCarrierValuesRespectVisibility() {
        var doc by mutableStateOf(SystemPropertyProbe.notRequested())
        var reveal by mutableStateOf(false)
        var calls = 0
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            SystemPropertiesSection(doc, false, reveal, {
                calls++
                doc = SystemPropertyProbe(object : PropertyCommandRunner {
                    override fun query(key: String, timeoutMillis: Long) = PropertyCommandResult(if (key == "gsm.operator.numeric") "00101,,00102" else "")
                }).collect()
            }) {}
        } } }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithText(strings.get(R.string.ui_read_properties)).performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        compose.onNodeWithText(strings.get(R.string.ui_carrier)).performClick()
        compose.onNodeWithText("gsm.operator.numeric").assertExists()
        compose.onNodeWithText("00101,,00102").assertDoesNotExist()
        compose.runOnIdle { reveal = true }
        compose.onNodeWithText("00101,,00102").assertExists()
    }
}
