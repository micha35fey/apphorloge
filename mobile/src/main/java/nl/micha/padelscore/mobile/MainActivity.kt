package nl.micha.padelscore.mobile

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.Team
import nl.micha.padelscore.update.Updater

private val TeamAColor = Color(0xFF1565C0)
private val TeamBColor = Color(0xFFC62828)
private val PanelShape = RoundedCornerShape(24.dp)

class MainActivity : ComponentActivity() {
    private val vm: PhoneViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PhoneApp(vm)
            }
        }
    }

    fun startUpdate() {
        if (!Updater.ensureInstallAllowed(this)) {
            vm.updateStatus = "Sta installeren toe en tik opnieuw"
            return
        }
        // Eerst het horloge laten bijwerken; de telefoon-app sluit zelf af tijdens zijn update.
        vm.updateWatch()
        vm.installUpdate()
    }

    /** Tijdens een partij: scherm aan en statusbalk en knoppen weg, zodat het scorebord het hele scherm krijgt. */
    fun scoreboardMode(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun PhoneApp(vm: PhoneViewModel) {
    val activity = LocalContext.current as? MainActivity
    val score = vm.score
    val playing = score != null && score.active && score.state.winner == null

    // Toestemming voor de melding met de stand (Android 13 en nieuwer).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    DisposableEffect(playing) {
        activity?.scoreboardMode(playing)
        onDispose { activity?.scoreboardMode(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (score == null) {
            Text("Winner Padel", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Wacht op het horloge...\nStart een partij op je watch.",
                    color = Color.Gray,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val state = score.state
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Bij kant wisselen schuiven de vlakken mee, met hun eigen kleur.
                    for (team in listOf(state.leftTeam, state.leftTeam.other)) {
                        val color = if (team == Team.A) TeamAColor else TeamBColor
                        TeamPanel(state, team, color, score.active, Modifier.weight(1f))
                    }
                }
                val status = if (score.active) state.statusText else "Geen partij bezig"
                if (status != null) {
                    Text(
                        text = status,
                        color = Color.Black,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                            .background(Color.Yellow, RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (state.settings.setsToWin == 0) "Games" else "Sets",
                    color = Color.Gray,
                    fontSize = 20.sp,
                )
                Spacer(Modifier.width(16.dp))
                Text(state.setsText(), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Knoppen alleen buiten een partij, dan heeft het scorebord alle ruimte.
        if (!playing) {
            vm.update?.let { info ->
                Spacer(Modifier.height(8.dp))
                Button(onClick = { activity?.startUpdate() }) { Text("Update naar ${info.versionName}") }
                vm.updateStatus?.let { status ->
                    Text(status, color = Color.Gray, fontSize = 14.sp, textAlign = TextAlign.Center)
                    // Lukt installeren vanuit de app niet, dan via de browser downloaden.
                    if (status.startsWith("Mislukt")) {
                        val context = LocalContext.current
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
                        }) { Text("Download via browser") }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Score omroepen", color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Switch(checked = vm.announce, onCheckedChange = { vm.updateAnnounce(it) })
            }
            if (vm.announce) {
                TextButton(onClick = { vm.nextVoice() }) { Text("Stem wisselen (${vm.voiceLabel})") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vm.updateWatch() }) { Text("Horloge bijwerken") }
            vm.watchUpdateStatus?.let { Text("Horloge: $it", color = Color.Gray, fontSize = 14.sp) }
            Text("Versie ${BuildConfig.VERSION_NAME}", color = Color.DarkGray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TeamPanel(state: MatchState, team: Team, color: Color, active: Boolean, modifier: Modifier) {
    val games = if (team == Team.A) state.gamesA else state.gamesB
    val faded = !active || (state.winner != null && state.winner != team)
    val serving = active && state.winner == null && state.server == team
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .clip(PanelShape)
            .background(color.copy(alpha = if (faded) 0.4f else 1f))
            .then(if (serving) Modifier.border(6.dp, Color.Yellow, PanelShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // Letters schalen mee met het vak, zodat de stand altijd zo groot mogelijk is.
        val density = LocalDensity.current
        val unit = minOf(maxHeight, maxWidth * 0.8f)
        fun scaled(fraction: Float): TextUnit = with(density) { (unit * fraction).toSp() }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (serving) "${team.label} · opslag" else team.label,
                color = if (serving) Color.Yellow else Color.White.copy(alpha = 0.85f),
                fontSize = scaled(0.09f),
                fontWeight = if (serving) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = state.pointLabel(team),
                color = Color.White,
                fontSize = scaled(0.5f),
                lineHeight = scaled(0.56f),
                fontWeight = FontWeight.Bold,
            )
            Text("Games $games", color = Color.White, fontSize = scaled(0.12f))
        }
    }
}
