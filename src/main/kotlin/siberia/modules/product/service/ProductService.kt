package siberia.modules.product.service

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
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
import siberia.modules.rbac.data.dao.RoleDao.Companion.createNullableRangeCond
import siberia.modules.rbac.data.dao.RuleCategoryDao.Companion.createNullableListCond
import siberia.modules.stock.data.dao.StockDao
import siberia.modules.stock.data.dao.StockDao.Companion.createLikeCond
import siberia.modules.stock.data.dto.StockOutputDto
import siberia.modules.stock.data.models.StockModel
import siberia.modules.stock.data.models.StockToProductModel
import siberia.modules.transaction.data.dto.TransactionFullOutputDto
import siberia.modules.user.data.dao.UserDao
import siberia.utils.kodein.KodeinService
import java.time.LocalDateTime
import java.time.ZoneOffset

class ProductService(di: DI) : KodeinService(di) {
    private val galleryService: GalleryService by instance()
    private fun createDao(productCreateDto: ProductCreateDto): ProductDao = transaction {
        val product = ProductDao.new {
            vendorCode = productCreateDto.vendorCode!!
            eanCode = productCreateDto.eanCode!!
            barcode = productCreateDto.barcode
            brand = if (productCreateDto.brand != null) BrandDao[productCreateDto.brand!!] else null
            name = productCreateDto.name!!
            description = productCreateDto.description!!
            distributorPrice = productCreateDto.distributorPrice!!
            professionalPrice = productCreateDto.professionalPrice!!
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
        val event = ProductCreateEvent(userDao.login, productCreateDto.name!!, productCreateDto.vendorCode!!)

        productCreateDto.photoList = galleryService.filterExists( productCreateDto.photoList ?: listOf())

        val productDao = createDao(productCreateDto)

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
            ProductMassiveInsertRollbackDto.serializer(),
            ProductMassiveInsertRollbackDto(insertedProducts)
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

    private fun SqlExpressionBuilder.convertToOperator(productSearchDto: ProductSearchDto): Op<Boolean> {

        val searchFilterDto = productSearchDto.filters

        return createRangeCond(searchFilterDto?.amountInBox, (ProductModel.id neq 0), ProductModel.amountInBox, -1, Int.MAX_VALUE) and
            createRangeCond(searchFilterDto?.commonPrice, (ProductModel.id neq 0), ProductModel.commonPrice, -1.0, Double.MAX_VALUE) and
            createNullableRangeCond(searchFilterDto?.purchasePrice, (ProductModel.id neq 0), ProductModel.lastPurchasePrice, -1.0, Double.MAX_VALUE) and
            createRangeCond(searchFilterDto?.distributorPrice, (ProductModel.id neq 0), ProductModel.distributorPrice, -1.0, Double.MAX_VALUE) and
            createRangeCond(searchFilterDto?.professionalPrice, (ProductModel.id neq 0), ProductModel.professionalPrice, -1.0, Double.MAX_VALUE) and
            createNullableListCond(searchFilterDto?.brand, (ProductModel.id neq 0), ProductModel.brand) and
            createNullableListCond(searchFilterDto?.category, (ProductModel.id neq 0), ProductModel.category) and
            createNullableListCond(searchFilterDto?.collection, (ProductModel.id neq 0), ProductModel.collection) and
            createLikeCond(searchFilterDto?.name, (ProductModel.id neq 0), ProductModel.name) and
            createLikeCond(searchFilterDto?.color, (ProductModel.id neq 0), ProductModel.color) and
            createLikeCond(searchFilterDto?.vendorCode, (ProductModel.id neq 0), ProductModel.vendorCode) and
            createLikeCond(searchFilterDto?.description, (ProductModel.id neq 0), ProductModel.description) and
            createRangeCond(searchFilterDto?.offertaPrice, (ProductModel.id neq 0), ProductModel.offertaPrice, -1.0, Double.MAX_VALUE)

//            Future iterations
//            createRangeCond(searchFilterDto.size, (ProductModel.id neq 0), ProductModel.size, -1.0, Double.MAX_VALUE) and
//            createRangeCond(searchFilterDto.volume, (ProductModel.id neq 0), ProductModel.volume, -1.0, Double.MAX_VALUE) and
    }

    fun getByFilter(productSearchDto: ProductSearchDto): List<ProductListItemOutputDto> = transaction {
        getAvailableByFilter(searchFilterDto = productSearchDto)
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

    fun getAvailableByFilter(
        authorizedUser: AuthorizedUser? = null,
        searchFilterDto: ProductSearchDto
    ): List<ProductListItemOutputDto> = transaction {

        val ordering = if (authorizedUser != null && searchFilterDto.filters?.availability != null && searchFilterDto.filters.availability){
            listOf(StockToProductModel.amount to SortOrder.DESC_NULLS_LAST, ProductModel.id to SortOrder.ASC)
        } else{
            listOf(ProductModel.id to SortOrder.ASC)
        }

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
                convertToOperator(searchFilterDto)
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
}