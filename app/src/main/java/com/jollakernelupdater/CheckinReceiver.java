/*
 * Copyright (C) 2014 OTA Update Center
 * Copyright (C) 2017 jollaman999
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

package com.jollakernelupdater;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import com.jollakernelupdater.utils.APIUtils;
import com.jollakernelupdater.utils.BaseInfo;
import com.jollakernelupdater.utils.Config;
import com.jollakernelupdater.utils.KernelInfo;
import com.jollakernelupdater.utils.PropUtils;

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

        if (PropUtils.isKernelOtaEnabled()) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            final WakeLock kernelWL = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, CheckinReceiver.class.getName());
            kernelWL.acquire();

            APIUtils.fetchKernelInfo(context, new BaseInfo.InfoLoadAdapter<KernelInfo>(KernelInfo.class, context) {
                @Override
                public void onInfoLoaded(KernelInfo info) {
                    KernelTab.notifyActiveFragment();
                }

                @Override
                public void onComplete(boolean success) {
                    kernelWL.release();
                }
            });
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
