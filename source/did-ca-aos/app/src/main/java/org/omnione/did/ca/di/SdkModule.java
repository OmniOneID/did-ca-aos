/*
 * Copyright 2026 OmniOne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnione.did.ca.di;

import android.content.Context;

import org.omnione.did.ca.BuildConfig;
import org.omnione.did.ca.data.config.AppConfig;
import org.omnione.did.sdk.core.api.WalletApi;
import org.omnione.did.sdk.core.exception.WalletCoreException;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class SdkModule {

    @Provides
    @Singleton
    static AppConfig provideAppConfig() {
        return new AppConfig(BuildConfig.WALLET_URL, BuildConfig.TAS_URL, BuildConfig.CAS_URL,
                BuildConfig.API_GW_URL, BuildConfig.VERIFIER_URL);
    }

    @Provides
    @Singleton
    static WalletApi provideWalletApi(@ApplicationContext Context context) {
        try {
            return WalletApi.getInstance(context);
        } catch (WalletCoreException e) {
            throw new RuntimeException("WalletApi initialization failed", e);
        }
    }

    private SdkModule() {}
}
