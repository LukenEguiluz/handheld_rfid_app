package com.dohealth.handheld.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.dohealth.handheld.data.model.InventoryItem
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object TextExporter {
    private const val TAG = "TextExporter"
    
    fun generateInventoryContent(items: List<InventoryItem>, mode: Int = 0): String {
        if (items.isEmpty()) {
            throw IllegalArgumentException("No hay items para exportar")
        }
        
        val content = StringBuilder()
        
        // Modo RFID: solo exportar los códigos
        if (mode == 0) { // MODE_RFID
            val rfidItems = items.filter { it.mode == "RFID" }.sortedByDescending { it.timestamp }
            
            if (rfidItems.isEmpty()) {
                throw IllegalArgumentException("No hay etiquetas RFID para exportar")
            }
            
            rfidItems.forEach { item ->
                content.append(item.data).append("\n")
            }
        } else { // Modo Barcode: código + veces leído
            val barcodeItems = items.filter { it.mode == "Barcode" }.sortedByDescending { it.timestamp }
            
            if (barcodeItems.isEmpty()) {
                throw IllegalArgumentException("No hay códigos de barras para exportar")
            }
            
            barcodeItems.forEach { item ->
                // Formato: código,veces_leído
                content.append("${item.data},${item.readCount}\n")
            }
        }
        
        return content.toString()
    }
    
    @Deprecated("Use generateInventoryContent instead", ReplaceWith("generateInventoryContent(items)"))
    fun exportInventory(context: Context, items: List<InventoryItem>): File {
        Log.d(TAG, "Iniciando exportación de ${items.size} items únicos a TXT")
        
        if (items.isEmpty()) {
            throw IllegalArgumentException("No hay items para exportar")
        }
        
        // Crear contenido del archivo
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val headerDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        
        val content = StringBuilder()
        
        // Encabezado
        content.append("=".repeat(80)).append("\n")
        content.append("INVENTARIO - doHealth Handheld\n")
        content.append("Fecha de exportación: $headerDate\n")
        content.append("Total de items únicos: ${items.size}\n")
        content.append("=".repeat(80)).append("\n\n")
        
        // Encabezados de columnas
        content.append(String.format("%-5s %-30s %-8s %-10s %-8s %-8s %-20s\n",
            "#", "Datos", "Modo", "RSSI", "Antena", "Rep.", "Última Lectura"))
        content.append("-".repeat(80)).append("\n")
        
        // Agregar datos - items únicos ordenados por timestamp
        val sortedItems = items.sortedByDescending { it.timestamp }
        
        sortedItems.forEachIndexed { index, item ->
            val rssi = if (item.rssi != 0) "${item.rssi} dBm" else "-"
            val antenna = if (item.antenna != 0) item.antenna.toString() else "-"
            val lastRead = dateFormat.format(Date(item.timestamp))
            
            content.append(String.format("%-5d %-30s %-8s %-10s %-8s %-8d %-20s\n",
                index + 1,
                item.data.take(30), // Limitar a 30 caracteres
                item.mode,
                rssi,
                antenna,
                item.readCount,
                lastRead
            ))
        }
        
        content.append("\n").append("=".repeat(80)).append("\n")
        content.append("FIN DEL REPORTE\n")
        content.append("=".repeat(80)).append("\n")
        
        // Guardar archivo
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "inventario_$timestamp.txt"
        
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
            FileWriter(file).use { writer ->
                writer.write(content.toString())
                writer.flush()
            }
            
            if (!file.exists() || file.length() == 0L) {
                throw Exception("El archivo no se creó correctamente o está vacío")
            }
            
            Log.d(TAG, "Archivo exportado exitosamente: ${file.absolutePath} (${file.length()} bytes)")
            
            return file
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar archivo: ${e.message}", e)
            throw Exception("Error al guardar el archivo TXT: ${e.message}", e)
        }
    }
}

