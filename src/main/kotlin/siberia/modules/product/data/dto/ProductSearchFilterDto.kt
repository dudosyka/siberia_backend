package siberia.modules.product.data.dto

import kotlinx.serialization.Serializable
import siberia.utils.database.FieldFilterWrapper

@Serializable
data class ProductSearchFilterDto (

    val vendorCode: String? = null,
    val brand: List<Int>? = null,
    val name: String? = null,
    val description: String? = null,
    val purchasePrice: Double? = null,
    val distributorPrice: FieldFilterWrapper<Double>? = null,
    val professionalPrice: FieldFilterWrapper<Double>? = null,
    val commonPrice: FieldFilterWrapper<Double>? = null,
    val category: List<Int>? = null,
    val collection: List<Int>? = null,
    val color: String? = null,
    val amountInBox: FieldFilterWrapper<Int>? = null,
    val size: FieldFilterWrapper<Double>? = null,
    val volume: FieldFilterWrapper<Double>? = null,
)