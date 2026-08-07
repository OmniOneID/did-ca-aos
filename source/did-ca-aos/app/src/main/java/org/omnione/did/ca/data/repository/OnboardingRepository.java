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

package org.omnione.did.ca.data.repository;

import org.omnione.did.ca.R;
import org.omnione.did.ca.data.model.OnboardingStep;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class OnboardingRepository {

    @Inject
    public OnboardingRepository() {}

    private final List<OnboardingStep> steps = Collections.unmodifiableList(Arrays.asList(
            new OnboardingStep(
                    1,
                    R.drawable.ic_user,
                    R.string.onboarding_step1_title,
                    Arrays.asList(
                            new OnboardingStep.Item(
                                    R.string.onboarding_step1_item1_title,
                                    R.string.onboarding_step1_item1_desc),
                            new OnboardingStep.Item(
                                    R.string.onboarding_step1_item2_title,
                                    R.string.onboarding_step1_item2_desc))),
            new OnboardingStep(
                    2,
                    R.drawable.ic_lock,
                    R.string.onboarding_step2_title,
                    Arrays.asList(
                            new OnboardingStep.Item(
                                    R.string.onboarding_step2_item1_title,
                                    R.string.onboarding_step2_item1_desc),
                            new OnboardingStep.Item(
                                    R.string.onboarding_step2_item2_title,
                                    R.string.onboarding_step2_item2_desc))),
            new OnboardingStep(
                    3,
                    R.drawable.ic_sign,
                    R.string.onboarding_step3_title,
                    Collections.singletonList(
                            new OnboardingStep.Item(
                                    R.string.onboarding_step3_item1_title,
                                    R.string.onboarding_step3_item1_desc)))
    ));

    public int totalSteps() {
        return steps.size();
    }

    public OnboardingStep getStep(int index) {
        if (index < 1 || index > steps.size()) {
            throw new IllegalArgumentException("step out of range: " + index);
        }
        return steps.get(index - 1);
    }

    public List<OnboardingStep> getAll() {
        return steps;
    }
}
