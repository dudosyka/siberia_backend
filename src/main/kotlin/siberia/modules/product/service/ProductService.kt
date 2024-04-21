package siberia.modules.product.service

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Table.Dual.join
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.conf.AppConf
import siberia.modules.auth.data.dto.AuthorizedUser
import siberia.modules.brand.data.dao.BrandDao
import siberia.modules.brand.data.dao.BrandDao.Companion.createRangeCond
import siberia.modules.category.data.dao.CategoryDao
import siberia.modules.collection.data.dao.CollectionDao
import siberia.modules.gallery.data.models.GalleryModel
import siberia.modules.gallery.service.GalleryService
import siberia.modules.logger.data.models.SystemEventModel
import siberia.modules.product.data.dao.ProductDao
import siberia.modules.product.data.dto.*
import siberia.modules.product.data.dto.systemevents.ProductCreateEvent
import siberia.modules.product.data.dto.systemevents.ProductMassiveCreateEvent
import siberia.modules.product.data.models.ProductModel
import siberia.modules.product.data.models.ProductToImageModel
import siberia.modules.category.data.models.CategoryModel
import siberia.modules.collection.data.models.CollectionModel
import siberia.modules.brand.data.models.BrandModel

import siberia.modules.rbac.data.dao.RoleDao.Companion.createNullableRangeCond
import siberia.modules.rbac.data.dao.RuleCategoryDao.Companion.createNullableListCond
import siberia.modules.stock.data.dao.StockDao
import siberia.modules.stock.data.dao.StockDao.Companion.createLikeCond
import siberia.modules.stock.data.dto.StockOutputDto
import siberia.modules.stock.data.models.StockModel
import siberia.modules.stock.data.models.StockToProductModel
import siberia.modules.transaction.data.dto.TransactionFullOutputDto
import siberia.modules.user.data.dao.UserDao
import siberia.utils.database.BaseIntEntity
import siberia.utils.database.EMPTY
import siberia.utils.database.idValue
import siberia.utils.kodein.KodeinService
import java.time.LocalDateTime
import java.time.ZoneOffset

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.firstValue
import siberia.plugins.Logger
import siberia.utils.files.FilesUtil
import java.io.ByteArrayOutputStream

class ProductService(di: DI) : KodeinService(di) {
    private val galleryService: GalleryService by instance()

    private fun getPrice(base: Double, percent: Double): Double
        = base * (percent / 100)

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
            color = productCreateDto.color!!
            amountInBox = productCreateDto.amountInBox!!
            expirationDate = productCreateDto.expirationDate!!
            link = productCreateDto.link!!
            offertaPrice = productCreateDto.offertaPrice!!
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

    fun bulkInsert(authorizedUser: AuthorizedUser, list : List<ProductCreateDto>) : List<ProductListItemOutputDto> = transaction {
        val userDao = UserDao[authorizedUser.id]
        val insertedProducts = list.map {
            createDao(it).listItemDto
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
            createRangeCond(searchFilterDto.offertaPrice, (ProductModel.id neq 0), ProductModel.offertaPrice, -1.0, Double.MAX_VALUE)

//            Future iterations
//            createRangeCond(searchFilterDto.size, (ProductModel.id neq 0), ProductModel.size, -1.0, Double.MAX_VALUE) and
//            createRangeCond(searchFilterDto.volume, (ProductModel.id neq 0), ProductModel.volume, -1.0, Double.MAX_VALUE) and
    }

    fun getByFilter(productSearchDto: ProductSearchDto): List<ProductListItemOutputDto> = transaction {
        getList(searchFilterDto = productSearchDto)
//        getAvailableByFilter(searchFilterDto = productSearchDto)
    }

    fun getList(authorizedUser: AuthorizedUser? = null, searchFilterDto: ProductSearchDto): List<ProductListItemOutputDto> = transaction {
        val ordering = if (authorizedUser != null && searchFilterDto.filters?.availability != null && searchFilterDto.filters.availability){
            listOf(StockToProductModel.amount to SortOrder.DESC_NULLS_LAST, ProductModel.id to SortOrder.ASC)
        } else{
            listOf(ProductModel.id to SortOrder.ASC)
        }

        val search = searchFilterDto.filters ?: ProductSearchFilterDto()

        val ids = ProductModel.slice(ProductModel.id).select {
            convertToOperator(search)
        }.map { it[ProductModel.id] }

        val photosMapped: MutableMap<Int, MutableList<String>> = mutableMapOf()

        ProductToImageModel.join(
            GalleryModel,
            JoinType.LEFT,
            additionalConstraint = {
                ProductToImageModel.photo eq GalleryModel.id
            }
        ).slice(ProductToImageModel.photo, GalleryModel.url).select {
            ProductToImageModel.product inList ids
        }.map {
            photosMapped[it[ProductToImageModel.photo].value]?.add(it[GalleryModel.url])
                ?: with(photosMapped) {
                    this[it[ProductToImageModel.photo].value] = mutableListOf(it[GalleryModel.url])
                }
        }

        val slice = mutableListOf(
            ProductModel.id,
            ProductModel.name,
            ProductModel.vendorCode,
            ProductModel.commonPrice,
            ProductModel.eanCode
        )

        if (authorizedUser != null)
            slice.add(StockToProductModel.amount)

        with(
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
        }.orderBy(*ordering.toTypedArray()).map {
            val amount = try { it[StockToProductModel.amount].toDouble() } catch (_: Exception) { 0.0 }

            val id = it[ProductModel.id].value
            ProductListItemOutputDto(
                id = id,
                name = it[ProductModel.name],
                vendorCode = it[ProductModel.vendorCode],
                quantity = amount,
                price = it[ProductModel.commonPrice],
                photo = photosMapped[id] ?: mutableListOf(),
                eanCode = it[ProductModel.eanCode]
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

        StockDao.find { StockModel.id inList stocks }.map { it.toOutputDto() }
    }

    fun getSliceBasedOnDto(demandDto: ProductFieldsDemandDto): MutableList<Pair<Column<*>, String>> {
        var slice = mutableListOf<Pair<Column<*>, String>>()

        if (demandDto.id != null) slice.add (ProductModel.id to demandDto.id)
        if (demandDto.vendorCode != null) slice.add(ProductModel.vendorCode to demandDto.vendorCode)
        if (demandDto.eanCode != null) slice.add(ProductModel.eanCode to demandDto.eanCode)
        if (demandDto.barcode != null) slice.add(ProductModel.barcode to demandDto.barcode)
        if (demandDto.brand != null) slice.add(BrandModel.name to demandDto.brand)
        if (demandDto.name != null) slice.add(ProductModel.name to demandDto.name)
        if (demandDto.description != null) slice.add(ProductModel.description to demandDto.description)
        if (demandDto.lastPurchasePrice != null) slice.add(ProductModel.lastPurchasePrice to demandDto.lastPurchasePrice)
        if (demandDto.cost != null) slice.add(ProductModel.cost to demandDto.cost)
        if (demandDto.lastPurchaseDate != null) slice.add(ProductModel.lastPurchaseDate to demandDto.lastPurchaseDate)
        if (demandDto.distributorPrice != null) slice.add(ProductModel.distributorPrice to demandDto.distributorPrice)
        if (demandDto.professionalPrice != null) slice.add(ProductModel.professionalPrice to demandDto.professionalPrice)
        if (demandDto.commonPrice != null) slice.add(ProductModel.commonPrice to demandDto.commonPrice)
        if (demandDto.category != null) slice.add(CategoryModel.name to demandDto.category)
        if (demandDto.collection != null) slice.add(CollectionModel.name to demandDto.collection)
        if (demandDto.color != null) slice.add(ProductModel.color to demandDto.color)
        if (demandDto.amountInBox != null) slice.add(ProductModel.amountInBox to demandDto.amountInBox)
        if (demandDto.expirationDate != null) slice.add(ProductModel.expirationDate to demandDto.expirationDate)
        if (demandDto.link != null) slice.add(ProductModel.link to demandDto.link)
        if (demandDto.distributorPercent != null) slice.add(ProductModel.distributorPercent to demandDto.distributorPercent)
        if (demandDto.professionalPercent != null) slice.add(ProductModel.professionalPercent to demandDto.professionalPercent)
        if (demandDto.offertaPrice != null) slice.add(ProductModel.offertaPrice to demandDto.offertaPrice)

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
        ProductDao.find {
            ProductModel.barcode eq barCode
        }.map { el -> el.listItemDto }
    }
}