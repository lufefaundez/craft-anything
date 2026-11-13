package app.ofiofi.docxdocx.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import uniffi.word_mobile_ffi.MobileDocumentSession
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class StorageProviderKind {
    LOCAL_OR_DRIVE_SAF,
    WEBDAV,
}

enum class DocTemplateId(
    val titleEsCl: String,
    val subtitleEsCl: String,
    val seedParagraphs: List<String>,
) {
    BLANK(
        titleEsCl = "Documento en blanco",
        subtitleEsCl = "Tamaño Carta • Márgenes normales (es-CL)",
        seedParagraphs = emptyList(),
    ),
    FORMAL_LETTER(
        titleEsCl = "Carta Formal",
        subtitleEsCl = "Encabezado formal, fecha, destinatario y firma",
        seedParagraphs = listOf(
            "Santiago, 12 de noviembre de 2026",
            "",
            "Señor(a):",
            "Presente",
            "",
            "De mi consideración:",
            "",
            "Por medio de la presente me dirijo a usted con el objeto de exponer lo siguiente...",
            "",
            "Saluda atentamente,",
        ),
    ),
    OFICIO_CHILE(
        titleEsCl = "Oficio Ordinario Chile",
        subtitleEsCl = "Estructura N° / ANT. / MAT. / DE / A",
        seedParagraphs = listOf(
            "OFICIO ORDINARIO N° ____ / 2026",
            "ANT.: Sin antecedentes.",
            "MAT.: Lo que indica.",
            "SANTIAGO, ",
            "",
            "DE : REMITENTE",
            "A  : DESTINATARIO",
            "",
            "1. Informo a usted que...",
            "",
            "DISTRIBUCIÓN:",
            "1.- Destinatario.",
            "2.- Archivo.",
        ),
    ),
    EXECUTIVE_REPORT(
        titleEsCl = "Informe Ejecutivo",
        subtitleEsCl = "Portada resumida, objetivos, desarrollo y conclusiones",
        seedParagraphs = listOf(
            "INFORME EJECUTIVO",
            "1. Resumen Ejecutivo",
            "Describa aquí los hallazgos principales del documento.",
            "2. Antecedentes y Análisis",
            "Detalle técnico y financiero.",
            "3. Conclusiones y Próximos Pasos",
        ),
    ),
}

data class WebDavConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    fun normalizedBaseUrl(): String {
        val trimmed = baseUrl.trim()
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            "La URL WebDAV debe comenzar con https:// o http://"
        }
        return trimmed.trimEnd('/') + "/"
    }

    fun resolveChildUrl(childRelativePath: String): String {
        val clean = childRelativePath.trim().trimStart('/')
        require(!clean.contains("..")) { "Ruta inválida: no se permiten segmentos '..'" }
        val base = URI(normalizedBaseUrl())
        return base.resolve(clean).toString()
    }

    fun basicAuthHeader(): String {
        val raw = "$username:$password".toByteArray(StandardCharsets.UTF_8)
        return "Basic " + Base64.getEncoder().encodeToString(raw)
    }
}

data class WebDavEntry(
    val href: String,
    val displayName: String,
    val isDirectory: Boolean,
    val contentLengthBytes: Long?,
)

data class RecentDocEntry(
    val title: String,
    val uriOrUrl: String,
    val provider: StorageProviderKind,
    val updatedAtEpochMs: Long,
)

interface WebDavTransport {
    fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray? = null,
    ): Pair<Int, ByteArray>
}

class HttpUrlConnectionWebDavTransport : WebDavTransport {
    override fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): Pair<Int, ByteArray> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12_000
            readTimeout = 20_000
            doInput = true
            for ((k, v) in headers) {
                setRequestProperty(k, v)
            }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val payload = stream?.use { it.readBytes() } ?: ByteArray(0)
        conn.disconnect()
        return code to payload
    }
}

class WebDavClient(
    private val transport: WebDavTransport = HttpUrlConnectionWebDavTransport(),
) {
    fun listDirectory(config: WebDavConfig, relativeDir: String = ""): List<WebDavEntry> {
        val targetUrl = config.resolveChildUrl(relativeDir)
        val propfindBody = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
                <D:getcontentlength/>
              </D:prop>
            </D:propfind>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val (status, bytes) = transport.request(
            method = "PROPFIND",
            url = targetUrl,
            headers = mapOf(
                "Authorization" to config.basicAuthHeader(),
                "Depth" to "1",
                "Content-Type" to "application/xml; charset=utf-8",
            ),
            body = propfindBody,
        )
        require(status in 200..299 || status == 207) {
            "Error WebDAV PROPFIND ($status)"
        }
        return parsePropfindXml(bytes.toString(StandardCharsets.UTF_8), targetUrl)
    }

    fun downloadBytes(config: WebDavConfig, hrefOrRelativePath: String): ByteArray {
        val targetUrl = if (hrefOrRelativePath.startsWith("http://") || hrefOrRelativePath.startsWith("https://")) {
            hrefOrRelativePath
        } else {
            config.resolveChildUrl(hrefOrRelativePath)
        }
        val (status, bytes) = transport.request(
            method = "GET",
            url = targetUrl,
            headers = mapOf("Authorization" to config.basicAuthHeader()),
        )
        require(status in 200..299) { "Error WebDAV GET ($status)" }
        return bytes
    }

    fun uploadBytes(
        config: WebDavConfig,
        relativePath: String,
        bytes: ByteArray,
        contentType: String = SafDocumentHelper.DOCX_MIME,
    ) {
        val targetUrl = config.resolveChildUrl(relativePath)
        val (status, _) = transport.request(
            method = "PUT",
            url = targetUrl,
            headers = mapOf(
                "Authorization" to config.basicAuthHeader(),
                "Content-Type" to contentType,
            ),
            body = bytes,
        )
        require(status in 200..299) { "Error WebDAV PUT ($status)" }
    }

    companion object {
        private val responseRegex = Regex("<(?:[a-zA-Z0-9_-]+:)?response\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?response>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val hrefRegex = Regex("<(?:[a-zA-Z0-9_-]+:)?href\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?href>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val displayNameRegex = Regex("<(?:[a-zA-Z0-9_-]+:)?displayname\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?displayname>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val lengthRegex = Regex("<(?:[a-zA-Z0-9_-]+:)?getcontentlength\\b[^>]*>(.*?)</(?:[a-zA-Z0-9_-]+:)?getcontentlength>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val collectionRegex = Regex("<(?:[a-zA-Z0-9_-]+:)?collection\\s*/?>", RegexOption.IGNORE_CASE)

        fun parsePropfindXml(xml: String, requestUrl: String = ""): List<WebDavEntry> {
            val reqPath = runCatching { URI(requestUrl).path.trimEnd('/') }.getOrDefault("")
            return responseRegex.findAll(xml).mapNotNull { match ->
                val block = match.groupValues[1]
                val href = hrefRegex.find(block)?.groupValues?.get(1)?.trim() ?: return@mapNotNull null
                val cleanHrefPath = href.trimEnd('/')
                if (reqPath.isNotEmpty() && cleanHrefPath == reqPath) {
                    return@mapNotNull null
                }
                val rawName = displayNameRegex.find(block)?.groupValues?.get(1)?.trim().orEmpty()
                val fallbackName = cleanHrefPath.substringAfterLast('/').ifEmpty { href }
                val isDir = collectionRegex.containsMatchIn(block) || href.endsWith("/")
                val size = lengthRegex.find(block)?.groupValues?.get(1)?.trim()?.toLongOrNull()
                WebDavEntry(
                    href = href,
                    displayName = rawName.ifEmpty { fallbackName },
                    isDirectory = isDir,
                    contentLengthBytes = size,
                )
            }.toList()
        }
    }
}

object SafDocumentHelper {
    const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val DOC_MIME = "application/msword"
    const val ODT_MIME = "application/vnd.oasis.opendocument.text"
    const val RTF_MIME = "application/rtf"
    const val PDF_MIME = "application/pdf"

    val OPEN_DOCUMENT_MIME_TYPES = arrayOf(
        DOCX_MIME,
        DOC_MIME,
        ODT_MIME,
        RTF_MIME,
        "text/rtf",
        "text/plain",
        "*/*",
    )

    fun inferExtension(filename: String): String =
        filename.substringAfterLast('.', "").lowercase()

    fun inferMimeType(filename: String): String =
        when (inferExtension(filename)) {
            "docx" -> DOCX_MIME
            "doc" -> DOC_MIME
            "odt" -> ODT_MIME
            "rtf" -> RTF_MIME
            "pdf" -> PDF_MIME
            else -> "application/octet-stream"
        }

    fun readBytes(resolver: ContentResolver, uri: Uri): ByteArray =
        resolver.openInputStream(uri)?.use { input ->
            val buf = ByteArrayOutputStream()
            input.copyTo(buf)
            buf.toByteArray()
        } ?: ByteArray(0)

    fun writeBytes(resolver: ContentResolver, uri: Uri, bytes: ByteArray) {
        resolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(bytes)
            out.flush()
        }
    }

    fun createSessionFromTemplate(
        template: DocTemplateId,
        regionTag: String = "es-CL",
    ): MobileDocumentSession {
        val session = MobileDocumentSession.newBlank(regionTag)
        for ((index, line) in template.seedParagraphs.withIndex()) {
            if (line.isNotEmpty()) {
                session.imeCommitText(line, joinWithPrevious = false)
            }
            if (index < template.seedParagraphs.lastIndex) {
                session.executeCommand("text.newParagraph", "{}")
            }
        }
        return session
    }
}

class RecentDocumentStore(context: Context) {
    private val prefs = context.getSharedPreferences("ofiofi_docx_recent", Context.MODE_PRIVATE)

    fun loadRecent(): List<RecentDocEntry> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        RecentDocEntry(
                            title = o.getString("title"),
                            uriOrUrl = o.getString("uriOrUrl"),
                            provider = StorageProviderKind.valueOf(o.getString("provider")),
                            updatedAtEpochMs = o.getLong("updatedAtEpochMs"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun record(entry: RecentDocEntry) {
        val updated = (listOf(entry) + loadRecent().filterNot { it.uriOrUrl == entry.uriOrUrl }).take(15)
        val arr = JSONArray()
        updated.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("uriOrUrl", item.uriOrUrl)
                    put("provider", item.provider.name)
                    put("updatedAtEpochMs", item.updatedAtEpochMs)
                },
            )
        }
        prefs.edit().putString(KEY_RECENT, arr.toString()).apply()
    }

    companion object {
        private const val KEY_RECENT = "recent_docs_v1"
    }
}
