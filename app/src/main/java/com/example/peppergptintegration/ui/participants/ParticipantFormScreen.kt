package com.example.peppergptintegration.ui.participants

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.peppergptintegration.data.Api
import com.example.peppergptintegration.data.ApiException
import com.example.peppergptintegration.data.Vocab
import com.example.peppergptintegration.ui.components.Adaptive
import com.example.peppergptintegration.ui.components.ChoiceChip
import com.example.peppergptintegration.ui.components.ChunkyButton
import com.example.peppergptintegration.ui.components.ErrorBox
import com.example.peppergptintegration.ui.components.Field
import com.example.peppergptintegration.ui.components.LoadingBox
import com.example.peppergptintegration.ui.components.Notice
import com.example.peppergptintegration.ui.components.Overline
import com.example.peppergptintegration.ui.components.Panel
import com.example.peppergptintegration.ui.components.Picker
import com.example.peppergptintegration.ui.components.QuietButton
import com.example.peppergptintegration.ui.theme.Palette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The enrolment form's working copy. Text throughout; parsed on save. */
private class Draft {
    var code by mutableStateOf("")
    var age by mutableStateOf("")
    var gender by mutableStateOf<String?>(null)
    var firstLanguage by mutableStateOf("")
    var otherLanguages by mutableStateOf("")
    var homeLanguage by mutableStateOf("")
    var region by mutableStateOf("")
    var onsetAge by mutableStateOf("")
    var medium by mutableStateOf<String?>(null)
    var cefr by mutableStateOf<String?>(null)
    var voiceUse by mutableStateOf<String?>(null)
    var difficulty by mutableStateOf(false)
    var difficultyNote by mutableStateOf("")
    var consentParticipation by mutableStateOf(false)
    var consentAudio by mutableStateOf(false)
    var consentDate by mutableStateOf("")
    var notes by mutableStateOf("")

    fun toBody(): Map<String, Any?> = mapOf(
        "participant_code" to code.trim(),
        "age" to age.trim().toIntOrNull(),
        "gender" to gender,
        "first_language" to firstLanguage.trim().ifBlank { null },
        "other_languages" to otherLanguages.trim().ifBlank { null },
        "home_language" to homeLanguage.trim().ifBlank { null },
        "region_of_upbringing" to region.trim().ifBlank { null },
        "english_onset_age" to onsetAge.trim().toIntOrNull(),
        "primary_medium_of_instruction" to medium,
        "self_rated_english" to cefr,
        "voice_assistant_use" to voiceUse,
        "hearing_or_speech_difficulty" to difficulty,
        "hearing_or_speech_note" to difficultyNote.trim().ifBlank { null },
        "consent_participation" to consentParticipation,
        "consent_audio_retention" to consentAudio,
        "consent_date" to consentDate.trim().ifBlank { null },
        "notes" to notes.trim().ifBlank { null },
        "areas_of_interest_ids" to emptyList<String>(),
    )
}

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/** Enrol a participant ([participantId] null) or edit one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParticipantFormScreen(
    participantId: String?,
    onSaved: (String) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val draft = remember { Draft() }
    var loading by remember { mutableStateOf(participantId != null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(participantId) {
        if (participantId == null) return@LaunchedEffect
        try {
            val p = Api.participant(participantId)
            draft.code = p.participant_code.orEmpty()
            draft.age = p.age?.toString().orEmpty()
            draft.gender = p.gender
            draft.firstLanguage = p.first_language.orEmpty()
            draft.otherLanguages = p.other_languages.orEmpty()
            draft.homeLanguage = p.home_language.orEmpty()
            draft.region = p.region_of_upbringing.orEmpty()
            draft.onsetAge = p.english_onset_age?.toString().orEmpty()
            draft.medium = p.primary_medium_of_instruction
            draft.cefr = p.self_rated_english
            draft.voiceUse = p.voice_assistant_use
            draft.difficulty = p.hearing_or_speech_difficulty
            draft.difficultyNote = p.hearing_or_speech_note.orEmpty()
            draft.consentParticipation = p.consent_participation
            draft.consentAudio = p.consent_audio_retention
            draft.consentDate = p.consent_date.orEmpty()
            draft.notes = p.notes.orEmpty()
        } catch (e: ApiException) {
            loadError = e.message
        }
        loading = false
    }

    fun save() {
        error = when {
            draft.code.isBlank() -> "A participant code is required, for example P21."
            draft.age.isNotBlank() && draft.age.trim().toIntOrNull() == null -> "Age must be a number."
            draft.onsetAge.isNotBlank() && draft.onsetAge.trim().toIntOrNull() == null ->
                "Age of first English must be a number."
            else -> null
        }
        if (error != null) return
        saving = true
        scope.launch {
            try {
                val saved = if (participantId == null) Api.createParticipant(draft.toBody())
                else Api.updateParticipant(participantId, draft.toBody())
                onSaved(saved.id)
            } catch (e: ApiException) {
                error = e.message
            } finally {
                saving = false
            }
        }
    }

    Adaptive(Modifier.background(Palette.Background).systemBarsPadding().imePadding(), cap = 980.dp) { wide ->
        Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 32.dp else 16.dp)) {
            Spacer(Modifier.height(if (wide) 20.dp else 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuietButton("Back", onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, color = Palette.Muted)
                Spacer(Modifier.width(16.dp))
                Text(
                    if (participantId == null) "Enrol a participant" else "Edit participant",
                    style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))

            when {
                loading -> LoadingBox(Modifier.weight(1f))
                loadError != null -> ErrorBox(loadError!!, null, Modifier.weight(1f))
                else -> Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Section("Identity") {
                        Notice(
                            "Use a study code only. The link between a code and a name stays on " +
                                "paper and is never entered here.",
                            background = Palette.SkyWash, content = Palette.SkyDeep,
                        )
                        Pair2(wide, {
                            Field(draft.code, { draft.code = it.uppercase() }, "Participant code *",
                                supporting = "For example P21",
                                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                        }, {
                            Field(draft.age, { draft.age = it.filter(Char::isDigit).take(3) }, "Age",
                                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                        })
                        Picker("Gender", draft.gender, Vocab.genders, { draft.gender = it })
                    }

                    Section("Language background") {
                        Field(draft.firstLanguage, { draft.firstLanguage = it }, "First language",
                            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Vocab.firstLanguages.forEach { language ->
                                ChoiceChip(language, draft.firstLanguage.equals(language, true),
                                    onClick = { draft.firstLanguage = language })
                            }
                        }
                        Pair2(wide, {
                            Field(draft.homeLanguage, { draft.homeLanguage = it }, "Language spoken at home")
                        }, {
                            Field(draft.otherLanguages, { draft.otherLanguages = it }, "Other languages",
                                supporting = "Comma-separated, most fluent first")
                        })
                        Pair2(wide, {
                            Field(draft.region, { draft.region = it }, "Region of upbringing")
                        }, {
                            Field(draft.onsetAge, { draft.onsetAge = it.filter(Char::isDigit).take(2) },
                                "Age when English began",
                                keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                        })
                        Picker("Main language of schooling", draft.medium, Vocab.mediums,
                            { draft.medium = it }, display = Vocab::pretty)
                        Label("Self-rated English (CEFR)")
                        ChipRow(Vocab.cefr, draft.cefr) { draft.cefr = it }
                    }

                    Section("Controls") {
                        Label("How often they use a voice assistant")
                        ChipRow(Vocab.voiceUse, draft.voiceUse, Vocab::pretty) { draft.voiceUse = it }
                        Toggle("Has a hearing or speech difficulty", draft.difficulty) { draft.difficulty = it }
                        if (draft.difficulty) {
                            Field(draft.difficultyNote, { draft.difficultyNote = it },
                                "What should the researcher know?", singleLine = false, minLines = 2)
                        }
                    }

                    Section("Consent") {
                        Toggle("Consented to take part", draft.consentParticipation) {
                            draft.consentParticipation = it
                            if (it && draft.consentDate.isBlank()) draft.consentDate = today()
                        }
                        Toggle("Consented to their recordings being kept", draft.consentAudio) {
                            draft.consentAudio = it
                        }
                        Field(draft.consentDate, { draft.consentDate = it }, "Consent date",
                            supporting = "Year-month-day, for example ${today()}")
                    }

                    Section("Notes") {
                        Field(draft.notes, { draft.notes = it }, "Anything else worth recording",
                            singleLine = false, minLines = 3)
                    }

                    if (participantId != null) {
                        QuietButton("Delete this participant", { confirmDelete = true },
                            icon = Icons.Filled.Delete, color = Palette.Red)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (!loading && loadError == null) {
                error?.let {
                    Notice(it)
                    Spacer(Modifier.height(10.dp))
                }
                ChunkyButton(
                    if (participantId == null) "Enrol participant" else "Save changes",
                    ::save, Modifier.fillMaxWidth().padding(bottom = 12.dp), loading = saving,
                    color = Palette.Coral, edge = Palette.CoralDark,
                )
            }
        }
    }

    if (confirmDelete && participantId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${draft.code}?") },
            text = {
                Text("This removes the participant record. It cannot be undone, and the " +
                    "server refuses it once sessions have been recorded for them.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            Api.deleteParticipant(participantId)
                            onDeleted()
                        } catch (e: ApiException) {
                            error = e.message
                        }
                    }
                }) { Text("Delete", color = Palette.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Overline(title)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
    }
}

/** Two fields side by side on a tablet, stacked on a phone. */
@Composable
private fun Pair2(wide: Boolean, first: @Composable () -> Unit, second: @Composable () -> Unit) {
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) { first() }
            Column(Modifier.weight(1f)) { second() }
        }
    } else {
        first()
        second()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = Palette.Ink)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<String>,
    selected: String?,
    display: (String) -> String = { it },
    onPick: (String?) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            // Tapping the selected chip clears it: "not recorded" is a real answer.
            ChoiceChip(display(option), selected == option,
                onClick = { onPick(if (selected == option) null else option) })
        }
    }
}

@Composable
private fun Toggle(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
