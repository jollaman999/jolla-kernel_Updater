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

package com.jollakernelupdater.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

public class Config {

    public static final String VERSION = "1.2";

    public static final String LOG_TAG = "jolla::";

    public static final String HTTPC_UA = "jolla-kernel Updater App";

    public static final String SITE_BASE_URL = "https://www.otaupdatecenter.pro/";
    public static final String SITE_GITHUB_URL = "https://github.com/jollaman999";
    public static final String KERNEL_PULL_URL = "device/info/kernel";

    public static final int KERNEL_NOTIF_ID = 200;
    public static final int KERNEL_FAILED_NOTIF_ID = 201;
    public static final int KERNEL_FLASH_NOTIF_ID = 202;

    public static final String BASE_DL_PATH = PropUtils.getSystemSdPath();
    public static final String KERNEL_SD_PATH = "/jolla-kernel/";
    public static final String KERNEL_DL_PATH = BASE_DL_PATH + "/jolla-kernel/";

    public static final File DL_PATH_FILE = new File(BASE_DL_PATH);
    public static final File KERNEL_DL_PATH_FILE = new File(KERNEL_DL_PATH);

    static {
        //noinspection ResultOfMethodCallIgnored
        DL_PATH_FILE.mkdirs();
        //noinspection ResultOfMethodCallIgnored
        KERNEL_DL_PATH_FILE.mkdirs();
    }

    private boolean showNotif = true;
    private boolean wifiOnlyDl = true;
    private boolean autoDl = false;

    private boolean ignoredUnsupportedWarn = false;
    private boolean ignoredDataWarn = false;
    private boolean ignoredWifiWarn = false;

    private int lastVersion = -1;
    private String lastDevice = null;
    private String lastKernelID = null;

    private KernelInfo storedKernelUpdate = null;

    private long romDownloadID = -1;
    private long kernelDownloadID = -1;

    private static final String PREFS_NAME = "prefs";
    private final SharedPreferences PREFS;

    private Config(Context ctx) {
        ctx = ctx.getApplicationContext();
        assert ctx != null;
        PREFS = ctx.getSharedPreferences(PREFS_NAME, 0);

        showNotif = PREFS.getBoolean("showNotif", showNotif);
        wifiOnlyDl = PREFS.getBoolean("wifiOnlyDl", wifiOnlyDl);
        autoDl = PREFS.getBoolean("autoDl", autoDl);

        ignoredUnsupportedWarn = PREFS.getBoolean("ignoredUnsupportedWarn", ignoredUnsupportedWarn);
        ignoredDataWarn = PREFS.getBoolean("ignoredDataWarn", ignoredDataWarn);
        ignoredWifiWarn = PREFS.getBoolean("ignoredWifiWarn", ignoredWifiWarn);

        if (PREFS.contains("kernel_info_name")) {
            if (PropUtils.isKernelOtaEnabled()) {
                storedKernelUpdate = KernelInfo.FACTORY.fromSharedPrefs(PREFS);
            } else {
                clearStoredKernelUpdate();
            }
        }

        lastVersion  = PREFS.getInt("version", lastVersion);
        lastDevice   = PREFS.getString("device", lastDevice);
        lastKernelID = PREFS.getString("kernel_id", lastKernelID);

        kernelDownloadID = PREFS.getLong("kernelDownloadID", kernelDownloadID);
    }
    private static Config instance = null;
    public static synchronized Config getInstance(Context ctx) {
        if (instance == null) instance = new Config(ctx);
        return instance;
    }

    public boolean getShowNotif() {
        return showNotif;
    }

    public void setShowNotif(boolean showNotif) {
        this.showNotif = showNotif;
        putBoolean("showNotif", showNotif);
    }

    public boolean getWifiOnlyDl() {
        return wifiOnlyDl;
    }

    public void setWifiOnlyDl(boolean wifiOnlyDl) {
        this.wifiOnlyDl = wifiOnlyDl;
        putBoolean("wifiOnlyDl", wifiOnlyDl);
    }

    public boolean getAutoDlState() {
        return autoDl;
    }

    public void setAutoDlState(boolean autoDl) {
        this.autoDl = autoDl;
        putBoolean("autoDl", autoDl);
    }

    public void clearIgnored() {
        ignoredUnsupportedWarn = false;
        ignoredDataWarn = false;
        ignoredWifiWarn = false;

        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putBoolean("ignoredUnsupportedWarn", ignoredUnsupportedWarn);
            editor.putBoolean("ignoredDataWarn", ignoredDataWarn);
            editor.putBoolean("ignoredWifiWarn", ignoredWifiWarn);
            editor.apply();
        }
    }

    public boolean getIgnoredUnsupportedWarn() {
        return ignoredUnsupportedWarn;
    }

    public void setIgnoredUnsupportedWarn(boolean ignored) {
        this.ignoredUnsupportedWarn = ignored;
        putBoolean("ignoredUnsupportedWarn", ignored);
    }

    public boolean getIgnoredDataWarn() {
        return ignoredDataWarn;
    }

    public void setIgnoredDataWarn(boolean ignored) {
        this.ignoredDataWarn = ignored;
        putBoolean("ignoredDataWarn", ignored);
    }

    public boolean getIgnoredWifiWarn() {
        return ignoredWifiWarn;
    }

    public void setIgnoredWifiWarn(boolean ignored) {
        this.ignoredWifiWarn = ignored;
        putBoolean("ignoredWifiWarn", ignored);
    }

    public boolean hasStoredKernelUpdate() {
        return storedKernelUpdate != null;
    }

    public KernelInfo getStoredKernelUpdate() {
        return storedKernelUpdate;
    }

    public void storeKernelUpdate(KernelInfo info) {
        this.storedKernelUpdate = info;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            info.putToSharedPrefs(editor);
            editor.apply();
        }
    }

    public void clearStoredKernelUpdate() {
        storedKernelUpdate = null;
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            KernelInfo.FACTORY.clearFromSharedPrefs(editor);
            editor.apply();
        }
    }

    public boolean isDownloadingRom() {
        return romDownloadID != -1;
    }

    public void storeKernelDownloadID(long downloadID) {
        kernelDownloadID = downloadID;
        putLong("kernelDownloadID", kernelDownloadID);
    }

    public long getKernelDownloadID() {
        return kernelDownloadID;
    }

    public boolean isDownloadingKernel() {
        return kernelDownloadID != -1;
    }

    public void clearDownloadingKernel() {
        if (romDownloadID != -1) storeKernelDownloadID(-1);
    }

    public void storeDownloadID(BaseInfo info, long downloadID) {
        storeKernelDownloadID(downloadID);
    }

    public void storeUpdate(BaseInfo info) {
        storeKernelUpdate((KernelInfo) info);
    }

    public void clearStoredUpdate(Class<? extends BaseInfo> cls) {
        clearStoredKernelUpdate();
    }

    private void putBoolean(String name, boolean value) {
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putBoolean(name, value);
            editor.apply();
        }
    }

    private void putLong(String name, long value) {
        synchronized (PREFS) {
            SharedPreferences.Editor editor = PREFS.edit();
            editor.putLong(name, value);
            editor.apply();
        }
    }
}
