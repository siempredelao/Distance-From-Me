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

package gc.david.dfm.distance.data;

import java.util.List;

import gc.david.dfm.model.Distance;
import gc.david.dfm.model.Position;

/**
 * Created by david on 16.01.17.
 */
public interface DistanceRepository {

    interface Callback {
        void onSuccess();

        void onFailure();
    }

    interface LoadDistancesCallback {
        void onSuccess(List<Distance> distanceList);

        void onFailure();
    }

    interface LoadPositionsByIdCallback {
        void onSuccess(List<Position> positionList);

        void onFailure();
    }

    void insert(Distance distance, List<Position> positionList, Callback callback);

    void loadDistances(LoadDistancesCallback callback);

    void clear(Callback callback);

    void getPositionListById(long distanceId, LoadPositionsByIdCallback callback);
}
