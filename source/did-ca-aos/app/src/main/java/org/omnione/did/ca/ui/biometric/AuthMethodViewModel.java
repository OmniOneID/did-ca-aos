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

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.data.model.BioResult;
import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.ui.Event;

import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class AuthMethodViewModel extends ViewModel {

    private final BiometricRepository bioRepo;
    private final Executor mainExecutor;

    private final MutableLiveData<Boolean> inFlight = new MutableLiveData<>(false);
    private final MutableLiveData<Event<AuthMethodResult>> resultEvent = new MutableLiveData<>();

    @Inject
    public AuthMethodViewModel(BiometricRepository bioRepo,
                               @ApplicationContext Context appCtx) {
        this.bioRepo = bioRepo;
        this.mainExecutor = ContextCompat.getMainExecutor(appCtx);
    }

    public LiveData<Boolean> getInFlight()                       { return inFlight; }
    public LiveData<Event<AuthMethodResult>> getResultEvent()    { return resultEvent; }

    public void onBiometricSelected(Context activityCtx) {
        if (Boolean.TRUE.equals(inFlight.getValue())) return;
        inFlight.setValue(true);
        bioRepo.authenticateBiometric(activityCtx)
                .whenCompleteAsync((result, err) -> {
                    inFlight.setValue(false);
                    AuthMethodResult res = (err == null
                            && result != null
                            && result.status == BioResult.Status.SUCCESS)
                            ? AuthMethodResult.BIO_AUTH_SUCCESS
                            : AuthMethodResult.BIO_AUTH_FAILED;
                    resultEvent.setValue(new Event<>(res));
                }, mainExecutor);
    }
}
