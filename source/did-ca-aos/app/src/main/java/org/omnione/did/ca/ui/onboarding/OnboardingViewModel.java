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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import org.omnione.did.ca.data.repository.OnboardingRepository;
import org.omnione.did.ca.data.repository.UserRegistrationRepository;
import org.omnione.did.ca.ui.Event;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public final class OnboardingViewModel extends ViewModel {

    private final OnboardingRepository repository;
    private final UserRegistrationRepository registrationRepo;
    private final MutableLiveData<OnboardingUiState> state = new MutableLiveData<>();
    private final MutableLiveData<Event<OnboardingEvent>> events = new MutableLiveData<>();
    private int currentStep = 1;
    private boolean preExecuteFired = false;

    private boolean viaBio = false;

    @Inject
    public OnboardingViewModel(OnboardingRepository repository,
                               UserRegistrationRepository registrationRepo) {
        this.repository = repository;
        this.registrationRepo = registrationRepo;
    }

    public LiveData<OnboardingUiState> getState() {
        return state;
    }

    public LiveData<Event<OnboardingEvent>> getEvents() {
        return events;
    }

    public void setStep(int step) {
        int total = repository.totalSteps();
        currentStep = clamp(step, 1, total);
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
            events.setValue(new Event<>(OnboardingEvent.SHOW_LOCK_DIALOG));
        } else if (currentStep < total) {
            events.setValue(new Event<>(OnboardingEvent.GO_PIN_REGISTER));
        } else {

            events.setValue(new Event<>(OnboardingEvent.GO_PIN_REGISTER_SIGN));
        }
    }

    public void advanceFromLockDialog() {
        setStep(currentStep + 1);
    }

    public void firePreExecute() {
        if (preExecuteFired) return;
        preExecuteFired = true;
        registrationRepo.preExecute();

    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
