package siberia.modules.transaction.service

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.conf.AppConf
import siberia.exceptions.BadRequestException
import siberia.exceptions.ForbiddenException
import siberia.modules.auth.data.dto.AuthorizedUser
import siberia.modules.product.service.ProductService
import siberia.modules.stock.data.dao.StockDao
import siberia.modules.stock.data.models.StockModel
import siberia.modules.transaction.data.dao.TransactionDao
import siberia.modules.transaction.data.dto.TransactionInputDto
import siberia.modules.transaction.data.dto.TransactionOutputDto
import siberia.modules.transaction.data.models.TransactionModel
import siberia.modules.transaction.data.models.TransactionToProductModel
import siberia.modules.user.data.dao.UserDao

class IncomeTransactionService(di: DI) : AbstractTransactionService(di) {
    private val productService: ProductService by instance()
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

    fun processed(authorizedUser: AuthorizedUser, transactionId: Int, productsDiff: List<TransactionInputDto.TransactionProductInputDto>): TransactionOutputDto = transaction {
        val transactionDao = TransactionDao[transactionId]
        if (transactionDao.typeId != AppConf.requestTypes.income)
            throw ForbiddenException()

        val targetStockId = transactionDao.toId ?: throw BadRequestException("Bad transaction")
        val approvedTransaction = changeStatusTo(authorizedUser, transactionId, targetStockId, AppConf.requestStatus.processed)

        transactionDao.isPaid = true
        transactionDao.flush()

        StockDao[targetStockId]
        val products = TransactionModel.updateAndSetActualProducts(transactionId, productsDiff)
        productService.updateLastPurchaseData(products, approvedTransaction.updatedAt ?: approvedTransaction.createdAt)
        StockModel.appendProducts(targetStockId, products.map {
            TransactionInputDto.TransactionProductInputDto(
                it.product.id,
                it.actualAmount ?: 0.0
            )
        })

        approvedTransaction.toOutputDto()
    }

    fun updateActualSingle(transactionId: Int, productDiff: TransactionInputDto.TransactionProductInputDto) = transaction {
        val transactionDao = TransactionDao[transactionId]
        if (transactionDao.typeId != AppConf.requestTypes.income || transactionDao.statusId != AppConf.requestStatus.created)
            throw ForbiddenException()

        TransactionToProductModel.update({ (TransactionToProductModel.transaction eq transactionId) and (TransactionToProductModel.product eq productDiff.productId)}) {
            it[actualAmount] = productDiff.amount
        }
        mapOf(
            "success" to true
        )
    }
}