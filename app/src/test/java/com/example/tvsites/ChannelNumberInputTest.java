package com.example.tvsites;

import android.view.KeyEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelNumberInputTest {
    @Test public void supportsRemoteDigitsAndNumericKeypad() {
        for (int digit = 0; digit < 10; digit++) {
            assertEquals(digit, ChannelNumberInput.digitForKeyCode(KeyEvent.KEYCODE_0 + digit));
            assertEquals(digit, ChannelNumberInput.digitForKeyCode(KeyEvent.KEYCODE_NUMPAD_0 + digit));
        }
        assertEquals(-1, ChannelNumberInput.digitForKeyCode(KeyEvent.KEYCODE_DPAD_CENTER));
    }
    @Test public void waitsForSecondDigitInsteadOfTuningChannelOne() {
        ChannelNumberInput input = new ChannelNumberInput();
        input.append(1, 120, 100);
        input.append(2, 120, 900);
        assertEquals("12", input.digits());
        assertEquals(11, input.channelIndex(120));
        assertEquals(ChannelNumberInput.TIMEOUT_MS, input.remainingMs(900));
        assertEquals(0, input.remainingMs(900 + ChannelNumberInput.TIMEOUT_MS));
        assertEquals(11, input.commit(120));
        assertFalse(input.pending());
        assertEquals(-1, input.commit(120));
    }
    @Test public void supportsThreeDigitsAndLeadingZeroes() {
        ChannelNumberInput input = new ChannelNumberInput();
        input.append(1, 150, 100); input.append(2, 150, 200); input.append(3, 150, 300);
        assertEquals(122, input.commit(150));
        input.append(0, 150, 400); input.append(0, 150, 500); input.append(6, 150, 600);
        assertEquals(5, input.commit(150));
    }
    @Test public void invalidNumberDoesNotFallBackToAnotherChannel() {
        ChannelNumberInput input = new ChannelNumberInput();
        input.append(9, 25, 100); input.append(9, 25, 200);
        assertEquals(-1, input.commit(25)); assertFalse(input.pending());
        input.append(0, 25, 300); assertEquals(-1, input.commit(25));
    }
    @Test public void newDigitAfterDeadlineStartsNewEntryEvenIfTimerWasDelayed() {
        ChannelNumberInput input = new ChannelNumberInput();
        input.append(1, 120, 100);
        input.append(2, 120, 100 + ChannelNumberInput.TIMEOUT_MS);
        assertEquals("2", input.digits()); assertEquals(1, input.commit(120));
    }
    @Test public void cancellationCannotCommitLater() {
        ChannelNumberInput input = new ChannelNumberInput();
        input.append(1, 120, 100); input.clear();
        assertEquals(-1, input.commit(120)); assertEquals(0, input.remainingMs(200));
        input.append(6, 120, 300); assertEquals(5, input.commit(120));
    }
    @Test public void rejectsEmptyListsAndOutOfRangeDigits() {
        ChannelNumberInput input = new ChannelNumberInput();
        assertFalse(input.append(1, 0, 100));
        assertFalse(input.append(-1, 10, 100)); assertFalse(input.append(10, 10, 100));
        assertFalse(input.pending());
    }
    @Test public void boundedInputHandlesLargestChannelCountsWithoutOverflow() {
        ChannelNumberInput input = new ChannelNumberInput();
        for (int i = 0; i < 10; i++) input.append(9, Integer.MAX_VALUE, 100 + i);
        assertEquals(-1, input.commit(Integer.MAX_VALUE));
    }
}
