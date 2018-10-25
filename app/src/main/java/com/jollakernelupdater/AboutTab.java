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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.jollakernelupdater.utils.Config;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

public class AboutTab extends ListFragment {
    protected static final String KEY_TITLE = "title";
    protected static final String KEY_SUMMARY = "summary";

    private final ArrayList<HashMap<String, String>> DATA = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        HashMap<String, String> item;

        item = new HashMap<>();
        item.put(KEY_TITLE, getString(R.string.about_ota_title));
        item.put(KEY_SUMMARY, getString(R.string.about_ota_summary));
        DATA.add(item);

        item = new HashMap<>();
        item.put(KEY_TITLE, getString(R.string.about_version_title));
        item.put(KEY_SUMMARY, Config.VERSION);
        DATA.add(item);

        item = new HashMap<>();
        item.put(KEY_TITLE, getString(R.string.about_license_title));
        item.put(KEY_SUMMARY, "");
        DATA.add(item);

        item = new HashMap<>();
        item.put(KEY_TITLE, getString(R.string.about_contrib_title));
        item.put(KEY_SUMMARY, "");
        DATA.add(item);
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.list, container, false);
    }


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter(new SimpleAdapter(getActivity(),
                DATA,
                R.layout.two_line_icon_list_item,
                new String[] { KEY_TITLE, KEY_SUMMARY },
                new int[] { android.R.id.text1, android.R.id.text2 }));
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        switch (position) {
        case 0:
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Config.SITE_GITHUB_URL)));
            break;
        case 2:
            startActivity(new Intent(getActivity(), LicenseActivity.class));
            break;
        case 3:
            startActivity(new Intent(getActivity(), ContributorsActivity.class));
            break;
        }
    }
}
