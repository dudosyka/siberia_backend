package siberia.conf

data class RulesConf (
    val userManaging: Int,
    val rbacManaging: Int,
    val checkLogs: Int,
    val brandManaging: Int,
    val collectionManaging: Int,
    val categoryManaging: Int,
    val productsManaging: Int,
    val viewProductsList: Int,
    val stockManaging: Int,

    val concreteStockView: Int,

    val createWriteOffRequest: Int,
    val approveWriteOffRequest: Int,

    val createIncomeRequest: Int,
    val approveIncomeRequest: Int,

    val createOutcomeRequest: Int,
    val approveOutcomeRequest: Int,

    val createTransferRequest: Int,
    val approveTransferRequestCreation: Int,
    val manageTransferRequest: Int,
    val approveTransferDelivery: Int,
    val solveNotDeliveredProblem: Int,

    //Specific rules for mobile app authorization
    //ONLY for token verifying
    val mobileAuth: Int,
    val mobileAccess: Int
)