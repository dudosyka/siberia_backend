package siberia.modules.product.data.models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import siberia.modules.brand.data.models.BrandModel
import siberia.modules.category.data.models.CategoryModel
import siberia.modules.collection.data.models.CollectionModel
import siberia.modules.product.data.dao.ProductDao
import siberia.modules.product.data.dto.groups.MassiveUpdateDto
import siberia.modules.product.data.dto.ProductOutputDto
import siberia.modules.product.data.dto.ProductUpdateDto
import siberia.utils.database.BaseIntIdTable
import siberia.utils.database.idValue

object ProductModel: BaseIntIdTable() {
    val vendorCode = text("vendor_code")
    val barcode = text("barcode").nullable().default(null)
    val brand = reference("brand", BrandModel, ReferenceOption.SET_NULL, ReferenceOption.SET_NULL).nullable().default(null)
    val name = text("name")
    val description = text("description")
    val lastPurchasePrice = double("last_purchase_price").nullable().default(null)
    val cost = double("cost").nullable().default(null)
    val lastPurchaseDate = long("last_purchase_date").nullable().default(null)
    val distributorPrice = double("distributor_price")
    val professionalPrice = double("professional_price")
    val commonPrice = double("common_price")
    val category = reference("category", CategoryModel, ReferenceOption.SET_NULL, ReferenceOption.SET_NULL).nullable()
    val collection = reference("collection", CollectionModel, ReferenceOption.SET_NULL, ReferenceOption.SET_NULL).nullable().default(null)
    val color = text("color")
    val amountInBox = integer("amount_in_box")
    val expirationDate = long("expiration_date")
    val link = text("link")

    val distributorPercent = double("distributor_percent").default(1.0)
    val professionalPercent = double("professional_percent").default(1.0)
    val eanCode = text("ean_code")
    val offertaPrice = double("offertaPrice").default(0.0)
    //Future iterations
//    val size = double("size").default(1.0)
//    val volume = double("volume").default(1.0)

    fun updateBatch(products: List<Int>, massiveUpdateDto: MassiveUpdateDto): List<ProductUpdateDto> = transaction {
        val rollbackList = ProductDao.find {
            ProductModel.id inList products
        }.map {
            it.getRollbackInstance<ProductOutputDto, ProductUpdateDto>(massiveUpdateDto.productUpdateDto(it.idValue))
        }

        ProductModel.update({ ProductModel.id inList products }) {
            if (massiveUpdateDto.brand != 0 && massiveUpdateDto.brand != null)
                it[brand] = massiveUpdateDto.brand
            else if (massiveUpdateDto.brand == 0)
                it[brand] = null

            if (massiveUpdateDto.collection != 0 && massiveUpdateDto.collection != null)
                it[collection] = massiveUpdateDto.collection
            else if (massiveUpdateDto.collection == 0)
                it[collection] = null

            if (massiveUpdateDto.category != 0 && massiveUpdateDto.category != null)
                it[category] = massiveUpdateDto.category
            else if (massiveUpdateDto.category == 0)
                it[category] = null

            if (massiveUpdateDto.name != null)
                it[name] = massiveUpdateDto.name!!

            if (massiveUpdateDto.description != null)
                it[description] = massiveUpdateDto.description!!

            if (massiveUpdateDto.commonPrice != null)
                it[commonPrice] = massiveUpdateDto.commonPrice!!

            if (massiveUpdateDto.color != null)
                it[color] = massiveUpdateDto.color!!

            if (massiveUpdateDto.amountInBox != null)
                it[amountInBox] = massiveUpdateDto.amountInBox!!

            if (massiveUpdateDto.expirationDate != null)
                it[expirationDate] = massiveUpdateDto.expirationDate!!

            if (massiveUpdateDto.link != null)
                it[link] = massiveUpdateDto.link!!

            if (massiveUpdateDto.distributorPercent != null)
                it[distributorPercent] = massiveUpdateDto.distributorPercent

            if (massiveUpdateDto.professionalPercent != null)
                it[professionalPercent] = massiveUpdateDto.professionalPercent

            if (massiveUpdateDto.offertaPrice != null)
                it[offertaPrice] = massiveUpdateDto.offertaPrice
        }

        rollbackList
    }
}