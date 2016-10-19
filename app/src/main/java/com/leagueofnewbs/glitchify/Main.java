package com.leagueofnewbs.glitchify;

import static de.robv.android.xposed.XposedHelpers.*;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Hashtable;

import javax.net.ssl.HttpsURLConnection;

public class Main implements IXposedHookLoadPackage {

    private Hashtable<String, String> ffzRoomEmotes = new Hashtable<>();
    private Hashtable<String, String> ffzGlobalEmotes = new Hashtable<>();
    private Hashtable<String, String> bttvRoomEmotes = new Hashtable<>();
    private Hashtable<String, String> bttvGlobalEmotes = new Hashtable<>();
    public static String ffzAPIURL = "https://api.frankerfacez.com/v1/";
    public static String bttvAPIURL = "https://api.betterttv.net/2/";
    private HashMap<Integer, Object> twitchBadgeHash = null;
    private HashMap<Integer, Object> twitchEmoteHash = null;
    private HashMap<Integer, Object> twitchMentionHash = null;
    private HashMap<Integer, Object> twitchLinkHash = null;
    private HashMap<Integer, Object> twitchBitsHash = null;

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("tv.twitch.android.app")) {
            return;
        }

        XSharedPreferences pref = new XSharedPreferences(Main.class.getPackage().getName(), "preferences");
        pref.makeWorldReadable();
        pref.reload();
        final boolean prefFFZEmotes  = pref.getBoolean("ffz_emotes_enable", true);
        final boolean prefBTTVEmotes = pref.getBoolean("bttv_emotes_enable", true);

        XposedBridge.log("LoN: FFZ Emotes set to " + String.valueOf(prefFFZEmotes));

        final Class<?> chatMessageClass = findClass("tv.twitch.chat.ChatMessage", lpparam.classLoader);
        final Class<?> channelModelClass = findClass("tv.twitch.android.models.ChannelModel", lpparam.classLoader);

        findAndHookMethod("tv.twitch.android.social.a", lpparam.classLoader, "a", boolean.class, chatMessageClass, StringBuilder.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                StringBuilder chatMsg = (StringBuilder) param.args[2];
                XposedBridge.log("LoN: " + chatMsg.toString());
                twitchBadgeHash = (HashMap) getObjectField(param.thisObject, "c");
                twitchEmoteHash = (HashMap) getObjectField(param.thisObject, "b");
                twitchMentionHash = (HashMap) getObjectField(param.thisObject, "d");
                twitchLinkHash = (HashMap) getObjectField(param.thisObject, "e");
                twitchBitsHash = (HashMap) getObjectField(param.thisObject, "f");
                // key = index_in_string, value = url
                if (prefFFZEmotes) {
                    injectEmotes(chatMsg, ffzGlobalEmotes);
                    injectEmotes(chatMsg, ffzRoomEmotes);
                }
                if (prefBTTVEmotes) {
                    injectEmotes(chatMsg, bttvRoomEmotes);
                }
                XposedBridge.log(twitchBadgeHash.toString());
                XposedBridge.log(twitchEmoteHash.toString());
                XposedBridge.log(twitchMentionHash.toString());
                XposedBridge.log(twitchLinkHash.toString());
                XposedBridge.log(twitchBitsHash.toString());
                XposedBridge.log("LoN: " + chatMsg.toString());
            }
        });

        findAndHookMethod("tv.twitch.android.social.widgets.ChatWidget", lpparam.classLoader, "a", channelModelClass, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object channelModel = getObjectField(param.thisObject, "v");
                String game = (String) getObjectField(channelModel, "g");
                String status = (String) getObjectField(channelModel, "h");
                final String channel = (String) getObjectField(channelModel, "e");
                Thread ffzRoomThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            getFFZRoomEmotes(channel);
                        } catch (Exception e) {
                            if (e.getMessage() == null || e.getMessage().equals("")) {
                                return;
                            }
                            XposedBridge.log(e.getMessage());
                        }
                    }
                });
                ffzRoomThread.start();
                if ((prefFFZEmotes && ffzGlobalEmotes.isEmpty()) || (prefBTTVEmotes && bttvGlobalEmotes.isEmpty())) {
                    Thread ffzGlobalThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (prefFFZEmotes) {
                                    getFFZGlobalEmotes();
                                }
                                if (prefBTTVEmotes) {
                                    getBTTVGlobalEmotes();
                                }
                            } catch (Exception e) {
                                if (e.getMessage() == null || e.getMessage().equals("")) {
                                    return;
                                }
                                XposedBridge.log(e.getMessage());
                            }
                        }
                    });
                    ffzGlobalThread.start();
                }
                if (prefBTTVEmotes) {
                    Thread bttvRoomThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                getBTTVRoomEmotes(channel);
                            } catch (Exception e) {
                                if (e.getMessage() == null || e.getMessage().equals("")) {
                                    return;
                                }
                                XposedBridge.log(e.getMessage());
                            }
                        }
                    });
                    bttvRoomThread.start();
                }
            }
        });
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

    private void getFFZRoomEmotes(String channel) throws Exception {
        URL roomURL = new URL(ffzAPIURL + "room/" + channel);
        JSONObject roomEmotes = getJSON(roomURL);
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

    private JSONObject getJSON(URL url) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Glitchify|bated@leagueofnewbs.com");
        BufferedReader buffReader = new BufferedReader(new InputStreamReader(url.openStream()));
        StringBuilder jsonString = new StringBuilder();
        String line;
        while ((line = buffReader.readLine()) != null) {
            jsonString.append(line);
        }
        buffReader.close();
        return new JSONObject(jsonString.toString());
    }

}