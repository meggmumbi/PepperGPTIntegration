package com.example.peppergptintegration.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.peppergptintegration.ui.theme.Palette

/** Tablet-sized layouts start here; below it everything stacks in one column. */
val WideBreakpoint = 720.dp

/**
 * A page body: centred, capped in width, and told whether it is on a wide
 * screen (Pepper's tablet, landscape) or a narrow one (a phone, portrait).
 */
@Composable
fun Adaptive(
    modifier: Modifier = Modifier,
    cap: Dp = 1120.dp,
    content: @Composable (wide: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= WideBreakpoint
        Box(Modifier.widthIn(max = cap).fillMaxSize()) { content(wide) }
    }
}

/**
 * The main button: a solid face on a darker edge that it presses down into.
 * Large on purpose. It has to be hit first time by a finger at arm's length.
 */
@Composable
fun ChunkyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = Palette.Sky,
    edge: Color = Palette.SkyDark,
    contentColor: Color = Color.White,
    icon: ImageVector? = null,
    height: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && !loading
    val lift by animateDpAsState(if (pressed || !active) 0.dp else 4.dp, label = "lift")
    val face = if (active) color else Palette.LineSoft
    val under = if (active) edge else Palette.Line
    val tint = if (active) contentColor else Palette.Muted
    Row(
        modifier
            .defaultMinSize(minWidth = 120.dp)
            .height(height + 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                val radius = CornerRadius(16.dp.toPx())
                val depth = 4.dp.toPx()
                val top = depth - lift.toPx()
                // The edge the face sits on, then the face, lowered as it is pressed.
                drawRoundRect(under, Offset(0f, depth), Size(size.width, size.height - depth), radius)
                drawRoundRect(face, Offset(0f, top), Size(size.width, size.height - depth), radius)
            }
            .clickable(
                interactionSource = interaction, indication = null,
                enabled = active, role = Role.Button, onClick = onClick,
            )
            .padding(start = 20.dp, end = 20.dp, top = 4.dp - lift, bottom = lift),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Palette.Muted, strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text, color = tint, style = MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
/** The secondary button: outlined, same footprint as [ChunkyButton]. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Palette.Sky,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
) {
    val shape = RoundedCornerShape(16.dp)
    val tint = if (enabled) color else Palette.Muted
    Row(
        modifier
            .defaultMinSize(minWidth = 96.dp)
            .height(height)
            .clip(shape)
            .background(Palette.Surface)
            .border(2.dp, if (enabled) Palette.Line else Palette.LineSoft, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = tint, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

/** A white panel with a hairline border; the console's card. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        modifier = modifier.clip(shape).then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = shape,
        color = Palette.Surface,
        border = BorderStroke(1.dp, Palette.LineSoft),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** The small capitalised label that heads a panel. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier,
        style = MaterialTheme.typography.labelSmall, color = Palette.Muted,
    )
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Palette.SkyWash,
    content: Color = Palette.SkyDeep,
    border: Color? = null,
) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        modifier = modifier
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1,
    )
}

/** A selectable chip for a small controlled vocabulary (CEFR level, and so on). */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        modifier = modifier
            .clip(shape)
            .background(if (selected) Palette.SkyLight else Palette.Surface)
            .border(2.dp, if (selected) Palette.Sky else Palette.Line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minWidth = 52.dp, minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        color = if (selected) Palette.SkyDeep else Palette.Ink,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
    )
}

/** The participant's code in a circle, standing in for a face or a name. */
@Composable
fun CodeBadge(code: String, size: Dp = 52.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Palette.SkyLight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            code.take(4), color = Palette.SkyDeep, fontWeight = FontWeight.Bold,
            style = if (size >= 64.dp) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Palette.Sky, strokeWidth = 4.dp)
        if (label != null) {
            Spacer(Modifier.height(16.dp))
            Text(label, color = Palette.Muted, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Palette.Amber,
            modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            message, style = MaterialTheme.typography.bodyLarge, color = Palette.Ink,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp),
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            QuietButton("Try again", onRetry, icon = Icons.Filled.Refresh)
        }
    }
}

/** An inline notice: something went wrong, or something the user should know. */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = Palette.RedLight,
    content: Color = Palette.Red,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = content, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            action()
        }
    }
}

/** A text field styled once, so every form in the app matches. */
@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboard: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    visual: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    leading: ImageVector? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        enabled = enabled,
        keyboardOptions = keyboard,
        visualTransformation = visual,
        shape = RoundedCornerShape(14.dp),
        leadingIcon = leading?.let { { Icon(it, contentDescription = null) } },
        supportingText = supporting?.let { { Text(it) } },
    )
}

/** A dropdown over a short fixed list. */
@Composable
fun Picker(
    label: String,
    value: String?,
    options: List<String>,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
    display: (String) -> String = { it },
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable { open = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
                Text(
                    value?.let(display) ?: "Not recorded",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value == null) Palette.Muted else Palette.Ink,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Palette.Muted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Not recorded") }, onClick = { onPick(null); open = false })
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onPick(option); open = false },
                )
            }
        }
    }
}
