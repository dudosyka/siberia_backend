package siberia.modules.transaction.data.dto.systemevents

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import siberia.modules.transaction.data.dto.TransactionInputDto

@Serializable
data class PartialDeliveringEvent (
    val productsDiff: List<TransactionInputDto.TransactionProductInputDto>
) {
    @Transient private val serializer = Json { ignoreUnknownKeys = true }

    val json: String
        get() = serializer.encodeToString(serializer(), this)
}