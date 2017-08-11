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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class Utils {
    private static final SimpleDateFormat OTA_DATE = new SimpleDateFormat("yyyyMMdd-kkmm", Locale.US);

    private Utils() { }

    private static String sha256(String s) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(s.getBytes());

            return byteArrToStr(digest.digest());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static final HashMap<File, String> SHA256_FILE_CACHE = new HashMap<>();
    static String sha256(File f) {
        if (!f.exists()) return "";
        if (SHA256_FILE_CACHE.containsKey(f)) {
            String cachedsha256 = SHA256_FILE_CACHE.get(f);
            int cachedSHA256Split = cachedsha256.indexOf(':');
            long lastModified = Long.parseLong(cachedsha256.substring(cachedSHA256Split + 1));
            if (lastModified == f.lastModified()) {
                return cachedsha256.substring(0, cachedSHA256Split);
            } else {
                SHA256_FILE_CACHE.remove(f);
            }
        }

        InputStream in = null;
        try {
            in = new FileInputStream(f);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] buf = new byte[4096];
            int nRead;
            while ((nRead = in.read(buf)) != -1) {
                digest.update(buf, 0, nRead);
            }

            String sha256 = byteArrToStr(digest.digest());
            SHA256_FILE_CACHE.put(f, sha256 + ":" + Long.toString(f.lastModified()));
            return sha256;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try { in.close(); }
                catch (IOException ignored) { }
            }
        }
        return "";
    }

    public static boolean dataAvailable(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    public static boolean wifiConnected(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected() && ni.getType() == ConnectivityManager.TYPE_WIFI;
    }

    static Date parseDate(String date) {
        if (date == null) return null;
        try {
            return OTA_DATE.parse(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    static String formatDate(Date date) {
        if (date == null) return null;
        return OTA_DATE.format(date);
    }

    private static String device = null;
    static String getDevice() {
        if (device != null) return device;

        device = Build.DEVICE.toLowerCase(Locale.US);

        return device;
    }

    private static String deviceID = null;
    static String getRandomID() {

        if (deviceID != null) return deviceID;

        deviceID = "" + Math.random();
        deviceID = sha256(deviceID);

        return deviceID;
    }

    static String sanitizeName(String name) {
        if (name == null) return "";

        name = Normalizer.normalize(name, Normalizer.Form.NFD);
        name = name.trim();
        name = name.replaceAll("[^\\p{ASCII}]","");
        name = name.replaceAll("[ _-]+", "_");
        name = name.replaceAll("(^_|_$)", "");
        name = name.toLowerCase(Locale.US);

        return name;
    }

    private static final char[] HEX_DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
    private static String byteArrToStr(byte[] bytes) {
        StringBuilder str = new StringBuilder();
        for (byte b : bytes) {
            str.append(HEX_DIGITS[(0xF0 & b) >>> 4]);
            str.append(HEX_DIGITS[0xF & b]);
        }
        return str.toString();
    }
}
