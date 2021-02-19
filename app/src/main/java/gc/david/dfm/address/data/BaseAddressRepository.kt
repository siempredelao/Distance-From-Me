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

package gc.david.dfm.address.data

import com.google.android.gms.maps.model.LatLng
import gc.david.dfm.address.domain.AddressRepository

class BaseAddressRepository(private val remoteDataSource: AddressRemoteDataSource)
    : AddressRepository {

    override fun getNameByCoordinates(coordinates: LatLng, callback: AddressRepository.Callback) {
        remoteDataSource.getNameByCoordinates(coordinates, callback)
    }

    override fun getCoordinatesByName(name: String, callback: AddressRepository.Callback) {
        remoteDataSource.getCoordinatesByName(name, callback)
    }
}