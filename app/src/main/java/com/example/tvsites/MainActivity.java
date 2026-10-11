package com.example.tvsites;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    // Сайтове в менюто: {етикет, адрес}
    private static final String[][] SITES = {
            {"IPTV-BG", "https://iptv-bg.com/"},
            {"GledaiTV", "https://www.gledaitv.fan/"},
            {"FilmiZip", "https://filmizip.com/"},
            {"Filmi9", "https://filmi9.com/"},
    };

    // Рекламни и проследяващи домейни. Блокират се заедно с поддомейните им.
    // Добави още тук, ако видиш реклама, която се промъква.
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
    private CursorView cursor;
    private float cx, cy;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private String allowedHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        buildMenu();
        hideSystemBars();
    }

    private void buildMenu() {
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER);
        menu.setBackgroundColor(Color.parseColor("#111827"));

        TextView title = new TextView(this);
        title.setText("Избери сайт");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 48);
        menu.addView(title);

        boolean first = true;
        for (final String[] site : SITES) {
            Button b = new Button(this);
            b.setText(site[0]);
            b.setTextSize(24);
            b.setAllCaps(false);
            b.setFocusable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(720, 140);
            lp.setMargins(0, 0, 0, 32);
            b.setOnClickListener(v -> openSite(site[1]));
            menu.addView(b, lp);
            if (first) {
                b.requestFocus();
                first = false;
            }
        }

        root.addView(menu, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void openSite(String url) {
        allowedHost = Uri.parse(url).getHost();
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
                    // Връща празен отговор за рекламните домейни (скриптове, рамки, картинки).
                    if (isBlocked(r.getUrl().getHost())) {
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
            });

            web.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onShowCustomView(View view, CustomViewCallback cb) {
                    // Цял екран за видеото
                    customView = view;
                    customCallback = cb;
                    root.addView(view, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    web.setVisibility(View.GONE);
                    updateCursorVisibility();
                }

                @Override
                public void onHideCustomView() {
                    if (customView == null) return;
                    root.removeView(customView);
                    customView = null;
                    web.setVisibility(View.VISIBLE);
                    if (customCallback != null) customCallback.onCustomViewHidden();
                    updateCursorVisibility();
                }

                @Override
                public boolean onCreateWindow(WebView v, boolean d, boolean g, android.os.Message m) {
                    return false; // без изскачащи прозорци
                }
            });

            root.addView(web, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Курсорът се рисува върху страницата и се управлява със стрелките.
            cursor = new CursorView(this);
            root.addView(cursor, new FrameLayout.LayoutParams(CURSOR_SIZE, CURSOR_SIZE));
        }
        menu.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);

        // Курсорът започва в средата на екрана.
        cx = (root.getWidth() > 0 ? root.getWidth() : 1920) / 2f;
        cy = (root.getHeight() > 0 ? root.getHeight() : 1080) / 2f;
        updateCursor();
        updateCursorVisibility();

        web.loadUrl(url);
        web.requestFocus();
    }

    private static boolean isBlocked(String host) {
        if (host == null) return false;
        for (String d : BLOCKED) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    private static String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private void backToMenu() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.setVisibility(View.GONE);
        }
        menu.setVisibility(View.VISIBLE);
        updateCursorVisibility();
        if (menu.getChildCount() > 1) menu.getChildAt(1).requestFocus();
    }

    // ---------- Курсор, управляван от дистанционното ----------

    private boolean webActive() {
        return web != null && web.getVisibility() == View.VISIBLE && customView == null;
    }

    private void updateCursorVisibility() {
        if (cursor != null) cursor.setVisibility(webActive() ? View.VISIBLE : View.GONE);
    }

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

        // Страницата е отворена: стрелките местят курсора, OK е клик.
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
            if (okKey) {
                if (down && e.getRepeatCount() == 0) tapAt(web, cx, cy);
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
            if (customView != null) {
                web.getWebChromeClient().onHideCustomView();
                return true;
            }
            if (web != null && web.getVisibility() == View.VISIBLE) {
                if (web.canGoBack()) web.goBack();
                else backToMenu();
                return true;
            }
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
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
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
