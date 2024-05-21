package siberia.modules.transaction.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class TransactionInputDto (
    val from: Int? = null,
    val to: Int? = null,
    val type: Int,
    val products: List<TransactionProductInputDto>,
    var hidden: Boolean = false,
    var isPaid: Boolean? = null,
    val arrivalDate: Long? = null
) {
    @Serializable
    data class TransactionProductInputDto(
        val productId: Int,
        val amount: Double,
        val price: Double? = null
    )
}