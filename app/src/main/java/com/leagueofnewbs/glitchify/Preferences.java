package com.leagueofnewbs.glitchify;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XSharedPreferences;

class Preferences {
    private final ConcurrentHashMap<String, Object> pref = new ConcurrentHashMap<>();

    Preferences(XSharedPreferences prefs) {
        pref.put("ffzEmotes", prefs.getBoolean("ffz_emotes_enable", true));
        pref.put("ffzBadges", prefs.getBoolean("ffz_badges_enable", true));
        pref.put("ffzModBadge", prefs.getBoolean("ffz_mod_enable", true));
        pref.put("ffzModBadgeURL", "");
        pref.put("ffzModeBadgeScale", 1);
        pref.put("bttvEmotes", prefs.getBoolean("bttv_emotes_enable", true));
        pref.put("bttvBadges", prefs.getBoolean("bttv_badges_enable", true));
        pref.put("disableGifEmotes", prefs.getBoolean("disable_gif", false));
        pref.put("bitsCombine", prefs.getBoolean("bits_combine_enable", true));
        pref.put("hiddenBadges", new ArrayList<String>());
        String hiddenBadges = prefs.getString("badge_hiding_enable", "");
        if (!hiddenBadges.equals("")) {
            for (String key : hiddenBadges.split(",")) {
                //noinspection unchecked
                ((ArrayList<String>)pref.get("hiddenBadges")).add(key.trim());
            }
        }
        pref.put("preventChatClear", prefs.getBoolean("prevent_channel_clear", true));
        pref.put("showDeletedMessages", prefs.getBoolean("show_deleted_messages", true));
        pref.put("showTimeStamps", prefs.getBoolean("show_timestamps", true));
        pref.put("chatScrollbackLength", Integer.valueOf(prefs.getString("chat_scrollback_length", "100")));
        pref.put("colorAdjust", prefs.getBoolean("color_adjust", false));
        pref.put("hide_video_ads", prefs.getBoolean("hide_video_ads", false));

        XSharedPreferences darkPrefs = new XSharedPreferences("tv.twitch.android.app", "tv.twitch.android.app_preferences");
        pref.put("isDark", darkPrefs.getBoolean("dark_theme_enabled", false));
    }

    boolean ffzEmotes() {
        return (boolean) pref.get("ffzEmotes");
    }

    boolean ffzBadges() {
        return (boolean) pref.get("ffzBadges");
    }

    boolean ffzModBadge() {
        return (boolean) pref.get("ffzModBadge");
    }

    String ffzModBadgeURL() {
        return (String) pref.get("ffzModBadgeURL");
    }

    Integer ffzModBadgeScale() {
        return (Integer) pref.get("ffzModBadgeScale");
    }

    void ffzModBadgeScale(Integer scale) {
        pref.put("ffzModBadgeScale", scale);
    }

    void ffzModBadgeURL(String url) {
        pref.put("ffzModBadgeURL", url);
    }

    boolean bttvEmotes() {
        return (boolean) pref.get("bttvEmotes");
    }

    boolean bttvBadges() {
        return (boolean) pref.get("bttvBadges");
    }

    boolean disableGifEmotes() {
        return (boolean) pref.get("disableGifEmotes");
    }

    boolean bitsCombine() {
        return (boolean) pref.get("bitsCombine");
    }

    ArrayList<String> hiddenBadges() {
        //noinspection unchecked
        return (ArrayList<String>) pref.get("hiddenBadges");
    }

    boolean preventChatClear() {
        return (boolean) pref.get("preventChatClear");
    }

    boolean showDeletedMessages() {
        return (boolean) pref.get("showDeletedMessages");
    }

    boolean showTimeStamps() {
        return (boolean) pref.get("showTimeStamps");
    }

    Integer chatScrollbackLength() {
        return (Integer) pref.get("chatScrollbackLength");
    }

    boolean colorAdjust() {
        return (boolean) pref.get("colorAdjust");
    }

    boolean hideAds() {
        return (boolean) pref.get("hide_video_ads");
    }

    boolean darkMode() {
        return (boolean) pref.get("isDark");
    }

}