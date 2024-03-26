package siberia.modules.product.data.dto

import siberia.modules.brand.data.dto.BrandOutputDto
import siberia.modules.category.data.dto.CategoryOutputDto
import siberia.modules.collection.data.dto.CollectionOutputDto
import siberia.modules.stock.data.models.StockProductsListMapped

data class  ProductRollbackDto (
    val id: Int,
    val photo: List<Int>,
    val vendorCode: String,
    val eanCode: String,
    val barcode: String?,
    val brand: BrandOutputDto?,
    val name: String,
    val description: String,
    val lastPurchasePrice: Double?,
    val cost: Double?,
    val lastPurchaseDate: Long?,
    val distributorPrice: Double,
    val professionalPrice: Double,
    val commonPrice: Double,
    val category: CategoryOutputDto?,
    val collection: CollectionOutputDto?,
    val color: String,
    val amountInBox: Int,
    val expirationDate: Long,
    val link: String,
    val quantity: Double,
    val stocksRelations: MutableMap<Int, StockProductsListMapped> = mutableMapOf(),
    val offertaPrice: Double?,
    val fileAlreadyUploaded: Boolean = true,
) {
    val createDto: ProductCreateDto get() = ProductCreateDto(
        photo, vendorCode, eanCode, barcode,
        brand?.id, name, description, distributorPrice,
        professionalPrice, commonPrice, category?.id,
        collection?.id, color, amountInBox,
        expirationDate, link, offertaPrice
    )
}