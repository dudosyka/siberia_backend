package siberia.conf

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.*

object AppConf {
    private val mainConfig: ApplicationConfig = HoconApplicationConfig(ConfigFactory.load().getConfig("application"))
    private val jwtConfig: ApplicationConfig = mainConfig.config("jwt")
    private val databaseConfig: ApplicationConfig = mainConfig.config("database")
    private val serverConfig: ApplicationConfig = mainConfig.config("server")
    private val rulesConf: ApplicationConfig = mainConfig.config("rules")
    private val eventTypesConf: ApplicationConfig = mainConfig.config("eventTypes")
    private val objectTypesConf: ApplicationConfig = mainConfig.config("objectTypes")
    private val requestTypeConf: ApplicationConfig = mainConfig.config("requestTypes")
    private val requestStatusConf: ApplicationConfig = mainConfig.config("requestStatuses")

    enum class StockPair {
        FROM, TO,
    }

    private fun ApplicationConfig.getString(name: String): String = this.property(name).getString()
    private fun ApplicationConfig.getInt(name: String): Int = this.getString(name).toInt()

    val zoneOffset: Int = serverConfig.getInt("zoneOffset")

    val isDebug: Boolean = mainConfig.getString("debug") == "true"

    val jwt = JwtConf(
        domain = jwtConfig.getString("domain"),
        secret = jwtConfig.getString("secret"),
        expirationTime = jwtConfig.config("expiration").getInt("seconds"),
        refreshExpirationTime = jwtConfig.config("refreshExpiration").getInt("seconds"),
        mobileExpirationTime = jwtConfig.config("mobile").config("accessExpiration").getInt("seconds"),
        mobileAuthExpirationTime = jwtConfig.config("mobile").config("authExpiration").getInt("seconds")
    )

    val database = DatabaseConf(
        url = databaseConfig.getString("url"),
        driver = databaseConfig.getString("driver"),
        user = databaseConfig.getString("user"),
        password = databaseConfig.getString("password")
    )

    val server = ServerConf(
        host = serverConfig.getString("host"),
        port = serverConfig.getInt("port"),
        fileLocation = serverConfig.getString("file-location")
    )

    val rules = RulesConf(
        userManaging = rulesConf.getInt("user-managing"),
        rbacManaging = rulesConf.getInt("rbac-managing"),
        checkLogs = rulesConf.getInt("check-logs"),
        brandManaging = rulesConf.getInt("brand-managing"),
        collectionManaging = rulesConf.getInt("collection-managing"),
        categoryManaging = rulesConf.getInt("category-managing"),
        productsManaging = rulesConf.getInt("products-managing"),
        viewProductsList = rulesConf.getInt("view-products-list"),
        stockManaging = rulesConf.getInt("stock-managing"),

        concreteStockView = rulesConf.getInt("concrete-stock-view"),

        createWriteOffRequest = rulesConf.getInt("create-write-off"),

        createIncomeRequest = rulesConf.getInt("create-income-request"),

        createOutcomeRequest = rulesConf.getInt("create-outcome-request"),

        createTransferRequest = rulesConf.getInt("create-transfer-request"),
        manageTransferRequest = rulesConf.getInt("manage-transfer-request"),
        solveNotDeliveredProblem = rulesConf.getInt("solve-not-delivered-problem"),

        mobileAuth = rulesConf.getInt("mobile-auth"),
        mobileAccess = rulesConf.getInt("mobile-access")
    )

    val eventTypes = EventTypesConf(
        createEvent = eventTypesConf.getInt("create"),
        updateEvent = eventTypesConf.getInt("update"),
        removeEvent = eventTypesConf.getInt("remove")
    )

    val objectTypes = ObjectTypesConf(
        userEvent = objectTypesConf.getInt("user"),
        stockEvent = objectTypesConf.getInt("stock"),
        roleEvent = objectTypesConf.getInt("role"),
        productEvent = objectTypesConf.getInt("product"),
        massiveProductUpdateEvent = objectTypesConf.getInt("massive-product-update"),
        brandEvent = objectTypesConf.getInt("brand"),
        collectionEvent = objectTypesConf.getInt("collection"),
        categoryEvent = objectTypesConf.getInt("category"),
        transactionEvent = objectTypesConf.getInt("transaction"),
        productGroupEvent = objectTypesConf.getInt("product-group"),
        productBulkCreate = objectTypesConf.getInt("product-bulk-create"),
        userRightsEvent = objectTypesConf.getInt("user-rights"),
        galleryEvent = objectTypesConf.getInt("gallery"),
    )

    val requestTypes = RequestTypeConf(
        income = requestTypeConf.getInt("income"),
        outcome = requestTypeConf.getInt("outcome"),
        transfer = requestTypeConf.getInt("transfer"),
        writeOff = requestTypeConf.getInt("write-off")
    )

    val requestStatus = RequestStatusConf(
        open = requestStatusConf.getInt("open"),
        created = requestStatusConf.getInt("created"),
        creationCancelled = requestStatusConf.getInt("creation-cancelled"),
        inProgress = requestStatusConf.getInt("in-progress"),
        processingCancelled = requestStatusConf.getInt("processing-cancelled"),
        delivered = requestStatusConf.getInt("delivered"),
        notDelivered = requestStatusConf.getInt("not-delivered"),
        failed = requestStatusConf.getInt("failed"),
        processed = requestStatusConf.getInt("processed"),
        deliveryCancelled = requestStatusConf.getInt("delivery-cancelled")
    )


    /*

            Maps request type to Map of requestStatus -> available statuses list

            Structure:
            Income -> {
                created -> [ processed, creationCancelled ],
                ...
            }
            Outcome -> {
                created -> [ processed, creationCancelled ],
                ...
            }
            Transfer -> {
                created -> [ creationCancelled, open ],
                ...
                inProgress -> [ notDelivered, delivered, processingCancelled ]
                ...
            }

         */
    val requestStatusMapper = mapOf(
        requestTypes.income to mapOf(
            requestStatus.created to listOf(
                requestStatus.inProgress, requestStatus.processed, requestStatus.creationCancelled
            ),
            requestStatus.inProgress to listOf(
                requestStatus.inProgress, requestStatus.processed
            )
        ),
        requestTypes.outcome to mapOf(
            requestStatus.created to listOf(
                requestStatus.open, requestStatus.creationCancelled
            ),
            requestStatus.open to listOf(
                requestStatus.processed, requestStatus.creationCancelled
            )
        ),
        requestTypes.transfer to mapOf(
            requestStatus.created to listOf(
                requestStatus.creationCancelled, requestStatus.open
            ),
            requestStatus.open to listOf(
                requestStatus.inProgress, requestStatus.creationCancelled
            ),
            requestStatus.inProgress to listOf(
                requestStatus.notDelivered, requestStatus.delivered, requestStatus.processingCancelled
            ),
            requestStatus.notDelivered to listOf(
                requestStatus.failed, requestStatus.deliveryCancelled, requestStatus.delivered
            )
        ),
        requestTypes.writeOff to mapOf(
            requestStatus.created to listOf(
                requestStatus.processed, requestStatus.creationCancelled
            ),
        ),
    )


    /*

        Maps request type to Map of requestStatus -> Which stock is target

        Structure:
        Income -> {
            created -> TO,
            ...
        }
        Outcome -> {
            created -> FROM,
            ...
        }
        Transfer -> {
            created -> TO,
            ...
            inProgress -> FROM
            ...
        }

     */
    val requestToStockMapper = mapOf<Int, Map<Int, StockPair>>(
        requestTypes.income to mapOf(
            requestStatus.created to StockPair.TO,
            requestStatus.inProgress to StockPair.TO,
            requestStatus.processed to StockPair.TO,
            requestStatus.creationCancelled to StockPair.TO,
        ),
        requestTypes.outcome to mapOf(
            requestStatus.created to StockPair.FROM,
            requestStatus.open to StockPair.FROM,
            requestStatus.processed to StockPair.FROM,
            requestStatus.creationCancelled to StockPair.FROM,
        ),
        requestTypes.transfer to mapOf(
            requestStatus.created to StockPair.TO,
            requestStatus.creationCancelled to StockPair.TO,
            requestStatus.open to StockPair.TO,
            requestStatus.inProgress to StockPair.FROM,
            requestStatus.processingCancelled to StockPair.FROM,
            requestStatus.delivered to StockPair.TO,
            requestStatus.notDelivered to StockPair.TO,
            requestStatus.failed to StockPair.TO,
            requestStatus.deliveryCancelled to StockPair.TO,
        ),
        requestTypes.writeOff to mapOf(
            requestStatus.created to StockPair.FROM,
            requestStatus.processed to StockPair.FROM,
            requestStatus.creationCancelled to StockPair.FROM,
        )
    )
}