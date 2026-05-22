# RollingPaper

Android rolling-paper prototype with Firebase anonymous auth and Firestore REST.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Built APK:

```text
out\rolling-paper-debug.apk
```

## Firebase setup

Edit:

```text
app\src\main\java\com\codexplayground\rollingpaper\FirebaseConfig.java
```

Set:

- `API_KEY`
- `PROJECT_ID`

Firebase console steps:

1. Create a new Firebase project.
2. Add an Android app with package `com.codexplayground.rollingpaper`.
3. Enable Authentication > Sign-in method > Anonymous.
4. Create Firestore Database.
5. Use test rules while prototyping, then tighten later.
