package com.glycemicgpt.mobile.presentation.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import dev.jeziellago.compose.markdowntext.MarkdownText

@Composable
fun AppMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    if (markdown.isBlank()) return

    val uriHandler = LocalUriHandler.current
    val textColor = MaterialTheme.colorScheme.onSurface

    MarkdownText(
        markdown = markdown,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        onLinkClicked = { url ->
            if (url.startsWith("https://") || url.startsWith("http://")) {
                uriHandler.openUri(url)
            }
        },
    )
}
