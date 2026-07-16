package com.dohealth.handheld.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.dohealth.handheld.data.model.InventoryItem
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExcelExporter {
    private const val TAG = "ExcelExporter"
    
    fun exportInventory(context: Context, items: List<InventoryItem>): File {
        Log.d(TAG, "Iniciando exportación de ${items.size} items únicos")
        
        if (items.isEmpty()) {
            throw IllegalArgumentException("No hay items para exportar")
        }
        
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Inventario")
        
        // Crear encabezados
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("#")
        headerRow.createCell(1).setCellValue("Datos")
        headerRow.createCell(2).setCellValue("Modo")
        headerRow.createCell(3).setCellValue("RSSI (dBm)")
        headerRow.createCell(4).setCellValue("Antena")
        headerRow.createCell(5).setCellValue("Repeticiones")
        headerRow.createCell(6).setCellValue("Última Lectura")
        
        // Estilo para encabezados
        val headerStyle = workbook.createCellStyle()
        val headerFont = workbook.createFont()
        headerFont.bold = true
        headerStyle.setFont(headerFont)
        
        for (i in 0..6) {
            headerRow.getCell(i).cellStyle = headerStyle
        }
        
        // Agregar datos - items únicos ordenados
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sortedItems = items.sortedByDescending { it.timestamp }
        
        sortedItems.forEachIndexed { index, item ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue((index + 1).toDouble())
            row.createCell(1).setCellValue(item.data)
            row.createCell(2).setCellValue(item.mode)
            
            // RSSI
            val rssiCell = row.createCell(3)
            if (item.rssi != 0) {
                rssiCell.setCellValue(item.rssi.toDouble())
            } else {
                rssiCell.setCellValue("")
            }
            
            // Antena
            val antennaCell = row.createCell(4)
            if (item.antenna != 0) {
                antennaCell.setCellValue(item.antenna.toDouble())
            } else {
                antennaCell.setCellValue("")
            }
            
            // Repeticiones
            row.createCell(5).setCellValue(item.readCount.toDouble())
            
            // Fecha/Hora de última lectura
            row.createCell(6).setCellValue(dateFormat.format(Date(item.timestamp)))
        }
        
        // Auto-ajustar columnas
        for (i in 0..6) {
            try {
                sheet.autoSizeColumn(i)
            } catch (e: Exception) {
                Log.w(TAG, "Error al auto-ajustar columna $i: ${e.message}")
            }
        }
        
        // Guardar archivo en directorio externo o interno según la versión
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "inventario_$timestamp.xlsx"
        
        val file: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - usar getExternalFilesDir (no requiere permisos)
            val externalDir = context.getExternalFilesDir("exports")
            if (externalDir == null) {
                // Fallback a directorio interno
                File(context.filesDir, fileName)
            } else {
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }
                File(externalDir, fileName)
            }
        } else {
            // Android 9 y anteriores - usar getExternalFilesDir
            val externalDir = context.getExternalFilesDir("exports")
            if (externalDir == null) {
                File(context.filesDir, fileName)
            } else {
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }
                File(externalDir, fileName)
            }
        }
        
        Log.d(TAG, "Guardando archivo en: ${file.absolutePath}")
        
        // Crear directorio si no existe
        file.parentFile?.mkdirs()
        
        // Asegurar que el directorio existe
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            val created = parentDir.mkdirs()
            Log.d(TAG, "Directorio creado: $created - ${parentDir.absolutePath}")
        }
        
        // Guardar el archivo
        try {
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
                outputStream.flush()
            }
            
            workbook.close()
            
            if (!file.exists() || file.length() == 0L) {
                throw Exception("El archivo no se creó correctamente o está vacío")
            }
            
            Log.d(TAG, "Archivo exportado exitosamente: ${file.absolutePath} (${file.length()} bytes)")
            
            return file
        } catch (e: Exception) {
            workbook.close()
            Log.e(TAG, "Error al guardar archivo: ${e.message}", e)
            throw Exception("Error al guardar el archivo Excel: ${e.message}", e)
        }
    }
    
    fun exportInventorySimple(context: Context, items: List<InventoryItem>): File {
        Log.d(TAG, "Iniciando exportación simple de ${items.size} items únicos a Excel")
        
        if (items.isEmpty()) {
            throw IllegalArgumentException("No hay items para exportar")
        }
        
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("RFID")
        
        // Crear encabezado
        val headerRow = sheet.createRow(0)
        headerRow.createCell(0).setCellValue("RFID")
        
        // Estilo para encabezado
        val headerStyle = workbook.createCellStyle()
        val headerFont = workbook.createFont()
        headerFont.bold = true
        headerStyle.setFont(headerFont)
        headerRow.getCell(0).cellStyle = headerStyle
        
        // Agregar datos - items únicos ordenados por timestamp
        val sortedItems = items.sortedByDescending { it.timestamp }
        
        sortedItems.forEachIndexed { index, item ->
            val row = sheet.createRow(index + 1)
            row.createCell(0).setCellValue(item.data)
        }
        
        // Auto-ajustar columna
        try {
            sheet.autoSizeColumn(0)
        } catch (e: Exception) {
            Log.w(TAG, "Error al auto-ajustar columna: ${e.message}")
        }
        
        // Guardar archivo en directorio externo o interno según la versión
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "inventario_rfid_$timestamp.xlsx"
        
        val file: File = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ - usar getExternalFilesDir (no requiere permisos)
            val externalDir = context.getExternalFilesDir("exports")
            if (externalDir == null) {
                // Fallback a directorio interno
                File(context.filesDir, fileName)
            } else {
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }
                File(externalDir, fileName)
            }
        } else {
            // Android 9 y anteriores
            val externalDir = context.getExternalFilesDir("exports")
            if (externalDir == null) {
                File(context.filesDir, fileName)
            } else {
                if (!externalDir.exists()) {
                    externalDir.mkdirs()
                }
                File(externalDir, fileName)
            }
        }
        
        Log.d(TAG, "Guardando archivo en: ${file.absolutePath}")
        
        // Asegurar que el directorio existe
        val parentDir = file.parentFile
        if (parentDir != null && !parentDir.exists()) {
            val created = parentDir.mkdirs()
            Log.d(TAG, "Directorio creado: $created - ${parentDir.absolutePath}")
        }
        
        // Guardar el archivo
        try {
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
                outputStream.flush()
            }
            
            workbook.close()
            
            if (!file.exists() || file.length() == 0L) {
                throw Exception("El archivo no se creó correctamente o está vacío")
            }
            
            Log.d(TAG, "Archivo exportado exitosamente: ${file.absolutePath} (${file.length()} bytes)")
            
            return file
        } catch (e: Exception) {
            workbook.close()
            Log.e(TAG, "Error al guardar archivo: ${e.message}", e)
            throw Exception("Error al guardar el archivo Excel: ${e.message}", e)
        }
    }
}

