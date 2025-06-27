/*
 * Copyright 2024-2025 OmniOne.
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

package org.omnione.did.ca.ui.common;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import org.omnione.did.ca.R;

public class ProgressCircle {
    private Dialog dialog;
    private ProgressBar progressBar;

    public ProgressCircle(Context context) {
        dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress_circle, null);
        progressBar = view.findViewById(R.id.progressBar);
        //progressBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#FF0000")));
        progressBar.setIndeterminate(true);
        progressBar.getIndeterminateDrawable().setColorFilter(Color.parseColor("#FF8400"), android.graphics.PorterDuff.Mode.MULTIPLY);
        dialog.setContentView(view);
        dialog.setCancelable(false);
    }

    public void show() {
        dialog.show();
    }
    public void dismiss() {
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }
}
