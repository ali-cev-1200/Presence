package com.ali_1200.presence

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// یک فیلد متنی در پنجره
data class InputField(
    val hint: String,
    val latinCode: Boolean = false, // برای کد کلاس: حروف بزرگ، اعداد لاتین، چپ‌به‌راست
    val helper: String? = null
)

// پنجره ساده با چند فیلد متنی؛ onOk مقدار فیلدها را به همان ترتیب می‌گیرد
fun showInputDialog(
    context: Context,
    title: String,
    fields: List<InputField>,
    positiveText: String,
    onOk: (List<String>) -> Unit
) {
    val density = context.resources.displayMetrics.density
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val padding = (24 * density).toInt()
        setPadding(padding, (8 * density).toInt(), padding, 0)
    }

    val edits = fields.map { field ->
        val layout = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = field.hint
            helperText = field.helper
        }
        val edit = TextInputEditText(layout.context).apply {
            maxLines = 1
            if (field.latinCode) {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                textDirection = View.TEXT_DIRECTION_LTR
                filters = arrayOf(android.text.InputFilter.AllCaps())
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            } else {
                inputType = InputType.TYPE_CLASS_TEXT
            }
        }
        layout.addView(edit)
        container.addView(
            layout,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (8 * density).toInt() }
        )
        edit
    }

    MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(container)
        .setPositiveButton(positiveText) { _, _ -> onOk(edits.map { it.text.toString().trim() }) }
        .setNegativeButton("انصراف", null)
        .show()
}

fun confirmDialog(
    context: Context,
    title: String,
    message: String,
    positiveText: String,
    onOk: () -> Unit
) {
    MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveText) { _, _ -> onOk() }
        .setNegativeButton("انصراف", null)
        .show()
}
