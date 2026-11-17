package com.rnotlnglgq.netprobe

import com.rnotlnglgq.netprobe.ui.AppLanguage
import org.junit.Assert.*
import org.junit.Test

class AppLanguageTest {
    @Test fun systemChoiceUsesEmptyLocaleListAndKnownLanguageAliasesAreRecognized() {
        assertEquals("",AppLanguage.SYSTEM.tag)
        assertEquals(AppLanguage.SYSTEM,AppLanguage.fromTags(""))
        assertEquals(AppLanguage.CHINESE,AppLanguage.fromTags("zh-CN"))
        assertEquals(AppLanguage.CHINESE,AppLanguage.fromTags("zh-Hans-CN,en"))
        assertEquals(AppLanguage.ENGLISH,AppLanguage.fromTags("en-US"))
    }
}
