package com.example.tvsites;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Non-focusable confirmation, so typing never moves D-pad focus. */
final class ChannelNumberOverlay extends LinearLayout {
    private final TextView number, channel;

    ChannelNumberOverlay(Context context, float unit) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(Math.round(28 * unit), Math.round(20 * unit), Math.round(28 * unit), Math.round(22 * unit));
        setBackground(TvTheme.panel(18 * unit, TvTheme.SURFACE, TvTheme.OUTLINE, unit));
        setElevation(48 * unit);
        setGravity(Gravity.START);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(label("ИЗБОР НА КАНАЛ", 14, TvTheme.MUTED, unit));
        number = label("", 58, TvTheme.TEXT, unit);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        addView(number);
        channel = label("", 23, TvTheme.TEXT, unit);
        channel.setSingleLine(true);
        channel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        addView(channel);
        TextView hint = label("OK  Гледай   ·   Назад  Отказ", 16, TvTheme.MUTED, unit);
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(14 * unit);
        addView(hint, lp);
    }

    void show(String digits, String name) {
        number.setText(digits);
        channel.setText(name == null ? "Няма канал с този номер" : name);
        channel.setTextColor(name == null ? TvTheme.WARNING : TvTheme.TEXT);
        setContentDescription("Канал " + digits + ". " + channel.getText());
        setVisibility(VISIBLE);
    }

    private TextView label(String value, int size, int color, float unit) {
        TextView text = new TextView(getContext());
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit);
        return text;
    }
}
