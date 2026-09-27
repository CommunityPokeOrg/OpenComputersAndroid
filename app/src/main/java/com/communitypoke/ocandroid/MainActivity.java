package com.communitypoke.ocandroid;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.opencomputers.Computer;

/**
 * Single-activity shell for the emulator: status line, screen view, and a
 * control strip (power, reset, keyboard, redstone side toggles).
 *
 * No androidx/appcompat dependencies — plain framework widgets only, so the
 * module builds with nothing but the Android SDK.
 */
public class MainActivity extends Activity {
    private EmulatorRuntime runtime;
    private ScreenView screenView;
    private TextView statusView;
    private Button powerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runtime = new EmulatorRuntime();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1B1B1B);

        statusView = new TextView(this);
        statusView.setTextColor(0xFFCCCCCC);
        statusView.setTextSize(12f);
        statusView.setPadding(16, 8, 16, 8);
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        screenView = new ScreenView(this);
        screenView.attach(runtime);
        root.addView(screenView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(controlStrip());
        setContentView(root);

        runtime.setStateListener((state, error) -> runOnUiThread(() -> {
            updateStatus(state, error);
        }));
        updateStatus(Computer.State.OFF, null);
    }

    private View controlStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(8, 4, 8, 8);

        powerButton = new Button(this);
        powerButton.setText("POWER");
        powerButton.setOnClickListener(v -> {
            if (runtime.computer().state() == Computer.State.RUNNING) {
                runtime.powerOff();
            } else {
                runtime.powerOn();
            }
            updateStatus(runtime.computer().state(), runtime.computer().machine().lastError());
        });
        strip.addView(powerButton, lp(1));

        Button reset = new Button(this);
        reset.setText("RESET");
        reset.setOnClickListener(v -> runtime.reset());
        strip.addView(reset, lp(1));

        Button kbd = new Button(this);
        kbd.setText("KBD");
        kbd.setOnClickListener(v -> toggleIme());
        strip.addView(kbd, lp(1));

        for (int side = 0; side < 6; side++) {
            final int s = side;
            ToggleButton t = new ToggleButton(this);
            t.setText("R" + s);
            t.setTextOn("R" + s);
            t.setTextOff("r" + s);
            t.setTextSize(10f);
            t.setOnCheckedChangeListener((b, checked) ->
                    runtime.redstone().setExternalInput(s, checked ? 15 : 0));
            strip.addView(t, lp(0.7f));
        }
        return strip;
    }

    private LinearLayout.LayoutParams lp(float weight) {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight);
    }

    private void updateStatus(Computer.State state, String error) {
        powerButton.setText(state == Computer.State.RUNNING ? "POWER OFF" : "POWER");
        String text = "state: " + state
                + "  uptime: " + String.format("%.1fs", runtime.computer().uptime())
                + "  energy: " + (int) runtime.computer().energy();
        if (error != null) {
            text += "  | " + error;
        }
        statusView.setText(text);
    }

    private void toggleIme() {
        screenView.requestFocus();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    // ---- keyboard → OC signals ----

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int ocCode = ocKeyCode(event.getKeyCode());
        if (ocCode < 0) {
            return super.dispatchKeyEvent(event);
        }
        int unicode = event.getUnicodeChar();
        int charCode = unicode > 0 ? unicode : 0;
        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            charCode = '\n';
        } else if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
            charCode = 8;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            runtime.keyboard().pressKey(charCode, ocCode);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            runtime.keyboard().releaseKey(charCode, ocCode);
        }
        return true;
    }

    /** Android keycode → OC keycode (LWJGL-derived). -1 = unmapped. */
    private static int ocKeyCode(int androidCode) {
        if (androidCode >= KeyEvent.KEYCODE_A && androidCode <= KeyEvent.KEYCODE_Z) {
            // OC uses LWJGL codes; map the alpha block explicitly.
            int[] oc = {30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50,
                    49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44};
            return oc[androidCode - KeyEvent.KEYCODE_A];
        }
        if (androidCode >= KeyEvent.KEYCODE_0 && androidCode <= KeyEvent.KEYCODE_9) {
            return androidCode == KeyEvent.KEYCODE_0
                    ? 11
                    : 2 + (androidCode - KeyEvent.KEYCODE_1);
        }
        return switch (androidCode) {
            case KeyEvent.KEYCODE_ENTER -> 28;
            case KeyEvent.KEYCODE_DEL -> 14;
            case KeyEvent.KEYCODE_SPACE -> 57;
            case KeyEvent.KEYCODE_TAB -> 15;
            case KeyEvent.KEYCODE_SHIFT_LEFT -> 42;
            case KeyEvent.KEYCODE_SHIFT_RIGHT -> 54;
            case KeyEvent.KEYCODE_CTRL_LEFT -> 29;
            case KeyEvent.KEYCODE_ALT_LEFT -> 56;
            case KeyEvent.KEYCODE_DPAD_UP -> 200;
            case KeyEvent.KEYCODE_DPAD_DOWN -> 208;
            case KeyEvent.KEYCODE_DPAD_LEFT -> 203;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> 205;
            case KeyEvent.KEYCODE_MINUS -> 12;
            case KeyEvent.KEYCODE_EQUALS -> 13;
            case KeyEvent.KEYCODE_LEFT_BRACKET -> 26;
            case KeyEvent.KEYCODE_RIGHT_BRACKET -> 27;
            case KeyEvent.KEYCODE_SEMICOLON -> 39;
            case KeyEvent.KEYCODE_APOSTROPHE -> 40;
            case KeyEvent.KEYCODE_GRAVE -> 41;
            case KeyEvent.KEYCODE_BACKSLASH -> 43;
            case KeyEvent.KEYCODE_COMMA -> 51;
            case KeyEvent.KEYCODE_PERIOD -> 52;
            case KeyEvent.KEYCODE_SLASH -> 53;
            case KeyEvent.KEYCODE_FORWARD_DEL -> 211;
            case KeyEvent.KEYCODE_MOVE_HOME -> 199;
            case KeyEvent.KEYCODE_MOVE_END -> 207;
            case KeyEvent.KEYCODE_PAGE_UP -> 201;
            case KeyEvent.KEYCODE_PAGE_DOWN -> 209;
            default -> -1;
        };
    }

    @Override
    protected void onDestroy() {
        runtime.powerOff();
        super.onDestroy();
    }
}
