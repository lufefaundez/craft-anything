package app.ofiofi.docxdocx.ui.shell

import uniffi.word_mobile_ffi.loadAndListSystemFonts

data class DocCommentItem(
    val id: Int,
    val author: String,
    val date: String,
    val resolved: Boolean,
    val parent: Int?,
    val text: String,
)

/**
 * Pure-Kotlin JSON parser for `review.comments` + merged system/document font loader.
 */
object DocCommentParser {
    fun parseList(json: String?): List<DocCommentItem> {
        val trimmed = json?.trim() ?: return emptyList()
        if (trimmed.isEmpty()) return emptyList()
        return splitTopLevelObjects(trimmed).mapNotNull { objJson ->
            val id = extractInt(objJson, "id") ?: return@mapNotNull null
            if (id < 0) return@mapNotNull null
            val parent = extractInt(objJson, "parent")?.takeIf { it >= 0 }
            DocCommentItem(
                id = id,
                author = extractString(objJson, "author") ?: "Usuario",
                date = extractString(objJson, "date") ?: "",
                resolved = extractBoolean(objJson, "resolved") ?: false,
                parent = parent,
                text = extractString(objJson, "text") ?: "",
            )
        }
    }

    private fun splitTopLevelObjects(json: String): List<String> {
        val arrayStart = json.indexOf('[')
        if (arrayStart < 0) return emptyList()
        val objects = mutableListOf<String>()
        var inString = false
        var escaped = false
        var depth = 0
        var objStart = -1
        for (i in arrayStart until json.length) {
            val ch = json[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (inString) {
                when (ch) {
                    '\\' -> escaped = true
                    '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        objects.add(json.substring(objStart, i + 1))
                        objStart = -1
                    }
                }
                ']' -> if (depth == 0) break
            }
        }
        return objects
    }

    private fun extractString(obj: String, key: String): String? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        val raw = regex.find(obj)?.groupValues?.getOrNull(1) ?: return null
        return raw
            .replace("""\"""", "\"")
            .replace("""\\""", "\\")
            .replace("""\n""", "\n")
            .replace("""\r""", "\r")
            .replace("""\t""", "\t")
    }

    private fun extractInt(obj: String, key: String): Int? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+)""")
        return regex.find(obj)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractBoolean(obj: String, key: String): Boolean? {
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""")
        return regex.find(obj)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
    }
}

object DocxFontCatalog {
    val preferredWordFonts: List<String> = listOf(
        "Aptos",
        "Calibri",
        "Carlito",
        "Times New Roman",
        "Caladea",
        "Cambria",
        "Arial",
        "Liberation Sans",
        "Georgia",
        "Verdana",
        "Tahoma",
        "Trebuchet MS",
        "Courier New",
        "STIX Two Math",
    )

    fun mergeFontFamilies(systemFamilies: List<String>): List<String> {
        val seen = LinkedHashSet<String>()
        val result = mutableListOf<String>()
        for (name in preferredWordFonts) {
            val trimmed = name.trim()
            if (trimmed.isNotEmpty() && seen.add(trimmed.lowercase())) {
                result.add(trimmed)
            }
        }
        val extra = systemFamilies
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(".") }
            .sortedBy { it.lowercase() }
        for (name in extra) {
            if (seen.add(name.lowercase())) {
                result.add(name)
            }
        }
        return result
    }

    fun loadAllFonts(): List<String> = runCatching {
        mergeFontFamilies(loadAndListSystemFonts())
    }.getOrDefault(preferredWordFonts)
}
