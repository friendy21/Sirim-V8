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
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook

class ExportManager(private val context: Context) {

    fun exportSkuToExcel(sku: SkuRecord, ocrRecords: List<QrRecord>): SkuExportResult {
        val file = createSkuExportFile(sku.barcode)
        val formHeaders = listOf(
            "Barcode",
            "Brand/Trademark",
            "Model",
            "Type",
            "Rating",
            "Batch",
            "Created"
        )
        val sirimHeaders = listOf(
            "Number",
            "Captured Text",
            "Label",
            "Field Source",
            "Field Note",
            "Captured",
            "Duplication"
        )

        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("SKU ${sku.barcode}")
            val columnWidths = intArrayOf(12, 36, 26, 22, 18, 18, 22)
            columnWidths.forEachIndexed { index, width ->
                sheet.setColumnWidth(index, width * 256)
            }

            val formHeaderStyle = workbook.createFormHeaderStyle()
            val formValueStyle = workbook.createFormValueStyle()
            val tableHeaderStyle = workbook.createTableHeaderStyle()
            val tableBodyStyle = workbook.createTableBodyStyle()

            var rowIndex = 0

            val formHeaderRow = sheet.createRow(rowIndex++)
            formHeaders.forEachIndexed { columnIndex, title ->
                formHeaderRow.createCell(columnIndex).apply {
                    setCellValue(title)
                    cellStyle = formHeaderStyle as XSSFCellStyle
                }
            }

            val formValues = listOf(
                sku.barcode,
                sku.brandTrademark.orEmpty(),
                sku.model.orEmpty(),
                sku.type.orEmpty(),
                sku.rating.orEmpty(),
                sku.batchNo.orEmpty(),
                sku.createdAt.toReadableDate()
            )

            val formValueRow = sheet.createRow(rowIndex++)
            formValues.forEachIndexed { columnIndex, value ->
                formValueRow.createCell(columnIndex).apply {
                    setCellValue(value)
                    cellStyle = formValueStyle as XSSFCellStyle
                }
            }

            rowIndex++ // spacer row

            val sirimHeaderRow = sheet.createRow(rowIndex++)
            sirimHeaders.forEachIndexed { columnIndex, title ->
                sirimHeaderRow.createCell(columnIndex).apply {
                    setCellValue(title)
                    cellStyle = tableHeaderStyle as XSSFCellStyle
                }
            }

            ocrRecords.forEachIndexed { recordIndex, record ->
                val row = sheet.createRow(rowIndex++)
                row.createCell(0).apply {
                    setCellValue((recordIndex + 1).toDouble())
                    cellStyle = tableBodyStyle as XSSFCellStyle
                }
                row.createCell(1).apply {
                    setCellValue(record.payload)
                    cellStyle = tableBodyStyle as XSSFCellStyle
                }
                row.createCell(2).apply {
                    setCellValue(record.label.orEmpty())
                    cellStyle = tableBodyStyle as XSSFCellStyle
                }
                row.createCell(3).apply {
                    setCellValue(record.fieldSource.orEmpty())
                    cellStyle = tableBodyStyle as XSSFCellStyle
                }
                row.createCell(4).apply {
                    setCellValue(record.fieldNote.orEmpty())
                    cellStyle = tableBodyStyle as XSSFCellStyle
                }
                row.createCell(5).apply {
                    setCellValue(record.capturedAt.toReadableDate())
                    cellStyle = tableBodyStyle as XSSFCellStyle
                }
                row.createCell(6).apply {
                    setCellValue("None")
                    cellStyle = tableBodyStyle as XSSFCellStyle
                }
            }

            FileOutputStream(file, false).use { output ->
                workbook.write(output)
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return SkuExportResult(
            uri = uri,
            file = file,
            fieldCount = formHeaders.size,
            ocrCount = ocrRecords.size
        )
    }

    private fun createSkuExportFile(barcode: String): File {
        val directory = getSkuExportDirectory()
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val sanitizedBarcode = sanitizeBarcode(barcode)
        val fileName = "sku_${sanitizedBarcode}.xlsx"
        return File(directory, fileName)
    }

    private fun getSkuExportDirectory(): File = File(context.getExternalFilesDir(null), "exports/sku")

    private fun sanitizeBarcode(raw: String): String {
        val normalized = raw.lowercase(Locale.US).trim()
        val cleaned = buildString {
            normalized.forEach { char ->
                when {
                    char.isLetterOrDigit() -> append(char)
                    char == '-' || char == '_' -> append(char)
                    else -> append('_')
                }
            }
        }.trim('_')
        return if (cleaned.isEmpty()) {
            "unknown"
        } else {
            cleaned
        }
    }

    private fun Long.toReadableDate(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return formatter.format(Date(this))
    }

    private fun XSSFWorkbook.createFormHeaderStyle(): XSSFCellStyle {
        val font: XSSFFont = createFont().apply {
            bold = true
            color = IndexedColors.WHITE.index
        }
        return createCellStyle().apply {
            setFont(font)
            fillForegroundColor = IndexedColors.GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
        }
    }

    private fun XSSFWorkbook.createFormValueStyle(): CellStyle = createCellStyle().apply {
        alignment = HorizontalAlignment.LEFT
        verticalAlignment = VerticalAlignment.CENTER
    }

    private fun XSSFWorkbook.createTableHeaderStyle(): XSSFCellStyle {
        val font: XSSFFont = createFont().apply {
            bold = true
            color = IndexedColors.WHITE.index
        }
        return createCellStyle().apply {
            setFont(font)
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }
    }

    private fun XSSFWorkbook.createTableBodyStyle(): CellStyle = createCellStyle().apply {
        alignment = HorizontalAlignment.LEFT
        verticalAlignment = VerticalAlignment.TOP
        wrapText = true
    }

    private fun XSSFWorkbook.createEmptyStateStyle(): CellStyle {
        val font = createFont().apply {
            italic = true
            color = IndexedColors.GREY_80_PERCENT.index
        }
        return createCellStyle().apply {
            setFont(font)
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.CENTER
        }
    }
}

data class SkuExportResult(
    val uri: Uri,
    val file: File,
    val fieldCount: Int,
    val ocrCount: Int
)
