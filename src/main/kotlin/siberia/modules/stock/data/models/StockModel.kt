package siberia.modules.stock.data.models

import io.ktor.server.plugins.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import siberia.modules.transaction.data.dto.TransactionInputDto
import siberia.utils.database.BaseIntIdTable
import siberia.utils.database.transaction

object StockModel: BaseIntIdTable() {
    val name = text("name")
    val address = text("address")

    private fun getProductsMapped(products: List<TransactionInputDto.TransactionProductInputDto>): Pair<MutableMap<Int, Double>, List<Int>> =
        Pair(products.associate { Pair(it.productId, it.amount) }.toMutableMap(), products.map { it.productId })

    fun appendProducts(stockId: Int, products: List<TransactionInputDto.TransactionProductInputDto>) = transaction {
        val productsMapped = getProductsMapped(products)
        val exist = StockToProductModel.select {
            (StockToProductModel.product inList productsMapped.second) and (StockToProductModel.stock eq stockId)
        }.map { it }

        StockToProductModel.batchInsert(exist) {
            this[StockToProductModel.product] = it[StockToProductModel.product]
            this[StockToProductModel.stock] = it[StockToProductModel.stock]
            this[StockToProductModel.amount] = (it[StockToProductModel.amount] + (productsMapped.first[it[StockToProductModel.product].value] ?: 0.0))
            productsMapped.first.remove(it[StockToProductModel.product].value)
        }

        StockToProductModel.deleteWhere {
            StockToProductModel.id inList exist.map { it[StockToProductModel.id] }
        }

        StockToProductModel.batchInsert(productsMapped.first.map { Pair(it.key, it.value) }) {
            this[StockToProductModel.stock] = stockId
            this[StockToProductModel.product] = it.first
            this[StockToProductModel.amount] = it.second
        }
    }

    fun checkAvailableAmount(stockId: Int, products: List<TransactionInputDto.TransactionProductInputDto>) = transaction {
        val productsMapped = getProductsMapped(products).first
        val exist = StockToProductModel.select {
            (StockToProductModel.product inList products.map { it.productId }) and (StockToProductModel.stock eq stockId)
        }.map {
            if (it[StockToProductModel.amount] - (productsMapped[it[StockToProductModel.product].value] ?: 0.0) <= 0.0)
                throw Exception("Not enough")
            it
        }

        if (exist.size < products.size)
            throw Exception("Not enough")
    }

    fun removeProducts(stockId: Int, products: List<TransactionInputDto.TransactionProductInputDto>) = transaction {
        val productsMapped = getProductsMapped(products)
        val exist = StockToProductModel.select {
            (StockToProductModel.product inList productsMapped.second) and (StockToProductModel.stock eq stockId)
        }.map { it }


        StockToProductModel.deleteWhere {
            StockToProductModel.id inList exist.map { it[StockToProductModel.id] }
        }

        StockToProductModel.batchInsert(exist) {
            this[StockToProductModel.product] = it[StockToProductModel.product]
            this[StockToProductModel.stock] = it[StockToProductModel.stock]
            val resultAmount = (it[StockToProductModel.amount] - (productsMapped.first[it[StockToProductModel.product].value] ?: 0.0))

            if (resultAmount < 0)
                throw BadRequestException("Not enough products in stock")

            this[StockToProductModel.amount] = resultAmount
        }
    }
}