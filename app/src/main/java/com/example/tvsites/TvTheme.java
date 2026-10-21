package com.example.tvsites;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Shared native palette of the approved TV Focus proposal. */
final class TvTheme {
    static final int BACKGROUND = 0xFF0D1119;
    static final int SURFACE = 0xFF171E2A;
    static final int SURFACE_ALT = 0xFF222D3E;
    static final int TEXT = 0xFFF5F7FB;
    static final int MUTED = 0xFFA6B2C5;
    static final int OUTLINE = 0x19FFFFFF;
    static final int FOCUS = 0xFF4589FF;
    static final int BLUE = 0xFF4589FF;
    static final int BLUE_LIGHT = 0xFFB5D0FF;
    static final int PRESSED = 0xFF285EA8;
    static final int BACKGROUND_TOP = BACKGROUND;
    static final int ACCENT = BLUE;
    static GradientDrawable panel(float radius, int color, int stroke, float width) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(Math.max(1, Math.round(width)), stroke);
        return d;
    }
    static GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{BACKGROUND_TOP, BACKGROUND});
    }
    static StateListDrawable control(float unit, boolean selected) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, panel(12*unit, selected?BLUE:SURFACE_ALT, FOCUS, 3*unit));
        d.addState(new int[]{}, panel(12*unit, selected?BLUE:SURFACE, selected?BLUE:OUTLINE, unit));
        return d;
    }
    static StateListDrawable navigation(float unit, boolean selected) {
        StateListDrawable d=new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused},panel(10*unit,SURFACE_ALT,FOCUS,2*unit));
        d.addState(new int[]{},panel(10*unit,selected?SURFACE_ALT:0,0,0));
        return d;
    }
    private TvTheme() {}
}
