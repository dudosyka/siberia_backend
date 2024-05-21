package siberia.modules.transaction.data.dto

import kotlinx.serialization.Serializable
import siberia.modules.product.data.dto.ProductOutputDto
import siberia.modules.stock.data.dto.StockOutputDto
import siberia.modules.transaction.data.dto.status.TransactionStatusOutputDto
import siberia.modules.transaction.data.dto.type.TransactionTypeOutputDto

@Serializable
data class TransactionFullOutputDto (
    val id: Int,
    val from: StockOutputDto?,
    val to: StockOutputDto?,
    val status: TransactionStatusOutputDto,
    val type: TransactionTypeOutputDto,
    val products: List<TransactionProductDto>,
    val isPaid: Boolean?,
    val arrivalDate: String?,
    val timestamp: String,
    var availableStatuses: List<TransactionStatusOutputDto> = listOf(),
) {
    @Serializable
    data class TransactionProductDto(
        val product: ProductOutputDto,
        var amount: Double,
        var actualAmount: Double?,
        val price: Double? = null
    )
}