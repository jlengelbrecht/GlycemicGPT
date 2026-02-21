package com.glycemicgpt.mobile.presentation.common

import android.content.ActivityNotFoundException
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import dev.jeziellago.compose.markdowntext.MarkdownText
import timber.log.Timber

@Composable
fun AppMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    if (markdown.isBlank()) return

    val uriHandler = LocalUriHandler.current
    val textColor = MaterialTheme.colorScheme.onSurface

    // Strip image markdown and HTML img tags to prevent outbound image fetches
    // from AI-generated content (mirrors web MarkdownContent image suppression)
    val sanitized = markdown
        .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
        .replace(Regex("""!\[[^\]]*]\[[^\]]*]"""), "")
        .replace(Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE), "")

    MarkdownText(
        markdown = sanitized,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        onLinkClicked = { url ->
            val lower = url.lowercase()
            if (lower.startsWith("https://") || lower.startsWith("http://")) {
                try {
                    uriHandler.openUri(url)
                } catch (e: ActivityNotFoundException) {
                    Timber.w(e, "No activity found to handle URL")
                } catch (e: SecurityException) {
                    Timber.w(e, "Security exception opening URL")
                }
            }
        },
    )
}
