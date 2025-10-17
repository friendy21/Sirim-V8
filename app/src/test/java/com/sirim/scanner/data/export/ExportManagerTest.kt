package com.sirim.scanner.data.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sirim.scanner.data.db.QrRecord
import com.sirim.scanner.data.db.SkuRecord
import java.io.FileInputStream
import java.util.Calendar
import java.util.GregorianCalendar
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportManagerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val exportManager = ExportManager(context)

    @Test
    fun `exporter writes header block followed by OCR table`() {
        val createdAt = GregorianCalendar(2024, Calendar.FEBRUARY, 1, 9, 30).timeInMillis
        val sku = SkuRecord(
            id = 42,
            barcode = "ABC123",
            brandTrademark = "Brand",
            model = "Model X",
            type = "Type C",
            rating = "220V",
            batchNo = "Batch 7",
            createdAt = createdAt
        )
        val ocrRecords = listOf(
            QrRecord(
                id = 1,
                payload = "payload-one",
                label = "Label 1",
                fieldSource = "Field",
                fieldNote = "Note",
                capturedAt = createdAt + 60_000
            ),
            QrRecord(
                id = 2,
                payload = "payload-two",
                label = "Label 2",
                fieldSource = "Field",
                fieldNote = "Note",
                capturedAt = createdAt + 120_000
            )
        )

        val result = exportManager.exportSkuToExcel(sku, ocrRecords)

        FileInputStream(result.file).use { input ->
            XSSFWorkbook(input).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                assertEquals("Barcode", sheet.getRow(0).getCell(0).stringCellValue)
                assertEquals("Brand/Trademark", sheet.getRow(0).getCell(1).stringCellValue)

                val valueRow = sheet.getRow(1)
                assertEquals("ABC123", valueRow.getCell(0).stringCellValue)
                assertEquals("Brand", valueRow.getCell(1).stringCellValue)
                assertEquals("Model X", valueRow.getCell(2).stringCellValue)

                assertNull(sheet.getRow(2))

                val ocrHeaderRow = sheet.getRow(3)
                assertEquals("Number", ocrHeaderRow.getCell(0).stringCellValue)
                assertEquals("Captured Text", ocrHeaderRow.getCell(1).stringCellValue)

                val firstOcrRow = sheet.getRow(4)
                assertEquals(1.0, firstOcrRow.getCell(0).numericCellValue, 0.0)
                assertEquals("payload-one", firstOcrRow.getCell(1).stringCellValue)
                assertEquals("Label 1", firstOcrRow.getCell(2).stringCellValue)

                val secondOcrRow = sheet.getRow(5)
                assertEquals(2.0, secondOcrRow.getCell(0).numericCellValue, 0.0)
                assertEquals("payload-two", secondOcrRow.getCell(1).stringCellValue)
            }
        }

        assertEquals(7, result.fieldCount)
        assertEquals(2, result.ocrCount)

        result.file.delete()
    }
}
