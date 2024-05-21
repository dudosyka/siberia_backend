package siberia.modules.transaction.data.dto

import kotlinx.serialization.Serializable
import siberia.modules.transaction.data.dto.status.TransactionStatusOutputDto
import siberia.modules.transaction.data.dto.type.TransactionTypeOutputDto

@Serializable
data class TransactionListItemOutputDto (
    val id: Int,
    val from: Int? = null,
    val fromName: String? = null,
    val to: Int? = null,
    val toName: String? = null,
    val status: TransactionStatusOutputDto,
    val type: TransactionTypeOutputDto,
    val isPaid: Boolean?,
    val timestamp: String,
    val arrivalDate: String?,
)