package gc.david.dfm.executor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Interactor to use with coroutines.
 */
abstract class CoInteractor<out Type, in Params> where Type : Any {

    abstract suspend fun run(params: Params): Either<Failure, Type>

    open operator fun invoke(
            scope: CoroutineScope,
            params: Params,
            onResult: (Either<Failure, Type>) -> Unit = {}
    ) {
        val job = scope.async { run(params) }
        scope.launch { onResult(job.await()) }
    }

    class None
}
