package org.opencomputers.component;

import org.opencomputers.api.AbstractComponent;
import org.opencomputers.api.ComputerContext;

/**
 * OC keyboard: has no callbacks of its own — it is a pure signal source.
 * The host UI feeds key events through {@link #pressKey}/{@link #releaseKey}
 * which enqueue {@code key_down}/{@code key_up} signals with (address, char,
 * keyCode, playerName-less) args like real OC.
 */
public class KeyboardComponent extends AbstractComponent {

    /** OC key codes for the subset the app maps. */
    public static final class Keys {
        public static final int BACK = 14;
        public static final int ENTER = 28;
        public static final int LSHIFT = 42;
        public static final int LCONTROL = 29;
        public static final int SPACE = 57;
        public static final int UP = 200;
        public static final int DOWN = 208;
        public static final int LEFT = 203;
        public static final int RIGHT = 205;
        public static final int F1 = 59;
        public static final int F12 = 88;
    }

    public KeyboardComponent() {
        super("keyboard");
    }

    @Override
    public void onAttach(ComputerContext context) {
        super.onAttach(context);
    }

    /** Feed a key press: pushes a key_down signal. charCode 0 for non-printable. */
    public void pressKey(int charCode, int keyCode) {
        if (context != null) {
            context.pushSignal("key_down", address(), (double) charCode, (double) keyCode);
        }
    }

    /** Feed a key release: pushes a key_up signal. */
    public void releaseKey(int charCode, int keyCode) {
        if (context != null) {
            context.pushSignal("key_up", address(), (double) charCode, (double) keyCode);
        }
    }

    /** Feed a paste event: pushes a clipboard signal with the pasted text. */
    public void paste(String text) {
        if (context != null) {
            context.pushSignal("clipboard", address(), text);
        }
    }
}
