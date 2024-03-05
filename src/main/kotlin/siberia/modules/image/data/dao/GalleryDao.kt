package siberia.modules.image.data.dao

import org.jetbrains.exposed.dao.id.EntityID
import siberia.modules.image.data.dto.ImageOutputDto
import siberia.modules.image.data.models.GalleryModel
import siberia.modules.user.data.dao.UserDao

import siberia.utils.database.BaseIntEntity
import siberia.utils.database.BaseIntEntityClass
import siberia.utils.database.idValue

class GalleryDao(id: EntityID<Int>): BaseIntEntity<ImageOutputDto>(id, GalleryModel) {

    companion object: BaseIntEntityClass<ImageOutputDto, GalleryDao>(GalleryModel)

    var photo by GalleryModel.url
    var name by GalleryModel.name
    var description by GalleryModel.description

    var author by UserDao optionalReferencedOn GalleryModel.authorId
    override fun toOutputDto(): ImageOutputDto =
        ImageOutputDto(
            idValue,
            name,
            photo,
            author?.login,
            description
        )
}