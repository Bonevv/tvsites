package com.example.tvsites;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Shared native palette of the approved TVsites visual specification. */
final class TvTheme {
    static final int BACKGROUND = 0xFF101218;
    static final int SURFACE = 0xFF1B2029;
    static final int SURFACE_ALT = 0xFF2B3442;
    static final int BUTTON = 0xFF202630;
    static final int TEXT = 0xFFF5F7FC;
    static final int MUTED = 0xFFADB6C5;
    static final int OUTLINE = 0xFF394351;
    static final int FOCUS = 0xFFF5F7FC;
    static final int BLUE = 0xFF56B7F0;
    static final int BLUE_LIGHT = 0xFFACDCFF;
    static final int PRESSED = 0xFF344C60;
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
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF1A212B, BACKGROUND, 0xFF0D0F14});
    }
    static GradientDrawable hero(float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF283C4C, 0xFF1D2A37, 0xFF161C24});
        d.setCornerRadius(radius);
        d.setStroke(1, OUTLINE);
        return d;
    }
    static StateListDrawable control(float unit, boolean selected) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, panel(14*unit, PRESSED, FOCUS, 3*unit));
        d.addState(new int[]{android.R.attr.state_focused}, panel(14*unit, SURFACE_ALT, FOCUS, 3*unit));
        d.addState(new int[]{}, panel(14*unit, selected?0xFF29485F:BUTTON, selected?BLUE:OUTLINE, unit));
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
        d.addState(new int[]{},panel(12*unit,selected?0xFF263C4D:0,0,0));
        return d;
    }
    static void focus(android.view.View view, boolean focused) {
        view.animate().scaleX(focused ? 1.025f : 1f).scaleY(focused ? 1.025f : 1f).setDuration(130).start();
    }
    static android.graphics.drawable.LayerDrawable progress(float unit) {
        android.graphics.drawable.Drawable track = panel(3*unit, OUTLINE, 0, 0);
        android.graphics.drawable.Drawable fill = new android.graphics.drawable.ClipDrawable(
                panel(3*unit, BLUE, 0, 0), android.view.Gravity.START,
                android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable drawable = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{track, fill});
        drawable.setId(0, android.R.id.background);
        drawable.setId(1, android.R.id.progress);
        return drawable;
    }
    private TvTheme() {}
}
