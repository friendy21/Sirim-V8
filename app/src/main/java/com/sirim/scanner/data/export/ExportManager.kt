package com.sirim.scanner.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sirim.scanner.data.db.QrRecord
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

    fun exportSkuToExcel(skuRecords: List<SkuRecord>, ocrRecords: List<QrRecord>): Uri {
        val file = createSkuExportFile()
        XSSFWorkbook().use { workbook ->
            val skuSheet = workbook.createSheet("SKU Records")
            skuSheet.setColumnWidth(0, 20 * 256)
            skuSheet.setColumnWidth(1, 20 * 256)
            skuSheet.setColumnWidth(2, 30 * 256)
            skuSheet.setColumnWidth(3, 30 * 256)
            skuSheet.setColumnWidth(4, 20 * 256)
            skuSheet.setColumnWidth(5, 20 * 256)
            skuSheet.setColumnWidth(6, 20 * 256)
            skuSheet.setColumnWidth(7, 25 * 256)
            skuSheet.setColumnWidth(8, 18 * 256)
            skuSheet.setColumnWidth(9, 20 * 256)

            val headerStyle = workbook.createHeaderStyle()
            val headerRow = skuSheet.createRow(0)
            listOf(
                "Barcode",
                "Batch No.",
                "Brand/Trademark",
                "Model",
                "Type",
                "Rating",
                "Size",
                "Linked Serial",
                "Verified",
                "Created"
            )
                .forEachIndexed { index, title ->
                    val cell = headerRow.createCell(index)
                    cell.setCellValue(title)
                    cell.cellStyle = headerStyle
                }

            val bodyStyle = workbook.createBodyStyle()
            val skuRowIndex = mutableMapOf<Long, Int>()
            skuRecords.forEachIndexed { index, record ->
                val row = skuSheet.createRow(index + 1)
                row.createCell(0).setCellValue(record.barcode)
                row.createCell(1).setCellValue(record.batchNo.orEmpty())
                row.createCell(2).setCellValue(record.brandTrademark.orEmpty())
                row.createCell(3).setCellValue(record.model.orEmpty())
                row.createCell(4).setCellValue(record.type.orEmpty())
                row.createCell(5).setCellValue(record.rating.orEmpty())
                row.createCell(6).setCellValue(record.size.orEmpty())
                row.createCell(7).setCellValue(record.linkedSerial.orEmpty())
                row.createCell(8).setCellValue(if (record.isVerified) "Verified" else "Pending")
                row.createCell(9).setCellValue(record.createdAt.toReadableDate())
                row.forEach { cell -> cell.cellStyle = bodyStyle }
                skuRowIndex[record.id] = index + 2
            }

            val ocrSheet = workbook.createSheet("SIRIM Scanner 2.0")
            ocrSheet.setColumnWidth(0, 60 * 256)
            ocrSheet.setColumnWidth(1, 25 * 256)
            ocrSheet.setColumnWidth(2, 25 * 256)
            ocrSheet.setColumnWidth(3, 25 * 256)
            ocrSheet.setColumnWidth(4, 25 * 256)
            ocrSheet.setColumnWidth(5, 18 * 256)
            ocrSheet.setColumnWidth(6, 20 * 256)

            val ocrHeader = ocrSheet.createRow(0)
            listOf(
                "Captured Text",
                "Label",
                "Field Source",
                "Field Note",
                "Linked SKU",
                "SKU Row",
                "Captured"
            )
                .forEachIndexed { index, title ->
                    val cell = ocrHeader.createCell(index)
                    cell.setCellValue(title)
                    cell.cellStyle = headerStyle
                }

            val skuRecordsById = skuRecords.associateBy { it.id }
            ocrRecords.forEachIndexed { index, record ->
                val row = ocrSheet.createRow(index + 1)
                row.createCell(0).setCellValue(record.payload)
                row.createCell(1).setCellValue(record.label.orEmpty())
                row.createCell(2).setCellValue(record.fieldSource.orEmpty())
                row.createCell(3).setCellValue(record.fieldNote.orEmpty())
                val linkedSku = record.skuId?.let(skuRecordsById::get)
                val skuCellValue = linkedSku?.barcode ?: "Unlinked"
                row.createCell(4).setCellValue(skuCellValue)
                val rowNumber = record.skuId?.let(skuRowIndex::get)
                row.createCell(5).setCellValue(rowNumber?.let { "Row $it" }.orEmpty())
                row.createCell(6).setCellValue(record.capturedAt.toReadableDate())
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
