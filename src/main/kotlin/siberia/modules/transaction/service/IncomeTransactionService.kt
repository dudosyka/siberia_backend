package siberia.modules.transaction.service

import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import siberia.conf.AppConf
import siberia.exceptions.BadRequestException
import siberia.exceptions.ForbiddenException
import siberia.modules.auth.data.dto.AuthorizedUser
import siberia.modules.stock.data.dao.StockDao
import siberia.modules.stock.data.models.StockModel
import siberia.modules.transaction.data.dao.TransactionDao
import siberia.modules.transaction.data.dao.TransactionStatusDao
import siberia.modules.transaction.data.dto.TransactionInputDto
import siberia.modules.transaction.data.dto.TransactionOutputDto
import siberia.modules.transaction.data.dto.systemevents.PartialDeliveringEvent
import siberia.modules.transaction.data.dto.systemevents.TransactionUpdateEvent
import siberia.modules.transaction.data.models.TransactionModel
import siberia.modules.transaction.data.models.TransactionRelatedUserModel
import siberia.modules.user.data.dao.UserDao

class IncomeTransactionService(di: DI) : AbstractTransactionService(di) {
    fun create(authorizedUser: AuthorizedUser, transactionInputDto: TransactionInputDto): TransactionOutputDto = transaction {
        val userDao = UserDao[authorizedUser.id]
        val targetStockId = transactionInputDto.to ?: throw BadRequestException("Incorrect target stock")
        if (transactionInputDto.isPaid == null)
            transactionInputDto.isPaid = false
        if (transactionInputDto.type != AppConf.requestTypes.income)
            throw BadRequestException("Bad transaction type")
        val transactionDao = createTransaction(userDao, transactionInputDto, targetStockId)

        commit()

        transactionDao.toOutputDto()
    }

    fun cancelCreation(authorizedUser: AuthorizedUser, transactionId: Int): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        if (transactionDao.typeId != AppConf.requestTypes.income)
            throw ForbiddenException()
        val targetStockId = transactionDao.toId ?: throw BadRequestException("Bad transaction")

        changeStatusTo(
            authorizedUser,
            transactionId,
            targetStockId,
            AppConf.requestStatus.creationCancelled
        ).toOutputDto()
    }

    fun receivePartially(authorizedUser: AuthorizedUser, transactionId: Int, productsDiff: List<TransactionInputDto.TransactionProductInputDto>): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        val author = UserDao[authorizedUser.id]
        val availableStatuses = listOf(
            AppConf.requestStatus.created, AppConf.requestStatus.inProgress
        )
        if (transactionDao.typeId != AppConf.requestTypes.income || !availableStatuses.contains(transactionDao.statusId))
            throw ForbiddenException()

        val targetStockId = transactionDao.toId ?: throw BadRequestException("Bad transaction")
        val targetStock = StockDao[targetStockId]

        transactionDao.isPaid = true

        TransactionModel.updateActualByDiff(transactionId, productsDiff)

        StockModel.appendProducts(targetStockId, productsDiff.map {
            TransactionInputDto.TransactionProductInputDto(
                it.productId,
                it.amount
            )
        })

        if (transactionDao.statusId == AppConf.requestStatus.created) {
            if (!checkAccessToStatusForTransaction(author, transactionId, targetStockId, AppConf.requestStatus.inProgress))
                throw ForbiddenException()
            transactionDao.status = TransactionStatusDao[AppConf.requestStatus.inProgress]
        }

        transactionDao.flush()

        val event = TransactionUpdateEvent(author.login, targetStock.name, transactionId, transactionDao.typeId)
        event.eventObjectData = PartialDeliveringEvent(productsDiff).json

        TransactionRelatedUserModel.addRelated(authorizedUser.id, transactionId)
        transactionSocketService.updateStatus(transactionDao.fullOutput())

        transactionDao.toOutputDto()
    }

    fun processed(authorizedUser: AuthorizedUser, transactionId: Int, productsDiff: List<TransactionInputDto.TransactionProductInputDto>): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        val availableStatuses = listOf(
            AppConf.requestStatus.created, AppConf.requestStatus.inProgress
        )
        if (transactionDao.typeId != AppConf.requestTypes.income || !availableStatuses.contains(transactionDao.statusId))
            throw ForbiddenException()

        val targetStockId = transactionDao.toId ?: throw BadRequestException("Bad transaction")

        transactionDao.isPaid = true
        transactionDao.flush()

        val approvedTransaction = changeStatusTo(authorizedUser, transactionId, targetStockId, AppConf.requestStatus.processed)

        TransactionModel.updateActualByDiff(transactionId, productsDiff)

        StockModel.appendProducts(targetStockId, productsDiff.map {
            TransactionInputDto.TransactionProductInputDto(
                it.productId,
                it.amount
            )
        })

        approvedTransaction.toOutputDto()
    }
}