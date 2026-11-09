package com.example.ui.components

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

private val KEYWORD_REGEX = Regex("\\b(tasks?|lists?|projects?|done|reopened)\\b", RegexOption.IGNORE_CASE)

/**
 * The words to make stand out in a notification: what it is about (Task / List / Project) and
 * what happened to a task (Done / Reopened).
 *
 * Only the part before the first quote is looked at: messages put the user's own titles in
 * single quotes ("Anna deleted task: 'Buy list'"), and those must stay exactly as typed.
 */
private fun keywordMatches(message: String): Sequence<MatchResult> {
    val firstQuote = message.indexOf('\'').let { if (it < 0) message.length else it }
    return KEYWORD_REGEX.findAll(message.substring(0, firstQuote))
}

private fun capitalised(word: String) = word.replaceFirstChar { it.uppercase() }

/** For the in-app banner and list: the keywords are capitalised, bold and underlined. */
fun highlightNotificationKeywords(message: String): AnnotatedString {
    val keywordStyle = SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
    return buildAnnotatedString {
        var last = 0
        for (match in keywordMatches(message)) {
            append(message.substring(last, match.range.first))
            withStyle(keywordStyle) {
                append(capitalised(match.value))
            }
            last = match.range.last + 1
        }
        append(message.substring(last))
    }
}

/** The same highlighting for a system (notification shade) notification. */
fun highlightNotificationKeywordsForTray(message: String): CharSequence {
    val matches = keywordMatches(message).toList()
    if (matches.isEmpty()) return message
    val text = StringBuilder(message)
    for (match in matches) {
        text.replace(match.range.first, match.range.last + 1, capitalised(match.value))
    }
    return SpannableString(text).apply {
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
