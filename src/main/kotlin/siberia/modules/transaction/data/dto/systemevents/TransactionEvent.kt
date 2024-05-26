package siberia.modules.transaction.data.dto.systemevents

import siberia.conf.AppConf
import siberia.modules.logger.data.dto.SystemEventCreateDto

abstract class TransactionEvent: SystemEventCreateDto() {
    override val eventObjectType: Int
        get() = AppConf.objectTypes.transactionEvent

}