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

package gc.david.dfm.distance.domain

import gc.david.dfm.database.Position
import gc.david.dfm.distance.data.DistanceRepository
import gc.david.dfm.executor.Executor
import gc.david.dfm.executor.Interactor
import gc.david.dfm.executor.MainThread

/**
 * Created by david on 16.01.17.
 */
class GetPositionListInteractor(
        private val executor: Executor,
        private val mainThread: MainThread,
        private val repository: DistanceRepository
) : Interactor, GetPositionListUseCase {

    private lateinit var callback: GetPositionListUseCase.Callback
    private var distanceId: Long = 0

    override fun execute(distanceId: Long, callback: GetPositionListUseCase.Callback) {
        this.distanceId = distanceId
        this.callback = callback
        this.executor.run(this)
    }

    override fun run() {
        repository.getPositionListById(distanceId, object : DistanceRepository.LoadPositionsByIdCallback {
            override fun onSuccess(positionList: List<Position>) {
                mainThread.post(Runnable { callback.onPositionListLoaded(positionList) })
            }

            override fun onFailure() {
                mainThread.post(Runnable { callback.onError() })
            }
        })
    }
}
