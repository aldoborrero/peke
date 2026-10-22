package dev.aldoborrero.peke.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Peke ships English and Spanish only, so every translatable string needs a Spanish entry
 */
class SpanishCoverageTest {
    private fun names(
        path: String,
        translatableOnly: Boolean,
    ): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        return listOf("string", "plurals")
            .flatMap { tag ->
                val nodes = doc.getElementsByTagName(tag)
                (0 until nodes.length)
                    .map { nodes.item(it) as Element }
                    .filter { !translatableOnly || it.getAttribute("translatable") != "false" }
                    .map { it.getAttribute("name") }
            }.toSet()
    }

    @Test
    fun everyStringHasASpanishTranslation() {
        val english = names("src/main/res/values/strings.xml", translatableOnly = true)
        val spanish = names("src/main/res/values-es/strings.xml", translatableOnly = false)
        assertEquals(emptyList<String>(), (english - spanish).sorted())
    }
}
