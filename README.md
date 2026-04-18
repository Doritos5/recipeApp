# RecipeApp

## Overview
RecipeApp is an Android app for browsing and managing recipes with user accounts, profiles, and a nearby supermarkets map. It combines a public recipe API with user-generated content stored in Firebase, plus a local Room cache for fast access.

## Features
- Browse recipes from a public API and user-created recipes from Firebase.
- Create, edit, delete recipes with tags, images, ingredients, and instructions.
- Like and comment on recipes.
- User authentication (email/password) and profile editing.
- Profile image upload (Base64 by default; Firebase Storage optional).
- Nearby supermarkets list + map using OpenStreetMap Overpass.
- Address fallback via Nominatim reverse geocoding when OSM tags are missing.
- Guest mode with limited access.

## Tech Stack
- Kotlin, AndroidX, Material Design
- Hilt for DI
- Room for local cache
- Firebase Auth + Firestore (Storage optional)
- Retrofit + OkHttp
- OpenStreetMap Overpass + Nominatim

## Setup
1. Open the project in Android Studio.
2. Add your Firebase config:
   - Place `google-services.json` in `app/`.
   - Enable Email/Password authentication in Firebase Auth.
   - Enable Firestore.
3. (Optional) Firebase Storage:
   - Image uploads to Storage require the Blaze plan.
   - On the free plan, the app stores profile/recipe images as Base64 in Firestore.
4. Location permissions are required for the Nearby Supermarkets screen (coarse or fine).

## Run
- Use Android Studio Run, or from terminal:

```powershell
C:\Users\ofir\Documents\GitHub\recipeApp.git\gradlew.bat :app:installDebug
```

## Notes
- Overpass and Nominatim have usage limits; avoid rapid repeated queries.
- The Nearby Supermarkets feature does not require Google Maps.
- If you see "address is not available," the location likely lacks OSM address tags and the reverse geocode may be rate-limited.

## Project Structure
- `app/src/main/java/com/example/recipeapp/ui` — screens/fragments and adapters
- `app/src/main/java/com/example/recipeapp/data` — repositories, DAO, network clients
- `app/src/main/java/com/example/recipeapp/auth` — authentication helpers
- `app/src/main/java/com/example/recipeapp/storage` — image compression and upload
- `app/src/main/java/com/example/recipeapp/di` — Hilt modules
