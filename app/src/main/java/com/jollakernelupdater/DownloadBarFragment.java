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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.jollakernelupdater.utils.BaseInfo;
import com.jollakernelupdater.utils.Config;
import com.jollakernelupdater.utils.DownloadDialogCallback;
import com.jollakernelupdater.utils.DownloadStatus;

public class DownloadBarFragment extends Fragment {

    private static DownloadBarFragment activeFragment = null;

    private Config cfg;
    private DownloadManager dm;

    private static View kernelContainer;
    private static ProgressBar kernelProgressBar;
    private static TextView kernelProgressText;
    private static TextView kernelStatusText;

    private static TextView progressText;
    private static TextView statusText;
    private static ProgressBar progressBar;

    static AlertDialog.Builder builder;
    static AlertDialog dlg;

    private static boolean is_download_end = false;

    private View bottomBorder;

    protected static final int REFRESH_DELAY = 1000;
    private static Handler REFRESH_HANDLER = new RefreshHandler();

    static Context RefreshHandler_ctx;
    static DownloadManager RefreshHandler_dm;
    static long RefreshHandler_downloadID;

    private static class RefreshHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            DownloadStatus status = DownloadStatus.forDownloadID(
                    RefreshHandler_ctx, RefreshHandler_dm, RefreshHandler_downloadID);

            if (status == null) {
                dlg.dismiss();
                return;
            }

            if (isActive(status)) {
                dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setText(android.R.string.cancel);
                progressBar.setVisibility(View.VISIBLE);

                if (status.getStatus() == DownloadManager.STATUS_PENDING) {
                    progressText.setVisibility(View.GONE);

                    progressBar.setIndeterminate(true);

                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText(R.string.downloads_starting);
                } else {
                    if (status.getStatus() == DownloadManager.STATUS_PAUSED) {
                        progressText.setVisibility(progressText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);

                        int pausedStatus = -1;
                        switch (status.getReason()) {
                            case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                                pausedStatus = R.string.downloads_paused_wifi;
                                break;
                            case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                                pausedStatus = R.string.downloads_paused_network;
                                break;
                            case DownloadManager.PAUSED_WAITING_TO_RETRY:
                                pausedStatus = R.string.downloads_paused_retry;
                                break;
                            case DownloadManager.PAUSED_UNKNOWN:
                                pausedStatus = R.string.downloads_paused_unknown;
                                break;
                        }

                        if (pausedStatus == -1) {
                            statusText.setVisibility(View.GONE);
                        } else {
                            statusText.setVisibility(View.VISIBLE);
                            statusText.setText(pausedStatus);
                        }
                    } else {
                        progressText.setVisibility(View.VISIBLE);

                        statusText.setVisibility(View.GONE);
                    }

                    progressText.setText(status.getProgressStr(RefreshHandler_ctx));

                    progressBar.setIndeterminate(false);
                    progressBar.setMax(status.getTotalBytes());
                    progressBar.setProgress(status.getDownloadedBytes());
                }
            } else {
                progressText.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                statusText.setVisibility(View.VISIBLE);

                if (status.isSuccessful()) {
                    dlg.setTitle(R.string.downloads_complete);
                    dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setText(R.string.flash);
                    statusText.setText(R.string.downloads_complete);
                } else {
                    dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setText(R.string.retry);
                    statusText.setText(status.getErrorString(RefreshHandler_ctx));
                }
            }

            this.sendMessageDelayed(this.obtainMessage(), DownloadBarFragment.REFRESH_DELAY);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        cfg = Config.getInstance(context);
        dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.download_bar, container, false);

        kernelContainer = view.findViewById(R.id.download_kernel_container);
        kernelProgressBar = view.findViewById(R.id.download_kernel_progress_bar);
        kernelProgressText = view.findViewById(R.id.download_kernel_progress_text);
        kernelStatusText = view.findViewById(R.id.download_kernel_status);
        View kernelCancelButton = view.findViewById(R.id.download_kernel_cancel);

        kernelContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!is_download_end) {
                    showDownloadingDialog(cfg.getKernelDownloadID());
                }
            }
        });

        kernelCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!cfg.isDownloadingKernel()) return;
                dm.remove(cfg.getKernelDownloadID());
                cfg.clearDownloadingKernel();
                updateStatus();
                REFRESH_HANDLER.removeCallbacksAndMessages(null);

                if (kernelContainer != null) kernelContainer.setVisibility(View.GONE);
                if (progressText != null) progressText.setVisibility(View.GONE);
                if (statusText != null) statusText.setVisibility(View.GONE);
                if (progressBar != null) progressBar.setVisibility(View.GONE);
            }
        });

        bottomBorder = view.findViewById(R.id.download_bottom_border);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateStatus();
        activeFragment = this;
    }

    @Override
    public void onPause() {
        activeFragment = null;
        REFRESH_HANDLER.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void updateStatus() {
        DownloadStatus kernelDlStatus = DownloadStatus.forDownloadID(getActivity(), dm, cfg.getKernelDownloadID());

        if (kernelDlStatus == null) cfg.clearDownloadingKernel();

        updateViews(kernelDlStatus, kernelContainer, kernelProgressBar, kernelProgressText, kernelStatusText);

        if (isActive(kernelDlStatus)) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateStatus();
                }
            }, REFRESH_DELAY);
            //REFRESH_HANDLER_2.sendMessageDelayed(REFRESH_HANDLER_2.obtainMessage(), REFRESH_DELAY);
        }

        boolean kernelVisible = kernelContainer.getVisibility() == View.VISIBLE;

        if (kernelVisible) {
            bottomBorder.setVisibility(View.VISIBLE);
        } else {
            bottomBorder.setVisibility(View.GONE);
        }
    }

    private void updateViews(final DownloadStatus status, final View container, final ProgressBar progressBar, final TextView progressText, final TextView statusText) {
        is_download_end = false;

        if (status == null) {
            container.setVisibility(View.GONE);
        } else {
            container.setVisibility(View.VISIBLE);

            if (isActive(status)) {
                progressBar.setVisibility(View.VISIBLE);

                if (status.getStatus() == DownloadManager.STATUS_PENDING) {
                    progressText.setVisibility(View.GONE);

                    progressBar.setIndeterminate(true);

                    statusText.setVisibility(View.VISIBLE);
                    statusText.setText(R.string.downloads_starting);
                } else if (status.getStatus() == DownloadManager.STATUS_RUNNING) {
                    statusText.setText(R.string.alert_downloading);
                } else {
                    if (status.getStatus() == DownloadManager.STATUS_PAUSED) {
                        progressText.setVisibility(progressText.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);

                        int pausedStatus = -1;
                        switch (status.getReason()) {
                        case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                            pausedStatus = R.string.downloads_paused_wifi;
                            break;
                        case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                            pausedStatus = R.string.downloads_paused_network;
                            break;
                        case DownloadManager.PAUSED_WAITING_TO_RETRY:
                            pausedStatus = R.string.downloads_paused_retry;
                            break;
                        case DownloadManager.PAUSED_UNKNOWN:
                            pausedStatus = R.string.downloads_paused_unknown;
                            break;
                        }

                        if (pausedStatus == -1) {
                            statusText.setVisibility(View.GONE);
                        } else {
                            statusText.setVisibility(View.VISIBLE);
                            statusText.setText(pausedStatus);
                        }
                    } else {
                        progressText.setVisibility(View.VISIBLE);

                        statusText.setVisibility(View.GONE);
                    }

                    progressText.setText(status.getProgressStr(getActivity()));

                    progressBar.setIndeterminate(false);
                    progressBar.setMax(status.getTotalBytes());
                    progressBar.setProgress(status.getDownloadedBytes());
                }
            } else {
                progressText.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                statusText.setVisibility(View.VISIBLE);

                if (status.getStatus() == DownloadManager.STATUS_SUCCESSFUL) {
                    statusText.setText(R.string.downloads_complete);
                    is_download_end = true;
                } else {
                    statusText.setText(status.getErrorString(getActivity()));
                }
            }
        }
    }

    private static boolean isActive(DownloadStatus state) {
        return state != null && (
                state.getStatus() == DownloadManager.STATUS_PAUSED ||
                state.getStatus() == DownloadManager.STATUS_RUNNING ||
                state.getStatus() == DownloadManager.STATUS_PENDING
        );
    }

    public static void notifyActiveFragment() {
        if (activeFragment != null) activeFragment.updateStatus();
    }

    private Dialog showDownloadingDialog(long downloadID) {
        return showDownloadingDialog(getActivity(), downloadID,
                getActivity() instanceof DownloadDialogCallback ? (DownloadDialogCallback) getActivity() : null);
    }

    public static Dialog showDownloadingDialog(final Context ctx, final long downloadID, final DownloadDialogCallback callback) {
        final DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);

        RefreshHandler_ctx = ctx;
        RefreshHandler_dm = dm;
        RefreshHandler_downloadID = downloadID;

        DownloadStatus initStatus = DownloadStatus.forDownloadID(ctx, dm, downloadID);
        if (initStatus == null) return null;

        LayoutInflater inflater = LayoutInflater.from(ctx);
        @SuppressLint("InflateParams")
        View view = inflater.inflate(R.layout.download_dialog, null);

        TextView titleView = view.findViewById(R.id.download_dlg_title);

        final BaseInfo info = initStatus.getInfo();
        titleView.setText(ctx.getString(info.getDownloadingTitle(), info.name, info.version));

        progressText = view.findViewById(R.id.download_dlg_progress_text);
        statusText = view.findViewById(R.id.download_dlg_status);
        progressBar = view.findViewById(R.id.download_dlg_progress_bar);

        DownloadStatus status = DownloadStatus.forDownloadID(ctx, dm, downloadID);
        builder = new AlertDialog.Builder(ctx);
        if (status != null) {
            if (status.getStatus() == DownloadManager.STATUS_SUCCESSFUL) {
                builder.setTitle(R.string.downloads_complete);
            } else {
                builder.setTitle(R.string.alert_downloading);
            }
        }
        builder.setView(view);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.hide, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DownloadStatus status = DownloadStatus.forDownloadID(ctx, dm, downloadID);
                if (status == null) return;

                if (status.getStatus() == DownloadManager.STATUS_RUNNING) {
                    Toast.makeText(ctx, R.string.toast_downloading, Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                activeFragment.cfg.clearDownloadingKernel();
                notifyActiveFragment();
                REFRESH_HANDLER.removeCallbacksAndMessages(null);
            }
        });

        dlg = builder.create();

        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                dlg.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        DownloadStatus status = DownloadStatus.forDownloadID(ctx, dm, downloadID);

                        if (isActive(status)) {
                            dlg.dismiss();
                            dm.remove(downloadID);
                        } else if (status != null) {
                            if (status.getStatus() == DownloadManager.STATUS_SUCCESSFUL) {
                                Intent i = new Intent(ctx, DownloadsActivity.class);
                                i.setAction(info.getFlashAction());
                                info.addToIntent(i);
                                ctx.startActivity(i);
                            } else if (status.getStatus() == DownloadManager.STATUS_FAILED) {
                                dlg.dismiss();
                                info.downloadFileDialog(ctx, callback);
                            }
                        }
                    }
                });

                REFRESH_HANDLER.sendMessage(REFRESH_HANDLER.obtainMessage());
                if (callback != null) {
                    callback.onDialogShown(dlg);
                    callback.onDownloadDialogShown(downloadID, dlg);
                }
            }
        });
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                REFRESH_HANDLER.removeCallbacksAndMessages(null);
                if (callback != null) {
                    callback.onDialogClosed(dlg);
                    callback.onDownloadDialogClosed(downloadID, dlg);
                }
            }
        });
        dlg.show();

        return dlg;
    }
}
