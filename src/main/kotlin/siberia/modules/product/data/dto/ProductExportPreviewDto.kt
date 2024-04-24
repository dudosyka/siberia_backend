package siberia.modules.product.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductExportPreviewDto(
    val id: Int,
    val photo: List<String>,
    val photoIds: List<Int>,
    val vendorCode: String,
    val barcode: String?,
    val brand: String?,
    val name: String,
    val description: String,
    val lastPurchasePrice: Double?,
    val distributorPrice: Double,
    val professionalPrice: Double,
    val cost: Double?,
    val lastPurchaseDate: Long?,
    val commonPrice: Double,
    val category: String?,
    val collection: String?,
    val color: String,
    val amountInBox: Int,
    val expirationDate: Long,
    val link: String,
    val distributorPercent: Double,
    val professionalPercent: Double,
    val eanCode: String,
    val offertaPrice: Double?
//    Future iterations
//    val size: Double,
//    val volume: Double,
)
