package siberia.modules.user.controller

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.exceptions.BadRequestException
import siberia.modules.auth.data.dto.LinkedRuleInputDto
import siberia.modules.auth.data.dto.RoleInputDto
import siberia.modules.user.service.RbacService
import siberia.utils.kodein.KodeinController

class RbacController(override val di: DI) : KodeinController() {
    private val rbacService: RbacService by instance()
    /**
     * Method that subtypes must override to register the handled [Routing] routes.
     */
    override fun Routing.registerRoutes() {
        route("rbac") {
            route("roles") {
                get {
                    call.respond(rbacService.getAllRoles())
                }
                post {
                    val roleInputDto = call.receive<RoleInputDto>()
                    call.respond(rbacService.createRole(roleInputDto))
                }
                route("{roleId}") {
                    get {
                        val roleId =
                            call.parameters["roleId"]?.toInt() ?: throw BadRequestException("Role id must be INT")
                        call.respond(rbacService.getRole(roleId))
                    }
                    post("append/rules") {
                        val onAppend = call.receive<List<LinkedRuleInputDto>>()
                        val roleId =
                            call.parameters["roleId"]?.toInt() ?: throw BadRequestException("Role id must be INT")
                        call.respond(rbacService.appendRulesToRole(roleId, onAppend))
                    }
                    delete("remove/rules") {
                        val onRemove = call.receive<List<LinkedRuleInputDto>>()
                        val roleId =
                            call.parameters["roleId"]?.toInt() ?: throw BadRequestException("Role id must be INT")

                        call.respond(rbacService.removeRulesFromRole(roleId, onRemove))
                    }
                    patch {
                        val roleInputDto = call.receive<RoleInputDto>()
                        val roleId =
                            call.parameters["roleId"]?.toInt() ?: throw BadRequestException("Role id must be INT")
                        call.respond(rbacService.updateRole(roleId, roleInputDto))
                    }
                }
            }
            route("rules") {
                route("categories") {
                    get {
                        call.respond(rbacService.getAllCategories())
                    }
                }
                get {
                    call.respond(rbacService.getAllRules())
                }
            }
        }
    }
}