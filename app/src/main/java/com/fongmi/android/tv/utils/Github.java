package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.setting.Setting;

public class Github {

    public static final String URL = Setting.getAcceleration() + "https://raw.githubusercontent.com/IsayIsee/TVBoxOS-Build/master";

    private static String getUrl(String name) {
        return URL + "/apk/" + name;
    }

    public static String getJson(String name) {
        return getUrl(name + ".json");
    }

    public static String getApk(String name) {
        return getUrl(name + ".apk");
    }
}
