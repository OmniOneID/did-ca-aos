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

package org.omnione.did.ca.ui.splash;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.data.repository.UserIdentityRepository;
import org.omnione.did.ca.data.repository.WalletStateRepository;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.AppLog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class SplashViewModel extends ViewModel {

    private final WalletStateRepository walletStateRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final MutableLiveData<Event<SplashRoute>> routeEvent = new MutableLiveData<>();

    @Inject
    public SplashViewModel(WalletStateRepository walletStateRepository,
                           UserIdentityRepository userIdentityRepository) {
        this.walletStateRepository = walletStateRepository;
        this.userIdentityRepository = userIdentityRepository;
    }

    public LiveData<Event<SplashRoute>> getRouteEvent() {
        return routeEvent;
    }

    public void bootstrap(Executor mainExecutor) {
        walletStateRepository.ensureWalletCreated()
                .thenCompose(created -> {
                    if (created) {
                        return CompletableFuture.completedFuture(SplashRoute.ONBOARDING);
                    }
                    if (!userIdentityRepository.isInitialized()) {

                        return CompletableFuture.completedFuture(SplashRoute.ONBOARDING_RESUME);
                    }
                    return walletStateRepository.isLocked()
                            .thenApply(locked -> locked ? SplashRoute.PIN_AUTH : SplashRoute.DOCS);
                })
                .whenCompleteAsync((next, err) -> {
                    if (err != null) {
                        AppLog.e("SplashRoute", "bootstrap error", err);
                        routeEvent.postValue(new Event<>(SplashRoute.ERROR));
                        return;
                    }
                    routeEvent.postValue(new Event<>(next));
                }, mainExecutor);
    }
}
