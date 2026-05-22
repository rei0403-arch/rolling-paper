# Firebase setup for RollingPaper

## 1. Create project

Create a new Firebase project.

Recommended project name:

```text
rolling-paper
```

## 2. Add Android app

Package name:

```text
com.codexplayground.rollingpaper
```

Download `google-services.json` if Firebase offers it. This app does not require the file yet, but it is an easy place to copy the API key from.

## 3. Enable Authentication

Firebase Console > Authentication > Sign-in method:

```text
Anonymous = enabled
```

## 4. Create Firestore

Firebase Console > Firestore Database:

```text
Create database
```

Start in test mode while checking the first prototype, or use the rules below.

## 5. Put config values in code

Edit:

```text
app\src\main\java\com\codexplayground\rollingpaper\FirebaseConfig.java
```

Fill:

```java
static final String API_KEY = "your-api-key";
static final String PROJECT_ID = "your-project-id";
```

You can find `PROJECT_ID` in Firebase Project settings. You can find `API_KEY` in the downloaded `google-services.json` under `api_key.current_key`.

## 6. Prototype Firestore rules

```js
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    match /rooms/{roomId} {
      allow read: if request.auth != null;
      allow create, update: if request.auth != null
        && request.resource.data.keys().hasOnly(['roomCode', 'ownerUid', 'updatedAt'])
        && request.resource.data.roomCode is string
        && request.resource.data.ownerUid == request.auth.uid;
      allow delete: if false;

      match /messages/{messageId} {
        allow read: if request.auth != null;
        allow create: if request.auth != null
          && request.resource.data.keys().hasOnly([
            'roomCode',
            'text',
            'author',
            'authorUid',
            'anonymous',
            'createdAt'
          ])
          && request.resource.data.roomCode is string
          && request.resource.data.text is string
          && request.resource.data.text.size() > 0
          && request.resource.data.text.size() <= 220
          && request.resource.data.author is string
          && request.resource.data.authorUid == request.auth.uid
          && request.resource.data.anonymous is bool
          && request.resource.data.createdAt is int;
        allow update, delete: if false;
      }
    }
  }
}
```
