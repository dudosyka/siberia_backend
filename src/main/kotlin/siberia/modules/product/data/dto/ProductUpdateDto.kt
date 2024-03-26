package siberia.modules.product.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProductUpdateDto (
    var photo: List<Int>? = null,
    var vendorCode: String? = null,
    val eanCode: String? = null,
    var barcode: String? = null,
    //If null -> dont update
    //If 0 -> set null
    //if not zero and not null -> update to what set
    var brand: Int? = null,
    var name: String? = null,
    var description: String? = null,
    //var distributorPrice: Double? = null,
    //var professionalPrice: Double? = null,
    var commonPrice: Double? = null,
    var category: Int? = null,
    //If null -> dont update
    //If 0 -> set null
    //if not zero and not null -> update to what set
    var collection: Int? = null,
    var color: String? = null,
    var amountInBox: Int? = null,
    var expirationDate: Long? = null,
    var link: String? = null,
    var isFileAlreadyUploaded: Boolean? = null,
    val distributorPercent: Double? = null,
    val professionalPercent: Double? = null,
    val id: Int? = null,
    val offertaPrice: Double? = null

    //Future iterations
    //val size: Double? = null,
    //val volume: Double? = null,
)
