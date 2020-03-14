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

package gc.david.dfm.elevation.domain

import com.google.android.gms.maps.model.LatLng
import gc.david.dfm.elevation.data.ElevationRepository
import gc.david.dfm.elevation.data.mapper.ElevationEntityDataMapper
import gc.david.dfm.elevation.data.model.ElevationEntity
import gc.david.dfm.elevation.data.model.ElevationStatus
import gc.david.dfm.elevation.domain.ElevationInteractor.Params
import gc.david.dfm.elevation.domain.model.Elevation
import gc.david.dfm.executor.CoInteractor
import gc.david.dfm.executor.Either
import gc.david.dfm.executor.Failure
import javax.inject.Inject

/**
 * Created by david on 05.01.17.
 */
class ElevationInteractor @Inject constructor(
        private val mapper: ElevationEntityDataMapper,
        private val repository: ElevationRepository
) : CoInteractor<Elevation, Params>() {

    override suspend fun run(params: Params): Either<Failure, Elevation> {
        if (params.coordinateList.isEmpty()) {
            return Either.Left(ElevationFailure.EmptyList())
        } else {
            val coordinatesPath = getCoordinatesPath(params.coordinateList)

            repository.getElevation("", 1).
            return repository.getElevation(coordinatesPath, params.maxSamples)
                    .either({
                        Either.Left(Failure.ServerError)
                    }, {
                        if (ElevationStatus.OK == it.status) {
                            val elevation = mapper.transform(it)
                            Either.Right(elevation)
                        } else {
                            Either.Left(it.status.toString())
                        }
                    })
        }
    }

    private fun getCoordinatesPath(coordinateList: List<LatLng>): String {
        return coordinateList.joinToString("|") { "${it.latitude},${it.longitude}" }
    }

    data class Params(val coordinateList: List<LatLng>, val maxSamples: Int)

    sealed class ElevationFailure : Failure.FeatureFailure() {

        class EmptyList: FeatureFailure()
    }
}
