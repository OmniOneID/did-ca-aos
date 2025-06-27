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

package org.omnione.did.ca.zkp.referent;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.TextView;

import org.omnione.did.ca.R;

import java.util.ArrayList;

public class SelfAttrRefAdapter  extends BaseAdapter {

    private ArrayList<ReferentItem> referentItems = new ArrayList<ReferentItem>() ;

    public SelfAttrRefAdapter() {

    }

    @Override
    public int getCount() {
        return referentItems.size() ;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Context context = parent.getContext();

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_self_attr_ref, parent, false);
        }

        TextView textTextView = (TextView) convertView.findViewById(R.id.textView_self_attr_ref_item) ;
        EditText editText = (EditText) convertView.findViewById(R.id.editText_self_attr_ref_item);

        ReferentItem listViewItem = referentItems.get(position);

        textTextView.setText(listViewItem.getText());
        editText.setText(listViewItem.getTextValue());

        return convertView;
    }

    @Override
    public long getItemId(int position) {
        return position ;
    }

    @Override
    public Object getItem(int position) {
        return referentItems.get(position) ;
    }

    public void addItem(String data) {
        ReferentItem item = new ReferentItem();
        item.setText(data);
        referentItems.add(item);
    }
}
