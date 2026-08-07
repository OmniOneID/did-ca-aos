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

package org.omnione.did.ca.ui.issue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

public abstract class IssueByQrEvent {

    private IssueByQrEvent() {}

    public static final class LaunchPin extends IssueByQrEvent {
        @NonNull public final String afterAuthTag;

        public LaunchPin(@NonNull String afterAuthTag) {
            this.afterAuthTag = afterAuthTag;
        }
    }

    public static final class LaunchBio extends IssueByQrEvent {
        public LaunchBio() {}
    }

    public static final class LaunchAuthMethodChooser extends IssueByQrEvent {
        @NonNull public final String afterAuthTag;

        public LaunchAuthMethodChooser(@NonNull String afterAuthTag) {
            this.afterAuthTag = afterAuthTag;
        }
    }

    public static final class ShowError extends IssueByQrEvent {
        @StringRes public final int titleRes;
        @StringRes public final int messageRes;
        @Nullable public final String detailMessage;

        public ShowError(@StringRes int titleRes, @StringRes int messageRes) {
            this(titleRes, messageRes, null);
        }

        public ShowError(@StringRes int titleRes, @StringRes int messageRes,
                         @Nullable String detailMessage) {
            this.titleRes = titleRes;
            this.messageRes = messageRes;
            this.detailMessage = detailMessage;
        }
    }
}
