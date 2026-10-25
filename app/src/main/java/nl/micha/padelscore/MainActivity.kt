package nl.micha.padelscore

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.wear.ambient.AmbientLifecycleObserver
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

    /** True als het scherm gedimd is (ambient mode). De app blijft dan voorop staan. */
    private var ambient by mutableStateOf(false)

    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient = true
            }

            override fun onExitAmbient() {
                ambient = false
            }

            override fun onUpdateAmbient() {}
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)
        setContent {
            MaterialTheme {
                PadelApp(vm, ambient)
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

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
fun PadelApp(vm: MatchViewModel, ambient: Boolean) {
    // Meldingen nodig voor een update die de telefoon start terwijl de app dicht is.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (ambient && vm.screen != Screen.Match) {
        AmbientText("Winner Padel")
        return
    }
    when (vm.screen) {
        Screen.Start -> StartScreen(vm)
        Screen.Match -> MatchScreen(vm, ambient)
        Screen.Menu -> MenuScreen(vm)
        Screen.ConfirmStop -> ConfirmStopScreen(vm)
    }
}

@Composable
private fun StartScreen(vm: MatchViewModel) {
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text(stringResource(R.string.app_name)) } }
            if (vm.update != null) item { UpdateChip(vm) }
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
                // Spelvorm: sets volgens de regels, of alleen games tellen zonder sets.
                val gamesOnly = vm.settings.setsToWin == 0
                Chip(
                    onClick = { vm.updateSettings(vm.settings.copy(setsToWin = if (gamesOnly) 2 else 0)) },
                    label = { Text("Spelvorm") },
                    secondaryLabel = { Text(if (gamesOnly) "Alleen games tellen" else "Sets spelen") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (vm.settings.setsToWin != 0) {
                item {
                    Chip(
                        onClick = {
                            val next = if (vm.settings.setsToWin == 2) 1 else 2
                            vm.updateSettings(vm.settings.copy(setsToWin = next))
                        },
                        label = { Text("Aantal sets") },
                        secondaryLabel = { Text(if (vm.settings.setsToWin == 2) "Best of 3" else "1 set") },
                        colors = ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Chip(
                    onClick = { vm.updateSettings(vm.settings.copy(startRight = !vm.settings.startRight)) },
                    label = { Text("Wij staan") },
                    secondaryLabel = { Text(if (vm.settings.startRight) "Rechts" else "Links") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ToggleChip(
                    checked = vm.settings.changeEnds,
                    onCheckedChange = { vm.updateSettings(vm.settings.copy(changeEnds = it)) },
                    label = { Text("Kant wisselen") },
                    secondaryLabel = { Text(if (vm.settings.changeEnds) "Na oneven games" else "Blijven staan") },
                    toggleControl = { Switch(checked = vm.settings.changeEnds) },
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
                ToggleChip(
                    checked = vm.keepScreenOn,
                    onCheckedChange = { vm.updateKeepScreenOn(it) },
                    label = { Text("Scherm altijd aan") },
                    secondaryLabel = { Text(if (vm.keepScreenOn) "Fel tijdens het spelen" else "Uit: dimt, tik om terug") },
                    toggleControl = { Switch(checked = vm.keepScreenOn) },
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
private fun MatchScreen(vm: MatchViewModel, ambient: Boolean) {
    val state = vm.state
    val haptics = LocalHapticFeedback.current
    val activity = LocalContext.current as? MainActivity

    // Scherm aan houden tijdens de partij, zodat je niet steeds terug moet naar de app.
    DisposableEffect(state.winner, vm.keepScreenOn) {
        activity?.keepScreenOn(vm.keepScreenOn && state.winner == null)
        onDispose { activity?.keepScreenOn(false) }
    }
    BackHandler { vm.openMenu() }

    if (ambient) {
        AmbientMatch(state)
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Bij kant wisselen schuiven de vlakken mee: elk team houdt zijn eigen kleur.
        Row(modifier = Modifier.fillMaxSize()) {
            for (team in listOf(state.leftTeam, state.leftTeam.other)) {
                TeamHalf(
                    state = state,
                    team = team,
                    color = if (team == Team.A) TeamAColor else TeamBColor,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.point(team)
                    },
                )
            }
        }

        // Bovenaan: gespeelde sets en de status van de game.
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.sets.isNotEmpty()) {
                Text(
                    text = state.sets.joinToString("  ") { state.leftFirst(it.gamesA, it.gamesB) },
                    fontSize = 13.sp,
                    color = Color.White,
                )
            }
            val status = state.statusText
            if (status != null) {
                Text(text = status, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Yellow)
            }
        }

        // Geen knoppen tijdens het spelen; ongedaan maken en de rest zit in het menu (terugknop).
        if (state.winner != null) {
            Button(
                onClick = { vm.stopMatch() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .size(ButtonDefaults.SmallButtonSize),
            ) { Text("✓", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun MenuScreen(vm: MatchViewModel) {
    BackHandler { vm.closeMenu() }
    val listState = rememberScalingLazyListState()
    Scaffold(timeText = { TimeText() }) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            item { ListHeader { Text("Menu") } }
            item {
                Chip(
                    onClick = { vm.closeMenu() },
                    label = { Text("Verder spelen") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = {
                        vm.undo()
                        vm.closeMenu()
                    },
                    enabled = vm.canUndo,
                    label = { Text("Laatste punt terug") },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (vm.update != null) item { UpdateChip(vm) }
            item {
                ToggleChip(
                    checked = vm.state.settings.goldenPoint,
                    onCheckedChange = { vm.setGoldenPoint(it) },
                    label = { Text("Gouden punt") },
                    toggleControl = { Switch(checked = vm.state.settings.goldenPoint) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ToggleChip(
                    checked = vm.state.settings.changeEnds,
                    onCheckedChange = { vm.setChangeEnds(it) },
                    label = { Text("Kant wisselen") },
                    toggleControl = { Switch(checked = vm.state.settings.changeEnds) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ToggleChip(
                    checked = vm.keepScreenOn,
                    onCheckedChange = { vm.updateKeepScreenOn(it) },
                    label = { Text("Scherm altijd aan") },
                    toggleControl = { Switch(checked = vm.keepScreenOn) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Chip(
                    onClick = { vm.askStop() },
                    label = { Text("Partij stoppen") },
                    colors = ChipDefaults.chipColors(backgroundColor = Color(0xFF8B1A1A)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun UpdateChip(vm: MatchViewModel) {
    val info = vm.update ?: return
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
            Text(teamName(team), fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            Text(
                text = state.pointLabel(team),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text("Games $games", fontSize = 16.sp, color = Color.White)
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

private fun teamName(team: Team): String = team.label

/** Games in de volgorde van het scherm: eerst het team dat links staat. */
private fun MatchState.leftFirst(gamesA: Int, gamesB: Int): String =
    if (leftTeam == Team.A) "$gamesA-$gamesB" else "$gamesB-$gamesA"

/**
 * Gedimde stand: zwart met grijze en witte tekst, geen vlakken en geen knoppen.
 * Zuinig, en het scherm brandt niet in. Tik, knop of pols draaien maakt de app weer actief.
 */
@Composable
private fun AmbientMatch(state: MatchState) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            text = state.statusText ?: state.sets.joinToString("  ") { state.leftFirst(it.gamesA, it.gamesB) },
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 22.dp),
        )
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            for (team in listOf(state.leftTeam, state.leftTeam.other)) {
                val games = if (team == Team.A) state.gamesA else state.gamesB
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val serving = state.winner == null && state.server == team
                    Text(if (serving) "${teamName(team)} •" else teamName(team), fontSize = 12.sp, color = Color.Gray)
                    Text(state.pointLabel(team), fontSize = 40.sp, color = Color.White)
                    Text("Games $games", fontSize = 13.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun AmbientText(text: String) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray)
    }
}
