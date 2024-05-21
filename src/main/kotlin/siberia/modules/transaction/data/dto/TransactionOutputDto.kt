package siberia.modules.transaction.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class TransactionOutputDto (
    val id: Int,
    val from: Int? = null,
    val to: Int? = null,
    val status: Int,
    val type: Int,
    val isPaid: Boolean?,
    val timestamp: String,
    val arrivalDate: String?,
)