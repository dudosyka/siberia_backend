package siberia.modules.logger.data.dto

import kotlinx.serialization.Serializable

@Serializable
abstract class SystemEventCreateDto {
    abstract val author: String

    abstract val eventType: Int
    abstract val eventDescription: String

    abstract val eventObjectName: String
    abstract val eventObjectType: Int

    abstract val eventObjectId: Int

    var eventObjectData: String = ""
}