package com.example.tvsites;

import android.view.KeyEvent;

/** Bounded, one-shot channel entry. Uses the master list, never a filtered list. */
final class ChannelNumberInput {
    static final long TIMEOUT_MS = 1500;
    private String digits = "";
    private long deadline;

    static int digitForKeyCode(int code) {
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return code - KeyEvent.KEYCODE_0;
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) return code - KeyEvent.KEYCODE_NUMPAD_0;
        return -1;
    }

    boolean append(int digit, int channelCount, long now) {
        if (digit < 0 || digit > 9 || channelCount <= 0) return false;
        int maxDigits = Math.max(3, Integer.toString(channelCount).length());
        if (now >= deadline || digits.length() >= maxDigits) clear();
        digits += digit;
        deadline = now + TIMEOUT_MS;
        return true;
    }

    boolean pending() { return !digits.isEmpty(); }
    String digits() { return digits; }
    long remainingMs(long now) { return pending() ? Math.max(0, deadline - now) : 0; }

    int channelIndex(int channelCount) {
        if (!pending()) return -1;
        long number = Long.parseLong(digits);
        return number > 0 && number <= channelCount ? (int) number - 1 : -1;
    }

    int commit(int channelCount) {
        int index = channelIndex(channelCount);
        clear();
        return index;
    }

    void clear() { digits = ""; deadline = 0; }
}
