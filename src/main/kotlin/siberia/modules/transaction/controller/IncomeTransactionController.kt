package siberia.modules.transaction.controller

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.modules.transaction.data.dto.TransactionInputDto
import siberia.modules.transaction.service.IncomeTransactionService
import siberia.utils.kodein.KodeinController

class IncomeTransactionController(override val di: DI) : KodeinController() {
    private val incomeTransactionService: IncomeTransactionService by instance()
    /**
     * Method that subtypes must override to register the handled [Routing] routes.
     */
    override fun Routing.registerRoutes() {
        route("transaction/income") {
            authenticate ("create-income-request") {
                post {
                    val transactionInputDto = call.receive<TransactionInputDto>()
                    val authorizedUser = call.getAuthorized()

                    call.respond(incomeTransactionService.create(authorizedUser, transactionInputDto))
                }
            }
            authenticate ("create-income-request") {
                route("{transactionId}") {
                    patch("actual") {
                        val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                        val productDiff = call.receive<TransactionInputDto.TransactionProductInputDto>()

                        call.respond(incomeTransactionService.updateActualSingle(transactionId, productDiff))
                    }
                    patch("approve") {
                        val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                        val authorizedUser = call.getAuthorized()

                        val productsDiff = call.receive<List<TransactionInputDto.TransactionProductInputDto>>()

                        call.respond(incomeTransactionService.processed(authorizedUser, transactionId, productsDiff))
                    }
                    patch("cancel") {
                        val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                        val authorizedUser = call.getAuthorized()

                        call.respond(incomeTransactionService.cancelCreation(authorizedUser, transactionId))
                    }
                }
            }
        }
    }
}