package com.example.peppergptintegration.ui.participants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.peppergptintegration.data.Api
import com.example.peppergptintegration.data.ApiException
import com.example.peppergptintegration.data.Assignment
import com.example.peppergptintegration.data.Balance
import com.example.peppergptintegration.data.Participant
import com.example.peppergptintegration.data.Plan
import com.example.peppergptintegration.data.Step
import com.example.peppergptintegration.data.Vocab
import com.example.peppergptintegration.ui.components.Adaptive
import com.example.peppergptintegration.ui.components.ChunkyButton
import com.example.peppergptintegration.ui.components.CodeBadge
import com.example.peppergptintegration.ui.components.ErrorBox
import com.example.peppergptintegration.ui.components.Field
import com.example.peppergptintegration.ui.components.LoadingBox
import com.example.peppergptintegration.ui.components.Notice
import com.example.peppergptintegration.ui.components.Overline
import com.example.peppergptintegration.ui.components.Panel
import com.example.peppergptintegration.ui.components.Pill
import com.example.peppergptintegration.ui.components.QuietButton
import com.example.peppergptintegration.ui.theme.Palette
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private fun prettyDate(iso: String?): String? = try {
    iso?.take(10)?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it) }
        ?.let { SimpleDateFormat("MMM d, yyyy", Locale.US).format(it) }
} catch (e: Exception) {
    null
}

/**
 * One participant: who they are, which counterbalancing group they are in,
 * and where they are in the protocol. This is the screen a session starts
 * from. Every fact on it comes from the server in one call, so the tablet
 * cannot disagree with the database about which condition someone is in.
 */
@Composable
fun ParticipantScreen(
    participantId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStart: (step: String, resumeSessionId: String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var person by remember { mutableStateOf<Participant?>(null) }
    var plan by remember { mutableStateOf<Plan?>(null) }
    var balance by remember { mutableStateOf<Balance?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var excluding by remember { mutableStateOf(false) }

    fun load() {
        error = null
        scope.launch {
            try {
                coroutineScope {
                    val p = async { Api.participant(participantId) }
                    val pl = async { Api.plan(participantId) }
                    val b = async { runCatching { Api.balance() }.getOrNull() }
                    person = p.await()
                    plan = pl.await()
                    balance = b.await()
                }
            } catch (e: ApiException) {
                error = e.message
            }
        }
    }

    // Reloaded on return, so a step just finished shows as done.
    LifecycleResumeEffect(participantId) {
        load()
        onPauseOrDispose { }
    }

    fun assign() {
        busy = true
        actionError = null
        scope.launch {
            try {
                Api.assign(participantId)
                load()
            } catch (e: ApiException) {
                actionError = e.message
            } finally {
                busy = false
            }
        }
    }

    Adaptive(Modifier.background(Palette.Background).systemBarsPadding()) { wide ->
        val p = person
        val pl = plan
        when {
            error != null -> ErrorBox(error!!, ::load)
            p == null || pl == null -> LoadingBox()
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wide) 32.dp else 16.dp),
            ) {
                Spacer(Modifier.height(if (wide) 20.dp else 10.dp))
                Header(p, wide, onBack, onEdit)
                Spacer(Modifier.height(16.dp))

                pl.list_problems.orEmpty().forEach {
                    Notice("Word lists: $it", background = Palette.AmberLight, content = Palette.Ink)
                    Spacer(Modifier.height(10.dp))
                }
                if (pl.unstamped_sessions.orEmpty().isNotEmpty()) {
                    Notice(
                        "${pl.unstamped_sessions!!.size} earlier session(s) were not started from " +
                            "the study plan and cannot be attributed to a condition.",
                        background = Palette.AmberLight, content = Palette.Ink,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                actionError?.let {
                    Notice(it)
                    Spacer(Modifier.height(10.dp))
                }

                val assignment: @Composable (Modifier) -> Unit = { m ->
                    AssignmentPanel(pl.assignment, balance, busy, m, ::assign) { excluding = true }
                }
                val protocol: @Composable (Modifier) -> Unit = { m ->
                    ProtocolPanel(pl, m, onStart)
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            assignment(Modifier.fillMaxWidth())
                            BackgroundPanel(p, Modifier.fillMaxWidth())
                        }
                        protocol(Modifier.weight(1f))
                    }
                } else {
                    assignment(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    protocol(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    BackgroundPanel(p, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (excluding) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { excluding = false },
            title = { Text("Exclude from the study?") },
            text = {
                Column {
                    Text("Their record is kept, but they stop counting towards the balance " +
                        "of groups and no further sessions can be started for them.")
                    Spacer(Modifier.height(12.dp))
                    Field(reason, { reason = it }, "Reason", singleLine = false, minLines = 2)
                }
            },
            confirmButton = {
                TextButton(enabled = reason.isNotBlank(), onClick = {
                    excluding = false
                    scope.launch {
                        try {
                            Api.exclude(participantId, reason.trim())
                            load()
                        } catch (e: ApiException) {
                            actionError = e.message
                        }
                    }
                }) { Text("Exclude", color = if (reason.isNotBlank()) Palette.Red else Palette.Muted) }
            },
            dismissButton = { TextButton(onClick = { excluding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(p: Participant, wide: Boolean, onBack: () -> Unit, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        QuietButton(if (wide) "Participants" else "Back", onBack,
            icon = Icons.AutoMirrored.Filled.ArrowBack, color = Palette.Muted)
        Spacer(Modifier.weight(1f))
        QuietButton("Edit", onEdit, icon = Icons.Filled.Edit)
    }
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CodeBadge(p.label, size = 68.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(p.label, style = MaterialTheme.typography.headlineLarge)
            Text(p.summaryLine(), style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
        }
    }
}

/** K is drawn in sky and outlined, D in coral and filled, as in the console. */
@Composable
private fun ConditionPill(text: String, condition: String?) {
    if (condition == "D") Pill(text, background = Palette.Coral, content = Color.White)
    else Pill(text, background = Palette.Surface, content = Palette.Sky, border = Palette.Sky)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssignmentPanel(
    assignment: Assignment?,
    balance: Balance?,
    busy: Boolean,
    modifier: Modifier,
    onAssign: () -> Unit,
    onExclude: () -> Unit,
) {
    Panel(modifier) {
        Overline("Assignment")
        Spacer(Modifier.height(14.dp))
        if (assignment == null) {
            Text("Not yet assigned to a counterbalancing group.",
                style = MaterialTheme.typography.titleMedium, color = Palette.Ink)
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                balance?.per_group.orEmpty().forEach { (group, count) ->
                    Pill("$group · $count", background = Palette.LineSoft, content = Palette.Ink)
                }
            }
            Spacer(Modifier.height(16.dp))
            ChunkyButton(
                "Assign to least-filled group", onAssign, Modifier.fillMaxWidth(), loading = busy,
                icon = Icons.Filled.Add, color = Palette.Coral, edge = Palette.CoralDark,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Least-filled cell, ties broken from the participant's own id. Not random: " +
                    "with forty participants and four cells, random assignment routinely " +
                    "lands 14/8/12/10.",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
            )
        } else {
            Text(assignment.group.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                assignment.blocks.orEmpty().forEach { block ->
                    ConditionPill(
                        "Block ${block.block}: ${block.condition}-${block.word_list}", block.condition,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (assignment.excluded) {
                Notice(
                    "Excluded from the study" +
                        (assignment.exclusion_reason?.let { ": $it" } ?: "."),
                )
            } else {
                Text(
                    "Assigned ${prettyDate(assignment.assigned_at) ?: "earlier"}. Re-assignment " +
                        "is not possible: it would break the counterbalance in a way the " +
                        "session records could not show.",
                    style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                )
                Spacer(Modifier.height(14.dp))
                QuietButton("Exclude from study…", onExclude, color = Palette.Ink)
            }
        }
    }
}

private enum class StepState { Done, Resume, Next, NeedsGroup, Locked }

@Composable
private fun ProtocolPanel(
    plan: Plan,
    modifier: Modifier,
    onStart: (String, String?) -> Unit,
) {
    val steps = plan.steps.orEmpty()
    val excluded = plan.assignment?.excluded == true
    // The protocol is run in order: the first step not yet finished is the
    // only one that can be started, and an interrupted one is resumed, never
    // started twice.
    val current = steps.indexOfFirst { it.done?.completed != true }

    Panel(modifier) {
        Overline("Protocol")
        Spacer(Modifier.height(14.dp))
        steps.forEachIndexed { index, step ->
            val state = when {
                step.done?.completed == true -> StepState.Done
                excluded || index != current -> StepState.Locked
                step.done != null -> StepState.Resume
                // Blocks only exist once a group is assigned, so an unassigned
                // participant goes straight from the pre-test to the post-test
                // in this list. That jump is what has to be stopped.
                plan.assignment == null && step.step != "pretest" -> StepState.NeedsGroup
                else -> StepState.Next
            }
            StepRow(index + 1, step, state, last = index == steps.lastIndex, onStart = onStart)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (excluded) "This participant is excluded, so no further sessions can be started."
            else "Steps run in order. Each one is recorded against this participant with " +
                "the condition its group dictates.",
            style = MaterialTheme.typography.bodySmall, color = Palette.Muted,
        )
    }
}

@Composable
private fun StepRow(
    number: Int,
    step: Step,
    state: StepState,
    last: Boolean,
    onStart: (String, String?) -> Unit,
) {
    val active = state == StepState.Next || state == StepState.Resume
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(
                    when {
                        state == StepState.Done -> Palette.Green
                        active -> Palette.Sky
                        else -> Palette.Line
                    }
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (state == StepState.Done) {
                    Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White,
                        modifier = Modifier.size(20.dp))
                } else {
                    Text("$number", color = if (active) Color.White else Palette.Muted,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!last) {
                Box(Modifier.padding(vertical = 4.dp).width(2.dp).height(if (active) 76.dp else 36.dp)
                    .background(Palette.Line))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 8.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    step.label, style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (state == StepState.Locked) Palette.Muted else Palette.Ink,
                )
                if (step.condition != null) {
                    Spacer(Modifier.width(8.dp))
                    ConditionPill(step.condition, step.condition)
                }
            }
            Text(
                when {
                    state == StepState.Done ->
                        "Completed" + (prettyDate(step.done?.started_at)?.let { " $it" } ?: "") + "."
                    step.kind == "training" -> "Pepper gives feedback after each word."
                    else -> "Pepper records and says nothing about the answer."
                },
                style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
            )
            when (state) {
                StepState.Next -> {
                    Spacer(Modifier.height(10.dp))
                    ChunkyButton(
                        "Start ${step.label.substringBefore(':')}",
                        onClick = { onStart(step.step, null) },
                        modifier = Modifier.width(260.dp), icon = Icons.Filled.PlayArrow, height = 50.dp,
                    )
                }
                StepState.Resume -> {
                    Spacer(Modifier.height(10.dp))
                    ChunkyButton(
                        "Resume where it stopped",
                        onClick = { onStart(step.step, step.done?.session_id) },
                        modifier = Modifier.width(260.dp), icon = Icons.Filled.PlayArrow, height = 50.dp,
                        color = Palette.Amber, edge = Color(0xFF8F6200),
                    )
                }
                StepState.NeedsGroup -> {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Palette.Coral,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Assign a group to unlock the training blocks.",
                            style = MaterialTheme.typography.bodyMedium, color = Palette.Coral)
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun BackgroundPanel(p: Participant, modifier: Modifier) {
    Panel(modifier) {
        Overline("Background")
        Spacer(Modifier.height(12.dp))
        val rows = listOf(
            "Gender" to p.gender,
            "First language" to p.first_language,
            "Home language" to p.home_language,
            "Other languages" to p.other_languages,
            "Grew up in" to p.region_of_upbringing,
            "English from age" to p.english_onset_age?.toString(),
            "Schooled in" to p.primary_medium_of_instruction?.let(Vocab::pretty),
            "Voice assistant use" to p.voice_assistant_use?.let(Vocab::pretty),
            "Hearing or speech" to if (p.hearing_or_speech_difficulty)
                (p.hearing_or_speech_note ?: "Difficulty reported") else null,
            "Notes" to p.notes,
        ).filter { !it.second.isNullOrBlank() }
        if (rows.isEmpty()) {
            Text("Nothing recorded yet. Use Edit to add it.", color = Palette.Muted)
        }
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(label, Modifier.width(150.dp), color = Palette.Muted,
                    style = MaterialTheme.typography.bodyMedium)
                Text(value.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                if (p.consent_participation) "Consented to take part" else "No consent to take part",
                background = if (p.consent_participation) Palette.GreenLight else Palette.AmberLight,
                content = if (p.consent_participation) Palette.GreenDark else Palette.Ink,
            )
            Pill(
                if (p.consent_audio_retention) "Audio kept" else "Audio not kept",
                background = if (p.consent_audio_retention) Palette.GreenLight else Palette.AmberLight,
                content = if (p.consent_audio_retention) Palette.GreenDark else Palette.Ink,
            )
        }
    }
}
