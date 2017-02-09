/*
 * Copyright (c) 2017 David Aguiar Gonzalez
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

package gc.david.dfm.opensource.presentation.mapper;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import gc.david.dfm.opensource.data.model.OpenSourceLibraryEntity;
import gc.david.dfm.opensource.presentation.model.OpenSourceLibraryModel;

/**
 * Created by david on 25.01.17.
 * <p>
 * Mapper class used to transform {@link OpenSourceLibraryEntity} in the Domain layer
 * to {@link OpenSourceLibraryModel} in the Presentation layer.
 */
@Singleton
public class OpenSourceLibraryMapper {

    @Inject
    public OpenSourceLibraryMapper() {
    }

    public List<OpenSourceLibraryModel> transform(final List<OpenSourceLibraryEntity> openSourceLibraryEntityList) {
        final List<OpenSourceLibraryModel> openSourceLibraryModelList = new ArrayList<>();
        for (final OpenSourceLibraryEntity openSourceLibraryEntity : openSourceLibraryEntityList) {
            final OpenSourceLibraryModel openSourceLibraryModel = new OpenSourceLibraryModel(openSourceLibraryEntity.getName(),
                                                                                             openSourceLibraryEntity.getAuthor(),
                                                                                             openSourceLibraryEntity.getVersion(),
                                                                                             openSourceLibraryEntity.getLink(),
                                                                                             openSourceLibraryEntity.getLicenseCode(),
                                                                                             openSourceLibraryEntity.getLicenseYear(),
                                                                                             openSourceLibraryEntity.getDescription());
            openSourceLibraryModelList.add(openSourceLibraryModel);
        }
        return openSourceLibraryModelList;
    }
}
