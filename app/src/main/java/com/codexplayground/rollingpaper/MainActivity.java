package com.codexplayground.rollingpaper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String PREFS = "rolling_paper";
    private static final int BG = Color.rgb(247, 245, 239);
    private static final int INK = Color.rgb(35, 33, 29);
    private static final int MUTED = Color.rgb(109, 103, 94);
    private static final int ACCENT = Color.rgb(46, 125, 107);
    private static final int ACCENT_DARK = Color.rgb(29, 89, 76);
    private static final int CARD = Color.WHITE;
    private static final int LINE = Color.rgb(221, 215, 202);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private SharedPreferences prefs;
    private EditText roomInput;
    private EditText nicknameInput;
    private EditText messageInput;
    private TextView statusText;
    private TextView currentRoomText;
    private LinearLayout messagesLayout;

    private String currentRoomCode = "";
    private String uid = "";
    private String idToken = "";
    private String streamingRoomCode = "";
    private volatile boolean streamActive;
    private volatile boolean refreshInFlight;
    private Thread streamThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        uid = prefs.getString("uid", "");
        idToken = prefs.getString("idToken", "");
        currentRoomCode = prefs.getString("roomCode", "");

        setContentView(buildContent());
        updateRoomLabel();
        if (FirebaseConfig.isConfigured()) {
            setStatus("준비됨");
        } else {
            setStatus("Firebase 설정 전: 값을 넣으면 온라인 저장이 켜져");
        }
        if (!currentRoomCode.isEmpty() && FirebaseConfig.isConfigured()) {
            refreshMessages();
            startAutoRefresh();
        } else {
            renderEmpty("아직 방에 들어가지 않았어.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (FirebaseConfig.isConfigured() && !currentRoomCode.isEmpty()) {
            startAutoRefresh();
        }
    }

    @Override
    protected void onPause() {
        stopAutoRefresh();
        super.onPause();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(22), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("롤링페이퍼");
        title.setTextColor(INK);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("방 코드를 만들고 공유해서 같이 메시지를 남겨봐.");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle);

        statusText = new TextView(this);
        statusText.setTextColor(ACCENT_DARK);
        statusText.setTextSize(14);
        statusText.setPadding(dp(12), dp(9), dp(12), dp(9));
        statusText.setBackground(rounded(Color.rgb(231, 242, 237), dp(10), 0, 0));
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout roomBox = section(root, dp(16));
        TextView roomTitle = label("방");
        roomBox.addView(roomTitle);

        currentRoomText = new TextView(this);
        currentRoomText.setTextColor(INK);
        currentRoomText.setTextSize(20);
        currentRoomText.setTypeface(Typeface.DEFAULT_BOLD);
        currentRoomText.setPadding(0, dp(4), 0, dp(12));
        roomBox.addView(currentRoomText);

        roomInput = editText("방 코드 입력", true, 16);
        roomInput.setText(currentRoomCode);
        roomBox.addView(roomInput, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout roomButtons = horizontal();
        Button createButton = button("새 방 만들기", true);
        createButton.setOnClickListener(v -> createRoom());
        Button joinButton = button("입장", false);
        joinButton.setOnClickListener(v -> joinRoom());
        roomButtons.addView(createButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        roomButtons.addView(joinButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        roomBox.addView(roomButtons);

        LinearLayout writeBox = section(root, dp(14));
        writeBox.addView(label("남기기"));

        nicknameInput = editText("닉네임", false, 16);
        nicknameInput.setText(prefs.getString("nickname", ""));
        writeBox.addView(nicknameInput, new LinearLayout.LayoutParams(-1, dp(50)));

        messageInput = editText("메시지를 적어줘", false, 220);
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        messageInput.setMinLines(4);
        writeBox.addView(messageInput, new LinearLayout.LayoutParams(-1, dp(132)));

        Button sendButton = button("메시지 붙이기", true);
        sendButton.setOnClickListener(v -> postMessage());
        writeBox.addView(sendButton, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout listHeader = horizontal();
        TextView listTitle = label("메시지");
        listHeader.addView(listTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Button refreshButton = button("새로고침", false);
        refreshButton.setOnClickListener(v -> refreshMessages());
        listHeader.addView(refreshButton, new LinearLayout.LayoutParams(dp(112), dp(44)));
        root.addView(listHeader, new LinearLayout.LayoutParams(-1, -2));

        messagesLayout = new LinearLayout(this);
        messagesLayout.setOrientation(LinearLayout.VERTICAL);
        messagesLayout.setPadding(0, dp(8), 0, 0);
        root.addView(messagesLayout, new LinearLayout.LayoutParams(-1, -2));

        return scroll;
    }

    private LinearLayout section(LinearLayout root, int topMargin) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(14), dp(14), dp(14));
        box.setBackground(rounded(CARD, dp(8), LINE, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, topMargin, 0, dp(14));
        root.addView(box, params);
        return box;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, 0);
        return row;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(MUTED);
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private EditText editText(String hint, boolean allCaps, int maxLength) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(INK);
        input.setHintTextColor(Color.rgb(145, 137, 126));
        input.setTextSize(16);
        input.setSingleLine(maxLength < 100);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(rounded(Color.rgb(252, 251, 247), dp(8), LINE, 1));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        if (allCaps) {
            input.setAllCaps(true);
        }
        return input;
    }

    private Button button(String text, boolean primary) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(15);
        btn.setAllCaps(false);
        btn.setTextColor(primary ? Color.WHITE : ACCENT_DARK);
        btn.setBackground(rounded(primary ? ACCENT : Color.rgb(235, 241, 238), dp(8), primary ? 0 : LINE, 1));
        return btn;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private void createRoom() {
        if (!ensureConfigured()) {
            return;
        }
        hideKeyboard();
        String roomCode = generateRoomCode();
        setStatus("방 만드는 중...");
        new Thread(() -> {
            try {
                ensureSignedIn();
                upsertRoom(roomCode);
                currentRoomCode = roomCode;
                prefs.edit().putString("roomCode", roomCode).apply();
                mainHandler.post(() -> {
                    roomInput.setText(roomCode);
                    updateRoomLabel();
                    setStatus("새 방 생성됨: " + roomCode);
                    renderEmpty("아직 메시지가 없어. 첫 장을 붙여봐.");
                    startAutoRefresh();
                });
            } catch (Exception e) {
                fail("방 만들기 실패", e);
            }
        }).start();
    }

    private void joinRoom() {
        String code = cleanRoomCode(roomInput.getText().toString());
        if (code.isEmpty()) {
            toastDialog("방 코드 필요", "방 코드를 입력하거나 새 방을 만들어줘.");
            return;
        }
        currentRoomCode = code;
        prefs.edit().putString("roomCode", code).apply();
        roomInput.setText(code);
        updateRoomLabel();
        hideKeyboard();
        if (FirebaseConfig.isConfigured()) {
            refreshMessages();
            startAutoRefresh();
        } else {
            setStatus("Firebase 설정 전: 방 코드는 저장했어");
            renderEmpty("Firebase 값을 넣으면 이 방의 메시지를 불러올 수 있어.");
        }
    }

    private void postMessage() {
        if (!ensureConfigured()) {
            return;
        }
        String code = cleanRoomCode(currentRoomCode.isEmpty() ? roomInput.getText().toString() : currentRoomCode);
        String text = messageInput.getText().toString().trim();
        String nickname = nicknameInput.getText().toString().trim();

        if (code.isEmpty()) {
            toastDialog("방 코드 필요", "먼저 방을 만들거나 방 코드로 입장해줘.");
            return;
        }
        if (text.isEmpty()) {
            toastDialog("메시지 필요", "붙일 메시지를 적어줘.");
            return;
        }
        if (nickname.isEmpty()) {
            toastDialog("닉네임 필요", "친구들이 알아볼 수 있게 닉네임을 써줘.");
            return;
        }

        currentRoomCode = code;
        prefs.edit()
                .putString("roomCode", code)
                .putString("nickname", nickname)
                .apply();
        updateRoomLabel();
        hideKeyboard();
        setStatus("메시지 붙이는 중...");

        new Thread(() -> {
            try {
                ensureSignedIn();
                uploadMessage(code, text, nickname);
                mainHandler.post(() -> {
                    messageInput.setText("");
                    setStatus("메시지 붙였어");
                    refreshMessages();
                    startAutoRefresh();
                });
            } catch (Exception e) {
                fail("메시지 업로드 실패", e);
            }
        }).start();
    }

    private void refreshMessages() {
        refreshMessages(false);
    }

    private void refreshMessages(boolean quiet) {
        if (!FirebaseConfig.isConfigured()) {
            if (!quiet) {
                showFirebaseGuide();
            }
            return;
        }
        String code = cleanRoomCode(currentRoomCode.isEmpty() ? roomInput.getText().toString() : currentRoomCode);
        if (code.isEmpty()) {
            if (!quiet) {
                renderEmpty("방에 들어가면 메시지가 여기에 보여.");
            }
            return;
        }
        if (refreshInFlight) {
            return;
        }
        refreshInFlight = true;
        currentRoomCode = code;
        prefs.edit().putString("roomCode", code).apply();
        updateRoomLabel();
        if (!quiet) {
            setStatus("메시지 불러오는 중...");
        }

        new Thread(() -> {
            try {
                ensureSignedIn();
                JSONArray docs = fetchMessages(code);
                mainHandler.post(() -> {
                    renderMessages(docs);
                    setStatus(docs.length() + "개 확인됨 / 실시간 연결됨");
                });
            } catch (Exception e) {
                if (!quiet) {
                    fail("불러오기 실패", e);
                } else {
                    mainHandler.post(() -> setStatus("자동 새로고침 실패: " + shortError(e)));
                }
            } finally {
                refreshInFlight = false;
            }
        }).start();
    }

    private void startAutoRefresh() {
        String code = cleanRoomCode(currentRoomCode);
        if (!FirebaseConfig.isConfigured() || code.isEmpty()) {
            return;
        }
        if (streamActive && code.equals(streamingRoomCode)) {
            return;
        }
        stopAutoRefresh();
        streamActive = true;
        streamingRoomCode = code;
        streamThread = new Thread(() -> streamMessages(code), "rolling-paper-rtdb-stream");
        streamThread.start();
        setStatus("실시간 연결 중...");
    }

    private void stopAutoRefresh() {
        streamActive = false;
        streamingRoomCode = "";
        if (streamThread != null) {
            streamThread.interrupt();
            streamThread = null;
        }
    }

    private void streamMessages(String roomCode) {
        while (streamActive && roomCode.equals(streamingRoomCode)) {
            HttpURLConnection connection = null;
            try {
                ensureSignedIn();
                String endpoint = rtdbUrl("rooms/" + path(roomCode) + "/messages.json")
                        + "&orderBy=%22createdAt%22&limitToLast=80";
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(0);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setRequestProperty("Cache-Control", "no-cache");

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new RuntimeException("HTTP " + code + " " + readFully(connection.getErrorStream()));
                }

                mainHandler.post(() -> {
                    setStatus("실시간 연결됨");
                    refreshMessages(true);
                });

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    String event = "";
                    while (streamActive
                            && roomCode.equals(streamingRoomCode)
                            && (line = reader.readLine()) != null) {
                        if (line.startsWith("event:")) {
                            event = line.substring("event:".length()).trim();
                        } else if (line.startsWith("data:")) {
                            String data = line.substring("data:".length()).trim();
                            if ("put".equals(event) || "patch".equals(event)) {
                                mainHandler.post(() -> refreshMessages(true));
                            }
                            event = "";
                        }
                    }
                }
            } catch (Exception e) {
                if (streamActive && roomCode.equals(streamingRoomCode)) {
                    String message = shortError(e);
                    mainHandler.post(() -> setStatus("실시간 재연결 중: " + message));
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private boolean ensureConfigured() {
        if (FirebaseConfig.isConfigured()) {
            return true;
        }
        showFirebaseGuide();
        return false;
    }

    private void showFirebaseGuide() {
        setStatus("FirebaseConfig 값 필요");
        new AlertDialog.Builder(this)
                .setTitle("Firebase 연결 전")
                .setMessage("코드는 준비됐어.\n\nFirebase 프로젝트를 새로 만들고 API_KEY, PROJECT_ID를 FirebaseConfig.java에 넣으면 온라인 롤링페이퍼로 동작해.")
                .setPositiveButton("확인", null)
                .show();
    }

    private void ensureSignedIn() throws Exception {
        long savedAt = prefs.getLong("idTokenAt", 0L);
        boolean fresh = !uid.isEmpty()
                && !idToken.isEmpty()
                && savedAt > 0L
                && System.currentTimeMillis() - savedAt < 50L * 60L * 1000L;
        if (fresh) {
            return;
        }

        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key="
                + url(FirebaseConfig.API_KEY);
        JSONObject response = new JSONObject(http("POST", url, "{\"returnSecureToken\":true}", null));
        uid = response.getString("localId");
        idToken = response.getString("idToken");
        prefs.edit()
                .putString("uid", uid)
                .putString("idToken", idToken)
                .putLong("idTokenAt", System.currentTimeMillis())
                .apply();
    }

    private void upsertRoom(String roomCode) throws Exception {
        JSONObject meta = new JSONObject();
        meta.put("roomCode", roomCode);
        meta.put("ownerUid", uid);
        meta.put("updatedAt", System.currentTimeMillis());

        String endpoint = rtdbUrl("rooms/" + path(roomCode) + "/meta.json");
        http("PUT", endpoint, meta.toString(), null);
    }

    private void uploadMessage(String roomCode, String text, String author) throws Exception {
        long now = System.currentTimeMillis();
        JSONObject message = new JSONObject();
        message.put("roomCode", roomCode);
        message.put("text", text);
        message.put("author", author);
        message.put("authorUid", uid);
        message.put("createdAt", now);

        String endpoint = rtdbUrl("rooms/" + path(roomCode) + "/messages.json");
        http("POST", endpoint, message.toString(), null);
    }

    private JSONArray fetchMessages(String roomCode) throws Exception {
        String endpoint = rtdbUrl("rooms/" + path(roomCode) + "/messages.json")
                + "&orderBy=%22createdAt%22&limitToLast=80";
        String raw = http("GET", endpoint, null, null);
        if (raw == null || raw.trim().isEmpty() || "null".equals(raw.trim())) {
            return new JSONArray();
        }

        JSONObject messages = new JSONObject(raw);
        List<JSONObject> sorted = new ArrayList<>();
        Iterator<String> keys = messages.keys();
        while (keys.hasNext()) {
            JSONObject message = messages.optJSONObject(keys.next());
            if (message != null) {
                sorted.add(message);
            }
        }
        Collections.sort(sorted, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject left, JSONObject right) {
                return Long.compare(right.optLong("createdAt", 0L), left.optLong("createdAt", 0L));
            }
        });

        JSONArray docs = new JSONArray();
        for (JSONObject message : sorted) {
            docs.put(message);
        }
        return docs;
    }

    private String rtdbUrl(String pathAndQuery) {
        String base = FirebaseConfig.DATABASE_URL.endsWith("/")
                ? FirebaseConfig.DATABASE_URL.substring(0, FirebaseConfig.DATABASE_URL.length() - 1)
                : FirebaseConfig.DATABASE_URL;
        String separator = pathAndQuery.contains("?") ? "&" : "?";
        return base + "/" + pathAndQuery + separator + "auth=" + url(idToken);
    }

    private JSONObject stringValue(String value) throws Exception {
        return new JSONObject().put("stringValue", value);
    }

    private JSONObject intValue(long value) throws Exception {
        return new JSONObject().put("integerValue", Long.toString(value));
    }

    private JSONObject boolValue(boolean value) throws Exception {
        return new JSONObject().put("booleanValue", value);
    }

    private void renderMessages(JSONArray docs) {
        messagesLayout.removeAllViews();
        if (docs.length() == 0) {
            renderEmpty("아직 메시지가 없어. 첫 장을 붙여봐.");
            return;
        }

        for (int i = 0; i < docs.length(); i++) {
            JSONObject fields = docs.optJSONObject(i);
            if (fields == null) {
                continue;
            }
            addMessageCard(
                    getStringField(fields, "author", "친구"),
                    getStringField(fields, "text", ""),
                    getLongField(fields, "createdAt", 0L)
            );
        }
    }

    private void renderEmpty(String message) {
        messagesLayout.removeAllViews();
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(MUTED);
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(26), dp(16), dp(26));
        empty.setBackground(rounded(Color.rgb(252, 251, 247), dp(8), LINE, 1));
        messagesLayout.addView(empty, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addMessageCard(String author, String text, long createdAt) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(CARD, dp(8), LINE, 1));

        LinearLayout top = horizontal();
        top.setPadding(0, 0, 0, dp(8));
        TextView name = new TextView(this);
        name.setText(author);
        name.setTextColor(ACCENT_DARK);
        name.setTextSize(14);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));

        TextView time = new TextView(this);
        time.setText(formatTime(createdAt));
        time.setTextColor(MUTED);
        time.setTextSize(12);
        top.addView(time);
        card.addView(top);

        TextView body = new TextView(this);
        body.setText(text);
        body.setTextColor(INK);
        body.setTextSize(17);
        body.setLineSpacing(dp(2), 1.0f);
        card.addView(body);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        messagesLayout.addView(card, params);
    }

    private String getStringField(JSONObject fields, String name, String fallback) {
        Object field = fields.opt(name);
        if (field == null || JSONObject.NULL.equals(field)) {
            return fallback;
        }
        if (field instanceof JSONObject) {
            return ((JSONObject) field).optString("stringValue", fallback);
        }
        String value = String.valueOf(field);
        return value.trim().isEmpty() ? fallback : value;
    }

    private long getLongField(JSONObject fields, String name, long fallback) {
        Object field = fields.opt(name);
        if (field == null || JSONObject.NULL.equals(field)) {
            return fallback;
        }
        if (field instanceof Number) {
            return ((Number) field).longValue();
        }
        String raw = field instanceof JSONObject
                ? ((JSONObject) field).optString("integerValue", "")
                : String.valueOf(field);
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String http(String method, String urlText, String body, String bearerToken) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (bearerToken != null && !bearerToken.isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
        }
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
            }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String text = readFully(stream);
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new RuntimeException("HTTP " + code + " " + text);
        }
        return text;
    }

    private String readFully(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private void updateRoomLabel() {
        String code = cleanRoomCode(currentRoomCode);
        currentRoomText.setText(code.isEmpty() ? "방 없음" : code);
    }

    private String generateRoomCode() {
        char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            builder.append(alphabet[random.nextInt(alphabet.length)]);
        }
        return builder.toString();
    }

    private String cleanRoomCode(String value) {
        if (value == null) {
            return "";
        }
        return value.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]", "");
    }

    private String formatTime(long millis) {
        if (millis <= 0L) {
            return "";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.KOREA)
                .format(new Date(millis));
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private void fail(String title, Exception e) {
        String message = shortError(e);
        mainHandler.post(() -> {
            setStatus(title + ": " + message);
            toastDialog(title, message);
        });
    }

    private String shortError(Exception e) {
        String text = e.getMessage();
        if (text == null || text.trim().isEmpty()) {
            text = e.getClass().getSimpleName();
        }
        return text.length() > 220 ? text.substring(0, 220) + "..." : text;
    }

    private void toastDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
            view.clearFocus();
        }
    }

    private String url(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private String path(String text) {
        return url(text).replace("+", "%20");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
