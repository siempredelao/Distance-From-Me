/*
 * Copyright (c) 2018 David Aguiar Gonzalez
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

package gc.david.dfm.address.presentation

import com.google.android.gms.maps.model.LatLng
import com.nhaarman.mockitokotlin2.whenever

import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

import java.util.ArrayList

import gc.david.dfm.ConnectionManager
import gc.david.dfm.address.domain.GetAddressUseCase
import gc.david.dfm.address.domain.model.AddressCollection

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Created by david on 15.01.17.
 */
class AddressPresenterTest {

    @Mock
    lateinit var addressView: Address.View
    @Mock
    lateinit var getAddressCoordinatesByNameUseCase: GetAddressUseCase<String>
    @Mock
    lateinit var getAddressNameByCoordinatesUseCase: GetAddressUseCase<LatLng>
    @Mock
    lateinit var connectionManager: ConnectionManager

    private lateinit var addressPresenter: AddressPresenter

    private val fakeCoordinates: LatLng
        get() = LatLng(0.0, 0.0)

    private val fakeLocationName: String
        get() = "fake location name"

    private val fakeAddress: gc.david.dfm.address.domain.model.Address
        get() = gc.david.dfm.address.domain.model.Address(fakeLocationName, fakeCoordinates)

    private val fakeErrorMessage: String
        get() = "fake errorMessage"

    private val emptyAddressCollection: AddressCollection
        get() = AddressCollection(ArrayList())

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        addressPresenter = AddressPresenter(addressView,
                getAddressCoordinatesByNameUseCase,
                getAddressNameByCoordinatesUseCase,
                connectionManager)
    }

    @Test
    fun `shows connection problems dialog when no connection available in position by name`() {
        whenever(connectionManager.isOnline).thenReturn(false)
        val locationName = fakeLocationName

        addressPresenter.searchPositionByName(locationName)

        verify(addressView).showConnectionProblemsDialog()
    }

    @Test
    fun `shows progress dialog when connection available in position by name`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val locationName = fakeLocationName

        addressPresenter.searchPositionByName(locationName)

        verify(addressView).showProgressDialog()
    }

    @Test
    fun `executes coordinates by name use case when connection available`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val locationName = fakeLocationName

        addressPresenter.searchPositionByName(locationName)

        verify(getAddressCoordinatesByNameUseCase)
                .execute(any(), anyInt(), any(GetAddressUseCase.Callback::class.java))
    }

    @Test
    fun `hides progress dialog when position by name use case succeeds`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val locationName = fakeLocationName
        val addressCollection = emptyAddressCollection
        executeOnAddressLoadedAfterCoordinatesByNameUseCaseCallback(locationName, addressCollection)

        addressPresenter.searchPositionByName(locationName)

        verify(addressView).hideProgressDialog()
    }

    @Test
    fun `shows no matches when position by name use case return zero results`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val locationName = fakeLocationName
        val addressCollection = emptyAddressCollection
        executeOnAddressLoadedAfterCoordinatesByNameUseCaseCallback(locationName, addressCollection)

        addressPresenter.searchPositionByName(locationName)

        verify(addressView).showNoMatchesMessage()
    }

    @Test
    fun `shows position by name when use case return one result`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val locationName = fakeLocationName
        val address = fakeAddress
        val addressList = ArrayList<gc.david.dfm.address.domain.model.Address>()
        addressList.add(address)
        val addressCollection = AddressCollection(addressList)
        executeOnAddressLoadedAfterCoordinatesByNameUseCaseCallback(locationName, addressCollection)

        addressPresenter.searchPositionByName(locationName)

        verify(addressView).showPositionByName(address)
    }

    @Test
    fun `shows address selection dialog when position by name use case return several results`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val locationName = fakeLocationName
        val address = fakeAddress
        val addressList = ArrayList<gc.david.dfm.address.domain.model.Address>()
        addressList.add(address)
        addressList.add(address)
        val addressCollection = AddressCollection(addressList)
        executeOnAddressLoadedAfterCoordinatesByNameUseCaseCallback(locationName, addressCollection)

        addressPresenter.searchPositionByName(locationName)

        verify(addressView).showAddressSelectionDialog(addressCollection.addressList)
    }

    @Test
    fun `hides progress dialog when position by name use case fails`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val locationName = fakeLocationName
        val errorMessage = fakeErrorMessage
        executeOnErrorAfterCoordinatesByNameUseCaseCallback(locationName, errorMessage)

        addressPresenter.searchPositionByName(locationName)

        verify(addressView).hideProgressDialog()
    }

    @Test
    fun `shows error when position by name use case fails`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val locationName = fakeLocationName
        val errorMessage = fakeErrorMessage
        executeOnErrorAfterCoordinatesByNameUseCaseCallback(locationName, errorMessage)

        addressPresenter.searchPositionByName(locationName)

        verify(addressView).showCallError(errorMessage)
    }

    @Test
    fun `select address in dialog shows position by name with address`() {
        val address = fakeAddress

        addressPresenter.selectAddressInDialog(address)

        verify(addressView).showPositionByName(address)
    }

    @Test
    fun `shows connection problems dialog when no connection available in position by coordinates`() {
        whenever(connectionManager.isOnline).thenReturn(false)
        val coordinates = fakeCoordinates

        addressPresenter.searchPositionByCoordinates(coordinates)

        verify(addressView).showConnectionProblemsDialog()
    }

    @Test
    fun `shows progress dialog when connection available in position by coordinates`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val coordinates = fakeCoordinates

        addressPresenter.searchPositionByCoordinates(coordinates)

        verify(addressView).showProgressDialog()
    }

    @Test
    fun `executes name by coordinates use case when connection available`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val coordinates = fakeCoordinates

        addressPresenter.searchPositionByCoordinates(coordinates)

        verify(getAddressNameByCoordinatesUseCase)
                .execute(any(), anyInt(), any(GetAddressUseCase.Callback::class.java))
    }

    @Test
    fun `hides progress dialog when use case succeeds in position by coordinates`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val coordinates = fakeCoordinates
        val addressCollection = emptyAddressCollection
        executeOnAddressLoadedAfterNameByCoordinatesUseCaseCallback(coordinates, addressCollection)

        addressPresenter.searchPositionByCoordinates(coordinates)

        verify(addressView).hideProgressDialog()
    }

    @Test
    fun `shows no matches when position by coordinates use case return zero results`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val coordinates = fakeCoordinates
        val addressCollection = emptyAddressCollection
        executeOnAddressLoadedAfterNameByCoordinatesUseCaseCallback(coordinates, addressCollection)

        addressPresenter.searchPositionByCoordinates(coordinates)

        verify(addressView).showNoMatchesMessage()
    }

    @Test
    fun `shows position by coordinates when use case return one result`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val coordinates = fakeCoordinates
        val address = fakeAddress
        val addressList = ArrayList<gc.david.dfm.address.domain.model.Address>()
        addressList.add(address)
        val addressCollection = AddressCollection(addressList)
        executeOnAddressLoadedAfterNameByCoordinatesUseCaseCallback(coordinates, addressCollection)

        addressPresenter.searchPositionByCoordinates(coordinates)

        verify(addressView).showPositionByCoordinates(address)
    }

    @Test
    fun `hides progress dialog when position by coordinates use case fails`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val coordinates = fakeCoordinates
        val errorMessage = fakeErrorMessage
        executeOnErrorAfterNameByCoordinatesUseCaseCallback(coordinates, errorMessage)

        addressPresenter.searchPositionByCoordinates(coordinates)

        verify(addressView).hideProgressDialog()
    }

    @Test
    fun `shows error when position by coordinates use case fails`() {
        whenever(connectionManager.isOnline).thenReturn(true)
        val coordinates = fakeCoordinates
        val errorMessage = fakeErrorMessage
        executeOnErrorAfterNameByCoordinatesUseCaseCallback(coordinates, errorMessage)

        addressPresenter.searchPositionByCoordinates(coordinates)

        verify(addressView).showCallError(errorMessage)
    }

    private fun executeOnAddressLoadedAfterCoordinatesByNameUseCaseCallback(locationName: String,
                                                                            addressCollection: AddressCollection) {
        doAnswer {
                (it.arguments[2] as GetAddressUseCase.Callback).onAddressLoaded(addressCollection)
        }.whenever(getAddressCoordinatesByNameUseCase)
                .execute(eq(locationName), anyInt(), any(GetAddressUseCase.Callback::class.java))
    }

    private fun executeOnErrorAfterCoordinatesByNameUseCaseCallback(locationName: String,
                                                                    errorMessage: String) {
        doAnswer {
                (it.arguments[2] as GetAddressUseCase.Callback).onError(errorMessage)
        }.whenever(getAddressCoordinatesByNameUseCase)
                .execute(eq(locationName), anyInt(), any(GetAddressUseCase.Callback::class.java))
    }

    private fun executeOnAddressLoadedAfterNameByCoordinatesUseCaseCallback(coordinates: LatLng,
                                                                            addressCollection: AddressCollection) {
        doAnswer {
                (it.arguments[2] as GetAddressUseCase.Callback).onAddressLoaded(addressCollection)
        }.whenever(getAddressNameByCoordinatesUseCase)
                .execute(eq(coordinates), anyInt(), any(GetAddressUseCase.Callback::class.java))
    }

    private fun executeOnErrorAfterNameByCoordinatesUseCaseCallback(coordinates: LatLng,
                                                                    errorMessage: String) {
        doAnswer {
                (it.arguments[2] as GetAddressUseCase.Callback).onError(errorMessage)
        }.whenever(getAddressNameByCoordinatesUseCase)
                .execute(eq(coordinates), anyInt(), any(GetAddressUseCase.Callback::class.java))
    }
}