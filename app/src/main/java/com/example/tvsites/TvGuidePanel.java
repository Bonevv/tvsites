package com.example.tvsites;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Recycled channel rows and a single, lazily updated programme panel. */
@android.annotation.SuppressLint("ViewConstructor")
final class TvGuidePanel extends LinearLayout {
    static final class Channel {
        final String id, name;
        final boolean favourite;
        final int number;
        Channel(String id, String name, boolean favourite, int number) {
            this.id = id; this.name = name; this.favourite = favourite; this.number = number;
        }
    }
    interface Actions {
        void watch(String id);
        void sources(String id);
        void favourite(String id);
        void retry();
        void menu();
        View logo(String name);
    }
    private final float unit;
    private final Actions actions;
    private final boolean overVideo;
    private final ListView list;
    private final ArrayList<Channel> channels = new ArrayList<>();
    private final Map<String, Channel> byId = new HashMap<>();
    private final LinearLayout details;
    private final ScrollView detailScroll;
    private final BaseAdapter adapter;
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private EpgGuide guide, displayedGuide;
    private EpgGuide.Event displayedEvent;
    private String guideError, displayedError, selectedId, displayedId, returnId;
    private boolean displayedFavourite;
    private int returnTop;
    private ProgressBar detailProgress;
    private View detailAction;
    private final Runnable render = this::renderDetails;

    TvGuidePanel(Context context, float unit, Actions actions, boolean overVideo) {
        super(context);
        this.unit = unit; this.actions = actions; this.overVideo = overVideo;
        setOrientation(HORIZONTAL); setPadding(px(3), px(3), px(3), px(3));
        list = new ListView(context);
        list.setId(View.generateViewId()); list.setFocusable(true); list.setFocusableInTouchMode(true);
        list.setDivider(null); list.setDividerHeight(px(6)); list.setVerticalScrollBarEnabled(false);
        list.setItemsCanFocus(false); list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        list.setSelector(shape(0x08FFFFFF, TvTheme.FOCUS, 3)); list.setDrawSelectorOnTop(true);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return channels.size(); }
            @Override public Object getItem(int position) { return channels.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convert, ViewGroup parent) {
                Row row = convert instanceof Row ? (Row) convert : new Row();
                row.bind(channels.get(position)); return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnKeyListener((v, key, event) -> {
            if (!overVideo && key == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) actions.menu();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    removeCallbacks(render); renderDetails();
                    if (detailAction != null) detailAction.requestFocus();
                }
                return true;
            }
            return false;
        });
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < channels.size()) select(channels.get(position).id);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> actions.watch(channels.get(position).id));
        list.setOnItemLongClickListener((parent, view, position, id) -> { actions.sources(channels.get(position).id); return true; });
        LayoutParams left = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1.25f); left.rightMargin = px(26);
        addView(list, left);
        detailScroll = new ScrollView(context); detailScroll.setVerticalScrollBarEnabled(false);
        detailScroll.setBackground(shape(TvTheme.SURFACE, TvTheme.OUTLINE, 1));
        details = new LinearLayout(context); details.setOrientation(VERTICAL);
        details.setPadding(px(26), px(24), px(26), px(26));
        detailScroll.addView(details, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(detailScroll, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    void setChannels(List<Channel> values, String preferredId) {
        removeCallbacks(render); channels.clear(); channels.addAll(values); byId.clear();
        for (Channel c : channels) byId.put(c.id, c);
        displayedId = null; adapter.notifyDataSetChanged();
        int chosen = 0;
        for (int i = 0; i < channels.size(); i++) if (channels.get(i).id.equals(preferredId)) chosen = i;
        if (!channels.isEmpty()) { list.setSelection(chosen); select(channels.get(chosen).id); renderDetails(); }
        else {
            selectedId = null; detailAction = null; details.removeAllViews();
            details.addView(text("Няма канали в този списък", 28, TvTheme.TEXT, true));
            details.addView(text("Добави любими от менюто на канал.", 19, TvTheme.MUTED, false), top(16));
        }
    }
    void setGuide(EpgGuide value, String error) { guide = value; guideError = error; refresh(); }
    void refresh() {
        adapter.notifyDataSetChanged();
        if (selectedId != null && !details.hasFocus()) renderDetails();
        else updateProgress(System.currentTimeMillis());
    }
    void rememberPosition() {
        returnId = selectedId;
        int index = list.getSelectedItemPosition();
        if (index >= 0 && index < channels.size()) returnId = channels.get(index).id;
        View row = list.getSelectedView(); returnTop = row == null ? 0 : row.getTop();
    }
    void focusChannels() {
        String id = returnId != null ? returnId : selectedId;
        int position = 0;
        for (int i = 0; i < channels.size(); i++) if (channels.get(i).id.equals(id)) { position = i; break; }
        final int target = position, offset = returnTop; returnId = null;
        list.requestFocusFromTouch(); list.setSelectionFromTop(target, offset);
        list.post(() -> { if (list.hasFocus() && target < channels.size() && list.getSelectedItemPosition() == target) list.setSelectionFromTop(target, offset); });
    }
    String selection() { return selectedId; }
    private void select(String id) {
        if (id.equals(selectedId)) return;
        selectedId = id;
        // Moving focus is not a data change: do not rebind every visible logo.
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof Row) ((Row) child).highlight();
        }
        removeCallbacks(render); postDelayed(render, 110);
    }
    @Override protected void onDetachedFromWindow() { removeCallbacks(render); super.onDetachedFromWindow(); }

    private void renderDetails() {
        Channel channel = byId.get(selectedId);
        if (channel == null) return;
        long now = System.currentTimeMillis();
        EpgGuide.Event current = guide == null ? null : guide.current(channel.name, now);
        if (channel.id.equals(displayedId) && current == displayedEvent && guide == displayedGuide
                && Objects.equals(guideError, displayedError) && channel.favourite == displayedFavourite) {
            updateProgress(now); return;
        }
        displayedId = channel.id; displayedEvent = current; displayedGuide = guide;
        displayedError = guideError; displayedFavourite = channel.favourite;
        details.removeAllViews(); detailProgress = null; detailScroll.scrollTo(0, 0);
        LinearLayout heading = new LinearLayout(getContext()); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(actions.logo(channel.name), new LayoutParams(px(86), px(60)));
        LinearLayout labels = new LinearLayout(getContext()); labels.setOrientation(VERTICAL);
        labels.addView(text(String.format(Locale.ROOT, "КАНАЛ %03d", channel.number), 14, TvTheme.BLUE_LIGHT, true));
        TextView name = text(channel.name + (channel.favourite ? "  ★" : ""), 30, TvTheme.TEXT, true);
        name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END); labels.addView(name, top(5));
        LayoutParams labelLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f); labelLp.leftMargin = px(18);
        heading.addView(labels, labelLp); details.addView(heading);
        if (!overVideo && current != null && !current.image.isEmpty()) {
            TvArtwork artwork = new TvArtwork(getContext());
            LayoutParams art = new LayoutParams(LayoutParams.MATCH_PARENT, px(170)); art.topMargin = px(22);
            details.addView(artwork, art); artwork.show(current.image);
        }
        details.addView(text("СЕГА НА ЖИВО", 14, TvTheme.BLUE_LIGHT, true), top(24));
        TextView title = text(current == null ? "Телевизия на живо" : current.title, 30, TvTheme.TEXT, true);
        title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); details.addView(title, top(8));
        details.addView(text(current == null ? "Няма налична програма" : times(current), 18, TvTheme.MUTED, false), top(10));
        if (current != null) {
            detailProgress = progress(); LayoutParams bar = new LayoutParams(LayoutParams.MATCH_PARENT, px(5));
            bar.topMargin = px(13); details.addView(detailProgress, bar); updateProgress(now);
            if (!current.description.isEmpty()) {
                TextView description = text(current.description, 18, TvTheme.MUTED, false);
                description.setMaxLines(3); description.setEllipsize(TextUtils.TruncateAt.END); details.addView(description, top(16));
            }
        }
        LinearLayout buttons = new LinearLayout(getContext()); buttons.setClipChildren(false);
        detailAction = button("▶  Гледай", () -> actions.watch(channel.id), true);
        buttons.addView(detailAction, new LayoutParams(0, px(52), 1f));
        LayoutParams favouriteLp = new LayoutParams(0, px(52), 1f); favouriteLp.leftMargin = px(12);
        buttons.addView(button(channel.favourite ? "★ В любими" : "☆ Любим", () -> actions.favourite(channel.id), false), favouriteLp);
        details.addView(buttons, top(24));
        details.addView(button("Потоци и източници", () -> actions.sources(channel.id), false), top(12));
        details.addView(text("СЛЕДВА ПО " + channel.name, 14, TvTheme.MUTED, true), top(26));
        int count = 0;
        if (guide != null) for (EpgGuide.Event event : guide.forChannel(channel.name, now)) {
            if (event == current) continue;
            TextView next = text(times(event) + "\n" + event.title, 18, TvTheme.TEXT, false);
            next.setPadding(px(14), px(12), px(14), px(12)); next.setBackground(shape(TvTheme.BUTTON, 0, 0));
            next.setMaxLines(3); next.setEllipsize(TextUtils.TruncateAt.END); details.addView(next, top(8));
            if (++count == 6) break;
        }
        if (count == 0) details.addView(text(guide == null && guideError == null ? "Програмата се зарежда…" : "Няма данни за следващи предавания.", 18, TvTheme.MUTED, false), top(12));
        if (guideError != null) details.addView(button("Обнови програмата", actions::retry, false), top(14));
    }
    private void updateProgress(long now) {
        if (detailProgress != null) detailProgress.setProgress(progressOf(displayedEvent, now));
    }
    private static int progressOf(EpgGuide.Event event, long now) {
        return event == null ? 0 : (int) Math.max(0, Math.min(1000, (now - event.start) * 1000 / Math.max(1, event.end - event.start)));
    }
    private ProgressBar progress() {
        ProgressBar bar = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(1000); bar.setProgressDrawable(TvTheme.progress(unit)); return bar;
    }
    private View button(String label, Runnable action, boolean primary) {
        TextView b = text(label, 18, TvTheme.TEXT, true); b.setGravity(Gravity.CENTER);
        b.setPadding(px(16), px(12), px(16), px(12)); b.setMinimumHeight(px(48));
        b.setId(View.generateViewId()); b.setFocusable(true); b.setClickable(true);
        b.setBackground(TvTheme.control(unit, primary)); b.setOnFocusChangeListener(TvTheme::focus);
        b.setNextFocusLeftId(list.getId()); b.setOnClickListener(v -> action.run()); return b;
    }
    private String times(EpgGuide.Event event) { return clock.format(new Date(event.start)) + " – " + clock.format(new Date(event.end)); }
    private int px(float n) { return Math.round(n * unit); }
    private LayoutParams top(int margin) { LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT); lp.topMargin = px(margin); return lp; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(getContext()); t.setText(value); t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL)); return t;
    }
    private GradientDrawable shape(int color, int stroke, float width) { return TvTheme.panel(px(14), color, stroke, px(width)); }

    private final class Row extends LinearLayout {
        final TextView name, programme, time, number;
        final FrameLayout logo;
        final ProgressBar rowProgress;
        final Drawable idle = shape(TvTheme.SURFACE, TvTheme.OUTLINE, 1);
        final Drawable selected = shape(TvTheme.SURFACE_ALT, TvTheme.BLUE, 1);
        String boundId, logoName;
        Row() {
            super(TvGuidePanel.this.getContext()); setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
            setPadding(px(16), px(12), px(16), px(12)); setMinimumHeight(px(86)); setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            number = text("", 18, TvTheme.MUTED, true); addView(number, new LayoutParams(px(48), LayoutParams.WRAP_CONTENT));
            logo = new FrameLayout(getContext()); LayoutParams logoLp = new LayoutParams(px(64), px(48)); logoLp.rightMargin = px(16); addView(logo, logoLp);
            LinearLayout labels = new LinearLayout(getContext()); labels.setOrientation(VERTICAL);
            LinearLayout title = new LinearLayout(getContext()); title.setGravity(Gravity.CENTER_VERTICAL);
            name = text("", 23, TvTheme.TEXT, true); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.END);
            title.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            time = text("", 13, TvTheme.MUTED, false); time.setGravity(Gravity.END); title.addView(time, new LayoutParams(px(116), LayoutParams.WRAP_CONTENT));
            labels.addView(title);
            programme = text("", 18, TvTheme.MUTED, false); programme.setSingleLine(true); programme.setEllipsize(TextUtils.TruncateAt.END); labels.addView(programme, top(4));
            rowProgress = progress(); LayoutParams bar = new LayoutParams(LayoutParams.MATCH_PARENT, px(3)); bar.topMargin = px(8); labels.addView(rowProgress, bar);
            addView(labels, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
        void highlight() {
            boolean active = Objects.equals(boundId, selectedId);
            setBackground(active ? selected : idle); number.setTextColor(active ? TvTheme.BLUE_LIGHT : TvTheme.MUTED);
        }
        void bind(Channel channel) {
            boundId = channel.id; highlight(); number.setText(String.format(Locale.ROOT, "%03d", channel.number));
            name.setText(channel.name + (channel.favourite ? "  ★" : ""));
            if (!channel.name.equals(logoName)) {
                logoName = channel.name; logo.removeAllViews();
                logo.addView(actions.logo(channel.name), new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            long now = System.currentTimeMillis(); EpgGuide.Event event = guide == null ? null : guide.current(channel.name, now);
            programme.setText(event == null ? "Телевизия на живо" : event.title); time.setText(event == null ? "" : times(event));
            rowProgress.setVisibility(event == null ? INVISIBLE : VISIBLE); rowProgress.setProgress(progressOf(event, now));
            setContentDescription("Канал " + channel.number + ", " + channel.name + ", " + programme.getText());
        }
    }
}
