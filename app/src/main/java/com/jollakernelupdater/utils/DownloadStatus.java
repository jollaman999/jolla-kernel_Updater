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

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;

import com.jollakernelupdater.R;

public class DownloadStatus {
    static final int ERROR_SHA256_MISMATCH = 900;

    private static final int SCALE_KBYTES = 1024;
    private static final int KBYTE_THRESH = 920; //0.9kb

    private static final int SCALE_MBYTES = 1048576;
    private static final int MBYTE_THRESH = 943718; //0.9mb

    private static final int SCALE_GBYTES = 1073741824;
    private static final int GBYTE_THRESH = 966367641; //0.9gb

    private final long ID;

    private int status;
    private int reason;
    private int totalBytes;
    private int downloadedBytes;

    protected BaseInfo info = null;

    private DownloadStatus(long id) {
        ID = id;
    }

    public static DownloadStatus forDownloadID(Context ctx, long id) {
        return forDownloadID(ctx, (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE), id);
    }

    public static DownloadStatus forDownloadID(Context ctx, DownloadManager dm, long id) {
        if (id > 0) {
            Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        DownloadStatus status = new DownloadStatus(id);

                        status.status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        status.reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                        status.totalBytes = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        status.downloadedBytes = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));

                        Config cfg = Config.getInstance(ctx);
                        if (cfg.isDownloadingKernel() && cfg.getKernelDownloadID() == id) {
                            status.info = cfg.getStoredKernelUpdate();
                        }

                        status.checkDownloadedFile();

                        return status;
                    }
                } finally {
                    c.close();
                }
            }
        }

        return null;
    }

    public long getId() {
        return ID;
    }

    public BaseInfo getInfo() {
        return info;
    }

    public int getStatus() {
        return status;
    }

    public boolean isSuccessful() {
        return status == DownloadManager.STATUS_SUCCESSFUL;
    }

    private void checkDownloadedFile() {
        if (status != DownloadManager.STATUS_SUCCESSFUL) return;
        if (info == null) return;

        int error = info.checkDownloadedFile();
        if (error == 0) return;

        status = DownloadManager.STATUS_FAILED;
        reason = error;
    }

    public int getReason() {
        return reason;
    }

    public int getTotalBytes() {
        return totalBytes;
    }

    public int getDownloadedBytes() {
        return downloadedBytes;
    }

    private double getDownloadedPercent() {
        if (totalBytes <= 0) return 0;
        return 100.0 * (double) downloadedBytes / (double) totalBytes;
    }

    public String getProgressStr(Context ctx) {
        int scaledDone = downloadedBytes;
        int scaledTotal = totalBytes;

        if (totalBytes == 0 || totalBytes == -1) {
            int bytesTxtRes = R.string.downloads_size_progress_unknown_b;
            return ctx.getString(bytesTxtRes, getDownloadedPercent(), scaledDone);
        } else {
            int bytesTxtRes = R.string.downloads_size_progress_b;
            if (totalBytes >= DownloadStatus.GBYTE_THRESH) {
                scaledDone /= DownloadStatus.SCALE_GBYTES;
                scaledTotal /= DownloadStatus.SCALE_GBYTES;
                bytesTxtRes = R.string.downloads_size_progress_gb;
            } else if (totalBytes >= DownloadStatus.MBYTE_THRESH) {
                scaledDone /= DownloadStatus.SCALE_MBYTES;
                scaledTotal /= DownloadStatus.SCALE_MBYTES;
                bytesTxtRes = R.string.downloads_size_progress_mb;
            } else if (totalBytes >= DownloadStatus.KBYTE_THRESH) {
                scaledDone /= DownloadStatus.SCALE_KBYTES;
                scaledTotal /= DownloadStatus.SCALE_KBYTES;
                bytesTxtRes = R.string.downloads_size_progress_kb;
            }
            return ctx.getString(bytesTxtRes, getDownloadedPercent(), scaledDone, scaledTotal);
        }
    }

    public String getErrorString(Context ctx) {
        int statusTextRes;

        switch (getReason()) {
        case DownloadManager.ERROR_CANNOT_RESUME:
            statusTextRes = R.string.downloads_failed_resume;
            break;
        case DownloadManager.ERROR_DEVICE_NOT_FOUND:
            statusTextRes = R.string.downloads_failed_mount;
            break;
        case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
            statusTextRes = R.string.downloads_failed_fileexists;
            break;
        case DownloadManager.ERROR_HTTP_DATA_ERROR:
        case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
        case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
            statusTextRes = R.string.downloads_failed_http;
            break;
        case DownloadManager.ERROR_INSUFFICIENT_SPACE:
            statusTextRes = R.string.downloads_failed_space;
            break;
        case DownloadManager.ERROR_FILE_ERROR:
        case DownloadManager.ERROR_UNKNOWN:
            statusTextRes = R.string.downloads_failed_unknown;
            break;
        case DownloadStatus.ERROR_SHA256_MISMATCH:
            statusTextRes = R.string.downloads_failed_sha256;
            break;
        default:
            statusTextRes = R.string.downloads_no_status;
        }

        return ctx.getString(statusTextRes);
    }
}
