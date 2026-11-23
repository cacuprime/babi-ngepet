package id.dompetku.importer

import id.dompetku.core.t

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfPasswordRequired : Exception(t("PDF ini dikunci kata sandi"))

/** Device-only: turn files into text lines (on-device OCR and PDF text). Parsers do the rest. */
object Extractors {

    suspend fun imageLines(context: Context, uri: Uri): List<String> = withContext(Dispatchers.IO) {
        ocr(InputImage.fromFilePath(context, uri))
    }

    private fun ocr(image: InputImage): List<String> {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return rows(Tasks.await(client.process(image)))
        } finally {
            client.close()
        }
    }

    /** OCR lines grouped into visual rows ("Total   Rp35.000" becomes one line), top to bottom. */
    private fun rows(text: Text): List<String> {
        val lines = text.textBlocks.flatMap { it.lines }.filter { it.boundingBox != null }
            .sortedBy { it.boundingBox!!.centerY() }
        val out = ArrayList<MutableList<Text.Line>>()
        for (l in lines) {
            val box = l.boundingBox!!
            val row = out.lastOrNull()
            val ref = row?.first()?.boundingBox
            if (ref != null && kotlin.math.abs(box.centerY() - ref.centerY()) < ref.height() / 2) row.add(l) else out.add(mutableListOf(l))
        }
        return out.map { r -> r.sortedBy { it.boundingBox!!.left }.joinToString(" ") { it.text } }
    }

    /** PDF text in content order; scanned PDFs fall back to OCR of each page. The password is never stored. */
    suspend fun pdfLines(context: Context, uri: Uri, password: String?): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            val doc = try {
                context.contentResolver.openInputStream(uri)!!.use { PDDocument.load(it, password ?: "") }
            } catch (e: InvalidPasswordException) {
                throw PdfPasswordRequired()
            }
            doc.use { d ->
                val text = PDFTextStripper().apply { sortByPosition = false }.getText(d)
                if (text.isNotBlank()) return@use text.lines()
                val renderer = PDFRenderer(d)
                (0 until minOf(d.numberOfPages, 20)).flatMap { p ->
                    val bmp: Bitmap = renderer.renderImageWithDPI(p, 200f)
                    try { ocr(InputImage.fromBitmap(bmp, 0)) } finally { bmp.recycle() }
                }
            }
        }
    }

    class Read(val drafts: List<Draft>, val source: String, val warning: String?)

    /** Text of any supported file, turned into drafts (plus a warning when a statement doesn't add up). */
    suspend fun drafts(context: Context, uri: Uri, mime: String, password: String?, now: java.time.Instant): Result<Read> =
        runCatching {
            when {
                mime.startsWith("image/") -> {
                    val lines = imageLines(context, uri)
                    val statement = StatementParser.parse(lines)
                    if (statement.size >= 2) Read(statement, "STATEMENT", StatementParser.check(lines, statement))
                    else Read(listOfNotNull(ReceiptParser.parse(lines, now)), "SCREENSHOT", null)
                }
                mime == "application/pdf" -> {
                    val lines = pdfLines(context, uri, password).getOrThrow()
                    val statement = StatementParser.parse(lines)
                    when {
                        statement.isNotEmpty() -> Read(statement, "STATEMENT", StatementParser.check(lines, statement))
                        // An unreadable statement must not be read as one receipt (it would pick a balance as a "total").
                        StatementParser.looksLikeStatement(lines) ->
                            error(t("Format rekening koran ini belum dikenali. Kirim contohnya ke pembuat aplikasi, atau ekspor CSV dari m-banking."))
                        else -> Read(listOfNotNull(ReceiptParser.parse(lines, now)), "SCREENSHOT", null)
                    }
                }
                mime.contains("spreadsheet") || mime.contains("excel") ->
                    Read(TableParser.parse(context.contentResolver.openInputStream(uri)!!.use { Xlsx.rows(it) }), "STATEMENT", null)
                else -> {
                    val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                    Read(TableParser.parse(Csv.rows(text)), "STATEMENT", null)
                }
            }
        }
}
