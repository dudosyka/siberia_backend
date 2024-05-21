package siberia.modules.transaction.data.dao

import org.jetbrains.exposed.dao.id.EntityID
import siberia.modules.product.data.dao.ProductDao
import siberia.modules.stock.data.dao.StockDao
import siberia.modules.transaction.data.dto.TransactionFullOutputDto
import siberia.modules.transaction.data.dto.TransactionInputDto
import siberia.modules.transaction.data.dto.TransactionListItemOutputDto
import siberia.modules.transaction.data.dto.TransactionOutputDto
import siberia.modules.transaction.data.models.TransactionModel
import siberia.modules.transaction.data.models.TransactionToProductModel
import siberia.utils.database.BaseIntEntity
import siberia.utils.database.BaseIntEntityClass
import siberia.utils.database.idValue

class TransactionDao(id: EntityID<Int>) : BaseIntEntity<TransactionOutputDto>(id, TransactionModel) {
    companion object : BaseIntEntityClass<TransactionOutputDto, TransactionDao>(TransactionModel)

    private val _fromId by TransactionModel.from
    val fromId: Int? get() = _fromId?.value
    var from by StockDao optionalReferencedOn TransactionModel.from

    private val _toId by TransactionModel.to
    val toId: Int? get() = _toId?.value
    val to by StockDao optionalReferencedOn TransactionModel.to

    private val _statusId by TransactionModel.status
    val statusId: Int get() = _statusId.value
    var status by TransactionStatusDao referencedOn TransactionModel.status

    private val _typeId by TransactionModel.type
    val typeId: Int get() = _typeId.value
    val type by TransactionTypeDao referencedOn TransactionModel.type

    var hidden by TransactionModel.hidden
    var isPaid by TransactionModel.isPaid
    var arrivalDate by TransactionModel.arrivalDate

    val products by ProductDao via TransactionToProductModel

    private val createdAtString: String get() = createdAt.toString()
    private val arrivalDateString: String? get() = arrivalDate?.toString()

    override fun toOutputDto(): TransactionOutputDto =
        TransactionOutputDto(idValue, fromId, toId, statusId, typeId, isPaid, createdAtString, arrivalDateString)

    val inputProductsList get(): List<TransactionInputDto.TransactionProductInputDto> =
        TransactionModel.getFullProductList(idValue).map {
            TransactionInputDto.TransactionProductInputDto(
                it.product.id,
                it.amount
            )
        }


    val listItemOutputDto: TransactionListItemOutputDto get() =
        TransactionListItemOutputDto(
            idValue, fromId, from?.name,
            toId, to?.name,
            status.toOutputDto(),
            type.toOutputDto(),
            isPaid,
            createdAtString,
            arrivalDateString
        )

    fun fullOutput(): TransactionFullOutputDto =
        TransactionFullOutputDto(
            idValue, from?.toOutputDto(),
            to?.toOutputDto(),
            status.toOutputDto(),
            type.toOutputDto(),
            TransactionModel.getFullProductList(idValue),
            isPaid,
            arrivalDateString,
            createdAtString
        )

}