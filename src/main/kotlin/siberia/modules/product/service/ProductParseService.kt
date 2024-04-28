package siberia.modules.product.service

import io.ktor.server.plugins.*
import org.kodein.di.DI
import siberia.modules.product.data.dto.ProductCreateDto
import siberia.modules.product.data.dto.csv.ProductCreateCsvMapper
import siberia.utils.kodein.KodeinService
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.CellType

class ProductParseService (di: DI) : KodeinService(di) {
    fun parseCSVtoProductDto(bytes : ByteArray) : List<ProductCreateDto> {
        val productCreateCsvMapper = ProductCreateCsvMapper()
        return productCreateCsvMapper.readList(bytes)
    }
    fun parseXLSXtoProductDto(workbook: XSSFWorkbook): List<ProductCreateDto> {
        val numberOfSheets = workbook.numberOfSheets
        val products = mutableListOf<ProductCreateDto>()
        val requiredHeaders = setOf(
            "vendorCode", "eanCode", "name", "description", "commonPrice",
            "distributorPercent", "professionalPercent", "category", "color", "amountInBox",
            "expirationDate", "link"
        )

        for (sheetIndex in 0 until numberOfSheets) {
            val sheet = workbook.getSheetAt(sheetIndex)
            val headerRow = sheet.getRow(0) ?: continue

            val columnIndexMap = headerRow.cellIterator().asSequence().mapIndexed { index, cell ->
                cell.stringCellValue.trim() to index
            }.toMap()

            // check if all required headers are exist
            val missedHeaders = mutableListOf<String>()
            requiredHeaders.forEach { header ->
                if (!columnIndexMap.containsKey(header)) {
                    missedHeaders.add(header)
                }
            }
            if (missedHeaders.isNotEmpty()) {
                continue
            }
            else {                                 // if all ok -> parse  rows
                for (row in sheet.rowIterator()) {
                    if (row.rowNum == 0) continue
                    val product = parseRow(row, columnIndexMap)
                    products.add(product)
                }
            }
        }
        if (products.isEmpty()) {
            throw BadRequestException("No one page contains a complete list of required headers.")
        }
        return products
    }

    private fun parseRow(row: Row, columnIndexMap: Map<String, Int>): ProductCreateDto {
        try {
            return ProductCreateDto(
                photoList = emptyList(),
                vendorCode = getStringValue(row, columnIndexMap["vendorCode"]),
                eanCode = getStringValue(row, columnIndexMap["eanCode"]),
                barcode = getStringValue(row, columnIndexMap["barcode"]),
                brand = getIntValue(row, columnIndexMap["brand"]),
                name = getStringValue(row, columnIndexMap["name"]),
                description = getStringValue(row, columnIndexMap["description"]),
                commonPrice = getDoubleValue(row, columnIndexMap["commonPrice"]),
                category = getIntValue(row, columnIndexMap["category"]),
                collection = getIntValue(row, columnIndexMap["collection"]),
                color = getStringValue(row, columnIndexMap["color"]),
                amountInBox = getIntValue(row, columnIndexMap["amountInBox"]),
                expirationDate = getLongValue(row, columnIndexMap["expirationDate"]),
                link = getStringValue(row, columnIndexMap["link"]),
                offertaPrice = getDoubleValue(row, columnIndexMap["offertaPrice"]),
                distributorPercent = getDoubleValue(row, columnIndexMap["distributorPercent"]),
                professionalPercent = getDoubleValue(row, columnIndexMap["professionalPercent"])
            )
        } catch (e: Exception) {
            val rowNumber = row.rowNum + 1
            throw BadRequestException("Invalid type in row $rowNumber: ${e.message}", e)
        }
    }

    private fun getStringValue(row: Row, columnIndex: Int?): String {
        return columnIndex?.let { idx ->
            val cell = row.getCell(idx)
            if (cell != null) {
                when (cell.cellType) {
                    CellType.STRING -> cell.stringCellValue
                    CellType.NUMERIC -> cell.numericCellValue.toString()
                    CellType.BOOLEAN -> cell.booleanCellValue.toString()
                    CellType.BLANK -> ""
                    else -> cell.toString()  // На случай других типов данных, таких как формулы
                }
            } else ""
        } ?: ""
    }

    private fun getDoubleValue(row: Row, columnIndex: Int?): Double? {
        val cell = columnIndex?.let { row.getCell(it) }
        return if (cell != null) {
            when (cell.cellType) {
                CellType.NUMERIC -> cell.numericCellValue
                CellType.STRING -> cell.stringCellValue.toDoubleOrNull()
                else -> null
            }
        } else null
    }

    private fun getIntValue(row: Row, columnIndex: Int?): Int? {
        return getDoubleValue(row, columnIndex)?.toInt()
    }

    private fun getLongValue(row: Row, columnIndex: Int?): Long? {
        return getDoubleValue(row, columnIndex)?.toLong()
    }

}