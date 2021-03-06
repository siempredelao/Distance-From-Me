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

package gc.david.dfm.di

import androidx.room.Room
import gc.david.dfm.*
import gc.david.dfm.address.data.AddressRemoteDataSource
import gc.david.dfm.address.data.BaseAddressRepository
import gc.david.dfm.address.data.mapper.AddressCollectionEntityDataMapper
import gc.david.dfm.address.domain.AddressRepository
import gc.david.dfm.address.domain.GetAddressCoordinatesByNameInteractor
import gc.david.dfm.address.domain.GetAddressNameByCoordinatesInteractor
import gc.david.dfm.address.presentation.AddressViewModel
import gc.david.dfm.database.DFMDatabase
import gc.david.dfm.distance.data.BaseDistanceRepository
import gc.david.dfm.distance.data.DistanceLocalDataSource
import gc.david.dfm.distance.domain.*
import gc.david.dfm.elevation.data.BaseElevationRepository
import gc.david.dfm.elevation.data.ElevationRemoteDataSource
import gc.david.dfm.elevation.data.mapper.ElevationEntityDataMapper
import gc.david.dfm.elevation.domain.ElevationInteractor
import gc.david.dfm.elevation.domain.ElevationRepository
import gc.david.dfm.elevation.presentation.ElevationViewModel
import gc.david.dfm.executor.NewMainThread
import gc.david.dfm.executor.NewThreadExecutor
import gc.david.dfm.faq.data.BaseFaqRepository
import gc.david.dfm.faq.data.FaqDiskDataSource
import gc.david.dfm.faq.domain.FaqRepository
import gc.david.dfm.faq.domain.GetFaqsInteractor
import gc.david.dfm.faq.presentation.FaqViewModel
import gc.david.dfm.initializers.DefaultUnitInitializer
import gc.david.dfm.initializers.FirebaseInitializer
import gc.david.dfm.initializers.Initializers
import gc.david.dfm.initializers.LoggingInitializer
import gc.david.dfm.main.presentation.MainViewModel
import gc.david.dfm.opensource.data.BaseOpenSourceRepository
import gc.david.dfm.opensource.data.OpenSourceDiskDataSource
import gc.david.dfm.opensource.domain.OpenSourceInteractor
import gc.david.dfm.opensource.domain.OpenSourceRepository
import gc.david.dfm.opensource.presentation.OpenSourceViewModel
import gc.david.dfm.opensource.presentation.mapper.OpenSourceLibraryMapper
import gc.david.dfm.settings.presentation.SettingsViewModel
import gc.david.dfm.showinfo.presentation.SaveDistanceViewModel
import gc.david.dfm.showinfo.presentation.ShowInfoViewModel
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    single<ConnectionManager> { DefaultConnectionManager(get()) }
    single { arrayOf(DefaultUnitInitializer(), FirebaseInitializer(), LoggingInitializer(get())) }
    single { Initializers(get()) }
    single<PreferencesProvider> { DefaultPreferencesProvider(get()) }
    single { NewMainThread() }
    single { NewThreadExecutor() }
    single { ResourceProvider(get()) }
}

val viewModelModule = module {

    viewModel { FaqViewModel(get(), get()) }
    viewModel { OpenSourceViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
    viewModel { ShowInfoViewModel(get(), get(), get(), get()) }
    viewModel { SaveDistanceViewModel(get(), get()) }
    viewModel { ElevationViewModel(get(), get(), get()) }
    viewModel { AddressViewModel(get(), get(), get(), get()) }
    viewModel { MainViewModel(get(), get(), get(), get()) }
}

val useCaseModule = module {
    // Use cases
    factory { ClearDistancesInteractor(get(), get(), get()) }
    factory { ElevationInteractor(get(), get(), get(), get()) }
    factory { GetAddressCoordinatesByNameInteractor(get(), get(), get(), get()) }
    factory { GetAddressNameByCoordinatesInteractor(get(), get(), get(), get()) }
    factory { GetFaqsInteractor(get(), get(), get()) }
    factory { GetPositionListInteractor(get(), get(), get()) }
    factory { InsertDistanceInteractor(get(), get(), get()) }
    factory { LoadDistancesInteractor(get(), get(), get()) }
    factory { OpenSourceInteractor(get(), get(), get()) }

    // Mappers
    factory { AddressCollectionEntityDataMapper() }
    factory { ElevationEntityDataMapper() }
    factory { OpenSourceLibraryMapper() }
}

val repositoryModule = module {

    single<DistanceRepository> { BaseDistanceRepository(get()) }
    single<AddressRepository> { BaseAddressRepository(get()) }
    single<ElevationRepository> { BaseElevationRepository(get()) }
    single<OpenSourceRepository> { BaseOpenSourceRepository(get()) }
    single<FaqRepository> { BaseFaqRepository(get()) }

    single { DistanceLocalDataSource(get()) }
    single { AddressRemoteDataSource(get()) }
    single { ElevationRemoteDataSource(get()) }
    single { OpenSourceDiskDataSource() }
    single { FaqDiskDataSource() }
}

val storageModule = module {

    single { Room.databaseBuilder(get(), DFMDatabase::class.java, "DistanciasDB.db").build() }
}