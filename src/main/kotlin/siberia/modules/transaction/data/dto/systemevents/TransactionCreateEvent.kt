package siberia.modules.transaction.data.dto.systemevents

import siberia.conf.AppConf

data class TransactionCreateEvent(
    override val author: String, val createdTransactionStockName: String, val createdTransactionId: Int
) : TransactionEvent() {
    override val eventType: Int
        get() = AppConf.eventTypes.createEvent
    override val eventDescription: String
        get() = "Transaction (id = $createdTransactionId) for $createdTransactionStockName stock was created."
    override val eventObjectName: String
        get() = createdTransactionId.toString()
}