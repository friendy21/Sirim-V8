package com.sirim.scanner.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sirim.scanner.data.db.SkuRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class ExportManager(private val context: Context) {

    fun exportSkuToExcel(records: List<SkuRecord>): Uri {
        val file = createSkuExportFile()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("SKU Records")
            sheet.setColumnWidth(0, 20 * 256)
            sheet.setColumnWidth(1, 30 * 256)
            sheet.setColumnWidth(2, 30 * 256)
            sheet.setColumnWidth(3, 20 * 256)
            sheet.setColumnWidth(4, 20 * 256)
            sheet.setColumnWidth(5, 20 * 256)

            val headerStyle = workbook.createHeaderStyle()
            val headerRow = sheet.createRow(0)
            listOf("Barcode", "Brand/Trademark", "Model", "Type", "Rating", "Created")
                .forEachIndexed { index, title ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(title)
                    cell.cellStyle = headerStyle
                }

            val bodyStyle = workbook.createBodyStyle()
            records.forEachIndexed { index, record ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(record.barcode)
                row.createCell(1).setCellValue(record.brandTrademark.orEmpty())
                row.createCell(2).setCellValue(record.model.orEmpty())
                row.createCell(3).setCellValue(record.type.orEmpty())
                row.createCell(4).setCellValue(record.rating.orEmpty())
                row.createCell(5).setCellValue(record.createdAt.toReadableDate())
                row.forEach { cell -> cell.cellStyle = bodyStyle }
            }

            FileOutputStream(file).use { output ->
                workbook.write(output)
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun createSkuExportFile(): File {
        val directory = getSkuExportDirectory()
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "sku_export_$timestamp.xlsx"
        return File(directory, fileName)
    }

    private fun getSkuExportDirectory(): File = File(context.getExternalFilesDir(null), "exports/sku")

    private fun Long.toReadableDate(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return formatter.format(Date(this))
    }

    private fun XSSFWorkbook.createHeaderStyle(): XSSFCellStyle {
        val font: XSSFFont = createFont().apply {
            bold = true
            color = org.apache.poi.ss.usermodel.IndexedColors.WHITE.index
        }
        return createCellStyle().apply {
            setFont(font)
            fillForegroundColor = org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
        }
    }

    private fun XSSFWorkbook.createBodyStyle(): CellStyle = createCellStyle().apply {
        alignment = HorizontalAlignment.LEFT
    }
}
