package app.ofiofi.docxdocx.print

import android.content.Context
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import app.ofiofi.docxdocx.canvas.BitmapTileCache
import uniffi.word_mobile_ffi.MobileDocumentSession
import uniffi.word_mobile_ffi.TileRequest
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Pure-Kotlin helpers for Android PrintManager pagination & page-size mapping.
 */
object PrintLayoutHelper {
    /**
     * Converts PostScript points (72 pt/in) to Android PrintAttributes mils (1000 mils/in).
     */
    fun pointsToMils(points: Float): Int =
        ((points / 72f) * 1000f).roundToInt().coerceAtLeast(1000)

    /**
     * Expands an array of `(start, end)` page ranges into sorted 0-based page indices.
     */
    fun expandPageRanges(ranges: List<Pair<Int, Int>>, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        if (ranges.isEmpty()) return (0 until totalPages).toList()
        val selected = linkedSetOf<Int>()
        for ((start, end) in ranges) {
            val s = start.coerceIn(0, totalPages - 1)
            val e = end.coerceIn(s, totalPages - 1)
            for (idx in s..e) selected.add(idx)
        }
        return selected.sorted()
    }

    fun selectMediaSizeLabel(widthPt: Float, heightPt: Float): String {
        val diffOficio = kotlin.math.abs(widthPt - 612f) + kotlin.math.abs(heightPt - 935.43f)
        val diffA4 = kotlin.math.abs(widthPt - 595.28f) + kotlin.math.abs(heightPt - 841.89f)
        val diffLegal = kotlin.math.abs(widthPt - 612f) + kotlin.math.abs(heightPt - 1008f)
        return when {
            diffOficio < 8f -> "Oficio Chile"
            diffA4 < 8f -> "ISO A4"
            diffLegal < 8f -> "US Legal"
            else -> "Carta"
        }
    }
}

class DocxPrintDocumentAdapter(
    private val context: Context,
    private val jobTitle: String,
    private val session: MobileDocumentSession,
) : PrintDocumentAdapter() {
    private var printAttributes: PrintAttributes? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        printAttributes = newAttributes
        val count = session.pageCount().toInt().coerceAtLeast(1)
        val safeTitle = if (jobTitle.endsWith(".docx", ignoreCase = true)) jobTitle else "$jobTitle.docx"
        val info = PrintDocumentInfo.Builder(safeTitle)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(count)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val attrs = printAttributes ?: PrintAttributes.Builder().build()
        val pdfDocument = PrintedPdfDocument(context, attrs)
        try {
            val totalPages = session.pageCount().toInt().coerceAtLeast(1)
            val ranges = pages.map { range ->
                if (range == PageRange.ALL_PAGES) 0 to (totalPages - 1) else range.start to range.end
            }
            val selectedPages = PrintLayoutHelper.expandPageRanges(ranges, totalPages)
            for (pageIndex in selectedPages) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val sz = session.pageSizePt(pageIndex.toUInt())
                val wPt = sz.widthPt
                val hPt = sz.heightPt
                val scale = 2.0f
                val wPx = (wPt * scale).roundToInt().coerceAtLeast(1)
                val hPx = (hPt * scale).roundToInt().coerceAtLeast(1)
                val rgba = session.renderTile(
                    TileRequest(
                        pageIndex = pageIndex.toUInt(),
                        dpiScale = scale,
                        tileXPx = 0u,
                        tileYPx = 0u,
                        tileWPx = wPx.toUInt(),
                        tileHPx = hPx.toUInt(),
                        darkPage = false,
                    ),
                )
                val bitmap = BitmapTileCache.rgbaBytesToBitmap(rgba, wPx, hPx) ?: continue
                val pdfPage: PdfDocument.Page = pdfDocument.startPage(pageIndex)
                val pageRect = Rect(0, 0, pdfPage.info.pageWidth, pdfPage.info.pageHeight)
                pdfPage.canvas.drawBitmap(bitmap, null, pageRect, null)
                pdfDocument.finishPage(pdfPage)
                bitmap.recycle()
            }
            pdfDocument.writeTo(FileOutputStream(destination.fileDescriptor))
            callback.onWriteFinished(pages)
        } catch (t: Throwable) {
            callback.onWriteFailed(t.message ?: "Error al imprimir el documento")
        } finally {
            pdfDocument.close()
        }
    }

    companion object {
        fun launchPrintJob(context: Context, title: String, session: MobileDocumentSession) {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
            val adapter = DocxPrintDocumentAdapter(context, title, session)
            manager.print("DocxDocx - $title", adapter, PrintAttributes.Builder().build())
        }
    }
}
