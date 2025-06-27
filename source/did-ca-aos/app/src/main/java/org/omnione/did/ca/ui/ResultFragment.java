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

package org.omnione.did.ca.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import org.omnione.did.ca.R;
import org.omnione.did.ca.config.Constants;
import org.omnione.did.ca.config.Preference;

public class ResultFragment extends Fragment {
    NavController navController;
    Activity activity;
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        return inflater.inflate(R.layout.fragment_result, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        TextView resultTxt = view.findViewById(R.id.message);
        TextView nameView = view.findViewById(R.id.name);
        nameView.setText(Preference.getUsernameForDemo(activity));
        FrameLayout vc = view.findViewById(R.id.vc);
        FrameLayout vp = view.findViewById(R.id.check);
        if(requireArguments().getString("type").equals(Constants.TYPE_ISSUE)) {
            resultTxt.setText("You can now add your certificate");
            vc.setVisibility(View.VISIBLE);
            vp.setVisibility(View.GONE);
        } else {
            resultTxt.setText("You Successfully shared information with Opendid");
            vc.setVisibility(View.GONE);
            vp.setVisibility(View.VISIBLE);
        }

        Button button = (Button) view.findViewById(R.id.button);
        button.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
               navController.navigate(R.id.action_resultFragment_to_vcListFragment);
            }
        });
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }
}
