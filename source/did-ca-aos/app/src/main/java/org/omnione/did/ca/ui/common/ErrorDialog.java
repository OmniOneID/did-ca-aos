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
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.omnione.did.ca.R;

public class ErrorDialog extends Dialog implements View.OnClickListener {
    private ErrorDialogInterface errorDialogInterface;
    private Context context;
    private String title;
    private String message;
    private String input;
    private int type;
    private Button mDlgOkBtn;
    public String mBtnName;

    public interface ErrorDialogInterface {
        void okBtnClicked(String btnName);
    }

    public void setDialogListener(ErrorDialogInterface errorDialogInterface){
        this.errorDialogInterface = errorDialogInterface;
    }

    public ErrorDialog(Context context) {
        super(context);
    }
    public void setTitle(String title){
        this.title = title;
    }
    public void setMessage(String message){
        this.message = message;
    }
    public void setInput(String input){
        this.input = input;
    }
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        setContentView(R.layout.error_dialog);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView titleTextview = findViewById(R.id.titleTv);
        TextView messageTextview = findViewById(R.id.messageTv);
        String[] title = message.split("]");
        titleTextview.setText(title[0] + "]");
        if(title.length != 1) {
            messageTextview.setText(title[1]);
        } else {
            messageTextview.setText(message);
        }

        mDlgOkBtn = findViewById(R.id.okButton);

        mDlgOkBtn.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.okButton) {
            errorDialogInterface.okBtnClicked(mBtnName);
            dismiss();
        }
    }
}