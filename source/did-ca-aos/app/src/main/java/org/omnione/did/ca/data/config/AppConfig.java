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
package org.omnione.did.ca.data.config;

import androidx.annotation.NonNull;

import org.omnione.did.ca.data.repository.ServerUrlRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AppConfig {

    private final ServerUrlRepository serverUrlRepository;

    @Inject
    public AppConfig(@NonNull ServerUrlRepository serverUrlRepository) {
        this.serverUrlRepository = serverUrlRepository;
    }

    public String walletUrl() {
        return serverUrlRepository.url(ServerUrlRepository.KEY_WALLET_URL);
    }

    public String tasUrl() {
        return serverUrlRepository.url(ServerUrlRepository.KEY_TAS_URL);
    }

    public String casUrl() {
        return serverUrlRepository.url(ServerUrlRepository.KEY_CAS_URL);
    }

    public String apiGwUrl() {
        return serverUrlRepository.url(ServerUrlRepository.KEY_API_GW_URL);
    }

    public String verifierUrl() {
        return serverUrlRepository.url(ServerUrlRepository.KEY_VERIFIER_URL);
    }

    public String demoUrl() {
        return serverUrlRepository.url(ServerUrlRepository.KEY_DEMO_URL);
    }
}
