package com.example.tvsites;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
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
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

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

    // Адрес на текстов файл с настройки (сайтове и блокирани домейни).
    // Остави го празен, ако не искаш да го ползваш. Пример за формата: tvsites-example.txt
    // Пример: "https://gist.githubusercontent.com/ТВОЕТО_ИМЕ/ИДЕНТИФИКАТОР/raw/tvsites.txt"
    private static final String CONFIG_URL = "";

    // Сайтове по подразбиране: {етикет, адрес, режим}. Режим: "" | "mobile" | "desktop"
    private static final String[][] DEFAULT_SITES = {
            {"IPTV-BG", "https://iptv-bg.com/", ""},
            {"GledaiTV", "https://www.gledaitv.fan/", ""},
            {"FilmiZip", "https://filmizip.com/", ""},
            {"Filmi9", "https://filmi9.com/", ""},
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
    private boolean okLongHandled;
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
        menuHolder.setBackgroundColor(Color.parseColor("#111827"));
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
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
    }

    // ---------- Настройки от текстов файл ----------

    private void useDefaultSites() {
        sites.clear();
        for (String[] s : DEFAULT_SITES) sites.add(s);
    }

    private void fetchConfig() {
        if (CONFIG_URL.isEmpty()) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.HttpURLConnection c =
                            (java.net.HttpURLConnection) new java.net.URL(CONFIG_URL).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    if (c.getResponseCode() != 200) return;
                    java.io.InputStream in = c.getInputStream();
                    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0 && bo.size() < 200000) {
                        bo.write(buf, 0, n);
                    }
                    in.close();
                    final String text = bo.toString("UTF-8");
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
                    if (!name.isEmpty() && url.startsWith("http")) {
                        newSites.add(new String[]{name, url, mode});
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

    private Button addMenuButton(String text, int topMargin, final Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(22);
        b.setAllCaps(false);
        b.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(720, 100);
        lp.setMargins(0, topMargin, 0, 16);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        menu.addView(b, lp);
        return b;
    }

    private String[] findSite(String url, String name) {
        for (String[] s : sites) {
            if (s[1].equals(url)) return s;
        }
        return new String[]{name, url, ""};
    }

    private void fillMenu() {
        menu.removeAllViews();

        TextView title = new TextView(this);
        title.setText("Избери сайт");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 32, 0, 32);
        menu.addView(title);

        // Продължи от последния сайт.
        String lastUrl = prefs.getString("lastUrl", null);
        String lastName = prefs.getString("lastName", null);
        if (lastUrl != null && lastName != null) {
            final String[] last = findSite(lastUrl, lastName);
            addMenuButton("▶  Продължи: " + last[0], 0, new Runnable() {
                @Override
                public void run() {
                    openSite(last);
                }
            });
        }

        for (final String[] site : sites) {
            addMenuButton(site[0], 0, new Runnable() {
                @Override
                public void run() {
                    openSite(site);
                }
            });
        }

        // Изход: затваря приложението напълно.
        addMenuButton("Изход", 24, new Runnable() {
            @Override
            public void run() {
                exitApp();
            }
        });

        if (menuHolder.getVisibility() == View.VISIBLE && menu.getChildCount() > 1) {
            menu.getChildAt(1).requestFocus();
        }
    }

    // ---------- Отваряне на сайт ----------

    private void openSite(String[] site) {
        String url = site[1];
        String mode = site.length > 2 ? site[2] : "";
        allowedHost = Uri.parse(url).getHost();
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
            cursor.setVisibility(webActive() && !cursorIdle ? View.VISIBLE : View.GONE);
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
    }

    private void saveLists() {
        prefs.edit()
                .putStringSet("blocked", new HashSet<String>(userBlocked))
                .putStringSet("allowed", new HashSet<String>(userAllowed))
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
        logPanel.setBackgroundColor(Color.parseColor("#F2111827"));
        logPanel.setPadding(80, 40, 80, 40);

        TextView t = new TextView(this);
        t.setText("Журнал");
        t.setTextColor(Color.WHITE);
        t.setTextSize(26);
        logPanel.addView(t);

        TextView hint = new TextView(this);
        hint.setText("OK = избери   Назад = затвори.   Червените домейни са блокирани."
                + " След промяна избери \"Презареди страницата\".");
        hint.setTextColor(Color.parseColor("#9CA3AF"));
        hint.setTextSize(16);
        hint.setPadding(0, 8, 0, 16);
        logPanel.addView(hint);

        logList = new ListView(this);
        logList.setSelector(new ColorDrawable(0x553B82F6));
        logList.setDividerHeight(0);
        logAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                new ArrayList<String>()) {
            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(pos, convertView, parent);
                tv.setTextSize(20);
                tv.setPadding(24, 16, 24, 16);
                String key = pos < logKeys.size() ? logKeys.get(pos) : "";
                if (key.equals("#none")) {
                    tv.setTextColor(Color.parseColor("#6B7280"));
                } else if (key.startsWith("#")) {
                    tv.setTextColor(Color.parseColor("#93C5FD"));
                } else if (isBlocked(key)) {
                    tv.setTextColor(Color.parseColor("#F87171"));
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
        refreshLog(2);
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
            rows.add("(още няма намерени видео потоци - пусни канала и отвори журнала пак)");
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

        // Страницата е отворена: стрелките местят курсора, OK е клик,
        // дълго задържане на OK (или бутон Menu) отваря журнала.
        if (webActive()) {
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
            if (code == KeyEvent.KEYCODE_MENU) {
                if (down && e.getRepeatCount() == 0) showLog();
                return true;
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
                        // Ако курсорът е скрит, първото натискане само го показва.
                        if (cursorIdle) {
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
