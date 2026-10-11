package com.example.tvsites;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

public class MainActivity extends Activity {

    // Сайтове в менюто: {етикет, адрес}
    private static final String[][] SITES = {
            {"IPTV-BG", "https://iptv-bg.com/"},
            {"GledaiTV", "https://www.gledaitv.fan/"},
            {"FilmiZip", "https://filmizip.com/"},
            {"Filmi9", "https://filmi9.com/"},
    };

    // Рекламни и проследяващи домейни. Блокират се заедно с поддомейните им.
    // Допълнително можеш да блокираш домейни от журнала (дълго задържане на OK или бутон Menu).
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

    // JavaScript: пуска или спира първото видео в страницата.
    private static final String TOGGLE_JS =
            "(function(){var v=document.querySelector('video');"
                    + "if(v){if(v.paused){v.play();}else{v.pause();}return 'ok';}"
                    + "return 'none';})()";

    private static final int CURSOR_SIZE = 40;

    private FrameLayout root;
    private LinearLayout menu;
    private WebView web;
    private WebChromeClient chromeClient;
    private CursorView cursor;
    private float cx, cy;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String allowedHost;
    private boolean exiting;
    private boolean clearHistoryPending;
    private boolean okLongHandled;

    // Журнал на домейните и списъци, които потребителят променя.
    private final HashMap<String, Integer> hostCounts = new HashMap<>();
    private final CopyOnWriteArraySet<String> userBlocked = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<String> userAllowed = new CopyOnWriteArraySet<>();
    private SharedPreferences prefs;
    private LinearLayout logPanel;
    private ListView logList;
    private ArrayAdapter<String> logAdapter;
    private final ArrayList<String> logHosts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("tvsites", MODE_PRIVATE);
        loadLists();
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        buildMenu();
        hideSystemBars();
    }

    // ---------- Меню ----------

    private void buildMenu() {
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
        menu.setBackgroundColor(Color.parseColor("#111827"));

        TextView title = new TextView(this);
        title.setText("Избери сайт");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 32);
        menu.addView(title);

        boolean first = true;
        for (final String[] site : SITES) {
            Button b = new Button(this);
            b.setText(site[0]);
            b.setTextSize(22);
            b.setAllCaps(false);
            b.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(720, 100);
            lp.setMargins(0, 0, 0, 16);
            b.setOnClickListener(v -> openSite(site[1]));
            menu.addView(b, lp);
            if (first) {
                b.requestFocus();
                first = false;
            }
        }

        // Бутон Изход: затваря приложението напълно.
        Button exit = new Button(this);
        exit.setText("Изход");
        exit.setTextSize(22);
        exit.setAllCaps(false);
        exit.setFocusable(true);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(720, 100);
        elp.setMargins(0, 24, 0, 0);
        exit.setOnClickListener(v -> exitApp());
        menu.addView(exit, elp);

        root.addView(menu, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void openSite(String url) {
        allowedHost = Uri.parse(url).getHost();
        clearHistoryPending = false;
        synchronized (hostCounts) {
            hostCounts.clear();
        }
        if (web == null) {
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
                    if (host != null) {
                        synchronized (hostCounts) {
                            Integer c = hostCounts.get(host);
                            hostCounts.put(host, c == null ? 1 : c + 1);
                        }
                    }
                    // Празен отговор за блокираните домейни (скриптове, рамки, картинки).
                    if (isBlocked(host)) {
                        return new WebResourceResponse("text/plain", "utf-8",
                                new java.io.ByteArrayInputStream(new byte[0]));
                    }
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

            // Курсорът се рисува върху страницата и се управлява със стрелките.
            cursor = new CursorView(this);
            root.addView(cursor, new FrameLayout.LayoutParams(CURSOR_SIZE, CURSOR_SIZE));

            // Журналът е най-отгоре.
            buildLogPanel();
        }
        menu.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);

        // Курсорът започва в средата на екрана.
        cx = (root.getWidth() > 0 ? root.getWidth() : 1920) / 2f;
        cy = (root.getHeight() > 0 ? root.getHeight() : 1080) / 2f;
        updateCursor();
        updateState();

        web.loadUrl(url);
        web.requestFocus();
    }

    private static String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    // Връща към менюто и спира всичко, което върви в страницата.
    private void backToMenu() {
        if (logPanel != null) logPanel.setVisibility(View.GONE);
        if (customView != null && chromeClient != null) chromeClient.onHideCustomView();
        if (web != null) {
            clearHistoryPending = true;
            web.loadUrl("about:blank");
            web.setVisibility(View.GONE);
        }
        menu.setVisibility(View.VISIBLE);
        updateState();
        if (menu.getChildCount() > 1) menu.getChildAt(1).requestFocus();
    }

    // Пълно затваряне: приложението и процесът му се прекратяват.
    private void exitApp() {
        exiting = true;
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
        }
        finishAndRemoveTask();
    }

    private boolean webActive() {
        return web != null && web.getVisibility() == View.VISIBLE
                && customView == null && !logShown();
    }

    private void updateState() {
        if (cursor != null) cursor.setVisibility(webActive() ? View.VISIBLE : View.GONE);
        // Докато гледаш, екранът не заспива.
        boolean inSite = customView != null || (web != null && web.getVisibility() == View.VISIBLE);
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

    // ---------- Журнал на домейните ----------

    private void buildLogPanel() {
        logPanel = new LinearLayout(this);
        logPanel.setOrientation(LinearLayout.VERTICAL);
        logPanel.setBackgroundColor(Color.parseColor("#F2111827"));
        logPanel.setPadding(80, 40, 80, 40);

        TextView t = new TextView(this);
        t.setText("Домейни, към които страницата праща заявки");
        t.setTextColor(Color.WHITE);
        t.setTextSize(26);
        logPanel.addView(t);

        TextView hint = new TextView(this);
        hint.setText("OK = блокирай / отблокирай   Назад = затвори   Червените са блокирани."
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
                String host = pos < logHosts.size() ? logHosts.get(pos) : "";
                if (host.isEmpty()) {
                    tv.setTextColor(Color.parseColor("#93C5FD"));
                } else if (isBlocked(host)) {
                    tv.setTextColor(Color.parseColor("#F87171"));
                } else {
                    tv.setTextColor(Color.WHITE);
                }
                return tv;
            }
        };
        logList.setAdapter(logAdapter);
        logList.setOnItemClickListener((p, v, pos, id) -> onLogItem(pos));
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
        refreshLog(1);
        logPanel.setVisibility(View.VISIBLE);
        updateState();
        logList.requestFocus();
    }

    private void hideLog() {
        if (logPanel != null) logPanel.setVisibility(View.GONE);
        if (web != null) web.requestFocus();
        updateState();
    }

    private void refreshLog(int keepPos) {
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>();
        synchronized (hostCounts) {
            for (Map.Entry<String, Integer> en : hostCounts.entrySet()) {
                list.add(new AbstractMap.SimpleEntry<String, Integer>(en.getKey(), en.getValue()));
            }
        }
        Collections.sort(list, (a, b) -> b.getValue() - a.getValue());

        logHosts.clear();
        ArrayList<String> rows = new ArrayList<>();
        logHosts.add("");
        rows.add("↻  Презареди страницата");
        int n = 0;
        for (Map.Entry<String, Integer> en : list) {
            if (n++ >= 80) break;
            String h = en.getKey();
            String tag = isOwnHost(h) ? "   [сайтът]" : (isBlocked(h) ? "   [БЛОКИРАН]" : "");
            logHosts.add(h);
            rows.add(h + "   x" + en.getValue() + tag);
        }
        logAdapter.clear();
        logAdapter.addAll(rows);
        logList.setSelection(Math.max(0, Math.min(keepPos, rows.size() - 1)));
    }

    private void onLogItem(int pos) {
        if (pos < 0 || pos >= logHosts.size()) return;
        String host = logHosts.get(pos);
        if (host.isEmpty()) {
            hideLog();
            web.reload();
            return;
        }
        if (isOwnHost(host)) {
            Toast.makeText(this, "Това е самият сайт - не се блокира", Toast.LENGTH_SHORT).show();
            return;
        }
        toggleBlock(host);
        refreshLog(pos);
    }

    // ---------- Курсор, управляван от дистанционното ----------

    private void updateCursor() {
        if (cursor == null) return;
        cursor.setX(cx - CURSOR_SIZE / 2f);
        cursor.setY(cy - CURSOR_SIZE / 2f);
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
    }

    private void tapAt(final View target, final float x, final float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(down);
        down.recycle();
        final long downTime = t;
        target.postDelayed(() -> {
            MotionEvent up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP, x, y, 0);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            target.dispatchTouchEvent(up);
            up.recycle();
        }, 60);
    }

    private void tapCenter() {
        View target = customView != null ? customView : web;
        if (target == null) return;
        tapAt(target, target.getWidth() / 2f, target.getHeight() / 2f);
    }

    private void togglePlayback() {
        if (web == null) return;
        web.evaluateJavascript(TOGGLE_JS, value -> {
            // Ако няма <video> в самата страница (плеърът е в рамка), чукаме в средата.
            if (value != null && value.contains("none")) tapCenter();
        });
    }

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

        // Видеото е на цял екран: OK пуска или спира.
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
                    if (!okLongHandled && !e.isCanceled()) tapAt(web, cx, cy);
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
