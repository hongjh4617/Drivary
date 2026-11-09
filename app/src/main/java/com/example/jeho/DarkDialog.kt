package com.example.jeho

import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout

// 차량의 밝은 기본 대화상자 대신 쓰는 어두운 대화상자. AlertDialog.Builder와 같은 방식으로 씁니다.
class DarkDialog(private val activity: Activity) {

    private var title = ""
    private var message: String? = null
    private var content: View? = null
    private var items: Array<String>? = null
    private var onItem: ((DialogInterface, Int) -> Unit)? = null
    private var positiveText: String? = null
    private var onPositive: ((DialogInterface, Int) -> Unit)? = null
    private var negativeText: String? = null
    private var onNegative: ((DialogInterface, Int) -> Unit)? = null

    fun setTitle(text: String): DarkDialog {
        title = text
        return this
    }

    fun setMessage(text: String): DarkDialog {
        message = text
        return this
    }

    fun setView(view: View): DarkDialog {
        content = view
        return this
    }

    fun setItems(options: Array<String>, listener: (DialogInterface, Int) -> Unit): DarkDialog {
        items = options
        onItem = listener
        return this
    }

    fun setPositiveButton(text: String, listener: ((DialogInterface, Int) -> Unit)?): DarkDialog {
        positiveText = text
        onPositive = listener
        return this
    }

    fun setNegativeButton(text: String, listener: ((DialogInterface, Int) -> Unit)?): DarkDialog {
        negativeText = text
        onNegative = listener
        return this
    }

    fun show(): Dialog {
        val c = activity
        val dialog = Dialog(c)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val match = LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = c.dp(28).toFloat()
                setColor(Color.parseColor("#101C33"))
                setStroke(c.dp(1), AppTheme.stroke)
            }
            setPadding(c.dp(32), c.dp(28), c.dp(32), c.dp(24))
        }

        root.addView(c.label(title, 26f, AppTheme.ink, true))

        message?.let {
            root.addView(c.label(it, 18f, AppTheme.gray).apply { setPadding(0, c.dp(12), 0, 0) })
        }

        content?.let { v ->
            if (v is EditText) {
                v.setTextColor(AppTheme.ink)
                v.setHintTextColor(AppTheme.gray)
                v.textSize = 18f * TEXT_SCALE
                v.background = c.glass(16)
                v.setPadding(c.dp(20), c.dp(16), c.dp(20), c.dp(16))
            }
            val lp = if (v is ImageView && v.layoutParams != null) {
                LinearLayout.LayoutParams(v.layoutParams.width, v.layoutParams.height)
            } else {
                LinearLayout.LayoutParams(match, wrap)
            }
            lp.topMargin = c.dp(16)
            lp.gravity = Gravity.CENTER_HORIZONTAL
            root.addView(v, lp)
        }

        items?.let { list ->
            val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            list.forEachIndexed { i, text ->
                box.addView(
                    c.label(text, 22f, AppTheme.ink).apply {
                        setPadding(c.dp(8), c.dp(16), c.dp(8), c.dp(16))
                        setOnClickListener {
                            dialog.dismiss()
                            onItem?.invoke(dialog, i)
                        }
                    },
                    LinearLayout.LayoutParams(match, wrap)
                )
                if (i < list.size - 1) {
                    box.addView(
                        View(c).apply { setBackgroundColor(AppTheme.stroke) },
                        LinearLayout.LayoutParams(match, c.dp(1))
                    )
                }
            }
            root.addView(box, LinearLayout.LayoutParams(match, wrap).apply { topMargin = c.dp(8) })
        }

        val buttons = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        negativeText?.let {
            buttons.addView(
                c.pillButton(it, AppTheme.surface, AppTheme.ink, 18f) {
                    dialog.dismiss()
                    onNegative?.invoke(dialog, 0)
                }.apply { setPadding(c.dp(28), 0, c.dp(28), 0) },
                LinearLayout.LayoutParams(wrap, c.dp(52)).apply { rightMargin = c.dp(12) }
            )
        }
        positiveText?.let {
            val color = if (it.contains("삭제")) AppTheme.danger else AppTheme.accent
            buttons.addView(
                c.pillButton(it, color, Color.WHITE, 18f) {
                    dialog.dismiss()
                    onPositive?.invoke(dialog, 0)
                }.apply { setPadding(c.dp(28), 0, c.dp(28), 0) },
                LinearLayout.LayoutParams(wrap, c.dp(52))
            )
        }
        if (buttons.childCount > 0) {
            root.addView(buttons, LinearLayout.LayoutParams(match, wrap).apply { topMargin = c.dp(24) })
        }

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = if (content is ImageView) ViewGroup.LayoutParams.WRAP_CONTENT else c.dp(640)
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
        return dialog
    }
}