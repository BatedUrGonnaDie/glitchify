package com.leagueofnewbs.glitchify;

import static de.robv.android.xposed.XposedHelpers.*;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;

import javax.net.ssl.HttpsURLConnection;

public class Main implements IXposedHookLoadPackage {

    private Hashtable<String, String> ffzRoomEmotes = new Hashtable<>();
    private Hashtable<String, String> ffzGlobalEmotes = new Hashtable<>();
    private Hashtable<String, Hashtable<String, Object>> ffzBadges = new Hashtable<>();
    private Hashtable<String, String> bttvRoomEmotes = new Hashtable<>();
    private Hashtable<String, String> bttvGlobalEmotes = new Hashtable<>();
    private Hashtable<String, Hashtable<String, Object>> bttvBadges = new Hashtable<>();
    private Hashtable<String, String> hiddenBadges = new Hashtable<>();
    public static String ffzAPIURL = "https://api.frankerfacez.com/v1/";
    public static String bttvAPIURL = "https://api.betterttv.net/2/";
    private HashMap<Integer, Object> twitchBadgeHash = null;
    private HashMap<Integer, Object> twitchEmoteHash = null;
    private HashMap<Integer, Object> twitchMentionHash = null;
    private HashMap<Integer, Object> twitchLinkHash = null;
    private HashMap<Integer, Object> twitchBitsHash = null;
    private String chatSender;

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("tv.twitch.android.app")) {
            return;
        }

        XSharedPreferences pref = new XSharedPreferences(Main.class.getPackage().getName(), "preferences");
        pref.makeWorldReadable();
        pref.reload();
        final boolean prefFFZEmotes  = pref.getBoolean("ffz_emotes_enable", true);
        final boolean prefFFZBadges  = pref.getBoolean("ffz_badges_enable", true);
        final boolean prefBTTVEmotes = pref.getBoolean("bttv_emotes_enable", true);
        final boolean prefBTTVBadges = pref.getBoolean("bttv_badges_enable", true);
        final String prefBadgeHiding = pref.getString("badge_hiding_enable", "");
        for (Object key : prefBadgeHiding.split(",")) {
            hiddenBadges.put(((String) key).trim(), "");
        }

        Thread globalThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (prefFFZEmotes) {
                        getFFZGlobalEmotes();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global FFZ emotes > ");
                }
                try {
                    if (prefBTTVEmotes) {
                        getBTTVGlobalEmotes();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global BTTV emotes > ");
                }
                try {
                    if (prefFFZBadges) {
                        getFFZBadges();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global FFZ badges > ");
                }
                try {
                    if (prefBTTVBadges) {
                        getBTTVBadges();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global BTTV badges > ");
                }
            }
        });
        globalThread.start();

        final Class<?> channelModelClass = findClass("tv.twitch.android.models.ChannelModel", lpparam.classLoader);
        final Class<?> chatMsgBuilderClass = findClass("tv.twitch.android.social.a", lpparam.classLoader);

        XposedBridge.hookAllMethods(chatMsgBuilderClass, "a", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length > 3) {
                    Object name = getObjectField(param.args[0], "userName");
                    if (name != null) {
                        chatSender = ((String) name).toLowerCase();
                    }
                }
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length != 3) {
                    return;
                }

                StringBuilder chatMsg = (StringBuilder) param.args[2];
                twitchBadgeHash = (HashMap) getObjectField(param.thisObject, "c");
                twitchEmoteHash = (HashMap) getObjectField(param.thisObject, "b");
                twitchMentionHash = (HashMap) getObjectField(param.thisObject, "d");
                twitchLinkHash = (HashMap) getObjectField(param.thisObject, "e");
                twitchBitsHash = (HashMap) getObjectField(param.thisObject, "f");
                // XposedBridge.log("LoN: " + chatMsg.toString());
                if (param.args[0] instanceof Boolean) {
                    if (prefFFZEmotes) {
                        injectEmotes(chatMsg, ffzGlobalEmotes);
                        injectEmotes(chatMsg, ffzRoomEmotes);
                    }
                    if (prefBTTVEmotes) {
                        injectEmotes(chatMsg, bttvGlobalEmotes);
                        injectEmotes(chatMsg, bttvRoomEmotes);
                    }

                } else {
                    if (prefFFZBadges) {
                        injectBadges(chatMsg, ffzBadges);
                    }
                    if (prefBTTVBadges) {
                        injectBadges(chatMsg, bttvBadges);
                    }
                }
            }
        });

        findAndHookMethod("tv.twitch.android.social.widgets.ChatWidget", lpparam.classLoader, "a", channelModelClass, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[1] == null) {
                    return;
                }
                Object channelModel = getObjectField(param.thisObject, "v");
                final String channel = (String) getObjectField(channelModel, "e");
                Thread roomThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (prefFFZEmotes) {
                                getFFZRoomEmotes(channel);
                            }
                        } catch (Exception e) {
                            printException(e, "Error fetching FFZ emotes for " + channel + " > ");
                        }
                        try {
                            if (prefBTTVEmotes) {
                                getBTTVRoomEmotes(channel);
                            }
                        } catch (Exception e) {
                            printException(e, "Error fetching BTTV emotes for " + channel + " > ");
                        }
                    }
                });
                roomThread.start();
            }
        });
    }

    private void injectBadges(StringBuilder chatMsg, Hashtable customBadges) {
        if (chatSender == null) {
            return;
        }
        for (Object key : customBadges.keySet()) {
            if (twitchBadgeHash.size() > 2) {
                // Already at 3 badges, anymore will clog up chat box
                return;
            }
            String keyString = (String) key;
            if (!((ArrayList) ((Hashtable) customBadges.get(keyString)).get("users")).contains(chatSender)) {
                continue;
            }
            int location = (twitchBadgeHash.size() * 2);
            String url = (String) ((Hashtable) customBadges.get(keyString)).get("image");
            chatMsg.append(". ");
            twitchBadgeHash.put(location, url);
        }
    }

    private void hideBadge(StringBuilder chatMsg, String badgeKey) {

    }

    private void injectEmotes(StringBuilder chatMsg, Hashtable customEmoteHash) {
        for (Object key : customEmoteHash.keySet()) {
            String keyString = (String) key;
            int location = chatMsg.indexOf(keyString);
            int keyLength = keyString.length();
            if (location == -1) {
                continue;
            }
            while ((location = chatMsg.indexOf(keyString, location)) != -1) {
                chatMsg.replace(location, location + keyLength, ".");
                twitchEmoteHash.put(location, customEmoteHash.get(keyString).toString());
                correctIndexes(location, keyLength);

            }
        }
    }

    private void correctIndexes(int locationStart, int locationLength) {
        HashMap<Integer, Object>[] twitchHashes = new HashMap[5];
        twitchHashes[0] = twitchBadgeHash;
        twitchHashes[1] = twitchEmoteHash;
        twitchHashes[2] = twitchLinkHash;
        twitchHashes[3] = twitchMentionHash;
        twitchHashes[4] = twitchBitsHash;
        HashMap<Integer, Object> tmpHash;
        for (int i = 0; i < twitchHashes.length; ++i) {
            tmpHash = new HashMap<>(twitchHashes[i]);
            for (Object key : tmpHash.keySet()) {
                String keyString = key.toString();
                if (Integer.valueOf(keyString) > locationStart) {
                    Object tmpObject = tmpHash.get(key);
                    twitchHashes[i].remove(key);
                    twitchHashes[i].put(Integer.valueOf(keyString) - locationLength + 1, tmpObject);
                }
            }
        }
    }

    private void getTwitchBadges() throws Exception {
        URL badgeURL = new URL("https://badges.twitch.tv/v1/badges/global/display?language=en");
        JSONObject badges = getJSON(badgeURL);
    }

    private void getFFZRoomEmotes(String channel) throws Exception {
        URL roomURL = new URL(ffzAPIURL + "room/" + channel);
        JSONObject roomEmotes = getJSON(roomURL);
        try {
            int status = roomEmotes.getInt("status");
            if (status == 404) {
                return;
            }
        } catch (JSONException e) {}
        int set = roomEmotes.getJSONObject("room").getInt("set");
        JSONArray roomEmoteArray = roomEmotes.getJSONObject("sets").getJSONObject(Integer.toString(set)).getJSONArray("emoticons");
        for (int i = 0; i < roomEmoteArray.length(); ++i) {
            String emoteName = roomEmoteArray.getJSONObject(i).getString("name");
            String emoteURL = roomEmoteArray.getJSONObject(i).getJSONObject("urls").getString("1");
            ffzRoomEmotes.put(emoteName, "https:" + emoteURL);
        }
    }

    private void getFFZGlobalEmotes() throws Exception {
        URL globalURL = new URL(ffzAPIURL + "set/global");
        JSONObject globalEmotes = getJSON(globalURL);
        JSONArray setsArray = globalEmotes.getJSONArray("default_sets");
        for (int i = 0; i < setsArray.length(); ++i) {
            int set = setsArray.getInt(i);
            JSONArray globalEmotesArray = globalEmotes.getJSONObject("sets").getJSONObject(Integer.toString(set)).getJSONArray("emoticons");
            for (int j = 0; j < globalEmotesArray.length(); ++j) {
                String emoteName = globalEmotesArray.getJSONObject(j).getString("name");
                String emoteURL = globalEmotesArray.getJSONObject(j).getJSONObject("urls").getString("1");
                ffzGlobalEmotes.put(emoteName, "https:" + emoteURL);
            }
        }
    }

    private void getFFZBadges() throws Exception {
        URL badgeURL = new URL(ffzAPIURL + "badges");
        JSONObject badges = getJSON(badgeURL);
        JSONArray badgesList = badges.getJSONArray("badges");
        for (int i = 0; i < badgesList.length(); ++i) {
            String name = badgesList.getJSONObject(i).getString("name");
            ffzBadges.put(name, new Hashtable<String, Object>());
            String imageLocation = "";
            switch(name) {
                case "developer": { imageLocation = "https://leagueofnewbs.com/images/ffz-dev.png"; break; }
                case "supporter": { imageLocation = "https://leagueofnewbs.com/images/ffz-supporter.png"; break; }
                case "bot": { imageLocation = "https://leagueofnewbs.com/images/ffz-bot.png"; break; }
            }
            ffzBadges.get(name).put("image", imageLocation);
            ffzBadges.get(name).put("users", new ArrayList<String>());
            JSONArray userList = badges.getJSONObject("users").getJSONArray(badgesList.getJSONObject(i).getString("id"));
            for (int j = 0; j < userList.length(); ++j) {
                ((ArrayList) ffzBadges.get(name).get("users")).add(userList.getString(j).toLowerCase());
            }
        }
        ((ArrayList) ffzBadges.get("developer").get("users")).add("batedurgonnadie");
    }

    private void getBTTVGlobalEmotes() throws Exception {
        URL globalURL = new URL(bttvAPIURL + "emotes");
        JSONObject globalEmotes = getJSON(globalURL);
        int status = globalEmotes.getInt("status");
        if (globalEmotes.getInt("status") != 200) {
            XposedBridge.log("LoN: Error fetching bttv global emotes (" + status + ")");
            return;
        }
        String urlTemplate = "https:" + globalEmotes.getString("urlTemplate");
        JSONArray globalEmotesArray = globalEmotes.getJSONArray("emotes");
        for (int i = 0; i < globalEmotesArray.length(); ++i) {
            String emoteName = globalEmotesArray.getJSONObject(i).getString("code");
            String emoteID = globalEmotesArray.getJSONObject(i).getString("id");
            String emoteURL = urlTemplate.replace("{{id}}", emoteID).replace("{{image}}", "1x");
            bttvGlobalEmotes.put(emoteName, emoteURL);
        }
    }

    private void getBTTVRoomEmotes(String channel) throws Exception {
        URL roomURL = new URL(bttvAPIURL + "channels/" + channel);
        JSONObject roomEmotes = getJSON(roomURL);
        int status = roomEmotes.getInt("status");
        if (status != 200) {
            return;
        }
        String urlTemplate = "https:" + roomEmotes.getString("urlTemplate");
        JSONArray roomEmotesArray = roomEmotes.getJSONArray("emotes");
        for (int i = 0; i < roomEmotesArray.length(); ++i) {
            String emoteName = roomEmotesArray.getJSONObject(i).getString("code");
            String emoteID = roomEmotesArray.getJSONObject(i).getString("id");
            String emoteURL = urlTemplate.replace("{{id}}", emoteID).replace("{{image}}", "1x");
            bttvRoomEmotes.put(emoteName, emoteURL);
        }
    }

    private void getBTTVBadges() throws Exception {
        URL badgeURL = new URL(bttvAPIURL + "badges");
        JSONObject badges = getJSON(badgeURL);
        if (badges.getInt("status") != 200) {
            XposedBridge.log("Error fetching bttv badges");
            return;
        }
        JSONArray users = badges.getJSONArray("badges");
        JSONArray badgesList = badges.getJSONArray("types");
        for (int i = 0; i < badgesList.length(); ++i) {
            String name = badgesList.getJSONObject(i).getString("name");
            bttvBadges.put(name, new Hashtable<String, Object>());
            String imageLocation = "";
            switch(name) {
                case "developer": { imageLocation = "https://leagueofnewbs.com/images/bttv-dev.png"; break; }
                case "support": { imageLocation = "https://leagueofnewbs.com/images/bttv-support.png"; break; }
                case "design": { imageLocation = "https://leagueofnewbs.com/images/bttv-design.png"; break; }
                case "emotes": { imageLocation = "https://leagueofnewbs.com/images/bttv-approver.png"; break; }
            }
            bttvBadges.get(name).put("image", imageLocation);
            bttvBadges.get(name).put("users", new ArrayList<String>());
        }
        for (int i = 0; i < users.length(); ++i) {
            String name = users.getJSONObject(i).getString("type");
            ((ArrayList) bttvBadges.get(name).get("users")).add(users.getJSONObject(i).getString("name"));
        }
        ((ArrayList) bttvBadges.get("developer").get("users")).add("batedurgonnadie");
    }

    private JSONObject getJSON(URL url) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Glitchify|bated@leagueofnewbs.com");
        if (url.getHost().contains("twitch.tv")) {
            conn.setRequestProperty("Client-ID", "2pvhvz6iubpg0ny77pyb1qrjynupjdu");
        }
        InputStream inStream;
        if (conn.getResponseCode() >= 400) {
            inStream = conn.getErrorStream();
        } else {
            inStream = conn.getInputStream();
        }
        BufferedReader buffReader = new BufferedReader(new InputStreamReader(inStream));
        StringBuilder jsonString = new StringBuilder();
        String line;
        while ((line = buffReader.readLine()) != null) {
            jsonString.append(line);
        }
        buffReader.close();
        return new JSONObject(jsonString.toString());
    }

    private void printException(Exception e, String prefix) {
        if (e.getMessage() == null || e.getMessage().equals("")) {
            return;
        }
        String output = "LoN: ";
        if (prefix != null) {
            output += prefix;
        }
        output += e.getMessage();
        XposedBridge.log(output);
        XposedBridge.log(e);
    }

}