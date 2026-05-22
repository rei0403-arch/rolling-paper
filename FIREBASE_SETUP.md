# Firebase setup for RollingPaper

This app uses Firebase Anonymous Auth and Realtime Database REST streaming.

## Already configured

- Project ID: `rolling-paper-4a35c`
- Package name: `com.codexplayground.rollingpaper`
- Realtime Database URL: `https://rolling-paper-4a35c-default-rtdb.firebaseio.com`
- Anonymous Authentication: enabled

## App config

The app reads Firebase values from:

```text
app\src\main\java\com\codexplayground\rollingpaper\FirebaseConfig.java
```

The Android config file is saved at:

```text
app\google-services.json
```

## Prototype Realtime Database rules

Use these while testing with friends:

```json
{
  "rules": {
    "rooms": {
      "$roomCode": {
        ".read": "auth != null",
        "meta": {
          ".write": "auth != null"
        },
        "messages": {
          ".indexOn": ["createdAt"],
          "$messageId": {
            ".write": "auth != null && !data.exists() && newData.child('authorUid').val() == auth.uid",
            ".validate": "newData.hasChildren(['roomCode', 'text', 'author', 'authorUid', 'anonymous', 'createdAt']) && newData.child('text').isString() && newData.child('text').val().length > 0 && newData.child('text').val().length <= 220"
          }
        }
      }
    }
  }
}
```
