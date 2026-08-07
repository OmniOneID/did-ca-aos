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

package org.omnione.did.ca.ui.dialog;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import org.omnione.did.ca.R;
import org.omnione.did.ca.ui.docs.DocsActivity;

import java.util.Arrays;
import java.util.List;

public class AddBottomSheetDialogFragment extends ActionBottomSheetDialogFragment {

    public static final String TAG = "AddBottomSheetDialogFragment";

    @Override
    protected int getTitleRes() {
        return R.string.add_sheet_title;
    }

    @Override
    protected int getDescriptionRes() {
        return R.string.add_sheet_description;
    }

    @NonNull
    @Override
    protected List<BottomSheetAction> buildActions() {
        return Arrays.asList(
                new BottomSheetAction(
                        R.drawable.ic_list,
                        R.string.add_sheet_from_list,
                        v -> docs().onAddFromList()),
                new BottomSheetAction(
                        R.drawable.ic_qr,
                        R.string.add_sheet_scan_qr,
                        v -> docs().onAddScanQr()));
    }

    private DocsActivity docs() {
        FragmentActivity host = requireActivity();
        return (DocsActivity) host;
    }
}
