package com.sirim.scanner.data.export

import com.sirim.scanner.data.db.QrRecord
import com.sirim.scanner.data.db.SkuRecord
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportManagerTest {

    @Test
    fun `populate writes form header spacer and ocr rows in order`() {
        val skuRecord = SkuRecord(
            id = 1,
            barcode = "ABC12345",
            batchNo = "BATCH-1",
            brandTrademark = "Brand",
            model = "ModelX",
            type = "TypeY",
            rating = "220V",
            size = "Medium",
            linkedSerial = "SN123",
            createdAt = 1_700_000_000_000
        )

        val ocrRecords = listOf(
            QrRecord(
                id = 10,
                payload = "First payload",
                label = "Label A",
                fieldSource = "Source A",
                fieldNote = "Note A",
                capturedAt = 1_700_000_100_000
            ),
            QrRecord(
                id = 11,
                payload = "Second payload",
                label = "Label B",
                fieldSource = "Source B",
                fieldNote = "Note B",
                capturedAt = 1_700_000_200_000
            )
        )

        val workbook = XSSFWorkbook()
        val metadata = ExportManager.SkuWorkbookWriter.populate(workbook, skuRecord, ocrRecords)
        assertEquals(1, workbook.numberOfSheets)
        val sheet = workbook.getSheetAt(0)

        val formHeader = readRowValues(
            sheet = sheet,
            rowIndex = 0,
            columnCount = ExportManager.SkuWorkbookWriter.formHeaderTitles.size
        )
        assertEquals(ExportManager.SkuWorkbookWriter.formHeaderTitles, formHeader)

        val labels = ExportManager.SkuWorkbookWriter.formFieldLabels
        val writtenLabels = labels.indices.map { index ->
            sheet.getRow(index + 1).getCell(0).stringCellValue
        }
        assertEquals(labels, writtenLabels)

        assertEquals(labels.size + 1, metadata.spacerRowIndex)
        val spacerRow = sheet.getRow(metadata.spacerRowIndex)
        assertNotNull(spacerRow)
        assertEquals(0, spacerRow.physicalNumberOfCells)

        assertEquals(metadata.spacerRowIndex + 1, metadata.ocrHeaderRowIndex)
        val ocrHeader = readRowValues(
            sheet = sheet,
            rowIndex = metadata.ocrHeaderRowIndex,
            columnCount = ExportManager.SkuWorkbookWriter.ocrHeaderTitles.size
        )
        assertEquals(ExportManager.SkuWorkbookWriter.ocrHeaderTitles, ocrHeader)

        val firstOcrRowIndex = metadata.ocrHeaderRowIndex + 1
        val firstOcrRow = sheet.getRow(firstOcrRowIndex)
        assertEquals("First payload", firstOcrRow.getCell(0).stringCellValue)
        val secondOcrRow = sheet.getRow(firstOcrRowIndex + 1)
        assertEquals("Second payload", secondOcrRow.getCell(0).stringCellValue)

        assertEquals(labels.size, metadata.fieldCount)
        assertEquals(ocrRecords.size, metadata.ocrCount)

        workbook.close()
    }
}

private fun readRowValues(sheet: Sheet, rowIndex: Int, columnCount: Int): List<String> {
    val row = requireNotNull(sheet.getRow(rowIndex)) { "Expected row at index $rowIndex" }
    return (0 until columnCount).map { column ->
        row.getCell(column)?.stringCellValue ?: ""
    }
}
