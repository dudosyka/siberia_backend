package siberia.modules.product.service

import io.ktor.server.plugins.*
import org.kodein.di.DI
import siberia.modules.product.data.dto.ProductCreateDto
import siberia.modules.product.data.dto.csv.ProductCreateCsvMapper
import siberia.utils.kodein.KodeinService
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.Row

class ProductParseService (di: DI) : KodeinService(di) {
    fun parseCSVtoProductDto(bytes : ByteArray) : List<ProductCreateDto> {
        val productCreateCsvMapper = ProductCreateCsvMapper()
        return productCreateCsvMapper.readList(bytes)
    }
    fun parseXLSXtoProductDto(workbook: XSSFWorkbook): List<ProductCreateDto> {
        val numberOfSheets = workbook.numberOfSheets
        val products = mutableListOf<ProductCreateDto>()
        val requiredHeaders = setOf(
            "vendorCode", "eanCode", "barcode", "name", "description", "commonPrice",
            "distributorPercent", "professionalPercent", "category", "color", "amountInBox",
            "expirationDate", "link", "offertaPrice"
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
                vendorCode = columnIndexMap["vendorCode"]?.let { row.getCell(it)?.stringCellValue },
                eanCode = columnIndexMap["eanCode"]?.let { row.getCell(it)?.stringCellValue },
                barcode = columnIndexMap["barcode"]?.let { row.getCell(it)?.stringCellValue },
                brand = columnIndexMap["brand"]?.let { row.getCell(it)?.numericCellValue?.toInt() },
                name = columnIndexMap["name"]?.let { row.getCell(it)?.stringCellValue },
                description = columnIndexMap["description"]?.let { row.getCell(it)?.stringCellValue },
                commonPrice = columnIndexMap["commonPrice"]?.let { row.getCell(it)?.numericCellValue?.toDouble() },
                category = columnIndexMap["category"]?.let { row.getCell(it)?.numericCellValue?.toInt() },
                collection = columnIndexMap["collection"]?.let { row.getCell(it)?.numericCellValue?.toInt() },
                color = columnIndexMap["color"]?.let { row.getCell(it)?.stringCellValue },
                amountInBox = columnIndexMap["amountInBox"]?.let { row.getCell(it)?.numericCellValue?.toInt() },
                expirationDate = columnIndexMap["expirationDate"]?.let { row.getCell(it)?.numericCellValue?.toLong() },
                link = columnIndexMap["link"]?.let { row.getCell(it)?.stringCellValue },
                offertaPrice = columnIndexMap["offertaPrice"]?.let { row.getCell(it)?.numericCellValue?.toDouble() },
                distributorPercent = columnIndexMap["distributorPercent"]?.let { row.getCell(it)?.numericCellValue?.toDouble() },
                professionalPercent = columnIndexMap["professionalPercent"]?.let { row.getCell(it)?.numericCellValue?.toDouble() }
            )
        } catch (e: Exception) {
            val rowNumber = row.rowNum + 1
            throw BadRequestException("Invalid type in row $rowNumber: ${e.message}", e)
        }
    }
}