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

package org.omnione.did.ca.data.repository;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.omnione.did.ca.util.AppLog;
import org.omnione.did.ca.util.IntentRouter;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public final class AppResetRepository {

    private static final String TAG = "AppResetRepo";

    private static final long RESTART_DELAY_MS = 500L;
    private static final int RESTART_REQUEST_CODE = 4619;

    private final Context appContext;

    @Inject
    public AppResetRepository(@ApplicationContext Context appContext) {
        this.appContext = appContext;
    }

    public boolean clearAppData() {
        ActivityManager am =
                (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            AppLog.e(TAG, "ActivityManager unavailable; cannot clear app data");
            return false;
        }
        scheduleRestart();
        boolean scheduled = am.clearApplicationUserData();
        if (!scheduled) {
            cancelRestart();
            AppLog.e(TAG, "clearApplicationUserData() refused; restart cancelled");
        }
        return scheduled;
    }

    private void scheduleRestart() {
        AlarmManager alarm = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) {
            AppLog.e(TAG, "AlarmManager unavailable; app will not auto-restart");
            return;
        }
        alarm.set(AlarmManager.RTC, System.currentTimeMillis() + RESTART_DELAY_MS,
                restartPendingIntent(PendingIntent.FLAG_CANCEL_CURRENT));
    }

    private void cancelRestart() {
        PendingIntent pi = restartPendingIntent(PendingIntent.FLAG_NO_CREATE);
        if (pi == null) return;
        AlarmManager alarm = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pi);
        pi.cancel();
    }

    private PendingIntent restartPendingIntent(int extraFlags) {
        Intent intent = IntentRouter.splashClearTaskIntent(appContext);
        return PendingIntent.getActivity(appContext, RESTART_REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE | extraFlags);
    }
}
