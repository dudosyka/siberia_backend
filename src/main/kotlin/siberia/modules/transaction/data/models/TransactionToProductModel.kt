package siberia.modules.transaction.data.models

import org.jetbrains.exposed.sql.ReferenceOption
import siberia.modules.product.data.models.ProductModel
import siberia.utils.database.BaseIntIdTable

object TransactionToProductModel : BaseIntIdTable() {
    val product = reference("product", ProductModel, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val transaction = reference("transaction", TransactionModel, ReferenceOption.CASCADE, ReferenceOption.CASCADE)
    val amount = double("amount")
    val actualAmount = double("actual_amount").nullable().default(null)
    val price = double("price").nullable().default(null)
}