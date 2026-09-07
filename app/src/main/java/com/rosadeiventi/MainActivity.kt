package com.rosadeiventi

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*

data class Vento(
    val nome: String,
    val abbrev: String,
    val centroGradi: Float,
    val descrizione: String
)

val venti16 = listOf(
    Vento("Tramontana", "N", 0f, "Nord"),
    Vento("Tramontana-Greco", "NNE", 22.5f, "Nord-Nord-Est"),
    Vento("Grecale", "NE", 45f, "Nord-Est"),
    Vento("Greco-Levante", "ENE", 67.5f, "Est-Nord-Est"),
    Vento("Levante", "E", 90f, "Est"),
    Vento("Levante-Scirocco", "ESE", 112.5f, "Est-Sud-Est"),
    Vento("Scirocco", "SE", 135f, "Sud-Est"),
    Vento("Ostro-Scirocco", "SSE", 157.5f, "Sud-Sud-Est"),
    Vento("Ostro", "S", 180f, "Sud"),
    Vento("Ostro-Libeccio", "SSW", 202.5f, "Sud-Sud-Ovest"),
    Vento("Libeccio", "SW", 225f, "Sud-Ovest"),
    Vento("Ponente-Libeccio", "WSW", 247.5f, "Ovest-Sud-Ovest"),
    Vento("Ponente", "W", 270f, "Ovest"),
    Vento("Maestrale-Ponente", "WNW", 292.5f, "Ovest-Nord-Ovest"),
    Vento("Maestrale", "NW", 315f, "Nord-Ovest"),
    Vento("Tramontana-Maestrale", "NNW", 337.5f, "Nord-Nord-Ovest"),
)

fun getVentoDaGradi(gradi: Float): Vento {
    val normalized = ((gradi % 360) + 360) % 360
    // ogni vento occupa 22.5 gradi (360/16)
    val index = ((normalized + 11.25f) / 22.5f).toInt() % 16
    return venti16[index]
}

// --- LOCALIZZAZIONE: Italiano (primaria) e Inglese (secondaria) ---
enum class Lingua { IT, EN }

// I nomi tradizionali (Tramontana, Grecale, Scirocco...) restano invariati in entrambe le lingue
// (sono nomi propri usati anche in testi inglesi sui venti mediterranei); solo la descrizione
// cardinale viene tradotta.
private val descrizioneVentoEN = mapOf(
    "N" to "North", "NNE" to "North-Northeast", "NE" to "Northeast", "ENE" to "East-Northeast",
    "E" to "East", "ESE" to "East-Southeast", "SE" to "Southeast", "SSE" to "South-Southeast",
    "S" to "South", "SSW" to "South-Southwest", "SW" to "Southwest", "WSW" to "West-Southwest",
    "W" to "West", "WNW" to "West-Northwest", "NW" to "Northwest", "NNW" to "North-Northwest"
)

fun descrizioneVento(v: Vento, lingua: Lingua): String =
    if (lingua == Lingua.IT) v.descrizione else (descrizioneVentoEN[v.abbrev] ?: v.descrizione)

// Tutte le stringhe dell'interfaccia, in italiano o inglese in base a `lingua`
class Testi(val lingua: Lingua) {
    private val en = lingua == Lingua.EN

    val titoloApp = if (en) "Wind Rose" else "Rosa dei Venti"
    val sottotitolo = if (en) "Point the phone toward where the wind is coming from" else "Punta il telefono verso la provenienza del vento"

    val precisioneInAttesa = if (en) "Waiting..." else "In attesa..."
    val precisioneAlta = if (en) "Accuracy: High" else "Precisione: Alta"
    val precisioneMedia = if (en) "Accuracy: Medium" else "Precisione: Media"
    val precisioneBassa = if (en) "Accuracy: Low - calibrate with a figure-8" else "Precisione: Bassa - calibra a 8"
    val precisioneInaffidabile = if (en) "Accuracy: Unreliable - calibrate with a figure-8" else "Precisione: Inaffidabile - calibra a 8"
    val sensoriNonDisponibili = if (en) "Sensors not available" else "Sensori non disponibili"
    val sensoriNonDisponibiliMsg = if (en) "Compass sensors are not available on this device." else "Sensori bussola non disponibili su questo dispositivo."

    val etichettaGradi = if (en) "Degrees" else "Gradi"
    val etichettaDirezione = if (en) "Direction" else "Direzione"
    val etichettaSettore = if (en) "Sector" else "Settore"

    val bottoneSalva = if (en) "💾 Save reading" else "💾 Salva lettura"

    val comeUsareTitolo = if (en) "How to use:" else "Come usare:"
    val comeUsare1 = if (en) "1. Hold the phone horizontal (parallel to the ground)." else "1. Tieni il telefono orizzontale (parallelo al suolo)."
    val comeUsare2 = if (en) "2. Point the top edge of the phone TOWARD where the wind is coming from (e.g. if it hits your face, point straight ahead)." else "2. Punta il lato superiore del telefono VERSO da dove viene il vento (es. se ti arriva in faccia, punta davanti a te)."
    val comeUsare3 = if (en) "3. The red needle shows the direction you're pointing at. Read the wind's name." else "3. L'ago rosso indica la direzione puntata. Leggi il nome del vento."
    val comeUsare4 = if (en) "4. If the compass is unstable, move the phone in a figure-8 to calibrate it, away from metal objects." else "4. Se la bussola balla, fai un movimento a '8' con il telefono per calibrarla lontano da metalli."

    val rosa16Titolo = if (en) "16-point wind rose" else "Rosa a 16 venti"

    val dialogFotoTitolo = if (en) "Add a photo?" else "Aggiungere una foto?"
    val dialogFotoTesto = if (en) "Do you want to take a photo of the place for this reading? You can also add it later from the history." else "Vuoi scattare una foto del posto per questa lettura? Potrai aggiungerla anche più tardi dallo storico."
    val dialogFotoScatta = if (en) "📷 Take photo" else "📷 Scatta foto"
    val dialogFotoNoGrazie = if (en) "No, thanks" else "No, grazie"
    val scattoAnnullato = if (en) "Shot cancelled or failed" else "Scatto annullato o non riuscito"
    val nessunaAppMappe = if (en) "No maps app available" else "Nessuna app mappe disponibile"

    val indietro = if (en) "← Back" else "← Indietro"
    val titoloStorico = if (en) "Reading history" else "Storico letture"
    val vuotoStorico = if (en) "No readings saved yet.\nGo back to the compass and tap \"Save reading\"." else "Nessuna lettura salvata.\nTorna alla bussola e premi \"Salva lettura\"."
    val aggiungiFoto = if (en) "📷 Add photo" else "📷 Aggiungi foto"
    val confermaEliminaTuttoTitolo = if (en) "Delete everything?" else "Eliminare tutto?"
    val eliminaTuttoConferma = if (en) "Delete all" else "Elimina tutto"
    val annulla = if (en) "Cancel" else "Annulla"
    val impossibileAprireFoto = if (en) "Unable to open photo" else "Impossibile aprire la foto"

    val titoloSetup = if (en) "Setup & calibration" else "Setup e calibrazione"
    val percheTitolo = if (en) "Why calibrate the compass?" else "Perché calibrare la bussola?"
    val percheTesto = if (en) "The phone figures out where North is using the magnetometer. With use, or near metal objects and magnets, the reading can \"drift\" from the real direction. If the main screen shows \"Accuracy: Low\" or \"Unreliable\", it's best to recalibrate before trusting the reading." else "Il telefono capisce dove sia il Nord grazie al magnetometro. Con l'uso, o vicino a oggetti metallici e magneti, la lettura può \"deviare\" dalla direzione reale. Se nella schermata principale vedi \"Precisione: Bassa\" o \"Inaffidabile\", conviene ricalibrare prima di fidarti della lettura."
    val calibraTitolo = if (en) "How to calibrate: the figure-8 motion" else "Come calibrare: il movimento a \"8\""
    val calibra1 = if (en) "1. Hold the phone in your hand with the screen facing up, horizontal and parallel to the ground (the same position you use to read the compass) — away from your body and other devices." else "1. Tieni il telefono in mano con lo schermo rivolto verso l'alto, in orizzontale e parallelo al suolo (la stessa posizione che usi per leggere la bussola) — lontano dal corpo e da altri dispositivi."
    val calibra2 = if (en) "2. Slowly move it drawing a \"∞\" (figure-8) in the air, as in the diagram above, keeping it flat." else "2. Muovilo lentamente disegnando una \"∞\" (figura a 8) nell'aria, come nel disegno sopra, mantenendolo piatto."
    val calibra3 = if (en) "3. Repeat the motion for 5-10 seconds, then also rotate your wrists a bit to tilt it forward/backward and sideways, so it turns on multiple axes." else "3. Ripeti il movimento per 5-10 secondi, poi ruota anche un po' i polsi per inclinarlo avanti/indietro e di lato, così da orientarlo su più assi."
    val calibra4 = if (en) "4. Go back to the compass and check that the indicator becomes \"High\" or \"Medium\"." else "4. Torna alla bussola e controlla che l'indicatore diventi \"Alta\" o \"Media\"."
    val disturboTitolo = if (en) "What can disturb the compass" else "Cosa può disturbare la bussola"
    val disturbo1 = if (en) "🧲 Magnets: magnetic phone cases, magnetic car mounts" else "🧲 Magneti: custodie con chiusura magnetica, supporti auto magnetici"
    val disturbo2 = if (en) "🔩 Nearby metal objects: desks, appliances, cables, keys" else "🔩 Oggetti metallici vicini: scrivanie, elettrodomestici, cavi, chiavi"
    val disturbo3 = if (en) "🔊 Speakers or cases with a metal plate" else "🔊 Altoparlanti o cover con placca metallica"
    val disturbo4 = if (en) "🏢 Reinforced concrete / steel structures: if indoors, move closer to a window" else "🏢 Cemento armato / strutture in ferro: se sei in casa, avvicinati a una finestra"
    val disturbo5 = if (en) "📶 Other phones or electronic devices very close by" else "📶 Altri telefoni o dispositivi elettronici molto vicini"
    val indicatoreTitolo = if (en) "How to read the accuracy indicator" else "Come leggere l'indicatore di precisione"
    val livelloAlta = if (en) "High" else "Alta"
    val livelloAltaDesc = if (en) "Reliable reading, no action needed." else "Lettura affidabile, nessuna azione necessaria."
    val livelloMedia = if (en) "Medium" else "Media"
    val livelloMediaDesc = if (en) "Generally reliable for everyday use." else "Generalmente affidabile per l'uso quotidiano."
    val livelloBassa = if (en) "Low" else "Bassa"
    val livelloBassaDesc = if (en) "Figure-8 calibration recommended before trusting it." else "Consigliata la calibrazione a \"8\" prima di fidarsi."
    val livelloInaffidabile = if (en) "Unreliable" else "Inaffidabile"
    val livelloInaffidabileDesc = if (en) "Calibrate now and move away from sources of interference." else "Calibra subito e allontanati da fonti di disturbo."
    val linguaTitolo = if (en) "Language" else "Lingua"
    val linguaSottotitolo = if (en) "Choose the app's language." else "Scegli la lingua dell'app."

    val autoreVoce = if (en) "👤 Author" else "👤 Autore"
    val autoreTitolo = if (en) "Author" else "Autore"
    val autoreRuolo = if (en) "Creator of this app" else "Ideatore di questa app"

    fun letturaSalvata(abbrev: String, gradi: String, conPosizione: Boolean): String {
        val extra = if (conPosizione) " 📍" else ""
        return if (en) "Reading saved: $abbrev (${gradi}°)$extra" else "Lettura salvata: $abbrev (${gradi}°)$extra"
    }

    fun storico(n: Int): String = if (en) "📋 History ($n)" else "📋 Storico ($n)"

    fun eliminaTutto(n: Int): String = if (en) "🗑 Delete all ($n)" else "🗑 Elimina tutto ($n)"

    fun confermaEliminaTuttoTesto(n: Int): String =
        if (en) "All $n saved readings will be deleted. This action cannot be undone."
        else "Verranno eliminate tutte le $n letture salvate. L'operazione non è reversibile."
}

val LocalStrings = staticCompositionLocalOf { Testi(Lingua.IT) }

private const val KEY_LINGUA = "lingua"

private fun caricaLingua(context: Context): Lingua {
    val salvata = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_LINGUA, null)
    return if (salvata == "EN") Lingua.EN else Lingua.IT
}

private fun salvaLingua(context: Context, lingua: Lingua) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_LINGUA, lingua.name)
        .apply()
}

// Una singola lettura salvata dall'utente (data/ora + direzione rilevata + posizione/foto opzionali)
data class LetturaVento(
    val id: Long,
    val timestamp: Long,
    val gradi: Float,
    val abbrev: String,
    val nome: String,
    val lat: Double? = null,
    val lon: Double? = null,
    val fotoPath: String? = null
)

private const val PREFS_NAME = "rosa_dei_venti_prefs"
private const val KEY_LETTURE = "letture"

// Persistenza semplice su SharedPreferences (JSON), sopravvive alla chiusura dell'app
object LetturaStorage {
    fun carica(context: Context): List<LetturaVento> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LETTURE, null) ?: return emptyList()
        val arr = JSONArray(json)
        val lista = mutableListOf<LetturaVento>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            lista.add(
                LetturaVento(
                    id = o.getLong("id"),
                    timestamp = o.getLong("timestamp"),
                    gradi = o.getDouble("gradi").toFloat(),
                    abbrev = o.getString("abbrev"),
                    nome = o.getString("nome"),
                    lat = if (o.isNull("lat")) null else o.optDouble("lat").takeUnless { it.isNaN() },
                    lon = if (o.isNull("lon")) null else o.optDouble("lon").takeUnless { it.isNaN() },
                    fotoPath = if (o.isNull("fotoPath")) null else o.optString("fotoPath").takeIf { it.isNotBlank() }
                )
            )
        }
        return lista.sortedByDescending { it.timestamp }
    }

    private fun salvaLista(context: Context, lista: List<LetturaVento>) {
        val arr = JSONArray()
        lista.forEach { l ->
            arr.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("timestamp", l.timestamp)
                    put("gradi", l.gradi.toDouble())
                    put("abbrev", l.abbrev)
                    put("nome", l.nome)
                    put("lat", l.lat ?: JSONObject.NULL)
                    put("lon", l.lon ?: JSONObject.NULL)
                    put("fotoPath", l.fotoPath ?: JSONObject.NULL)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LETTURE, arr.toString())
            .apply()
    }

    fun aggiungi(context: Context, lettura: LetturaVento): List<LetturaVento> {
        val lista = (listOf(lettura) + carica(context))
        salvaLista(context, lista)
        return lista
    }

    fun aggiornaFoto(context: Context, id: Long, path: String): List<LetturaVento> {
        val lista = carica(context).map { if (it.id == id) it.copy(fotoPath = path) else it }
        salvaLista(context, lista)
        return lista
    }

    fun elimina(context: Context, id: Long): List<LetturaVento> {
        carica(context).find { it.id == id }?.fotoPath?.let { path ->
            runCatching { File(path).delete() }
        }
        val lista = carica(context).filterNot { it.id == id }
        salvaLista(context, lista)
        return lista
    }

    fun eliminaTutto(context: Context): List<LetturaVento> {
        carica(context).forEach { l -> l.fotoPath?.let { path -> runCatching { File(path).delete() } } }
        salvaLista(context, emptyList())
        return emptyList()
    }
}

// Ultima posizione nota (rapida, senza attendere un nuovo fix GPS) - richiede permesso posizione
private fun ottieniUltimaPosizione(context: Context): Location? {
    val haFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val haCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!haFine && !haCoarse) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    var migliore: Location? = null
    for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
        try {
            if (lm.isProviderEnabled(provider)) {
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null && (migliore == null || loc.time > migliore!!.time)) migliore = loc
            }
        } catch (e: SecurityException) {
            // permesso mancante per questo provider, si prova il successivo
        }
    }
    return migliore
}

// File univoco in storage privato dell'app dove salvare la foto scattata per una lettura
private fun creaFileFoto(context: Context, id: Long): File {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "lettura_$id.jpg")
}

private fun uriPerFile(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

// Decodifica un bitmap ridimensionato in modo che il lato più lungo non superi latoMassimoPx,
// per non caricare in memoria una foto a piena risoluzione quando non serve
private fun decodificaBitmapConLimite(path: String, latoMassimoPx: Int): Bitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var inSample = 1
        while (bounds.outWidth / inSample > latoMassimoPx || bounds.outHeight / inSample > latoMassimoPx) {
            inSample *= 2
        }
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = inSample })
    } catch (e: Exception) {
        null
    }
}

// Miniatura piccola e leggera per la lista dello storico
private fun decodificaThumbnail(path: String): Bitmap? = decodificaBitmapConLimite(path, 240)

// Versione ad alta risoluzione per la visualizzazione a schermo intero (nitida anche con lo zoom)
private fun decodificaFotoIntera(path: String): Bitmap? = decodificaBitmapConLimite(path, 2000)

private enum class Schermata { Bussola, Storico, Setup, Autore }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF5F7FB)) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var schermata by remember { mutableStateOf(Schermata.Bussola) }
    var letture by remember { mutableStateOf(LetturaStorage.carica(context)) }
    var lingua by remember { mutableStateOf(caricaLingua(context)) }
    val t = remember(lingua) { Testi(lingua) }

    // Richiede il permesso di posizione una volta all'avvio (facoltativo: se negato, si salva senza coordinate)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* nessuna azione: l'utente può comunque continuare a usare l'app */ }
    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // Stato per la foto in attesa di essere scattata/associata a una lettura.
    // rememberSaveable (non remember): la fotocamera è pesante e Android può terminare
    // il processo dell'app in background mentre è aperta - questo stato deve sopravvivere.
    var fotoInAttesaId by rememberSaveable { mutableStateOf<Long?>(null) }
    var fotoInAttesaPath by rememberSaveable { mutableStateOf<String?>(null) }
    var mostraDialogFoto by remember { mutableStateOf(false) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { successo ->
        val id = fotoInAttesaId
        val path = fotoInAttesaPath
        if (successo && id != null && path != null && File(path).exists()) {
            letture = LetturaStorage.aggiornaFoto(context, id, path)
        } else if (fotoInAttesaId != null) {
            Toast.makeText(context, t.scattoAnnullato, Toast.LENGTH_SHORT).show()
        }
        fotoInAttesaId = null
        fotoInAttesaPath = null
    }

    fun scattaFotoPer(id: Long) {
        val file = creaFileFoto(context, id)
        fotoInAttesaId = id
        fotoInAttesaPath = file.absolutePath
        takePictureLauncher.launch(uriPerFile(context, file))
    }

    CompositionLocalProvider(LocalStrings provides t) {
        when (schermata) {
            Schermata.Bussola -> RosaDeiVentiScreen(
                numeroLetture = letture.size,
                onSalvaLettura = { gradi, vento ->
                    val posizione = ottieniUltimaPosizione(context)
                    val id = System.currentTimeMillis()
                    letture = LetturaStorage.aggiungi(
                        context,
                        LetturaVento(
                            id = id,
                            timestamp = id,
                            gradi = gradi,
                            abbrev = vento.abbrev,
                            nome = vento.nome,
                            lat = posizione?.latitude,
                            lon = posizione?.longitude
                        )
                    )
                    Toast.makeText(
                        context,
                        t.letturaSalvata(vento.abbrev, String.format("%.0f", gradi), posizione != null),
                        Toast.LENGTH_SHORT
                    ).show()
                    fotoInAttesaId = id
                    mostraDialogFoto = true
                },
                onVediStorico = { schermata = Schermata.Storico },
                onApriSetup = { schermata = Schermata.Setup }
            )
            Schermata.Storico -> StoricoScreen(
                letture = letture,
                onElimina = { id -> letture = LetturaStorage.elimina(context, id) },
                onEliminaTutto = { letture = LetturaStorage.eliminaTutto(context) },
                onAggiungiFoto = { id -> scattaFotoPer(id) },
                onIndietro = { schermata = Schermata.Bussola }
            )
            Schermata.Setup -> SetupScreen(
                lingua = lingua,
                onCambiaLingua = { nuova ->
                    lingua = nuova
                    salvaLingua(context, nuova)
                },
                onApriAutore = { schermata = Schermata.Autore },
                onIndietro = { schermata = Schermata.Bussola }
            )
            Schermata.Autore -> AutoreScreen(
                onIndietro = { schermata = Schermata.Setup }
            )
        }

        if (mostraDialogFoto) {
            AlertDialog(
                onDismissRequest = { mostraDialogFoto = false; fotoInAttesaId = null },
                title = { Text(t.dialogFotoTitolo) },
                text = { Text(t.dialogFotoTesto) },
                confirmButton = {
                    TextButton(onClick = {
                        mostraDialogFoto = false
                        fotoInAttesaId?.let { scattaFotoPer(it) }
                    }) {
                        Text(t.dialogFotoScatta, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostraDialogFoto = false; fotoInAttesaId = null }) {
                        Text(t.dialogFotoNoGrazie)
                    }
                }
            )
        }
    }
}

@Composable
fun RosaDeiVentiScreen(
    numeroLetture: Int,
    onSalvaLettura: (Float, Vento) -> Unit,
    onVediStorico: () -> Unit,
    onApriSetup: () -> Unit
) {
    val context = LocalContext.current
    val t = LocalStrings.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }
    // Livello di precisione (non il testo già formattato): il testo mostrato viene tradotto
    // al volo in base alla lingua corrente, senza dover rileggere i sensori.
    var livelloPrecisione by remember { mutableStateOf(-1) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        // Fallback: rotation vector se disponibile (più stabile)
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (accelerometer == null || magnetometer == null) {
            // proveremo con rotation vector
            if (rotationVector == null) {
                hasSensor = false
            }
        }

        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var gravitySet = false
        var geomagneticSet = false

        // low-pass filter per stabilizzare
        val alpha = 0.15f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]
                        gravitySet = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomagnetic[0] = alpha * geomagnetic[0] + (1 - alpha) * event.values[0]
                        geomagnetic[1] = alpha * geomagnetic[1] + (1 - alpha) * event.values[1]
                        geomagnetic[2] = alpha * geomagnetic[2] + (1 - alpha) * event.values[2]
                        geomagneticSet = true
                    }
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val rm = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rm, event.values)
                        SensorManager.getOrientation(rm, orientation)
                        var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        az = (az + 360) % 360
                        azimuth = az
                        return
                    }
                }
                if (gravitySet && geomagneticSet) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        var az = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        az = (az + 360) % 360
                        // smoothing aggiuntivo sui gradi (evita salti bruschi)
                        // gestione wrap-around 0/360
                        val diff = ((az - azimuth + 540) % 360) - 180
                        azimuth += diff * 0.2f
                        if (azimuth < 0) azimuth += 360f
                        if (azimuth >= 360) azimuth -= 360f
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                livelloPrecisione = accuracy
            }
        }

        if (rotationVector != null) {
            sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        } else {
            accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
            magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME) }
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    val vento = getVentoDaGradi(azimuth)
    // animazione fluida dell'ago
    val animatedAzimuth by animateFloatAsState(
        targetValue = azimuth,
        animationSpec = tween(durationMillis = 250),
        label = "azimuth"
    )
    val textMeasurer = rememberTextMeasurer()

    val accuracyText = if (!hasSensor) {
        t.sensoriNonDisponibili
    } else when (livelloPrecisione) {
        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> t.precisioneAlta
        SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> t.precisioneMedia
        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> t.precisioneBassa
        SensorManager.SENSOR_STATUS_UNRELIABLE -> t.precisioneInaffidabile
        else -> t.precisioneInAttesa
    }
    val accuracyCritica = livelloPrecisione == SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
        livelloPrecisione == SensorManager.SENSOR_STATUS_UNRELIABLE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = t.titoloApp,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A2B4C),
                modifier = Modifier.align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8ECF5))
                    .clickable { onApriSetup() },
                contentAlignment = Alignment.Center
            ) {
                Text("⚙️", fontSize = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = t.sottotitolo,
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = accuracyText,
            fontSize = 12.sp,
            color = if (accuracyCritica) Color(0xFFD32F2F) else Color(0xFF388E3C)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Card con rosa
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Canvas rosa dei venti
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(320.dp)) {
                        val cx = size.width / 2
                        val cy = size.height / 2
                        // radius = raggio dell'anello principale; fuori resta spazio per le etichette
                        val radius = size.minDimension / 2 - 46

                        fun punto(angleDeg: Float, r: Float): Offset {
                            val rad = Math.toRadians(angleDeg.toDouble())
                            return Offset(cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat())
                        }

                        // sfondo cerchio decorativo (anello esterno dove vivono le etichette)
                        drawCircle(color = Color(0xFFF0F4FF), radius = radius + 40)
                        drawCircle(color = Color.White, radius = radius + 30, style = Stroke(width = 2f))
                        drawCircle(color = Color(0xFFE0E7FF), radius = radius + 30)
                        drawCircle(color = Color.White, radius = radius + 4, style = Stroke(width = 1.5f))

                        // --- STELLA DECORATIVA (il classico "fiore" della rosa dei venti) ---
                        fun disegnaPunta(angleDeg: Float, tipR: Float, baseR: Float, semiapertura: Float, scuro: Color, chiaro: Color) {
                            val centro = Offset(cx, cy)
                            val tip = punto(angleDeg, tipR)
                            val sinistra = punto(angleDeg - semiapertura, baseR)
                            val destra = punto(angleDeg + semiapertura, baseR)
                            // due triangoli con tonalità diverse -> effetto sfaccettato/3D
                            drawPath(
                                Path().apply { moveTo(centro.x, centro.y); lineTo(sinistra.x, sinistra.y); lineTo(tip.x, tip.y); close() },
                                color = scuro
                            )
                            drawPath(
                                Path().apply { moveTo(centro.x, centro.y); lineTo(tip.x, tip.y); lineTo(destra.x, destra.y); close() },
                                color = chiaro
                            )
                        }

                        // 8 punte secondarie corte (NNE, ENE, ESE, ...) - riempiono lo sfondo
                        for (i in 0 until 16) {
                            if (i % 2 == 1) {
                                val angleDeg = i * 22.5f - 90f
                                disegnaPunta(angleDeg, radius * 0.42f, radius * 0.12f, 9f, Color(0xFFC7D2E8), Color(0xFFDCE4F5))
                            }
                        }
                        // 4 punte intercardinali (NE, SE, SW, NW)
                        for (angleDeg in listOf(-45f, 45f, 135f, 225f)) {
                            disegnaPunta(angleDeg, radius * 0.72f, radius * 0.12f, 11f, Color(0xFF3A5A8C), Color(0xFF5B7DB1))
                        }
                        // 4 punte cardinali (N, E, S, W) - le più lunghe e marcate
                        for (angleDeg in listOf(-90f, 0f, 90f, 180f)) {
                            disegnaPunta(angleDeg, radius - 4f, radius * 0.14f, 13f, Color(0xFF1A2B4C), Color(0xFF2E4570))
                        }
                        // punta Nord in rosso (riferimento visivo forte, come sulle rose nautiche)
                        disegnaPunta(-90f, radius - 4f, radius * 0.14f, 13f, Color(0xFFB71C1C), Color(0xFFD32F2F))

                        // anello principale
                        drawCircle(color = Color(0xFF1A2B4C), radius = radius, style = Stroke(width = 2.5f))
                        drawCircle(color = Color.White, radius = radius - 3f, style = Stroke(width = 1f))

                        // tacche di graduazione fitte (ogni 5°) sul bordo esterno dell'anello
                        for (g in 0 until 72) {
                            val angleDeg = g * 5f - 90f
                            val isMaggiore = g % 18 == 0 // ogni 90°
                            val isMedia = g % 9 == 0     // ogni 45°
                            val len = when { isMaggiore -> 0f; isMedia -> 10f; else -> 5f }
                            if (len == 0f) continue // già coperto dalle punte cardinali
                            val p1 = punto(angleDeg, radius + 2f)
                            val p2 = punto(angleDeg, radius + 2f + len)
                            drawLine(
                                color = if (isMedia) Color(0xFF3A5A8C) else Color(0xFFA8B7D6),
                                start = p1, end = p2,
                                strokeWidth = if (isMedia) 1.8f else 1f
                            )
                        }

                        // --- Etichette dei 16 venti, posizionate dove punta l'ago ---
                        for (i in 0 until 16) {
                            val angleDeg = i * 22.5f - 90f
                            val isCardinal = i % 4 == 0
                            val isIntercardinal = i % 2 == 0
                            val testo = venti16[i].abbrev
                            val dimensione = when {
                                isCardinal -> 17.sp
                                isIntercardinal -> 13.sp
                                else -> 9.sp
                            }
                            val coloreTesto = when {
                                isCardinal && i == 0 -> Color(0xFFD32F2F) // N in rosso
                                isCardinal -> Color(0xFF1A2B4C)
                                isIntercardinal -> Color(0xFF3A5A8C)
                                else -> Color(0xFF8AA0C8)
                            }
                            val layout = textMeasurer.measure(
                                text = testo,
                                style = TextStyle(
                                    fontSize = dimensione,
                                    fontWeight = if (isCardinal) FontWeight.Black else if (isIntercardinal) FontWeight.Bold else FontWeight.Medium,
                                    color = coloreTesto
                                )
                            )
                            val p = punto(angleDeg, radius + 20f)
                            drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f)
                            )
                        }

                        // cerchio centrale
                        drawCircle(color = Color(0xFF1A2B4C), radius = 7f)

                        // AGO - punta verso azimuth (direzione del telefono / provenienza vento)
                        rotate(degrees = animatedAzimuth, pivot = Offset(cx, cy)) {
                            // ago rosso (nord della bussola = direzione puntata)
                            val path = Path().apply {
                                moveTo(cx, cy - radius + 14) // punta
                                lineTo(cx - 11, cy + 14)
                                lineTo(cx + 11, cy + 14)
                                close()
                            }
                            drawPath(path, color = Color(0xFFD32F2F))
                            // coda ago (sud)
                            val tail = Path().apply {
                                moveTo(cx, cy + radius - 30)
                                lineTo(cx - 8, cy - 10)
                                lineTo(cx + 8, cy - 10)
                                close()
                            }
                            drawPath(tail, color = Color(0xFF90A4C8))
                            // perno
                            drawCircle(color = Color.White, radius = 8f, center = Offset(cx, cy))
                            drawCircle(color = Color(0xFF1A2B4C), radius = 8f, center = Offset(cx, cy), style = Stroke(2f))
                            drawCircle(color = Color(0xFFD32F2F), radius = 4f, center = Offset(cx, cy))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Risultato grande
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2B4C)),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = vento.abbrev,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Text(
                    text = vento.nome.uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFC107),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = descrizioneVento(vento, t.lingua),
                    fontSize = 13.sp,
                    color = Color(0xFFB0C4DE)
                )
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.etichettaGradi, fontSize = 11.sp, color = Color(0xFFB0C4DE))
                        Text(
                            text = String.format("%.1f°", azimuth),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.etichettaDirezione, fontSize = 11.sp, color = Color(0xFFB0C4DE))
                        Text(
                            text = "${vento.centroGradi.toInt()}°",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(t.etichettaSettore, fontSize = 11.sp, color = Color(0xFFB0C4DE))
                        val start = (vento.centroGradi - 11.25f + 360) % 360
                        val end = (vento.centroGradi + 11.25f) % 360
                        Text(
                            text = "${start.toInt()}°-${end.toInt()}°",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Salva lettura corrente / vai allo storico
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onSalvaLettura(azimuth, vento) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2B4C))
            ) {
                Text(t.bottoneSalva, fontSize = 14.sp)
            }
            OutlinedButton(
                onClick = onVediStorico,
                modifier = Modifier.weight(1f)
            ) {
                Text(t.storico(numeroLetture), fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!hasSensor) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Text(
                    modifier = Modifier.padding(12.dp),
                    text = t.sensoriNonDisponibiliMsg,
                    color = Color(0xFFD32F2F),
                    fontSize = 12.sp
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t.comeUsareTitolo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1A2B4C))
                Spacer(modifier = Modifier.height(10.dp))
                Text(t.comeUsare1, fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFF3A4556))
                Spacer(modifier = Modifier.height(6.dp))
                Text(t.comeUsare2, fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFF3A4556))
                Spacer(modifier = Modifier.height(6.dp))
                Text(t.comeUsare3, fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFF3A4556))
                Spacer(modifier = Modifier.height(6.dp))
                Text(t.comeUsare4, fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFF3A4556))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Legenda 16 venti
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(t.rosa16Titolo, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1A2B4C))
                Spacer(modifier = Modifier.height(8.dp))
                venti16.chunked(4).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { v ->
                            val isSelected = v.abbrev == vento.abbrev
                            Text(
                                text = "${v.abbrev} ${v.centroGradi.toInt()}°",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFD32F2F) else Color.Gray,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun StoricoScreen(
    letture: List<LetturaVento>,
    onElimina: (Long) -> Unit,
    onEliminaTutto: () -> Unit,
    onAggiungiFoto: (Long) -> Unit,
    onIndietro: () -> Unit
) {
    var mostraConfermaEliminaTutto by remember { mutableStateOf(false) }
    var fotoInVisualizzazione by remember { mutableStateOf<String?>(null) }
    val t = LocalStrings.current
    val formatter = remember(t.lingua) { SimpleDateFormat("dd/MM/yyyy HH:mm", if (t.lingua == Lingua.EN) Locale.ENGLISH else Locale.ITALY) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onIndietro) {
                Text(t.indietro, fontSize = 15.sp, color = Color(0xFF1A2B4C))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = t.titoloStorico,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A2B4C)
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (letture.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = t.vuotoStorico,
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = Color.Gray
                )
            }
        } else {
            Button(
                onClick = { mostraConfermaEliminaTutto = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text(t.eliminaTutto(letture.size), fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(letture, key = { it.id }) { lettura ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Miniatura foto, se presente
                                if (lettura.fotoPath != null && File(lettura.fotoPath).exists()) {
                                    val bitmap = remember(lettura.fotoPath) { decodificaThumbnail(lettura.fotoPath) }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = "Foto del posto",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { fotoInVisualizzazione = lettura.fotoPath }
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = lettura.abbrev,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF1A2B4C)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = lettura.nome,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF3A4556)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${String.format("%.1f", lettura.gradi)}°  ·  ${formatter.format(Date(lettura.timestamp))}",
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                    if (lettura.lat != null && lettura.lon != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "📍 ${String.format("%.5f", lettura.lat)}, ${String.format("%.5f", lettura.lon)}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF3A5A8C),
                                            modifier = Modifier.clickable {
                                                try {
                                                    val uri = Uri.parse("geo:${lettura.lat},${lettura.lon}?q=${lettura.lat},${lettura.lon}")
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, t.nessunaAppMappe, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFEBEE))
                                        .clickable { onElimina(lettura.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✕",
                                        color = Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }
                            }

                            if (lettura.fotoPath == null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { onAggiungiFoto(lettura.id) },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(t.aggiungiFoto, fontSize = 13.sp, color = Color(0xFF1A2B4C))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostraConfermaEliminaTutto) {
        AlertDialog(
            onDismissRequest = { mostraConfermaEliminaTutto = false },
            title = { Text(t.confermaEliminaTuttoTitolo) },
            text = { Text(t.confermaEliminaTuttoTesto(letture.size)) },
            confirmButton = {
                TextButton(onClick = {
                    onEliminaTutto()
                    mostraConfermaEliminaTutto = false
                }) {
                    Text(t.eliminaTuttoConferma, color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostraConfermaEliminaTutto = false }) {
                    Text(t.annulla)
                }
            }
        )
    }

    fotoInVisualizzazione?.let { path ->
        VisualizzatoreFotoDialog(path = path, onChiudi = { fotoInVisualizzazione = null })
    }
}

@Composable
private fun VisualizzatoreFotoDialog(path: String, onChiudi: () -> Unit) {
    val t = LocalStrings.current
    val bitmap = remember(path) { decodificaFotoIntera(path) }
    var scala by remember(path) { mutableFloatStateOf(1f) }
    var offsetX by remember(path) { mutableFloatStateOf(0f) }
    var offsetY by remember(path) { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onChiudi,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Foto del posto",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scala,
                            scaleY = scala,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .pointerInput(path) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scala = (scala * zoom).coerceIn(1f, 5f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                )
            } else {
                Text(
                    t.impossibileAprireFoto,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.9f))
                    .clickable(onClick = onChiudi),
                contentAlignment = Alignment.Center
            ) {
                Text("✕", color = Color(0xFF1A2B4C), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun SetupScreen(lingua: Lingua, onCambiaLingua: (Lingua) -> Unit, onApriAutore: () -> Unit, onIndietro: () -> Unit) {
    val t = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onIndietro) {
                Text(t.indietro, fontSize = 15.sp, color = Color(0xFF1A2B4C))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = t.titoloSetup,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A2B4C)
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selettore lingua: Italiano (primaria) / English (secondaria)
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t.linguaTitolo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1A2B4C))
                Spacer(modifier = Modifier.height(4.dp))
                Text(t.linguaSottotitolo, fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelettoreLinguaBottone(
                        etichetta = "🇮🇹 Italiano",
                        selezionato = lingua == Lingua.IT,
                        modifier = Modifier.weight(1f),
                        onClick = { onCambiaLingua(Lingua.IT) }
                    )
                    SelettoreLinguaBottone(
                        etichetta = "🇬🇧 English",
                        selezionato = lingua == Lingua.EN,
                        modifier = Modifier.weight(1f),
                        onClick = { onCambiaLingua(Lingua.EN) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t.percheTitolo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1A2B4C))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    t.percheTesto,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF1B2430)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    t.calibraTitolo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF1A2B4C),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Diagramma del movimento a 8
                Canvas(modifier = Modifier.size(180.dp, 110.dp)) {
                    val w = size.width
                    val h = size.height
                    val r = h / 2.3f
                    val leftCenter = Offset(w / 2f - r * 0.9f, h / 2f)
                    val rightCenter = Offset(w / 2f + r * 0.9f, h / 2f)
                    drawCircle(color = Color(0xFF3A5A8C), radius = r, center = leftCenter, style = Stroke(width = 4f))
                    drawCircle(color = Color(0xFF3A5A8C), radius = r, center = rightCenter, style = Stroke(width = 4f))
                    // frecce che indicano il verso di percorrenza
                    drawCircle(color = Color(0xFFD32F2F), radius = 7f, center = Offset(leftCenter.x, leftCenter.y - r))
                    drawCircle(color = Color(0xFFD32F2F), radius = 7f, center = Offset(rightCenter.x, rightCenter.y + r))
                    // icona telefono al centro
                    drawCircle(color = Color(0xFF1A2B4C), radius = 5f, center = Offset(w / 2f, h / 2f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                listOf(t.calibra1, t.calibra2, t.calibra3, t.calibra4).forEach { riga ->
                    Text(
                        text = riga,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFF1B2430),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t.disturboTitolo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1A2B4C))
                Spacer(modifier = Modifier.height(10.dp))
                listOf(t.disturbo1, t.disturbo2, t.disturbo3, t.disturbo4, t.disturbo5).forEach { riga ->
                    Text(
                        text = "• $riga",
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color(0xFF1B2430),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2B4C))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(t.indicatoreTitolo, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                Spacer(modifier = Modifier.height(14.dp))
                RigaPrecisione(t.livelloAlta, t.livelloAltaDesc, Color(0xFF69F0AE))
                RigaPrecisione(t.livelloMedia, t.livelloMediaDesc, Color(0xFFFFD54F))
                RigaPrecisione(t.livelloBassa, t.livelloBassaDesc, Color(0xFFFFAB91))
                RigaPrecisione(t.livelloInaffidabile, t.livelloInaffidabileDesc, Color(0xFFFF8A80))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onApriAutore() },
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t.autoreVoce, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A2B4C))
                Spacer(modifier = Modifier.weight(1f))
                Text("›", fontSize = 20.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SelettoreLinguaBottone(
    etichetta: String,
    selezionato: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selezionato) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2B4C))
        ) {
            Text(etichetta, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(etichetta, fontSize = 14.sp)
        }
    }
}

@Composable
fun AutoreScreen(onIndietro: () -> Unit) {
    val t = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onIndietro) {
                Text(t.indietro, fontSize = 15.sp, color = Color(0xFF1A2B4C))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = t.autoreTitolo,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A2B4C)
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(64.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = R.drawable.autore),
                contentDescription = "Gianni",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color(0xFF1A2B4C), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Gianni",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A2B4C),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = t.autoreRuolo,
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun RigaPrecisione(livello: String, descrizione: String, colore: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(colore)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(livello, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            Text(descrizione, fontSize = 13.sp, lineHeight = 18.sp, color = Color(0xFFE3E9F7))
        }
    }
}
