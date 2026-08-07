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

import androidx.annotation.StringRes;

public final class PinUiState {

    @StringRes public final int titleRes;
    @StringRes public final int stepDescRes;
    public final int filled;
    public final boolean error;
    @StringRes public final int errorMessageRes;

    public final boolean inFlight;

    public PinUiState(@StringRes int titleRes,
                      @StringRes int stepDescRes,
                      int filled,
                      boolean error,
                      @StringRes int errorMessageRes,
                      boolean inFlight) {
        this.titleRes = titleRes;
        this.stepDescRes = stepDescRes;
        this.filled = filled;
        this.error = error;
        this.errorMessageRes = errorMessageRes;
        this.inFlight = inFlight;
    }
}
