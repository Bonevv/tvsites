package com.example.tvsites;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Shared graphite / aubergine theme for every native screen. */
final class TvTheme {
    static final int BACKGROUND = 0xFF1C1A29;
    static final int BACKGROUND_TOP = 0xFF302D40;
    static final int SURFACE = 0xD9323042;
    static final int SURFACE_ALT = 0xFF444153;
    static final int TEXT = 0xFFF7F6FA;
    static final int MUTED = 0xFFB6B2C3;
    static final int OUTLINE = 0x30D4CEE5;
    static final int FOCUS = 0xFFF7F6FA;
    static final int ACCENT = 0xFFFF914D;
    // Legacy callers share the same neutral palette, including player and dialogs.
    static final int BLUE = 0xFF6B637F;
    static final int BLUE_LIGHT = 0xFFE6E1F0;
    static GradientDrawable panel(float radius, int color, int stroke, float width) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(Math.max(1, Math.round(width)), stroke);
        return d;
    }
    static GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{BACKGROUND_TOP, BACKGROUND, 0xFF252232});
    }
    static StateListDrawable control(float unit, boolean selected) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, panel(10*unit, SURFACE_ALT, FOCUS, 2*unit));
        d.addState(new int[]{}, panel(10*unit, selected ? SURFACE_ALT : SURFACE, OUTLINE, unit));
        return d;
    }
    private TvTheme() {}
}
