# recipeApp

## Nearby supermarkets (OSM / Overpass)
This app includes a Nearby Supermarkets screen that uses the OpenStreetMap Overpass API to find supermarkets close to the user's current location. No Google Cloud services are required.

### Notes
- Location permissions are required (fine or coarse).
- Overpass API has usage limits; avoid rapid repeat queries.

### Test
Run the unit tests:

```powershell
./gradlew test
```

