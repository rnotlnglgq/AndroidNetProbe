package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.model.*
import com.rnotlnglgq.netprobe.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class I18nContractTest {
    private fun resources(folder: String): Map<String,String> {
        val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings.xml")).documentElement.childNodes
        return (0 until nodes.length).map { nodes.item(it) }.filter { it.attributes?.getNamedItem("name") != null && it.attributes?.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { it.attributes.getNamedItem("name").nodeValue to it.textContent }
    }
    @Test fun localesHaveSameKeysAndFormatParameters() {
        val en=resources("values");val zh=resources("values-b+zh+Hans")
        assertEquals(en.keys,zh.keys)
        val parameters=Regex("%[0-9]+"+Regex.escape("$")+"[sd]")
        en.forEach { (key,value) -> assertEquals(key,parameters.findAll(value).map { it.value }.toSet().sorted(),parameters.findAll(zh.getValue(key)).map { it.value }.toSet().sorted()) }
    }
    @Test fun everyDiagnosticResolvesInBothLanguagesAndRetainsParameters() {
        DiagnosticCatalog.templates.forEach { (code,template) ->
            val count=Regex("%[0-9]+"+Regex.escape("$")+"s").findAll(template).count()
            val note=DiagnosticCatalog.legacy(code,*Array(count) { "parameter-$it" })
            val diagnostic=DiagnosticCatalog.fromLegacy(note)!!
            val english=TestStrings.english.legacy(note)
            assertFalse("Untranslated: $code",Regex("[\\u4e00-\\u9fff]").containsMatchIn(english))
            diagnostic.arguments.forEach { assertTrue(english.contains(it)) }
            assertEquals(note.replace("%%","%"),TestStrings.chinese.legacy(note))
        }
    }
    @Test fun localeChangesLabelsAndKeepsFieldValuesAndComparisonStable() {
        val field=readField("telephony.dataState","test",sensitive=true) { 2 }
        assertEquals("Connected",Presentation(TestStrings.english).fieldText(field))
        assertEquals("已连接",Presentation(TestStrings.chinese).fieldText(field))
        assertEquals(2,field.value)
        val required=field.copy(permission="READ_PHONE_STATE + ACCESS_COARSE_LOCATION")
        assertEquals("Permission: Phone state, Location",PermissionLabels(TestStrings.english).forField(required))
        assertEquals("需授权：电话状态、位置",PermissionLabels(TestStrings.chinese).forField(required))
    }
}
