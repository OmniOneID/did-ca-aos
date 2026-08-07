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

package org.omnione.did.ca.ui.issuebyoid4vci;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class IssueByOid4vciEvent {

    private IssueByOid4vciEvent() {}

    public static final class LaunchTxCode extends IssueByOid4vciEvent {
        public final int length;
        @NonNull public final String inputMode;
        @Nullable public final String description;

        public LaunchTxCode(int length, @NonNull String inputMode, @Nullable String description) {
            this.length = length;
            this.inputMode = inputMode;
            this.description = description;
        }
    }

    public static final class LaunchPin extends IssueByOid4vciEvent {
        @NonNull public final String afterAuthTag;

        public LaunchPin(@NonNull String afterAuthTag) {
            this.afterAuthTag = afterAuthTag;
        }
    }

    public static final class LaunchBio extends IssueByOid4vciEvent {
        public LaunchBio() {}
    }

    public static final class LaunchAuthMethodChooser extends IssueByOid4vciEvent {
        @NonNull public final String afterAuthTag;

        public LaunchAuthMethodChooser(@NonNull String afterAuthTag) {
            this.afterAuthTag = afterAuthTag;
        }
    }

    public static final class ShowError extends IssueByOid4vciEvent {
        public final int titleRes;
        public final int messageRes;
        @Nullable public final String detailMessage;

        public ShowError(int titleRes, int messageRes, @Nullable String detailMessage) {
            this.titleRes = titleRes;
            this.messageRes = messageRes;
            this.detailMessage = detailMessage;
        }
    }
}
