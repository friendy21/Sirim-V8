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

    fun exportSkuToExcel(skuRecords: List<SkuRecord>, ocrRecords: List<QrRecord>): Uri {
        val file = createSkuExportFile()
        XSSFWorkbook().use { workbook ->
            val sessionSheet = workbook.createSheet("SKU Sessions")
            val columnWidths = intArrayOf(12, 36, 26, 22, 18, 18, 22)
            columnWidths.forEachIndexed { index, width ->
                sessionSheet.setColumnWidth(index, width * 256)
            }

            val formHeaderStyle = workbook.createFormHeaderStyle()
            val formValueStyle = workbook.createFormValueStyle()
            val tableHeaderStyle = workbook.createTableHeaderStyle()
            val tableBodyStyle = workbook.createTableBodyStyle()
            val emptyStateStyle = workbook.createEmptyStateStyle()

            val grouping = groupSessionsBySku(skuRecords, ocrRecords)
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

            var rowIndex = 0

            if (grouping.sessions.isEmpty()) {
                val emptyRow = sessionSheet.createRow(rowIndex++)
                emptyRow.createCell(0).apply {
                    setCellValue("No SKU sessions available")
                    cellStyle = emptyStateStyle
                }
                if (ocrRecords.isNotEmpty()) {
                    val detailRow = sessionSheet.createRow(rowIndex++)
                    detailRow.createCell(0).apply {
                        setCellValue("SIRIM scans saved: ${ocrRecords.size}")
                        cellStyle = emptyStateStyle
                    }
                }
            } else {
                grouping.sessions.forEachIndexed { index, (sku, sirimRecords) ->
                    if (index > 0) {
                        sessionSheet.createRow(rowIndex++)
                    }

                    val formHeaderRow = sessionSheet.createRow(rowIndex++)
                    formHeaders.forEachIndexed { columnIndex, title ->
                        formHeaderRow.createCell(columnIndex).apply {
                            setCellValue(title)
                            cellStyle = formHeaderStyle
                        }
                    }

                    val formValueRow = sessionSheet.createRow(rowIndex++)
                    listOf(
                        sku.barcode,
                        sku.brandTrademark.orEmpty(),
                        sku.model.orEmpty(),
                        sku.type.orEmpty(),
                        sku.rating.orEmpty(),
                        sku.batchNo.orEmpty(),
                        sku.createdAt.toReadableDate()
                    ).forEachIndexed { columnIndex, value ->
                        formValueRow.createCell(columnIndex).apply {
                            setCellValue(value)
                            cellStyle = formValueStyle
                        }
                    }

                    sessionSheet.createRow(rowIndex++)

                    val sirimHeaderRow = sessionSheet.createRow(rowIndex++)
                    sirimHeaders.forEachIndexed { columnIndex, title ->
                        sirimHeaderRow.createCell(columnIndex).apply {
                            setCellValue(title)
                            cellStyle = tableHeaderStyle
                        }
                    }

                    if (sirimRecords.isEmpty()) {
                        val noRecordsRow = sessionSheet.createRow(rowIndex++)
                        noRecordsRow.createCell(0).apply {
                            setCellValue("No SIRIM scans captured for this SKU")
                            cellStyle = emptyStateStyle
                        }
                    } else {
                        sirimRecords.forEachIndexed { recordIndex, record ->
                            val row = sessionSheet.createRow(rowIndex++)
                            row.createCell(0).apply {
                                setCellValue((recordIndex + 1).toDouble())
                                cellStyle = tableBodyStyle
                            }
                            row.createCell(1).apply {
                                setCellValue(record.payload)
                                cellStyle = tableBodyStyle
                            }
                            row.createCell(2).apply {
                                setCellValue(record.label.orEmpty())
                                cellStyle = tableBodyStyle
                            }
                            row.createCell(3).apply {
                                setCellValue(record.fieldSource.orEmpty())
                                cellStyle = tableBodyStyle
                            }
                            row.createCell(4).apply {
                                setCellValue(record.fieldNote.orEmpty())
                                cellStyle = tableBodyStyle
                            }
                            row.createCell(5).apply {
                                setCellValue(record.capturedAt.toReadableDate())
                                cellStyle = tableBodyStyle
                            }
                            row.createCell(6).apply {
                                setCellValue("None")
                                cellStyle = tableBodyStyle
                            }
                        }
                    }
                }
            }

            if (grouping.unassigned.isNotEmpty()) {
                sessionSheet.createRow(rowIndex++)
                sessionSheet.createRow(rowIndex++)

                val titleRow = sessionSheet.createRow(rowIndex++)
                titleRow.createCell(0).apply {
                    setCellValue("Unassigned SIRIM scans (no preceding SKU)")
                    cellStyle = formHeaderStyle
                }

                val headerRow = sessionSheet.createRow(rowIndex++)
                sirimHeaders.forEachIndexed { columnIndex, title ->
                    headerRow.createCell(columnIndex).apply {
                        setCellValue(title)
                        cellStyle = tableHeaderStyle
                    }
                }

                grouping.unassigned.forEachIndexed { recordIndex, record ->
                    val row = sessionSheet.createRow(rowIndex++)
                    row.createCell(0).apply {
                        setCellValue((recordIndex + 1).toDouble())
                        cellStyle = tableBodyStyle
                    }
                    row.createCell(1).apply {
                        setCellValue(record.payload)
                        cellStyle = tableBodyStyle
                    }
                    row.createCell(2).apply {
                        setCellValue(record.label.orEmpty())
                        cellStyle = tableBodyStyle
                    }
                    row.createCell(3).apply {
                        setCellValue(record.fieldSource.orEmpty())
                        cellStyle = tableBodyStyle
                    }
                    row.createCell(4).apply {
                        setCellValue(record.fieldNote.orEmpty())
                        cellStyle = tableBodyStyle
                    }
                    row.createCell(5).apply {
                        setCellValue(record.capturedAt.toReadableDate())
                        cellStyle = tableBodyStyle
                    }
                    row.createCell(6).apply {
                        setCellValue("None")
                        cellStyle = tableBodyStyle
                    }
                }
            }

            FileOutputStream(file).use { output ->
                workbook.write(output)
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun groupSessionsBySku(
        skuRecords: List<SkuRecord>,
        ocrRecords: List<QrRecord>
    ): SessionGrouping {
        if (skuRecords.isEmpty()) {
            return SessionGrouping(emptyList(), ocrRecords.sortedBy { it.capturedAt })
        }

        val sortedSku = skuRecords.sortedBy { it.createdAt }
        val assignedBySku: Map<Long, List<QrRecord>> = ocrRecords
            .filter { it.skuRecordId != null }
            .groupBy { it.skuRecordId!! }
            .mapValues { (_, records) -> records.sortedBy { it.capturedAt } }

        val legacyRecords = ocrRecords
            .filter { it.skuRecordId == null }
            .sortedBy { it.capturedAt }

        val sessions = mutableListOf<Pair<SkuRecord, List<QrRecord>>>()
        val unassigned = mutableListOf<QrRecord>()
        val assignedConsumed = mutableSetOf<Long>()

        var legacyIndex = 0

        val firstSkuCreatedAt = sortedSku.first().createdAt
        while (legacyIndex < legacyRecords.size && legacyRecords[legacyIndex].capturedAt < firstSkuCreatedAt) {
            unassigned += legacyRecords[legacyIndex]
            legacyIndex++
        }

        sortedSku.forEachIndexed { index, sku ->
            val nextCreatedAt = sortedSku.getOrNull(index + 1)?.createdAt
            val bucket = mutableListOf<QrRecord>()

            assignedBySku[sku.id]?.let { assigned ->
                bucket += assigned
                assignedConsumed += sku.id
            }

            while (legacyIndex < legacyRecords.size) {
                val record = legacyRecords[legacyIndex]
                if (record.capturedAt < sku.createdAt) {
                    unassigned += record
                    legacyIndex++
                    continue
                }

                val withinCurrentSession = nextCreatedAt?.let { record.capturedAt < it } ?: true
                if (!withinCurrentSession) {
                    break
                }

                bucket += record
                legacyIndex++
            }

            sessions += sku to bucket.sortedBy { it.capturedAt }
        }

        while (legacyIndex < legacyRecords.size) {
            unassigned += legacyRecords[legacyIndex]
            legacyIndex++
        }

        assignedBySku
            .filterKeys { it !in assignedConsumed }
            .values
            .forEach { records -> unassigned += records }

        return SessionGrouping(
            sessions = sessions.sortedByDescending { it.first.createdAt },
            unassigned = unassigned.sortedBy { it.capturedAt }
        )
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

    private fun XSSFWorkbook.createFormValueStyle(): XSSFCellStyle = createCellStyle().apply {
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

    private fun XSSFWorkbook.createTableBodyStyle(): XSSFCellStyle = createCellStyle().apply {
        alignment = HorizontalAlignment.LEFT
        verticalAlignment = VerticalAlignment.TOP
        wrapText = true
    }

    private fun XSSFWorkbook.createEmptyStateStyle(): XSSFCellStyle {
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

private data class SessionGrouping(
    val sessions: List<Pair<SkuRecord, List<QrRecord>>>,
    val unassigned: List<QrRecord>
)
