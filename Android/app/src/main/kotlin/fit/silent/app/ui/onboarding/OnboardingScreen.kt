package fit.silent.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fit.silent.app.R
import fit.silent.app.SilentFitApp
import fit.silent.app.ui.LocalGraph
import fit.silent.app.ui.components.ButtonStyle
import fit.silent.app.ui.components.Divider
import fit.silent.app.ui.components.FormPage
import fit.silent.app.ui.components.Icon
import fit.silent.app.ui.components.InlineStatus
import fit.silent.app.ui.components.Label
import fit.silent.app.ui.components.NdButton
import fit.silent.app.ui.components.Section
import fit.silent.app.ui.components.SegmentedBar
import fit.silent.app.ui.components.SegmentedSpinner
import fit.silent.app.ui.components.TextInput
import fit.silent.app.ui.components.Txt
import fit.silent.app.ui.components.dotGrid
import fit.silent.app.ui.theme.Nd
import fit.silent.app.ui.theme.Space
import fit.silent.app.watch.LinkPhase
import fit.silent.app.watch.SppLink
import fit.silent.app.watch.WatchBonding
import fit.silent.core.protocol.WatchQr

private enum class Step(val number: Int) { WELCOME(0), PERMISSIONS(1), FIND(2), SCAN(2), PAIR(3), READY(4) }

private const val STEPS = 4

/**
 * First run and re-pairing. Welcome → permissions → find the watch (QR code or an already-paired device)
 * → Bluetooth bonding and the watch's own confirmation → ready.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, pairOnly: Boolean = false) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(if (pairOnly) Step.FIND else Step.WELCOME) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var accountId by rememberSaveable { mutableStateOf(settings.accountId) }
    var refresh by remember { mutableIntStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val first = if (pairOnly) Step.FIND else Step.WELCOME
    BackHandler(enabled = step != first && step != Step.READY) {
        step = when (step) {
            Step.SCAN, Step.PAIR -> Step.FIND
            Step.FIND -> Step.PERMISSIONS
            else -> Step.WELCOME
        }
    }

    fun finish() {
        graph.settings.update { it.copy(onboarded = true) }
        onDone()
    }

    fun connect(address: String) {
        target = address
        step = Step.PAIR
    }

    FormPage {
        if (step == Step.WELCOME) {
            Welcome(
                onStart = { step = Step.PERMISSIONS },
                onDemo = {
                    graph.session.setDemoMode(true)
                    finish()
                },
            )
            return@FormPage
        }
        Progress(step.number)
        when (step) {
            Step.PERMISSIONS -> Permissions(
                refresh = refresh,
                onAllow = { permissions.launch(graph.phone.runtimePermissions().toTypedArray()) },
                onNext = { step = Step.FIND },
            )
            Step.FIND -> FindWatch(
                refresh = refresh,
                accountId = accountId,
                onAccountId = { accountId = it },
                onScan = { step = Step.SCAN },
                onPick = { address ->
                    graph.settings.update { it.copy(accountId = accountId.trim().ifEmpty { it.accountId }) }
                    connect(address)
                },
                onAllow = { permissions.launch(graph.phone.runtimePermissions().toTypedArray()) },
            )
            Step.SCAN -> Scan(onAddress = { address ->
                graph.settings.update { it.copy(accountId = accountId.trim().ifEmpty { it.accountId }) }
                connect(address)
            }, onCancel = { step = Step.FIND })
            Step.PAIR -> PairWatch(
                address = target.orEmpty(),
                onConnected = { step = Step.READY },
                onRescan = { step = Step.SCAN },
                onBack = { step = Step.FIND },
            )
            Step.READY -> Ready(refresh = refresh, onFinish = ::finish)
            Step.WELCOME -> Unit
        }
    }
}

// ------------------------------------------------------------------ steps

@Composable
private fun Welcome(onStart: () -> Unit, onDemo: () -> Unit) {
    val c = Nd.colors
    Box(Modifier.fillMaxWidth().height(160.dp).padding(top = Space.xl).dotGrid(c.borderVisible, 14.dp, 1.5.dp))
    Spacer(Modifier.height(Space.xxl))
    Txt("SILENT", Nd.type.displayXl, color = c.textDisplay)
    Txt("FIT", Nd.type.displayXl, color = c.textDisplay)
    Spacer(Modifier.height(Space.lg))
    Txt(
        "A companion for Noise watches that keeps everything on this phone. No account, no cloud.",
        Nd.type.body,
        Modifier.widthIn(max = 320.dp),
        color = c.textSecondary,
    )
    Spacer(Modifier.height(Space.xl))
    Label("Works with NF 2 · Bluetooth Classic")
    Spacer(Modifier.height(Space.xxl))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        NdButton("Set up watch", onStart)
        NdButton("Try demo", onDemo, style = ButtonStyle.GHOST)
    }
}

@Composable
private fun Permissions(refresh: Int, onAllow: () -> Unit, onNext: () -> Unit) {
    val graph = LocalGraph.current
    val phone = graph.phone
    val c = Nd.colors
    val bluetooth = remember(refresh) { phone.hasBluetoothPermission }
    val notifications = remember(refresh) { phone.has(NOTIFICATIONS) }
    val calls = remember(refresh) { phone.has(Manifest.permission.ANSWER_PHONE_CALLS) }
    val missing = remember(refresh) { phone.missingPermissions().isNotEmpty() }
    Title("Permissions", "SilentFit asks only for what the watch uses. Nothing leaves the phone.")
    Column {
        Divider()
        PermissionRow(R.drawable.ic_bluetooth, "Nearby devices", "Find, pair and stay connected to the watch", bluetooth)
        PermissionRow(R.drawable.ic_bell, "Notifications", "Connection status and find-my-phone", notifications)
        PermissionRow(R.drawable.ic_phone, "Phone and SMS", "Answer calls and send quick replies from the watch", calls)
    }
    Spacer(Modifier.height(Space.xl))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
        if (missing) NdButton("Allow", onAllow)
        NdButton("Continue", onNext, style = if (missing) ButtonStyle.SECONDARY else ButtonStyle.PRIMARY, enabled = bluetooth)
    }
    if (!bluetooth) {
        Spacer(Modifier.height(Space.md))
        Txt("Nearby devices is required. The others can be granted later in Settings.", Nd.type.bodySm, color = c.textSecondary)
    }
}

@Composable
private fun FindWatch(
    refresh: Int,
    accountId: String,
    onAccountId: (String) -> Unit,
    onScan: () -> Unit,
    onPick: (String) -> Unit,
    onAllow: () -> Unit,
) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val c = Nd.colors
    Title("Find your watch", "Open the QR code screen on the watch, then scan it. SilentFit pairs over Bluetooth from here.")
    NdButton("Scan QR code", onScan, icon = R.drawable.ic_scan_line, enabled = graph.phone.hasBluetoothPermission)
    if (!graph.phone.hasBluetoothPermission) {
        Spacer(Modifier.height(Space.md))
        InlineStatus("Nearby devices permission needed", color = c.warning)
        Spacer(Modifier.height(Space.sm))
        NdButton("Allow", onAllow, style = ButtonStyle.SECONDARY)
    }

    val devices = remember(refresh) { SppLink.bonded(context) }
    Section("Already paired", trailing = { Label("${devices.size}") }) {
        if (devices.isEmpty()) Txt("No Bluetooth devices are paired with this phone.", Nd.type.bodySm, color = c.textDisabled)
        devices.forEach { d ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onPick(d.address) }.padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(R.drawable.ic_watch, tint = if (d.looksLikeWatch) c.textDisplay else c.textDisabled)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Txt(d.name, Nd.type.body, maxLines = 1)
                    Label(d.address, color = c.textDisabled)
                }
                Icon(R.drawable.ic_chevron_right, tint = c.textDisabled, size = 16.dp)
            }
            Divider()
        }
    }

    if (graph.phone.noiseFitInstalled) {
        Section("NoiseFit is installed") {
            Txt(
                "The watch accepts one app at a time. If NoiseFit keeps reconnecting, restrict its background use or disable it.",
                Nd.type.bodySm,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(Space.md))
            NdButton("NoiseFit app info", { graph.phone.openNoiseFitSettings() }, style = ButtonStyle.SECONDARY)
        }
    }

    Section("Moving from NoiseFit") {
        Txt(
            "A watch bound to a NoiseFit account only accepts that account. Enter its ID to keep the binding, or leave empty and confirm on the watch.",
            Nd.type.bodySm,
            color = c.textSecondary,
        )
        TextInput(accountId, { onAccountId(it.trim().take(100)) }, "NoiseFit account ID", placeholder = "Optional", mono = true)
    }
}

@Composable
private fun Scan(onAddress: (String) -> Unit, onCancel: () -> Unit) {
    val graph = LocalGraph.current
    val c = Nd.colors
    var granted by remember { mutableStateOf(graph.phone.has(Manifest.permission.CAMERA)) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var rejected by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (!granted) camera.launch(Manifest.permission.CAMERA) }

    Title("Scan the watch", "Hold the phone about 20 cm from the watch and keep it steady. The code is read on this phone; no image is saved.")
    if (granted) {
        QrViewfinder(onCode = { text ->
            val address = WatchQr.address(text)
            android.util.Log.i("SilentFit", "QR read, address=$address, length=${text.length}: ${text.take(160)}")
            if (address != null) onAddress(address) else rejected = text
        })
        Spacer(Modifier.height(Space.md))
        if (rejected != null) InlineStatus("No watch address in that code", color = c.warning)
        else InlineStatus("Looking for a code")
    } else {
        Box(Modifier.fillMaxWidth().height(240.dp).dotGrid(c.borderVisible, 14.dp, 1.5.dp))
        Spacer(Modifier.height(Space.md))
        InlineStatus("Camera permission needed", color = c.warning)
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            NdButton("Allow camera", { camera.launch(Manifest.permission.CAMERA) })
            NdButton("App settings", { graph.phone.openAppSettings() }, style = ButtonStyle.GHOST)
        }
    }
    Spacer(Modifier.height(Space.xl))
    NdButton("Cancel", onCancel, style = ButtonStyle.SECONDARY)
}

private enum class Stage { WAITING, ACTIVE, DONE, FAILED }

@Composable
private fun PairWatch(address: String, onConnected: () -> Unit, onRescan: () -> Unit, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val c = Nd.colors
    val connection by graph.session.connection.collectAsStateWithLifecycle()
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    var attempt by remember { mutableIntStateOf(0) }
    var bond by remember { mutableStateOf<WatchBonding.Step?>(null) }
    var linkStarted by remember { mutableStateOf(false) }
    var sawProgress by remember { mutableStateOf(false) }
    var accountId by remember { mutableStateOf(settings.accountId) }

    LaunchedEffect(address, attempt) {
        linkStarted = false
        sawProgress = false
        val current = graph.settings.value
        if (current.demoMode) graph.session.setDemoMode(false)
        val alreadyLinked = current.pairedWatch?.address.equals(address, true) && graph.session.connection.value.isConnected
        WatchBonding.bond(context, address).collect { s ->
            bond = s
            if (s is WatchBonding.Step.Bonded) {
                graph.settings.update { it.copy(keepConnected = true, demoMode = false) }
                if (alreadyLinked) sawProgress = true
                else {
                    graph.session.pair(s.watch)
                    (context.applicationContext as SilentFitApp).ensureLink()
                }
                linkStarted = true
            }
        }
    }
    LaunchedEffect(linkStarted, connection.phase) {
        if (!linkStarted) return@LaunchedEffect
        if (connection.phase != LinkPhase.CONNECTED) sawProgress = true
        else if (sawProgress) onConnected()
    }

    val name = (bond as? WatchBonding.Step.Bonding)?.name ?: (bond as? WatchBonding.Step.Bonded)?.watch?.name ?: "NF 2_${address.replace(":", "").takeLast(4)}"
    val bondFailed = bond as? WatchBonding.Step.Failed
    val phase = connection.phase.takeIf { linkStarted }
    val linkFailed = phase == LinkPhase.FAILED

    Title(name, address)
    Column {
        Divider()
        StageRow(
            "Bluetooth pairing",
            when (bond) {
                null, WatchBonding.Step.Searching -> Stage.ACTIVE
                is WatchBonding.Step.Bonding -> Stage.ACTIVE
                is WatchBonding.Step.Bonded -> Stage.DONE
                is WatchBonding.Step.Failed -> Stage.FAILED
            },
            when (bond) {
                WatchBonding.Step.Searching, null -> "Searching nearby"
                is WatchBonding.Step.Bonding -> "Accept the pairing request on this phone"
                else -> null
            },
        )
        StageRow(
            "Connecting",
            when {
                phase == null -> Stage.WAITING
                phase == LinkPhase.CONNECTING || phase == LinkPhase.HANDSHAKE || phase == LinkPhase.DISCONNECTED -> Stage.ACTIVE
                linkFailed && connection.message.isNotEmpty() && !connection.message.contains("account") && !connection.message.contains("declined") -> Stage.FAILED
                else -> Stage.DONE
            },
            null,
        )
        StageRow(
            "Confirm on the watch",
            when {
                phase == LinkPhase.PAIRING -> Stage.ACTIVE
                phase == LinkPhase.CONNECTED -> Stage.DONE
                linkFailed && (connection.message.contains("account") || connection.message.contains("declined")) -> Stage.FAILED
                else -> Stage.WAITING
            },
            if (phase == LinkPhase.PAIRING) "Tap the check mark on the watch" else null,
        )
    }

    val error = bondFailed?.reason ?: connection.message.takeIf { linkFailed }
    if (error != null) {
        Spacer(Modifier.height(Space.lg))
        InlineStatus(error, color = c.accent)
        if (linkFailed && connection.message.contains("account")) {
            TextInput(accountId, { accountId = it.trim().take(100) }, "NoiseFit account ID", placeholder = "From NoiseFit › Profile", mono = true)
        }
        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            NdButton("Try again", {
                if (accountId.isNotBlank()) graph.settings.update { it.copy(accountId = accountId.trim()) }
                bond = null
                attempt++
            })
            NdButton("Scan again", onRescan, style = ButtonStyle.SECONDARY)
        }
    } else {
        Spacer(Modifier.height(Space.xl))
        NdButton("Cancel", {
            graph.session.disconnect()
            onBack()
        }, style = ButtonStyle.GHOST)
    }
}

@Composable
private fun Ready(refresh: Int, onFinish: () -> Unit) {
    val graph = LocalGraph.current
    val c = Nd.colors
    val settings by graph.settings.flow.collectAsStateWithLifecycle()
    val access = remember(refresh) { graph.phone.notificationAccessGranted }
    Spacer(Modifier.height(Space.xl))
    Txt("READY", Nd.type.displayLg, color = c.textDisplay)
    Spacer(Modifier.height(Space.sm))
    Txt("${settings.pairedWatch?.name ?: "Your watch"} is connected. Health data syncs in the background from now on.", Nd.type.body, color = c.textSecondary)
    Section("Recommended") {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Notification access", Nd.type.body)
                Txt("Show app notifications and music on the watch", Nd.type.bodySm, color = c.textSecondary)
            }
            Spacer(Modifier.width(Space.md))
            if (access) Label("On", color = c.textPrimary) else NdButton("Allow", { graph.phone.openNotificationAccess() }, style = ButtonStyle.SECONDARY)
        }
        Divider()
    }
    Spacer(Modifier.height(Space.xl))
    NdButton("Open SilentFit", onFinish)
}

// ------------------------------------------------------------------ pieces

@Composable
private fun Progress(number: Int) {
    Spacer(Modifier.height(Space.lg))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label("Setup", Modifier.weight(1f))
        Label("${number.coerceAtMost(STEPS).toString().padStart(2, '0')} / ${STEPS.toString().padStart(2, '0')}")
    }
    Spacer(Modifier.height(Space.sm))
    SegmentedBar(number / STEPS.toFloat(), segments = STEPS, height = 4.dp)
}

@Composable
private fun Title(title: String, body: String) {
    Spacer(Modifier.height(Space.xl))
    Txt(title, Nd.type.displayMd, color = Nd.colors.textDisplay, maxLines = 2)
    Spacer(Modifier.height(Space.sm))
    Txt(body, Nd.type.bodySm, color = Nd.colors.textSecondary)
    Spacer(Modifier.height(Space.lg))
}

@Composable
private fun PermissionRow(icon: Int, title: String, body: String, granted: Boolean) {
    val c = Nd.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.Top) {
        Icon(icon, size = 20.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Txt(title, Nd.type.body)
            Txt(body, Nd.type.bodySm, color = c.textSecondary)
        }
        Spacer(Modifier.width(Space.md))
        Label(if (granted) "Allowed" else "Off", color = if (granted) c.textPrimary else c.textDisabled)
    }
    Divider()
}

@Composable
private fun StageRow(title: String, stage: Stage, detail: String?) {
    val c = Nd.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(title, Nd.type.body, color = if (stage == Stage.WAITING) c.textDisabled else c.textPrimary)
            if (detail != null && stage == Stage.ACTIVE) Txt(detail, Nd.type.bodySm, color = c.textSecondary)
        }
        Spacer(Modifier.width(Space.md))
        when (stage) {
            Stage.ACTIVE -> SegmentedSpinner(size = 16.dp)
            Stage.DONE -> Label("Done", color = c.textPrimary)
            Stage.FAILED -> Label("Failed", color = c.accent)
            Stage.WAITING -> Label("—", color = c.textDisabled)
        }
    }
    Divider()
}

private val NOTIFICATIONS = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else Manifest.permission.VIBRATE
