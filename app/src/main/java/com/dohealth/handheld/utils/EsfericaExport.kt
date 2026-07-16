package com.dohealth.handheld.utils

import com.dohealth.handheld.data.esferica.EsfericaCountPersistedSession
import com.dohealth.handheld.data.esferica.ReconcileResponseDto
import com.dohealth.handheld.domain.EsfericaReconcileLocal
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EsfericaExport {

    enum class EstadoLinea(val tag: String) {
        MATCH("MATCH"),
        FALTANTE("FALTANTE"),
        SOBRANTE("SOBRANTE"),
        DESCONOCIDO("DESCONOCIDO"),
    }

    /**
     * Una sola hoja CSV: cada fila de inventario esperado frente al RFID válido contado si hubo lectura coincidente
     * (misma etiqueta normalizada en escaneos únicos). Sin match, la columna derecha va vacía.
     */
    fun buildExpectedVsValidCountedCsv(session: EsfericaCountPersistedSession): String {
        fun q(cell: String) = "\"" + cell.replace("\"", "\"\"") + "\""
        val scannedUnique =
            EsfericaReconcileLocal.distinctNormalizedPreserveOrder(session.scannedRfidsOrdered).toSet()
        val sb = StringBuilder()
        sb.appendLine(
            listOf(
                "Cliente",
                "Almacén",
                "Codigo",
                "Descripcion",
                "RFID Esperado",
                "RFID contado válido",
            ).joinToString(",") { q(it) },
        )
        for (item in session.expectedItems) {
            val expectedRfid = EsfericaRfidNormalizer.normalize(item.rfid) ?: continue
            val countedValid = if (expectedRfid in scannedUnique) expectedRfid else ""
            sb.appendLine(
                listOf(
                    session.clientName,
                    session.warehouseName,
                    item.code ?: "",
                    item.description ?: "",
                    expectedRfid,
                    countedValid,
                ).joinToString(",") { q(it) },
            )
        }
        return sb.toString()
    }

    fun buildCsv(session: EsfericaCountPersistedSession): String {
        val reconcile = session.lastReconcile ?: error("Sin conciliar")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = fmt.format(Date())

        fun q(cell: String) = "\"" + cell.replace("\"", "\"\"") + "\""

        val sb = StringBuilder()
        sb.appendLine(
            listOf("Cliente", "Almacen", "FechaHora", "Codigo", "Descripcion", "RFID", "Lote", "Caducidad", "EstadoConteo")
                .joinToString(",") { q(it) },
        )

        fun emit(
            codigo: String,
            descr: String,
            rfid: String,
            lote: String,
            cad: String,
            estado: EstadoLinea,
        ) {
            val line = listOf(
                session.clientName,
                session.warehouseName,
                now,
                codigo,
                descr,
                rfid,
                lote,
                cad,
                estado.tag,
            ).joinToString(",") { q(it) }
            sb.append(line).append('\n')
        }

        fun walk(r: ReconcileResponseDto) {
            for (it in r.matched.orEmpty()) {
                emit(it.code ?: "", it.description ?: "", it.rfid ?: "", it.batch ?: "", it.expiration ?: "", EstadoLinea.MATCH)
            }
            for (it in r.missing.orEmpty()) {
                emit(it.code ?: "", it.description ?: "", it.rfid ?: "", it.batch ?: "", it.expiration ?: "", EstadoLinea.FALTANTE)
            }
            for (it in r.extraIdentified.orEmpty()) {
                emit(it.code ?: "", it.description ?: "", it.rfid ?: "", it.batch ?: "", it.expiration ?: "", EstadoLinea.SOBRANTE)
            }
            for (unknown in r.extraUnknown.orEmpty()) {
                emit("", "", unknown, "", "", EstadoLinea.DESCONOCIDO)
            }
        }
        walk(reconcile)
        return sb.toString()
    }

    fun buildXlsx(session: EsfericaCountPersistedSession): ByteArray {
        val reconcile = session.lastReconcile ?: error("Sin conciliar")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val now = fmt.format(Date())
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("Conteo RFID")
        var rowIdx = 0
        fun header() {
            val r = sheet.createRow(rowIdx++)
            val hdr = arrayOf("Cliente", "Almacén", "Fecha/hora", "Código", "Descripción", "RFID", "Lote", "Caducidad", "Estado conteo")
            hdr.forEachIndexed { i, t -> r.createCell(i).setCellValue(t) }
        }
        header()
        fun row(
            codigo: String,
            descr: String,
            rfid: String,
            lote: String,
            cad: String,
            estado: EstadoLinea,
        ) {
            val r = sheet.createRow(rowIdx++)
            r.createCell(0).setCellValue(session.clientName)
            r.createCell(1).setCellValue(session.warehouseName)
            r.createCell(2).setCellValue(now)
            r.createCell(3).setCellValue(codigo)
            r.createCell(4).setCellValue(descr)
            r.createCell(5).setCellValue(rfid)
            r.createCell(6).setCellValue(lote)
            r.createCell(7).setCellValue(cad)
            r.createCell(8).setCellValue(estado.tag)
        }
        fun walk(r: ReconcileResponseDto) {
            for (it in r.matched.orEmpty()) row(it.code ?: "", it.description ?: "", it.rfid ?: "", it.batch ?: "", it.expiration ?: "", EstadoLinea.MATCH)
            for (it in r.missing.orEmpty()) row(it.code ?: "", it.description ?: "", it.rfid ?: "", it.batch ?: "", it.expiration ?: "", EstadoLinea.FALTANTE)
            for (it in r.extraIdentified.orEmpty()) row(it.code ?: "", it.description ?: "", it.rfid ?: "", it.batch ?: "", it.expiration ?: "", EstadoLinea.SOBRANTE)
            for (unknown in r.extraUnknown.orEmpty()) row("", "", unknown, "", "", EstadoLinea.DESCONOCIDO)
        }
        walk(reconcile)
        for (i in 0..8) sheet.autoSizeColumn(i)
        return ByteArrayOutputStream().also { bos -> wb.write(bos); wb.close() }.toByteArray()
    }
}
