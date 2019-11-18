package gc.david.dfm.executor

sealed class Failure {

    object NetworkConnection : Failure()

    object ServerError : Failure()

    /** * Extend this class for feature specific failures.*/
    abstract class FeatureFailure: Failure()
}