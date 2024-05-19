package siberia

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import siberia.conf.AppConf
import siberia.conf.DatabaseInitializer
import siberia.modules.auth.controller.AuthController
import siberia.modules.auth.data.models.UserLoginModel
import siberia.modules.auth.service.AuthQrService
import siberia.modules.rbac.data.models.role.RoleModel
import siberia.modules.rbac.data.models.rule.RuleCategoryModel
import siberia.modules.rbac.data.models.rule.RuleModel
import siberia.modules.auth.service.AuthService
import siberia.modules.auth.service.AuthSocketService
import siberia.modules.brand.controller.BrandController
import siberia.modules.brand.data.models.BrandModel
import siberia.modules.brand.service.BrandEventService
import siberia.modules.brand.service.BrandService
import siberia.modules.bug.controller.BugReportController
import siberia.modules.bug.data.models.BugReportModel
import siberia.modules.bug.service.BugReportService
import siberia.modules.category.controller.CategoryController
import siberia.modules.category.data.models.CategoryModel
import siberia.modules.category.data.models.CategoryToCategoryModel
import siberia.modules.category.service.CategoryEventService
import siberia.modules.category.service.CategoryService
import siberia.modules.collection.controller.CollectionController
import siberia.modules.collection.data.models.CollectionModel
import siberia.modules.collection.service.CollectionEventService
import siberia.modules.collection.service.CollectionService
import siberia.modules.files.controller.FilesController
import siberia.modules.files.service.FilesService
import siberia.modules.gallery.controller.GalleryController
import siberia.modules.gallery.service.GalleryService
import siberia.modules.gallery.data.models.GalleryModel
import siberia.modules.logger.controller.SystemEventController
import siberia.modules.logger.data.models.SystemEventModel
import siberia.modules.logger.data.models.SystemEventObjectTypeModel
import siberia.modules.logger.data.models.SystemEventTypeModel
import siberia.modules.logger.service.SystemEventService
import siberia.modules.product.controller.ProductController
import siberia.modules.product.controller.ProductGroupController
import siberia.modules.product.data.models.ProductGroupModel
import siberia.modules.product.data.models.ProductModel
import siberia.modules.product.data.models.ProductToGroupModel
import siberia.modules.product.data.models.ProductToImageModel
import siberia.modules.product.service.*
import siberia.modules.stock.data.models.StockModel
import siberia.modules.stock.data.models.StockToProductModel
import siberia.modules.rbac.controller.RbacController
import siberia.modules.rbac.data.models.RbacModel
import siberia.modules.user.controller.UserController
import siberia.modules.user.data.models.UserModel
import siberia.modules.rbac.service.RbacService
import siberia.modules.rbac.service.RoleEventService
import siberia.modules.rbac.service.RoleRulesEventService
import siberia.modules.stock.controller.StockController
import siberia.modules.stock.service.StockEventService
import siberia.modules.stock.service.StockService
import siberia.modules.transaction.controller.*
import siberia.modules.transaction.data.models.*
import siberia.modules.transaction.service.*
import siberia.modules.user.service.*
import siberia.plugins.*
import siberia.utils.database.DatabaseConnector
import siberia.utils.kodein.bindSingleton
import siberia.utils.kodein.kodeinApplication
import siberia.utils.websockets.WebSocketRegister

fun main() {
    embeddedServer(Netty, port = AppConf.server.port, host = AppConf.server.host, module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSecurity()
    configureCORS()
    configureMonitoring()
    configureSerialization()
    configureSockets()
    configureExceptionFilter()

    kodeinApplication {
        bindSingleton { AuthService(it) }
        bindSingleton { AuthQrService(it) }
        bindSingleton { UserService(it) }
        bindSingleton { UserEventService(it) }
        bindSingleton { UserAccessControlService(it) }
        bindSingleton { UserRulesEventService(it) }
        bindSingleton { UserRolesEventService(it) }
        bindSingleton { UserSocketService(it) }
        bindSingleton { RbacService(it) }
        bindSingleton { RoleEventService(it) }
        bindSingleton { RoleRulesEventService(it) }
        bindSingleton { SystemEventService(it) }
        bindSingleton { BrandService(it) }
        bindSingleton { BrandEventService(it) }
        bindSingleton { CollectionService(it) }
        bindSingleton { CollectionEventService(it) }
        bindSingleton { CategoryService(it) }
        bindSingleton { CategoryEventService(it) }
        bindSingleton { ProductService(it) }
        bindSingleton { ProductEventService(it) }
        bindSingleton { ProductParseService(it) }
        bindSingleton { ProductMassiveEventService(it) }
        bindSingleton { StockService(it) }
        bindSingleton { StockEventService(it) }
        bindSingleton { TransactionService(it) }
        bindSingleton { TransactionSocketService(it) }
        bindSingleton { IncomeTransactionService(it) }
        bindSingleton { OutcomeTransactionService(it) }
        bindSingleton { TransferTransactionService(it) }
        bindSingleton { WriteOffTransactionService(it) }
        bindSingleton { WebSocketRegister(it) }
        bindSingleton { AuthSocketService(it) }
        bindSingleton { ProductGroupService(it) }
        bindSingleton { ProductGroupEventService(it) }
        bindSingleton { BugReportService(it) }
        bindSingleton { GalleryService(it) }
        bindSingleton { FilesService(it) }


        bindSingleton { AuthController(it) }
        bindSingleton { UserController(it) }
        bindSingleton { RbacController(it) }
        bindSingleton { CollectionController(it) }
        bindSingleton { BrandController(it) }
        bindSingleton { CategoryController(it) }
        bindSingleton { SystemEventController(it) }
        bindSingleton { ProductController(it) }
        bindSingleton { StockController(it) }
        bindSingleton { TransactionController(it) }
        bindSingleton { IncomeTransactionController(it) }
        bindSingleton { OutcomeTransactionController(it) }
        bindSingleton { TransferTransactionController(it) }
        bindSingleton { WriteOffTransactionController(it) }
        bindSingleton { FilesController(it) }
        bindSingleton { ProductGroupController(it) }
        bindSingleton { BugReportController(it) }
        bindSingleton { GalleryController(it) }
    }

    DatabaseConnector(
        UserModel, UserLoginModel,
        RbacModel, RoleModel, RuleModel, RuleCategoryModel,
        StockModel, StockToProductModel,
        BrandModel, CollectionModel,
        CategoryModel, CategoryToCategoryModel,
        ProductModel, ProductToImageModel,
        ProductGroupModel, ProductToGroupModel,
        SystemEventModel, SystemEventTypeModel, SystemEventObjectTypeModel,
        TransactionModel, TransactionToProductModel, TransactionRelatedUserModel, TransactionStatusModel, TransactionTypeModel,
        BugReportModel, GalleryModel,
    ) {
        DatabaseInitializer.initRules()
        DatabaseInitializer.initEventTypes()
        DatabaseInitializer.initObjectTypes()
        DatabaseInitializer.initRequestTypes()
        DatabaseInitializer.initRequestStatuses()
        DatabaseInitializer.initUsers()
        DatabaseInitializer.initCategory()
//        DatabaseInitializer.initTestData()
        commit()
    }
}
