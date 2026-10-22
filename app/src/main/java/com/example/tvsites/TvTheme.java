package com.example.tvsites;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Shared native palette of the approved TVsites visual specification. */
final class TvTheme {
    static final int BACKGROUND = 0xFF060E1B;
    static final int SURFACE = 0xFF101F37;       // panel
    static final int SURFACE_ALT = 0xFF1E3D62;   // surface (focused control)
    static final int BUTTON = 0xFF142840;        // idle control
    static final int TEXT = 0xFFF2F7FF;
    static final int MUTED = 0xFF9DB2CF;
    static final int OUTLINE = 0xFF305172;
    static final int FOCUS = 0xFF56D6FF;
    static final int BLUE = 0xFF118DFF;          // accent
    static final int BLUE_LIGHT = 0xFF7FDFFF;
    static final int PRESSED = 0xFF0F5FCC;
    static final int PLAYING = 0xFF27D3A0;
    static final int WARNING = 0xFFFFC166;
    static final int FAILURE = 0xFFFF6576;
    static final int LIVE = 0xFFC1182E;
    static final int BACKGROUND_TOP = BACKGROUND;
    static final int ACCENT = BLUE;
    static GradientDrawable panel(float radius, int color, int stroke, float width) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(Math.max(1, Math.round(width)), stroke);
        return d;
    }
    static GradientDrawable background() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF0C1B31, BACKGROUND, 0xFF050B15});
    }
    static GradientDrawable hero(float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1A4778, 0xFF0F2C4F, 0xFF091525});
        d.setCornerRadius(radius);
        d.setStroke(1, OUTLINE);
        return d;
    }
    static StateListDrawable control(float unit, boolean selected) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, panel(14*unit, selected?BLUE:SURFACE_ALT, FOCUS, 3*unit));
        d.addState(new int[]{}, panel(14*unit, selected?BLUE:BUTTON, selected?BLUE:OUTLINE, unit));
        return d;
    }
    /** Control with a coloured accent when idle (danger, warning...). */
    static StateListDrawable tinted(float unit, int idleFill, int idleStroke) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, panel(14*unit, SURFACE_ALT, FOCUS, 3*unit));
        d.addState(new int[]{}, panel(14*unit, idleFill, idleStroke, unit));
        return d;
    }
    static StateListDrawable navigation(float unit, boolean selected) {
        StateListDrawable d=new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused},panel(12*unit,SURFACE_ALT,FOCUS,2*unit));
        d.addState(new int[]{},panel(12*unit,selected?BLUE:0,0,0));
        return d;
    }
    private TvTheme() {}
}
