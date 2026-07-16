package com.dohealth.handheld.utils

import android.content.Context
import android.net.Uri
import com.dohealth.handheld.data.hgm.HgmCatalogImportResult
import com.dohealth.handheld.data.hgm.HgmCatalogRow
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.Normalizer
import java.nio.charset.Charset
import java.util.Locale

object HgmCatalogImporter {

    private enum class Col { UNIDAD, SKU, HEMOCOMPONENTE, GRUPO }

    fun importFromUri(context: Context, uri: Uri, displayName: String?): HgmCatalogImportResult {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val name = displayName?.lowercase(Locale.ROOT).orEmpty()
        val isExcel = mime.contains("spreadsheet", ignoreCase = true) ||
            mime.contains("excel", ignoreCase = true) ||
            name.endsWith(".xlsx") ||
            name.endsWith(".xls")
        return context.contentResolver.openInputStream(uri)?.use { input ->
            if (isExcel) parseExcel(input) else parseCsv(input)
        } ?: HgmCatalogImportResult(emptyList(), 0, listOf("No se pudo abrir el archivo"))
    }

    private fun parseCsv(input: java.io.InputStream): HgmCatalogImportResult {
        val bytes = input.readBytes()
        val text = decodeTextBestEffort(bytes)
        val lines = text.lineSequence()
            .map { it.removePrefix("\uFEFF") }
            .toList()
        if (lines.isEmpty()) {
            return HgmCatalogImportResult(emptyList(), 0, listOf("El archivo está vacío"))
        }
        val delimiter = detectDelimiter(lines.first())
        val headerCells = splitCsvLine(lines.first(), delimiter).map { normalizeHeader(it) }
        val colMap = mapColumns(headerCells)
            ?: return HgmCatalogImportResult(
                emptyList(),
                0,
                listOf("Encabezados no reconocidos. Se requieren: Unidad, SKU, Hemocomponente, Grupo ABO y Rh"),
            )
        val dataRows = lines.drop(1).map { splitCsvLine(it, delimiter) }
        return parseRows(dataRows, colMap)
    }

    /**
     * Excel en Windows suele exportar CSV como Windows-1252/ANSI.
     * Intentamos UTF-8 primero; si no cuadra, hacemos fallback a Windows-1252 para conservar acentos (ej. “Aféresis”).
     */
    private fun decodeTextBestEffort(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        // BOM UTF-8
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return bytes.toString(Charsets.UTF_8)
        }
        val utf8 = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
        if (utf8 != null && !utf8.contains('\uFFFD') && looksLikeUtf8(bytes)) return utf8
        return bytes.toString(Charset.forName("windows-1252"))
    }

    private fun looksLikeUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b <= 0x7F -> i++
                b in 0xC2..0xDF -> {
                    if (i + 1 >= bytes.size) return false
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    if (b2 !in 0x80..0xBF) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= bytes.size) return false
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val b3 = bytes[i + 2].toInt() and 0xFF
                    if (b2 !in 0x80..0xBF || b3 !in 0x80..0xBF) return false
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= bytes.size) return false
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val b3 = bytes[i + 2].toInt() and 0xFF
                    val b4 = bytes[i + 3].toInt() and 0xFF
                    if (b2 !in 0x80..0xBF || b3 !in 0x80..0xBF || b4 !in 0x80..0xBF) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    private fun parseExcel(input: java.io.InputStream): HgmCatalogImportResult {
        return runCatching {
            val formatter = DataFormatter()
            WorkbookFactory.create(input).use { wb ->
                val sheet = wb.getSheetAt(0)
                val headerRow = sheet.getRow(0)
                    ?: return HgmCatalogImportResult(emptyList(), 0, listOf("La hoja no tiene encabezados"))
                val headerCells = (0 until headerRow.lastCellNum.toInt()).map { ci ->
                    normalizeHeader(formatter.formatCellValue(headerRow.getCell(ci)))
                }
                val colMap = mapColumns(headerCells)
                    ?: return HgmCatalogImportResult(
                        emptyList(),
                        0,
                        listOf("Encabezados no reconocidos. Se requieren: Unidad, SKU, Hemocomponente, Grupo ABO y Rh"),
                    )
                val dataLines = (1..sheet.lastRowNum).mapNotNull { ri ->
                    sheet.getRow(ri)?.let { row ->
                        (0 until row.lastCellNum.toInt()).map { ci ->
                            formatter.formatCellValue(row.getCell(ci)).trim()
                        }
                    }
                }
                parseRows(dataLines, colMap)
            }
        }.getOrElse { e ->
            HgmCatalogImportResult(emptyList(), 0, listOf("Error al leer Excel: ${e.message ?: "desconocido"}"))
        }
    }

    private fun parseRows(
        dataRows: List<List<String>>,
        colMap: Map<Col, Int>,
    ): HgmCatalogImportResult {
        val rows = mutableListOf<HgmCatalogRow>()
        var skipped = 0
        val warnings = mutableListOf<String>()
        dataRows.forEachIndexed { idx, cells ->
            if (cells.all { it.isBlank() }) {
                skipped++
                return@forEachIndexed
            }
            val unidad = cells.getOrNull(colMap.getValue(Col.UNIDAD)).orEmpty().trim()
            val sku = cells.getOrNull(colMap.getValue(Col.SKU)).orEmpty().trim()
            val hemo = cells.getOrNull(colMap.getValue(Col.HEMOCOMPONENTE)).orEmpty().trim()
            val grupo = cells.getOrNull(colMap.getValue(Col.GRUPO)).orEmpty().trim()
            if (sku.isEmpty()) {
                skipped++
                warnings.add("Fila ${idx + 2}: sin SKU")
                return@forEachIndexed
            }
            rows.add(HgmCatalogRow(unidad, sku, hemo, grupo))
        }
        return HgmCatalogImportResult(rows, skipped, warnings)
    }

    private fun mapColumns(normalizedHeaders: List<String>): Map<Col, Int>? {
        val map = mutableMapOf<Col, Int>()
        normalizedHeaders.forEachIndexed { i, h ->
            when {
                h == "unidad" || h.startsWith("unidad") -> map[Col.UNIDAD] = i
                h == "sku" -> map[Col.SKU] = i
                h.contains("hemocomponente") -> map[Col.HEMOCOMPONENTE] = i
                h.contains("grupo") && h.contains("abo") -> map[Col.GRUPO] = i
            }
        }
        return if (Col.values().all { map.containsKey(it) }) map else null
    }

    /** Solo para reconocer encabezados; las celdas de datos conservan acentos. */
    private fun normalizeHeader(raw: String): String {
        val n = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return n
    }

    private fun detectDelimiter(header: String): Char =
        when {
            header.count { it == ';' } > header.count { it == ',' } -> ';'
            header.count { it == '\t' } > header.count { it == ',' } -> '\t'
            else -> ','
        }

    private fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    out.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        out.add(sb.toString().trim())
        return out
    }
}
