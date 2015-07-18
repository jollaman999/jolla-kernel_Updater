/*
 * Copyright (C) 2014 OTA Update Center
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

package com.otaupdater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.otaupdater.utils.APIUtils;
import com.otaupdater.utils.BaseInfo;
import com.otaupdater.utils.Config;
import com.otaupdater.utils.KernelInfo;
import com.otaupdater.utils.PropUtils;
import com.otaupdater.utils.Utils;

public class CheckinReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        final Context context = ctx.getApplicationContext();
        final Config cfg = Config.getInstance(context);

        assert context != null;
        assert cfg != null;

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (cfg.hasStoredKernelUpdate()) {
                if (PropUtils.isKernelOtaEnabled()) {
                    KernelInfo info = cfg.getStoredKernelUpdate();
                    if (info.isUpdate()) {
                        if (cfg.getShowNotif()) {
                            info.showUpdateNotif(context);
                            Log.v(Config.LOG_TAG + "Receiver", "Found stored kernel update");
                        } else {
                            Log.v(Config.LOG_TAG + "Receiver", "Found stored kernel update, notif not shown");
                        }
                    } else {
                        Log.v(Config.LOG_TAG + "Receiver", "Found invalid stored kernel update");
                        cfg.clearStoredKernelUpdate();
                        KernelInfo.FACTORY.clearUpdateNotif(context);
                    }
                } else {
                    Log.v(Config.LOG_TAG + "Receiver", "Found stored kernel update, not OTA-kernel");
                    cfg.clearStoredKernelUpdate();
                }
            } else {
                Log.v(Config.LOG_TAG + "Receiver", "No stored kernel update");
            }

            setDailyAlarm(context);
        }
    }

    public static void setDailyAlarm(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, CheckinReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(ctx, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        am.cancel(pendingIntent);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + 86400000, AlarmManager.INTERVAL_DAY, pendingIntent);
    }
}
