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

import gc.david.dfm.executor.Failure
import gc.david.dfm.faq.model.Faq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Created by david on 21.12.16.
 */
class FaqsPresenter(
        private val faqsView: Faqs.View,
        private val getFaqsUseCase: GetFaqsUseCase
) : Faqs.Presenter, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val job = Job()

    init {
        this.faqsView.setPresenter(this)
    }

    override fun start() {
        faqsView.showLoading()

        launch {
            getFaqsUseCase(this, Unit) { it.either(::handleFailure, ::handleSuccess) }
        }
    }

    private fun handleSuccess(faqs: Set<Faq>) {
        with(faqsView) {
            hideLoading()
            setupList()
            add(faqs)
        }
    }

    private fun handleFailure(failure: Failure) {
        with(faqsView) {
            hideLoading()
            showError()
        }
    }
}
