package siberia.modules.transaction.data.models

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.date
import siberia.conf.AppConf
import siberia.modules.product.data.dao.ProductDao
import siberia.modules.product.data.models.ProductModel
import siberia.modules.stock.data.models.StockModel
import siberia.modules.transaction.data.dao.TransactionDao
import siberia.modules.transaction.data.dto.TransactionFullOutputDto
import siberia.modules.transaction.data.dto.TransactionInputDto
import siberia.utils.database.BaseIntIdTable
import siberia.utils.database.idValue
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

object TransactionModel : BaseIntIdTable() {
    val from = reference("from", StockModel, ReferenceOption.CASCADE, ReferenceOption.CASCADE).nullable().default(null)
    val to = reference("to", StockModel, ReferenceOption.CASCADE, ReferenceOption.CASCADE).nullable().default(null)
    val status = reference("status", TransactionStatusModel, ReferenceOption.RESTRICT, ReferenceOption.RESTRICT)
    val type = reference("type", TransactionTypeModel, ReferenceOption.RESTRICT, ReferenceOption.RESTRICT)
    val hidden = bool("hidden")
    val isPaid = bool("is_paid").nullable().default(null)
    val arrivalDate = date("arrival_date").nullable().default(null)

    fun create(transactionInputDto: TransactionInputDto): TransactionDao = transaction {
        val arrivalDateObject = if (transactionInputDto.arrivalDate != null) {
            val year = transactionInputDto.arrivalDate / 10000
            val month = (transactionInputDto.arrivalDate - year * 10000) / 100
            val day = transactionInputDto.arrivalDate - year * 10000 - month * 100
            LocalDate.of(year.toInt(), month.toInt(), day.toInt())
        } else null

        val createdTransaction = TransactionDao.wrapRow(TransactionModel.insert {
            it[to] = transactionInputDto.to
            it[from] = transactionInputDto.from
            it[status] = AppConf.requestStatus.created
            it[type] = transactionInputDto.type
            it[hidden] = transactionInputDto.hidden
            it[isPaid] = transactionInputDto.isPaid
            it[arrivalDate] = arrivalDateObject
        }.resultedValues!!.first())

        TransactionToProductModel.batchInsert(transactionInputDto.products) {
            this[TransactionToProductModel.transaction] = createdTransaction.idValue
            this[TransactionToProductModel.product] = it.productId
            this[TransactionToProductModel.amount] = it.amount
            this[TransactionToProductModel.price] = it.price
        }

        createdTransaction
    }

    fun clearProductsList(transactionId: Int): List<TransactionFullOutputDto.TransactionProductDto> = transaction {
        val products = getFullProductList(transactionId)
        TransactionToProductModel.deleteWhere { transaction eq transactionId }
        products
    }

    fun addProductList(transactionId: Int, products: List<TransactionInputDto.TransactionProductInputDto>) = transaction {
        TransactionDao[transactionId]
        TransactionToProductModel.batchInsert(products) {
            this[TransactionToProductModel.transaction] = transactionId
            this[TransactionToProductModel.product] = it.productId
            this[TransactionToProductModel.amount] = it.amount
            this[TransactionToProductModel.price] = it.price
        }
    }


    fun getFullProductList(transactionId: Int): List<TransactionFullOutputDto.TransactionProductDto> = transaction {
        val slice = ProductModel.columns.toMutableList()
        slice.add(TransactionToProductModel.amount)
        slice.add(TransactionToProductModel.actualAmount)
        slice.add(TransactionToProductModel.price)
        TransactionToProductModel
            .leftJoin(ProductModel)
            .slice(slice)
            .select {
                TransactionToProductModel.transaction eq transactionId
            }
            .map {
                val productDao = ProductDao.wrapRow(it)
                TransactionFullOutputDto.TransactionProductDto(productDao.toOutputDto(), it[TransactionToProductModel.amount], it[TransactionToProductModel.actualAmount], it[TransactionToProductModel.price])
            }
    }

    fun updateAndSetActualProducts(transactionId: Int, productsDiff: List<TransactionInputDto.TransactionProductInputDto>): List<TransactionFullOutputDto.TransactionProductDto> = transaction {
        val productList = clearProductsList(transactionId)
        val diffIdToAmount = mutableMapOf<Int, Double>()
        productsDiff.forEach {
            if (diffIdToAmount.containsKey(it.productId))
                diffIdToAmount[it.productId] = diffIdToAmount[it.productId]!! + it.amount
            else
                diffIdToAmount[it.productId] = it.amount
        }

        val actualList = productList.map {
            if (diffIdToAmount.containsKey(it.product.id))
                it.actualAmount = (diffIdToAmount[it.product.id] ?: (it.actualAmount ?: 0.0))
            else if (it.actualAmount == null)
                it.actualAmount = it.amount

            it
        }

        TransactionToProductModel.batchInsert(actualList) {
            this[TransactionToProductModel.transaction] = transactionId
            this[TransactionToProductModel.product] = it.product.id
            this[TransactionToProductModel.amount] = it.amount
            this[TransactionToProductModel.actualAmount] = it.actualAmount
            this[TransactionToProductModel.price] = it.price
        }


        actualList
    }
}