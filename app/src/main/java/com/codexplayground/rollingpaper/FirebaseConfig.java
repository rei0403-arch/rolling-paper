package com.codexplayground.rollingpaper;

final class FirebaseConfig {
    static final String API_KEY = "PASTE_FIREBASE_WEB_API_KEY_HERE";
    static final String PROJECT_ID = "PASTE_FIREBASE_PROJECT_ID_HERE";
    static final String DATABASE_ID = "(default)";

    private FirebaseConfig() {
    }

    static boolean isConfigured() {
        return !API_KEY.startsWith("PASTE_")
                && !PROJECT_ID.startsWith("PASTE_")
                && !API_KEY.trim().isEmpty()
                && !PROJECT_ID.trim().isEmpty();
    }
}
