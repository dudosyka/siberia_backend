package siberia.modules.product.data.dto

import kotlinx.serialization.Serializable
import siberia.modules.brand.data.dto.BrandOutputDto
import siberia.modules.category.data.dto.CategoryOutputDto
import siberia.modules.collection.data.dto.CollectionOutputDto

@Serializable
data class ProductFullOutputDto (
    val id: Int,
    val photo: List<String>,
    val photoIds: List<Int>,
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
    val distributorPercent: Double,
    val professionalPercent: Double,
    val commonPrice: Double,
    val category: CategoryOutputDto?,
    val collection: CollectionOutputDto?,
    val color: String,
    val amountInBox: Int,
    val expirationDate: Long,
    val link: String,
    val quantity: Double,
    val offertaPrice: Double?,
    val createdAt: String,
//    Future iterations
//    val size: Double,
//    val volume: Double,
)