package siberia.modules.category.data.dto.systemevents

import siberia.conf.AppConf

data class CategoryCreateEvent(
    override val author: String, val createdCategoryName: String
) : CategoryEvent() {
    override val eventObjectId: Int
        get() = 0
    override val rollbackInstance: String
        get() = ""
    override val eventType: Int
        get() = AppConf.eventTypes.createEvent
    override val eventDescription: String
        get() = "Category $createdCategoryName was created."
    override val eventObjectName: String
        get() = createdCategoryName
}