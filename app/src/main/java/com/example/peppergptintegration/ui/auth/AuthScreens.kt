package com.example.peppergptintegration.ui.auth

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.peppergptintegration.AppConfig
import com.example.peppergptintegration.R
import com.example.peppergptintegration.data.Api
import com.example.peppergptintegration.data.ApiException
import com.example.peppergptintegration.ui.components.Adaptive
import com.example.peppergptintegration.ui.components.ChunkyButton
import com.example.peppergptintegration.ui.components.Field
import com.example.peppergptintegration.ui.components.Notice
import com.example.peppergptintegration.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * The frame both sign-in pages share. On Pepper's tablet the brand sits beside
 * the form; on a phone it sits above it.
 */
@Composable
private fun AuthFrame(title: String, subtitle: String, form: @Composable () -> Unit) {
    Adaptive(Modifier.background(Palette.Background).systemBarsPadding().imePadding()) { wide ->
        if (wide) {
            Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Brand(Modifier.weight(1f).fillMaxHeight(), large = true)
                Spacer(Modifier.size(32.dp))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Heading(title, subtitle)
                    form()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            ) {
                Brand(Modifier.fillMaxWidth().height(180.dp), large = false)
                Spacer(Modifier.height(24.dp))
                Heading(title, subtitle)
                form()
            }
        }
    }
}

@Composable
private fun Brand(modifier: Modifier, large: Boolean) {
    Box(
        modifier.clip(RoundedCornerShape(28.dp)).background(
            Brush.linearGradient(listOf(Palette.Sky, Palette.SkyDeep))
        ),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(if (large) 36.dp else 20.dp)) {
            Box(
                Modifier.size(if (large) 84.dp else 52.dp).clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_c_mic), contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(if (large) 44.dp else 28.dp),
                )
            }
            Spacer(Modifier.height(if (large) 28.dp else 12.dp))
            Text(
                "Pepper Pronunciation", color = Color.White,
                style = if (large) MaterialTheme.typography.displaySmall
                else MaterialTheme.typography.headlineMedium,
            )
            if (large) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Listen, speak, and hear what to change.\nA pronunciation tutor for " +
                        "English learners, run by a researcher.",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge, color = Palette.Ink)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
    Spacer(Modifier.height(24.dp))
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit, onRegister: () -> Unit, notice: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf(AppConfig.baseUrl) }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (username.isBlank() || password.isBlank()) {
            error = "Enter your username and password."
            return
        }
        // Applied on every sign-in, so a laptop whose address changed can be
        // followed from this screen without rebuilding the app.
        if (server.isNotBlank()) AppConfig.setBaseUrl(context, server)
        server = AppConfig.baseUrl
        busy = true
        error = null
        scope.launch {
            try {
                Api.login(username.trim(), password)
                onSignedIn()
            } catch (e: ApiException) {
                error = if (e.code == 401) "That username and password do not match." else e.message
            } finally {
                busy = false
            }
        }
    }

    AuthFrame("Sign in", "Use your researcher account.") {
        Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (notice != null) {
                Notice(notice, background = Palette.AmberLight, content = Palette.Ink)
            }
            Field(
                username, { username = it }, "Username", leading = Icons.Filled.Person,
                keyboard = KeyboardOptions(imeAction = ImeAction.Next),
            )
            Field(
                password, { password = it }, "Password", leading = Icons.Filled.Lock,
                visual = PasswordVisualTransformation(),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            if (showServer) {
                Field(
                    server, { server = it }, "Server address", leading = Icons.Filled.Settings,
                    supporting = "The laptop running the backend, for example 192.168.100.20:8000",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            error?.let { Notice(it) }
            ChunkyButton("Sign in", ::submit, Modifier.fillMaxWidth(), loading = busy)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Link("Create an account", onRegister)
                Link(if (showServer) "Hide server" else "Server: ${shortServer(server)}") {
                    showServer = !showServer
                }
            }
        }
    }
}

private fun shortServer(url: String): String =
    url.removePrefix("http://").removePrefix("https://").trimEnd('/')

@Composable
private fun Link(text: String, onClick: () -> Unit) {
    Text(
        text, color = Palette.Sky, fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
    )
}

@Composable
fun RegisterScreen(onDone: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = when {
            username.isBlank() -> "Choose a username."
            !email.contains('@') -> "Enter a valid email address."
            password.length < 6 -> "Use a password of at least six characters."
            password != confirm -> "The two passwords do not match."
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            try {
                Api.register(username.trim(), email.trim(), password)
                // Sign straight in, so the new account lands on its first screen.
                Api.login(username.trim(), password)
                onDone()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    AuthFrame("Create an account", "For researchers running sessions.") {
        Column(Modifier.widthIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field(username, { username = it }, "Username", leading = Icons.Filled.Person,
                keyboard = KeyboardOptions(imeAction = ImeAction.Next))
            Field(email, { email = it }, "Email", leading = Icons.Filled.Email,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
            Field(password, { password = it }, "Password", leading = Icons.Filled.Lock,
                visual = PasswordVisualTransformation(),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next))
            Field(confirm, { confirm = it }, "Confirm password", leading = Icons.Filled.Lock,
                visual = PasswordVisualTransformation(),
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
            error?.let { Notice(it) }
            ChunkyButton("Create account", ::submit, Modifier.fillMaxWidth(), loading = busy)
            Link("I already have an account", onBack)
        }
    }
}
