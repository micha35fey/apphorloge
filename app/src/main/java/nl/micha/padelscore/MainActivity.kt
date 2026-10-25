package nl.micha.padelscore

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import nl.micha.padelscore.score.MatchState
import nl.micha.padelscore.score.Team
import nl.micha.padelscore.update.Updater

private val TeamAColor = Color(0xFF1565C0)
private val TeamBColor = Color(0xFFC62828)

class MainActivity : ComponentActivity() {
    private val vm: MatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PadelApp(vm)
            }
        }
    }

    fun startUpdate() {
        // De eerste keer moet je toestaan dat deze app andere apps (zijn eigen update) installeert.
        if (!packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
            try {
                startActivity(intent)
                vm.updateStatus = "Sta installeren toe en tik opnieuw"
                return
            } catch (e: ActivityNotFoundException) {
                // Geen instellingenscherm op dit horloge: gewoon proberen, Android vraagt het dan zelf.
            }
        }
        vm.installUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != Updater.ACTION_INSTALL_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> vm.updateStatus = "Geïnstalleerd"
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "code $status"
                vm.updateStatus = "Mislukt: $message"
            }
        }
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun PadelApp(vm: MatchViewModel) {
    when (vm.screen) {
        Screen.Start -> StartScreen(vm)
        Screen.Match -> MatchScreen(vm)
        Screen.ConfirmStop -> ConfirmStopScreen(vm)
    }
}

@Composable
private fun StartScreen(vm: MatchViewModel) {
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(stringResource(R.string.app_name)) } }
            vm.update?.let { info ->
                item {
                    val activity = LocalContext.current as? MainActivity
                    Chip(
                        onClick = { activity?.startUpdate() },
                        label = { Text("Update naar ${info.versionName}") },
                        secondaryLabel = vm.updateStatus.let { status ->
                            if (status != null) { { Text(status) } } else null
                        },
                        colors = ChipDefaults.gradientBackgroundChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                ToggleChip(
                    checked = vm.settings.goldenPoint,
                    onCheckedChange = { vm.updateSettings(vm.settings.copy(goldenPoint = it)) },
                    label = { Text("Gouden punt") },
                    secondaryLabel = { Text(if (vm.settings.goldenPoint) "Aan" else "Uit, met voordeel") },
                    toggleControl = { Switch(checked = vm.settings.goldenPoint) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = {
                        val next = if (vm.settings.setsToWin == 2) 1 else 2
                        vm.updateSettings(vm.settings.copy(setsToWin = next))
                    },
                    label = { Text("Sets") },
                    secondaryLabel = { Text(if (vm.settings.setsToWin == 2) "Best of 3" else "1 set") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { vm.updateSettings(vm.settings.copy(firstServer = vm.settings.firstServer.other)) },
                    label = { Text("Eerste opslag") },
                    secondaryLabel = { Text(teamName(vm.settings.firstServer)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { vm.startMatch() },
                    label = { Text("Start partij") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "Versie ${BuildConfig.VERSION_NAME}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun MatchScreen(vm: MatchViewModel) {
    val state = vm.state
    val haptics = LocalHapticFeedback.current
    val activity = LocalContext.current as? MainActivity

    // Scherm aan houden tijdens de partij, zodat je niet steeds terug moet naar de app.
    DisposableEffect(state.winner) {
        activity?.keepScreenOn(state.winner == null)
        onDispose { activity?.keepScreenOn(false) }
    }
    BackHandler { vm.askStop() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxSize()) {
            TeamHalf(
                state = state,
                team = Team.A,
                color = TeamAColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.point(Team.A)
                },
            )
            TeamHalf(
                state = state,
                team = Team.B,
                color = TeamBColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.point(Team.B)
                },
            )
        }

        // Bovenaan: gespeelde sets en de status van de game.
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.sets.isNotEmpty()) {
                Text(
                    text = state.sets.joinToString("  ") { "${it.gamesA}-${it.gamesB}" },
                    fontSize = 13.sp,
                    color = Color.White,
                )
            }
            val status = when {
                state.winner != null -> "${teamName(state.winner)} wint!"
                state.tiebreak -> "Tiebreak"
                state.isGoldenPointMoment -> "Gouden punt"
                else -> null
            }
            if (status != null) {
                Text(text = status, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Yellow)
            }
        }

        // Onderaan: ongedaan maken, of na afloop een nieuwe partij.
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = { vm.undo() },
                enabled = vm.canUndo,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
            ) { Text("↶", fontSize = 18.sp) }
            if (state.winner != null) {
                Button(
                    onClick = { vm.stopMatch() },
                    modifier = Modifier.size(ButtonDefaults.SmallButtonSize),
                ) { Text("✓", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun TeamHalf(
    state: MatchState,
    team: Team,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val games = if (team == Team.A) state.gamesA else state.gamesB
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(color.copy(alpha = if (state.winner == null || state.winner == team) 1f else 0.4f))
            .clickable(enabled = state.winner == null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(teamName(team), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            Text(
                text = state.pointLabel(team),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text("Games $games", fontSize = 13.sp, color = Color.White)
            Spacer(Modifier.height(4.dp))
            // Gele stip bij het team dat serveert.
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (state.winner == null && state.server == team) Color.Yellow else Color.Transparent,
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun ConfirmStopScreen(vm: MatchViewModel) {
    BackHandler { vm.cancelStop() }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Partij stoppen?", textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.cancelStop() }, colors = ButtonDefaults.secondaryButtonColors()) {
                Text("Nee")
            }
            Spacer(Modifier.width(4.dp))
            Button(onClick = { vm.stopMatch() }) { Text("Ja") }
        }
    }
}

private fun teamName(team: Team): String = if (team == Team.A) "Wij" else "Zij"
