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

package org.omnione.did.ca.ui.onboarding;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.data.network.CommunicationErrors;
import org.omnione.did.ca.data.repository.OnboardingRepository;
import org.omnione.did.ca.data.repository.UserRegistrationRepository;
import org.omnione.did.ca.data.repository.WalletStateRepository;
import org.omnione.did.ca.ui.Event;
import org.omnione.did.ca.util.AppLog;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class OnboardingViewModel extends ViewModel {

    private final OnboardingRepository repository;
    private final UserRegistrationRepository registrationRepo;
    private final WalletStateRepository walletStateRepository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean lockCheckPending = false;
    private final MutableLiveData<OnboardingUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<OnboardingEvent>> events = new MutableLiveData<>();
    private final MutableLiveData<Boolean> inFlight = new MutableLiveData<>(false);
    private int currentStep = 1;
    private volatile boolean preExecuteFired = false;
    private boolean lockDecided = false;
    private volatile boolean lastErrorUnreachable = false;
    private volatile String lastErrorDetail = null;

    private boolean viaBio = false;

    @Inject
    public OnboardingViewModel(OnboardingRepository repository,
                               UserRegistrationRepository registrationRepo,
                               WalletStateRepository walletStateRepository) {
        this.repository = repository;
        this.registrationRepo = registrationRepo;
        this.walletStateRepository = walletStateRepository;
    }

    public LiveData<OnboardingUiState> getState() {
        return state;
    }

    public LiveData<Event<OnboardingEvent>> getEvents() {
        return events;
    }

    public LiveData<Boolean> getInFlight() {
        return inFlight;
    }

    public void setStep(int step) {
        int total = repository.totalSteps();
        currentStep = registrationRepo.hasRegisteredUser() ? clamp(step, 1, total) : 1;
        if (currentStep > 1) lockDecided = true;
        state.setValue(new OnboardingUiState(currentStep, total, repository.getStep(currentStep)));
    }

    public void setViaBio(boolean viaBio) {
        this.viaBio = viaBio;
    }

    public boolean isViaBio() {
        return viaBio;
    }

    public void onNext() {
        int total = repository.totalSteps();
        if (currentStep == 1) {
            if (registrationRepo.hasRegisteredUser()) {
                continueAfterRegistration();
            } else {
                events.setValue(new Event<>(OnboardingEvent.OPEN_USER_INFO_WEB));
            }
        } else if (currentStep < total) {
            if (registrationRepo.isPreExecuteReady()) {
                events.setValue(new Event<>(OnboardingEvent.GO_PIN_REGISTER));
            } else {
                inFlight.setValue(true);
                firePreExecute();
            }
        } else {

            events.setValue(new Event<>(OnboardingEvent.GO_PIN_REGISTER_SIGN));
        }
    }

    public String userInfoUrl() {
        return registrationRepo.userInfoUrl();
    }

    public void onUserRegistered(String userName, String userId) {
        registrationRepo.saveRegisteredUser(userName, userId);
        continueAfterRegistration();
    }

    private void continueAfterRegistration() {
        if (lockDecided) {
            advanceFromLockDialog();
            return;
        }
        if (lockCheckPending) return;
        lockCheckPending = true;
        walletStateRepository.isLocked().whenCompleteAsync((locked, err) -> {
            lockCheckPending = false;
            if (err != null) AppLog.e("Onboarding", "isLocked failed", err);
            if (Boolean.TRUE.equals(locked)) {
                advanceFromLockDialog();
            } else {
                events.setValue(new Event<>(OnboardingEvent.SHOW_LOCK_DIALOG));
            }
        }, mainHandler::post);
    }

    public void onLockAccepted() {
        lockDecided = true;
    }

    public void advanceFromLockDialog() {
        setStep(currentStep + 1);
    }

    public void firePreExecute() {
        if (preExecuteFired) return;
        preExecuteFired = true;
        registrationRepo.preExecute().whenCompleteAsync((ignored, err) -> {
            if (err == null) {
                lastErrorUnreachable = false;
                lastErrorDetail = null;
                preExecuteFired = false;
                if (Boolean.TRUE.equals(inFlight.getValue())) {
                    inFlight.setValue(false);
                    events.setValue(new Event<>(OnboardingEvent.GO_PIN_REGISTER));
                }
                return;
            }
            preExecuteFired = false;
            inFlight.setValue(false);
            lastErrorUnreachable = CommunicationErrors.isUnreachable(err);
            lastErrorDetail = detailOf(err);
            events.setValue(new Event<>(OnboardingEvent.SHOW_SERVER_ERROR));
        }, mainHandler::post);
    }

    public boolean isLastErrorUnreachable() {
        return lastErrorUnreachable;
    }

    public String getLastErrorDetail() {
        return lastErrorDetail;
    }

    public void resetRegistrationForServerChange() {
        registrationRepo.resetForServerChange();
        preExecuteFired = false;
        lastErrorUnreachable = false;
        inFlight.setValue(false);
        setStep(1);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String detailOf(Throwable err) {
        String detail = CommunicationErrors.findDetailMessage(err);
        return detail != null ? detail : CommunicationErrors.findSdkMessage(err);
    }
}
