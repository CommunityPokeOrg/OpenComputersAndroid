package org.communitypoke.opencomputers

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.communitypoke.opencomputers.core.machine.Machine

class MainActivity : Activity() {

    private lateinit var controller: MachineController
    private lateinit var screenView: ScreenView
    private lateinit var statusView: TextView
    private lateinit var powerButton: Button
    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        controller = MachineController(filesDir)
        screenView = findViewById(R.id.screenView)
        statusView = findViewById(R.id.statusView)
        powerButton = findViewById(R.id.powerButton)
        input = findViewById(R.id.input)

        screenView.buffer = controller.buffer
        controller.setBufferChangedListener { screenView.onBufferChanged() }

        controller.machine.onStateChanged = { state ->
            runOnUiThread { statusView.text = state.name.lowercase() }
        }
        controller.machine.onCrashed = { reason ->
            runOnUiThread { statusView.text = "crashed: $reason" }
        }

        powerButton.setOnClickListener { controller.power() }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString() ?: return
                if (count > before) {
                    text.substring(start + before, start + count).codePoints()
                        .forEach { controller.onKeyChar(it) }
                } else if (count < before) {
                    repeat(before - count) { controller.onKeyChar(8) } // backspace
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Physical keyboard keys that produce no character (enter, delete...)
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            controller.onKeyChar('\n'.code)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        controller.machine.stop()
        super.onDestroy()
    }
}
