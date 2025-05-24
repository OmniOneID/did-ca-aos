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
import android.widget.EditText;
import android.widget.TextView;


import org.omnione.did.ca.R;

import org.omnione.did.ca.config.Constants;

public class CustomDialog extends Dialog implements View.OnClickListener {
    private CustomDialogInterface customDialogInterface;
    private Context context;
    private String title;
    private String message;
    private String input;
    private int type;
    private EditText inputEditText;
    private Button mDlgYesBtn, mDlgNoBtn;
    public String mBtnName;

    public interface CustomDialogInterface{
        void yesBtnClicked(String btnName);
        void noBtnClicked(String btnName);
    }

    public void setDialogListener(CustomDialogInterface customDialogInterface){
        this.customDialogInterface = customDialogInterface;
    }

    public CustomDialog(Context context, int type) {
        super(context);
        this.type = type;
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

        setContentView(R.layout.custom_dialog);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        TextView messageTextview = findViewById(R.id.messageTv);
        messageTextview.setText(message);
        inputEditText = findViewById(R.id.inputEt);

        if(type == Constants.DIALOG_CONFIRM_TYPE){
            inputEditText.setVisibility(View.GONE);
        } else if(type == Constants.DIALOG_INPUT_TYPE){
            inputEditText.setVisibility(View.VISIBLE);
            inputEditText.setText(input);
        }

        mDlgYesBtn = findViewById(R.id.yesButton);
        mDlgNoBtn = findViewById(R.id.noButton);

        if(type == Constants.DIALOG_ERROR_TYPE){
            mDlgYesBtn.setVisibility(View.GONE);
            mDlgNoBtn.setVisibility(View.VISIBLE);
            mDlgNoBtn.setOnClickListener(this);
        }
        else {
            mDlgYesBtn.setVisibility(View.VISIBLE);
            mDlgNoBtn.setVisibility(View.VISIBLE);
            mDlgYesBtn.setOnClickListener(this);
            mDlgNoBtn.setOnClickListener(this);
        }

    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.yesButton) {
            customDialogInterface.yesBtnClicked(inputEditText.getText().toString());
            dismiss();
        }
        if(id == R.id.noButton) {
            customDialogInterface.noBtnClicked(mBtnName);
            dismiss();
        }
    }
}