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

package gc.david.dfm.opensource.data;

import java.util.ArrayList;
import java.util.List;

import gc.david.dfm.opensource.data.model.OpenSourceLibraryEntity;

/**
 * Created by david on 25.01.17.
 */
public class OpenSourceDiskDataSource implements OpenSourceRepository {

    private final List<OpenSourceLibraryEntity> openSourceLibraryEntityList;

    public OpenSourceDiskDataSource() {
        this.openSourceLibraryEntityList = new ArrayList<>();
        // TODO: 21.12.16 get open source libraries from Firebase and cache them
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("Support Library",
                                                                    "The Android Open Source Project",
                                                                    "27.1.1",
                                                                    "https://developer.android.com/topic/libraries/support-library/index.html",
                                                                    "Apache-2.0",
                                                                    "2007-2017",
                                                                    "Serves a standard way to provide newer features on earlier versions of Android or gracefully fall back to equivalent functionality."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("Google Play Services",
                                                                    "The Android Open Source Project",
                                                                    "15.0.1",
                                                                    "https://developers.google.com/android/guides/overview",
                                                                    "Apache-2.0",
                                                                    "2007-2017",
                                                                    "Take advantage of the latest, Google-powered features such as Maps, Google+, and more, with automatic platform updates distributed as an APK through the Google Play store."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("GraphView",
                                                                    "Jonas Gehring",
                                                                    "3.1.3",
                                                                    "http://www.android-graphview.org/",
                                                                    "Apache-2.0",
                                                                    "2016",
                                                                    "Android Graph Library for creating zoomable and scrollable line and bar graphs."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("InMobi",
                                                                    "InMobi",
                                                                    "7.2.0",
                                                                    "http://www.inmobi.com/",
                                                                    "Copyright",
                                                                    "2017",
                                                                    "InMobi enables consumers to discover new products and services by providing contextual and personalized ad experiences on mobile devices."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("ButterKnife",
                                                                    "Jake Wharton",
                                                                    "8.8.1",
                                                                    "http://jakewharton.github.io/butterknife/",
                                                                    "Apache-2.0",
                                                                    "2013",
                                                                    "Bind Android views and callbacks to fields and methods."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("greenDAO",
                                                                    "Markus Junginger / greenrobot.org",
                                                                    "3.2.2",
                                                                    "http://greenrobot.org/greendao/",
                                                                    "Apache-2.0",
                                                                    "2011-2017",
                                                                    "greenDAO is a light & fast ORM solution for Android that maps objects to SQLite databases."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("Crashlytics",
                                                                    "Fabric",
                                                                    "2.9.5",
                                                                    "https://fabric.io/kits/android/crashlytics",
                                                                    "Copyright",
                                                                    "2017",
                                                                    "The most powerful, yet lightest weight crash reporting solution."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("Dagger",
                                                                    "The Dagger Authors",
                                                                    "2.16",
                                                                    "https://google.github.io/dagger/",
                                                                    "Apache-2.0",
                                                                    "2012",
                                                                    "A fast dependency injector for Android and Java."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("OkHttp",
                                                                    "Square, Inc.",
                                                                    "3.11.0",
                                                                    "http://square.github.io/okhttp/",
                                                                    "Apache-2.0",
                                                                    "2016",
                                                                    "An HTTP+HTTP/2 client for Android and Java applications."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("Gson",
                                                                    "Google Inc.",
                                                                    "2.8.5",
                                                                    "https://github.com/google/gson",
                                                                    "Apache-2.0",
                                                                    "2008",
                                                                    "A Java serialization/deserialization library that can convert Java Objects into JSON and back."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("ConstraintLayout",
                                                                    "The Android Open Source Project",
                                                                    "1.1.3",
                                                                    "https://developer.android.com/training/constraint-layout/index.html",
                                                                    "Apache-2.0",
                                                                    "2007-2017",
                                                                    "ConstraintLayout allows you to create large and complex layouts with a flat view hierarchy (no nested view groups)."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("LeakCanary",
                                                                    "Square, Inc.",
                                                                    "1.6.1",
                                                                    "https://github.com/square/leakcanary",
                                                                    "Apache-2.0",
                                                                    "2015",
                                                                    "A memory leak detection library for Android and Java."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("JUnit",
                                                                    "JUnit",
                                                                    "4.12",
                                                                    "http://junit.org/junit4/",
                                                                    "EPL-1.0",
                                                                    "2002-2017",
                                                                    "JUnit is a simple framework to write repeatable tests. It is an instance of the xUnit architecture for unit testing frameworks."));
        openSourceLibraryEntityList.add(new OpenSourceLibraryEntity("Mockito",
                                                                    "Mockito contributors",
                                                                    "2.3.0",
                                                                    "http://site.mockito.org/",
                                                                    "MIT",
                                                                    "2007",
                                                                    "Most popular Mocking framework for unit tests written in Java."));
    }

    @Override
    public void getOpenSourceLibraries(final Callback callback) {
        waitToMakeThisFeatureMoreInteresting();
        callback.onSuccess(openSourceLibraryEntityList);
    }

    private void waitToMakeThisFeatureMoreInteresting() {
        try {
            Thread.sleep(500L);
        } catch (InterruptedException e) {
            // nothing
        }
    }
}
