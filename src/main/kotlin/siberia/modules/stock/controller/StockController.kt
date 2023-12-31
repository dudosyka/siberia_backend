package siberia.modules.stock.controller

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.modules.stock.data.dto.StockCreateDto
import siberia.modules.stock.data.dto.StockSearchDto
import siberia.modules.stock.data.dto.StockUpdateDto
import siberia.modules.stock.service.StockService
import siberia.utils.kodein.KodeinController

class StockController(override val di: DI) : KodeinController() {
    private val stockService: StockService by instance()
    /**
     * Method that subtypes must override to register the handled [Routing] routes.
     */
    override fun Routing.registerRoutes() {
        route("stock") {
            authenticate ("default") {
                post("all") {
                    val stockSearchDto = call.receive<StockSearchDto>()
                    val authorizedUser = call.getAuthorized()

                    call.respond(stockService.getAvailableByFilter(authorizedUser, stockSearchDto))
                }
                get("{stockId}") {
                    val stockId = call.parameters.getInt("stockId", "Stock id must be INT")
                    val authorizedUser = call.getAuthorized()

                    call.respond(stockService.getOne(authorizedUser, stockId))
                }
            }
            authenticate ("stock-managing") {
                post {
                    val stockCreateDto = call.receive<StockCreateDto>()
                    val authorizedUser = call.getAuthorized()

                    call.respond(stockService.create(authorizedUser, stockCreateDto))
                }
                route("{stockId}") {
                    delete {
                        val authorizedUser = call.getAuthorized()
                        val stockId = call.parameters.getInt("stockId", "Stock id must be INT")

                        call.respond(stockService.remove(authorizedUser, stockId))
                    }
                    patch {
                        val authorizedUser = call.getAuthorized()
                        val stockUpdateDto = call.receive<StockUpdateDto>()
                        val stockId = call.parameters.getInt("stockId", "Stock id must be INT")

                        call.respond(stockService.update(authorizedUser, stockId, stockUpdateDto))
                    }
                }
            }
        }
    }
}