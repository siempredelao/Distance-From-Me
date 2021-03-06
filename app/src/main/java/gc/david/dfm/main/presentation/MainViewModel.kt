/*
 * Copyright (c) 2021 David Aguiar Gonzalez
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

package gc.david.dfm.main.presentation

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gc.david.dfm.ConnectionManager
import gc.david.dfm.Event
import gc.david.dfm.ResourceProvider
import gc.david.dfm.Utils
import gc.david.dfm.database.Distance
import gc.david.dfm.database.Position
import gc.david.dfm.distance.domain.GetPositionListInteractor
import gc.david.dfm.distance.domain.LoadDistancesInteractor
import timber.log.Timber

class MainViewModel(
        private val loadDistancesUseCase: LoadDistancesInteractor,
        private val getPositionListUseCase: GetPositionListInteractor,
        private val connectionManager: ConnectionManager,
        private val resourceProvider: ResourceProvider
) : ViewModel() {

    val showLoadDistancesItem = MutableLiveData<Boolean>()
    val showForceCrashItem = MutableLiveData<Boolean>()
    val selectFromDistancesLoaded = MutableLiveData<Event<List<Distance>>>()
    val drawDistance = MutableLiveData<DrawDistanceModel>()

    fun onStart() {
        // TODO
    }

    fun onResume() {
        // Reloading distances in case a new one was saved into database
        // TODO transform use case to observable to avoid this workaround
        loadDistancesItem()
    }

    /**
     * Triggered when the menu is already built and ready to be updated.
     */
    fun onMenuReady() {
        loadDistancesItem()
        showForceCrashItem.value = !Utils.isReleaseBuild()
    }

    private fun loadDistancesItem() {
        loadDistancesUseCase.execute(object : LoadDistancesInteractor.Callback {
            override fun onDistanceListLoaded(distanceList: List<Distance>) {
                showLoadDistancesItem.value = distanceList.isNotEmpty()
            }

            override fun onError() {
                showLoadDistancesItem.value = false
            }
        })
    }

    /**
     * Triggered when the user taps on the "Show distances" menu item.
     */
    fun onLoadDistancesClick() {
        loadDistancesUseCase.execute(object : LoadDistancesInteractor.Callback {
            override fun onDistanceListLoaded(distanceList: List<Distance>) {
                selectFromDistancesLoaded.value = Event(distanceList)
            }

            override fun onError() {
                Timber.tag(TAG).e(Exception("Unable to load distances."))
            }
        })
    }

    /**
     * Triggered when the user selects a distance from the loaded distances dialog.
     */
    fun onDistanceToShowSelected(distance: Distance) {
        getPositionListUseCase.execute(distance.id!!, object : GetPositionListInteractor.Callback {
            override fun onPositionListLoaded(positionList: List<Position>) {
                drawDistance.value = DrawDistanceModel(positionList, distance.name)
            }

            override fun onError() {
                Timber.tag(TAG).e(Exception("Unable to get position by id."))
            }
        })
    }

    fun onForceCrashClick() {
        throw RuntimeException("User forced crash")
    }

    companion object {

        private const val TAG = "MainViewModel"
    }
}

data class DrawDistanceModel(val positionList: List<Position>, val distanceName: String)