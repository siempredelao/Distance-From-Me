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

import gc.david.dfm.executor.CoInteractor
import gc.david.dfm.executor.Either
import gc.david.dfm.executor.Failure
import gc.david.dfm.faq.model.Faq
import javax.inject.Inject

/**
 * Created by david on 17.12.16.
 */
class GetFaqsUseCase @Inject constructor(
        private val repository: GetFaqsRepository
) : CoInteractor<Set<Faq>, Unit>() {

    override suspend fun run(params: Unit): Either<Failure, Set<Faq>> {
        return repository.getFaqs()
    }
}
