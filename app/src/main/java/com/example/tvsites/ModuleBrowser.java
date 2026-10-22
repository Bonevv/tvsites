package com.example.tvsites;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** TV-focusable cards shared by saved websites and live radio. */
@SuppressLint("ViewConstructor")
final class ModuleBrowser extends LinearLayout {
    static final class Entry {
        final String name, subtitle, description, primary, secondary;
        final Runnable open, alternate;
        final int icon;

        Entry(String name, String subtitle, String description, int icon,
              String primary, Runnable open, String secondary, Runnable alternate) {
            this.name = name == null || name.trim().isEmpty() ? "Сайт" : name;
            this.subtitle = subtitle;
            this.description = description;
            this.icon = icon;
            this.primary = primary;
            this.open = open;
            this.secondary = secondary;
            this.alternate = alternate;
        }
    }

    private static final int[] TONES = {
            0xFF153F72, 0xFF225E69, 0xFF503873, 0xFF6A3D52, 0xFF665332
    };
    private final float unit;
    private View first;

    ModuleBrowser(Context context, float unit, List<Entry> entries) {
        super(context);
        this.unit = unit;
        setOrientation(VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);
        setPadding(px(4), px(5), px(4), px(5));
        if (entries.isEmpty()) {
            TextView empty = label("Няма добавени елементи.", 22, false, TvTheme.MUTED);
            addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, px(80)));
            return;
        }

        boolean radio = entries.get(0).icon == 2;
        int columns = radio ? 4 : 3;
        LinearLayout row = null;
        for (int i = 0; i < entries.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(context);
                row.setOrientation(HORIZONTAL);
                row.setClipChildren(false);
                row.setClipToPadding(false);
                LayoutParams rp = new LayoutParams(LayoutParams.MATCH_PARENT, px(radio ? 150 : 145));
                if (i > 0) rp.topMargin = px(17);
                addView(row, rp);
            }
            Entry e = entries.get(i);
            LinearLayout tile = createCard(context, e, radio);
            LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
            if (i % columns > 0) lp.leftMargin = px(15);
            row.addView(tile, lp);
            if (first == null) first = tile;
        }
        if (row != null) {
            while (row.getChildCount() < columns) {
                row.addView(new View(context), new LayoutParams(0, 1, 1f));
            }
        }
    }

    private LinearLayout createCard(Context context, Entry entry, boolean radio) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setPadding(px(20), px(17), px(18), px(16));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setTag((radio ? "radio:" : "site:") + entry.name);
        card.setBackground(TvTheme.control(unit, false));
        card.setClipChildren(false);

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = label(radio ? "♫" : mark(entry.name), 26, true, TvTheme.TEXT);
        mark.setGravity(Gravity.CENTER);
        GradientDrawable tone = TvTheme.panel(px(11),
                TONES[(entry.name.hashCode() & Integer.MAX_VALUE) % TONES.length], 0, 0);
        mark.setBackground(tone);
        top.addView(mark, new LayoutParams(px(54), px(54)));

        TextView meta = label(radio ? "●  НА ЖИВО" : "↗  САЙТ", 14, true,
                radio ? TvTheme.PLAYING : TvTheme.BLUE_LIGHT);
        LayoutParams ml = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        ml.leftMargin = px(13);
        top.addView(meta, ml);
        card.addView(top, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView name = label(entry.name, 23, true, TvTheme.TEXT);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams nl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        nl.topMargin = px(13);
        card.addView(name, nl);

        String subtitle = entry.subtitle == null ? "" : entry.subtitle;
        TextView sub = label(subtitle, 16, false, TvTheme.MUTED);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams sl = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sl.topMargin = px(5);
        card.addView(sub, sl);

        card.setContentDescription(entry.name + ". OK: " + entry.primary +
                (entry.alternate == null ? "" : ". Задръж OK: " + entry.secondary));
        if (entry.open != null) card.setOnClickListener(v -> entry.open.run());
        if (entry.alternate != null) {
            card.setLongClickable(true);
            card.setOnLongClickListener(v -> { entry.alternate.run(); return true; });
        }
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.025f : 1f)
                    .scaleY(focused ? 1.025f : 1f).setDuration(140).start();
            v.setElevation(focused ? px(16) : px(2));
        });
        return card;
    }

    View first() { return first; }

    private String mark(String text) {
        String cleaned = text == null ? "" : text.trim();
        return cleaned.isEmpty() ? "●" : cleaned.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private TextView label(String value, int size, boolean bold, int color) {
        TextView text = new TextView(getContext());
        text.setText(value);
        text.setTextColor(color);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }

    private int px(float value) { return Math.round(value * unit); }
}
