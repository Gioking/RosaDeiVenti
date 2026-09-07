# RosaDeiVenti

App Android nativa (Kotlin + Jetpack Compose) che mostra la rosa dei venti a 16 direzioni usando i sensori del telefono.

> **Stato**: progetto recuperato e verificato con build reale (`assembleDebug` → BUILD SUCCESSFUL).
> Fix applicato: il plugin `org.jetbrains.kotlin.plugin.compose` non esiste per Kotlin 1.9.22 (introdotto solo da Kotlin 2.0+),
> quindi Kotlin è stato allineato a `2.0.21` e rimosso il vecchio blocco `composeOptions.kotlinCompilerExtensionVersion`
> (superfluo con il nuovo plugin). Aggiunti anche `gradlew`/`gradlew.bat`/`gradle-wrapper.jar` mancanti.

## Funzionamento
- Accelerometro + Magnetometro (o Rotation Vector se disponibile)
- Calcolo azimuth con `SensorManager.getRotationMatrix()` + `getOrientation()`
- Mapping 0-360° -> 16 venti (ampiezza 22.5° ciascuno)
- Ago rosso = direzione puntata = provenienza del vento

## Apertura in Android Studio
1. Apri `C:\temp\RosaDeiVenti` in Android Studio Hedgehog o successivo
2. Sync Gradle
3. Run su dispositivo fisico (i sensori non funzionano bene su emulatore)

## Build APK
./gradlew assembleDebug
# APK in app/build/outputs/apk/debug/app-debug.apk

## Installare l'APK già pronto senza Android Studio
Se hai solo il file `app-debug.apk` (già compilato):
1. Copialo sul telefono (email, drive, cavo USB...)
2. Sul telefono, apri il file dal file manager: Android chiederà di abilitare "Installa da fonti sconosciute" per quell'app
3. Installa e apri "Rosa dei Venti"
4. Concedi eventuali permessi richiesti e tieni il telefono lontano da metalli/calamite per una lettura stabile della bussola

## Venti (16)
N Tramontana 0°, NNE 22.5°, NE Grecale 45°, ENE 67.5°, E Levante 90°, ESE 112.5°, SE Scirocco 135°, SSE 157.5°, S Ostro 180°, SSW 202.5°, SW Libeccio 225°, WSW 247.5°, W Ponente 270°, WNW 292.5°, NW Maestrale 315°, NNW 337.5°

## Uso
Tieni il telefono orizzontale e punta il bordo superiore verso la provenienza del vento. L'ago rosso indicherà il settore sulla rosa.
Se la bussola è instabile: fai un movimento a "8" lontano da metalli.
