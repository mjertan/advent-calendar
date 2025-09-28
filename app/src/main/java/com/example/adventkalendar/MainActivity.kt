package com.example.adventkalendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.runtime.mutableLongStateOf

// DataStore
val Context.dataStore by preferencesDataStore(name = "settings_prefs")

// —— Snijeg – podešavanja
private const val SNOW_SIZE_MULTIPLIER = 1.25f
private const val SNOW_MIN_RADIUS_BASE = 7f
private const val SNOW_MAX_RADIUS_BASE = 12f
private const val SNOW_MIN_SPEED_Y = 28f
private const val SNOW_MAX_SPEED_Y = 60f
private const val SNOW_MAX_SIDE_SPEED = 10f
private const val SNOW_MAX_SPIN = 12f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AdventApp() }
    }
}

private enum class CounterMode { FULL, NIGHTS, MINUTES, SECONDS;
    fun next(): CounterMode = entries[(ordinal + 1) % entries.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdventApp() {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Christmas Countdown",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                NavigationDrawerItem(
                    label = { Text("Countdown") },
                    selected = nav.currentDestinationRoute() == "home",
                    onClick = { scope.launch { drawerState.close(); nav.navigateSingleTop("home") } }
                )
                NavigationDrawerItem(
                    label = { Text("My Advent Calendar") },
                    selected = nav.currentDestinationRoute() == "calendar",
                    onClick = { scope.launch { drawerState.close(); nav.navigateSingleTop("calendar") } }
                )
                NavigationDrawerItem(
                    label = { Text("Gallery") },
                    selected = nav.currentDestinationRoute() == "gallery",
                    onClick = { scope.launch { drawerState.close(); nav.navigateSingleTop("gallery") } }
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = nav.currentDestinationRoute() == "settings",
                    onClick = { scope.launch { drawerState.close(); nav.navigateSingleTop("settings") } },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                val isRoot = nav.currentDestinationRoute() in listOf("home")
                TopAppBar(
                    title = { Text("Christmas Countdown") },
                    navigationIcon = {
                        if (isRoot) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        } else {
                            IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    }
                )
            }
        ) { inner ->
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(inner)) {
                composable("home") { HomeScreen() }
                composable("calendar") { PlaceholderScreen("Advent Kalendar (uskoro)") }
                composable("gallery") { PlaceholderScreen("Galerija (uskoro)") }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val store = ctx.dataStore
    val bgKey = stringPreferencesKey("bg_uri")
    val snowKey = booleanPreferencesKey("snow_enabled")
    var bgUri by remember { mutableStateOf<String?>(null) }
    var snowEnabled by remember { mutableStateOf(true) }

    // učitaj spremljene postavke
    LaunchedEffect(Unit) {
        val prefs = store.data.first()
        bgUri = prefs[bgKey]
        snowEnabled = prefs[snowKey] ?: true
    }

    // Dani do Božića – za gustoću snijega
    val now = LocalDateTime.now()
    val christmasThisYear = LocalDateTime.of(LocalDate.now().year, 12, 25, 0, 0)
    val target = if (now.isAfter(christmasThisYear)) christmasThisYear.plusYears(1) else christmasThisYear
    val daysRemaining = Duration.between(now, target).toDays().coerceAtLeast(0)

    Box(Modifier.fillMaxSize()) {
        // 1) fallback boja – dno
        Box(Modifier.matchParentSize().background(Color(0xFF1E2A38)))

        // 2) slika iz postavki ili default iz drawable
        if (!bgUri.isNullOrEmpty()) {
            AsyncImage(
                model = ImageRequest.Builder(ctx)
                    .data(Uri.parse(bgUri))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.preuzmi), // tvoj default u res/drawable
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (snowEnabled) {
            SnowfallLayer(daysRemaining = daysRemaining, modifier = Modifier.matchParentSize())
        }

        CountdownCard(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 41.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
fun CountdownCard(modifier: Modifier = Modifier) {
    var mode by remember { mutableStateOf(CounterMode.FULL) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // ažuriraj vrijeme svake sekunde (bez vizualne animacije)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = LocalDateTime.now()
        }
    }

    val christmasThisYear = LocalDateTime.of(LocalDate.now().year, 12, 25, 0, 0)
    val target = if (now.isAfter(christmasThisYear)) christmasThisYear.plusYears(1) else christmasThisYear
    val duration = Duration.between(now, target)

    val days = duration.toDays()
    val hours = duration.minusDays(days).toHours()
    val minutes = duration.minusDays(days).minusHours(hours).toMinutes()
    val seconds = duration.seconds % 60

    val nights = Duration.between(LocalDateTime.now(), target.with(LocalTime.MIDNIGHT))
        .toDays().coerceAtLeast(0)

    val totalMinutes = duration.toMinutes().coerceAtLeast(0)
    val totalSeconds = duration.seconds.coerceAtLeast(0)

    Surface(
        color = Color(0xAA000000),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { mode = mode.next() }
            .alpha(0.95f)
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (mode) {
                CounterMode.FULL -> {
                    BigText("$days days")
                    SmallRow("$hours hours, $minutes minutes", "$seconds seconds")
                }
                CounterMode.NIGHTS -> {
                    BigText("$nights night${if (nights == 1L) "" else "s"}")
                    SmallCenter("to Christmas Eve midnight")
                }
                CounterMode.MINUTES -> {
                    BigText("$totalMinutes minutes")
                    SmallCenter("until Christmas")
                }
                CounterMode.SECONDS -> {
                    BigText("$totalSeconds seconds")
                    SmallCenter("until Christmas")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Tap to change", fontSize = 12.sp, color = Color(0xCCFFFFFF))
        }
    }
}

@Composable private fun BigText(txt: String) {
    Text(txt, fontSize = 36.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}
@Composable private fun SmallRow(line1: String, line2: String) {
    Text(line1, fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Center)
    Text(line2, fontSize = 14.sp, color = Color(0xFFEFEFEF), textAlign = TextAlign.Center)
}
@Composable private fun SmallCenter(t: String) {
    Text(t, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center)
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val store = ctx.dataStore
    val scope = rememberCoroutineScope()
    val bgKey = stringPreferencesKey("bg_uri")
    val snowKey = booleanPreferencesKey("snow_enabled")

    var bgUri by remember { mutableStateOf<String?>(null) }
    var snowEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val prefs = store.data.first()
        bgUri = prefs[bgKey]
        snowEnabled = prefs[snowKey] ?: true
    }

    // Android 13+ — PHOTO PICKER
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val localUri = saveBackgroundImageToInternal(ctx, uri)
                if (localUri != null) {
                    store.edit { it[bgKey] = localUri.toString() }
                    bgUri = localUri.toString()
                }
            }
        }
    }

    // Android 12 i niže — OPEN DOCUMENT + trajna dozvola, pa kopija u interne datoteke
    val pickOpenDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { }
            scope.launch {
                val localUri = saveBackgroundImageToInternal(ctx, uri)
                if (localUri != null) {
                    store.edit { it[bgKey] = localUri.toString() }
                    bgUri = localUri.toString()
                }
            }
        }
    }

    fun launchGallery() {
        if (Build.VERSION.SDK_INT >= 33) {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            pickOpenDoc.launch(arrayOf("image/*"))
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // 1) Promjena pozadine
        ListItem(
            headlineContent = { Text("Change background image") },
            supportingContent = { Text("Choose from Gallery") },
            leadingContent = { Icon(imageVector = Icons.Filled.Image, contentDescription = null) },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { launchGallery() }
                .padding(4.dp)
        )

        Spacer(Modifier.height(12.dp))

        // 2) Snijeg ON/OFF
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Snow", fontWeight = FontWeight.SemiBold)
                    Text("Turn snowflakes on/off on the home screen", fontSize = 12.sp, color = Color.Gray)
                }
                Switch(
                    checked = snowEnabled,
                    onCheckedChange = {
                        snowEnabled = it
                        scope.launch { store.edit { prefs -> prefs[snowKey] = it } }
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!bgUri.isNullOrEmpty()) {
            Text("Current wallpaper:", modifier = Modifier.padding(top = 12.dp))
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(Uri.parse(bgUri)).build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    store.edit { it.remove(bgKey) }
                    runCatching { java.io.File(ctx.filesDir, "wallpaper.jpg").delete() }
                    bgUri = null
                }
            }) { Text("Reset to default wallpaper") }
        }
    }
}

@Composable
fun PlaceholderScreen(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun NavHostController.currentDestinationRoute(): String? =
    currentBackStackEntryFlow.collectAsState(initial = currentBackStackEntry).value?.destination?.route

fun NavHostController.navigateSingleTop(route: String) =
    this.navigate(route) { launchSingleTop = true }

// ——— Snijeg — sporiji, veće pahulje, gotovo vertikalno ———

private data class Snowflake(
    var x: Float,
    var y: Float,
    var radius: Float,
    var speedY: Float,
    var speedX: Float,
    var angle: Float,
    var spin: Float
)

@Composable
private fun SnowfallLayer(daysRemaining: Long, modifier: Modifier = Modifier) {
    val flakes = remember { mutableStateListOf<Snowflake>() }
    var lastTime by remember { mutableLongStateOf(0L) }
    var frameTime by remember { mutableLongStateOf(0L) }

    val targetCount = remember(daysRemaining) { targetFlakeCount(daysRemaining) }

    LaunchedEffect(Unit) {
        while (true) frameTime = withFrameNanos { it }
    }

    Canvas(modifier = modifier) {
        val now = frameTime
        if (lastTime == 0L) lastTime = now
        val dt = ((now - lastTime).coerceAtMost(50_000_000)).toFloat() / 1_000_000_000f
        lastTime = now

        val w = size.width
        val h = size.height

        if (flakes.size < targetCount) {
            repeat((targetCount - flakes.size).coerceAtMost(6)) {
                flakes += randomFlake(width = w)
            }
        } else if (flakes.size > targetCount) {
            repeat((flakes.size - targetCount).coerceAtMost(6)) {
                if (flakes.isNotEmpty()) flakes.removeAt(0)
            }
        }

        for (f in flakes) {
            f.x += f.speedX * dt
            f.y += f.speedY * dt
            f.angle = (f.angle + f.spin * dt) % 360f
            if (f.x < -f.radius * 2) f.x = w + f.radius * 2
            if (f.x > w + f.radius * 2) f.x = -f.radius * 2
        }

        flakes.removeAll { it.y - it.radius > h }
        while (flakes.size < targetCount) {
            flakes += randomFlake(width = w)
        }

        val alphaFactor = when {
            targetCount > 120 -> 0.55f
            targetCount > 100 -> 0.65f
            targetCount > 80  -> 0.75f
            else              -> 0.85f
        }

        for (f in flakes) drawSnowflake(f, alphaFactor)
    }
}

private fun targetFlakeCount(daysRemaining: Long): Int = when {
    daysRemaining > 60 -> 30
    daysRemaining > 30 -> 60
    daysRemaining > 15 -> 90
    daysRemaining > 7  -> 110
    daysRemaining > 2  -> 120
    else -> 130
}.coerceAtMost(140)

// veličinu podešavaš gore kroz SNOW_SIZE_MULTIPLIER i min/max
private fun randomFlake(width: Float): Snowflake {
    val r = Random.Default
    val minR = SNOW_MIN_RADIUS_BASE * SNOW_SIZE_MULTIPLIER
    val maxR = SNOW_MAX_RADIUS_BASE * SNOW_SIZE_MULTIPLIER
    val radius = r.nextFloat() * (maxR - minR) + minR
    val startY = -r.nextFloat() * 200f // uvijek kreće iznad ekrana

    return Snowflake(
        x = r.nextFloat() * width,
        y = startY,
        radius = radius,
        speedY = r.nextFloat() * (SNOW_MAX_SPEED_Y - SNOW_MIN_SPEED_Y) + SNOW_MIN_SPEED_Y,
        speedX = (r.nextFloat() * 2f - 1f) * SNOW_MAX_SIDE_SPEED,
        angle = r.nextFloat() * 360f,
        spin = (r.nextFloat() - 0.5f) * SNOW_MAX_SPIN
    )
}

private fun DrawScope.drawSnowflake(f: Snowflake, alphaFactor: Float) {
    val cx = f.x
    val cy = f.y
    val branchLen = f.radius
    val small = branchLen * 0.45f
    val stroke = 1.2f

    rotate(f.angle, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) {
        for (k in 0 until 6) {
            val theta = (PI.toFloat() / 3f) * k
            val ex = cx + branchLen * cos(theta)
            val ey = cy + branchLen * sin(theta)

            drawLine(
                color = Color.White.copy(alpha = 0.95f * alphaFactor),
                start = androidx.compose.ui.geometry.Offset(cx, cy),
                end = androidx.compose.ui.geometry.Offset(ex, ey),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            val g1 = 0.5f; val g2 = 0.72f
            branch(cx, cy, theta, branchLen * g1, small, stroke, alphaFactor)
            branch(cx, cy, theta, branchLen * g2, small * 0.9f, stroke, alphaFactor)
        }
    }
}

private fun DrawScope.branch(
    cx: Float, cy: Float, theta: Float, mainLen: Float, sideLen: Float,
    stroke: Float, alphaFactor: Float
) {
    val bx = cx + mainLen * cos(theta)
    val by = cy + mainLen * sin(theta)
    val offset = (35f * PI / 180f).toFloat()
    val angles = floatArrayOf(theta + offset, theta - offset)
    for (a in angles) {
        val sx = bx + sideLen * cos(a)
        val sy = by + sideLen * sin(a)
        drawLine(
            color = Color.White.copy(alpha = 0.9f * alphaFactor),
            start = androidx.compose.ui.geometry.Offset(bx, by),
            end = androidx.compose.ui.geometry.Offset(sx, sy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** Kopira odabranu sliku u internu memoriju app-a i vraća file:// URI. */
private suspend fun saveBackgroundImageToInternal(ctx: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
    try {
        val file = java.io.File(ctx.filesDir, "wallpaper.jpg")
        ctx.contentResolver.openInputStream(sourceUri).use { inStream ->
            if (inStream != null) {
                java.io.FileOutputStream(file).use { out ->
                    inStream.copyTo(out)
                }
                Uri.fromFile(file)
            } else null
        }
    } catch (_: Exception) {
        null
    }
}
