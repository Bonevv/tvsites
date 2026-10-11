package com.example.tvsites;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
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

    private FrameLayout root;
    private LinearLayout menu;
    private WebView web;
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
                }

                @Override
                public void onHideCustomView() {
                    if (customView == null) return;
                    root.removeView(customView);
                    customView = null;
                    web.setVisibility(View.VISIBLE);
                    if (customCallback != null) customCallback.onCustomViewHidden();
                }

                @Override
                public boolean onCreateWindow(WebView v, boolean d, boolean g, android.os.Message m) {
                    return false; // без изскачащи прозорци
                }
            });

            root.addView(web, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        menu.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
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
        if (menu.getChildCount() > 1) menu.getChildAt(1).requestFocus();
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
}
