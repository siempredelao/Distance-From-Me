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

object SdkVersions {

    const val min = 22
    const val target = 28
}

object AppVersions {

    const val code = 29
    const val name = "3.4.3"
}

object LibraryVersions {

    const val butterknife = "10.2.0"
    const val cardView = "1.0.0"
    const val collectionKtx = "1.1.0"
    const val constraintLayout = "1.1.3"
    const val coreKtx = "1.1.0"
    const val coroutines = "1.3.2"
    const val crashlytics = "2.10.1"
    const val dagger = "2.16"
    const val dexcount = "0.8.3"
    const val fabricPlugin = "1.31.2"
    const val gradlePlugin = "3.5.2"
    const val graphview = "4.2.2"
    const val gson = "2.8.6"
    const val junit = "4.12"
    const val kotlin = "1.3.41"
    const val leakCanary = "1.6.3"
    const val materialComponents = "1.0.0"
    const val mockito = "2.28.2"
    const val mockitoKotlin = "2.2.0"
    const val okhttp = "3.11.0"
    const val playServices = "17.0.0"
    const val preference = "1.0.0"
    const val room = "2.2.0"
}

object Dependencies {

    const val butterknife = "com.jakewharton:butterknife:${LibraryVersions.butterknife}"
    const val butterknifeCompiler = "com.jakewharton:butterknife-compiler:${LibraryVersions.butterknife}"
    const val cardView = "androidx.cardview:cardview:${LibraryVersions.cardView}"
    const val collectionKtx = "androidx.collection:collection-ktx:${LibraryVersions.collectionKtx}"
    const val constraintLayout = "androidx.constraintlayout:constraintlayout:${LibraryVersions.constraintLayout}"
    const val coreKtx = "androidx.core:core-ktx:${LibraryVersions.coreKtx}"
    const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${LibraryVersions.coroutines}"
    const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${LibraryVersions.coroutines}"
    const val crashlytics = "com.crashlytics.sdk.android:crashlytics:${LibraryVersions.crashlytics}"
    const val dagger = "com.google.dagger:dagger:${LibraryVersions.dagger}"
    const val daggerAndroid = "com.google.dagger:dagger-android:${LibraryVersions.dagger}"
    const val daggerAndroidSupport = "com.google.dagger:dagger-android-support:${LibraryVersions.dagger}"
    const val daggerCompiler = "com.google.dagger:dagger-compiler:${LibraryVersions.dagger}"
    const val daggerProcessor = "com.google.dagger:dagger-android-processor:${LibraryVersions.dagger}"
    const val dexcount = "com.getkeepsafe.dexcount:dexcount-gradle-plugin:${LibraryVersions.dexcount}"
    const val fabric = "io.fabric.tools:gradle:${LibraryVersions.fabricPlugin}"
    const val gradlePlugin = "com.android.tools.build:gradle:${LibraryVersions.gradlePlugin}"
    const val graphview = "com.jjoe64:graphview:${LibraryVersions.graphview}"
    const val gson = "com.google.code.gson:gson:${LibraryVersions.gson}"
    const val kotlinGradle = "org.jetbrains.kotlin:kotlin-gradle-plugin:${LibraryVersions.kotlin}"
    const val leakCanary = "com.squareup.leakcanary:leakcanary-android:${LibraryVersions.leakCanary}"
    const val leakCanaryNoOp = "com.squareup.leakcanary:leakcanary-android-no-op:${LibraryVersions.leakCanary}"
    const val materialComponents = "com.google.android.material:material:${LibraryVersions.materialComponents}"
    const val okhttp = "com.squareup.okhttp3:okhttp:${LibraryVersions.okhttp}"
    const val playServicesLocation = "com.google.android.gms:play-services-location:${LibraryVersions.playServices}"
    const val playServicesMaps = "com.google.android.gms:play-services-maps:${LibraryVersions.playServices}"
    const val preference = "androidx.preference:preference:${LibraryVersions.preference}"
    const val roomCompiler = "androidx.room:room-compiler:${LibraryVersions.room}"
    const val roomKtx = "androidx.room:room-ktx:${LibraryVersions.room}"
    const val roomRuntime = "androidx.room:room-runtime:${LibraryVersions.room}"
}

object TestDependencies {

    const val junit = "junit:junit:${LibraryVersions.junit}"
    const val mockito = "org.mockito:mockito-core:${LibraryVersions.mockito}"
    const val mockitoInline = "org.mockito:mockito-inline:${LibraryVersions.mockito}"
    const val mockitoKotlin = "com.nhaarman.mockitokotlin2:mockito-kotlin:${LibraryVersions.mockitoKotlin}"
}
