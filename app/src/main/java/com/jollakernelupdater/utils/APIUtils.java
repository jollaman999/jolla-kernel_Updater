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
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import com.jollakernelupdater.R;

import org.json.JSONObject;

import java.util.Locale;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.HttpStatus;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.methods.HttpPost;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.util.EntityUtils;

public class APIUtils {

    public static void fetchKernelInfo(Context ctx, APICallback callback) {
        if (!PropUtils.isKernelOtaEnabled()) {
            if (callback != null)
                callback.onError(ctx.getString(R.string.kernel_unsupported), null);
            return;
        }

        new APITask(ctx, Config.KERNEL_PULL_URL, callback).execute();
    }

    public static class APITask extends AsyncTask<Void, Void, Boolean> {
        private final Context ctx;
        private final String endpoint;
        private final APICallback callback;

        private String respMsg;
        private JSONObject respObj;

        APITask(Context ctx, String endpoint, APICallback callback) {
            this.ctx = ctx;
            this.endpoint = endpoint;
            this.callback = callback;
        }

        @Override
        protected void onPreExecute() {
            if (callback != null) callback.onStart(this);
        }

        @Override
        protected Boolean doInBackground(Void... unused) {
            if (!Utils.dataAvailable(ctx)) {
                respMsg = ctx.getString(R.string.alert_nodata_title);
                return false;
            }

            JSONObject resp = makeServerCall(endpoint);
            if (resp == null || resp.length() == 0) {
                respMsg = ctx.getString(R.string.unknown_error);
                return false;
            }

            respMsg = resp.optString("message", null);
            respObj = resp.optJSONObject("data");

            return resp.optBoolean("success", true);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (callback != null) {
                if (success) {
                    callback.onSuccess(respMsg, respObj);
                } else {
                    callback.onError(respMsg, respObj);
                }

                callback.onComplete(success);
            }
        }

        @Override
        protected void onCancelled(Boolean aVoid) {
            if (callback != null) callback.onCancel();
        }

        JSONObject makeServerCall(String endpoint) {
            endpoint += android.os.Build.DEVICE.toLowerCase(Locale.US) + ".json";
            Log.v(Config.LOG_TAG + "serverCall", endpoint);

            Log.v(Config.LOG_TAG + "serverCall", Config.SITE_BASE_URL + endpoint);

            try {
                HttpClient http = HttpClientBuilder.create().build();

                HttpPost req = new HttpPost(Config.SITE_BASE_URL + endpoint);

                HttpResponse resp = http.execute(req);

                int status = resp.getStatusLine().getStatusCode();
                if (status != HttpStatus.SC_OK) {
                    Log.w(Config.LOG_TAG + "serverCall", "non-200 response to " + endpoint + " (" + status + ")");
                }

                HttpEntity e = resp.getEntity();
                if (e == null) {
                    Log.w(Config.LOG_TAG + "serverCall", "no response to " + endpoint);
                    return null;
                }

                String respBody = EntityUtils.toString(e);
                if (respBody.length() == 0) {
                    Log.w(Config.LOG_TAG + "serverCall", "empty response to " + endpoint);
                    return null;
                }

                JSONObject json = new JSONObject(respBody);
                if (json.length() == 0) {
                    Log.w(Config.LOG_TAG + "serverCall", "empty response to " + endpoint);
                    return null;
                }

                if (!json.has("success")) {
                    Log.w(Config.LOG_TAG + "serverCall", "malformed response to " + endpoint);
                    return null;
                }

                if (!json.getBoolean("success")) {
                    Log.w(Config.LOG_TAG + "serverCall", "error received from " + endpoint + " (" + json.optString("error", "unknown") + ")");
                }

                return json;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    interface APICallback {
        void onStart(APITask task);

        void onSuccess(String message, JSONObject respObj);

        void onError(String message, JSONObject respObj);

        void onCancel();

        void onComplete(boolean success);
    }

    static abstract class APIAdapter implements APICallback {
        @Override
        public void onStart(APITask task) {
        }

        @Override
        public void onSuccess(String message, JSONObject respObj) {
        }

        @Override
        public void onError(String message, JSONObject respObj) {
        }

        @Override
        public void onCancel() {
        }

        @Override
        public void onComplete(boolean success) {
        }
    }
}