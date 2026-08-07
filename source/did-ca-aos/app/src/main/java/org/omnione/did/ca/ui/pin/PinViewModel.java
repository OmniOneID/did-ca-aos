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

package org.omnione.did.ca.ui.pin;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.data.repository.BiometricRepository;
import org.omnione.did.ca.data.repository.LockRepository;
import org.omnione.did.ca.data.repository.UserRegistrationRepository;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.AppLog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public final class PinViewModel extends ViewModel {

    static final int PIN_LENGTH = 6;

    private final LockRepository lockRepo;
    private final UserRegistrationRepository registrationRepo;
    private final BiometricRepository bioRepo;
    private final Executor mainExecutor;
    private final PinMode mode;
    private final boolean viaBio;

    private String pendingPinForBio = null;

    private final MutableLiveData<PinUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<PinDoneEvent>> doneEvent = new MutableLiveData<>();

    private int step = 0;
    private String pin = "";
    private String firstPin = "";
    private String oldPinForChange = "";
    private boolean error = false;
    private boolean inFlight = false;

    @Inject
    public PinViewModel(SavedStateHandle handle,
                        LockRepository lockRepo,
                        UserRegistrationRepository registrationRepo,
                        BiometricRepository bioRepo,
                        @ApplicationContext Context appCtx) {
        this.lockRepo = lockRepo;
        this.registrationRepo = registrationRepo;
        this.bioRepo = bioRepo;
        this.mainExecutor = ContextCompat.getMainExecutor(appCtx);

        String modeName = handle.get(PinActivity.EXTRA_MODE);
        this.mode = parseMode(modeName);
        Boolean vb = handle.get(PinActivity.EXTRA_VIA_BIOMETRIC);
        this.viaBio = vb != null && vb;
        emit();
    }

    private static PinMode parseMode(@Nullable String name) {
        if (name == null) return PinMode.AUTH;
        try { return PinMode.valueOf(name); }
        catch (IllegalArgumentException ex) { return PinMode.AUTH; }
    }

    public LiveData<PinUiState> getState()                  { return state; }
    public LiveData<Event<PinDoneEvent>> getDoneEvent()     { return doneEvent; }
    public PinMode getMode()                                { return mode; }

    public boolean onKey(char key) {
        if (inFlight) return false;
        if (error) error = false;
        if (key == 'd') {
            if (!pin.isEmpty()) pin = pin.substring(0, pin.length() - 1);
            emit();
            return false;
        }
        if (pin.length() < PIN_LENGTH) {
            pin = pin + key;
            emit();
            return pin.length() == PIN_LENGTH;
        }
        return false;
    }

    public void advance() {
        if (inFlight) return;
        switch (mode) {
            case UNLOCK_AUTH:     advanceUnlockAuth();        break;
            case AUTH:            advanceAuth();              break;
            case CHANGE:          advanceChange(false);       break;
            case UNLOCK_CHANGE:   advanceChange(true);        break;
            case REGISTER:        advanceRegister(false);     break;
            case UNLOCK_REGISTER: advanceRegister(true);      break;
            case REGISTER_SIGN:   advanceRegisterSign();      break;
        }
    }

    private void advanceUnlockAuth() {
        inFlight = true;
        emit();
        final String entered = pin;
        lockRepo.authenticateUnlock(entered)
                .whenCompleteAsync((v, err) -> {
                    inFlight = false;
                    if (err == null) {
                        doneEvent.setValue(new Event<>(
                                new PinDoneEvent(PinDoneEvent.Type.UNLOCK_AUTH_DONE, null)));
                    } else {
                        error = true;
                        emit();
                    }
                }, mainExecutor);
    }

    private void advanceAuth() {
        inFlight = true;
        emit();
        final String entered = pin;
        lockRepo.authenticateSigning(entered)
                .whenCompleteAsync((v, err) -> {
                    inFlight = false;
                    if (err == null) {
                        doneEvent.setValue(new Event<>(
                                new PinDoneEvent(PinDoneEvent.Type.AUTH_DONE, entered)));
                    } else {
                        error = true;
                        emit();
                    }
                }, mainExecutor);
    }

    private void advanceChange(boolean unlock) {
        if (step == 0) {
            inFlight = true;
            emit();
            final String entered = pin;
            final CompletableFuture<Void> verify = unlock
                    ? lockRepo.authenticateUnlock(entered)
                    : lockRepo.authenticateSigning(entered);
            verify.whenCompleteAsync((v, err) -> {
                inFlight = false;
                if (err == null) {
                    oldPinForChange = entered;
                    pin = "";
                    step = 1;
                    emit();
                } else {

                    error = true;
                    emit();
                }
            }, mainExecutor);
            return;
        }
        if (step == 1) {
            if (pin.equals(oldPinForChange)) {
                error = true;
                emit();
            } else {
                firstPin = pin;
                pin = "";
                step = 2;
                emit();
            }
            return;
        }

        if (!pin.equals(firstPin)) {
            error = true;
            emit();
            return;
        }
        inFlight = true;
        emit();
        final String oldPin = oldPinForChange;
        final String newPin = firstPin;
        final CompletableFuture<Void> op = unlock
                ? lockRepo.changeUnlockPin(oldPin, newPin)
                : lockRepo.changeSigningPin(oldPin, newPin);
        op.whenCompleteAsync((v, err) -> {
            inFlight = false;
            if (err == null) {
                PinDoneEvent.Type type = unlock
                        ? PinDoneEvent.Type.UNLOCK_CHANGE_DONE
                        : PinDoneEvent.Type.CHANGE_DONE;
                doneEvent.setValue(new Event<>(new PinDoneEvent(type, null)));
            } else {
                step = 0;
                pin = "";
                firstPin = "";
                oldPinForChange = "";
                error = true;
                emit();
            }
        }, mainExecutor);
    }

    private void advanceRegister(boolean unlock) {
        if (step == 0) {
            firstPin = pin;
            pin = "";
            step = 1;
            emit();
            return;
        }
        if (!pin.equals(firstPin)) {
            error = true;
            emit();
            return;
        }
        inFlight = true;
        emit();
        final String newPin = firstPin;

        final CompletableFuture<Void> op = unlock
                ? lockRepo.registerUnlockPin(newPin)
                : lockRepo.registerSigningPin(newPin);
        op.whenCompleteAsync((v, err) -> {
            inFlight = false;
            if (err == null) {
                PinDoneEvent.Type type = unlock
                        ? PinDoneEvent.Type.UNLOCK_REGISTER_DONE
                        : PinDoneEvent.Type.REGISTER_DONE;
                doneEvent.setValue(new Event<>(new PinDoneEvent(type, null)));
            } else {

                AppLog.e("PinViewModel",
                        "register" + (unlock ? "Unlock" : "Signing") + "Pin failed", err);
                doneEvent.setValue(new Event<>(
                        new PinDoneEvent(PinDoneEvent.Type.REGISTRATION_FAILED, null)));
            }
        }, mainExecutor);
    }

    private void advanceRegisterSign() {
        if (viaBio) {
            pendingPinForBio = pin;
            inFlight = true;
            emit();
            doneEvent.setValue(new Event<>(
                    new PinDoneEvent(PinDoneEvent.Type.REGISTER_SIGN_NEEDS_BIO, pin)));
            return;
        }
        inFlight = true;
        emit();
        final String entered = pin;
        registrationRepo.registerUser(false, entered)
                .whenCompleteAsync((v, err) -> {
                    inFlight = false;
                    if (err == null) {
                        doneEvent.setValue(new Event<>(
                                new PinDoneEvent(PinDoneEvent.Type.REGISTER_SIGN_DONE, null)));
                    } else {

                        error = true;
                        emit();
                    }
                }, mainExecutor);
    }

    public void onBioAuthSuccess() {
        String savedPin = pendingPinForBio;
        if (savedPin == null) return;
        pendingPinForBio = null;
        registrationRepo.registerUser(true, savedPin)
                .whenCompleteAsync((v, err) -> {
                    inFlight = false;
                    if (err == null) {
                        doneEvent.setValue(new Event<>(
                                new PinDoneEvent(PinDoneEvent.Type.REGISTER_SIGN_DONE, null)));
                    } else {
                        error = true;
                        emit();
                    }
                }, mainExecutor);
    }

    public void onBioAuthFailed() {
        pendingPinForBio = null;
        inFlight = false;
        error = true;
        emit();
    }

    public BiometricRepository getBioRepo() {
        return bioRepo;
    }

    private void emit() {
        state.setValue(new PinUiState(
                mode.titleRes(),
                mode.descRes(step),
                pin.length(),
                error,
                mode.errorRes(step),
                inFlight));
    }
}
