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

package org.omnione.did.ca.ui.vp;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.omnione.did.ca.R;

import java.util.ArrayList;

public class SelectAuthTypeListViewAdapter extends BaseAdapter {

    ArrayList<String> listview_data = new ArrayList<String>();

    @Override
    public int getCount() {
        return listview_data.size();
    }

    @Override
    public Object getItem(int position) {
        return listview_data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Context context = parent.getContext();

        if(convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.select_auth_type_listview_item, parent, false);
        }

        TextView item = (TextView) convertView.findViewById(R.id.type);

        String selectAuthTypeData = listview_data.get(position);

        item.setText(selectAuthTypeData);

        return convertView;
    }

    public void addItem(String type) {
        listview_data.add(type);
    }

    public void removeAll() {
        listview_data.clear();
    }
}
