package siberia.modules.auth.service

import io.ktor.util.date.*
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.kodein.di.DI
import org.kodein.di.instance
import siberia.conf.AppConf
import siberia.exceptions.ForbiddenException
import siberia.exceptions.UnauthorizedException
import siberia.modules.auth.data.dto.*
import siberia.modules.rbac.data.models.RbacModel
import siberia.modules.stock.service.StockService
import siberia.modules.transaction.data.dao.TransactionDao
import siberia.modules.user.data.dao.UserDao
import siberia.modules.user.data.dto.UserOutputDto
import siberia.modules.user.data.models.UserModel
import siberia.modules.user.service.UserAccessControlService
import siberia.modules.user.service.UserService
import siberia.plugins.Logger
import siberia.utils.kodein.KodeinService
import siberia.utils.security.bcrypt.CryptoUtil
import siberia.utils.security.jwt.JwtUtil

class AuthService(override val di: DI) : KodeinService(di) {
    private val userService: UserService by instance()
    private val userAccessControlService: UserAccessControlService by instance()
    private val stockService: StockService by instance()
    private val authQrService: AuthQrService by instance()
    private fun generateTokenPair(userDao: UserDao, refreshTime: Long): TokenOutputDto {
        val accessToken = JwtUtil.createToken(userDao)
        val refreshToken = JwtUtil.createToken(userDao, lastLogin = refreshTime)

        return TokenOutputDto(accessToken, refreshToken)
    }

    fun refreshUser(refreshTokenDto: RefreshTokenDto): TokenOutputDto = transaction {
        try {
            UserModel.select { UserModel.id eq refreshTokenDto.id }.map {
                Logger.debug("Log from array", "main")
                Logger.debug(it[UserModel.lastLogin], "main")
            }
            val users = UserDao.wrapRows(UserModel.select { UserModel.id eq refreshTokenDto.id })
            if (users.empty()) throw ForbiddenException()
            val userDao = users.first()
            UserDao.removeFromCache(userDao)
            Logger.debug(refreshTokenDto, "main")
            Logger.debug(userDao.toOutputDto(), "main")
            Logger.debug(userDao.id, "main")
            Logger.debug(userDao.lastLogin, "main")

            if (userDao.lastLogin != refreshTokenDto.lastLogin)
                throw ForbiddenException()
            val lastLogin = getTimeMillis()
            userDao.lastLogin = lastLogin
            userDao.flush()
            commit()
            Logger.debug(userDao.lastLogin, "main")
            Logger.debug(lastLogin, "main")

            generateTokenPair(userDao, lastLogin)
        } catch (e: Exception) {
            Logger.debugException("Exception during refresh", e, "main")
            throw ForbiddenException()
        }
    }

    fun auth(authInputDto: AuthInputDto): TokenOutputDto = transaction {
        val search = UserDao.find {
            UserModel.login eq authInputDto.login
        }
        val userDao = if (search.empty())
            throw UnauthorizedException()
        else
            search.first()

        if (!CryptoUtil.compare(authInputDto.password, userDao.hash))
            throw ForbiddenException()

        val lastLogin = getTimeMillis()
        userDao.lastLogin = lastLogin
        userDao.flush()
        commit()

        generateTokenPair(userDao, lastLogin)
    }

    fun getAuthorized(authorizedUser: AuthorizedUser): UserOutputDto {
        val userDto = userService.getOne(authorizedUser.id)
        userDto.rules = RbacModel.userToRuleLinks(
            userDto.id, expanded = true
        )
        return userDto
    }

    fun getAuthenticatedStockData(authorizedUser: AuthorizedUser): AuthenticatedStockOutputDto = transaction {
        val targetStockId = authorizedUser.stockId ?: throw ForbiddenException()
        val stockData = stockService.getByAuthorizedUser(authorizedUser)
        val operationAccessData = MobileOperationAccessDto(
            arrivalsManaging = userAccessControlService.checkAccessToStock(authorizedUser.id, AppConf.rules.createIncomeRequest, targetStockId),
            salesManaging = userAccessControlService.checkAccessToStock(authorizedUser.id, AppConf.rules.createOutcomeRequest, targetStockId),
            transfersManaging = userAccessControlService.checkAccessToStock(authorizedUser.id, AppConf.rules.createTransferRequest, targetStockId),
        )


        AuthenticatedStockOutputDto(
            stockData = stockData,
            operationsAccess = operationAccessData,
            type = authQrService.getMobileTokenType(authorizedUser),
            transactionData = with(authorizedUser.transactionId) {
                if (this != null)
                    TransactionDao[this].fullOutput()
                else
                    null
            }
        )
    }
}