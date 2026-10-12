package com.example.tvsites;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    // Хранилището в GitHub, откъдето се проверяват новите версии.
    private static final String UPDATE_REPO = "Bonevv/tvsites";

    // Адрес на текстов файл с настройки (сайтове и блокирани домейни).
    // Остави го празен, ако не искаш да го ползваш. Пример за формата: tvsites-example.txt
    private static final String CONFIG_URL = "";

    // Сайтове по подразбиране: {етикет, адрес, режим, група}. Режим: "" | "mobile" | "desktop"
    private static final String[][] DEFAULT_SITES = {
            {"GledaiTV", "https://www.gledaitv.fan/", "", "Телевизия"},
            {"GledaiTV Live", "https://www.gledaitv.live/", "", "Телевизия"},
            {"IPTV-BG", "https://iptv-bg.com/", "", "Телевизия"},
            {"FilmiZip", "https://filmizip.com/", "", "Филми"},
            {"Filmi9", "https://filmi9.com/", "", "Филми"},
            {"ZenoPlay", "https://zenoplay.to/movies/", "", "Филми"},
            {"Всички филми", "https://vsichkifilmi.net/", "", "Филми"},
            {"BG Radio", "https://bg-radio.org/", "", "Радио"},
            {"RadiosBG", "https://www.radiosbg.com/", "", "Радио"},
    };

    // Вграден списък с рекламни и проследяващи домейни (заедно с поддомейните им).
    private static final String[] BLOCKED = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "google-analytics.com", "googletagmanager.com",
            "googletagservices.com", "adsystem.com", "amazon-adsystem.com",
            "trafficstars.com", "tsyndicate.com", "popads.net", "popcash.net",
            "propellerads.com", "propellerclick.com", "exoclick.com", "exosrv.com",
            "juicyads.com", "adsterra.com", "highperformanceformat.com",
            "onclickads.net", "ad-maven.com", "admaven.com", "clickadu.com",
            "hilltopads.net", "trafficjunky.net", "adnxs.com", "taboola.com",
            "outbrain.com", "criteo.com", "pubmatic.com", "rubiconproject.com",
            "openx.net", "adform.net", "smartadserver.com", "mgid.com",
            "revcontent.com", "adcash.com", "bidvertiser.com", "a-ads.com",
            "monetag.com", "richpush.co", "pushame.com", "realsrv.com",
            "an.yandex.ru", "mc.yandex.ru", "ads.yahoo.com", "adskeeper.com", "adsco.re",
            "adapex.io", "adsrvr.org", "casalemedia.com", "bidswitch.net",
            "sharethrough.com", "sovrn.com", "33across.com", "id5-sync.com",
            "contextweb.com", "teads.tv", "onetag-sys.com",
    };

    private static final String MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // JavaScript: пуска или спира първото видео в страницата.
    private static final String TOGGLE_JS =
            "(function(){var v=document.querySelector('video');"
                    + "if(v){if(v.paused){v.play();}else{v.pause();}return 'ok';}"
                    + "return 'none';})()";

    // Цветове
    private static final int C_CARD = 0xFF1B2142;
    private static final int C_CARD_FOCUS = 0xFF2E3578;
    private static final int C_ACCENT = 0xFF8B7CFF;
    private static final int C_MUTED = 0xFF9AA4D0;
    private static final int C_DANGER = 0xFFF87171;
    private static final int[] BADGES = {
            0xFF6366F1, 0xFFEC4899, 0xFF10B981, 0xFFF59E0B,
            0xFF06B6D4, 0xFFEF4444, 0xFF8B5CF6,
    };
    private static final int C_AMBER = 0xFFFFB020;
    private static final int COLS = 3;

    // Над този размер на кеша приложението пита дали да го изчисти.
    private static final long CACHE_ASK_BYTES = 150L * 1024 * 1024;
    private static final long CACHE_ASK_EVERY_MS = 6L * 60 * 60 * 1000;
    private TextView cacheBtn;
    private final HashSet<String> iconBusy = new HashSet<>();

    private static final int CURSOR_SIZE = 40;
    private static final int CURSOR_HIDE_MS = 4000;

    // ---- екрани ----
    private FrameLayout root;
    private ScrollView menuHolder;
    private LinearLayout menu;
    private WebView web;
    private WebChromeClient chromeClient;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    // ---- курсор ----
    private CursorView cursor;
    private float cx, cy;
    private boolean cursorIdle;
    private boolean cursorEnabled = true;
    private boolean okLongHandled;
    private final HashSet<String> cursorOff = new HashSet<>();
    private final Runnable hideCursor = new Runnable() {
        @Override
        public void run() {
            cursorIdle = true;
            updateState();
        }
    };

    // ---- състояние ----
    private String allowedHost;
    private boolean exiting;
    private boolean clearHistoryPending;
    private volatile String pageUrl;
    private String resumeUrl;
    private SharedPreferences prefs;
    private final ArrayList<String[]> sites = new ArrayList<>();

    // ---- обновяване ----
    private volatile boolean updCancel;
    private File pendingApk;

    // ---- блокиране ----
    private final CopyOnWriteArraySet<String> userBlocked = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<String> userAllowed = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<String> cfgBlocked = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<String> cfgAllowed = new CopyOnWriteArraySet<>();

    // ---- журнал, статистика, намерени видео адреси ----
    private final HashMap<String, Integer> hostCounts = new HashMap<>();
    private final AtomicInteger reqTotal = new AtomicInteger();
    private final AtomicInteger reqBlocked = new AtomicInteger();
    private final LinkedHashMap<String, HashMap<String, String>> mediaPlaylists = new LinkedHashMap<>();
    private final LinkedHashMap<String, HashMap<String, String>> mediaFiles = new LinkedHashMap<>();
    private LinearLayout logPanel;
    private ListView logList;
    private ArrayAdapter<String> logAdapter;
    private final ArrayList<String> logKeys = new ArrayList<>();
    private TextView statsView;
    private boolean statsOn;
    private final Runnable statsTick = new Runnable() {
        @Override
        public void run() {
            updateStats();
            if (statsOn) root.postDelayed(this, 2000);
        }
    };

    // ---- родно пускане ----
    private FrameLayout nativeLayer;
    private SurfaceView nativeSurface;
    private TextView nativeStatus;
    private ExoPlayer player;
    private final Runnable hideNativeStatus = new Runnable() {
        @Override
        public void run() {
            if (nativeStatus != null) nativeStatus.setVisibility(View.GONE);
        }
    };
    private final Player.Listener nativeListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_BUFFERING) {
                showNativeStatus("Зареждане...", 0);
            } else if (state == Player.STATE_READY) {
                showNativeStatus(null, 0);
            } else if (state == Player.STATE_ENDED) {
                showNativeStatus("Края на видеото. Назад = към страницата", 0);
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            showNativeStatus("Не успя да се пусне този поток (" + error.getErrorCodeName()
                    + "). Назад = към страницата", 0);
        }
    };

    // Лек ефект при фокус върху бутон в менюто.
    private final View.OnFocusChangeListener focusAnim = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(120).start();
        }
    };

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("tvsites", MODE_PRIVATE);
        loadLists();

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        menuHolder = new ScrollView(this);
        menuHolder.setFillViewport(true);
        menuHolder.setClipChildren(false);
        menuHolder.setClipToPadding(false);
        menuHolder.setVerticalScrollBarEnabled(false);
        menuHolder.setBackground(new GlowDrawable());
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER_VERTICAL);
        menu.setClipChildren(false);
        menu.setClipToPadding(false);
        menu.setPadding(dp(56), dp(36), dp(56), dp(36));
        menuHolder.addView(menu, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(menuHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Настройки от последното успешно изтегляне или сайтовете по подразбиране.
        String cached = prefs.getString("config", null);
        if (cached != null) {
            applyConfig(cached);
        } else {
            useDefaultSites();
            fillMenu();
        }
        fetchConfig();
        hideSystemBars();
        maybeAskClearCache();
    }

    // ---------- Настройки от текстов файл ----------

    private void useDefaultSites() {
        sites.clear();
        for (String[] s : DEFAULT_SITES) sites.add(s);
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            if (bo.size() > 500000) break;
        }
        in.close();
        return bo.toString("UTF-8");
    }

    private void fetchConfig() {
        if (CONFIG_URL.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(CONFIG_URL).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    if (c.getResponseCode() != 200) return;
                    final String text = readAll(c.getInputStream());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            prefs.edit().putString("config", text).apply();
                            applyConfig(text);
                        }
                    });
                } catch (Exception ignored) {
                    // няма връзка: остават последните запазени настройки
                }
            }
        }).start();
    }

    private void applyConfig(String text) {
        ArrayList<String[]> newSites = new ArrayList<>();
        cfgBlocked.clear();
        cfgAllowed.clear();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int i = line.indexOf(':');
            if (i < 0) continue;
            String key = line.substring(0, i).trim().toLowerCase(Locale.ROOT);
            String val = line.substring(i + 1).trim();
            if (key.equals("site")) {
                String[] p = val.split("\\|");
                if (p.length >= 2) {
                    String name = p[0].trim();
                    String url = p[1].trim();
                    String mode = p.length >= 3 ? p[2].trim().toLowerCase(Locale.ROOT) : "";
                    String group = p.length >= 4 ? p[3].trim() : "";
                    if (!name.isEmpty() && url.startsWith("http")) {
                        newSites.add(new String[]{name, url, mode, group});
                    }
                }
            } else if (key.equals("block")) {
                if (!val.isEmpty()) cfgBlocked.add(val.toLowerCase(Locale.ROOT));
            } else if (key.equals("allow")) {
                if (!val.isEmpty()) cfgAllowed.add(val.toLowerCase(Locale.ROOT));
            }
        }
        if (newSites.isEmpty()) {
            useDefaultSites();
        } else {
            sites.clear();
            sites.addAll(newSites);
        }
        fillMenu();
    }

    // ---------- Меню ----------

    private String[] findSite(String url, String name) {
        for (String[] s : sites) {
            if (s[1].equals(url)) return s;
        }
        return new String[]{name, url, "", ""};
    }

    private static String hostOf(String url) {
        String h = Uri.parse(url).getHost();
        return h == null ? url : stripWww(h);
    }

    private static int badgeColor(String name) {
        return BADGES[(name.hashCode() & 0x7fffffff) % BADGES.length];
    }

    private static String initial(String name) {
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String installedName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    @SuppressWarnings("deprecation")
    private int installedCode() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private LinearLayout.LayoutParams rowLp() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(dp(8), dp(8), dp(8), dp(8));
        return lp;
    }

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(8), 0, 0, dp(20));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.addView(logo, new LinearLayout.LayoutParams(dp(76), dp(76)));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(20), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("ТВ Сайтове");
        title.setTextColor(Color.WHITE);
        title.setTextSize(38);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.getPaint().setShader(new LinearGradient(0, 0, dp(300), 0,
                new int[]{0xFFFFFFFF, 0xFFFFD27A}, null, Shader.TileMode.CLAMP));
        TextView sub = new TextView(this);
        sub.setText("Телевизия и филми на големия екран");
        sub.setTextColor(0xFFC9C3F5);
        sub.setTextSize(16);
        t.addView(title);
        t.addView(sub);
        h.addView(t);
        return h;
    }

    private View makeSectionLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text.toUpperCase(Locale.forLanguageTag("bg")));
        t.setTextColor(C_MUTED);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.15f);
        t.setPadding(dp(10), dp(20), 0, dp(4));
        return t;
    }

    private StateListDrawable stateBg(int normalColor, int focusColor, int strokeColor) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(dp(16));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(focusColor);
        focused.setCornerRadius(dp(16));
        focused.setStroke(dp(3), strokeColor);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    private StateListDrawable heroBg() {
        GradientDrawable normal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF5B3FD6, 0xFFB5179E});
        normal.setCornerRadius(dp(16));
        GradientDrawable focused = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF7358F0, 0xFFD62BB8});
        focused.setCornerRadius(dp(16));
        focused.setStroke(dp(3), Color.WHITE);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    // Полупрозрачна "стъклена" карта; на фокус получава кехлибарена рамка.
    private StateListDrawable glassBg() {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x2EFFFFFF);
        normal.setCornerRadius(dp(20));
        normal.setStroke(dp(1), 0x33FFFFFF);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x4DFFFFFF);
        focused.setCornerRadius(dp(20));
        focused.setStroke(dp(3), C_AMBER);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    // Плочка с иконата на сайта; докато я няма, показва буква на цветен фон.
    private View makeIconTile(String host, int color, String letter, int sizeDp) {
        final FrameLayout tile = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{lighter(color), color});
        bg.setCornerRadius(dp(16));
        tile.setBackground(bg);

        final TextView t = new TextView(this);
        t.setText(letter);
        t.setTextColor(Color.WHITE);
        t.setTextSize(sizeDp / 3);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        tile.addView(t, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(dp(10), dp(10), dp(10), dp(10));
        iv.setVisibility(View.GONE);
        tile.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (host != null) loadIcon(host, tile, iv, t);
        return tile;
    }

    private static int lighter(int c) {
        int r = Math.min(255, ((c >> 16) & 0xFF) + 50);
        int g = Math.min(255, ((c >> 8) & 0xFF) + 50);
        int b = Math.min(255, (c & 0xFF) + 50);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void showIcon(FrameLayout tile, ImageView iv, TextView letter, Bitmap bmp) {
        GradientDrawable white = new GradientDrawable();
        white.setColor(0xFFF4F5FF);
        white.setCornerRadius(dp(16));
        tile.setBackground(white);
        iv.setImageBitmap(bmp);
        iv.setVisibility(View.VISIBLE);
        letter.setVisibility(View.GONE);
    }

    // Иконата се сваля веднъж и се пази във файловете на приложението (не в кеша).
    private void loadIcon(final String host, final FrameLayout tile, final ImageView iv,
                          final TextView letter) {
        final File f = new File(new File(getFilesDir(), "icons"), host + ".png");
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) {
                showIcon(tile, iv, letter, b);
                return;
            }
        }
        synchronized (iconBusy) {
            if (!iconBusy.add(host)) return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(
                            "https://www.google.com/s2/favicons?domain=" + host + "&sz=128")
                            .openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    if (c.getResponseCode() == 200) {
                        InputStream in = c.getInputStream();
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = in.read(buf)) > 0 && bo.size() < 400000) bo.write(buf, 0, n);
                        in.close();
                        byte[] data = bo.toByteArray();
                        bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                        if (bmp != null) {
                            f.getParentFile().mkdirs();
                            FileOutputStream fo = new FileOutputStream(f);
                            fo.write(data);
                            fo.close();
                        }
                    }
                } catch (Exception ignored) {
                }
                final Bitmap result = bmp;
                if (result != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isFinishing()) showIcon(tile, iv, letter, result);
                        }
                    });
                }
            }
        }).start();
    }

    private View makeCard(String title, String subtitle, int badgeColor, String badgeText,
                          boolean hero, String host, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setMinimumHeight(dp(hero ? 104 : 96));
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(hero ? heroBg() : glassBg());

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                dp(hero ? 68 : 64), dp(hero ? 68 : 64));
        blp.setMargins(0, 0, dp(18), 0);
        card.addView(makeIconTile(host, hero ? 0xFF7C3AED : badgeColor, badgeText,
                hero ? 68 : 64), blp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tt = new TextView(this);
        tt.setText(title);
        tt.setTextColor(Color.WHITE);
        tt.setTextSize(20);
        tt.setTypeface(Typeface.DEFAULT_BOLD);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        TextView st = new TextView(this);
        st.setText(subtitle);
        st.setTextColor(hero ? 0xCCFFFFFF : C_MUTED);
        st.setTextSize(13);
        st.setSingleLine(true);
        st.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(tt);
        texts.addView(st);
        card.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        card.setOnFocusChangeListener(focusAnim);
        return card;
    }

    private View makeButton(String text, boolean danger, final Runnable action) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(18);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(18), dp(16), dp(18));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(danger
                ? stateBg(0xFF3A1D2A, 0xFF5B2437, C_DANGER)
                : stateBg(0xFF161B38, C_CARD_FOCUS, C_ACCENT));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setOnFocusChangeListener(focusAnim);
        return b;
    }

    private void fillMenu() {
        menu.removeAllViews();
        menu.addView(buildHeader());

        // Продължи от последния сайт.
        String lastUrl = prefs.getString("lastUrl", null);
        String lastName = prefs.getString("lastName", null);
        if (lastUrl != null && lastName != null) {
            final String[] last = findSite(lastUrl, lastName);
            LinearLayout row = newRow();
            row.addView(makeCard("Продължи: " + last[0], "Последно отворен сайт",
                    0, "▶", true, hostOf(last[1]), new Runnable() {
                        @Override
                        public void run() {
                            openSite(last);
                        }
                    }), cellLp());
            menu.addView(row, rowLp());
        }

        // Групи от сайтове.
        LinkedHashMap<String, ArrayList<String[]>> groups = new LinkedHashMap<>();
        for (String[] s : sites) {
            String g = (s.length > 3 && !s[3].isEmpty()) ? s[3] : "Сайтове";
            ArrayList<String[]> l = groups.get(g);
            if (l == null) {
                l = new ArrayList<>();
                groups.put(g, l);
            }
            l.add(s);
        }
        for (Map.Entry<String, ArrayList<String[]>> en : groups.entrySet()) {
            menu.addView(makeSectionLabel(en.getKey()));
            ArrayList<String[]> list = en.getValue();
            LinearLayout row = null;
            for (int i = 0; i < list.size(); i++) {
                if (i % COLS == 0) {
                    row = newRow();
                    menu.addView(row, rowLp());
                }
                final String[] site = list.get(i);
                row.addView(makeCard(site[0], hostOf(site[1]), badgeColor(site[0]),
                        initial(site[0]), false, hostOf(site[1]), new Runnable() {
                            @Override
                            public void run() {
                                openSite(site);
                            }
                        }), cellLp());
            }
            int rem = list.size() % COLS;
            if (rem != 0 && row != null) {
                for (int k = rem; k < COLS; k++) row.addView(new View(this), cellLp());
            }
        }

        // Бутони най-долу.
        LinearLayout bottom = newRow();
        bottom.addView(makeButton("↻  Провери за актуализации", false, new Runnable() {
            @Override
            public void run() {
                checkUpdate();
            }
        }), cellLp());
        cacheBtn = (TextView) makeButton("♻  Изчисти кеша", false, new Runnable() {
            @Override
            public void run() {
                clearCacheNow();
            }
        });
        bottom.addView(cacheBtn, cellLp());
        bottom.addView(makeButton("⏻  Изход", true, new Runnable() {
            @Override
            public void run() {
                exitApp();
            }
        }), cellLp());
        refreshCacheLabel();
        LinearLayout.LayoutParams blp = rowLp();
        blp.topMargin = dp(20);
        menu.addView(bottom, blp);

        TextView ver = new TextView(this);
        ver.setText("Версия " + installedName());
        ver.setTextColor(0xFF5B6490);
        ver.setTextSize(13);
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(16), 0, 0);
        menu.addView(ver);

        menu.post(new Runnable() {
            @Override
            public void run() {
                if (menuHolder.getVisibility() == View.VISIBLE) menu.requestFocus();
            }
        });
    }

    // ---------- Кеш ----------

    private static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long s = 0;
        File[] l = f.listFiles();
        if (l != null) {
            for (File c : l) s += dirSize(c);
        }
        return s;
    }

    private static void deleteTree(File f, boolean keepSelf) {
        if (f == null || !f.exists()) return;
        File[] l = f.listFiles();
        if (l != null) {
            for (File c : l) deleteTree(c, false);
        }
        if (!keepSelf) f.delete();
    }

    private static String mb(long bytes) {
        return Math.round(bytes / 1048576.0) + " МБ";
    }

    // Смята размера на заден план и го показва върху бутона.
    private void refreshCacheLabel() {
        final TextView btn = cacheBtn;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long size = dirSize(getCacheDir());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (btn != null) btn.setText("♻  Изчисти кеша (" + mb(size) + ")");
                    }
                });
            }
        }).start();
    }

    private void clearCacheNow() {
        final long before = dirSize(getCacheDir());
        if (web != null) web.clearCache(true);
        // Старият свален файл за актуализация вече не трябва, освен ако чака инсталиране.
        File[] l = getCacheDir().listFiles();
        if (l != null) {
            for (File c : l) {
                if (c.getName().equals("WebView")) continue;
                if (c.getName().equals("updates") && pendingApk != null) continue;
                deleteTree(c, false);
            }
        }
        if (web == null) deleteTree(new File(getCacheDir(), "WebView"), false);
        long after = dirSize(getCacheDir());
        Toast.makeText(this, "Изчистено: " + mb(Math.max(0, before - after))
                + " (остава " + mb(after) + ")", Toast.LENGTH_LONG).show();
        refreshCacheLabel();
    }

    // Когато кешът е голям, пита дали да се изчисти (най-много веднъж на няколко часа).
    private void maybeAskClearCache() {
        long last = prefs.getLong("cacheAsk", 0);
        if (System.currentTimeMillis() - last < CACHE_ASK_EVERY_MS) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long size = dirSize(getCacheDir());
                if (size < CACHE_ASK_BYTES) return;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || menuHolder.getVisibility() != View.VISIBLE) return;
                        prefs.edit().putLong("cacheAsk", System.currentTimeMillis()).apply();
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Кешът е голям")
                                .setMessage("Заема " + mb(size) + ". Да се изчисти ли? "
                                        + "Няма да изтрие сайтовете и входовете ти.")
                                .setPositiveButton("Изчисти", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        clearCacheNow();
                                    }
                                })
                                .setNegativeButton("Не сега", null)
                                .show();
                    }
                });
            }
        }).start();
    }

    // Меко "светещо" фоново оформление на менюто.
    private static class GlowDrawable extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(Canvas c) {
            int w = getBounds().width();
            int h = getBounds().height();
            p.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{0xFF140C3A, 0xFF241261, 0xFF0E1A55}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            blob(c, w * 0.92f, h * 0.05f, Math.max(w, h) * 0.55f, 0x88D946A8);
            blob(c, w * 0.05f, h * 0.95f, Math.max(w, h) * 0.45f, 0x773B82F6);
            blob(c, w * 0.55f, h * 0.45f, Math.max(w, h) * 0.35f, 0x338B5CF6);
            p.setShader(null);
        }

        private void blob(Canvas c, float x, float y, float r, int color) {
            p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            c.drawCircle(x, y, r, p);
        }

        @Override
        public void setAlpha(int a) {
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    }

    // ---------- Отваряне на сайт ----------

    private void openSite(String[] site) {
        String url = site[1];
        String mode = site.length > 2 ? site[2] : "";
        allowedHost = Uri.parse(url).getHost();
        cursorEnabled = allowedHost == null || !cursorOff.contains(allowedHost);
        clearHistoryPending = false;
        pageUrl = null;
        synchronized (hostCounts) {
            hostCounts.clear();
        }
        synchronized (mediaPlaylists) {
            mediaPlaylists.clear();
        }
        synchronized (mediaFiles) {
            mediaFiles.clear();
        }
        reqTotal.set(0);
        reqBlocked.set(0);
        prefs.edit().putString("lastUrl", url).putString("lastName", site[0]).apply();

        if (web == null) createWebView();

        // Мобилна или десктоп версия по сайт.
        String ua = null;
        if ("mobile".equals(mode)) ua = MOBILE_UA;
        else if ("desktop".equals(mode)) ua = DESKTOP_UA;
        web.getSettings().setUserAgentString(ua);

        menuHolder.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);

        // Курсорът започва в средата на екрана.
        cx = (root.getWidth() > 0 ? root.getWidth() : 1920) / 2f;
        cy = (root.getHeight() > 0 ? root.getHeight() : 1080) / 2f;
        updateCursor();
        pokeCursor();

        web.loadUrl(url);
        web.requestFocus();
    }

    private void createWebView() {
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String host = r.getUrl().getHost();
                reqTotal.incrementAndGet();
                if (host != null) {
                    synchronized (hostCounts) {
                        Integer c = hostCounts.get(host);
                        hostCounts.put(host, c == null ? 1 : c + 1);
                    }
                }
                // Празен отговор за блокираните домейни (скриптове, рамки, картинки).
                if (isBlocked(host)) {
                    reqBlocked.incrementAndGet();
                    return new WebResourceResponse("text/plain", "utf-8",
                            new java.io.ByteArrayInputStream(new byte[0]));
                }
                maybeRecordMedia(r);
                return super.shouldInterceptRequest(v, r);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                // Блокира пренасочвания на главната страница към други домейни (реклами).
                String host = r.getUrl().getHost();
                return r.isForMainFrame() && host != null && allowedHost != null
                        && !host.endsWith(stripWww(allowedHost));
            }

            @Override
            public void onPageStarted(WebView v, String u, Bitmap favicon) {
                if (u != null && u.startsWith("http")) {
                    pageUrl = u;
                    // Нова страница: старите видео адреси вече не важат.
                    synchronized (mediaPlaylists) {
                        mediaPlaylists.clear();
                    }
                    synchronized (mediaFiles) {
                        mediaFiles.clear();
                    }
                }
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                // След връщане към менюто чистим историята, за да не се връщаме в празна страница.
                if (clearHistoryPending && "about:blank".equals(u)) {
                    v.clearHistory();
                    clearHistoryPending = false;
                }
            }
        });

        chromeClient = new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                // Цял екран за видеото
                customView = view;
                customCallback = cb;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
                updateState();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                if (customCallback != null) customCallback.onCustomViewHidden();
                updateState();
            }

            @Override
            public boolean onCreateWindow(WebView v, boolean d, boolean g, android.os.Message m) {
                return false; // без изскачащи прозорци
            }
        };
        web.setWebChromeClient(chromeClient);

        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(CURSOR_SIZE, CURSOR_SIZE));

        buildLogPanel();
        buildNativeLayer();
        buildStatsView();
    }

    private static String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    // Връща към менюто и спира всичко, което върви.
    private void backToMenu() {
        closeNative(false);
        if (logPanel != null) logPanel.setVisibility(View.GONE);
        if (customView != null && chromeClient != null) chromeClient.onHideCustomView();
        if (web != null) {
            clearHistoryPending = true;
            web.loadUrl("about:blank");
            web.setVisibility(View.GONE);
        }
        menuHolder.setVisibility(View.VISIBLE);
        updateState();
        fillMenu();
        maybeAskClearCache();
    }

    // Пълно затваряне: приложението и процесът му се прекратяват.
    private void exitApp() {
        exiting = true;
        closeNative(false);
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
        }
        finishAndRemoveTask();
    }

    private boolean nativeActive() {
        return nativeLayer != null && nativeLayer.getVisibility() == View.VISIBLE;
    }

    private boolean webActive() {
        return web != null && web.getVisibility() == View.VISIBLE
                && customView == null && !logShown() && !nativeActive();
    }

    private void updateState() {
        if (cursor != null) {
            cursor.setVisibility(webActive() && cursorEnabled && !cursorIdle
                    ? View.VISIBLE : View.GONE);
        }
        // Докато гледаш, екранът не заспива.
        boolean inSite = customView != null || nativeActive()
                || (web != null && web.getVisibility() == View.VISIBLE);
        if (inSite) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    // ---------- Блокиране на домейни ----------

    private static boolean matches(String host, String d) {
        return host.equals(d) || host.endsWith("." + d);
    }

    private boolean isBlocked(String host) {
        if (host == null) return false;
        for (String a : userAllowed) {
            if (matches(host, a)) return false;
        }
        for (String d : userBlocked) {
            if (matches(host, d)) return true;
        }
        for (String a : cfgAllowed) {
            if (matches(host, a)) return false;
        }
        for (String d : cfgBlocked) {
            if (matches(host, d)) return true;
        }
        for (String d : BLOCKED) {
            if (matches(host, d)) return true;
        }
        return false;
    }

    private boolean isOwnHost(String host) {
        if (allowedHost == null) return false;
        String base = stripWww(allowedHost);
        return host.equals(base) || host.endsWith("." + base);
    }

    private void loadLists() {
        userBlocked.addAll(prefs.getStringSet("blocked", new HashSet<String>()));
        userAllowed.addAll(prefs.getStringSet("allowed", new HashSet<String>()));
        cursorOff.addAll(prefs.getStringSet("cursorOff", new HashSet<String>()));
    }

    private void saveLists() {
        prefs.edit()
                .putStringSet("blocked", new HashSet<String>(userBlocked))
                .putStringSet("allowed", new HashSet<String>(userAllowed))
                .putStringSet("cursorOff", new HashSet<String>(cursorOff))
                .apply();
    }

    private void toggleBlock(String host) {
        if (isBlocked(host)) {
            userBlocked.remove(host);
            userAllowed.add(host);
        } else {
            userAllowed.remove(host);
            userBlocked.add(host);
        }
        saveLists();
    }

    private void toggleCursor() {
        cursorEnabled = !cursorEnabled;
        if (allowedHost != null) {
            if (cursorEnabled) cursorOff.remove(allowedHost);
            else cursorOff.add(allowedHost);
            saveLists();
        }
        Toast.makeText(this, cursorEnabled
                ? "Курсорът е включен: стрелките го местят, OK кликва"
                : "Курсорът е изключен: стрелките местят фокуса по страницата, OK натиска",
                Toast.LENGTH_LONG).show();
        updateState();
    }

    // Запомня адресите на видео потоци, които страницата зарежда.
    private void maybeRecordMedia(WebResourceRequest r) {
        String url = r.getUrl().toString();
        String low = url.toLowerCase(Locale.ROOT);
        LinkedHashMap<String, HashMap<String, String>> target;
        int cap;
        if (low.contains(".m3u8") || low.contains(".mpd")) {
            target = mediaPlaylists;
            cap = 15;
        } else if (low.contains(".mp4")) {
            target = mediaFiles;
            cap = 10;
        } else {
            return;
        }
        HashMap<String, String> h = new HashMap<>();
        Map<String, String> rh = r.getRequestHeaders();
        if (rh != null) {
            for (Map.Entry<String, String> en : rh.entrySet()) {
                String k = en.getKey();
                if (k.equalsIgnoreCase("Referer")) h.put("Referer", en.getValue());
                else if (k.equalsIgnoreCase("Origin")) h.put("Origin", en.getValue());
                else if (k.equalsIgnoreCase("User-Agent")) h.put("User-Agent", en.getValue());
            }
        }
        String pu = pageUrl;
        if (!h.containsKey("Referer") && pu != null) h.put("Referer", pu);
        synchronized (target) {
            if (target.containsKey(url)) return;
            target.put(url, h);
            while (target.size() > cap) {
                String eldest = target.keySet().iterator().next();
                target.remove(eldest);
            }
        }
    }

    // ---------- Журнал на домейните ----------

    private void buildLogPanel() {
        logPanel = new LinearLayout(this);
        logPanel.setOrientation(LinearLayout.VERTICAL);
        logPanel.setBackgroundColor(0xF20B1020);
        logPanel.setPadding(dp(64), dp(32), dp(64), dp(32));

        TextView t = new TextView(this);
        t.setText("Меню на страницата");
        t.setTextColor(Color.WHITE);
        t.setTextSize(28);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        logPanel.addView(t);

        TextView hint = new TextView(this);
        hint.setText("OK = избери   Назад = затвори.   Червените домейни са блокирани."
                + " След промяна избери \"Презареди страницата\".");
        hint.setTextColor(C_MUTED);
        hint.setTextSize(15);
        hint.setPadding(0, dp(6), 0, dp(14));
        logPanel.addView(hint);

        logList = new ListView(this);
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(0x558B7CFF);
        sel.setCornerRadius(dp(10));
        logList.setSelector(sel);
        logList.setDividerHeight(0);
        logAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                new ArrayList<String>()) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, convertView, parent);
                tv.setTextSize(19);
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                String key = pos < logKeys.size() ? logKeys.get(pos) : "";
                if (key.equals("#none")) {
                    tv.setTextColor(0xFF6B7280);
                } else if (key.startsWith("#")) {
                    tv.setTextColor(0xFF93C5FD);
                } else if (isBlocked(key)) {
                    tv.setTextColor(C_DANGER);
                } else {
                    tv.setTextColor(Color.WHITE);
                }
                return tv;
            }
        };
        logList.setAdapter(logAdapter);
        logList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                onLogItem(pos);
            }
        });
        logPanel.addView(logList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        logPanel.setVisibility(View.GONE);
        root.addView(logPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private boolean logShown() {
        return logPanel != null && logPanel.getVisibility() == View.VISIBLE;
    }

    private void showLog() {
        if (web == null || logPanel == null) return;
        refreshLog(0);
        logPanel.setVisibility(View.VISIBLE);
        updateState();
        logList.requestFocus();
    }

    private void hideLog() {
        if (logPanel != null) logPanel.setVisibility(View.GONE);
        if (web != null) web.requestFocus();
        updateState();
    }

    private static String shortName(String url) {
        Uri u = Uri.parse(url);
        String host = u.getHost() == null ? "" : u.getHost();
        String path = u.getPath() == null ? "" : u.getPath();
        int i = path.lastIndexOf('/');
        String tail = i >= 0 ? path.substring(i + 1) : path;
        String s = host + "/" + (tail.isEmpty() ? "..." : tail);
        return s.length() > 70 ? s.substring(0, 67) + "..." : s;
    }

    private void refreshLog(int keepPos) {
        logKeys.clear();
        ArrayList<String> rows = new ArrayList<>();

        logKeys.add("#reload");
        rows.add("↻  Презареди страницата");
        logKeys.add("#cursor");
        rows.add("⌖  Курсор: " + (cursorEnabled
                ? "ВКЛЮЧЕН (стрелките го местят)"
                : "изключен (стрелките местят фокуса по страницата)"));
        logKeys.add("#update");
        rows.add("⬇  Провери за актуализации");
        logKeys.add("#cache");
        rows.add("♻  Изчисти кеша");
        logKeys.add("#stats");
        rows.add("▣  Статистика на екрана: " + (statsOn ? "ВКЛЮЧЕНА" : "изключена"));

        // Намерени видео потоци (най-новите най-отгоре).
        ArrayList<String> pl = new ArrayList<>();
        ArrayList<String> fl = new ArrayList<>();
        synchronized (mediaPlaylists) {
            pl.addAll(mediaPlaylists.keySet());
        }
        synchronized (mediaFiles) {
            fl.addAll(mediaFiles.keySet());
        }
        int shown = 0;
        for (int i = pl.size() - 1; i >= 0 && shown < 6; i--, shown++) {
            logKeys.add("#media:" + pl.get(i));
            rows.add("▶  Родно пускане: " + shortName(pl.get(i)));
        }
        for (int i = fl.size() - 1, k = 0; i >= 0 && k < 4; i--, k++, shown++) {
            logKeys.add("#media:" + fl.get(i));
            rows.add("▶  Родно пускане (файл): " + shortName(fl.get(i)));
        }
        if (shown == 0) {
            logKeys.add("#none");
            rows.add("(още няма намерени видео потоци - пусни канала и отвори менюто пак)");
        }

        // Домейни, към които страницата праща заявки.
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>();
        synchronized (hostCounts) {
            for (Map.Entry<String, Integer> en : hostCounts.entrySet()) {
                list.add(new AbstractMap.SimpleEntry<String, Integer>(en.getKey(), en.getValue()));
            }
        }
        Collections.sort(list, (a, b) -> b.getValue() - a.getValue());
        int n = 0;
        for (Map.Entry<String, Integer> en : list) {
            if (n++ >= 80) break;
            String h = en.getKey();
            String tag = isOwnHost(h) ? "   [сайтът]" : (isBlocked(h) ? "   [БЛОКИРАН]" : "");
            logKeys.add(h);
            rows.add(h + "   x" + en.getValue() + tag);
        }

        logAdapter.clear();
        logAdapter.addAll(rows);
        logList.setSelection(Math.max(0, Math.min(keepPos, rows.size() - 1)));
    }

    private void onLogItem(int pos) {
        if (pos < 0 || pos >= logKeys.size()) return;
        String key = logKeys.get(pos);
        if (key.equals("#reload")) {
            hideLog();
            web.reload();
            return;
        }
        if (key.equals("#cursor")) {
            toggleCursor();
            refreshLog(pos);
            return;
        }
        if (key.equals("#update")) {
            checkUpdate();
            return;
        }
        if (key.equals("#cache")) {
            clearCacheNow();
            return;
        }
        if (key.equals("#stats")) {
            toggleStats();
            refreshLog(pos);
            return;
        }
        if (key.equals("#none")) return;
        if (key.startsWith("#media:")) {
            hideLog();
            playNative(key.substring(7));
            return;
        }
        if (isOwnHost(key)) {
            Toast.makeText(this, "Това е самият сайт - не се блокира", Toast.LENGTH_SHORT).show();
            return;
        }
        toggleBlock(key);
        refreshLog(pos);
    }

    // ---------- Актуализации ----------

    private void showInfo(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    // Проверява само когато потребителят го поиска.
    private void checkUpdate() {
        Toast.makeText(this, "Проверявам за нова версия...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                int latest = 0;
                String apkUrl = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(
                            "https://api.github.com/repos/" + UPDATE_REPO + "/releases/latest")
                            .openConnection();
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    c.setRequestProperty("User-Agent", "TvSites");
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    int rc = c.getResponseCode();
                    if (rc != 200) {
                        err = "GitHub отговори с код " + rc
                                + (rc == 404 ? " (няма публикувана версия или хранилището не е публично)" : "");
                    } else {
                        JSONObject o = new JSONObject(readAll(c.getInputStream()));
                        String digits = o.optString("tag_name", "").replaceAll("[^0-9]", "");
                        latest = digits.isEmpty() ? 0 : Integer.parseInt(digits);
                        JSONArray assets = o.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject a = assets.getJSONObject(i);
                                if (a.optString("name", "").endsWith(".apk")) {
                                    apkUrl = a.optString("browser_download_url", null);
                                    break;
                                }
                            }
                        }
                        if (apkUrl == null) err = "В последната версия няма APK файл.";
                    }
                } catch (Exception e) {
                    err = "Няма връзка или грешка при проверката.";
                }
                final String ferr = err;
                final int flatest = latest;
                final String furl = apkUrl;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ferr != null) {
                            showInfo("Проверка за актуализации", ferr);
                        } else if (flatest > installedCode()) {
                            askUpdate(flatest, furl);
                        } else {
                            showInfo("Проверка за актуализации",
                                    "Имаш последната версия (" + installedName() + ").");
                        }
                    }
                });
            }
        }).start();
    }

    private void askUpdate(final int latest, final String url) {
        new AlertDialog.Builder(this)
                .setTitle("Има нова версия")
                .setMessage("Инсталирана: " + installedName() + "\nНова: 2." + latest
                        + "\n\nДа се свали и инсталира ли?")
                .setPositiveButton("Да, обнови", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        downloadUpdate(url, latest);
                    }
                })
                .setNegativeButton("Не сега", null)
                .show();
    }

    private void downloadUpdate(final String url, final int ver) {
        updCancel = false;
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Сваляне на версия 2." + ver)
                .setMessage("Свалям... 0%")
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        updCancel = true;
                    }
                })
                .create();
        dlg.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                File f = null;
                String err = null;
                try {
                    File dir = new File(getCacheDir(), "updates");
                    dir.mkdirs();
                    f = new File(dir, "update.apk");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestProperty("User-Agent", "TvSites");
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(20000);
                    if (c.getResponseCode() != 200) {
                        throw new java.io.IOException("код " + c.getResponseCode());
                    }
                    final long total = c.getContentLength();
                    InputStream in = c.getInputStream();
                    FileOutputStream out = new FileOutputStream(f);
                    byte[] buf = new byte[16384];
                    long done = 0;
                    long lastUi = 0;
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        if (updCancel) break;
                        out.write(buf, 0, n);
                        done += n;
                        long now = SystemClock.uptimeMillis();
                        if (now - lastUi > 400) {
                            lastUi = now;
                            final String msg = total > 0
                                    ? "Свалям... " + (done * 100 / total) + "%"
                                    : "Свалям... " + (done / 1048576) + " МБ";
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    dlg.setMessage(msg);
                                }
                            });
                        }
                    }
                    out.close();
                    in.close();
                    if (!updCancel && total > 0 && done != total) {
                        throw new java.io.IOException("файлът е свален непълно");
                    }
                } catch (Exception e) {
                    err = "Неуспешно сваляне (" + e.getMessage() + ")";
                }
                final File fin = f;
                final String ferr = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dlg.dismiss();
                        if (updCancel) {
                            if (fin != null) fin.delete();
                        } else if (ferr != null) {
                            showInfo("Актуализация", ferr);
                        } else {
                            installApk(fin);
                        }
                    }
                });
            }
        }).start();
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
    }

    private void installApk(File f) {
        if (f == null || !f.exists()) {
            showInfo("Актуализация", "Файлът за инсталиране липсва.");
            return;
        }
        if (!canInstallPackages()) {
            pendingApk = f;
            new AlertDialog.Builder(this)
                    .setTitle("Нужно е разрешение")
                    .setMessage("Разреши на приложението да инсталира приложения. "
                            + "След като го разрешиш, върни се и инсталирането продължава.")
                    .setPositiveButton("Към настройките", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                startActivity(new Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + getPackageName())));
                            } catch (Exception e) {
                                showInfo("Настройки",
                                        "Отвори ръчно: Настройки, Сигурност, Неизвестни източници, "
                                                + "и разреши за това приложение.");
                            }
                        }
                    })
                    .setNegativeButton("Отказ", null)
                    .show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            showInfo("Актуализация", "Не може да се стартира инсталирането на тази система.");
        }
    }

    // ---------- Статистика ----------

    private void buildStatsView() {
        statsView = new TextView(this);
        statsView.setTextColor(Color.WHITE);
        statsView.setTextSize(14);
        statsView.setBackgroundColor(0x99000000);
        statsView.setPadding(16, 8, 16, 8);
        statsView.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        root.addView(statsView, lp);
    }

    private void toggleStats() {
        statsOn = !statsOn;
        statsView.setVisibility(statsOn ? View.VISIBLE : View.GONE);
        root.removeCallbacks(statsTick);
        if (statsOn) {
            updateStats();
            root.postDelayed(statsTick, 2000);
        }
    }

    private void updateStats() {
        if (statsView == null) return;
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long freeMb = mi.availMem / 1048576L;
        long totalMb = mi.totalMem / 1048576L;
        long appMb = 0;
        try {
            android.os.Debug.MemoryInfo[] pm =
                    am.getProcessMemoryInfo(new int[]{android.os.Process.myPid()});
            if (pm != null && pm.length > 0) appMb = pm[0].getTotalPss() / 1024L;
        } catch (Exception ignored) {
        }
        statsView.setText("Свободна RAM: " + freeMb + " / " + totalMb + " МБ   Приложение: "
                + appMb + " МБ   Заявки: " + reqTotal.get() + " (блок. " + reqBlocked.get() + ")");
    }

    // ---------- Родно пускане (ExoPlayer) ----------

    private void buildNativeLayer() {
        nativeLayer = new FrameLayout(this);
        nativeLayer.setBackgroundColor(Color.BLACK);
        nativeSurface = new SurfaceView(this);
        nativeLayer.addView(nativeSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        nativeStatus = new TextView(this);
        nativeStatus.setTextColor(Color.WHITE);
        nativeStatus.setTextSize(22);
        nativeStatus.setBackgroundColor(0xAA000000);
        nativeStatus.setPadding(24, 12, 24, 12);
        nativeStatus.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.setMargins(40, 0, 0, 40);
        nativeLayer.addView(nativeStatus, lp);
        nativeLayer.setVisibility(View.GONE);
        root.addView(nativeLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showNativeStatus(String msg, int hideAfterMs) {
        if (nativeStatus == null) return;
        nativeStatus.removeCallbacks(hideNativeStatus);
        if (msg == null) {
            nativeStatus.setVisibility(View.GONE);
            return;
        }
        nativeStatus.setText(msg);
        nativeStatus.setVisibility(View.VISIBLE);
        if (hideAfterMs > 0) nativeStatus.postDelayed(hideNativeStatus, hideAfterMs);
    }

    private void playNative(String url) {
        if (web == null) return;

        // Същите заглавки, с които страницата е поискала потока (Referer, Origin, бисквитки).
        HashMap<String, String> h = null;
        synchronized (mediaPlaylists) {
            h = mediaPlaylists.get(url);
        }
        if (h == null) {
            synchronized (mediaFiles) {
                h = mediaFiles.get(url);
            }
        }
        HashMap<String, String> props = new HashMap<>();
        String ua = web.getSettings().getUserAgentString();
        if (h != null) {
            props.putAll(h);
            String fromReq = props.remove("User-Agent");
            if (fromReq != null) ua = fromReq;
        }
        String cookie = CookieManager.getInstance().getCookie(url);
        if (cookie != null && !props.containsKey("Cookie")) props.put("Cookie", cookie);

        // Освобождаваме страницата, за да не товари бокса.
        if (customView != null && chromeClient != null) chromeClient.onHideCustomView();
        resumeUrl = web.getUrl();
        web.loadUrl("about:blank");
        web.setVisibility(View.GONE);

        closeNative(false);
        nativeLayer.setVisibility(View.VISIBLE);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setUserAgent(ua);
        http.setDefaultRequestProperties(props);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .build();
        player.setVideoSurfaceView(nativeSurface);
        player.addListener(nativeListener);

        MediaItem.Builder mib = new MediaItem.Builder().setUri(url);
        String low = url.toLowerCase(Locale.ROOT);
        if (low.contains(".m3u8")) mib.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if (low.contains(".mpd")) mib.setMimeType(MimeTypes.APPLICATION_MPD);
        player.setMediaItem(mib.build());
        player.prepare();
        player.setPlayWhenReady(true);

        showNativeStatus("Зареждане...  (OK = пауза, ◀ ▶ = превъртане, Назад = към страницата)", 5000);
        updateState();
    }

    private void closeNative(boolean backToPage) {
        if (player != null) {
            player.removeListener(nativeListener);
            player.release();
            player = null;
        }
        if (nativeStatus != null) nativeStatus.removeCallbacks(hideNativeStatus);
        boolean wasActive = nativeActive();
        if (nativeLayer != null) nativeLayer.setVisibility(View.GONE);
        if (backToPage && wasActive && web != null) {
            web.setVisibility(View.VISIBLE);
            if (web.canGoBack()) web.goBack();
            else if (resumeUrl != null) web.loadUrl(resumeUrl);
            web.requestFocus();
            pokeCursor();
        }
        updateState();
    }

    // ---------- Курсор, управляван от дистанционното ----------

    private void updateCursor() {
        if (cursor == null) return;
        cursor.setX(cx - CURSOR_SIZE / 2f);
        cursor.setY(cy - CURSOR_SIZE / 2f);
    }

    // Показва курсора и го скрива след няколко секунди без движение.
    private void pokeCursor() {
        cursorIdle = false;
        updateState();
        root.removeCallbacks(hideCursor);
        root.postDelayed(hideCursor, CURSOR_HIDE_MS);
    }

    private void moveCursor(int keyCode, int repeat) {
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) return;
        // Колкото по-дълго държиш бутона, толкова по-бързо се движи.
        float step = 24 + Math.min(repeat, 15) * 8;
        float nx = cx;
        float ny = cy;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:    ny -= step; break;
            case KeyEvent.KEYCODE_DPAD_DOWN:  ny += step; break;
            case KeyEvent.KEYCODE_DPAD_LEFT:  nx -= step; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: nx += step; break;
            default: return;
        }
        // На края на екрана страницата се превърта.
        if (ny < 0) { web.scrollBy(0, (int) ny); ny = 0; }
        if (ny > h - 1) { web.scrollBy(0, (int) (ny - (h - 1))); ny = h - 1; }
        if (nx < 0) { web.scrollBy((int) nx, 0); nx = 0; }
        if (nx > w - 1) { web.scrollBy((int) (nx - (w - 1)), 0); nx = w - 1; }
        cx = nx;
        cy = ny;
        updateCursor();
        pokeCursor();
    }

    private void tapAt(final View target, final float x, final float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(down);
        down.recycle();
        final long downTime = t;
        target.postDelayed(new Runnable() {
            @Override
            public void run() {
                MotionEvent up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP, x, y, 0);
                up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                target.dispatchTouchEvent(up);
                up.recycle();
            }
        }, 60);
    }

    private void tapCenter() {
        View target = customView != null ? customView : web;
        if (target == null) return;
        tapAt(target, target.getWidth() / 2f, target.getHeight() / 2f);
    }

    // OK, когато курсорът е изключен: натиска фокусирания елемент на страницата.
    private void sendOkToWeb() {
        if (web == null) return;
        long t = SystemClock.uptimeMillis();
        web.dispatchKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        web.dispatchKeyEvent(new KeyEvent(t, t + 50, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0));
    }

    private void togglePlayback() {
        if (web == null) return;
        web.evaluateJavascript(TOGGLE_JS, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                // Ако няма <video> в самата страница (плеърът е в рамка), чукаме в средата.
                if (value != null && value.contains("none")) tapCenter();
            }
        });
    }

    // ---------- Управление с дистанционно ----------

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        boolean playKey = code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || code == KeyEvent.KEYCODE_MEDIA_PLAY
                || code == KeyEvent.KEYCODE_MEDIA_PAUSE;
        boolean okKey = code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;

        // Родният плеър: OK = пауза/пускане, наляво/надясно = превъртане.
        if (nativeActive()) {
            if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
            if (down && player != null) {
                if ((okKey || playKey) && e.getRepeatCount() == 0) {
                    boolean play = !player.getPlayWhenReady();
                    player.setPlayWhenReady(play);
                    showNativeStatus(play ? "▶" : "❚❚  Пауза", 1200);
                } else if (code == KeyEvent.KEYCODE_DPAD_LEFT
                        || code == KeyEvent.KEYCODE_MEDIA_REWIND) {
                    player.seekBack();
                } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT
                        || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                    player.seekForward();
                }
            }
            return true;
        }

        // Видеото на страницата е на цял екран: OK пуска или спира.
        if (customView != null) {
            if ((okKey || playKey) && !e.isCanceled()) {
                if (down && e.getRepeatCount() == 0) togglePlayback();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        // Страницата е отворена. С курсор: стрелките го местят, OK е клик.
        // Без курсор: стрелките местят фокуса по страницата, OK натиска.
        // Дълго задържане на OK (или бутон Menu) отваря менюто на страницата.
        if (webActive()) {
            if (code == KeyEvent.KEYCODE_MENU) {
                if (down && e.getRepeatCount() == 0) showLog();
                return true;
            }
            if (cursorEnabled) {
                switch (code) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (down) moveCursor(code, e.getRepeatCount());
                        return true;
                    default:
                        break;
                }
            }
            if (okKey) {
                if (down) {
                    int rc = e.getRepeatCount();
                    if (rc == 0) {
                        okLongHandled = false;
                    } else if (!okLongHandled) {
                        okLongHandled = true;
                        showLog();
                    }
                } else {
                    if (!okLongHandled && !e.isCanceled()) {
                        if (!cursorEnabled) {
                            sendOkToWeb();
                        } else if (cursorIdle) {
                            // Ако курсорът е скрит, първото натискане само го показва.
                            pokeCursor();
                        } else {
                            tapAt(web, cx, cy);
                            pokeCursor();
                        }
                    }
                    okLongHandled = false;
                }
                return true;
            }
            if (playKey) {
                if (down && e.getRepeatCount() == 0) togglePlayback();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (nativeActive()) {
                closeNative(true);
                return true;
            }
            if (logShown()) {
                hideLog();
                return true;
            }
            if (customView != null) {
                chromeClient.onHideCustomView();
                return true;
            }
            if (web != null && web.getVisibility() == View.VISIBLE) {
                if (web.canGoBack()) web.goBack();
                else backToMenu();
                return true;
            }
            // В менюто Назад затваря приложението напълно.
            exitApp();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideSystemBars();
        // Ако чакаме разрешение за инсталиране и вече е дадено, продължаваме.
        if (pendingApk != null && canInstallPackages()) {
            File f = pendingApk;
            pendingApk = null;
            installApk(f);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Когато излезеш (Home), всичко се спира и при връщане започваш от менюто.
        if (!exiting && !isFinishing() && !isChangingConfigurations()) {
            backToMenu();
        }
    }

    @Override
    protected void onDestroy() {
        closeNative(false);
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
        // При Изход прекратяваме и процеса, за да не остава нищо на заден фон.
        if (exiting) {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    // Малък кръг, който служи за курсор.
    private static class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        CursorView(Context c) {
            super(c);
            fill.setColor(0xCCFFFFFF);
            stroke.setColor(Color.BLACK);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(4);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = getWidth() / 2f;
            canvas.drawCircle(r, r, r - 4, fill);
            canvas.drawCircle(r, r, r - 4, stroke);
        }
    }
}
