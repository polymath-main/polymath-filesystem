package com.polymath.fs.viewers

import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

object CodeSyntaxHighlighter {

    private val JS_KEYWORDS = Pattern.compile(
        "\\b(var|let|const|function|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|new|this|typeof|instanceof|void|delete|in|of|class|extends|super|import|export|default|await|async|yield)\\b"
    )

    private val BUILTIN_OBJECTS = Pattern.compile(
        "\\b(Polymath|PolymathOS|console|JSON|Math|Array|Object|String|Number|Boolean|Date|RegExp|Promise|Map|Set|Error)\\b"
    )

    private val STRING_LITERALS = Pattern.compile(
        "(\"[^\"]*\"|'[^']*'|`[^`]*`)"
    )

    private val NUMBERS = Pattern.compile(
        "\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?)\\b"
    )

    private val COMMENTS = Pattern.compile(
        "(//.*|/\\*[\\s\\S]*?\\*/)"
    )

    private val COLOR_KEYWORD = Color.parseColor("#38BDF8") // Sky blue
    private val COLOR_BUILTIN = Color.parseColor("#C084FC") // Purple
    private val COLOR_STRING = Color.parseColor("#FDE047")  // Warm yellow
    private val COLOR_NUMBER = Color.parseColor("#FB923C")  // Orange
    private val COLOR_COMMENT = Color.parseColor("#64748B") // Muted Slate

    fun highlight(editable: Editable, extension: String) {
        val ext = extension.lowercase()
        // Clear existing spans
        val spans = editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)
        for (span in spans) {
            editable.removeSpan(span)
        }

        if (ext !in listOf("js", "json", "ts", "kt", "java", "sh", "py", "html", "css")) {
            return
        }

        val text = editable.toString()

        // 1. Numbers
        val mNum = NUMBERS.matcher(text)
        while (mNum.find()) {
            editable.setSpan(
                ForegroundColorSpan(COLOR_NUMBER),
                mNum.start(),
                mNum.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 2. Keywords
        val mKey = JS_KEYWORDS.matcher(text)
        while (mKey.find()) {
            editable.setSpan(
                ForegroundColorSpan(COLOR_KEYWORD),
                mKey.start(),
                mKey.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 3. Builtin Objects
        val mBuiltin = BUILTIN_OBJECTS.matcher(text)
        while (mBuiltin.find()) {
            editable.setSpan(
                ForegroundColorSpan(COLOR_BUILTIN),
                mBuiltin.start(),
                mBuiltin.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 4. Strings (override keywords inside strings)
        val mStr = STRING_LITERALS.matcher(text)
        while (mStr.find()) {
            editable.setSpan(
                ForegroundColorSpan(COLOR_STRING),
                mStr.start(),
                mStr.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // 5. Comments (override everything inside comments)
        val mComment = COMMENTS.matcher(text)
        while (mComment.find()) {
            editable.setSpan(
                ForegroundColorSpan(COLOR_COMMENT),
                mComment.start(),
                mComment.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
