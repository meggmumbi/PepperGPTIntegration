package com.example.peppergptintegration.ui.session

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.peppergptintegration.R
import com.example.peppergptintegration.data.Exercise
import com.example.peppergptintegration.data.SessionItem
import com.example.peppergptintegration.ui.components.Adaptive
import com.example.peppergptintegration.ui.components.ChunkyButton
import com.example.peppergptintegration.ui.components.ErrorBox
import com.example.peppergptintegration.ui.components.LoadingBox
import com.example.peppergptintegration.ui.components.Notice
import com.example.peppergptintegration.ui.components.Pill
import com.example.peppergptintegration.ui.components.QuietButton
import com.example.peppergptintegration.ui.session.SessionViewModel.Phase
import com.example.peppergptintegration.ui.session.SessionViewModel.Stage
import com.example.peppergptintegration.ui.session.SessionViewModel.Tone
import com.example.peppergptintegration.ui.theme.Palette

/**
 * A whole study session on one screen: the items, then the questionnaire, the
 * summary and the researcher's note. Which of those is showing is the view
 * model's [Stage]; this file only draws it.
 */
@Composable
fun SessionScreen(
    childId: String,
    step: String,
    resumeSessionId: String?,
    onExit: () -> Unit,
    vm: SessionViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.start(childId, step, resumeSessionId) }
    LaunchedEffect(vm.stage) { if (vm.stage == Stage.Done) onExit() }

    var confirmEnd by remember { mutableStateOf(false) }
    // Back never drops a session silently: mid-run it asks, afterwards it is ignored
    // so a questionnaire or note cannot be lost to a stray press.
    BackHandler {
        when (vm.stage) {
            Stage.Intro, Stage.Failed -> onExit()
            Stage.Running, Stage.Revisit -> confirmEnd = true
            else -> Unit
        }
    }

    Adaptive(Modifier.background(Palette.Background).systemBarsPadding()) { wide ->
        when (vm.stage) {
            Stage.Loading, Stage.Done -> LoadingBox(label = "One moment…")
            Stage.Failed -> Column {
                ErrorBox(vm.failure.orEmpty(), vm::retryFailed, Modifier.weight(1f))
                QuietButton("Back to the participant", onExit,
                    Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp),
                    color = Palette.Muted)
            }
            Stage.Intro -> Intro(vm, wide, onExit)
            Stage.Running -> Running(vm, wide) { confirmEnd = true }
            Stage.Revisit -> Interstitial(
                "One more look",
                "Let's go back to a few words from earlier and try them once more.",
            )
            Stage.Questionnaire -> QuestionnairePage(vm, wide)
            Stage.Summary -> SummaryPage(vm, wide)
            Stage.Note -> NotePage(vm, wide)
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End this session now?") },
            text = {
                Text("What has been recorded so far is kept and the session is marked as " +
                    "ended early. It can be resumed later from the participant's page.")
            },
            confirmButton = {
                TextButton(onClick = { confirmEnd = false; vm.endEarly() }) {
                    Text("End session", color = Palette.Red)
                }
            },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun Interstitial(title: String, body: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = Palette.Sky,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(body, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 640.dp))
    }
}

@Composable
private fun Intro(vm: SessionViewModel, wide: Boolean, onExit: () -> Unit) {
    val remaining = vm.items.size - vm.resumedAt
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (wide) 48.dp else 20.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(Palette.SkyLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_c_mic), contentDescription = null,
                tint = Palette.Sky, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(vm.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (vm.resumedAt > 0) "Carrying on: $remaining of ${vm.items.size} items left."
            else "${vm.items.size} short items. It takes a few minutes.",
            style = MaterialTheme.typography.titleMedium, color = Palette.Muted,
        )
        Spacer(Modifier.height(28.dp))
        Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tip("1", "Listen to Pepper, then tap the microphone and speak clearly.")
            Tip("2", "For listening items, tap the answer and press Check.")
            Tip("3", "You can hear anything again, or skip an item you cannot do.")
            if (!vm.givesFeedback) {
                Tip("4", "Pepper will not say whether an answer was right this time. " +
                    "Just do your best on each one.")
            }
        }
        Spacer(Modifier.height(32.dp))
        ChunkyButton(
            if (vm.resumedAt > 0) "Carry on" else "Start", vm::begin, Modifier.width(320.dp),
            icon = Icons.Filled.PlayArrow, color = Palette.Coral, edge = Palette.CoralDark, height = 64.dp,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onExit) { Text("Not now", color = Palette.Muted) }
    }
}

@Composable
private fun Tip(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Palette.SkyLight),
            contentAlignment = Alignment.Center) {
            Text(number, color = Palette.SkyDeep, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

// --- the item screen --------------------------------------------------------

@Composable
private fun Running(vm: SessionViewModel, wide: Boolean, onClose: () -> Unit) {
    val item = vm.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 40.dp else 16.dp)) {
            Spacer(Modifier.height(if (wide) 20.dp else 10.dp))
            TopBar(vm, onClose)
            Spacer(Modifier.height(if (wide) 24.dp else 16.dp))
            if (item == null) {
                LoadingBox(Modifier.weight(1f))
            } else {
                Text(Exercise.title(item.type), style = MaterialTheme.typography.headlineMedium)
                if (item.target_feature_label != null || vm.inRevisit) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.inRevisit) {
                            Pill("Second look", background = Palette.AmberLight, content = Palette.Ink)
                        }
                        item.target_feature_label?.let { Pill("Sound: $it") }
                    }
                }
                Spacer(Modifier.height(if (wide) 20.dp else 14.dp))
                if (wide) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Box(Modifier.weight(1.15f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Stimulus(vm, item, wide)
                        }
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { Response(vm, item, wide) }
                    }
                } else {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Stimulus(vm, item, wide)
                        Spacer(Modifier.height(28.dp))
                        Response(vm, item, wide)
                    }
                }
                SkipLink(vm, item)
            }
        }
        FeedbackSheet(vm, wide, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TopBar(vm: SessionViewModel, onClose: () -> Unit) {
    val total = vm.items.size.coerceAtLeast(1)
    val progress by animateFloatAsState(vm.index.toFloat() / total, tween(500), label = "progress")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = "End session", tint = Palette.Muted,
                modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(50)).background(Palette.Line)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0.03f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(if (vm.inRevisit) Palette.Amber else Palette.Green)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text("${(vm.index + 1).coerceAtMost(total)} / $total",
            style = MaterialTheme.typography.titleMedium, color = Palette.Muted)
    }
}

/** What the participant is asked about: the word, the sentence, the sound. */
@Composable
private fun Stimulus(vm: SessionViewModel, item: SessionItem, wide: Boolean) {
    val canReplay = vm.phase == Phase.Respond
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (item.type) {
            Exercise.MINIMAL_PAIR_ID -> {
                if (vm.stimulusMissing) {
                    Notice(
                        "No recording has been made for this item yet, so it cannot be played. " +
                            "Skip it, and record its audio in the console's Stimuli page.",
                        background = Palette.AmberLight, content = Palette.Ink,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SoundButton(R.drawable.ic_c_volume, "Play", canReplay, 112.dp, vm.phase == Phase.Prompt) {
                            vm.replay(slow = false)
                        }
                        SoundButton(R.drawable.ic_c_slow, "Play slowly", canReplay, 112.dp, false) {
                            vm.replay(slow = true)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Play   ·   Slower", color = Palette.Muted,
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            Exercise.SCHWA_SPOT, Exercise.STRESS_PLACE -> {
                Syllables(vm, item, wide)
                Spacer(Modifier.height(20.dp))
                ReplayRow(vm, canReplay)
            }
            else -> {
                val shape = RoundedCornerShape(24.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(Palette.Surface)
                        .border(2.dp, Palette.LineSoft, shape)
                        .padding(horizontal = 24.dp, vertical = if (wide) 44.dp else 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stimulusText(item),
                        fontSize = when {
                            item.type == Exercise.REPEAT_WORD || item.type == Exercise.SAME_SOUND_MATCH ->
                                if (wide) 64.sp else 44.sp
                            item.type == Exercise.READ_SENTENCE -> if (wide) 34.sp else 26.sp
                            else -> if (wide) 28.sp else 22.sp
                        },
                        lineHeight = if (item.type == Exercise.READ_SENTENCE) (if (wide) 46.sp else 36.sp)
                        else (if (wide) 72.sp else 52.sp),
                        fontWeight = FontWeight.Bold, color = Palette.Ink, textAlign = TextAlign.Center,
                    )
                    if (item.type == Exercise.SAME_SOUND_MATCH) {
                        Spacer(Modifier.height(8.dp))
                        Text("Which word below has the same vowel sound?", color = Palette.Muted,
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(20.dp))
                ReplayRow(vm, canReplay)
            }
        }
    }
}

/** The word or sentence, with the letters that carry the target sound tinted. */
private fun stimulusText(item: SessionItem): AnnotatedString = buildAnnotatedString {
    val tint = SpanStyle(color = Palette.Coral)
    when (item.type) {
        Exercise.READ_SENTENCE -> {
            val sentence = item.sentence ?: item.word
            append(sentence)
            val target = item.targetWord
            val at = if (target.isNullOrBlank()) -1 else sentence.indexOf(target, ignoreCase = true)
            if (at >= 0 && target != null) {
                addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = Palette.SkyDeep),
                    at, at + target.length)
                item.highlight?.let { span ->
                    if (span.end <= target.length && span.start < span.end) {
                        addStyle(tint, at + span.start, at + span.end)
                    }
                }
            }
        }
        Exercise.SAME_SOUND_MATCH -> append(item.cue.orEmpty())
        Exercise.PICTURE_DESCRIBE -> append(item.promptText ?: item.description.orEmpty())
        Exercise.REPEAT_WORD -> {
            val word = item.word
            append(word)
            item.highlight?.let { span ->
                if (span.end <= word.length && span.start < span.end) addStyle(tint, span.start, span.end)
            }
        }
        else -> append(item.description.orEmpty())
    }
}

@Composable
private fun ReplayRow(vm: SessionViewModel, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SoundButton(R.drawable.ic_c_volume, "Hear it again", enabled, 60.dp, vm.phase == Phase.Prompt) {
            vm.replay(slow = false)
        }
        SoundButton(R.drawable.ic_c_slow, "Hear it slowly", enabled, 60.dp, false) {
            vm.replay(slow = true)
        }
        Text(if (vm.phase == Phase.Prompt) "Pepper is speaking…" else "Hear it again, or slower",
            color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SoundButton(
    icon: Int, label: String, enabled: Boolean, size: Dp, playing: Boolean, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (size > 80.dp) 28.dp else 18.dp)
    Box(
        Modifier.size(size).clip(shape)
            .background(if (enabled || playing) Palette.Sky else Palette.Line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = Color.White,
            modifier = Modifier.size(size * 0.5f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Syllables(vm: SessionViewModel, item: SessionItem, wide: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.syllables.forEachIndexed { position, syllable ->
            val chosen = position in vm.selectedSyllables
            val state = when {
                position in vm.revealSyllables -> TileState.Right
                chosen && vm.answerTone == Tone.Good -> TileState.Right
                chosen && vm.answerTone == Tone.Bad -> TileState.Wrong
                chosen -> TileState.Chosen
                else -> TileState.Idle
            }
            Tile(syllable, state, fontSize = if (wide) 40 else 30, minWidth = 72.dp) {
                vm.tapSyllable(position)
            }
        }
    }
}

private enum class TileState { Idle, Chosen, Right, Wrong }

/** An answer the participant can tap: a bordered tile on a thicker bottom edge. */
@Composable
private fun Tile(
    text: String,
    state: TileState,
    modifier: Modifier = Modifier,
    fontSize: Int = 26,
    minWidth: Dp = 120.dp,
    onClick: () -> Unit,
) {
    val (fill, line, ink) = when (state) {
        TileState.Idle -> Triple(Palette.Surface, Palette.Line, Palette.Ink)
        TileState.Chosen -> Triple(Palette.SkyLight, Palette.Sky, Palette.SkyDeep)
        TileState.Right -> Triple(Palette.GreenLight, Palette.Green, Palette.GreenDark)
        TileState.Wrong -> Triple(Palette.RedLight, Palette.Red, Palette.Red)
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.defaultMinSize(minWidth = minWidth, minHeight = 72.dp).clip(shape).background(line)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(16.dp)).background(fill)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = ink,
            textAlign = TextAlign.Center)
    }
}

/** How the participant answers: the microphone, or a set of options and Check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Response(vm: SessionViewModel, item: SessionItem, wide: Boolean) {
    if (item.needs_speech) {
        MicResponse(vm)
        return
    }
    val options = item.options
    if (options.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.forEach { option ->
                val chosen = vm.selectedOption == option
                val state = when {
                    vm.revealOption == option -> TileState.Right
                    chosen && vm.answerTone == Tone.Good -> TileState.Right
                    chosen && vm.answerTone == Tone.Bad -> TileState.Wrong
                    chosen -> TileState.Chosen
                    else -> TileState.Idle
                }
                Tile(option, state, fontSize = if (wide) 30 else 24, minWidth = if (wide) 180.dp else 132.dp) {
                    vm.select(option)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    vm.hint?.let {
        Text(it, color = Palette.Muted, style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(14.dp))
    }
    ChunkyButton(
        "Check", vm::check, Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        enabled = vm.canCheck, loading = vm.phase == Phase.Scoring,
        color = Palette.Green, edge = Palette.GreenDark, height = 60.dp,
    )
}

@Composable
private fun MicResponse(vm: SessionViewModel) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.toggleRecording() else vm.microphoneRefused()
    }
    val recording = vm.phase == Phase.Recording
    val ready = vm.phase == Phase.Respond || recording
    val pulse = rememberInfiniteTransition(label = "mic")
    val beat by pulse.animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "beat",
    )
    val shape = RoundedCornerShape(36.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.scale(if (recording) beat else 1f).size(148.dp).clip(shape)
                .background(
                    when {
                        recording -> Palette.CoralDark
                        ready -> Palette.SkyDark
                        else -> Palette.Line
                    }
                )
                .clickable(enabled = ready, role = Role.Button) {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) vm.toggleRecording() else permission.launch(Manifest.permission.RECORD_AUDIO)
                }
                .padding(bottom = 8.dp).clip(shape)
                .background(
                    when {
                        recording -> Palette.Coral
                        ready -> Palette.Sky
                        else -> Palette.LineSoft
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (vm.phase == Phase.Scoring) {
                CircularProgressIndicator(Modifier.size(52.dp), color = Palette.Muted, strokeWidth = 5.dp)
            } else {
                Icon(
                    painterResource(if (recording) R.drawable.ic_c_stop else R.drawable.ic_c_mic),
                    contentDescription = if (recording) "Stop" else "Speak",
                    tint = if (ready) Color.White else Palette.Muted, modifier = Modifier.size(68.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when (vm.phase) {
                Phase.Prompt -> "Listen first…"
                Phase.Respond -> vm.hint ?: "Tap the microphone and speak"
                Phase.Recording -> if (vm.heardSpeech) "Listening… it stops when you do" else "Go ahead, I'm listening"
                Phase.Scoring -> "Checking…"
                Phase.Feedback -> ""
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (vm.phase == Phase.Recording) Palette.Coral else Palette.Muted,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

@Composable
private fun SkipLink(vm: SessionViewModel, item: SessionItem) {
    val visible = vm.phase == Phase.Respond || vm.phase == Phase.Prompt
    val (label, reason) = when {
        item.needs_speech -> "Can't speak now" to "cant_say"
        item.type == Exercise.MINIMAL_PAIR_ID -> "Can't listen now" to "cant_hear"
        else -> "Skip this one" to "other"
    }
    Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
        if (visible) {
            TextButton(onClick = { vm.skip(reason) }) {
                Text(label.uppercase(), color = Palette.Muted, style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 1.sp)
            }
        }
    }
}

/** The verdict, rising from the bottom edge while the robot says it. */
@Composable
private fun FeedbackSheet(vm: SessionViewModel, wide: Boolean, modifier: Modifier) {
    val shown = vm.feedback
    AnimatedVisibility(
        visible = shown != null && vm.phase == Phase.Feedback,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        // Held so the sheet keeps its text while it slides away.
        var last by remember { mutableStateOf(shown) }
        if (shown != null) last = shown
        val fb = last ?: return@AnimatedVisibility
        val (fill, ink) = when (fb.tone) {
            Tone.Good -> Palette.GreenLight to Palette.GreenDark
            Tone.Bad -> Palette.CoralLight to Palette.CoralDark
            Tone.Neutral -> Palette.SkyLight to Palette.SkyDeep
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(fill)
                .padding(horizontal = if (wide) 48.dp else 20.dp, vertical = if (wide) 28.dp else 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fb.tone == Tone.Good) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Green),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Text(fb.text, color = ink, fontWeight = FontWeight.Bold,
                    style = if (wide) MaterialTheme.typography.headlineSmall
                    else MaterialTheme.typography.titleLarge)
            }
            fb.advice?.let {
                Spacer(Modifier.height(8.dp))
                Text("Recording: $it", color = ink.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall)
            }
            if (fb.rateAttemptId != null) {
                Spacer(Modifier.height(16.dp))
                Text("How helpful was that?", color = ink, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Not", color = ink, style = MaterialTheme.typography.bodyMedium)
                    (1..5).forEach { value ->
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Palette.Surface)
                                .border(2.dp, ink.copy(alpha = 0.4f), CircleShape)
                                .clickable(role = Role.Button) { vm.rateHelpfulness(value) },
                            contentAlignment = Alignment.Center,
                        ) { Text("$value", color = ink, style = MaterialTheme.typography.titleLarge) }
                    }
                    Text("Very", color = ink, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { vm.rateHelpfulness(null) }) { Text("Pass", color = ink) }
                }
            }
        }
    }
}
