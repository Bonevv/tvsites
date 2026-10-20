package com.example.tvsites;

import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Shared tokens from the approved, uploaded theme.json. No runtime theme parsing. */
final class TvTheme {
    static final int BACKGROUND = 0xFF040313; // loginFormColorBackground
    static final int SURFACE = 0xFF09081C; // cardColorContainer
    static final int SURFACE_ALT = 0xFF323144; // ePGListColorSurfaceVariant
    static final int TEXT = 0xFFFCFCFC; // cardColorOnCard
    static final int MUTED = 0x80FCFCFC; // cardColorOnCardVariant
    static final int OUTLINE = 0x1FFCFCFC; // cardColorOutline
    static final int FOCUS = 0xFF136BFF; // colorFocusFocus
    static final int BLUE = 0xFF136BFF; // colorButtonButtonContainer
    static final int BLUE_LIGHT = 0xFF478CFF; // colorButtonPrimaryButton
    static final int PRESSED = 0xFF0044B8; // colorButtonButtonContainerPressed
    static final int BACKGROUND_TOP = 0xFF0A072E; // bannerColorPlaceholder
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
        d.addState(new int[]{android.R.attr.state_focused}, panel(8*unit, selected?BLUE:SURFACE, FOCUS, 2*unit));
        d.addState(new int[]{}, panel(8*unit, selected?BLUE:SURFACE, selected?BLUE:OUTLINE, unit));
        return d;
    }
    static StateListDrawable navigation(float unit, boolean selected) {
        StateListDrawable d=new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused},panel(8*unit,selected?BLUE:SURFACE,FOCUS,2*unit));
        d.addState(new int[]{},panel(8*unit,selected?BLUE:0,0,0));
        return d;
    }
    private TvTheme() {}
}
