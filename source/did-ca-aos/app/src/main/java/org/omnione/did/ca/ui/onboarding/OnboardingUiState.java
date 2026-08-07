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

import org.omnione.did.ca.data.model.OnboardingStep;

public final class OnboardingUiState {

    public final int step;
    public final int totalSteps;
    public final OnboardingStep current;

    public OnboardingUiState(int step, int totalSteps, OnboardingStep current) {
        this.step = step;
        this.totalSteps = totalSteps;
        this.current = current;
    }
}
