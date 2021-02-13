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

package gc.david.dfm.dagger

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import gc.david.dfm.database.DFMDatabase
import javax.inject.Singleton

/**
 * Created by david on 16.01.17.
 */
@Module
class StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): DFMDatabase {
        return Room.databaseBuilder(context, DFMDatabase::class.java, "DistanciasDB.db").build()
    }
}
