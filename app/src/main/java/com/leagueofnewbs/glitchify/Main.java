package com.leagueofnewbs.glitchify;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

public class Main implements IXposedHookLoadPackage {

    private final Hashtable<String, String> ffzRoomEmotes = new Hashtable<>();
    private final Hashtable<String, String> ffzGlobalEmotes = new Hashtable<>();
    private final Hashtable<String, Hashtable<String, Object>> ffzBadges = new Hashtable<>();
    private final Hashtable<String, String> bttvRoomEmotes = new Hashtable<>();
    private final Hashtable<String, String> bttvGlobalEmotes = new Hashtable<>();
    private final Hashtable<String, Hashtable<String, Object>> bttvBadges = new Hashtable<>();
    private ArrayList<String> hiddenBadges = new ArrayList<>();
    private static String customModBadge;
    private static Object customModBadgeImage;
    private static String ffzAPIURL = "https://api.frankerfacez.com/v1/";
    private static String bttvAPIURL = "https://api.betterttv.net/2/";
    private ColorHelper colorHelper = ColorHelper.getInstance();
    private boolean isDark = false;

    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("tv.twitch.android.app") || !lpparam.isFirstApplication) {
            return;
        }

        // Load all the preferences and save them all so it doesn't need to be loaded again
        XSharedPreferences pref = new XSharedPreferences(Main.class.getPackage().getName(), "preferences");
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
        final boolean prefColorAdjust = pref.getBoolean("color_adjust", false);

        XSharedPreferences darkPrefs = new XSharedPreferences("tv.twitch.android.app", "tv.twitch.android.app_preferences");
        isDark = darkPrefs.getBoolean("dark_theme_enabled", false);

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
            }
        });
        globalThread.start();


        // These are all the different class definitions that are needed in the function hooking
        final Class<?> chatInfoClass = findClass("tv.twitch.android.models.ChannelInfo", lpparam.classLoader);
        final Class<?> chatControllerClass = findClass("tv.twitch.android.b.a", lpparam.classLoader);
        final Class<?> chatUpdaterClass = findClass("tv.twitch.android.b.a.b", lpparam.classLoader);
        final Class<?> chatViewClass = findClass("tv.twitch.android.social.a.h", lpparam.classLoader);
        final Class<?> newMessageRecyclerItemClass = findClass("tv.twitch.android.adapters.c.i", lpparam.classLoader);
        final Class<?> newChannelChatAdapterClass = findClass("tv.twitch.android.adapters.t", lpparam.classLoader);
        final Class<?> chatUtilClass = findClass("tv.twitch.android.util.k", lpparam.classLoader);
        final Class<?> systemMessageTypeClass = findClass("tv.twitch.android.adapters.c.n", lpparam.classLoader);
        final Class<?> chatMessageFactoryClass = findClass("tv.twitch.android.social.c", lpparam.classLoader);
        final Class<?> glideChatImageTargetInterfaceClass = findClass("tv.twitch.android.social.m.a", lpparam.classLoader);
        final Class<?> clickableUsernameClass = findClass("tv.twitch.android.social.k", lpparam.classLoader);
        final Class<?> usernameClickableSpanInterfaceClass = findClass("tv.twitch.android.social.k.a", lpparam.classLoader);
        final Class<?> twitchUrlSpanInterfaceClass = findClass("tv.twitch.android.util.androidUI.TwitchURLSpan.a", lpparam.classLoader);
        final Class<?> webViewDialogFragmentEnumClass = findClass("tv.twitch.android.app.core.WebViewDialogFragment.a", lpparam.classLoader);
        final Class<?> chatMessageInterfaceClass = findClass("tv.twitch.android.social.f", lpparam.classLoader);
        final Class<?> chatBadgeImageClass = findClass("tv.twitch.chat.ChatBadgeImage", lpparam.classLoader);
        final Class<?> chatBitsTokenClass = findClass("tv.twitch.chat.ChatBitsToken", lpparam.classLoader);
        final Class<?> cheermotesHelperClass = findClass("tv.twitch.android.app.bits.w", lpparam.classLoader);
        final Class<?> chommentModelDelegateClass = findClass("tv.twitch.android.models.chomments.ChommentModelDelegate", lpparam.classLoader);

        // This is called when a chat widget gets a channel name attached to it
        // It sets up all the channel specific stuff (bttv/ffz emotes, etc)
        findAndHookMethod(chatViewClass, "a", chatInfoClass, new XC_MethodHook() {
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
        findAndHookMethod(chatUtilClass, "a", Spanned.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefShowDeletedMessages) {
                    Spanned messageSpan = (Spanned) param.args[0];
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
                    param.setResult(ssb);
                }
            }
        });

        // Add timestamps to the beginning of every message
        findAndHookConstructor(newMessageRecyclerItemClass, Context.class, int.class, String.class, String.class, int.class, CharSequence.class, List.class, systemMessageTypeClass, float.class, int.class, float.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefShowTimeStamps) {
                    SimpleDateFormat formatter = new SimpleDateFormat("h:mm ", Locale.US);
                    SpannableString dateString = SpannableString.valueOf(formatter.format(new Date()));
                    dateString.setSpan(new RelativeSizeSpan(0.75f), 0, dateString.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    SpannableString messageSpan = (SpannableString) param.args[5];
                    SpannableStringBuilder message = new SpannableStringBuilder(dateString);
                    message.append(messageSpan);
                    param.args[5] = SpannableString.valueOf(message);
                }
            }
        });

        // Override complete chat clears
        findAndHookMethod(chatUpdaterClass, "chatChannelMessagesCleared", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (prefPreventChatClear) {
                    param.setResult(null);
                }
                param.args[0] = prefChatScrollbackLength;
            }
        });

        // Prevent overriding of chat history length
        findAndHookConstructor(newChannelChatAdapterClass, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = prefChatScrollbackLength;
            }
        });

        // Inject all badges and emotes into the finished message
        findAndHookMethod(chatMessageFactoryClass, "a", chatMessageInterfaceClass, glideChatImageTargetInterfaceClass, boolean.class, boolean.class, boolean.class, int.class, int.class, usernameClickableSpanInterfaceClass, twitchUrlSpanInterfaceClass, webViewDialogFragmentEnumClass, ArrayList.class, String.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void  beforeHookedMethod(MethodHookParam param) throws Throwable {
                setAdditionalInstanceField(param.thisObject, "allowBitInsertion", false);
                if (prefColorAdjust) {
                    Integer color = (Integer) param.args[5];
                    Integer newColor = colorHelper.maybeBrighten(color, isDark);
                    param.args[5] = newColor;
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                setAdditionalInstanceField(param.thisObject, "allowBitInsertion", true);
                SpannableStringBuilder msg = new SpannableStringBuilder((SpannableString) param.getResult());

                if (prefFFZBadges) {
                    msg = injectBadges(param, msg, ffzBadges);
                }
                if (prefBTTVBadges) {
                    msg = injectBadges(param, msg, bttvBadges);
                }
                if (prefFFZEmotes) {
                    msg = injectEmotes(param, msg, ffzGlobalEmotes);
                    msg = injectEmotes(param, msg, ffzRoomEmotes);
                }
                if (prefBTTVEmotes) {
                    msg = injectEmotes(param, msg, bttvGlobalEmotes);
                    msg = injectEmotes(param, msg, bttvRoomEmotes);
                }
                if (prefBitsCombine && !chommentModelDelegateClass.isInstance(param.args[0])) {
                    Object bit = newInstance(chatBitsTokenClass);
                    Object chatMessageInfo = getObjectField(param.args[0], "a");
                    int numBits = getIntField(chatMessageInfo, "numBitsSent");
                    if (numBits > 0) {
                        setIntField(bit, "numBits", numBits);
                        setObjectField(bit, "prefix", "cheer");
                        SpannableString bitString = (SpannableString) callMethod(param.thisObject, "a", bit, getObjectField(param.thisObject, "d"), param.args[1], param.args[10]);
                        if (bitString != null) {
                            msg.append(" ");
                            msg.append(bitString);
                        }
                    }
                }
                param.setResult(SpannableString.valueOf(msg));
            }
        });

        // Stop bits from being put into chat by the message factory
        findAndHookMethod(chatMessageFactoryClass, "a", chatBitsTokenClass, cheermotesHelperClass, glideChatImageTargetInterfaceClass, ArrayList.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (!((Boolean) getAdditionalInstanceField(param.thisObject, "allowBitInsertion"))) {
                    param.setResult(null);
                }
            }
        });

        // Return null for any hidden badges, for some reason this works and I'm not going to complain because it's much easier this way
        // If custom mod badge, return a customized ChatBadgeImage instance with our url for mod badge
        // Whenever we leave the chat, return to using the default
        findAndHookMethod(chatControllerClass, "a", int.class, String.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String badgeName = (String) param.args[1];
                if (hiddenBadges.contains(badgeName)) {
                    param.setResult(null);
                }
                if (prefFFZModBadge && customModBadge != null && badgeName.equals("moderator")) {
                    if (customModBadgeImage == null || !getObjectField(customModBadgeImage, "url").equals(customModBadge)) {
                        customModBadgeImage = newInstance(chatBadgeImageClass);
                        setObjectField(customModBadgeImage, "url", customModBadge);
                        setFloatField(customModBadgeImage, "scale", customModBadge.charAt(customModBadge.length() - 1));
                    }
                    param.setResult(customModBadgeImage);
                }
            }
        });
    }

    private SpannableStringBuilder injectBadges(XC_MethodHook.MethodHookParam param, SpannableStringBuilder chatMsg, Hashtable customBadges) {
        String chatSender = (String) callMethod(param.args[0], "getUserName");
        int location = 0;
        while (chatMsg.toString().indexOf(".", location) == location) { location += 2; }
        if (location >= 6) { return chatMsg; }
        int badgeCount = location / 2;
        for (Object key : customBadges.keySet()) {
            if (badgeCount >= 3) {
                // Already at 3 badges, anymore will clog up chat box
                return chatMsg;
            }
            String keyString = (String) key;
            if (hiddenBadges.contains(keyString)) {
                continue;
            }
            if (!((ArrayList) ((Hashtable) customBadges.get(keyString)).get("users")).contains(chatSender)) {
                continue;
            }
            String url = (String) ((Hashtable) customBadges.get(keyString)).get("image");
            SpannableString badgeSpan = (SpannableString) callMethod(param.thisObject, "a", url, param.args[1], null, true, null, 47);
            chatMsg.insert(location, badgeSpan);
            location += 2;
            badgeCount++;
        }
        return chatMsg;
    }

    private SpannableStringBuilder injectEmotes(XC_MethodHook.MethodHookParam param, SpannableStringBuilder chatMsg, Hashtable customEmoteHash) {
        for (Object key : customEmoteHash.keySet()) {
            String keyString = (String) key;
            int location;
            if ((location = chatMsg.toString().indexOf(":")) == -1 ) {
                location = 0;
                while (chatMsg.toString().indexOf(".") == location) { location += 2; }
                location = chatMsg.toString().indexOf(" ", location);
            }
            location++;
            int keyLength = keyString.length();
            while ((location = chatMsg.toString().indexOf(keyString, location)) != -1) {
                try {
                    if (chatMsg.charAt(location - 1) != ' ' || chatMsg.charAt(location + keyLength) != ' ') {
                        ++location;
                        continue;
                    }
                } catch(IndexOutOfBoundsException e) {}
                String url = customEmoteHash.get(keyString).toString();
                SpannableString emoteSpan = (SpannableString) callMethod(param.thisObject, "a", url, param.args[1], null, false, null, 63);
                chatMsg.replace(location, location + keyLength, emoteSpan);
            }
        }
        return chatMsg;
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
