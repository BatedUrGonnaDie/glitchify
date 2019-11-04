package com.leagueofnewbs.glitchify;

import android.os.Build;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.util.Log;

import static de.robv.android.xposed.XposedHelpers.*;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public class Main implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static XSharedPreferences pref;
    private Preferences preferences;
    private final ColorHelper colorHelper = ColorHelper.getInstance();
    private final Hashtable<String, String> ffzRoomEmotes = new Hashtable<>();
    private final Hashtable<String, String> ffzGlobalEmotes = new Hashtable<>();
    private final Hashtable<String, Hashtable<String, Object>> ffzBadges = new Hashtable<>();
    private final Hashtable<String, String> bttvRoomEmotes = new Hashtable<>();
    private final Hashtable<String, String> bttvGlobalEmotes = new Hashtable<>();
    private final Hashtable<String, Hashtable<String, Object>> bttvBadges = new Hashtable<>();
    private static Object customModBadgeImage;
    private static final String ffzAPIURL = "https://api.frankerfacez.com/v1/";
    private static final String bttvAPIURL = "https://api.betterttv.net/2/";
    private static final String logTag = "Glitchify";

    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            pref = new XSharedPreferences(new File("/data/user_de/0/com.leagueofnewbs.glitchify/shared_prefs/preferences.xml"));
        } else {
            //noinspection ConstantConditions
            pref = new XSharedPreferences(Main.class.getPackage().getName(), "preferences");
        }
    }

    @SuppressWarnings("RedundantThrows")
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("tv.twitch.android.app") || !lpparam.isFirstApplication) {
            return;
        }

        preferences = new Preferences(pref);

        // Get all global info that we can all at once
        // FFZ/BTTV global emotes, global twitch badges, and FFZ mod badge
        Thread globalThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (preferences.ffzEmotes()) {
                        getFFZGlobalEmotes();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global FFZ emotes > ");
                }
                try {
                    if (preferences.bttvEmotes()) {
                        getBTTVGlobalEmotes();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global BTTV emotes > ");
                }
                try {
                    if (preferences.ffzBadges()) {
                        getFFZBadges();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global FFZ badges > ");
                }
                try {
                    if (preferences.bttvBadges()) {
                        getBTTVBadges();
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching global BTTV badges > ");
                }
            }
        });
        globalThread.start();


        // These are all the different class definitions that are needed in the function hooking
        final Class<?> chatControllerClass = findClass("tv.twitch.a.k.c0", lpparam.classLoader);
        final Class<?> chatUpdaterClass = findClass("tv.twitch.a.k.c0$h", lpparam.classLoader);
        final Class<?> chatViewPresenterClass = findClass("tv.twitch.a.o.h.f", lpparam.classLoader);
        final Class<?> messageRecyclerItemClass = findClass("tv.twitch.android.adapters.k.e", lpparam.classLoader);
        final Class<?> channelChatAdapterClass = findClass("tv.twitch.a.m.d.b0.a", lpparam.classLoader);
        final Class<?> chatUtilClass = findClass("tv.twitch.a.m.d.z0.c", lpparam.classLoader);
        final Class<?> deletedMessageClickableSpanClass = findClass("tv.twitch.a.m.d.z0.g", lpparam.classLoader);
        final Class<?> systemMessageTypeClass = findClass("tv.twitch.a.m.d.b0.f", lpparam.classLoader);
        final Class<?> chatMessageFactoryClass = findClass("tv.twitch.a.o.a", lpparam.classLoader);
        final Class<?> clickableUsernameSpanClass = findClass("tv.twitch.a.m.d.z0.e", lpparam.classLoader);
        final Class<?> iClickableUsernameSpanListenerClass = findClass("tv.twitch.a.m.d.h0.b", lpparam.classLoader);
        final Class<?> twitchUrlSpanInterfaceClass = findClass("tv.twitch.android.util.androidUI.TwitchURLSpan$a", lpparam.classLoader);
        final Class<?> censoredMessageTrackingInfoClass = findClass("tv.twitch.a.m.d.x0.c", lpparam.classLoader);
        final Class<?> webViewDialogFragmentEnumClass = findClass("tv.twitch.android.app.core.z1$b", lpparam.classLoader);
        final Class<?> chatMessageInterfaceClass = findClass("tv.twitch.a.m.d.e", lpparam.classLoader);
        final Class<?> chatBadgeImageClass = findClass("tv.twitch.chat.ChatBadgeImage", lpparam.classLoader);
        final Class<?> bitsTokenClass = findClass("tv.twitch.android.models.chat.MessageToken$BitsToken", lpparam.classLoader);
        final Class<?> cheermotesHelperClass = findClass("tv.twitch.android.shared.bits.w.f", lpparam.classLoader);
        final Class<?> chommentModelDelegateClass = findClass("tv.twitch.android.models.ChommentModelDelegate", lpparam.classLoader);
        final Class<?> channelInfoClass = findClass("tv.twitch.android.models.channel.ChannelInfo", lpparam.classLoader);
        final Class<?> streamTypeClass = findClass("tv.twitch.android.models.streams.StreamType", lpparam.classLoader);
        //noinspection unchecked
        final Class<? extends Enum> urlDrawableClass = (Class<? extends Enum>) findClass("tv.twitch.a.m.r.b.p.g$b", lpparam.classLoader);
        final Class<?> vodPlayerPresenterClass = findClass("tv.twitch.a.m.k.e0.q", lpparam.classLoader);
        final Class<?> vodModelClass = findClass("tv.twitch.android.models.videos.VodModel", lpparam.classLoader);
        final Class<?> videoAdManagerClass = findClass("tv.twitch.android.player.ads.VideoAdManager", lpparam.classLoader);
        // Updated combined bits insertion object field to find bits helper in ChatMessageFactory

        // This is called when a vod chat widget gets a channel name attached to it
        // It sets up all the channel specific stuff (bttv/ffz emotes, etc)
        findAndHookMethod(vodPlayerPresenterClass, "a", vodModelClass, int.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                final String channelInfo = (String) callMethod(param.args[0], "getChannelName");
                getRoomEmotes(channelInfo);
            }
        });

        // This is called when a live chat widget gets a channel name attached to it
        // It sets up all the channel specific stuff (bttv/ffz emotes, etc)
        findAndHookMethod(chatViewPresenterClass, "a", channelInfoClass, String.class, streamTypeClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final String channelInfo = (String) callMethod(getObjectField(param.thisObject, "k"), "getName");
                getRoomEmotes(channelInfo);
            }
        });

        // This is what actually goes through and strikes out the messages
        // If show deleted is false this will replace with <message deleted>
        findAndHookMethod(chatUtilClass, "a", Spanned.class, String.class, deletedMessageClickableSpanClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (preferences.showDeletedMessages()) {
                    Spanned messageSpan = (Spanned) param.args[0];
                    Object[] spans = messageSpan.getSpans(0, messageSpan.length(), clickableUsernameSpanClass);
                    if ((spans.length == 0 ? 1 : null) != null) {
                        param.setResult(null);
                        return;
                    }

                    int spanEnd = messageSpan.getSpanEnd(spans[0]);
                    int length = 2 + spanEnd;
                    if (length < messageSpan.length() && messageSpan.subSequence(spanEnd, length).toString().equals(": ")) {
                        spanEnd = length;
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
        findAndHookConstructor(messageRecyclerItemClass, "androidx.fragment.app.FragmentActivity", String.class, int.class, String.class, String.class, int.class, Spanned.class, systemMessageTypeClass, float.class, int.class, float.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (preferences.showTimeStamps()) {
                    SimpleDateFormat formatter = new SimpleDateFormat("h:mm ", Locale.US);
                    SpannableString dateString = SpannableString.valueOf(formatter.format(new Date()));
                    dateString.setSpan(new RelativeSizeSpan(0.75f), 0, dateString.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    CharSequence messageSpan = (CharSequence) param.args[6];
                    SpannableStringBuilder message = new SpannableStringBuilder(dateString);
                    message.append(messageSpan);
                    param.args[6] = SpannedString.valueOf(message);
                }
            }
        });

        // Override complete chat clears
        findAndHookMethod(chatUpdaterClass, "chatChannelMessagesCleared", int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (preferences.preventChatClear()) {
                    param.setResult(null);
                }
            }
        });
        XposedBridge.hookAllMethods(chatUpdaterClass, "chatChannelModNoticeClearChat", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (preferences.preventChatClear()) {
                    param.setResult(null);
                }
            }
        });

        // Prevent overriding of chat history length
        findAndHookConstructor(channelChatAdapterClass, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[0] = preferences.chatScrollbackLength();
            }
        });

        // Inject all badges and emotes into the finished message
        findAndHookMethod(chatMessageFactoryClass, "a", chatMessageInterfaceClass, boolean.class, boolean.class, boolean.class, int.class, int.class, iClickableUsernameSpanListenerClass, twitchUrlSpanInterfaceClass, webViewDialogFragmentEnumClass, String.class, boolean.class, censoredMessageTrackingInfoClass, Integer.class, new XC_MethodHook() {
            @Override
            protected void  beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (preferences.bitsCombine() && !chommentModelDelegateClass.isInstance(param.args[0])) {
                    setAdditionalInstanceField(param.thisObject, "allowBitInsertion", false);
                }
                if (preferences.colorAdjust()) {
                    Integer color = (Integer) param.args[4];
                    Integer newColor = colorHelper.maybeBrighten(color, preferences.darkMode());
                    param.args[4] = newColor;
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                SpannableStringBuilder msg = new SpannableStringBuilder((SpannedString) param.getResult());

                if (preferences.ffzBadges()) {
                    msg = injectBadges(param, urlDrawableClass, msg, ffzBadges);
                }
                if (preferences.bttvBadges()) {
                    msg = injectBadges(param, urlDrawableClass, msg, bttvBadges);
                }
                if (preferences.ffzEmotes()) {
                    msg = injectEmotes(param, urlDrawableClass, msg, ffzGlobalEmotes);
                    msg = injectEmotes(param, urlDrawableClass, msg, ffzRoomEmotes);
                }
                if (preferences.bttvEmotes()) {
                    msg = injectEmotes(param, urlDrawableClass, msg, bttvGlobalEmotes);
                    msg = injectEmotes(param, urlDrawableClass, msg, bttvRoomEmotes);
                }
                if (preferences.bitsCombine() && !chommentModelDelegateClass.isInstance(param.args[0])) {
                    setAdditionalInstanceField(param.thisObject, "allowBitInsertion", true);
                    Object chatMessageInfo = getObjectField(param.args[0], "a");
                    int numBits = getIntField(chatMessageInfo, "numBitsSent");
                    if (numBits > 0) {
                        Object bit = newInstance(bitsTokenClass, "cheer", numBits);
                        SpannableString bitString = (SpannableString) callMethod(param.thisObject, "a", bit, getObjectField(param.thisObject, "b"));
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
        findAndHookMethod(chatMessageFactoryClass, "a", bitsTokenClass, cheermotesHelperClass, new XC_MethodHook() {
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
                if (preferences.hiddenBadges().contains(badgeName)) {
                    param.setResult(null);
                }
                if (preferences.ffzModBadge() && !preferences.ffzModBadgeURL().equals("") && badgeName.equals("moderator")) {
                    // Set and save a badge image to be reused for all messages
                    if (customModBadgeImage == null || !getObjectField(customModBadgeImage, "url").equals(preferences.ffzModBadgeURL())) {
                        customModBadgeImage = newInstance(chatBadgeImageClass);
                        setObjectField(customModBadgeImage, "url", preferences.ffzModBadgeURL());
                        setFloatField(customModBadgeImage, "scale", preferences.ffzModBadgeScale());
                    }
                    param.setResult(customModBadgeImage);
                }
            }
        });

        XposedBridge.hookAllMethods(videoAdManagerClass, "requestAds", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (preferences.hideAds()) {
                    param.setResult(null);
                }
            }
        });
    }

    private SpannableStringBuilder injectBadges(XC_MethodHook.MethodHookParam param, Class urlDrawableClass, SpannableStringBuilder chatMsg, Hashtable customBadges) {
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
            if (preferences.hiddenBadges().contains(keyString)) {
                continue;
            }
            if (!((ArrayList) ((Hashtable) customBadges.get(keyString)).get("users")).contains(chatSender)) {
                continue;
            }
            String url = (String) ((Hashtable) customBadges.get(keyString)).get("image");
            SpannableString badgeSpan = (SpannableString) callMethod(param.thisObject, "a", param.thisObject, url, Enum.valueOf(urlDrawableClass, "Badge"), null, true, 4, null);
            chatMsg.insert(location, badgeSpan);
            location += 2;
            badgeCount++;
        }
        return chatMsg;
    }

    private SpannableStringBuilder injectEmotes(XC_MethodHook.MethodHookParam param, Class urlDrawableClass, SpannableStringBuilder chatMsg, Hashtable customEmoteHash) {
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
                } catch(IndexOutOfBoundsException e) {
                    // End of line reached
                }
                //noinspection ConstantConditions
                String url = customEmoteHash.get(keyString).toString();
                SpannableString emoteSpan = (SpannableString) callMethod(param.thisObject, "a", param.thisObject, url, Enum.valueOf(urlDrawableClass, "Emote"), null, false, 12, null);
                chatMsg.replace(location, location + keyLength, emoteSpan);
            }
        }
        return chatMsg;
    }

    private void getRoomEmotes(String channel) {
        final String channelInfo = channel;
        Thread roomThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (preferences.ffzEmotes()) {
                        getFFZRoomEmotes(channelInfo);
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching FFZ emotes for " + channelInfo + " > ");
                }
                try {
                    if (preferences.bttvEmotes()) {
                        getBTTVRoomEmotes(channelInfo);
                    }
                } catch (Exception e) {
                    printException(e, "Error fetching BTTV emotes for " + channelInfo + " > ");
                }
            }
        });
        roomThread.start();
    }

    private void getFFZRoomEmotes(String channel) throws Exception {
        ffzRoomEmotes.clear();
        URL roomURL = new URL(ffzAPIURL + "room/" + channel);
        JSONObject roomEmotes = getJSON(roomURL);
        try {
            int status = roomEmotes.getInt("status");
            if (status == 404) {
                preferences.ffzModBadgeURL("");
                return;
            }
        } catch (JSONException e) {
            // Required to compile
        }
        int set = roomEmotes.getJSONObject("room").getInt("set");
        if (roomEmotes.getJSONObject("room").isNull("moderator_badge")) {
            preferences.ffzModBadgeURL("");
        } else {
            JSONObject modURLs = roomEmotes.getJSONObject("room").getJSONObject("mod_urls");
            String url = modURLs.getString("1");
            if (modURLs.has("2")) {
                url = modURLs.getString("2");
                preferences.ffzModBadgeScale(2);
            }
            preferences.ffzModBadgeURL("https:" + url + "/solid");
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

    @SuppressWarnings({"unchecked", "ConstantConditions"})
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
        bttvRoomEmotes.clear();
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

    @SuppressWarnings({"unchecked", "ConstantConditions"})
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
        Log.e(logTag, output, e);
    }

}
