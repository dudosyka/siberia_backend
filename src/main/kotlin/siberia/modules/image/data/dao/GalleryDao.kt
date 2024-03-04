package siberia.modules.image.data.dao

import org.jetbrains.exposed.dao.id.EntityID
import siberia.modules.image.data.dto.ImageOutputDto
import siberia.modules.image.data.models.GalleryModel

import siberia.utils.database.BaseIntEntity
import siberia.utils.database.BaseIntEntityClass
import siberia.utils.database.idValue

class GalleryDao(id: EntityID<Int>): BaseIntEntity<ImageOutputDto>(id, GalleryModel) {

    companion object: BaseIntEntityClass<ImageOutputDto, GalleryDao>(GalleryModel)

    var photo by GalleryModel.photo
    var name by GalleryModel.name
    var authorId by GalleryModel.authorId
    var description by GalleryModel.description
    override fun toOutputDto(): ImageOutputDto =
        ImageOutputDto(
            idValue,
            name,
            photo,
            authorId,
            description
        )
}