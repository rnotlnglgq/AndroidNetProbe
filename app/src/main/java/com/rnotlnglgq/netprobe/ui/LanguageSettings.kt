package com.rnotlnglgq.netprobe.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.rnotlnglgq.netprobe.R

enum class AppLanguage(val tag: String, val label: Int) {
    SYSTEM("",R.string.language_follow_system),
    CHINESE("zh-Hans",R.string.language_chinese),
    ENGLISH("en",R.string.language_english);

    companion object {
        fun fromTags(tags: String): AppLanguage = when (tags.substringBefore(',').substringBefore('-')) {
            "zh" -> CHINESE
            "en" -> ENGLISH
            else -> SYSTEM
        }
    }
}

/** AppCompat delegates to the platform on API 33+ and auto-stores locales on earlier versions. */
object ApplicationLanguage {
    fun current() = AppLanguage.fromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    fun select(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}

@Composable
fun LanguageSettings(onDismiss: () -> Unit) {
    val strings=rememberUiStrings()
    val current=ApplicationLanguage.current()
    AlertDialog(onDismissRequest=onDismiss,
        title={ Text(strings.get(R.string.language_settings)) },
        text={ Column {
            AppLanguage.entries.forEach { language ->
                Row(modifier=Modifier.fillMaxWidth().testTag("language-choice-${language.tag.ifEmpty { "system" }}")
                    .selectable(selected=current==language,role=Role.RadioButton,onClick={
                        onDismiss()
                        ApplicationLanguage.select(language)
                    }).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=current==language,onClick=null)
                    Spacer(Modifier.width(12.dp))
                    Text(strings.get(language.label))
                }
            }
        } },
        confirmButton={ TextButton(onClick=onDismiss) { Text(strings.get(R.string.ui_close)) } })
}
