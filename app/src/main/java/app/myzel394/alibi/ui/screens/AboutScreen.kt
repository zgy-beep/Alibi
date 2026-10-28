package app.myzel394.alibi.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.myzel394.alibi.BuildConfig
import app.myzel394.alibi.R
import app.myzel394.alibi.ui.components.atoms.AlibiSectionHeader
import app.myzel394.alibi.ui.components.atoms.AlibiSectionPanel
import app.myzel394.alibi.ui.components.atoms.AlibiTopAppBar
import kotlinx.coroutines.launch

private const val ALIBI_REPOSITORY_URL = "https://github.com/zgy-beep/Alibi"

@Composable
fun AboutScreen(
    onBackNavigate: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copySuccessMsg = stringResource(R.string.about_version_copied)

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(
                        snackbarData = it,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
            )
        },
        topBar = {
            AlibiTopAppBar(
                title = stringResource(R.string.ui_about_title),
                onBack = onBackNavigate,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero Brand & Version
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(92.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(R.drawable.launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(76.dp),
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Text(
                        text = stringResource(R.string.about_app_slogan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    val versionLabel = "v%s (%s)".format(
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE.toString(),
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier
                            .clickable(role = Role.Button) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Alibi Version", versionLabel)
                                clipboard.setPrimaryClip(clip)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = copySuccessMsg,
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = versionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Privacy & Architecture Highlights
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AlibiSectionHeader(
                        title = stringResource(R.string.about_section_privacy_title),
                    )
                    AlibiSectionPanel {
                        AboutFeatureRow(
                            icon = Icons.Default.Security,
                            title = stringResource(R.string.about_privacy_offline_title),
                            description = stringResource(R.string.about_privacy_offline_desc),
                        )
                        HorizontalDivider()
                        AboutFeatureRow(
                            icon = Icons.Default.Autorenew,
                            title = stringResource(R.string.about_privacy_loop_title),
                            description = stringResource(R.string.about_privacy_loop_desc),
                        )
                        HorizontalDivider()
                        AboutFeatureRow(
                            icon = Icons.Default.CameraAlt,
                            title = stringResource(R.string.about_privacy_discrete_title),
                            description = stringResource(R.string.about_privacy_discrete_desc),
                        )
                    }
                }
            }

            // Open Source & Licensing
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AlibiSectionHeader(
                        title = stringResource(R.string.about_section_community_title),
                    )
                    AlibiSectionPanel {
                        AboutActionRow(
                            painter = painterResource(R.drawable.ic_github),
                            title = stringResource(R.string.about_repo_title),
                            description = stringResource(R.string.about_repo_desc),
                            onClick = {
                                uriHandler.openUri(ALIBI_REPOSITORY_URL)
                            },
                        )
                        HorizontalDivider()
                        AboutActionRow(
                            icon = Icons.Default.Code,
                            title = stringResource(R.string.about_license_title),
                            description = stringResource(R.string.about_license_desc),
                            onClick = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutFeatureRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(38.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
