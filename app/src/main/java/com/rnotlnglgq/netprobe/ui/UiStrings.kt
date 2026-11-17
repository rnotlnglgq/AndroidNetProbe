package com.rnotlnglgq.netprobe.ui


import android.content.res.Resources
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalConfiguration
import com.rnotlnglgq.netprobe.model.DiagnosticCatalog

sealed interface UiText {
    data class Resource(val id: Int, val arguments: List<Any?>) : UiText
    companion object { fun resource(id: Int, vararg args: Any?) = Resource(id,args.toList()) }
}
data class UiMessage(val id: Long, val text: UiText)

interface UiStrings {
    fun get(id: Int, vararg arguments: Any?): String
    val locale: java.util.Locale get() = java.util.Locale.getDefault()
    fun time(utc: String): String = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.MEDIUM)
        .withLocale(locale).withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.parse(utc))
    fun quantity(id: Int, count: Int): String
    fun resolve(text: UiText): String = when (text) {
        is UiText.Resource -> get(text.id,*text.arguments.map { if (it is UiText) resolve(it) else it }.toTypedArray())
    }
    fun legacy(note: String): String {
        val reason = DiagnosticCatalog.fromLegacy(note) ?: return note
        val id = diagnosticResources[reason.code] ?: return note
        return get(id,*reason.arguments.toTypedArray())
    }
}

class AndroidUiStrings(private val resources: Resources) : UiStrings {
    override val locale: java.util.Locale get() = resources.configuration.locales[0]
    override fun get(id: Int, vararg arguments: Any?) = resources.getString(id,*arguments)
    override fun quantity(id: Int, count: Int) = resources.getQuantityString(id,count,count)
}
@Composable
fun rememberUiStrings(): UiStrings {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources,configuration) { AndroidUiStrings(resources) }
}
