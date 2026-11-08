package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

private val KEYWORD_REGEX = Regex("\\b(subtask|task|list|project)s?\\b", RegexOption.IGNORE_CASE)

/**
 * Makes the thing a notification is about stand out: the words Task / List / Project (and Subtask)
 * are capitalised, bold and underlined.
 *
 * Only the part before the first quote is looked at: messages put the user's own titles in
 * single quotes ("Anna deleted task: 'Buy list'"), and those must stay exactly as typed.
 */
fun highlightNotificationKeywords(message: String): AnnotatedString {
    val firstQuote = message.indexOf('\'').let { if (it < 0) message.length else it }
    val keywordStyle = SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
    return buildAnnotatedString {
        var last = 0
        for (match in KEYWORD_REGEX.findAll(message.substring(0, firstQuote))) {
            append(message.substring(last, match.range.first))
            withStyle(keywordStyle) {
                append(match.value.replaceFirstChar { it.uppercase() })
            }
            last = match.range.last + 1
        }
        append(message.substring(last))
    }
}
