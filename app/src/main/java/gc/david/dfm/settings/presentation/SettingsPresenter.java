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

package gc.david.dfm.settings.presentation;

import gc.david.dfm.distance.domain.ClearDistancesUseCase;

/**
 * Created by david on 24.01.17.
 */
public class SettingsPresenter implements Settings.Presenter {

    private final Settings.View settingsView;
    private final ClearDistancesUseCase clearDistancesUseCase;

    public SettingsPresenter(final Settings.View settingsView, final ClearDistancesUseCase clearDistancesUseCase) {
        this.settingsView = settingsView;
        this.clearDistancesUseCase = clearDistancesUseCase;
    }

    @Override
    public void onClearData() {
        clearDistancesUseCase.execute(new ClearDistancesUseCase.Callback() {
            @Override
            public void onClear() {
                settingsView.showClearDataSuccessMessage();
            }

            @Override
            public void onError() {
                settingsView.showClearDataErrorMessage();
            }
        });
    }
}
