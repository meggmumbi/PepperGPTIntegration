package com.example.peppergptintegration.ui.session

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.peppergptintegration.data.Group
import com.example.peppergptintegration.data.ScaleItem
import com.example.peppergptintegration.data.Summary
import com.example.peppergptintegration.ui.components.ChunkyButton
import com.example.peppergptintegration.ui.components.Field
import com.example.peppergptintegration.ui.components.Notice
import com.example.peppergptintegration.ui.components.Overline
import com.example.peppergptintegration.ui.components.Panel
import com.example.peppergptintegration.ui.components.Pill
import com.example.peppergptintegration.ui.components.QuietButton
import com.example.peppergptintegration.ui.theme.Palette
import kotlin.math.roundToInt

// --- questionnaire ----------------------------------------------------------

/**
 * The participant's ratings, one scale to a page.
 *
 * Shown before the summary, never after it. Nothing is required: declining an
 * item is a participant's right, and the server records how many were answered.
 */
@Composable
internal fun QuestionnairePage(vm: SessionViewModel, wide: Boolean) {
    val form = vm.questionnaire ?: return
    val scales = form.scales.orEmpty()
    var page by remember { mutableStateOf(0) }
    val scale = scales.getOrNull(page) ?: return
    val last = page == scales.lastIndex

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = if (wide) 48.dp else 16.dp)) {
        Spacer(Modifier.height(if (wide) 24.dp else 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline("A few questions  ·  ${page + 1} of ${scales.size}")
                Spacer(Modifier.height(4.dp))
                Text(scale.title.orEmpty(), style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("There are no right answers. Leave out anything you would rather not answer.",
            color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState(), enabled = true),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            scale.items.orEmpty().forEach { item ->
                if (item.kind == "single_choice") {
                    // The one open question. Its answer travels as the comment.
                    Panel(Modifier.fillMaxWidth()) {
                        Text(item.text.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Field(vm.comment, { vm.comment = it.take(1000) }, "Your answer (optional)",
                            singleLine = false, minLines = 4)
                    }
                } else {
                    RatingRow(item, form.scale_min, form.scale_max, vm.answers[item.key], wide) { value ->
                        // Tapping the chosen point again clears it.
                        if (vm.answers[item.key] == value) vm.answers.remove(item.key)
                        else vm.answers[item.key] = value
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) QuietButton("Back", { page-- }, Modifier.width(120.dp), height = 60.dp)
            ChunkyButton(
                if (last) "Finish" else "Next",
                onClick = { if (last) vm.submitQuestionnaire() else page++ },
                modifier = Modifier.weight(1f), loading = vm.busy,
                color = if (last) Palette.Coral else Palette.Sky,
                edge = if (last) Palette.CoralDark else Palette.SkyDark,
            )
        }
    }
}

/** One item on a seven-point scale, with both ends named. */
@Composable
private fun RatingRow(
    item: ScaleItem, min: Int, max: Int, chosen: Int?, wide: Boolean, onPick: (Int) -> Unit,
) {
    Panel(Modifier.fillMaxWidth(), padding = if (wide) 20.dp else 14.dp) {
        Text(item.text.orEmpty(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        // Capped, so seven points stay finger-sized on the tablet instead of
        // stretching into seven large discs.
        Row(
            Modifier.widthIn(max = 520.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 6.dp),
        ) {
            (min..max).forEach { value ->
                val on = chosen == value
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                        .background(if (on) Palette.Sky else Palette.Surface)
                        .border(2.dp, if (on) Palette.SkyDark else Palette.Line, CircleShape)
                        .clickable(role = Role.RadioButton) { onPick(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$value", color = if (on) Color.White else Palette.Ink,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Text(item.low_label.orEmpty(), Modifier.weight(1f), color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall)
            Text(item.high_label.orEmpty(), Modifier.weight(1f), color = Palette.Muted,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        }
    }
}

// --- summary ----------------------------------------------------------------

/**
 * What the session came to. A training block shows how it went; an assessment
 * shows only that it is done, because a score here would teach as surely as
 * the robot saying it aloud.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummaryPage(vm: SessionViewModel, wide: Boolean) {
    val summary = vm.summary
    Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 48.dp else 16.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (wide) 32.dp else 20.dp))
            if (summary == null || !summary.scored) {
                Spacer(Modifier.height(40.dp))
                Text("All done", style = MaterialTheme.typography.displaySmall, color = Palette.Sky)
                Spacer(Modifier.height(12.dp))
                Text("Thank you. That part is finished.", style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                if (summary != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("${summary.items_attempted} of ${summary.items_total}", "items")
                        summary.duration_minutes?.let { Stat(minutes(it), "time") }
                    }
                    summary.note?.let {
                        Spacer(Modifier.height(20.dp))
                        Text(it, color = Palette.Muted, style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp))
                    }
                }
            } else {
                Text("Well done!", style = MaterialTheme.typography.displaySmall, color = Palette.Sky)
                Text("Here is how that went.", style = MaterialTheme.typography.titleMedium,
                    color = Palette.Muted)
                Spacer(Modifier.height(24.dp))
                val head: @Composable () -> Unit = {
                    Ring(summary.accuracy_percentage)
                }
                val stats: @Composable () -> Unit = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Stat("${summary.correct_answers ?: 0}", "correct")
                        Stat("${summary.items_attempted} of ${summary.items_total}", "items")
                        summary.duration_minutes?.let { Stat(minutes(it), "time") }
                        if (summary.skipped > 0) Stat("${summary.skipped}", "skipped")
                    }
                }
                if (wide) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        head()
                        stats()
                    }
                } else {
                    head()
                    Spacer(Modifier.height(20.dp))
                    stats()
                }
                Spacer(Modifier.height(24.dp))
                val features = summary.by_feature.orEmpty()
                val kinds = summary.by_exercise.orEmpty()
                if (wide && features.isNotEmpty() && kinds.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Breakdown("By sound", features, Modifier.weight(1f))
                        Breakdown("By activity", kinds, Modifier.weight(1f))
                    }
                } else {
                    if (features.isNotEmpty()) Breakdown("By sound", features, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    if (kinds.isNotEmpty()) Breakdown("By activity", kinds, Modifier.fillMaxWidth())
                }
                val practise = summary.areas_for_improvement.orEmpty()
                if (practise.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Panel(Modifier.fillMaxWidth()) {
                        Overline("Worth another go")
                        Spacer(Modifier.height(12.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            practise.forEach {
                                Pill(it, background = Palette.CoralLight, content = Palette.CoralDark)
                            }
                        }
                    }
                }
                summary.recommendations.orEmpty().forEach {
                    Spacer(Modifier.height(10.dp))
                    Notice(it, background = Palette.SkyWash, content = Palette.SkyDeep)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        ChunkyButton(
            "Hand back to the researcher", vm::toNote,
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            color = Palette.Coral, edge = Palette.CoralDark,
        )
    }
}

private fun minutes(value: Double): String =
    if (value < 1) "${(value * 60).roundToInt()} sec" else "${(value * 10).roundToInt() / 10.0} min"

@Composable
private fun Stat(value: String, label: String) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.clip(shape).background(Palette.Surface).border(1.dp, Palette.LineSoft, shape)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
    }
}

@Composable
private fun Ring(percent: Double?) {
    val fraction = ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val box = Size(size.width - stroke, size.height - stroke)
            drawArc(Palette.Line, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            drawArc(Palette.Green, -90f, 360f * fraction, false, Offset(inset, inset), box,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (percent == null) "–" else "${percent.roundToInt()}%",
                style = MaterialTheme.typography.displaySmall, color = Palette.Ink)
            Text("CORRECT", style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
        }
    }
}

@Composable
private fun Breakdown(title: String, groups: List<Group>, modifier: Modifier) {
    Panel(modifier) {
        Overline(title)
        Spacer(Modifier.height(12.dp))
        groups.forEach { group ->
            val fraction = if (group.attempted > 0) group.correct.toFloat() / group.attempted else 0f
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(group.label ?: group.key.orEmpty(), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(Palette.Line)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(50)).background(Palette.Green))
                }
                Text("${group.correct}/${group.attempted}", Modifier.width(56.dp),
                    textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted)
            }
        }
    }
}

// --- the researcher's note --------------------------------------------------

/**
 * What happened in the room, from the researcher. Tick boxes rather than a
 * star rating: what the analysis needs is the reasons a session might have to
 * be set aside, recorded quickly enough that they get recorded at all.
 */
@Composable
internal fun NotePage(vm: SessionViewModel, wide: Boolean) {
    val flags = remember {
        listOf(
            "interrupted" to "The session was interrupted",
            "noisy_environment" to "The room was noisy",
            "equipment_problem" to "There was an equipment problem",
            "participant_unwell" to "The participant was unwell or very tired",
            "language_help_given" to "I helped with the language during the session",
            "exclude_recommended" to "I recommend leaving this session out of the analysis",
        )
    }
    val ticked = remember { mutableStateOf(setOf<String>()) }
    var note by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().imePadding().padding(horizontal = if (wide) 48.dp else 16.dp)) {
        Spacer(Modifier.height(if (wide) 24.dp else 12.dp))
        Overline("For the researcher")
        Spacer(Modifier.height(4.dp))
        Text("How did the session go?", style = MaterialTheme.typography.headlineMedium)
        Text("Tick anything that applied. Leave it all clear if the session ran normally.",
            color = Palette.Muted, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Panel(Modifier.fillMaxWidth(), padding = 8.dp) {
                flags.forEach { (key, label) ->
                    val on = key in ticked.value
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Checkbox) {
                                ticked.value = if (on) ticked.value - key else ticked.value + key
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge,
                            color = if (key == "exclude_recommended" && on) Palette.Red else Palette.Ink)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Field(note, { note = it.take(1000) }, "Anything else worth noting (optional)",
                singleLine = false, minLines = 3)
            Spacer(Modifier.height(12.dp))
        }

        vm.noteError?.let {
            Notice(it)
            Spacer(Modifier.height(8.dp))
        }
        ChunkyButton(
            "Save and finish",
            onClick = {
                vm.saveNote(flags.associate { (key, _) -> key to (key in ticked.value) } +
                    ("note" to note.trim().ifBlank { null }))
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), loading = vm.busy,
            color = Palette.Coral, edge = Palette.CoralDark,
        )
    }
}
