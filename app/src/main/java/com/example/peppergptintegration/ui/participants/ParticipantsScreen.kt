package com.example.peppergptintegration.ui.participants

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.peppergptintegration.data.Api
import com.example.peppergptintegration.data.ApiException
import com.example.peppergptintegration.data.Auth
import com.example.peppergptintegration.data.Participant
import com.example.peppergptintegration.ui.components.Adaptive
import com.example.peppergptintegration.ui.components.ChunkyButton
import com.example.peppergptintegration.ui.components.CodeBadge
import com.example.peppergptintegration.ui.components.ErrorBox
import com.example.peppergptintegration.ui.components.Field
import com.example.peppergptintegration.ui.components.LoadingBox
import com.example.peppergptintegration.ui.components.Panel
import com.example.peppergptintegration.ui.components.Pill
import com.example.peppergptintegration.ui.components.QuietButton
import com.example.peppergptintegration.ui.theme.Palette
import kotlinx.coroutines.launch

/** One line describing a participant without identifying them. */
internal fun Participant.summaryLine(): String = listOfNotNull(
    age?.let { "$it yrs" },
    first_language?.takeIf { it.isNotBlank() },
    self_rated_english?.let { "English $it" },
).joinToString("  ·  ").ifBlank { "No background recorded yet" }

@Composable
fun ParticipantsScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var participants by remember { mutableStateOf<List<Participant>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }

    fun load() {
        error = null
        scope.launch {
            try {
                participants = Api.participants()
            } catch (e: ApiException) {
                error = if (e.code == 403) {
                    "This account can rate recordings but cannot run sessions. " +
                        "Sign in with a researcher account."
                } else e.message
            }
        }
    }

    // Reloaded whenever the screen comes back, so a participant just enrolled
    // or edited is there without pulling to refresh.
    LifecycleResumeEffect(Unit) {
        load()
        onPauseOrDispose { }
    }

    Adaptive(Modifier.background(Palette.Background).systemBarsPadding()) { wide ->
        Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 32.dp else 16.dp)) {
            Spacer(Modifier.height(if (wide) 24.dp else 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Participants", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Signed in as ${Auth.username ?: "researcher"}",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.Muted,
                    )
                }
                QuietButton(
                    if (wide) "Sign out" else "Out",
                    onClick = { scope.launch { Api.logout(); onSignedOut() } },
                    icon = Icons.AutoMirrored.Filled.ExitToApp, color = Palette.Muted,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(
                    search, { search = it }, "Search by code or language",
                    modifier = Modifier.weight(1f), leading = Icons.Filled.Search,
                    keyboard = KeyboardOptions(imeAction = ImeAction.Search),
                )
                if (wide) {
                    Spacer(Modifier.width(16.dp))
                    ChunkyButton(
                        "New participant", onCreate, Modifier.width(240.dp),
                        icon = Icons.Filled.Add, color = Palette.Coral, edge = Palette.CoralDark,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            val all = participants
            when {
                error != null -> ErrorBox(error!!, ::load, Modifier.weight(1f))
                all == null -> LoadingBox(Modifier.weight(1f))
                all.isEmpty() -> Empty(Modifier.weight(1f), onCreate)
                else -> {
                    val query = search.trim().lowercase()
                    val shown = all.filter {
                        query.isEmpty() ||
                            it.label.lowercase().contains(query) ||
                            (it.first_language ?: "").lowercase().contains(query)
                    }.sortedBy { it.label }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(shown, key = { it.id }) { person ->
                            ParticipantCard(person) { onOpen(person.id) }
                        }
                        if (shown.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "Nobody matches \"$search\".", color = Palette.Muted,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (!wide) {
                ChunkyButton(
                    "New participant", onCreate, Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    icon = Icons.Filled.Add, color = Palette.Coral, edge = Palette.CoralDark,
                )
            }
        }
    }
}

@Composable
private fun ParticipantCard(person: Participant, onClick: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodeBadge(person.label)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(person.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    person.summaryLine(), style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = Palette.Muted, modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (person.consent_participation) {
                Pill("Consented", background = Palette.GreenLight, content = Palette.GreenDark)
            } else {
                Pill("No consent recorded", background = Palette.AmberLight, content = Palette.Ink)
            }
            if (person.participant_code.isNullOrBlank()) {
                Pill("Needs a code", background = Palette.RedLight, content = Palette.Red)
            }
        }
    }
}

@Composable
private fun Empty(modifier: Modifier, onCreate: () -> Unit) {
    Column(
        modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No participants yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enrol the first one to assign them to a group and start their pre-test.",
            color = Palette.Muted, style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        ChunkyButton(
            "Enrol a participant", onCreate, Modifier.width(280.dp), icon = Icons.Filled.Add,
            color = Palette.Coral, edge = Palette.CoralDark,
        )
    }
}
