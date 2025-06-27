/*
 * Copyright 2025 OmniOne.
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

package org.omnione.did.ca.zkp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.omnione.did.ca.R;
import org.omnione.did.ca.util.CaUtil;
import org.omnione.did.ca.zkp.referent.CredentialIdAdapter;
import org.omnione.did.sdk.datamodel.zkp.AttrReferent;
import org.omnione.did.sdk.datamodel.util.GsonWrapper;
import org.omnione.did.sdk.datamodel.zkp.CredentialDefinition;
import org.omnione.did.sdk.datamodel.zkp.CredentialSchema;
import org.omnione.did.sdk.datamodel.zkp.SubReferent;

import java.util.ArrayList;
import java.util.List;


public class ReferentListActivity extends AppCompatActivity {
    private ArrayList<String> credentialIdArrayList;
    private CredentialIdAdapter credentialIdAdapter;
    private ListView listView_value;
    private TextView textView_referentName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_referentlist);
        credentialIdArrayList = new ArrayList<>();

        textView_referentName = findViewById(R.id.textView_referentName);
        listView_value = findViewById(R.id.listView_value);

        final Intent getIntent = getIntent();
        String str_attrRef = getIntent.getStringExtra("referent");
        final int pos = getIntent.getIntExtra("pos", 0);

        AttrReferent attrReferent = GsonWrapper.getGson().fromJson(str_attrRef, AttrReferent.class);

        final List<SubReferent> attrSubReferentList = attrReferent.getAttrSubReferent();
        SubReferent subReferent = attrSubReferentList.get(0);

        CredentialDefinition credentialDefinitionForAttr = CaUtil.getCredentialDefinition(this.getApplicationContext(), subReferent.getCredentialDefId());
        CredentialSchema schema = CaUtil.getCredentialSchema(this, credentialDefinitionForAttr.getSchemaId());

        String caption = CaUtil.getAttributeCaptionValue(this, schema.getId(), attrReferent.getName());

        textView_referentName.setText(caption);

        for (int i = 0; i < attrSubReferentList.size(); i++) {
            String vcStatus = CaUtil.getVcMeta(this, attrSubReferentList.get(i).getCredentialId());
            if (vcStatus.equals("ACTIVE")) {
                credentialIdArrayList.add(CaUtil.extractSchemaName(attrSubReferentList.get(i).getCredentialDefId()) + "\n" + attrSubReferentList.get(i).getRaw());
            }
        }

        credentialIdAdapter = new CredentialIdAdapter();
        for (int i = 0; i < credentialIdArrayList.size(); i++) {
            credentialIdAdapter.addItem(credentialIdArrayList.get(i));
        }

        listView_value.setAdapter(credentialIdAdapter);
        Intent putIntent = new Intent();
        listView_value.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                final String selectedCredentialId =  attrSubReferentList.get(i).getCredentialId();
                final String selectedRaw =  attrSubReferentList.get(i).getRaw();

                putIntent.putExtra("credentialId", selectedCredentialId);
                putIntent.putExtra("raw", selectedRaw);
                putIntent.putExtra("pos", pos);

                credentialIdAdapter.setSelectedPosition(i);
                setResult(Activity.RESULT_OK, putIntent);
                finish();
            }
        });
    }
}