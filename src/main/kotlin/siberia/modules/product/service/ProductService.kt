package siberia.modules.product.service

import kotlinx.coroutines.Deferred
import kotlinx.serialization.json.Json
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Table.Dual.join
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.conf.AppConf
import siberia.modules.auth.data.dto.AuthorizedUser
import siberia.modules.brand.data.dao.BrandDao
import siberia.modules.brand.data.dao.BrandDao.Companion.createListCond
import siberia.modules.brand.data.dao.BrandDao.Companion.createRangeCond
import siberia.modules.brand.data.models.BrandModel
import siberia.modules.category.data.dao.CategoryDao
import siberia.modules.category.data.models.CategoryModel
import siberia.modules.collection.data.dao.CollectionDao
import siberia.modules.collection.data.models.CollectionModel
import siberia.modules.gallery.data.models.GalleryModel
import siberia.modules.gallery.service.GalleryService
import siberia.modules.logger.data.models.SystemEventModel
import siberia.modules.product.data.dao.ProductDao
import siberia.modules.product.data.dto.*
import siberia.modules.product.data.dto.systemevents.ProductCreateEvent
import siberia.modules.product.data.dto.systemevents.ProductMassiveCreateEvent
import siberia.modules.product.data.models.ProductModel
import siberia.modules.product.data.models.ProductToImageModel
import siberia.modules.rbac.data.dao.RoleDao.Companion.createNullableRangeCond
import siberia.modules.rbac.data.dao.RuleCategoryDao.Companion.createNullableListCond
import siberia.modules.stock.data.dao.StockDao.Companion.createLikeCond
import siberia.modules.stock.data.dto.StockOutputDto
import siberia.modules.stock.data.models.StockModel
import siberia.modules.stock.data.models.StockToProductModel
import siberia.modules.transaction.data.dto.TransactionFullOutputDto
import siberia.modules.user.data.dao.UserDao
import siberia.plugins.Logger
import siberia.utils.database.*
import siberia.utils.files.FilesUtil
import siberia.utils.kodein.KodeinService
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.round
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor


class ProductService(di: DI) : KodeinService(di) {
    private val galleryService: GalleryService by instance()
    private val productParseService: ProductParseService by instance()

    private fun getPrice(base: Double, percent: Double): Double {
        val price = base * (percent / 100)

        return round(price * 100) / 100
    }

    private fun createDao(productCreateDto: ProductCreateDto): ProductDao = transaction {
        val product = ProductDao.new {
            vendorCode = productCreateDto.vendorCode!!
            eanCode = productCreateDto.eanCode!!
            barcode = productCreateDto.barcode
            brand = if (productCreateDto.brand != null) BrandDao[productCreateDto.brand!!] else null
            name = productCreateDto.name!!
            description = productCreateDto.description!!
            distributorPrice = getPrice(productCreateDto.commonPrice!!, productCreateDto.distributorPercent!!)
            distributorPercent = productCreateDto.distributorPercent!!
            professionalPrice = getPrice(productCreateDto.commonPrice!!, productCreateDto.professionalPercent!!)
            professionalPercent = productCreateDto.professionalPercent!!
            commonPrice = productCreateDto.commonPrice!!
            category = if (productCreateDto.category != null) CategoryDao[productCreateDto.category!!] else null
            collection = if (productCreateDto.collection != null) CollectionDao[productCreateDto.collection!!] else null
            color = productCreateDto.color ?: ""
            amountInBox = productCreateDto.amountInBox!!
            expirationDate = productCreateDto.expirationDate ?: 0
            link = productCreateDto.link ?: ""
            offertaPrice = productCreateDto.offertaPrice ?: 0.0
//            Future iterations
//            size = productCreateDto.size
//            volume = productCreateDto.volume
        }
        product.setPhotos(productCreateDto.photoList ?: listOf())

        product
    }
    fun create(authorizedUser: AuthorizedUser, productCreateDto: ProductCreateDto): ProductFullOutputDto = transaction {
        val userDao = UserDao[authorizedUser.id]

        productCreateDto.photoList = galleryService.filterExists( productCreateDto.photoList ?: listOf())

        val productDao = createDao(productCreateDto)

        val event = ProductCreateEvent(userDao.login, productCreateDto.name!!, productDao.vendorCode, productDao.idValue)

        SystemEventModel.logEvent(event)

        commit()

        productDao.fullOutput()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun parseCsv(bytes : ByteArray): ProductParseResultDto {
        return parseOutput(productParseService.parseCSVtoProductDto(bytes))
    }

    fun parseXlsx(workbook: XSSFWorkbook): ProductParseResultDto {
        return parseOutput(productParseService.parseXLSXtoProductDto(workbook))
    }

    fun parseOutput(createList: List<ProductCreateDto>): ProductParseResultDto = transaction {
        val brands = mutableListOf<Int>()
        val collections = mutableListOf<Int>()
        val categories = mutableListOf<Int>()
        createList.forEach {
            if (it.brand != null)
                brands.add(it.brand!!)
            if (it.collection != null)
                brands.add(it.collection!!)
            if (it.category != null)
                brands.add(it.category!!)
        }
        val brandMap = mutableMapOf<Int, String>()
        val collectionMap = mutableMapOf<Int, String>()
        val categoryMap = mutableMapOf<Int, String>()
        BrandModel.slice(BrandModel.id, BrandModel.name).select {
            BrandModel.id inList brands
        }.forEach {
            brandMap[it[BrandModel.id].value] = it[BrandModel.name]
        }
        CollectionModel.slice(CollectionModel.id, CollectionModel.name).select {
            CollectionModel.id inList collections
        }.forEach {
            collectionMap[it[CollectionModel.id].value] = it[CollectionModel.name]
        }
        CategoryModel.slice(CategoryModel.id, CategoryModel.name).select {
            CategoryModel.id inList categories
        }.forEach {
            categoryMap[it[CategoryModel.id].value] = it[CategoryModel.name]
        }

        ProductParseResultDto(
            brandMap = brandMap,
            collectionMap = collectionMap,
            categoryMap = categoryMap,
            createList = createList
        )
    }

    fun bulkInsert(authorizedUser: AuthorizedUser, list : List<ProductCreateDto>) : List<ProductListItemOutputDto> = transaction {
        val userDao = UserDao[authorizedUser.id]
        val insertedProducts = ProductModel.batchInsert(list) {
            val professionalPrice = if (it.commonPrice != null && it.professionalPercent != null)
                getPrice(it.commonPrice!!, it.professionalPercent!!)
            else
                0.0

            val distributorPrice = if (it.commonPrice != null && it.distributorPercent != null)
                getPrice(it.commonPrice!!, it.distributorPercent!!)
            else
                0.0

            this[ProductModel.vendorCode] = it.vendorCode ?: ""
            this[ProductModel.eanCode] = it.eanCode ?: ""
            this[ProductModel.barcode] = it.barcode ?: ""
            this[ProductModel.brand] = it.brand
            this[ProductModel.name] = it.name ?: ""
            this[ProductModel.description] = it.description ?: ""
            this[ProductModel.distributorPrice] = distributorPrice
            this[ProductModel.distributorPercent] = it.distributorPercent ?: 0.0
            this[ProductModel.professionalPrice] = professionalPrice
            this[ProductModel.professionalPercent] = it.professionalPercent ?: 0.0
            this[ProductModel.commonPrice] = it.commonPrice ?: 0.0
            this[ProductModel.category] = it.category
            this[ProductModel.collection] = it.collection
            this[ProductModel.color] = it.color ?: ""
            this[ProductModel.amountInBox] = it.amountInBox ?: 0
            this[ProductModel.expirationDate] = it.expirationDate ?: 0
            this[ProductModel.link] = it.link ?: ""
            this[ProductModel.offertaPrice] = it.offertaPrice ?: 0.0
        }.map {
            ProductListItemOutputDto(
                id = it[ProductModel.id].value,
                name = it[ProductModel.name],
                vendorCode = it[ProductModel.vendorCode],
                quantity = 0.0,
                price = it[ProductModel.commonPrice],
                eanCode = it[ProductModel.eanCode]
            )
        }

        val rollbackInstance = json.encodeToString(
            BaseIntEntity.EventInstance.serializer(
                ProductMassiveInsertRollbackDto.serializer(),
                EMPTY.serializer()
            ),
            BaseIntEntity.EventInstance(
                ProductMassiveInsertRollbackDto(insertedProducts), EMPTY()
            )
        )

        val event = ProductMassiveCreateEvent(userDao.login, rollbackInstance)
        SystemEventModel.logResettableEvent(event)

        insertedProducts
    }

    fun update(authorizedUser: AuthorizedUser, productId: Int, productUpdateDto: ProductUpdateDto): ProductFullOutputDto = transaction {
        val userDao = UserDao[authorizedUser.id]
        val productDao = ProductDao[productId]

        productDao.loadAndFlush(userDao.login, productUpdateDto)

        commit()

        productDao.fullOutput()
    }

    fun remove(authorizedUser: AuthorizedUser, productId: Int): ProductRemoveResultDto = transaction {
        val userDao = UserDao[authorizedUser.id]
        val productDao = ProductDao[productId]

        productDao.delete(userDao.login)
        commit()

        ProductRemoveResultDto(
            success = true,
            message = "Product $productId (${productDao.vendorCode}) successfully removed"
        )
    }

    private fun SqlExpressionBuilder.convertToOperator(searchFilterDto: ProductSearchFilterDto): Op<Boolean> {
        return createRangeCond(searchFilterDto.amountInBox, (ProductModel.id neq 0), ProductModel.amountInBox, -1, Int.MAX_VALUE) and
            createRangeCond(searchFilterDto.commonPrice, (ProductModel.id neq 0), ProductModel.commonPrice, -1.0, Double.MAX_VALUE) and
            createNullableRangeCond(searchFilterDto.purchasePrice, (ProductModel.id neq 0), ProductModel.lastPurchasePrice, -1.0, Double.MAX_VALUE) and
            createRangeCond(searchFilterDto.distributorPrice, (ProductModel.id neq 0), ProductModel.distributorPrice, -1.0, Double.MAX_VALUE) and
            createRangeCond(searchFilterDto.professionalPrice, (ProductModel.id neq 0), ProductModel.professionalPrice, -1.0, Double.MAX_VALUE) and
            createNullableListCond(searchFilterDto.brand, (ProductModel.id neq 0), ProductModel.brand) and
            createNullableListCond(searchFilterDto.category, (ProductModel.id neq 0), ProductModel.category) and
            createNullableListCond(searchFilterDto.collection, (ProductModel.id neq 0), ProductModel.collection) and
            createLikeCond(searchFilterDto.name, (ProductModel.id neq 0), ProductModel.name) and
            createLikeCond(searchFilterDto.color, (ProductModel.id neq 0), ProductModel.color) and
            createLikeCond(searchFilterDto.vendorCode, (ProductModel.id neq 0), ProductModel.vendorCode) and
            createLikeCond(searchFilterDto.description, (ProductModel.id neq 0), ProductModel.description) and
            createRangeCond(searchFilterDto.offertaPrice, (ProductModel.id neq 0), ProductModel.offertaPrice, -1.0, Double.MAX_VALUE) and
            createListCond(searchFilterDto.ids, (ProductModel.id neq 0), ProductModel.id)

//            Future iterations
//            createRangeCond(searchFilterDto.size, (ProductModel.id neq 0), ProductModel.size, -1.0, Double.MAX_VALUE) and
//            createRangeCond(searchFilterDto.volume, (ProductModel.id neq 0), ProductModel.volume, -1.0, Double.MAX_VALUE) and
    }

    suspend fun getByFilter(productSearchDto: ProductSearchDto): Deferred<List<ProductListItemOutputDto>> = suspendedTransactionAsync {
//        getAvailableByFilter(searchFilterDto = productSearchDto)
        getList(searchFilterDto = productSearchDto).await()
    }

    private fun getOrdering(authorizedUser: AuthorizedUser? = null, searchFilterDto: ProductSearchDto) =
        if (authorizedUser != null && searchFilterDto.filters?.availability != null && searchFilterDto.filters.availability){
            listOf(StockToProductModel.amount to SortOrder.DESC_NULLS_LAST, ProductModel.id to SortOrder.ASC)
        } else{
            listOf(ProductModel.name to SortOrder.ASC)
        }

    suspend fun getUnminifiedList(searchFilterDto: ProductSearchDto): Deferred<List<ProductExportPreviewDto>> = suspendedTransactionAsync {
        val query = ProductModel
            .join(
                CategoryModel,
                JoinType.LEFT,
                additionalConstraint = { ProductModel.category eq CategoryModel.id }
            )
            .join(
                CollectionModel,
                JoinType.LEFT,
                additionalConstraint = { ProductModel.collection eq CollectionModel.id }
            )
            .join(
                BrandModel,
                JoinType.LEFT,
                additionalConstraint = { ProductModel.brand eq BrandModel.id })
            .select { convertToOperator(searchFilterDto.filters ?: ProductSearchFilterDto()) }
            .orderBy(ProductModel.id to SortOrder.ASC)

        parallelQueryProcessing(query, 400) {
            ProductExportPreviewDto(
                id = this[ProductModel.id].value,
                photo = listOf(),
                photoIds = listOf(),
                vendorCode = this[ProductModel.vendorCode],
                barcode = this[ProductModel.barcode],
                brand = if (this[ProductModel.brand] != null) this[BrandModel.name] else null,
                name = this[ProductModel.name],
                description = this[ProductModel.description],
                lastPurchasePrice = this[ProductModel.lastPurchasePrice],
                distributorPrice = this[ProductModel.distributorPrice],
                professionalPrice = this[ProductModel.professionalPrice],
                cost = this[ProductModel.cost],
                lastPurchaseDate = this[ProductModel.lastPurchaseDate],
                commonPrice = this[ProductModel.commonPrice],
                category = if (this[ProductModel.category] != null) this[CategoryModel.name] else null,
                collection = if (this[ProductModel.collection] != null) this[CollectionModel.name] else null,
                color = this[ProductModel.color],
                amountInBox = this[ProductModel.amountInBox],
                expirationDate = this[ProductModel.expirationDate],
                link = this[ProductModel.link],
                distributorPercent = this[ProductModel.distributorPercent],
                professionalPercent = this[ProductModel.professionalPercent],
                eanCode = this[ProductModel.eanCode],
                offertaPrice = this[ProductModel.offertaPrice],
            )
        }
    }

    suspend fun getList(authorizedUser: AuthorizedUser? = null, searchFilterDto: ProductSearchDto): Deferred<List<ProductListItemOutputDto>> = suspendedTransactionAsync {
        val ordering = getOrdering(authorizedUser, searchFilterDto)

        val search = searchFilterDto.filters ?: ProductSearchFilterDto()

        val photosMapped: MutableMap<Int, MutableList<String>> = if (searchFilterDto.needImages) {
            val map: MutableMap<Int, MutableList<String>> = mutableMapOf()

            val ids = ProductModel.slice(ProductModel.id).select {
                convertToOperator(search)
            }.map { it[ProductModel.id] }


            ProductToImageModel.join(
                GalleryModel,
                JoinType.LEFT,
                additionalConstraint = {
                    ProductToImageModel.photo eq GalleryModel.id
                }
            ).slice(ProductToImageModel.photo, GalleryModel.url).select {
                ProductToImageModel.product inList ids
            }.map {
                map[it[ProductToImageModel.photo].value]?.add(it[GalleryModel.url])
                    ?: with(map) {
                        this[it[ProductToImageModel.photo].value] = mutableListOf(it[GalleryModel.url])
                    }
            }

            map
        } else
            mutableMapOf()


        val slice = mutableListOf(
            ProductModel.id,
            ProductModel.name,
            ProductModel.vendorCode,
            ProductModel.commonPrice,
            ProductModel.eanCode
        )

        if (authorizedUser != null)
            slice.add(StockToProductModel.amount)

        val query = with(
                ProductModel.slice(slice)
            ) {
                if (authorizedUser != null)
                    join(
                        StockToProductModel,
                        JoinType.LEFT,
                        additionalConstraint = {
                            (StockToProductModel.product eq ProductModel.id) and
                                    (StockToProductModel.stock eq authorizedUser.stockId)
                        }
                    )
                else
                    this
            }.select {
                convertToOperator(search)
            }.orderBy(*ordering.toTypedArray())

        parallelQueryProcessing(query, 400) {
            val amount = try { this[StockToProductModel.amount].toDouble() } catch (_: Exception) { 0.0 }

            val id = this[ProductModel.id].value
            ProductListItemOutputDto(
                id = id,
                name = this[ProductModel.name],
                vendorCode = this[ProductModel.vendorCode],
                quantity = amount,
                price = this[ProductModel.commonPrice],
                photo = photosMapped[id] ?: mutableListOf(),
                eanCode = this[ProductModel.eanCode]
            )
        }
    }

    fun getOne(productId: Int): ProductFullOutputDto = transaction {
        ProductDao[productId].fullOutput()
    }

    fun updateLastPurchaseData(products: List<TransactionFullOutputDto.TransactionProductDto>, timestamp: LocalDateTime) {
        val timestampLong = timestamp.toEpochSecond(ZoneOffset.ofHours(AppConf.zoneOffset))
        products.forEach { product ->
            ProductModel.update({ ProductModel.id eq product.product.id }) {
                it[lastPurchasePrice] = (product.price ?: 0.0)
                it[lastPurchaseDate] = timestampLong
            }
        }
    }

    fun getAvailability(productId: Int): List<StockOutputDto> = transaction {
        //Check it exists
        ProductDao[productId]
         val stocks = StockToProductModel
            .slice(StockToProductModel.stock)
            .select { StockToProductModel.product eq productId }
            .map { it[StockToProductModel.stock] }

        StockModel
            .select { StockModel.id inList stocks }
            .map {
                StockOutputDto(
                    id = it[StockModel.id].value,
                    name = it[StockModel.name],
                    address = it[StockModel.address]
                )
            }
    }

    private fun getSliceBasedOnDto(demandDto: ProductFieldsDemandDto): MutableList<Pair<Column<*>, String>> {
        val slice = mutableListOf<Pair<Column<*>, String>>()

        demandDto::class.memberProperties.forEach { demandProp ->
            val demandPropValue = demandProp.call(demandDto)
            if (demandPropValue != null) {
                val productModelProp = ProductModel::class.memberProperties.firstOrNull {
                    it.name == demandProp.name
                } ?: return@forEach

                val productColumn = productModelProp.call(ProductModel) as Column<*>
                slice.add(productColumn to demandPropValue.toString())
            }
        }

        slice.add(ProductModel.id to "ID")

        return slice
    }

    fun getAvailableByFilter(
        authorizedUser: AuthorizedUser? = null,
        searchFilterDto: ProductSearchDto
    ): List<ProductListItemOutputDto> = transaction {

        val ordering = if (authorizedUser != null && searchFilterDto.filters?.availability != null && searchFilterDto.filters.availability){
            listOf(StockToProductModel.amount to SortOrder.DESC_NULLS_LAST, ProductModel.id to SortOrder.ASC)
        } else{
            listOf(ProductModel.id to SortOrder.ASC)
        }

        val search = searchFilterDto.filters ?: ProductSearchFilterDto()

        val slice = mutableListOf(
            ProductModel.id,
            ProductModel.name,
            ProductModel.vendorCode,
            ProductModel.commonPrice,
            ProductModel.eanCode,
            GalleryModel.url
        )
        if (authorizedUser != null)
            slice.add(StockToProductModel.amount)

        val resultMap: MutableMap<Int, ProductListItemOutputDto> = mutableMapOf()
            with(
                ProductModel
                .join(
                    ProductToImageModel,
                    JoinType.LEFT,
                    additionalConstraint = {
                        ProductModel.id eq ProductToImageModel.product
                    }
                )
                .join(
                    GalleryModel,
                    JoinType.LEFT,
                    additionalConstraint = {
                        ProductToImageModel.photo eq GalleryModel.id
                    }
                )
            ) {
                if (authorizedUser != null)
                    join(
                        StockToProductModel,
                        JoinType.LEFT,
                        additionalConstraint = {
                            (StockToProductModel.product eq ProductModel.id) and
                            (StockToProductModel.stock eq authorizedUser.stockId)
                        }
                    )
                else
                    this
            }
            .slice(
                slice
            )
            .select {
                convertToOperator(search)
            }
            .orderBy(*ordering.toTypedArray())
            .forEach {
                if (resultMap.containsKey(it[ProductModel.id].value)) {
                    resultMap[it[ProductModel.id].value]!!.photo.add(it[GalleryModel.url])
                } else {
                    //If join returns nothing (no such product in stock) amount = 0
                    val amount = try { it[StockToProductModel.amount].toDouble() } catch (_: Exception) { 0.0 }

                    val photoList = with(it[GalleryModel.url]) {
                        if (this == null)
                            mutableListOf()
                        else
                            mutableListOf(it[GalleryModel.url])
                    }

                    resultMap[it[ProductModel.id].value] = ProductListItemOutputDto(
                        id = it[ProductModel.id].value,
                        name = it[ProductModel.name],
                        vendorCode = it[ProductModel.vendorCode],
                        quantity = amount,
                        price = it[ProductModel.commonPrice],
                        photo = photoList,
                        eanCode = it[ProductModel.eanCode]
                    )
                }
            }
        resultMap.values.toList()
    }

    fun getXls(
        searchFilterDto: ProductSearchFilterDto,
        productFieldsDemandDto: ProductFieldsDemandDto
    ): String = transaction {

        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Products")

        val slicePairs = getSliceBasedOnDto(productFieldsDemandDto)
        val slice = slicePairs.map { it.first }
        val headers = slicePairs.map { it.second }

        val headerRow = sheet.createRow(0) // Table headers creation
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue( header )
        }

        val transforms = mutableMapOf<Column<*>, Any?.() -> String>(
            ProductModel.expirationDate to {
                val result = (this?. toString()?.toLong() ?: 0) * 1000 * 60 * 24
                result.toString()
            }
        )

        headerRow.forEach { cell ->
            Logger.debug("Header Cell Value: ${cell.stringCellValue}", "main")
        }

        var rowIndex = 1
        ProductModel
            .join(
                CategoryModel,
                JoinType.LEFT,
                additionalConstraint = { ProductModel.category eq CategoryModel.id }
            )
            .join(
                CollectionModel,
                JoinType.LEFT,
                additionalConstraint = { ProductModel.collection eq CollectionModel.id }
            )
            .join(
                BrandModel,
                JoinType.LEFT,
                additionalConstraint = { ProductModel.brand eq BrandModel.id })


            .slice(slice)
            .select { convertToOperator(searchFilterDto) }
            .orderBy(ProductModel.id to SortOrder.ASC)
            .forEach { row ->
                val dataRow = sheet.createRow(rowIndex++)
                slice.forEachIndexed { index, column ->
                    val cell = dataRow.createCell(index, CellType.STRING)
                    val value = if (transforms.containsKey(column))
                        transforms[column]?.invoke(row[column])
                    else
                        row[column]?.toString() ?: ""
                    cell.setCellValue(value)
                }
            }

        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()

        FilesUtil.encodeBytes(outputStream.toByteArray())
    }

    fun getByBarCode(barCode: String): List<ProductListItemOutputDto> = transaction {
        ProductDao.wrapRows(ProductModel.select {
            ProductModel.barcode eq barCode
        }).map { el -> el.listItemDto }
    }

    fun getAssortmentData(assortmentExportTemplateDto: AssortmentExportTemplateDto) = transaction {
        val brands = BrandModel.slice(BrandModel.id, BrandModel.name).selectAll().map {
            Pair(it[BrandModel.id].value, it[BrandModel.name])
        }
        val collections = CollectionModel.slice(CollectionModel.id, CollectionModel.name).selectAll().map {
            Pair(it[CollectionModel.id].value, it[CollectionModel.name])
        }
        val categories = CategoryModel.slice(CategoryModel.id, CategoryModel.name).selectAll().map {
            Pair(it[CategoryModel.id].value, it[CategoryModel.name])
        }
        val workbook = XSSFWorkbook()
        val brandsSheet = workbook.createSheet(assortmentExportTemplateDto.brandSheetTitle)
        val collectionsSheet = workbook.createSheet(assortmentExportTemplateDto.collectionSheetTitle)
        val categoriesSheet = workbook.createSheet(assortmentExportTemplateDto.categorySheetTitle)

        val sheets = listOf(brandsSheet, collectionsSheet, categoriesSheet)

        createHeadersForSheets(sheets)

        brands.forEachIndexed { index, brand ->
            val row = brandsSheet.createRow(index + 1)
            row.createCell(0, CellType.NUMERIC).setCellValue(brand.first.toDouble())
            row.createCell(1, CellType.STRING).setCellValue(brand.second)
        }
        collections.forEachIndexed {index, collection ->
        val row = collectionsSheet.createRow(index + 1)
            row.createCell(0, CellType.NUMERIC).setCellValue(collection.first.toDouble())
            row.createCell(1, CellType.STRING).setCellValue(collection.second)
        }
        categories.forEachIndexed { index, category ->
            val row = categoriesSheet.createRow(index + 1)
            row.createCell(0, CellType.NUMERIC).setCellValue(category.first.toDouble())
            row.createCell(1, CellType.STRING).setCellValue(category.second)
        }

        val productsSheet = workbook.createSheet(assortmentExportTemplateDto.productSheetTitle)
        productsSheet.createRow(0)

        val namesOfFields = ProductCreateDto::class.primaryConstructor?.parameters?.map { it.name }?.drop(1) ?: listOf()

        val productsHeader = productsSheet.createRow(0)

        namesOfFields.forEachIndexed{ index, fieldName ->
            productsHeader.createCell(index).setCellValue(fieldName)
        }

        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()

        FilesUtil.encodeBytes(outputStream.toByteArray())
    }
    private fun createHeadersForSheets(sheets : List<XSSFSheet>) {
        sheets.forEach {
            val header = it.createRow(0)
            header.createCell(0, CellType.STRING).setCellValue("ID")
            header.createCell(1, CellType.STRING).setCellValue("Name")
        }
    }
}