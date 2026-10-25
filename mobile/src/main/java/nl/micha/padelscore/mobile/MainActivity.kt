package nl.micha.padelscore.mobile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.Team
import nl.micha.padelscore.update.Updater

private val TeamAColor = Color(0xFF1565C0)
private val TeamBColor = Color(0xFFC62828)

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
        vm.installUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Updater.handleInstallStatus(this, intent)?.let { vm.updateStatus = it }
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
    // Scherm aan tijdens een partij, zodat de telefoon als scorebord kan dienen.
    DisposableEffect(playing) {
        activity?.keepScreenOn(playing)
        onDispose { activity?.keepScreenOn(false) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Winner Padel", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (score == null) {
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
            val status = if (score.active) state.statusText else "Geen partij bezig, laatste stand:"
            Text(status ?: " ", color = Color.Yellow, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TeamPanel(state, Team.A, TeamAColor, score.active, Modifier.weight(1f))
                TeamPanel(state, Team.B, TeamBColor, score.active, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text(if (state.settings.setsToWin == 0) "Games" else "Sets en games", color = Color.Gray, fontSize = 14.sp)
            Text(state.setsText(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }

        vm.update?.let { info ->
            Spacer(Modifier.height(12.dp))
            Button(onClick = { activity?.startUpdate() }) { Text("Update naar ${info.versionName}") }
            vm.updateStatus?.let { Text(it, color = Color.Gray, fontSize = 14.sp) }
        }
        Text("Versie ${BuildConfig.VERSION_NAME}", color = Color.DarkGray, fontSize = 12.sp)
    }
}

@Composable
private fun TeamPanel(state: MatchState, team: Team, color: Color, active: Boolean, modifier: Modifier) {
    val games = if (team == Team.A) state.gamesA else state.gamesB
    val faded = !active || (state.winner != null && state.winner != team)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = if (faded) 0.4f else 1f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(team.label, color = Color.White.copy(alpha = 0.85f), fontSize = 22.sp)
            Text(state.pointLabel(team), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold)
            Text("Games $games", color = Color.White, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        if (active && state.winner == null && state.server == team) Color.Yellow else Color.Transparent,
                        CircleShape,
                    ),
            )
        }
    }
}
