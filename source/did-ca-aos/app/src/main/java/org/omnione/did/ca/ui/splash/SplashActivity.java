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
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.splashscreen.SplashScreenViewProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import org.omnione.did.ca.R;
import org.omnione.did.ca.databinding.ActivitySplashBinding;
import org.omnione.did.ca.ui.common.BaseActivity;
import org.omnione.did.ca.ui.common.NoticeDialogFragment;
import org.omnione.did.ca.util.IntentRouter;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SplashActivity extends BaseActivity {

    private static final long SYSTEM_SPLASH_FADE_MS = 200L;
    private static final long MIN_SPLASH_VISIBLE_MS = 1500L;

    private ActivitySplashBinding binding;
    private SplashViewModel viewModel;
    private long splashStartedAt;
    private ActivityResultLauncher<android.content.Intent> serverUrlLauncher;
    private SplashRoute pendingRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        splashStartedAt = SystemClock.uptimeMillis();
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        splashScreen.setOnExitAnimationListener(this::handOffSystemSplash);

        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        serverUrlLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> continueAfterServerPrompt(result.getResultCode() == RESULT_OK));

        NoticeDialogFragment.dismissRestored(getSupportFragmentManager());

        viewModel = new ViewModelProvider(this).get(SplashViewModel.class);
        viewModel.getRouteEvent().observe(this, event -> {
            SplashRoute route = event.getIfNotHandled();
            if (route != null) dispatchAfterMinimumVisible(route);
        });
        viewModel.bootstrap(ContextCompat.getMainExecutor(this));
    }

    private void dispatchAfterMinimumVisible(SplashRoute route) {
        long elapsed = SystemClock.uptimeMillis() - splashStartedAt;
        long remaining = Math.max(0L, MIN_SPLASH_VISIBLE_MS - elapsed);
        binding.getRoot().postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                pendingRoute = route;
                return;
            }
            dispatch(route);
        }, remaining);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SplashRoute route = pendingRoute;
        pendingRoute = null;
        if (route != null) dispatch(route);
    }

    private void handOffSystemSplash(SplashScreenViewProvider provider) {
        View splashView = provider.getView();
        alignIconToLogo(provider.getIconView());
        splashView.animate()
                .alpha(0f)
                .setDuration(SYSTEM_SPLASH_FADE_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(provider::remove)
                .start();
    }

    private void alignIconToLogo(View iconView) {
        View logo = binding.logoImage;
        if (iconView == null || iconView.getHeight() == 0 || logo.getHeight() == 0) return;

        int[] iconLocation = new int[2];
        iconView.getLocationOnScreen(iconLocation);
        int[] logoLocation = new int[2];
        logo.getLocationOnScreen(logoLocation);

        float iconCenterY = iconLocation[1] + iconView.getHeight() / 2f;
        float logoCenterY = logoLocation[1] + logo.getHeight() / 2f;
        iconView.setTranslationY(logoCenterY - iconCenterY);
    }

    private void dispatch(SplashRoute route) {
        switch (route) {
            case SERVER_SETUP:
                binding.spinner.setVisibility(View.GONE);
                NoticeDialogFragment.newInstance(
                                this,
                                R.string.server_settings,
                                R.string.server_setup_message,
                                R.string.server_setup_use_default)
                        .setOnConfirmed(this::continueAfterServerPrompt)
                        .setOnCancelled(this::continueAfterServerPrompt)
                        .setLink(R.string.server_settings,
                                () -> serverUrlLauncher.launch(
                                        IntentRouter.serverUrlIntent(this, true)))
                        .show(getSupportFragmentManager());
                break;
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
                showInitFailed(false);
                break;
            case ERROR_UNREACHABLE:
                showInitFailed(true);
                break;
        }
    }

    private void continueAfterServerPrompt() {
        continueAfterServerPrompt(false);
    }

    private void continueAfterServerPrompt(boolean serverChanged) {
        binding.spinner.setVisibility(View.VISIBLE);
        if (serverChanged) {
            viewModel.onServerChanged(ContextCompat.getMainExecutor(this));
        } else {
            viewModel.onServerPromptSettled(ContextCompat.getMainExecutor(this));
        }
    }

    private void showInitFailed(boolean unreachable) {
        binding.spinner.setVisibility(View.GONE);
        String detail = viewModel.getLastErrorDetail();
        CharSequence message = (!unreachable && detail != null && !detail.isEmpty())
                ? detail
                : getText(R.string.splash_init_failed_message);
        NoticeDialogFragment dialog = NoticeDialogFragment.newInstance(
                        getText(R.string.common_error), message, getText(R.string.common_ok))
                .setOnConfirmed(this::finish)
                .setOnCancelled(this::finish);
        if (unreachable) {
            dialog.setLink(R.string.server_settings,
                    () -> serverUrlLauncher.launch(IntentRouter.serverUrlIntent(this, false)));
        }
        dialog.show(getSupportFragmentManager());
    }
}
