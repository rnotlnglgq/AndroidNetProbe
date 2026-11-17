package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.ui.UiStrings
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object TestStrings {
    fun load(folder: String, locale: Locale): UiStrings {
        val xml=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$folder/strings.xml"))
        val nodes=xml.getElementsByTagName("string")
        val byName=(0 until nodes.length).associate { i -> val node=nodes.item(i);node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'","'").replace("\\n","\n").removeSurrounding("\"") }
        val byId=R.string::class.java.declaredFields.mapNotNull { field -> byName[field.name]?.let { field.getInt(null) to it } }.toMap()
        return object : UiStrings {
            override fun get(id:Int,vararg arguments:Any?) = String.format(locale,requireNotNull(byId[id]) { "Missing $id" },*arguments)
            override fun quantity(id:Int,count:Int) = error("Use resource contract tests for plurals")
        }
    }
    val chinese by lazy { load("values-b+zh+Hans",Locale.SIMPLIFIED_CHINESE) }
    val english by lazy { load("values",Locale.ENGLISH) }
}
