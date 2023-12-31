package siberia.modules.rbac.data.dao

import org.jetbrains.exposed.dao.id.EntityID
import siberia.modules.rbac.data.dto.RuleOutputDto
import siberia.modules.rbac.data.models.rule.RuleModel
import siberia.utils.database.BaseIntEntity
import siberia.utils.database.BaseIntEntityClass
import siberia.utils.database.idValue

class RuleDao(id: EntityID<Int>): BaseIntEntity<RuleOutputDto>(id, RuleModel) {

    companion object: BaseIntEntityClass<RuleOutputDto, RuleDao>(RuleModel)

    val name by RuleModel.name
    val description by RuleModel.description

    private val _categoryId by RuleModel.category
    val category_id: Int?
        get() = _categoryId?.value
    val category by RuleCategoryDao optionalReferencedOn RuleModel.category
    val needStock by RuleModel.needStock

    override fun toOutputDto(): RuleOutputDto
        = RuleOutputDto(idValue, name, description, needStock, category?.toOutputDto())
}