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

package org.omnione.did.ca.ui.splash;

import android.os.Bundle;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivitySplashBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SplashActivity extends BaseActivity {

    private ActivitySplashBinding binding;
    private SplashViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        viewModel = new ViewModelProvider(this).get(SplashViewModel.class);
        viewModel.getRouteEvent().observe(this, event -> {
            SplashRoute route = event.getIfNotHandled();
            if (route != null) dispatch(route);
        });
        viewModel.bootstrap(ContextCompat.getMainExecutor(this));
    }

    private void dispatch(SplashRoute route) {
        switch (route) {
            case ONBOARDING:
                IntentRouter.startOnboarding(this, 1);
                finish();
                break;
            case ONBOARDING_RESUME:
                IntentRouter.startOnboarding(this, 2);
                finish();
                break;
            case PIN_AUTH:
                IntentRouter.startPinAuth(this);
                finish();
                break;
            case DOCS:
                IntentRouter.startDocs(this);
                finish();
                break;
            case ERROR:
                NoticeDialogFragment.newInstance(
                                this,
                                R.string.splash_init_failed_title,
                                R.string.splash_init_failed_message,
                                R.string.common_ok)
                        .setOnConfirmed(this::finish)
                        .show(getSupportFragmentManager());
                break;
        }
    }
}
