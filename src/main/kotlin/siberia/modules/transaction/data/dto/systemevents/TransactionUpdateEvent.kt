package siberia.modules.transaction.data.dto.systemevents

import siberia.conf.AppConf

data class TransactionUpdateEvent(
    override val author: String,
    val updatedTransactionStockName: String,
    override val eventObjectId: Int,
    val transactionType: Int
) : TransactionEvent() {
    override val eventType: Int
        get() = AppConf.eventTypes.updateEvent
    override val eventDescription: String
        get() = "Transaction (id = $eventObjectId) for $updatedTransactionStockName stock was updated."
    override val eventObjectName: String
        get() = "transactionType=(${transactionType}) №$eventObjectId"
}