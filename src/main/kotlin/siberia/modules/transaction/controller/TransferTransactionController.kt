package siberia.modules.transaction.controller

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.conf.AppConf
import siberia.exceptions.BadRequestException
import siberia.modules.transaction.data.dto.TransactionInputDto
import siberia.modules.transaction.service.TransferTransactionService
import siberia.utils.kodein.KodeinController

class TransferTransactionController(override val di: DI) : KodeinController() {
    private val transferTransactionService: TransferTransactionService by instance()
    /**
     * Method that subtypes must override to register the handled [Routing] routes.
     */
    override fun Routing.registerRoutes() {
        route("transaction/transfer") {
            authenticate ("create-transfer-request") {
                post {
                    val transactionInputDto = call.receive<TransactionInputDto>()
                    val authorizedUser = call.getAuthorized()

                    call.respond(transferTransactionService.create(authorizedUser, transactionInputDto))
                }
            }
            authenticate ("default") {
                route("{transactionId}") {
                    patch("{statusId}") {
                        val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                        val statusId = call.parameters.getInt("statusId", "Status id must be INT")
                        val authorizedUser = call.getAuthorized()

                        when (statusId) {
                            AppConf.requestStatus.open ->
                                call.respond(transferTransactionService.approveCreation(authorizedUser, transactionId))
                            AppConf.requestStatus.creationCancelled ->
                                call.respond(transferTransactionService.cancelCreation(authorizedUser, transactionId))
                            AppConf.requestStatus.processingCancelled ->
                                call.respond(transferTransactionService.cancelProcess(authorizedUser, transactionId))
                            AppConf.requestStatus.delivered ->
                                call.respond(transferTransactionService.delivered(authorizedUser, transactionId))
                            AppConf.requestStatus.notDelivered ->
                                call.respond(transferTransactionService.notDelivered(authorizedUser, transactionId))
                            else -> throw BadRequestException("Bad status provided")
                        }
                    }
                    patch ("partial") {
                        val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                        val authorizedUser = call.getAuthorized()
                        val deliveredProductsList = call.receive<List<Int>>()

                        call.respond(transferTransactionService.partialDelivered(authorizedUser, transactionId, deliveredProductsList))
                    }
                    patch ("partially") {
                        val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                        val authorizedUser = call.getAuthorized()
                        val deliveredProductsList = call.receive<List<TransactionInputDto.TransactionProductInputDto>>()

                        call.respond(transferTransactionService.partialDeliveredByDiff(authorizedUser, transactionId, deliveredProductsList))
                    }
                    get("${AppConf.requestStatus.inProgress}/{stockId}") {
                        val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                        val stockId = call.parameters.getInt("stockId", "Stock id must be INT")

                        val authorizedUser = call.getAuthorized()

                        call.respond(transferTransactionService.checkAccessToProcessTransfer(authorizedUser, transactionId, stockId))
                    }
                    patch("${AppConf.requestStatus.inProgress}/{stockId}") {
                        val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                        val stockId = call.parameters.getInt("stockId", "Stock id must be INT")

                        val authorizedUser = call.getAuthorized()

                        call.respond(transferTransactionService.startProcess(authorizedUser, transactionId, stockId))
                    }
                    authenticate ("solve-not-delivered-problem") {
                        patch("solve/{statusId}") {
                            val transactionId = call.parameters.getInt("transactionId", "Transaction id must be INT")
                            val statusId = call.parameters.getInt("statusId", "Status id must be INT")
                            val authorizedUser = call.getAuthorized()

                            val availableToSolve = listOf(
                                AppConf.requestStatus.delivered, AppConf.requestStatus.deliveryCancelled, AppConf.requestStatus.failed
                            )

                            if (availableToSolve.contains(statusId))
                                call.respond(transferTransactionService.solveNotDelivered(authorizedUser, transactionId, statusId))
                            else
                                throw BadRequestException("Bad status provided")
                        }
                    }
                }
            }
        }
    }
}