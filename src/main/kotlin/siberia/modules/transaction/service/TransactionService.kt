package siberia.modules.transaction.service

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.conf.AppConf
import siberia.exceptions.ForbiddenException
import siberia.modules.auth.data.dto.AuthorizedUser
import siberia.modules.rbac.data.dao.RuleDao.Companion.createListCond
import siberia.modules.rbac.data.dao.RuleDao.Companion.createNullableListCond
import siberia.modules.transaction.data.dao.TransactionDao
import siberia.modules.transaction.data.dao.TransactionStatusDao
import siberia.modules.transaction.data.dto.*
import siberia.modules.transaction.data.dto.status.TransactionStatusOutputDto
import siberia.modules.transaction.data.dto.type.TransactionTypeOutputDto
import siberia.modules.user.data.dao.UserDao
import siberia.modules.user.service.UserAccessControlService
import siberia.plugins.Logger
import siberia.utils.database.idValue
import org.jetbrains.exposed.sql.transactions.transaction
import siberia.conf.AppConf.requestStatus
import siberia.conf.AppConf.requestTypes
import siberia.exceptions.BadRequestException
import siberia.modules.product.data.dao.ProductDao
import siberia.modules.transaction.data.models.*
import siberia.utils.kodein.KodeinService

class TransactionService(di: DI) : KodeinService(di) {
    private val userAccessControlService: UserAccessControlService by instance()

    fun getAllTypes() = transaction { TransactionTypeModel.selectAll().map { TransactionTypeOutputDto(id = it[TransactionTypeModel.id].value, name = it[TransactionTypeModel.name]) } }

    fun getAllStatuses() = transaction { TransactionStatusModel.selectAll().map { TransactionStatusOutputDto(id = it[TransactionStatusModel.id].value, name = it[TransactionStatusModel.name]) } }

    private fun getTargetStock(transactionDao: TransactionDao, statusId: Int): Int {
        val statusToStock = AppConf.requestToStockMapper[transactionDao.typeId]
        val stockPair = if (statusToStock != null)
            statusToStock[statusId] ?: throw BadRequestException("Bad status")
        else
            throw BadRequestException("Bad type")

        Logger.debug(stockPair, "main")
        Logger.debug(statusId, "main")
        Logger.debug(transactionDao, "main")

        return (if (stockPair == AppConf.StockPair.TO)
            transactionDao.to?.idValue
        else
            transactionDao.from?.idValue
                ) ?: throw Exception("Bad transaction")
    }

    private fun checkAccessToTransaction(authorizedUser: AuthorizedUser, transactionId: Int): Boolean = transaction {
        val transactionDao = TransactionDao[transactionId]
        val transactionStocks = listOfNotNull(
            transactionDao.to?.idValue, transactionDao.from?.idValue
        )
        userAccessControlService.filterAvailable(authorizedUser.id, transactionStocks).isNotEmpty() || transactionDao.typeId == requestTypes.transfer
    }


    fun getAvailableTransactions(authorizedUser: AuthorizedUser, transactionSearchFilter: TransactionSearchFilter): List<TransactionListItemOutputDto> = transaction {
        val availableStocksWithRules = userAccessControlService.getAvailableStocksWithOperations(authorizedUser.id)
        val availableStocks = availableStocksWithRules.map { it.key }

        val showClosed = if (!transactionSearchFilter.showClosed)
            TransactionModel.status notInList listOf(
                requestStatus.creationCancelled,
                requestStatus.delivered,
                requestStatus.processed,
                requestStatus.processingCancelled,
                requestStatus.failed,
                requestStatus.notDelivered,
            ).filter { !(transactionSearchFilter.status?.contains(it) ?: false) }
        else //If show closed just add "trash" query
            TransactionModel.id neq 0

        val restricted = TransactionModel.select {
            (TransactionModel.from notInList availableStocks) and
            (TransactionModel.to notInList availableStocks)
        }.map { it[TransactionModel.id].value }.toMutableList()


        //If status is OPEN it means that managers from all other stocks can see that request
        if (authorizedUser.rules.map { rule -> rule.ruleId }.contains(AppConf.rules.manageTransferRequest))
            TransactionModel.select {
                (TransactionModel.status eq requestStatus.open) and
                (TransactionModel.type eq requestTypes.transfer)
            }.map {
                restricted.remove(it[TransactionModel.id].value)
            }

        TransactionModel.select {
            TransactionModel.hidden eq false and
            (TransactionModel.id notInList restricted) and
            showClosed and
            (
                createNullableListCond(transactionSearchFilter.to, TransactionModel.id.isNotNull(), TransactionModel.to) or
                createNullableListCond(
                    transactionSearchFilter.from,
                    createNullableListCond(transactionSearchFilter.to, TransactionModel.id.isNotNull(), TransactionModel.to),
                    TransactionModel.from
                )
            ) and
            createListCond(transactionSearchFilter.status, TransactionModel.id.isNotNull(), TransactionModel.status) and
            createListCond(transactionSearchFilter.type, TransactionModel.id.isNotNull(), TransactionModel.type)
        }.orderBy(Pair(TransactionModel.updatedAt, SortOrder.DESC_NULLS_FIRST), Pair(TransactionModel.status, SortOrder.ASC)).map {
            TransactionDao.wrapRow(it).listItemOutputDto
        }
    }

    fun getAvailableStatuses(authorizedUser: AuthorizedUser, transactionId: Int): List<TransactionStatusOutputDto> = transaction {
        val userDao = UserDao[authorizedUser.id]
        val transactionDao = TransactionDao[transactionId]
        if (!checkAccessToTransaction(authorizedUser, transactionId))
            throw ForbiddenException()
        val hasAccessToManageTransfers = userDao.hasAccessToProcessTransfers
        Logger.debug("HAS ACCESS TO MANAGE", "main")
        Logger.debug(hasAccessToManageTransfers, "main")
        Logger.debug(userDao.rulesWithStocks, "main")
        Logger.debug(TransactionUtils.availableStatuses(transactionDao), "main")
        TransactionUtils.availableStatuses(transactionDao).filter {
            if (
                transactionDao.statusId == requestStatus.open
                && transactionDao.typeId == requestTypes.transfer
                && hasAccessToManageTransfers
            )
                true
            else if (it == requestStatus.inProgress && !hasAccessToManageTransfers)
                false
            else {
                val ruleId = TransactionUtils.mapTypeToRule(transactionDao.typeId, it)
                Logger.debug("Transaction output", "main")
                Logger.debug(transactionDao.toOutputDto(), "main")
                val targetStock = getTargetStock(transactionDao, it)
                (userAccessControlService.checkAccessToStock(authorizedUser.id, ruleId, targetStock))
            }
        }.map { TransactionStatusDao[it].toOutputDto() }
    }

    fun getOne(authorizedUser: AuthorizedUser, transactionId: Int): TransactionFullOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        if (checkAccessToTransaction(authorizedUser, transactionId))
            transactionDao.fullOutput()
        else
            throw ForbiddenException()
    }

    fun getTransactionOnAssembly(authorizedUser: AuthorizedUser): List<TransactionListItemOutputDto> = transaction {
        TransactionModel
            .select {
                (TransactionModel.from inList listOfNotNull(authorizedUser.stockId)) and
                (TransactionModel.status eq requestStatus.open) and
                (TransactionModel.type eq requestTypes.outcome) and
                (TransactionModel.hidden eq false)
            }
            .sortedBy { TransactionModel.updatedAt }
            .map {
                TransactionDao.wrapRow(it).listItemOutputDto
            }
    }

    fun getProductsFromTransactions(authorizedUser: AuthorizedUser, transactionsList: List<Int>): List<TransactionFullOutputDto.TransactionProductDto> = transaction {
        val checkAccess = transactionsList.all { checkAccessToTransaction(authorizedUser, it) }
        if (!checkAccess)
            throw ForbiddenException()

        val productsMap: MutableMap<Int, TransactionFullOutputDto.TransactionProductDto> = mutableMapOf()

        TransactionToProductModel.select {
            TransactionToProductModel.transaction inList transactionsList
        }.map {
            val productId = it[TransactionToProductModel.product].value
            val productDao = ProductDao[productId]
            val transactionProductDto = TransactionFullOutputDto.TransactionProductDto(
                productDao.toOutputDto(),
                it[TransactionToProductModel.amount],
                it[TransactionToProductModel.actualAmount],
                it[TransactionToProductModel.price]
            )
            if (productsMap.containsKey(productId))
                productsMap[productId]!!.amount += transactionProductDto.amount
            else
                productsMap[productId] = transactionProductDto
        }

        productsMap.values.toList()
    }

    fun getTransactions(authorizedUser: AuthorizedUser, transactionsList: List<Int>): List<TransactionFullOutputDto> = transaction {
        val checkAccess = transactionsList.all { checkAccessToTransaction(authorizedUser, it) }
        if (!checkAccess)
            throw ForbiddenException()

        transactionsList.map { TransactionDao[it].fullOutput() }
    }

    fun getTransactionForQr(authorizedUser: AuthorizedUser, transactionId: Int): TransactionDao = transaction {
        val transactionDao = TransactionDao[transactionId]
        if (!checkAccessToTransaction(authorizedUser, transactionId))
            throw ForbiddenException()

        //Additional check for transfers
        if (transactionDao.statusId == requestStatus.inProgress) {
            val statuses = getAvailableStatuses(authorizedUser, transactionId)
            if (!statuses.map { it.id }.contains(requestStatus.delivered))
                throw ForbiddenException()
        }

        val transferAvailableStatuses = listOf(
            requestStatus.inProgress, requestStatus.open
        )

        val outcomeAvailableStatuses = listOf(
            requestStatus.open
        )

        val incomeAvailableStatuses = listOf(
            requestStatus.created
        )

        val writeOffAvailableStatuses = listOf<Int>()

        val transferForbidden =
            transactionDao.typeId == requestTypes.transfer &&
            !transferAvailableStatuses.contains(transactionDao.statusId)

        val outcomeForbidden =
            transactionDao.typeId == requestTypes.outcome &&
            !outcomeAvailableStatuses.contains(transactionDao.statusId)

        val incomeForbidden =
            transactionDao.typeId == requestTypes.income &&
            !incomeAvailableStatuses.contains(transactionDao.statusId)

        val writeOffForbidden =
            transactionDao.typeId == requestTypes.writeOff &&
            !writeOffAvailableStatuses.contains(transactionDao.statusId)

        if (transferForbidden || outcomeForbidden || incomeForbidden || writeOffForbidden)
            throw ForbiddenException()

        transactionDao
    }
}