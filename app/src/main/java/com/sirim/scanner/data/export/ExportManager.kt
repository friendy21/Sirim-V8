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
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class ExportManager(private val context: Context) {

    fun exportSkuToExcel(skuRecord: SkuRecord, ocrRecords: List<QrRecord>): SkuExportResult {
        val file = createSkuExportFile(skuRecord.barcode)
        val metadata = XSSFWorkbook().use { workbook ->
            val exportMeta = SkuWorkbookWriter.populate(workbook, skuRecord, ocrRecords)
            FileOutputStream(file).use { output ->
                workbook.write(output)
            }
            exportMeta
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return SkuExportResult(
            uri = uri,
            fileName = file.name,
            fieldCount = metadata.fieldCount,
            ocrCount = metadata.ocrCount
        )
    }

    private fun createSkuExportFile(barcode: String): File {
        val directory = getSkuExportDirectory()
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val sanitizedBarcode = barcode.trim().ifBlank { "unknown" }.sanitizeForFilename()
        val fileName = "sku_${sanitizedBarcode}.xlsx"
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

    private fun Row.applyStyle(style: CellStyle) {
        for (cell in this) {
            cell.cellStyle = style
        }
    }

    private fun String.sanitizeForFilename(): String {
        if (isEmpty()) return "unknown"
        val sanitized = replace(Regex("[^A-Za-z0-9_-]"), "_")
        return sanitized.ifEmpty { "unknown" }
    }

    data class SkuExportResult(
        val uri: Uri,
        val fileName: String,
        val fieldCount: Int,
        val ocrCount: Int
    )

    internal data class SkuWorkbookMetadata(
        val fieldCount: Int,
        val ocrCount: Int,
        val spacerRowIndex: Int,
        val ocrHeaderRowIndex: Int
    )

    internal object SkuWorkbookWriter {
        internal val formHeaderTitles = listOf("Field", "Value")

        private data class FormField(
            val label: String,
            val valueProvider: (SkuRecord) -> String
        )

        private val formFields = listOf(
            FormField("Barcode") { it.barcode },
            FormField("Batch Number") { it.batchNo.orEmpty() },
            FormField("Brand/Trademark") { it.brandTrademark.orEmpty() },
            FormField("Model") { it.model.orEmpty() },
            FormField("Type") { it.type.orEmpty() },
            FormField("Rating") { it.rating.orEmpty() },
            FormField("Size") { it.size.orEmpty() },
            FormField("Linked Serial") { it.linkedSerial.orEmpty() },
            FormField("Created") { it.createdAt.toReadableDate() }
        )

        internal val formFieldLabels: List<String> = formFields.map(FormField::label)

        internal val ocrHeaderTitles = listOf(
            "Captured Text",
            "Label",
            "Field Source",
            "Field Note",
            "Captured"
        )

        fun populate(
            workbook: XSSFWorkbook,
            skuRecord: SkuRecord,
            ocrRecords: List<QrRecord>
        ): SkuWorkbookMetadata {
            val sheet = workbook.createSheet("SKU")
            sheet.setColumnWidth(0, 24 * 256)
            sheet.setColumnWidth(1, 40 * 256)
            sheet.setColumnWidth(2, 60 * 256)
            sheet.setColumnWidth(3, 30 * 256)
            sheet.setColumnWidth(4, 25 * 256)

            val headerStyle = workbook.createHeaderStyle()
            val bodyStyle = workbook.createBodyStyle()

            var rowIndex = 0

            val formHeaderRow = sheet.createRow(rowIndex++)
            formHeaderTitles.forEachIndexed { index, title ->
                val cell = formHeaderRow.createCell(index)
                cell.setCellValue(title)
            }
            formHeaderRow.applyStyle(headerStyle)

            formFields.forEach { field ->
                val row = sheet.createRow(rowIndex++)
                row.createCell(0).setCellValue(field.label)
                row.createCell(1).setCellValue(field.valueProvider(skuRecord))
                row.applyStyle(bodyStyle)
            }

            val spacerRowIndex = rowIndex
            sheet.createRow(rowIndex++)

            val ocrHeaderRowIndex = rowIndex
            val ocrHeaderRow = sheet.createRow(rowIndex++)
            ocrHeaderTitles.forEachIndexed { index, title ->
                val cell = ocrHeaderRow.createCell(index)
                cell.setCellValue(title)
            }
            ocrHeaderRow.applyStyle(headerStyle)

            ocrRecords.forEach { record ->
                val row = sheet.createRow(rowIndex++)
                row.createCell(0).setCellValue(record.payload)
                row.createCell(1).setCellValue(record.label.orEmpty())
                row.createCell(2).setCellValue(record.fieldSource.orEmpty())
                row.createCell(3).setCellValue(record.fieldNote.orEmpty())
                row.createCell(4).setCellValue(record.capturedAt.toReadableDate())
                row.applyStyle(bodyStyle)
            }

            return SkuWorkbookMetadata(
                fieldCount = formFields.size,
                ocrCount = ocrRecords.size,
                spacerRowIndex = spacerRowIndex,
                ocrHeaderRowIndex = ocrHeaderRowIndex
            )
        }
    }
}
