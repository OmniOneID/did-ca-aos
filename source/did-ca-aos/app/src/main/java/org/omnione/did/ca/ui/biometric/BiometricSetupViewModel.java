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

package org.omnione.did.ca.ui.biometric;

import android.content.Context;

import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.ui.Event;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class BiometricSetupViewModel extends ViewModel {

    public enum BiometricCheckResult {
        ALREADY_REGISTERED,
        NONE_ENROLLED,
        HW_UNAVAILABLE,
        READY
    }

    private final BiometricRepository bioRepo;
    private final Executor mainExecutor;
    private final boolean fromSettings;

    private final MutableLiveData<BiometricSetupUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Boolean> inFlight = new MutableLiveData<>(false);
    private final MutableLiveData<Event<BioResult>> resultEvent = new MutableLiveData<>();
    private final MutableLiveData<Event<BiometricCheckResult>> checkEvent = new MutableLiveData<>();

    @Inject
    public BiometricSetupViewModel(SavedStateHandle handle,
                                   BiometricRepository bioRepo,
                                   @ApplicationContext Context appCtx) {
        this.bioRepo = bioRepo;
        this.mainExecutor = ContextCompat.getMainExecutor(appCtx);

        Boolean fs = handle.get(BiometricSetupActivity.EXTRA_FROM_SETTINGS);
        this.fromSettings = fs != null && fs;
        state.setValue(new BiometricSetupUiState(
                fromSettings ? R.string.common_cancel : R.string.biometric_skip));
    }

    public LiveData<BiometricSetupUiState> getState()                       { return state; }
    public LiveData<Boolean> getInFlight()                                  { return inFlight; }
    public LiveData<Event<BioResult>> getResultEvent()                      { return resultEvent; }
    public LiveData<Event<BiometricCheckResult>> getCheckEvent()            { return checkEvent; }
    public boolean isFromSettings()                                         { return fromSettings; }

    public void onEnableClicked(Context activityCtx) {
        if (Boolean.TRUE.equals(inFlight.getValue())) return;
        inFlight.setValue(true);
        bioRepo.registerBiometric(activityCtx).whenCompleteAsync((result, err) -> {
            inFlight.setValue(false);
            BioResult r = (err != null)
                    ? new BioResult(BioResult.Status.ERROR, String.valueOf(err.getMessage()))
                    : result;
            resultEvent.setValue(new Event<>(r));
        }, mainExecutor);
    }

    public void onSettingsBioWithPin(Context activityCtx, String pin) {
        if (Boolean.TRUE.equals(inFlight.getValue())) return;
        inFlight.setValue(true);
        bioRepo.registerBiometricFromSettings(activityCtx, pin).whenCompleteAsync((result, err) -> {
            inFlight.setValue(false);
            BioResult r = (err != null)
                    ? new BioResult(BioResult.Status.ERROR, String.valueOf(err.getMessage()))
                    : result;
            resultEvent.setValue(new Event<>(r));
        }, mainExecutor);
    }

    public void checkBioStateForSettings(int canAuth) {
        if (Boolean.TRUE.equals(inFlight.getValue())) return;
        if (canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            checkEvent.setValue(new Event<>(BiometricCheckResult.NONE_ENROLLED));
            return;
        }
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            checkEvent.setValue(new Event<>(BiometricCheckResult.HW_UNAVAILABLE));
            return;
        }
        inFlight.setValue(true);
        bioRepo.isBiometricRegistered().whenCompleteAsync((registered, err) -> {
            inFlight.setValue(false);
            boolean isRegistered = err == null && Boolean.TRUE.equals(registered);
            checkEvent.setValue(new Event<>(
                    isRegistered
                            ? BiometricCheckResult.ALREADY_REGISTERED
                            : BiometricCheckResult.READY));
        }, mainExecutor);
    }
}
