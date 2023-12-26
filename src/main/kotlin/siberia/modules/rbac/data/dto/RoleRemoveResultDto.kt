package siberia.modules.rbac.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RoleRemoveResultDto (
    val success: Boolean,
    val message: String
)