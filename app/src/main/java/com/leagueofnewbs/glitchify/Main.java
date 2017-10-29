package com.leagueofnewbs.glitchify;

import android.app.Activity;
import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;

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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

import javax.net.ssl.HttpsURLConnection;

public class Main implements IXposedHookLoadPackage {

    private Hashtable<String, String> ffzRoomEmotes = new Hashtable<>();
    private Hashtable<String, String> ffzGlobalEmotes = new Hashtable<>();
    private Hashtable<String, Hashtable<String, Object>> ffzBadges = new Hashtable<>();
    private Hashtable<String, String> bttvRoomEmotes = new Hashtable<>();
    private Hashtable<String, String> bttvGlobalEmotes = new Hashtable<>();
    private Hashtable<String, Hashtable<String, Object>> bttvBadges = new Hashtable<>();
    private Hashtable<String, ArrayList<String>> twitchGlobalBadges = new Hashtable<>();
    private ArrayList<String> hiddenBadges = new ArrayList<>();
    private static String customModBadge;
    private static String ffzAPIURL = "https://api.frankerfacez.com/v1/";
    private static String bttvAPIURL = "https://api.betterttv.net/2/";
    private HashMap<Integer, String> twitchBadgeHash = null;
    private HashMap<Integer, String> twitchEmoteHash = null;
    private HashMap<Integer, Object> twitchMentionHash = null;
    private HashMap<Integer, String> twitchLinkHash = null;
    private HashMap<Integer, Object> twitchBitsHash = null;
    private String chatSender;

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("tv.twitch.android.app")) {
            return;
        }

        // Load all the preferences and save them all so it doesn't need to be loaded again
        XSharedPreferences pref = new XSharedPreferences(Main.class.getPackage().getName(), "preferences");
        pref.makeWorldReadable();
        pref.reload();

        final boolean prefFFZEmotes = pref.getBoolean("ffz_emotes_enable", true);
        final boolean prefFFZBadges = pref.getBoolean("ffz_badges_enable", true);
        final boolean prefFFZModBadge = pref.getBoolean("ffz_mod_enable", true);
        final boolean prefBTTVEmotes = pref.getBoolean("bttv_emotes_enable", true);
        final boolean prefBTTVBadges = pref.getBoolean("bttv_badges_enable", true);
        final boolean prefBitsCombine = pref.getBoolean("bits_combine_enable", true);
        final String prefHiddenBadges = pref.getString("badge_hiding_enable", "");
        if (!prefHiddenBadges.equals("")) {
            for (Object key : prefHiddenBadges.split(",")) {
                hiddenBadges.add(((String) key).trim());
            }
        }
        final boolean prefPreventChatClear = pref.getBoolean("prevent_channel_clear", true);
        final boolean prefShowDeletedMessages = pref.getBoolean("show_deleted_messages", true);
        final boolean prefShowTimeStamps = pref.getBoolean("show_timestamps", true);
        final int prefChatScrollbackLength = Integer.valueOf(pref.getString("chat_scrollback_length", "100"));
        final boolean prefChatDivider = pref.getBoolean("chat_divider", false);
        final boolean prefOverrideVideoQuality = pref.getBoolean("override_video_quality", false);
        final String prefDefaultVideoQuality = pref.getString("default_video_quality", "Auto");

        // Get all global info that we can all at once
        // FFZ/BTTV global emotes, global twitch badges, and FFZ mod badge
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
                try {
                    if (prefFFZModBadge || !hiddenBadges.isEmpty()) {
                        getTwitchBadges();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global Twitch badges > ");
                }
            }
        });
        globalThread.start();


        // These are all the different class definitions that are needed in the function hooking
        final Class<?> chatInfoClass = findClass("tv.twitch.android.models.ChannelInfo", lpparam.classLoader);
        final Class<?> chatTokenizerClass = findClass("tv.twitch.android.social.a.b", lpparam.classLoader);
        final Class<?> chatMsgBuilderClass = findClass("tv.twitch.android.social.b", lpparam.classLoader);
        final Class<?> chatUpdaterClass = findClass("tv.twitch.android.b.a.b", lpparam.classLoader);
        final Class<?> chatWidgetClass = findClass("tv.twitch.android.social.viewdelegates.ChatViewDelegate", lpparam.classLoader);
        final Class<?> messageObjectClass = findClass("tv.twitch.android.adapters.social.MessageAdapterItem", lpparam.classLoader);
        final Class<?> messageListClass = findClass("tv.twitch.android.adapters.social.i", lpparam.classLoader);
        final Class<?> messageListHolderClass = findClass("tv.twitch.android.adapters.social.b", lpparam.classLoader);
        final Class<?> clickableUsernameClass = findClass("tv.twitch.android.social.m", lpparam.classLoader);
        final Class<?> chatMessage = findClass("tv.twitch.chat.ChatMessageInfo", lpparam.classLoader);
        final Class<?> dividerClass = findClass("tv.twitch.android.adapters.social.j", lpparam.classLoader);
        final Class<?> playerWidgetClass = findClass("tv.twitch.android.app.core.widgets.StreamWidget", lpparam.classLoader);

        // This is the monster function that creates the messages
        // Twitch uses multiple hashes to hold links, bits, mentions, emotes, and badges
        // We save these and then use a few different functions to modify them with what we need
        XposedBridge.hookAllMethods(chatMsgBuilderClass, "a", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length > 3) {
                    Object chatMsg = param.args[0];
                    String name;
                    if (chatMessage.isInstance(chatMsg) && (name = ((String) getObjectField(chatMsg, "userName"))) != null) {
                        chatSender = name.toLowerCase();
                    }
                }
            }
        });

        XposedBridge.hookAllMethods(chatTokenizerClass, "a", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args == null) {
                    return;
                }
                boolean badgesMethod = (param.args[1] instanceof Integer);
                StringBuilder chatMsg;
                if (badgesMethod) {
                    chatMsg = (StringBuilder) param.args[2];
                } else {
                    chatMsg = (StringBuilder) param.args[1];
                }

                Object hashesObject = param.args[3];
                twitchBadgeHash = (HashMap) getObjectField(hashesObject, "c");
                twitchEmoteHash = (HashMap) getObjectField(hashesObject, "a");
                twitchMentionHash = (HashMap) getObjectField(hashesObject, "d");
                twitchLinkHash = (HashMap) getObjectField(hashesObject, "b");
                twitchBitsHash = (HashMap) getObjectField(hashesObject, "f");

                if (!badgesMethod) {
                    if (prefBitsCombine) {
                        combineBits(chatMsg);
                    }
                    if (prefFFZEmotes) {
                        injectEmotes(chatMsg, ffzGlobalEmotes);
                        injectEmotes(chatMsg, ffzRoomEmotes);
                    }
                    if (prefBTTVEmotes) {
                        injectEmotes(chatMsg, bttvGlobalEmotes);
                        injectEmotes(chatMsg, bttvRoomEmotes);
                    }
                } else {
                    if (prefFFZModBadge && customModBadge != null) {
                        replaceModBadge();
                    }
                    if (!hiddenBadges.isEmpty()) {
                        hideBadges(chatMsg);
                    }
                    if (prefFFZBadges) {
                        injectBadges(chatMsg, ffzBadges);
                    }
                    if (prefBTTVBadges) {
                        injectBadges(chatMsg, bttvBadges);
                    }
                }
            }
        });

        // This is called when a chat widget gets a channel name attached to it
        // It sets up all the channel specific stuff (bttv/ffz emotes, etc)
        findAndHookMethod(chatWidgetClass, "a", chatInfoClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final String channelInfo = (String) callMethod(param.args[0], "getName");
                Thread roomThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (prefFFZEmotes) {
                                getFFZRoomEmotes(channelInfo);
                            }
                        } catch (Exception e) {
                            printException(e, "Error fetching FFZ emotes for " + channelInfo + " > ");
                        }
                        try {
                            if (prefBTTVEmotes) {
                                getBTTVRoomEmotes(channelInfo);
                            }
                        } catch (Exception e) {
                            printException(e, "Error fetching BTTV emotes for " + channelInfo + " > ");
                        }
                    }
                });
                roomThread.start();
            }
        });

        // This is what actually goes through and strikes out the messages
        // If show deleted is false this will replace with <message deleted>
        findAndHookMethod(messageListClass, "d", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefShowDeletedMessages) {
                    param.setResult(null);
                    List messageList = (List) getObjectField(param.thisObject, "b");
                    for (Object message : messageList) {
                        if (!(messageObjectClass.isInstance(message))) {
                            continue;
                        }
                        int userID = (int) getObjectField(message, "d");
                        if (userID == ((int) param.args[0])) {
                            Spanned messageSpan = (Spanned) getObjectField(message, "g");
                            Object[] spans = messageSpan.getSpans(0, messageSpan.length(), clickableUsernameClass);
                            int spanEnd = messageSpan.getSpanEnd(spans[0]);
                            int length = 2;
                            int i = spanEnd + length;
                            if (i < messageSpan.length() && messageSpan.subSequence(spanEnd, i).toString().equals(": ")) {
                                spanEnd += length;
                            }
                            SpannableStringBuilder ssb = new SpannableStringBuilder(messageSpan, 0, spanEnd);
                            SpannableStringBuilder ssb2 = new SpannableStringBuilder(messageSpan, spanEnd, messageSpan.length());
                            ssb.append(ssb2);
                            ssb.setSpan(new StrikethroughSpan(), ssb.length() - ssb2.length(), ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            setObjectField(message, "g", ssb);
                        }
                    }
                }
            }
        });

        // Add timestamps to the beginning of every message
        findAndHookConstructor(messageObjectClass, Context.class, int.class, String.class, String.class, Spannable.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefShowTimeStamps) {
                    SimpleDateFormat formatter = new SimpleDateFormat("h:mm ", Locale.US);
                    String dateString = formatter.format(new Date());
                    Spanned messageSpan = (Spanned) param.args[4];
                    SpannableStringBuilder message = new SpannableStringBuilder(dateString);
                    message.setSpan(new RelativeSizeSpan(0.75f), 0, dateString.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    message.append(new SpannableStringBuilder(messageSpan, 0, messageSpan.length()));
                    param.args[4] = message;
                }
            }
        });

        // Override complete chat clears
        findAndHookMethod(chatUpdaterClass, "chatChannelMessagesCleared", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefPreventChatClear) {
                    param.setResult(null);
                    Object outOb = getObjectField(param.thisObject, "a");
                    Set dList = (Set) getObjectField(outOb, "d");
                    for (Object d : dList) {
                        final Object chatWidget = getObjectField(d, "a");
                        if (chatWidgetClass.isInstance(chatWidget)) {
                            Activity chatActivity = (Activity) callMethod(chatWidget, "g");
                            chatActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    callMethod(chatWidget, "a", new Class<?>[]{String.class, boolean.class}, "Prevented chat from being cleared by a moderator.", false);
                                }
                            });
                        }
                    }
                }
                param.args[0] = prefChatScrollbackLength;
            }
        });
        findAndHookConstructor(messageListClass, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = prefChatScrollbackLength;
            }
        });
        findAndHookMethod(messageListClass, "e", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = prefChatScrollbackLength;
            }
        });

        // Add dividers before all new messages
        findAndHookMethod(messageListHolderClass, "a", List.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefChatDivider) {
                    Object divider = newInstance(dividerClass);
                    callMethod(getObjectField(param.thisObject, "a"), "a", divider);
                }
            }
        });

        // Set stream quality before it starts playing
        // Currently does not work for just "source"
        XposedBridge.hookAllConstructors(playerWidgetClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                setObjectField(param.thisObject, "d", prefDefaultVideoQuality);
            }
        });
    }

    private synchronized void replaceModBadge() {
        String modURL;
        try {
            ArrayList<String> allModURLs = twitchGlobalBadges.get("moderator");
            if (allModURLs == null) {
                return;
            }
            modURL = allModURLs.get(0);
        } catch (Exception e) {
            printException(e, "Error getting mod badge from table > ");
            return;
        }
        String finalURL = "";
        boolean found = false;
        for (int i = 1; i <= 3; ++i) {
            finalURL = modURL + "/" + String.valueOf(i);
            if (twitchBadgeHash.containsValue(finalURL)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }

        for (Integer key : twitchBadgeHash.keySet()) {
            if (finalURL.equals(twitchBadgeHash.get(key))) {
                twitchBadgeHash.put(key, customModBadge);
                break;
            }
        }
    }

    private synchronized void injectBadges(StringBuilder chatMsg, Hashtable customBadges) {
        if (chatSender == null) {
            return;
        }
        for (Object key : customBadges.keySet()) {
            if (twitchBadgeHash.size() > 2) {
                // Already at 3 badges, anymore will clog up chat box
                return;
            }
            String keyString = (String) key;
            if (hiddenBadges.contains(keyString)) {
                continue;
            }
            if (!((ArrayList) ((Hashtable) customBadges.get(keyString)).get("users")).contains(chatSender)) {
                continue;
            }
            int location = (twitchBadgeHash.size() * 2);
            String url = (String) ((Hashtable) customBadges.get(keyString)).get("image");
            chatMsg.append(". ");
            twitchBadgeHash.put(location, url);
        }
    }

    private synchronized void hideBadges(StringBuilder chatMsg) {
        for (String key : hiddenBadges) {
            if (twitchGlobalBadges.get(key) == null) { return; }
            for (String url : twitchGlobalBadges.get(key)) {
                String badgeURL = "";
                boolean found = false;
                for (int i = 1; i <= 3; ++i) {
                    badgeURL = url + "/" + String.valueOf(i);
                    if (twitchBadgeHash.containsValue(badgeURL)) {
                        found = true;
                        break;
                    }
                }
                if (!found) { continue; }
                Integer badgeKey = 0;
                for (Integer tmpKey : twitchBadgeHash.keySet()) {
                    if (twitchBadgeHash.get(tmpKey).equals(badgeURL)) {
                        badgeKey = tmpKey;
                        break;
                    }
                }
                twitchBadgeHash.remove(badgeKey);
                chatMsg.replace(badgeKey, badgeKey + 2, "");
                correctIndexes(badgeKey, badgeKey + 2);
                break;
            }
        }
    }

    private synchronized void injectEmotes(StringBuilder chatMsg, Hashtable customEmoteHash) {
        for (Object key : customEmoteHash.keySet()) {
            String keyString = (String) key;
            int location;
            if ((location = chatMsg.indexOf(":")) == -1 ) {
                location = chatMsg.indexOf(" ");
            }
            location++;
            int keyLength = keyString.length();
            while ((location = chatMsg.indexOf(keyString, location)) != -1) {
                if (chatMsg.charAt(location - 1) != ' ') {
                    ++location;
                    continue;
                }
                chatMsg.replace(location, location + keyLength, ".");
                twitchEmoteHash.put(location, customEmoteHash.get(keyString).toString());
                correctIndexes(location, keyLength);

            }
        }
    }

    private synchronized void combineBits(StringBuilder chatMsg) {
        if (twitchBitsHash.isEmpty()) { return; }
        Hashtable<String, Integer> bits = new Hashtable<>();
        Hashtable<String, Object> tmpBitObjs = new Hashtable<>();
        TreeMap<Integer, Object> bitTree = new TreeMap<>(Collections.<Integer>reverseOrder());
        bitTree.putAll(twitchBitsHash);
        Object tmpBitObj;
        for (Object key : bitTree.keySet()) {
            Integer location = (Integer) key;
            tmpBitObj = twitchBitsHash.get(key);
            int bitAmount = (Integer) getObjectField(tmpBitObj, "numBits");
            String bitType = (String) getObjectField(tmpBitObj, "prefix");
            if (!tmpBitObjs.containsKey(bitType)) {
                tmpBitObjs.put(bitType, tmpBitObj);
                bits.put(bitType, 0);
            }
            bits.put(bitType, bitAmount + bits.get(bitType));
            int length = String.valueOf(bitAmount).length() + 2;
            chatMsg.replace(location, location + length + 1, "");
            correctIndexes(location, length + 2);
        }
        twitchBitsHash.clear();
        for (String key : tmpBitObjs.keySet()) {
            setObjectField(tmpBitObjs.get(key), "numBits", bits.get(key));
        }
        for (String key : tmpBitObjs.keySet()) {
            if (chatMsg.charAt(chatMsg.length() - 1) != ' ') {
                chatMsg.append(" ");
            }
            twitchBitsHash.put(chatMsg.length(), tmpBitObjs.get(key));
            chatMsg.append("  ").append(bits.get(key));
        }
    }

    private synchronized void correctIndexes(int locationStart, int locationLength) {
        HashMap[] twitchHashes = {twitchBadgeHash, twitchEmoteHash, twitchLinkHash, twitchMentionHash, twitchBitsHash};
        HashMap<Integer, Object> tmpHash;
        for (HashMap hash: twitchHashes) {
            tmpHash = new HashMap<>(hash);
            for (Integer key : tmpHash.keySet()) {
                if (key > locationStart) {
                    Object tmpObject = tmpHash.get(key);
                    hash.remove(key);
                    hash.put(key - locationLength + 1, tmpObject);
                }
            }
        }
    }

    private void getTwitchBadges() throws Exception {
        URL badgeURL = new URL("https://badges.twitch.tv/v1/badges/global/display?language=en");
        JSONObject badges = getJSON(badgeURL);
        if (badges.getInt("status") == 200) {
            JSONObject sets = badges.getJSONObject("badge_sets");
            Iterator<?> keys = sets.keys();
            synchronized (twitchGlobalBadges) {
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    JSONObject badgeObject = sets.getJSONObject(key).getJSONObject("versions");
                    twitchGlobalBadges.put(key, new ArrayList<String>());
                    Iterator<?> innerKeys = badgeObject.keys();
                    while (innerKeys.hasNext()) {
                        String innerKey = (String) innerKeys.next();
                        String url = badgeObject.getJSONObject(innerKey).getString("image_url_1x");
                        url = url.substring(0, url.length() - 2);
                        twitchGlobalBadges.get(key).add(url);
                    }
                }
            }
        }
    }

    private void getFFZRoomEmotes(String channel) throws Exception {
        URL roomURL = new URL(ffzAPIURL + "room/" + channel);
        JSONObject roomEmotes = getJSON(roomURL);
        try {
            int status = roomEmotes.getInt("status");
            if (status == 404) {
                customModBadge = null;
                return;
            }
        } catch (JSONException e) {}
        int set = roomEmotes.getJSONObject("room").getInt("set");
        if (roomEmotes.getJSONObject("room").isNull("moderator_badge")) {
            customModBadge = null;
        } else {
            JSONObject modURLs = roomEmotes.getJSONObject("room").getJSONObject("mod_urls");
            String url = modURLs.getString("1");
            if (modURLs.has("2")) {
                url = modURLs.getString("2");
            }
            customModBadge = "https:" + url + "/solid";
        }
        JSONArray roomEmoteArray = roomEmotes.getJSONObject("sets").getJSONObject(Integer.toString(set)).getJSONArray("emoticons");
        synchronized (ffzRoomEmotes) {
            for (int i = 0; i < roomEmoteArray.length(); ++i) {
                String emoteName = roomEmoteArray.getJSONObject(i).getString("name");
                String emoteURL = roomEmoteArray.getJSONObject(i).getJSONObject("urls").getString("1");
                ffzRoomEmotes.put(emoteName, "https:" + emoteURL);
            }
        }
    }

    private void getFFZGlobalEmotes() throws Exception {
        URL globalURL = new URL(ffzAPIURL + "set/global");
        JSONObject globalEmotes = getJSON(globalURL);
        JSONArray setsArray = globalEmotes.getJSONArray("default_sets");
        synchronized (ffzGlobalEmotes) {
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
    }

    private void getFFZBadges() throws Exception {
        URL badgeURL = new URL(ffzAPIURL + "badges");
        JSONObject badges = getJSON(badgeURL);
        JSONArray badgesList = badges.getJSONArray("badges");
        synchronized (ffzBadges) {
            for (int i = 0; i < badgesList.length(); ++i) {
                String name = "ffz-" + badgesList.getJSONObject(i).getString("name");
                ffzBadges.put(name, new Hashtable<String, Object>());
                String imageLocation = "https:" + badgesList.getJSONObject(i).getJSONObject("urls").getString("2") + "/solid";
                ffzBadges.get(name).put("image", imageLocation);
                ffzBadges.get(name).put("users", new ArrayList<String>());
                JSONArray userList = badges.getJSONObject("users").getJSONArray(badgesList.getJSONObject(i).getString("id"));
                for (int j = 0; j < userList.length(); ++j) {
                    ((ArrayList) ffzBadges.get(name).get("users")).add(userList.getString(j).toLowerCase());
                }
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
        synchronized (bttvGlobalEmotes) {
            for (int i = 0; i < globalEmotesArray.length(); ++i) {
                String emoteName = globalEmotesArray.getJSONObject(i).getString("code");
                String emoteID = globalEmotesArray.getJSONObject(i).getString("id");
                String emoteURL = urlTemplate.replace("{{id}}", emoteID).replace("{{image}}", "1x");
                bttvGlobalEmotes.put(emoteName, emoteURL);
            }
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
        synchronized (bttvRoomEmotes) {
            for (int i = 0; i < roomEmotesArray.length(); ++i) {
                String emoteName = roomEmotesArray.getJSONObject(i).getString("code");
                String emoteID = roomEmotesArray.getJSONObject(i).getString("id");
                String emoteURL = urlTemplate.replace("{{id}}", emoteID).replace("{{image}}", "1x");
                bttvRoomEmotes.put(emoteName, emoteURL);
            }
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
        synchronized (bttvBadges) {
            for (int i = 0; i < badgesList.length(); ++i) {
                String name = "bttv-" + badgesList.getJSONObject(i).getString("name");
                bttvBadges.put(name, new Hashtable<String, Object>());
                String imageLocation = "";
                switch(name) {
                    case "bttv-developer": { imageLocation = "https://leagueofnewbs.com/images/bttv-dev.png"; break; }
                    case "bttv-support": { imageLocation = "https://leagueofnewbs.com/images/bttv-support.png"; break; }
                    case "bttv-design": { imageLocation = "https://leagueofnewbs.com/images/bttv-design.png"; break; }
                    case "bttv-emotes": { imageLocation = "https://leagueofnewbs.com/images/bttv-approver.png"; break; }
                }
                bttvBadges.get(name).put("image", imageLocation);
                bttvBadges.get(name).put("users", new ArrayList<String>());
            }
            for (int i = 0; i < users.length(); ++i) {
                String name = "bttv-" + users.getJSONObject(i).getString("type");
                ((ArrayList) bttvBadges.get(name).get("users")).add(users.getJSONObject(i).getString("name"));
            }
        }
    }

    private JSONObject getJSON(URL url) throws Exception {
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Glitchify|bated@leagueofnewbs.com");
        if (url.getHost().contains("twitch.tv")) {
            conn.setRequestProperty("Client-ID", "2pvhvz6iubpg0ny77pyb1qrjynupjdu");
        }
        InputStream inStream;
        int responseCode = conn.getResponseCode();
        if (responseCode >= 400) {
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
        JSONObject json =  new JSONObject(jsonString.toString());
        if (json.isNull("status")) {
            json.put("status", responseCode);
        }
        return json;
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