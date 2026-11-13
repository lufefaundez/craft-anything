package app.ofiofi.docxdocx

import app.ofiofi.docxdocx.storage.DocTemplateId
import app.ofiofi.docxdocx.storage.SafDocumentHelper
import app.ofiofi.docxdocx.storage.WebDavClient
import app.ofiofi.docxdocx.storage.WebDavConfig
import app.ofiofi.docxdocx.storage.WebDavTransport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

private class FakeWebDavTransport : WebDavTransport {
    var uploadedUrl: String? = null
    var uploadedBytes: ByteArray = ByteArray(0)
    var propfindXml: String = ""

    override fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?,
    ): Pair<Int, ByteArray> =
        when (method) {
            "PROPFIND" -> 207 to propfindXml.toByteArray(StandardCharsets.UTF_8)
            "PUT" -> {
                uploadedUrl = url
                uploadedBytes = body ?: ByteArray(0)
                201 to ByteArray(0)
            }
            "GET" -> 200 to "PK_DOCX_MOCK".toByteArray(StandardCharsets.UTF_8)
            else -> 405 to ByteArray(0)
        }
}

class DocumentStorageTest {
    @Test
    fun webDavConfigNormalizesUrlsAndRejectsPathTraversal() {
        val cfg = WebDavConfig("https://cloud.example.cl/remote.php/dav/files/lufe", "lufe", "secret")
        assertEquals(
            "https://cloud.example.cl/remote.php/dav/files/lufe/Oficio.docx",
            cfg.resolveChildUrl("Oficio.docx"),
        )
        assertTrue(cfg.basicAuthHeader().startsWith("Basic "))

        var rejected = false
        try {
            cfg.resolveChildUrl("../etc/passwd")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun webDavClientParsesPropfindMultistatusAndRoundtripsPutGet() {
        val transport = FakeWebDavTransport().apply {
            propfindXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:multistatus xmlns:D="DAV:">
                  <D:response>
                    <D:href>/dav/</D:href>
                    <D:propstat><D:prop><D:displayname>dav</D:displayname><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Proyectos/</D:href>
                    <D:propstat><D:prop><D:displayname>Proyectos</D:displayname><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
                  </D:response>
                  <D:response>
                    <D:href>/dav/Oficio_Chile.docx</D:href>
                    <D:propstat><D:prop><D:displayname>Oficio_Chile.docx</D:displayname><D:getcontentlength>4096</D:getcontentlength><D:resourcetype/></D:prop></D:propstat>
                  </D:response>
                </D:multistatus>
            """.trimIndent()
        }
        val cfg = WebDavConfig("https://dav.example.cl/dav", "user", "pass")
        val client = WebDavClient(transport)

        val entries = client.listDirectory(cfg)
        assertEquals(2, entries.size)
        assertTrue(entries[0].isDirectory)
        assertEquals("Proyectos", entries[0].displayName)
        assertFalse(entries[1].isDirectory)
        assertEquals("Oficio_Chile.docx", entries[1].displayName)
        assertEquals(4096L, entries[1].contentLengthBytes)

        val payload = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        client.uploadBytes(cfg, "Oficio_Chile.docx", payload)
        assertEquals("https://dav.example.cl/dav/Oficio_Chile.docx", transport.uploadedUrl)
        assertArrayEquals(payload, transport.uploadedBytes)

        val downloaded = client.downloadBytes(cfg, "/dav/Oficio_Chile.docx")
        assertTrue(downloaded.isNotEmpty())
    }

    @Test
    fun safHelperInfersSupportedOfficeAndPdfMimeTypesAndChileanTemplatesExist() {
        assertEquals(SafDocumentHelper.DOCX_MIME, SafDocumentHelper.inferMimeType("borrador.DOCX"))
        assertEquals(SafDocumentHelper.ODT_MIME, SafDocumentHelper.inferMimeType("nota.odt"))
        assertEquals(SafDocumentHelper.RTF_MIME, SafDocumentHelper.inferMimeType("contrato.rtf"))
        assertEquals(SafDocumentHelper.PDF_MIME, SafDocumentHelper.inferMimeType("salida.pdf"))

        assertTrue(DocTemplateId.OFICIO_CHILE.seedParagraphs.any { it.contains("OFICIO ORDINARIO") })
        assertTrue(DocTemplateId.FORMAL_LETTER.seedParagraphs.isNotEmpty())
    }
}
