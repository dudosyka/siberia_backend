package siberia.modules.collection.controller

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.modules.collection.data.dto.CollectionInputDto
import siberia.modules.collection.data.dto.CollectionUpdateDto
import siberia.modules.collection.service.CollectionEventService
import siberia.modules.collection.service.CollectionService
import siberia.utils.kodein.KodeinController

class CollectionController(override val di: DI) : KodeinController() {
    private val collectionService: CollectionService by instance()
    private val collectionEventService: CollectionEventService by instance()
    /**
     * Method that subtypes must override to register the handled [Routing] routes.
     */
    override fun Routing.registerRoutes() {
        route("collection") {
            authenticate ("collection-managing") {
                post {
                    val collectionInputDto = call.receive<CollectionInputDto>()
                    val authorizedUser = call.getAuthorized()

                    call.respond(collectionService.create(authorizedUser, collectionInputDto))
                }
                post("rollback/{eventId}") {
                    val authorizedUser = call.getAuthorized()
                    val eventId = call.parameters.getInt("eventId", "Event id must be INT")

                    call.respond(collectionEventService.rollback(authorizedUser, eventId))
                }
                route("{collectionId}") {
                    patch {
                        val collectionUpdateDto = call.receive<CollectionUpdateDto>()
                        val authorizedUser = call.getAuthorized()
                        val collectionId = call.parameters.getInt("collectionId", "Collection id must be INT")

                        call.respond(collectionService.update(authorizedUser, collectionId, collectionUpdateDto))
                    }

                    delete {
                        val authorizedUser = call.getAuthorized()
                        val collectionId = call.parameters.getInt("collectionId", "Collection id must be INT")

                        call.respond(collectionService.remove(authorizedUser, collectionId))
                    }
                }
            }
            authenticate("default") {
                get {
                    call.respond(collectionService.getAll())
                }

                get("{collectionId}") {
                    val collectionId = call.parameters.getInt("collectionId", "Collection id must be INT")

                    call.respond(collectionService.getOne(collectionId))
                }
            }
        }
    }
}