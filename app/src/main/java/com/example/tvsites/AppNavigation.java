package com.example.tvsites;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** A single, shared navigation bar. Hidden for every fullscreen playback mode. */
@android.annotation.SuppressLint("ViewConstructor") // Constructed in Java with explicit actions, never inflated from XML.
final class AppNavigation extends LinearLayout {
    interface Listener { void select(int section); }
    private final float unit;
    private final LinearLayout[] tabs = new LinearLayout[5];
    private int selected;
    AppNavigation(Context context, float unit, Listener listener) {
        super(context);
        this.unit = unit;
        setGravity(Gravity.CENTER_VERTICAL);
        setOrientation(HORIZONTAL);
        TextView brand = label("ТВ Сайтове", 23, true);
        brand.setTextColor(TvTheme.BLUE_LIGHT);
        addView(brand, new LayoutParams(px(200), LayoutParams.WRAP_CONTENT));
        String[] names = {"ТВ", "Видеотека", "Радио", "Сайтове", "Настройки"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(context);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(px(18), 0, px(18), 0);
            tab.setFocusable(true); tab.setClickable(true);
            tab.setTag("nav:" + i);
            tab.setContentDescription(names[i]);
            tab.addView(new NavigationIcon(context, i), new LayoutParams(px(22), px(22)));
            TextView text = label(names[i], 18, true);
            LayoutParams tl = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tl.leftMargin = px(10);
            tab.addView(text, tl);
            tab.setOnClickListener(v -> listener.select(index));
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, px(52));
            lp.rightMargin = px(10);
            addView(tab, lp);
            tabs[i] = tab;
        }
        addView(new View(context), new LayoutParams(0, 1, 1f));
        TextClock clock = new TextClock(context);
        clock.setFormat24Hour("HH:mm"); clock.setFormat12Hour("HH:mm");
        clock.setTextColor(TvTheme.TEXT);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(23));
        addView(clock);
        setSection(0);
    }
    void setSection(int section) {
        selected = section;
        for (int i = 0; i < tabs.length; i++) {
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, shape(i == selected ? TvTheme.BLUE : 0xFF20213A, 0xFF91B7FF, 2));
            bg.addState(new int[]{}, shape(i == selected ? TvTheme.BLUE : 0x00000000, 0, 0));
            tabs[i].setBackground(bg);
            tabs[i].setSelected(i == selected);
        }
    }
    void focusSelected() { tabs[selected].requestFocus(); }
    private GradientDrawable shape(int color, int stroke, int width) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(px(14));
        if (width > 0) bg.setStroke(px(width), stroke);
        return bg;
    }
    private TextView label(String value, int size, boolean bold) {
        TextView text = new TextView(getContext()); text.setText(value); text.setTextColor(TvTheme.TEXT);
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size)); text.setSingleLine(true);
        text.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return text;
    }
    private int px(float size) { return Math.round(size * unit); }
    private static final class NavigationIcon extends View {
        final int kind; final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        NavigationIcon(Context c, int kind) { super(c); this.kind = kind; }
        @Override protected void onDraw(Canvas c) {
            c.save(); c.scale(getWidth() / 24f, getHeight() / 24f);
            p.setColor(TvTheme.TEXT); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); p.setStrokeCap(Paint.Cap.ROUND);
            if (kind == 0) { c.drawRoundRect(2, 4, 22, 17, 2, 2, p); c.drawLine(8, 21, 16, 21, p); }
            else if (kind == 1) { c.drawRoundRect(3, 3, 21, 21, 2, 2, p); c.drawLine(8, 3, 8, 21, p); c.drawLine(16, 3, 16, 21, p); c.drawLine(3, 9, 21, 9, p); c.drawLine(3, 15, 21, 15, p); }
            else if (kind == 2) { c.drawRoundRect(3, 8, 21, 21, 2, 2, p); c.drawLine(5, 8, 18, 2, p); c.drawCircle(9, 14, 3, p); c.drawLine(15, 13, 18, 13, p); c.drawLine(15, 17, 18, 17, p); }
            else if (kind == 3) { c.drawCircle(12, 12, 10, p); c.drawOval(7, 2, 17, 22, p); c.drawLine(2, 12, 22, 12, p); }
            else { for (int i = 0; i < 3; i++) { float y = 5 + i * 7; c.drawLine(3, y, 21, y, p); c.drawCircle(i == 1 ? 9 : 15, y, 2, p); } }
            c.restore();
        }
    }
}
