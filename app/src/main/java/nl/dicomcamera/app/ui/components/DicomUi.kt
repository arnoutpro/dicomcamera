package nl.dicomcamera.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.dicomcamera.app.R
import nl.dicomcamera.app.ui.theme.BrandGradient
import nl.dicomcamera.app.ui.theme.DicomColors
import nl.dicomcamera.app.ui.theme.DicomShapes
import nl.dicomcamera.app.ui.theme.DicomType

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Black-square A-mark: keep as a rounded badge on light chrome.
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = "DICOM Camera",
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        if (showWordmark) {
            Text(
                text = "DICOM Camera",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = DicomColors.Ink,
                    letterSpacing = (-0.3).sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    size: Int = 18,
) {
    Text(
        text = "DICOM Camera",
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium.copy(
            fontSize = size.sp,
            fontWeight = FontWeight.Black,
            brush = BrandGradient,
            letterSpacing = (-0.3).sp,
        ),
    )
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DicomColors.Slate500,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(color = color),
    )
}

@Composable
fun SoftPanel(
    modifier: Modifier = Modifier,
    background: Color = DicomColors.Panel,
    border: Color = DicomColors.Hairline,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    shape: RoundedCornerShape = DicomShapes.Panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * Two-option mode switch (e.g. Worklist / Manual). Not a pill cluster — full-width segments.
 */
@Composable
fun SegmentedChoice(
    leftLabel: String,
    rightLabel: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(DicomShapes.Control)
            .border(1.dp, DicomColors.Hairline, DicomShapes.Control),
    ) {
        SegmentCell(
            label = leftLabel,
            selected = leftSelected,
            onClick = onLeft,
            modifier = Modifier.weight(1f),
        )
        SegmentCell(
            label = rightLabel,
            selected = !leftSelected,
            onClick = onRight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SegmentCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) DicomColors.Forest else DicomColors.White
    val fg = if (selected) DicomColors.White else DicomColors.Ink
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = fg,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = DicomColors.Slate800.copy(alpha = 0.06f),
    foreground: Color = DicomColors.Slate700,
    mono: Boolean = false,
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = foreground,
        fontFamily = if (mono) DicomType.Mono else DicomType.Sans,
        fontWeight = FontWeight.Bold,
        fontSize = if (mono) 11.sp else 10.sp,
        modifier = modifier
            .clip(DicomShapes.Chip)
            .background(background)
            .border(1.dp, foreground.copy(alpha = 0.18f), DicomShapes.Chip)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
fun ForestButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = DicomColors.Forest,
    compact: Boolean = false,
) {
    val content = DicomColors.onColor(containerColor)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "forest-btn-scale",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .scale(scale)
            .then(if (compact) Modifier.height(40.dp) else Modifier),
        shape = DicomShapes.Chip,
        contentPadding = if (compact) {
            PaddingValues(horizontal = 16.dp, vertical = 0.dp)
        } else {
            PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = content,
            disabledContainerColor = containerColor.copy(alpha = 0.35f),
            disabledContentColor = content.copy(alpha = 0.7f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = content,
                letterSpacing = 0.4.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
fun QuietOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "quiet-btn-scale",
    )
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.scale(scale),
        shape = DicomShapes.Chip,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DicomColors.Forest,
            disabledContentColor = DicomColors.Forest.copy(alpha = 0.4f),
        ),
        border = BorderStroke(1.dp, DicomColors.Forest.copy(alpha = 0.35f)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                color = DicomColors.Forest,
                letterSpacing = 0.3.sp,
            ),
        )
    }
}

@Composable
fun QuietTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = DicomColors.ForestMid,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        shape = DicomShapes.Chip,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(color = color, letterSpacing = 0.2.sp),
        )
    }
}

@Composable
fun ActionPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = DicomColors.Forest,
    filled: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "action-pill-scale",
    )
    val bg = if (filled) tint.copy(alpha = 0.14f) else DicomColors.White
    val border = tint.copy(alpha = if (filled) 0.28f else 0.22f)
    Row(
        modifier = modifier
            .scale(scale)
            .clip(DicomShapes.Chip)
            .background(bg)
            .border(1.dp, border, DicomShapes.Chip)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = tint,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.2.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
fun StatusBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Info,
) {
    val (bg, fg, border) = when (tone) {
        StatusTone.Info -> Triple(DicomColors.TealSoft, DicomColors.Forest, DicomColors.Teal.copy(alpha = 0.35f))
        StatusTone.Warn -> Triple(DicomColors.GoldSoft, DicomColors.GoldInk, DicomColors.Gold.copy(alpha = 0.4f))
        StatusTone.Error -> Triple(DicomColors.RoseSoft, DicomColors.Rose, DicomColors.Rose.copy(alpha = 0.35f))
        StatusTone.Success -> Triple(DicomColors.TealSoft, DicomColors.ForestMid, DicomColors.ForestMid.copy(alpha = 0.35f))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            color = fg,
            fontWeight = FontWeight.SemiBold,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(DicomShapes.Control)
            .background(bg)
            .border(1.dp, border, DicomShapes.Control)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

enum class StatusTone { Info, Warn, Error, Success }

@Composable
fun ResultRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "result-row-scale",
    )
    Row(
        modifier = modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(DicomShapes.Panel)
            .background(DicomColors.Panel)
            .border(1.dp, DicomColors.Hairline, DicomShapes.Panel)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun DicomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = DicomColors.Slate500,
                    letterSpacing = 0.4.sp,
                ),
            )
        },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        enabled = enabled,
        shape = DicomShapes.Control,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DicomColors.Teal,
            unfocusedBorderColor = DicomColors.Hairline,
            focusedContainerColor = DicomColors.White,
            unfocusedContainerColor = Color(0xFFF7FBFA),
            cursorColor = DicomColors.Forest,
            focusedLabelColor = DicomColors.ForestMid,
            unfocusedLabelColor = DicomColors.Slate500,
            focusedTextColor = DicomColors.Ink,
            unfocusedTextColor = DicomColors.Ink,
        ),
    )
}

@Composable
fun ChromeTopBar(
    /**
     * When true (main tabs), show brand logo only — bottom nav already names the screen.
     * When false (nested flows), show optional back + a single title.
     */
    branded: Boolean,
    title: String = "",
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DicomColors.Chrome)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (branded) {
            BrandLogo(modifier = Modifier.weight(1f))
            actions()
        } else {
            if (navigationIcon != null) {
                navigationIcon()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = DicomColors.Ink,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
    }
}

enum class MainTab {
    Worklist,
    Archive,
    Settings,
}

@Composable
fun ChromeBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DicomColors.Chrome)
            .border(BorderStroke(1.dp, DicomColors.Hairline))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTabItem(
            label = "Worklist",
            selected = selected == MainTab.Worklist,
            icon = Icons.AutoMirrored.Filled.ViewList,
            onClick = { onSelect(MainTab.Worklist) },
            modifier = Modifier.weight(1f),
        )
        BottomTabItem(
            label = "Archive",
            selected = selected == MainTab.Archive,
            icon = Icons.Default.FolderOpen,
            onClick = { onSelect(MainTab.Archive) },
            modifier = Modifier.weight(1f),
        )
        BottomTabItem(
            label = "Settings",
            selected = selected == MainTab.Settings,
            icon = Icons.Default.Settings,
            onClick = { onSelect(MainTab.Settings) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fg = if (selected) DicomColors.Forest else DicomColors.Slate500
    val bg = if (selected) DicomColors.White else Color.Transparent
    val border = if (selected) DicomColors.Forest.copy(alpha = 0.35f) else Color.Transparent
    Column(
        modifier = modifier
            .clip(DicomShapes.Chip)
            .background(bg)
            .border(1.dp, border, DicomShapes.Chip)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = fg,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}
