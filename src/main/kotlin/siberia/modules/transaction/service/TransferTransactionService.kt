package siberia.modules.transaction.service

import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
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
import siberia.modules.transaction.data.models.TransactionModel
import siberia.modules.transaction.data.models.TransactionToProductModel
import siberia.modules.user.data.dao.UserDao
import siberia.utils.database.idValue

class TransferTransactionService(di: DI) : AbstractTransactionService(di) {
    private fun checkTypeAndChangeStatus(
        authorizedUser: AuthorizedUser,
        transactionDao: TransactionDao,
        targetStockId: Int,
        statusId: Int
    ): TransactionDao = transaction {
        if (transactionDao.typeId != AppConf.requestTypes.transfer)
            throw ForbiddenException()

        changeStatusTo(
            authorizedUser,
            transactionDao.idValue,
            targetStockId,
            statusId
        )
    }
    fun create(authorizedUser: AuthorizedUser, transactionInputDto: TransactionInputDto): TransactionOutputDto = transaction {
        val userDao = UserDao[authorizedUser.id]
        val targetStockId = transactionInputDto.to ?: throw BadRequestException("Incorrect target stock")
        if (transactionInputDto.type != AppConf.requestTypes.transfer)
            throw BadRequestException("Bad transaction type")

        val transactionDao = createTransaction(userDao, transactionInputDto, targetStockId)

        commit()

        try {
            approveCreation(authorizedUser, transactionDao.idValue)
        } catch (_: Exception) {
            transactionDao.toOutputDto()
        }
    }

    fun approveCreation(authorizedUser: AuthorizedUser, transactionId: Int): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        if (transactionDao.typeId != AppConf.requestTypes.transfer)
            throw ForbiddenException()

        changeStatusTo(
            authorizedUser,
            transactionId,
            transactionDao.toId ?: throw BadRequestException("Bad transaction"),
            AppConf.requestStatus.open
        ).toOutputDto()
    }

    fun cancelCreation(authorizedUser: AuthorizedUser, transactionId: Int): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        val targetStockId = transactionDao.toId ?: throw BadRequestException("Bad transaction")

        checkTypeAndChangeStatus(
            authorizedUser,
            transactionDao,
            targetStockId,
            AppConf.requestStatus.creationCancelled
        ).toOutputDto()
    }

    fun startProcess(authorizedUser: AuthorizedUser, transactionId: Int, processByStock: Int): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        val fromStock = StockDao[processByStock]

        StockModel.removeProducts(processByStock, transactionDao.inputProductsList)

        val transactionInProgress = checkTypeAndChangeStatus(
            authorizedUser,
            transactionDao,
            processByStock,
            AppConf.requestStatus.inProgress
        )

        transactionInProgress.from = fromStock
        transactionInProgress.flush()

        transactionInProgress.toOutputDto()
    }

    fun checkAccessToProcessTransfer(authorizedUser: AuthorizedUser, transactionId: Int, processByStock: Int): Boolean = transaction {
        val userDao = UserDao[authorizedUser.id]
        val transactionDao = TransactionDao[transactionId]

        if (!checkAccessToStatusForTransaction(userDao, transactionId, processByStock, AppConf.requestStatus.inProgress))
            throw ForbiddenException()

        try {
            StockModel.checkAvailableAmount(processByStock, transactionDao.inputProductsList)
        } catch (_: Exception) {
            throw ForbiddenException()
        }

        true
    }

    fun cancelProcess(authorizedUser: AuthorizedUser, transactionId: Int): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        val processingStockId = transactionDao.fromId ?: throw BadRequestException("Bad transaction")

        val cancelledTransaction = checkTypeAndChangeStatus(
            authorizedUser,
            transactionDao,
            processingStockId,
            AppConf.requestStatus.processingCancelled
        )

        StockModel.appendProducts(processingStockId, transactionDao.inputProductsList)

        cancelledTransaction.toOutputDto()
    }

    fun delivered(authorizedUser: AuthorizedUser, transactionId: Int): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        val targetStockId = transactionDao.toId ?: throw BadRequestException("Bad transaction")

        val deliveredTransaction = checkTypeAndChangeStatus(
            authorizedUser,
            transactionDao,
            targetStockId,
            AppConf.requestStatus.delivered
        )

        StockModel.appendProducts(targetStockId, transactionDao.inputProductsList)

        deliveredTransaction.toOutputDto()
    }

    fun partialDelivered(authorizedUser: AuthorizedUser, transactionId: Int, delivered: List<Int>): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]

        //Create two lists
        // notDelivered - products which are skipped in partial delivering;
        // delivered - products which are accepted in partial delivering
        val notDeliveredProducts = mutableListOf<TransactionInputDto.TransactionProductInputDto>()
        val deliveredProducts = mutableListOf<TransactionInputDto.TransactionProductInputDto>()
        var allNotDelivered = true
        transactionDao.inputProductsList.forEach {
            if (delivered.contains(it.productId)) {
                allNotDelivered = false
                deliveredProducts.add(it)
            }
            else
                notDeliveredProducts.add(it)
        }

        // All not delivered means that
        // delivered array is empty or contains only products which are not located in stock
        if (allNotDelivered)
            throw BadRequestException("Bad delivered array")

        if (notDeliveredProducts.isNotEmpty()) {

            // To use standard delivering method we need to remove products which are skipped
            TransactionToProductModel.deleteWhere {
                (product inList notDeliveredProducts.map { it.productId }) and
                        (transaction eq transactionId)
            }

        }

        //Run standard delivered process
        val resultDto = delivered(authorizedUser, transactionId)

        if (notDeliveredProducts.isNotEmpty()) {
            //We don't need rules checking here due to delivered method using
            // (if not enough rules it will throw exception and that code become unreachable)
            val newTransactionDao = TransactionModel.create(
                TransactionInputDto(
                    transactionDao.fromId,
                    transactionDao.toId,
                    AppConf.requestTypes.transfer,
                    notDeliveredProducts
                )
            )
            newTransactionDao.status = TransactionStatusDao[AppConf.requestStatus.inProgress]
            newTransactionDao.flush()

        }

        resultDto
    }

    fun notDelivered(authorizedUser: AuthorizedUser, transactionId: Int): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        val targetStockId = transactionDao.toId ?: throw BadRequestException("Bad transaction")

        checkTypeAndChangeStatus(
            authorizedUser,
            transactionDao,
            targetStockId,
            AppConf.requestStatus.notDelivered
        ).toOutputDto()
    }

    fun solveNotDelivered(authorizedUser: AuthorizedUser, transactionId: Int, solveStatus: Int): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        if (transactionDao.typeId != AppConf.requestTypes.transfer)
            throw ForbiddenException()
        val targetStockId = when (solveStatus) {
            AppConf.requestStatus.failed -> {
                null
            }
            AppConf.requestStatus.delivered -> {
                transactionDao.toId ?: throw BadRequestException("Bad transaction")
            }
            AppConf.requestStatus.deliveryCancelled -> {
                transactionDao.fromId ?: throw BadRequestException("Bad transaction")
            }
            else -> {
                throw BadRequestException("Bad status provided")
            }
        }

        if (targetStockId != null)
            StockModel.appendProducts(targetStockId, transactionDao.inputProductsList)

        changeStatusTo(
            authorizedUser,
            transactionId,
            transactionDao.fromId ?: 0,
            solveStatus,
            checkRules = false
        ).toOutputDto()
    }
}