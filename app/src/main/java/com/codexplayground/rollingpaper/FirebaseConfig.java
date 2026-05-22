package com.codexplayground.rollingpaper;

final class FirebaseConfig {
    static final String API_KEY = "AIzaSyCm1-NU0rfsWUbiWft-DzRgIzOYGdUyMQY";
    static final String PROJECT_ID = "rolling-paper-4a35c";
    static final String DATABASE_URL = "https://rolling-paper-4a35c-default-rtdb.firebaseio.com";

    private FirebaseConfig() {
    }

    static boolean isConfigured() {
        return !API_KEY.startsWith("PASTE_")
                && !PROJECT_ID.startsWith("PASTE_")
                && !DATABASE_URL.startsWith("PASTE_")
                && !API_KEY.trim().isEmpty()
                && !PROJECT_ID.trim().isEmpty()
                && !DATABASE_URL.trim().isEmpty();
    }
}
