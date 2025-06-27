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

package org.omnione.did.ca.ui.vc;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.omnione.did.ca.R;
import org.omnione.did.ca.logger.CaLog;
import org.omnione.did.sdk.datamodel.vc.issue.VcStatus;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;


public class VcListItemView extends LinearLayout {

    TextView textView;
    TextView textView2;
    TextView textView3;
    TextView textVc;

    TextView textVcStatus;
    TextView textZkp;
    ImageView imageView;

    public VcListItemView(Context context) {
        super(context);
        init(context);
    }

    public VcListItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.vc_list_item, this, true);

        textView = (TextView) findViewById(R.id.textView);
        textView2 = (TextView) findViewById(R.id.textView2);
        textView3 = (TextView) findViewById(R.id.textView3);
        textView3 = (TextView) findViewById(R.id.textView3);
        textVc = (TextView) findViewById(R.id.textVc);
        textVcStatus = (TextView) findViewById(R.id.textVcStatus);
        textZkp = (TextView) findViewById(R.id.textZkp);
        imageView = (ImageView) findViewById(R.id.imageView);

    }

    public void setTitle(String title) {
        textView.setText(title);
    }

    public void setValidUntil(String validUntil) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date date = null;
        try {
            date = format.parse(validUntil);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        String strDateTime = format.format(date);
        textView2.setText("validUntil : " + strDateTime);
    }

    public void setIssuanceDate(String issuanceDate) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
        Date date = null;
        try {
            date = format.parse(issuanceDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        String strDateTime = format.format(date);
        textView3.setText("issuanceDate : " + strDateTime);
    }

    public void setIsZkp(boolean isZkp) {
        if (isZkp) {
            textZkp.setVisibility(VISIBLE);
        } else {
            textZkp.setVisibility(INVISIBLE);
        }
    }

    public void setVcStatus(String vcStatus) {

        if (vcStatus.equals("ACTIVE")) {
            textVcStatus.setText("ACTIVE");
        } else if (vcStatus.equals("INACTIVE")) {
            textVcStatus.setText("INACTIVE");
        } else {
            textVcStatus.setText("REVOKED");
        }
    }

    public void setImage(String img){
        imageView.setImageBitmap(BitmapFactory.decodeByteArray(Base64.decode(img.split(",")[1], Base64.DEFAULT), 0, Base64.decode(img.split(",")[1], Base64.DEFAULT).length));
    }

}
