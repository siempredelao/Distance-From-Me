/*
 * Copyright (c) 2019 David Aguiar Gonzalez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gc.david.dfm.faq

import gc.david.dfm.executor.Executor
import gc.david.dfm.executor.Interactor
import gc.david.dfm.executor.MainThread

/**
 * Created by david on 17.12.16.
 */
class GetFaqsInteractor(
        private val executor: Executor,
        private val mainThread: MainThread,
        private val repository: GetFaqsRepository
) : Interactor, GetFaqsUseCase {

    private lateinit var callback: GetFaqsUseCase.Callback

    override fun execute(callback: GetFaqsUseCase.Callback) {
        this.callback = callback
        this.executor.run(this)
    }

    override fun run() {
        try {
            val faqs = repository.getFaqs()

            mainThread.post(Runnable { callback.onFaqsLoaded(faqs) })
        } catch (exception: Exception) {
            mainThread.post(Runnable { callback.onError() })
        }
    }
}
