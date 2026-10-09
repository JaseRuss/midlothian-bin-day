# Midlothian Bin Day

An Android app for Midlothian Council bin collections: upcoming collections, a home-screen widget showing
days until each bin's next collection, and a reminder notification the evening before a collection.

## Features
- Upcoming collections with coloured bin chips, plus "Show more dates".
- 3x1 home-screen widget: five coloured buttons (plastic, glass, general, paper, food) with days until next collection.
- Optional reminder notification the day before a collection (default 18:00, adjustable).
- Import a new schedule from the council's PDF.

## Getting your schedule
1. Go to <https://my.midlothian.gov.uk/service/Bin_Collection_Dates>.
2. Enter your postcode and choose your address.
3. Select **Generate PDF** and download the file.
4. In the app tap **Import PDF** and pick the downloaded file.

The PDF is read on the phone; nothing is uploaded anywhere. On first launch the app asks whether you live on
Sycamore Drive, Penicuik (the built-in sample schedule) - answer No to clear it and import your own.

## Building
Open the folder in Android Studio, or run `./gradlew assembleDebug` (JDK 17, Android SDK 34).
Create `local.properties` with `sdk.dir=<path to your Android SDK>` if Android Studio hasn't done so.
