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

package org.omnione.did.ca.ui.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.data.repository.AppResetRepository;
import org.omnione.did.ca.data.repository.WalletStateRepository;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.AppLog;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class SettingsViewModel extends ViewModel {

    private static final String TAG = "SettingsVm";

    private final WalletStateRepository walletStateRepository;
    private final AppResetRepository appResetRepository;

    private final MutableLiveData<SettingsUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> resetCompleteEvent = new MutableLiveData<>();

    private final MutableLiveData<Event<Boolean>> unlockPinChangeRequest = new MutableLiveData<>();
    private final Executor callbackExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public SettingsViewModel(@NonNull WalletStateRepository walletStateRepository,
                             @NonNull AppResetRepository appResetRepository) {
        this.walletStateRepository = walletStateRepository;
        this.appResetRepository = appResetRepository;
    }

    public void init() {
        if (state.getValue() != null) return;
        state.setValue(new SettingsUiState(""));
        walletStateRepository.getHolderDid().whenCompleteAsync((did, err) -> {
            if (err != null) {
                AppLog.e(TAG, "getHolderDid failed", err);
                return;
            }
            state.postValue(new SettingsUiState(did == null ? "" : did));
        }, callbackExecutor);
    }

    public LiveData<SettingsUiState> getState() {
        return state;
    }

    public LiveData<Event<Boolean>> getResetCompleteEvent() {
        return resetCompleteEvent;
    }

    public LiveData<Event<Boolean>> getUnlockPinChangeRequest() {
        return unlockPinChangeRequest;
    }

    public void onChangeUnlockPinClicked() {
        walletStateRepository.isLocked().whenCompleteAsync((locked, err) -> {
            if (err != null) {
                AppLog.e(TAG, "isLocked failed", err);
                unlockPinChangeRequest.postValue(new Event<>(false));
                return;
            }
            unlockPinChangeRequest.postValue(new Event<>(Boolean.TRUE.equals(locked)));
        }, callbackExecutor);
    }

    public void resetApp() {
        callbackExecutor.execute(() -> {
            boolean killed = appResetRepository.clearAppData();
            if (!killed) {
                AppLog.e(TAG, "clearApplicationUserData() returned false; using fallback nav");
                resetCompleteEvent.postValue(new Event<>(false));
            }

        });
    }
}
