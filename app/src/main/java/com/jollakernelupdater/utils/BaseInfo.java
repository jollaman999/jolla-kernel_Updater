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

package com.jollakernelupdater.utils;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.jollakernelupdater.DownloadBarFragment;
import com.jollakernelupdater.DownloadReceiver;
import com.jollakernelupdater.R;
import com.jollakernelupdater.jollakernelUpdaterActivity;

import org.json.JSONObject;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.Locale;

public abstract class BaseInfo implements Parcelable, Serializable {
    private static final long serialVersionUID = 7138464743643950748L;

    private static final String KEY_NAME = "name";
    private static final String KEY_VERSION = "version";
    private static final String KEY_URL = "url";
    private static final String KEY_SHA256 = "sha256";
    private static final String KEY_DATE = "date";

    public String name;
    public String version;
    public String url;
    public String sha256;
    public Date date;

    BaseInfo() {
    }

    public void addToIntent(Intent i) {
        i.putExtra(getNameKey(), name);
        i.putExtra(KEY_VERSION, version);
        i.putExtra(KEY_URL, url);
        i.putExtra(KEY_SHA256, sha256);
        i.putExtra(KEY_DATE, Utils.formatDate(date));
    }

    void putToSharedPrefs(SharedPreferences.Editor editor) {
        editor.putString(getNameKey() + "_info_" + KEY_NAME, name);
        editor.putString(getNameKey() + "_info_" + KEY_VERSION, version);
        editor.putString(getNameKey() + "_info_" + KEY_URL, url);
        editor.putString(getNameKey() + "_info_" + KEY_SHA256, sha256);
        editor.putString(getNameKey() + "_info_" + KEY_DATE, Utils.formatDate(date));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(version);
        dest.writeString(url);
        dest.writeString(sha256);
        dest.writeLong(date.getTime());
    }

    public boolean isUpdate() {
        if (date != null) {
            Date propDate = getPropDate();
            if (propDate == null) return true;
            if (date.after(propDate)) return true;
        } else if (version != null) {
            String propVersion = getPropVersion();
            if (propVersion == null) return true;
            if (!version.equalsIgnoreCase(propVersion)) return true;
        }
        return false;
    }

    public void showUpdateNotif(Context ctx) {
        if (isDownloading(ctx)) return;

        Intent mainIntent = new Intent(ctx, jollakernelUpdaterActivity.class);
        mainIntent.setAction(getNotifAction());
        this.addToIntent(mainIntent);
        PendingIntent mainPIntent = PendingIntent.getActivity(ctx, 0, mainIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        Intent dlInent = new Intent(ctx, DownloadReceiver.class);
        dlInent.setAction(getDownloadAction());
        this.addToIntent(dlInent);
        PendingIntent dlPIntent = PendingIntent.getBroadcast(ctx, 0, dlInent, PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx);
        builder.setContentIntent(mainPIntent);
        builder.setContentTitle(ctx.getString(R.string.notif_source));
        builder.setContentText(ctx.getString(getNotifTickerStr()));
        builder.setTicker(ctx.getString(getNotifTextStr(), version));
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.ic_stat_system_update);
        builder.setStyle(new NotificationCompat.BigTextStyle().bigText(ctx.getString(getNotifDetailsStr(), version)));
        builder.setPriority(NotificationCompat.PRIORITY_LOW);
        builder.addAction(R.drawable.ic_action_av_download, ctx.getString(R.string.download), dlPIntent);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(getNotifID(), builder.build());
    }

    public void clearUpdateNotif(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(getNotifID());
    }

    public long startDownload(Context ctx) {
        Config cfg = Config.getInstance(ctx);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.addRequestHeader("User-Agent", Config.HTTPC_UA);
        request.setTitle(ctx.getString(getDownloadingNotifTitle()));
        request.setDestinationUri(getDownloadFileUri());
        request.setAllowedOverRoaming(false);
        request.setVisibleInDownloadsUi(false);

        int allowedNetworks = DownloadManager.Request.NETWORK_WIFI;
        if (!cfg.getWifiOnlyDl()) allowedNetworks |= DownloadManager.Request.NETWORK_MOBILE;
        request.setAllowedNetworkTypes(allowedNetworks);

        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        long downloadID = dm.enqueue(request);
        cfg.storeDownloadID(downloadID);
        DownloadBarFragment.notifyActiveFragment();
        clearUpdateNotif(ctx);

        return downloadID;
    }

    public void downloadFileDialog(final Context ctx, final DownloadDialogCallback callback) {
        DownloadBarFragment.showDownloadingDialog(ctx, startDownload(ctx), callback);
    }

    public String getDownloadFileName() {
        return Utils.sanitizeName(name) + "__" + Utils.sanitizeName(version) + ".zip";
    }

    private File getDownloadFile() {
        return new File(getDownloadPathFile(), getDownloadFileName());
    }

    public int checkDownloadedFile() {
        File file = getDownloadFile();
        if (!file.exists()) return DownloadManager.ERROR_FILE_ERROR;
        if (!Utils.sha256(file).equalsIgnoreCase(sha256)) return DownloadStatus.ERROR_SHA256_MISMATCH;
        return 0;
    }

    private Uri getDownloadFileUri() {
        return Uri.parse("file://" + getDownloadFile().getAbsolutePath());
    }

    public String getRecoveryFilePath() {
        return PropUtils.getRecoverySdPath() + getDownloadSdPath() + getDownloadFileName();
    }

    public void showUpdateDialog(final Context ctx, final DownloadDialogCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(R.string.alert_update_title);
        builder.setMessage(ctx.getString(getDownloadDialogMessageStr(), name, version));

        builder.setPositiveButton(R.string.download, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.dismiss();
                downloadFileDialog(ctx, callback);
            }
        });

        builder.setNeutralButton(R.string.alert_changelog, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                String strLanguage = Locale.getDefault().getLanguage();
                if (strLanguage.equals("ko")) {
                    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.SITE_CHANGELOG_URL_KO)));
                } else {
                    ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.SITE_CHANGELOG_URL_EN)));
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        final AlertDialog dlg = builder.create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                if (callback != null) callback.onDialogShown(dlg);
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (callback != null) callback.onDialogClosed(dlg);
            }
        });
        dlg.show();
    }

    public abstract String getFlashAction();

    public abstract String getNotifAction();

    public abstract int getDownloadingTitle();

    public abstract int getDownloadFailedTitle();

    public abstract int getDownloadDoneTitle();

    public abstract int getFailedNotifID();

    public abstract int getFlashNotifID();

    protected abstract String getNameKey();

    public abstract String getDownloadAction();

    protected abstract String getDownloadSdPath();

    protected abstract File getDownloadPathFile();

    protected abstract int getNotifTickerStr();

    protected abstract int getNotifTextStr();

    protected abstract int getNotifDetailsStr();

    protected abstract int getNotifID();

    protected abstract int getDownloadingNotifTitle();

    protected abstract int getDownloadDialogMessageStr();

    protected abstract boolean isDownloading(Context ctx);

    protected abstract Date getPropDate();

    protected abstract String getPropVersion();

    public static class InfoFactory<T extends BaseInfo> {
        private final Class<T> CLASS;

        InfoFactory(Class<T> cls) {
            this.CLASS = cls;
        }

        T fromJSON(JSONObject json) {
            if (json == null || json.length() == 0 || json.has("error")) return null;

            try {
                T info = CLASS.newInstance();

                info.name = json.getString(info.getNameKey());
                info.version = json.getString(KEY_VERSION);
                info.url = json.getString(KEY_URL);
                info.sha256 = json.getString(KEY_SHA256);
                info.date = Utils.parseDate(json.getString(KEY_DATE));

                return info;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        T fromBundle(Bundle bundle) {
            if (bundle == null || bundle.isEmpty()) return null;

            try {
                T info = CLASS.newInstance();

                info.name = bundle.getString(info.getNameKey());
                info.version = bundle.getString(KEY_VERSION);
                info.url = bundle.getString(KEY_URL);
                info.sha256 = bundle.getString(KEY_SHA256);
                info.date = Utils.parseDate(bundle.getString(KEY_DATE));

                return info;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        public T fromIntent(Intent i) {
            return fromBundle(i.getExtras());
        }

        T fromSharedPrefs(SharedPreferences prefs) {
            try {
                T info = CLASS.newInstance();

                info.name = prefs.getString(info.getNameKey() + "_info_" + KEY_NAME, null);
                info.version = prefs.getString(info.getNameKey() + "_info_" + KEY_VERSION, null);
                info.url = prefs.getString(info.getNameKey() + "_info_" + KEY_URL, null);
                info.sha256 = prefs.getString(info.getNameKey() + "_info_" + KEY_SHA256, null);
                info.date = Utils.parseDate(prefs.getString(info.getNameKey() + "_info_" + KEY_DATE, null));

                return info;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        Creator<T> getParcelableCreator() {
            return new Creator<T>() {
                @Override
                @SuppressWarnings("unchecked")
                public T[] newArray(int size) {
                    return (T[]) new BaseInfo[size];
                }

                @Override
                public T createFromParcel(Parcel source) {
                    try {
                        T info = CLASS.newInstance();

                        info.name = source.readString();
                        info.version = source.readString();
                        info.url = source.readString();
                        info.sha256 = source.readString();
                        info.date = new Date(source.readLong());

                        return info;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return null;
                }
            };
        }

        public void clearUpdateNotif(Context ctx) {
            try {
                CLASS.newInstance().clearUpdateNotif(ctx);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        void clearFromSharedPrefs(SharedPreferences.Editor editor) {
            try {
                T info = CLASS.newInstance();

                editor.remove(info.getNameKey() + "_info_" + KEY_NAME);
                editor.remove(info.getNameKey() + "_info_" + KEY_VERSION);
                editor.remove(info.getNameKey() + "_info_" + KEY_URL);
                editor.remove(info.getNameKey() + "_info_" + KEY_SHA256);
                editor.remove(info.getNameKey() + "_info_" + KEY_DATE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static abstract class InfoLoadAdapter<T extends BaseInfo> extends APIUtils.APIAdapter {
        private final Class<T> CLASS;
        private final Context ctx;
        private final Config cfg;

        protected InfoLoadAdapter(Class<T> cls, Context ctx) {
            this.CLASS = cls;
            this.ctx = ctx;
            this.cfg = Config.getInstance(ctx);
        }

        public abstract void onInfoLoaded(T info);

        @Override
        public void onSuccess(String message, JSONObject respObj) {
            InfoFactory<T> factory = new InfoFactory<>(CLASS);
            T info = factory.fromJSON(respObj);

            if (info != null && info.isUpdate()) {
                cfg.storeUpdate(info);
                if (cfg.getShowNotif()) {
                    info.showUpdateNotif(ctx);
                } else {
                    Log.v(Config.LOG_TAG + "InfoLoad", "found " + info.getNameKey() + " update, notif not shown");
                }
            } else {
                cfg.clearStoredUpdate();
                factory.clearUpdateNotif(ctx);
            }

            onInfoLoaded(info);
        }
    }
}
