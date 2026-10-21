package com.example.tvsites;

import android.animation.ValueAnimator;
import android.graphics.BitmapShader;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {

    // Хранилището в GitHub, откъдето се проверяват новите версии.
    private static final String UPDATE_REPO = "Bonevv/tvsites";

    // Адрес на текстов файл с настройки (сайтове и блокирани домейни).
    // Остави го празен, ако не искаш да го ползваш. Пример за формата: tvsites-example.txt
    private static final String CONFIG_URL = "";

    // ====================== НАШИ ФИЛМИ: ИЗТОЧНИЦИ ======================
    // ТУК се слагат адресите на източниците (законните, които ти дадат).
    // Първият е основен, вторият и третият са резервни: ако първият не се зареди,
    // приложението само минава на следващия.
    // В адреса можеш да ползваш:
    //   {tmdb}    номер от TMDB        {imdb}    номер от IMDb (tt1234567)
    //   {season}  сезон                {episode} епизод
    // Пример за вида (НЕ е истински адрес):
    //   "https://example.com/embed/movie/{tmdb}"
    //   "https://example.com/embed/tv/{tmdb}/{season}/{episode}"
    // Може и без редактиране на кода: бутон "Източници" в каталога.
private static final String[] FILM_SOURCES = {
    "https://vidsrc.sh/embed/movie/{tmdb}",
    "https://vidcore.org/embed/movie/{tmdb}",
    "https://vidlink.pro/movie/{tmdb}",
    "https://vidspark.to/movie/{tmdb}",
    "https://vidsrc.me/embed/movie/{tmdb}",
    "https://vsembed.ru/embed/movie/{tmdb}",
    "https://yapgrid.com/embed/movie/{tmdb}",
};

private static final String[] TV_SOURCES = {
    "https://vidsrc.sh/embed/tv/{tmdb}/{season}/{episode}",
    "https://vidcore.org/embed/tv/{tmdb}/{season}/{episode}",
    "https://vidlink.pro/tv/{tmdb}/{season}/{episode}",
    "https://vidspark.to/tv/{tmdb}/{season}/{episode}",
    "https://vidsrc.me/embed/tv/{tmdb}/{season}/{episode}",
    "https://vsembed.ru/embed/tv/{tmdb}/{season}/{episode}",
    "https://yapgrid.com/embed/tv/{tmdb}/{season}/{episode}",
};

    // Ключ за TMDB: по-добре го въведи на кутията (репото е публично).
    private static final String TMDB_KEY = "d2c1e02ec9baf771e1ce6b4511a5aea8";
    // Ключове за субтитрите (по желание; може да се въведат и от бутона Източници).
    // OpenSubtitles: ключ + потребител + парола; Subdl: ключ; SubSource: ключ. Subs.sab.bz не иска нищо.
    private static final String SUB_OS_KEY = "";
    private static final String SUB_OS_USER = "";
    private static final String SUB_OS_PASS = "";
    private static final String SUB_SUBDL_KEY = "";
    private static final String SUB_SUBSOURCE_KEY = "";

    // Сайтове по подразбиране: {етикет, адрес, режим, група}. Режим: "" | "mobile" | "desktop"
    private static final String[][] DEFAULT_SITES = {
            {"GledaiTV", "https://www.gledaitv.fan/", "", "Телевизия"},
            {"GledaiTV Live", "https://www.gledaitv.live/", "", "Телевизия"},
            {"IPTV-BG", "https://iptv-bg.com/", "", "Телевизия"},
            {"Seir Sanduk", "https://www.seirsanduk.online/", "", "Телевизия"},
            {"BGNow", "https://www.bgnow.tv/en/category/movie/", "", "Телевизия"},
            {"bTV Плюс", "https://btvplus.bg/live/", "", "Официални · телевизия"},
            {"FilmiZip", "https://filmizip.com/", "", "Филми"},
            {"Filmi9", "https://filmi9.com/", "", "Филми"},
            {"ZenoPlay", "https://zenoplay.to/movies/", "", "Филми"},
            {"Всички филми", "https://vsichkifilmi.net/", "", "Филми"},
            {"FilmiZT", "https://filmizt.com/", "", "Филми"},
            {"FilmiFen", "https://filmifen.com/", "", "Филми"},
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
            // Още рекламни мрежи, изскачащи реклами и проследяване
            "profitablecpmrate.com", "profitablegatecpm.com", "effectivegatecpm.com",
            "revenuecpmgate.com", "clickadilla.com", "mondiad.com", "acscdn.com",
            "exdynsrv.com", "magsrv.com", "plugrush.com", "tubecorporate.com",
            "criteo.net", "moatads.com", "doubleverify.com", "adsafeprotected.com",
            "serving-sys.com", "advertising.com", "yieldmo.com", "lijit.com",
            "indexww.com", "3lift.com", "triplelift.com", "gumgum.com", "media.net",
            "everesttech.net", "demdex.net", "krxd.net", "bluekai.com", "rlcdn.com",
            "tapad.com", "agkn.com", "mathtag.com", "bidr.io", "2mdn.net",
            "scorecardresearch.com", "quantserve.com", "quantcount.com", "hotjar.com",
            "clarity.ms", "histats.com", "statcounter.com", "gemius.pl", "gemius.com",
            "adocean.pl", "adocean-global.com", "yadro.ru", "liveinternet.ru",
            "mixpanel.com", "facebook.net", "addthis.com", "sharethis.com",
    };

    // Същият списък като множество: проверката е мигновена, без обхождане на списъка.
    private static final HashSet<String> BLOCKED_SET = new HashSet<>();

    static {
        Collections.addAll(BLOCKED_SET, BLOCKED);
    }

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

    // JavaScript за полетата за въвеждане. tvInfo връща JSON за полето или празен текст.
    private static final String JS_INFO_FN =
            "function tvInfo(a){var tag=a.tagName,type=(a.getAttribute('type')||'text').toLowerCase();"
                    + "var ce=!!a.isContentEditable;"
                    + "var ok=(tag==='TEXTAREA')||ce||(tag==='INPUT'&&['text','search','email','url','tel',"
                    + "'number','password',''].indexOf(type)>=0);"
                    + "if(!ok||a.readOnly||a.disabled)return '';"
                    + "var hint=a.getAttribute('placeholder')||a.getAttribute('aria-label')||a.getAttribute('title')||'';"
                    + "var key=(a.name||'')+' '+(a.id||'')+' '+hint+' '+(typeof a.className==='string'?a.className:'');"
                    + "var s=(type==='search')||/search|query|keyword|търс|намери|(^|\\s)(q|s)(\\s|$)/i.test(key);"
                    + "return JSON.stringify({t:type,v:ce?(a.textContent||''):(a.value||''),h:hint,s:s,m:(tag==='TEXTAREA')});}";

    private static final String JS_ACTIVE_INFO =
            "(function(){" + JS_INFO_FN + "var a=document.activeElement;"
                    + "try{while(a&&a.tagName==='IFRAME'&&a.contentDocument&&a.contentDocument.activeElement)"
                    + "{a=a.contentDocument.activeElement;}}catch(e){}"
                    + "return a?tvInfo(a):'';})()";

    // Търси поле за търсене в страницата, фокусира го и връща информация за него.
    private static final String JS_FIND_SEARCH =
            "(function(){" + JS_INFO_FN
                    + "var l=document.querySelectorAll('input[type=search],input[name=s],input[name=q],"
                    + "input[name=search],input[name=query],input[name=keyword],input[id*=search i],"
                    + "input[class*=search i],input[placeholder*=search i],input[placeholder*=търс i]');"
                    + "for(var i=0;i<l.length;i++){var e=l[i];"
                    + "if(e.disabled||e.readOnly||e.type==='hidden')continue;"
                    + "if(!(e.offsetWidth||e.offsetHeight||e.getClientRects().length))continue;"
                    + "try{e.focus();e.scrollIntoView({block:'center'});}catch(x){}"
                    + "var r=tvInfo(e);if(r)return r;}"
                    + "return '';})()";

    // Записва текста в полето и (при търсене) го изпраща.
    private static String jsApplyText(String text, boolean submit) {
        return "(function(v,sub){var a=document.activeElement;"
                + "try{while(a&&a.tagName==='IFRAME'&&a.contentDocument&&a.contentDocument.activeElement)"
                + "{a=a.contentDocument.activeElement;}}catch(e){}"
                + "if(!a)return;"
                + "if(a.isContentEditable){a.textContent=v;}else{"
                + "var p=(a.tagName==='TEXTAREA')?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;"
                + "var d=Object.getOwnPropertyDescriptor(p,'value');"
                + "if(d&&d.set){d.set.call(a,v);}else{a.value=v;}}"
                + "a.dispatchEvent(new Event('input',{bubbles:true}));"
                + "a.dispatchEvent(new Event('change',{bubbles:true}));"
                + "if(sub){var f=a.form;"
                + "if(f){if(f.requestSubmit){f.requestSubmit();}else{f.submit();}}"
                + "else{['keydown','keypress','keyup'].forEach(function(t){"
                + "a.dispatchEvent(new KeyboardEvent(t,{key:'Enter',code:'Enter',keyCode:13,which:13,"
                + "bubbles:true,cancelable:true}));});}}"
                + "})(" + JSONObject.quote(text) + "," + (submit ? "true" : "false") + ")";
    }

    // Цветове
    // Shared navy and blue display tokens.
    private static final int C_BG = TvTheme.BACKGROUND;
    private static final int C_ACCENT = TvTheme.BLUE;
    private static final int C_MUTED = 0xFFA2ADCB;
    private static final int C_DANGER = 0xFFF87171;
    private static final int[] BADGES = {
            0xFF3F6FB5, 0xFF2F8F83, 0xFF6B5BB8, 0xFFB5566B,
            0xFFB7822F, 0xFF4C8F4E, 0xFF5B7DB1,
    };
    private static final int C_AMBER = C_ACCENT;
    private static final String[] BG_DAYS = {"неделя", "понеделник", "вторник", "сряда",
            "четвъртък", "петък", "събота"};
    private static final String[] BG_MONTHS = {"януари", "февруари", "март", "април", "май",
            "юни", "юли", "август", "септември", "октомври", "ноември", "декември"};
    private static final int COLS = 3;

    // Над този размер на кеша приложението пита дали да го изчисти.
    private static final long CACHE_ASK_BYTES = 150L * 1024 * 1024;
    private static final long CACHE_ASK_EVERY_MS = 6L * 60 * 60 * 1000;
    private static final long CACHE_AUTO_BYTES = 200L * 1024 * 1024;
    private static final long CACHE_WATCH_MS = 120L * 1000;

    // Две нишки с нисък приоритет: IO за дневника и измерването на паметта, DISK за обхождане
    // на кеша. Главната нишка никога не чака диска, а гледането не се забавя от тези задачи.
    // Отделени са, за да не закъснява записът на събитията, докато се брои кешът.
    private static ThreadFactory lowPriority(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        android.os.Process.setThreadPriority(
                                android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        r.run();
                    }
                }, name);
                t.setDaemon(true);
                return t;
            }
        };
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(lowPriority("tvs-io"));
    private static final ExecutorService DISK =
            Executors.newSingleThreadExecutor(lowPriority("tvs-disk"));

    private static void submit(ExecutorService ex, final Runnable r) {
        try {
            ex.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        r.run();
                    } catch (Throwable ignored) {
                        // една неуспешна задача не бива да спира приложението
                    }
                }
            });
        } catch (Throwable ignored) {
            // нишката е спряна (приложението се затваря)
        }
    }

    private static void bg(Runnable r) {
        submit(IO, r);
    }

    private static void disk(Runnable r) {
        submit(DISK, r);
    }

    // Докато гледаш сайт, на всеки 2 минути проверява кеша и го чисти над прага
    // (ако авто чистенето е включено). Не показва съобщения, за да не пречи на картината.
    private final Runnable cacheWatch = new Runnable() {
        @Override
        public void run() {
            if (web == null) return; // сайтът е затворен: проверката приключва
            if (web.getVisibility() == View.VISIBLE) {
                disk(new Runnable() {
                    @Override
                    public void run() {
                        long cs = dirSize(getCacheDir());
                        logEvent("Проверка: кеш " + mb(cs), true);
                        if (!autoCacheOn() || cs < CACHE_AUTO_BYTES) return;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (web != null && web.getVisibility() == View.VISIBLE) {
                                    web.clearCache(true);
                                }
                            }
                        });
                    }
                });
            }
            root.postDelayed(this, CACHE_WATCH_MS);
        }
    };

    private void startWatch() {
        root.removeCallbacks(cacheWatch);
        root.postDelayed(cacheWatch, CACHE_WATCH_MS);
    }
    private final Handler ui = new Handler(Looper.getMainLooper());
    // Икони на сайтовете, вече прочетени от диска (само от главната нишка).
    private static final LruCache<String, Bitmap> ICONS = new LruCache<String, Bitmap>(2 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };

    // ---- нова версия, намерена при пускане ----
    private int updateAvail; // номер на по-нова версия (0 = няма)
    private String updateUrl;

    // ---- последни страници ----
    private static final int RECENT_MAX = 4;
    private String curSiteName = "";
    private final Runnable rememberTick = new Runnable() {
        @Override
        public void run() {
            rememberPage();
        }
    };

    // ---- таймер за заспиване (ръчен) ----
    private static final int[] SLEEP_MIN = {0, 15, 30, 45, 60, 90, 120};
    private long sleepAt; // кога спира (uptimeMillis); 0 = изключен
    private AlertDialog sleepDialog;
    private final Runnable sleepWarn = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || sleepAt == 0) return;
            sleepDialog = new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Таймер за заспиване")
                    .setMessage("След 1 минута приложението ще спре.")
                    .setPositiveButton("Остани още 30 мин", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            setSleep(30);
                        }
                    })
                    .setNegativeButton("Добре", null)
                    .show();
        }
    };
    private final Runnable sleepFire = new Runnable() {
        @Override
        public void run() {
            sleepAt = 0;
            try {
                if (sleepDialog != null) sleepDialog.dismiss();
            } catch (Exception ignored) {
            }
            logEvent("Таймерът за заспиване спря приложението");
            if (!isFinishing()) exitApp();
        }
    };
    // Версия на отворения сайт: "" (както е по подразбиране на устройството), "mobile" или "desktop".
    private String curMode = "";
    private final HashSet<String> iconBusy = new HashSet<>();

    private static final int CURSOR_SIZE = 40;
    private static final int CURSOR_HIDE_MS = 4000;

    // ---- екрани ----
    private FrameLayout root;
    private ScrollView menuHolder;
    private LinearLayout menu;
    private volatile WebView web;
    private LinearLayout websiteToolbar;
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
    private BaseAdapter logAdapter;
    private final ArrayList<LogRow> logRows = new ArrayList<>();
    private String cacheSizeText;

    // ---- зареждане, подсказки, въвеждане на текст ----
    private View loadBar;
    private FrameLayout splash;
    private LinearLayout splashBox;
    private TextView hintChip;
    private Dialog textDialog;
    private final Runnable splashTimeout = new Runnable() {
        @Override
        public void run() {
            hideSplash();
        }
    };
    private final Runnable hideHint = new Runnable() {
        @Override
        public void run() {
            if (hintChip != null) {
                hintChip.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        hintChip.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
    };
    private final Runnable editableCheck = new Runnable() {
        @Override
        public void run() {
            if (webActive()) probeEditable(JS_ACTIVE_INFO, false);
        }
    };

    // Ред от панела с опции.
    private static class LogRow {
        static final int HEADER = 0, ACTION = 1, DOMAIN = 2, NOTE = 3;
        int kind;
        String icon, title, sub, state;
        int stateColor;
    }
    private final ArrayList<String> logKeys = new ArrayList<>();
    private TextView statsView;
    private boolean statsOn;
    private final Runnable statsTick = new Runnable() {
        @Override
        public void run() {
            updateStats();
            if (statsOn) root.postDelayed(this, 3000);
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
            showNativeStatus("Не успя да се пусне този поток (" + tvErrInfo(error)
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
        // Мерна единица на менюто (макет 1600x900) и шрифтове на системата.
        android.util.DisplayMetrics dm0 = getResources().getDisplayMetrics();
        U = Math.min(dm0.heightPixels, dm0.widthPixels * 9f / 16f) / 900f;
        tfCondBold = Typeface.create("sans-serif", Typeface.BOLD);
        tfCond = Typeface.create("sans-serif", Typeface.NORMAL);
        tfMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        installCrashLogger();
        logEvent("Приложението стартира (v" + installedName() + ") - " + deviceLine(), true);
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
        menuHolder.setBackground(pageBg());
        menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setGravity(Gravity.CENTER_VERTICAL);
        menu.setClipChildren(false);
        menu.setClipToPadding(false);
        menu.setPadding(u(64), u(30), u(64), u(22));
        menuHolder.addView(menu, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(menuHolder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        menuHolder.setBackground(TvTheme.background());
        appBar = new AppNavigation(this, U, this::showSection);
        appBar.setContentAction(this::focusSectionContent);
        appBar.setSearchAction(this::showSectionSearch);
        FrameLayout.LayoutParams nav = new FrameLayout.LayoutParams(MP, MP);
        root.addView(appBar, nav);

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
        scanCache(true);
        menu.post(new Runnable() {
            @Override
            public void run() {
                showLastCrash();
            }
        });
        // Една тиха проверка за нова версия при всяко пускане (след като менюто се е показало).
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) checkUpdateQuiet();
            }
        }, 3000);
    }

    // ---------- Дневник на събитията (за диагностика) ----------

    private static final Object EVENT_LOCK = new Object();
    private static ArrayList<String> eventLines; // последните редове; пази се докато процесът живее

    // Пази последните ~40 събития във файл, за да се вижда какво е станало преди спиране.
    // Записът става на заден план. withMem добавя и паметта (измерва се във фоновата нишка).
    private void logEvent(String msg) {
        logEvent(msg, false);
    }

    private void logEvent(final String msg, final boolean withMem) {
        final long when = System.currentTimeMillis();
        bg(new Runnable() {
            @Override
            public void run() {
                writeEvent(when, withMem ? msg + " - " + memLine() : msg);
            }
        });
    }

    // Само докато се държи EVENT_LOCK.
    private ArrayList<String> eventsLocked() {
        if (eventLines == null) {
            eventLines = new ArrayList<>();
            try {
                File f = new File(getFilesDir(), "events.txt");
                if (f.exists()) {
                    for (String l : readAll(new java.io.FileInputStream(f)).split("\n")) {
                        if (!l.isEmpty()) eventLines.add(l);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return eventLines;
    }

    private void writeEvent(long when, String msg) {
        try {
            synchronized (EVENT_LOCK) {
                ArrayList<String> l = eventsLocked();
                l.add(new java.text.SimpleDateFormat("dd.MM HH:mm:ss", Locale.US)
                        .format(new java.util.Date(when)) + "  " + msg.replace('\n', ' '));
                while (l.size() > 40) l.remove(0);
                StringBuilder sb = new StringBuilder();
                for (String s : l) sb.append(s).append('\n');
                FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), "events.txt"));
                fo.write(sb.toString().getBytes("UTF-8"));
                fo.close();
            }
        } catch (Throwable ignored) {
        }
    }

    // Мерането на PSS е скъпо (системата чете цялата карта на паметта на процеса),
    // затова се вика само от фоновата нишка и рядко.
    private long pssMb() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            android.os.Debug.MemoryInfo[] pm =
                    am.getProcessMemoryInfo(new int[]{android.os.Process.myPid()});
            if (pm != null && pm.length > 0) return pm[0].getTotalPss() / 1024L;
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String memLine() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return "RAM своб. " + (mi.availMem / 1048576L) + "/" + (mi.totalMem / 1048576L)
                + " МБ, прил. " + pssMb() + " МБ" + (mi.lowMemory ? ", МАЛКО ПАМЕТ" : "");
    }

    // Кратко описание на устройството (за диагностиката).
    private String deviceLine() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        return Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE
                + ", екран " + dm.widthPixels + "x" + dm.heightPixels + " @" + dm.density
                + ", клас " + am.getMemoryClass() + " МБ"
                + (am.isLowRamDevice() ? ", МАЛКО RAM" : "");
    }

    private void showDiagnostics() {
        // Опашката е една, затова четенето идва след всички чакащи записи.
        bg(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                synchronized (EVENT_LOCK) {
                    ArrayList<String> l = eventsLocked();
                    for (int i = Math.max(0, l.size() - 10); i < l.size(); i++) {
                        sb.append(l.get(i)).append('\n');
                    }
                }
                final String txt = sb.length() == 0 ? "Още няма записани събития." : sb.toString();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFinishing()) {
                            showInfo("Диагностика",
                                    "Последни събития (най-новите са най-долу).\n\n" + txt);
                        }
                    }
                });
            }
        });
    }

    private long lastTrimLog;

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            BACKDROPS.evictAll();
            ICONS.evictAll();
            tvLogoTrim();
            // Кориците и отговорите на каталога са само за удобство: при малко памет отиват първи.
            POSTERS.evictAll();
            synchronized (TMDB_JSON) {
                TMDB_JSON.clear();
                TMDB_AT.clear();
            }
        }
        if (level >= TRIM_MEMORY_RUNNING_LOW && web != null) {
            // Сигналите идват на серии: записваме най-много един на половин минута.
            long now = SystemClock.uptimeMillis();
            if (now - lastTrimLog > 30000) {
                lastTrimLog = now;
                logEvent("Системата сигнализира малко памет (ниво " + level + ")", true);
            }
        }
    }

    // ---------- Запис на неочаквано затваряне ----------

    // При неочаквано затваряне записва причината, за да я покажем следващия път.
    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    // Чакащите записи се довършват, после се записва причината (веднага, без опашка).
                    IO.shutdown();
                    IO.awaitTermination(700, TimeUnit.MILLISECONDS);
                } catch (Throwable ignored) {
                }
                try {
                    writeEvent(System.currentTimeMillis(), "ГРЕШКА: " + e);
                } catch (Throwable ignored) {
                }
                try {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(sw));
                    String txt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(new java.util.Date()) + "  v" + installedName() + "\n" + sw;
                    FileOutputStream fo = new FileOutputStream(new File(getFilesDir(), "crash.txt"));
                    fo.write(txt.getBytes("UTF-8"));
                    fo.close();
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    private void showLastCrash() {
        File f = new File(getFilesDir(), "crash.txt");
        if (!f.exists()) return;
        String txt = "";
        try {
            txt = readAll(new java.io.FileInputStream(f));
        } catch (Exception ignored) {
        }
        f.delete();
        if (txt.isEmpty() || isFinishing()) return;
        if (txt.length() > 1100) txt = txt.substring(0, 1100);
        showInfo("Приложението се затвори неочаквано",
                "Приложението спря неочаквано. Причина:\n\n" + txt);
    }

    // ---------- Настройки от текстов файл ----------

    private void useDefaultSites() {
        sites.clear();
        for (String[] s : DEFAULT_SITES) sites.add(s);
    }

    private static byte[] readBytes(InputStream in, int limit) throws java.io.IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(32 * 1024);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
            if (bo.size() > limit) break;
        }
        in.close();
        return bo.toByteArray();
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
        blockCache.clear();
        fillMenu();
    }

    // ---------- Меню ----------

    private String[] findSite(String url, String name) {
        for (String[] s : sites) {
            if (s[1].equals(url)) return s;
        }
        // Последният адрес може да е вътрешна страница (резултати, филм): отваряме сайта му.
        String h = hostOf(url);
        for (String[] s : sites) {
            if (hostOf(s[1]).equals(h)) return s;
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

    // ---------- Нов вид: мерна единица, шрифтове, иконки ----------

    // Макетът е чертан на 1600x900 "единици". U са пикселите за една единица, така че
    // менюто изглежда еднакво на всеки телевизор (720p, 1080p, 4K).
    private float U = 1f;
    private Typeface tfCondBold = Typeface.DEFAULT_BOLD;
    private Typeface tfCond = Typeface.DEFAULT;
    private Typeface tfMedium = Typeface.DEFAULT;

    private static final int C_BG_TOP = TvTheme.BACKGROUND_TOP;
    private static final int C_BG_BOTTOM = TvTheme.BACKGROUND;
    private static final int C_PANEL = TvTheme.SURFACE;
    private static final int C_PANEL_LINE = TvTheme.OUTLINE;
    private static final int C_PANEL_FOCUS = TvTheme.SURFACE_ALT;
    private static final int C_ICON_BG = TvTheme.SURFACE_ALT;
    private static final int C_ICON_FG = TvTheme.TEXT;
    private static final int C_GREEN_BG = 0xFF12382F;
    private static final int C_GREEN_FG = 0xFF6EE7B7;
    private static final int C_AMBER_BG = 0xFF4A3512;
    private static final int C_AMBER_FG = 0xFFFBBF24;
    private static final int C_RED_BG = 0xFF4A1D2C;
    private static final int C_RED_FG = 0xFFFDA4AF;
    private static final int TILES_PER_ROW = 6;

    // Цветове на плочките по вид: телевизия (сини), филми (розови и лилави), радио (кехлибарени).
    private static final int[][][] PALS = {
            {{0xFF2563EB, 0xFF1E3A8A}, {0xFF0EA5E9, 0xFF0B4A73}, {0xFF6366F1, 0xFF312E81},
                    {0xFF3B82F6, 0xFF1E40AF}},
            {{0xFFF43F5E, 0xFF881337}, {0xFFD946EF, 0xFF701A75}, {0xFFFB923C, 0xFF9A3412},
                    {0xFF14B8A6, 0xFF115E59}, {0xFF8B5CF6, 0xFF4C1D95}, {0xFFEC4899, 0xFF831843},
                    {0xFFEF4444, 0xFF7F1D1D}},
            {{0xFFF59E0B, 0xFF92400E}, {0xFFEAB308, 0xFF713F12}, {0xFFF97316, 0xFF7C2D12}},
            {{0xFF6366F1, 0xFF312E81}, {0xFF14B8A6, 0xFF115E59}, {0xFFF43F5E, 0xFF881337},
                    {0xFFF59E0B, 0xFF92400E}, {0xFF8B5CF6, 0xFF4C1D95}},
    };

    private static final int IC_CHIP = 0, IC_DB = 1, IC_MOON = 2, IC_CHECK = 3, IC_PLAY = 4,
            IC_CLOCK = 5, IC_REFRESH = 6, IC_TRASH = 7, IC_SPARK = 8, IC_PULSE = 9, IC_POWER = 10,
            IC_TV = 11, IC_FILM = 12, IC_RADIO = 13, IC_SEARCH = 14, IC_MONITOR = 15,
            IC_CURSOR = 16, IC_BARS = 17, IC_SHIELD = 18, IC_DOWN = 19, IC_SLIDERS = 20,
            IC_STAR = 21, IC_GRID = 22, IC_DICE = 23, IC_PLUS = 24, IC_HOME = 25;

    // Иконка, нарисувана с няколко линии (без файлове). Координатите са на мрежа 24x24.
    private static class IconView extends View {
        private final int kind;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Path path2 = new Path();
        private final RectF rf = new RectF();

        IconView(Context c, int kind, int color) {
            super(c);
            this.kind = kind;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        private void line(Canvas c, float x1, float y1, float x2, float y2) {
            c.drawLine(x1, y1, x2, y2, p);
        }

        private void poly(Canvas c, boolean close, float... xy) {
            path.reset();
            path.moveTo(xy[0], xy[1]);
            for (int i = 2; i + 1 < xy.length; i += 2) path.lineTo(xy[i], xy[i + 1]);
            if (close) path.close();
            c.drawPath(path, p);
        }

        private void rect(Canvas c, float l, float t, float r, float b, float rad) {
            rf.set(l, t, r, b);
            c.drawRoundRect(rf, rad, rad, p);
        }

        private void arc(Canvas c, float l, float t, float r, float b, float start, float sweep) {
            rf.set(l, t, r, b);
            c.drawArc(rf, start, sweep, false, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            if (s <= 0) return;
            c.save();
            c.scale(s, s);
            switch (kind) {
                case IC_CHIP:
                    rect(c, 6, 6, 18, 18, 2);
                    line(c, 9, 2, 9, 5);
                    line(c, 15, 2, 15, 5);
                    line(c, 9, 19, 9, 22);
                    line(c, 15, 19, 15, 22);
                    line(c, 2, 9, 5, 9);
                    line(c, 2, 15, 5, 15);
                    line(c, 19, 9, 22, 9);
                    line(c, 19, 15, 22, 15);
                    break;
                case IC_DB:
                    rf.set(5, 3, 19, 9);
                    c.drawOval(rf, p);
                    arc(c, 5, 9, 19, 15, 0, 180);
                    arc(c, 5, 15, 19, 21, 0, 180);
                    line(c, 5, 6, 5, 18);
                    line(c, 19, 6, 19, 18);
                    break;
                case IC_MOON:
                    path.reset();
                    path.addCircle(12, 12, 8, Path.Direction.CW);
                    path2.reset();
                    path2.addCircle(16.5f, 8.5f, 7f, Path.Direction.CW);
                    if (path.op(path2, Path.Op.DIFFERENCE)) c.drawPath(path, p);
                    break;
                case IC_CHECK:
                    poly(c, false, 5, 12.5f, 9.5f, 17, 19, 7.5f);
                    break;
                case IC_PLAY:
                    p.setStyle(Paint.Style.FILL_AND_STROKE);
                    poly(c, true, 7.5f, 4.5f, 7.5f, 19.5f, 19.5f, 12);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                case IC_CLOCK:
                    c.drawCircle(12, 12, 9, p);
                    poly(c, false, 12, 7, 12, 12, 15, 14);
                    break;
                case IC_REFRESH:
                    arc(c, 4, 4, 20, 20, -35, 300);
                    poly(c, false, 20, 4, 20, 9, 15, 9);
                    break;
                case IC_TRASH:
                    line(c, 4, 7, 20, 7);
                    poly(c, false, 9, 7, 9, 4.5f, 15, 4.5f, 15, 7);
                    poly(c, false, 6.5f, 7, 7.4f, 19.5f, 16.6f, 19.5f, 17.5f, 7);
                    break;
                case IC_SPARK:
                    poly(c, true, 12, 3, 13.8f, 8.2f, 19, 10, 13.8f, 11.8f, 12, 17, 10.2f, 11.8f,
                            5, 10, 10.2f, 8.2f);
                    break;
                case IC_PULSE:
                    poly(c, false, 3, 12, 7, 12, 9.5f, 6, 13.5f, 18, 16, 12, 21, 12);
                    break;
                case IC_POWER:
                    line(c, 12, 3.5f, 12, 11.5f);
                    arc(c, 4.5f, 4.5f, 19.5f, 19.5f, -60, 300);
                    break;
                case IC_TV:
                    rect(c, 3, 5, 21, 17, 2);
                    line(c, 9, 21, 15, 21);
                    break;
                case IC_FILM:
                    rect(c, 3, 4, 21, 20, 2);
                    line(c, 3, 9, 21, 9);
                    line(c, 3, 15, 21, 15);
                    line(c, 8, 4, 8, 20);
                    line(c, 16, 4, 16, 20);
                    break;
                case IC_RADIO:
                    rect(c, 3, 9, 21, 20, 2);
                    line(c, 7, 9, 17, 4);
                    c.drawCircle(9, 14.5f, 2, p);
                    line(c, 14, 13, 18, 13);
                    line(c, 14, 16, 18, 16);
                    break;
                case IC_SEARCH:
                    c.drawCircle(11, 11, 6.5f, p);
                    line(c, 16, 16, 20.5f, 20.5f);
                    break;
                case IC_MONITOR:
                    rect(c, 3, 4, 21, 16, 2);
                    line(c, 8, 20, 16, 20);
                    line(c, 12, 16, 12, 20);
                    break;
                case IC_CURSOR:
                    poly(c, true, 6, 3, 18, 12, 12.8f, 13.2f, 16, 20, 13, 21.2f, 9.8f, 14.4f,
                            6, 18);
                    break;
                case IC_BARS:
                    line(c, 5, 20, 5, 10);
                    line(c, 12, 20, 12, 4);
                    line(c, 19, 20, 19, 13);
                    break;
                case IC_SHIELD:
                    path.reset();
                    path.moveTo(12, 3);
                    path.lineTo(20, 6);
                    path.lineTo(20, 11);
                    path.quadTo(20, 17, 12, 21);
                    path.quadTo(4, 17, 4, 11);
                    path.lineTo(4, 6);
                    path.close();
                    c.drawPath(path, p);
                    poly(c, false, 9, 12, 11, 14, 15, 10);
                    break;
                case IC_SLIDERS:
                    line(c, 4, 7, 20, 7);
                    line(c, 4, 12, 20, 12);
                    line(c, 4, 17, 20, 17);
                    c.drawCircle(9, 7, 2.2f, p);
                    c.drawCircle(15, 12, 2.2f, p);
                    c.drawCircle(8, 17, 2.2f, p);
                    break;
                case IC_DOWN:
                    line(c, 12, 4, 12, 15);
                    poly(c, false, 7, 11, 12, 16, 17, 11);
                    line(c, 5, 20, 19, 20);
                    break;
                case IC_STAR:
                    p.setStyle(Paint.Style.FILL_AND_STROKE);
                    p.setStrokeWidth(1f);
                    starPath(path);
                    c.drawPath(path, p);
                    p.setStrokeWidth(2f);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                case IC_GRID:
                    rect(c, 4, 4, 10.5f, 10.5f, 1.6f);
                    rect(c, 13.5f, 4, 20, 10.5f, 1.6f);
                    rect(c, 4, 13.5f, 10.5f, 20, 1.6f);
                    rect(c, 13.5f, 13.5f, 20, 20, 1.6f);
                    break;
                case IC_DICE:
                    rect(c, 4, 4, 20, 20, 3.5f);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(9, 9, 1.3f, p);
                    c.drawCircle(15, 9, 1.3f, p);
                    c.drawCircle(9, 15, 1.3f, p);
                    c.drawCircle(15, 15, 1.3f, p);
                    p.setStyle(Paint.Style.STROKE);
                    break;
                case IC_PLUS:
                    line(c, 12, 5, 12, 19);
                    line(c, 5, 12, 19, 12);
                    break;
                case IC_HOME:
                    poly(c, false, 4, 11.5f, 12, 4, 20, 11.5f);
                    poly(c, false, 6.5f, 10, 6.5f, 19.5f, 17.5f, 19.5f, 17.5f, 10);
                    poly(c, false, 10, 19.5f, 10, 14.5f, 14, 14.5f, 14, 19.5f);
                    break;
                default:
                    break;
            }
            c.restore();
        }
    }

    private int u(float units) {
        return Math.round(units * U);
    }

    private void ts(TextView t, float units) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, units * U);
    }

    private TextView text(String s, float sizeUnits, int color, Typeface tf, boolean oneLine) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        ts(t, sizeUnits);
        t.setTypeface(tf);
        if (oneLine) {
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
        }
        return t;
    }

    private static int darker(int c, float f) {
        int r = Math.round(((c >> 16) & 0xFF) * f);
        int g = Math.round(((c >> 8) & 0xFF) * f);
        int b = Math.round((c & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private GradientDrawable pageBg() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{C_BG_TOP, C_BG_BOTTOM});
    }

    private GradientDrawable panelBg(float radiusUnits) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(C_PANEL);
        g.setCornerRadius(u(radiusUnits));
        g.setStroke(Math.max(1, u(1)), C_PANEL_LINE);
        return g;
    }

    // Фон със състояние: на фокус получава бяла рамка. normalStroke 0 = без рамка.
    private StateListDrawable stateBg(int normalColor, int focusColor, int strokeColor,
                                      int radiusPx, int normalStroke) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(radiusPx);
        if (normalStroke != 0) normal.setStroke(Math.max(1, u(1)), normalStroke);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(focusColor);
        focused.setCornerRadius(radiusPx);
        focused.setStroke(u(2), TvTheme.FOCUS);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    private StateListDrawable gradBg(int[] colors, float radiusUnits, float strokeUnits) {
        GradientDrawable n = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        n.setCornerRadius(u(radiusUnits));
        GradientDrawable f = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        f.setCornerRadius(u(radiusUnits));
        f.setStroke(u(strokeUnits), Color.WHITE);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, f);
        sl.addState(new int[]{}, n);
        return sl;
    }

    // Плочките и панелите се уголемяват на фокус и се показват над съседите си.
    private final View.OnFocusChangeListener tileFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            float k = v.getTag(R_SCALE) instanceof Float ? (Float) v.getTag(R_SCALE) : 1.07f;
            v.animate().scaleX(has ? k : 1f).scaleY(has ? k : 1f).setDuration(120).start();
            v.setTranslationZ(has ? 12 * U : 0f);
        }
    };
    private static final int R_SCALE = 0x7f0b0001; // ключ за таг (произволно число, не ресурс)

    private View iconCircle(int kind, int bg, int fg, float sizeUnits) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(bg);
        f.setBackground(g);
        int s = u(sizeUnits * 0.56f);
        f.addView(new IconView(this, kind, fg), new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        return f;
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    // Поле за състояние: икона, надпис и стойност.
    private LinearLayout capsule(int kind, int iconBg, int iconFg, String label, String value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(u(12), 0, u(18), 0);
        c.setBackground(panelBg(32));
        LinearLayout.LayoutParams ilp = lp(u(40), u(40));
        ilp.rightMargin = u(12);
        c.addView(iconCircle(kind, iconBg, iconFg, 40), ilp);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(label, 14, C_MUTED, tfMedium, true));
        TextView v = text(value, 18, Color.WHITE, Typeface.DEFAULT_BOLD, true);
        tx.addView(v);
        c.addView(tx);
        c.setTag(v);
        return c;
    }

    private String freeMemText() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return (mi.availMem / 1048576L) + " МБ";
        } catch (Exception e) {
            return "-";
        }
    }

    private TextView capCache;
    private boolean versionOk;
    private View heroView;
    private View firstTile;

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout logo = new FrameLayout(this);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF38BDF8, 0xFFFB7185});
        lg.setCornerRadius(u(15));
        logo.setBackground(lg);
        int ls = u(26);
        logo.addView(new IconView(this, IC_PLAY, 0xFF0A0E1A),
                new FrameLayout.LayoutParams(ls, ls, Gravity.CENTER));
        LinearLayout.LayoutParams llp = lp(u(52), u(52));
        llp.rightMargin = u(14);
        h.addView(logo, llp);
        h.addView(text("ТВ Сайтове", 32, Color.WHITE, tfCondBold, true));

        LinearLayout caps = new LinearLayout(this);
        caps.setOrientation(LinearLayout.HORIZONTAL);
        caps.setGravity(Gravity.CENTER);
        caps.setClipChildren(false);
        LinearLayout.LayoutParams gap = lp(ViewGroup.LayoutParams.WRAP_CONTENT, u(64));
        gap.leftMargin = u(12);

        caps.addView(capsule(IC_CHIP, C_ICON_BG, C_ICON_FG, "Свободна памет", freeMemText()),
                lp(ViewGroup.LayoutParams.WRAP_CONTENT, u(64)));
        LinearLayout cache = capsule(IC_DB, C_ICON_BG, C_ICON_FG, "Кеш",
                cacheSizeText != null ? cacheSizeText : "...");
        capCache = (TextView) cache.getTag();
        caps.addView(cache, gap);
        caps.addView(capsule(IC_MOON, sleepAt != 0 ? C_AMBER_BG : C_ICON_BG,
                sleepAt != 0 ? C_AMBER_FG : C_ICON_FG, "Таймер",
                sleepAt != 0 ? "Остават " + sleepLabel().toLowerCase(Locale.ROOT) : "Изключен"), gap);

        LinearLayout ver;
        if (updateAvail > installedCode()) {
            final int uv = updateAvail;
            final String uurl = updateUrl;
            ver = capsule(IC_DOWN, C_AMBER_BG, C_AMBER_FG, "Има нова версия", "2." + uv + " · Обнови");
            ver.setFocusable(true);
            ver.setClickable(true);
            ver.setBackground(stateBg(0xFF3B2A0A, 0xFF4A3512, Color.WHITE, u(32), C_AMBER_FG));
            ver.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askUpdate(uv, uurl);
                }
            });
            ver.setOnFocusChangeListener(focusAnim);
        } else {
            ver = capsule(IC_CHECK, C_GREEN_BG, C_GREEN_FG, "Версия " + installedName(),
                    versionOk ? "Актуална" : "Инсталирана");
        }
        caps.addView(ver, gap);
        LinearLayout.LayoutParams caplp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        h.addView(caps, caplp);

        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        when.setGravity(Gravity.END);
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        ts(clock, 56);
        clock.setTypeface(tfCond);
        clock.setGravity(Gravity.END);
        clock.setIncludeFontPadding(false);
        when.addView(clock);
        TextView date = text(dateLabel(), 15, C_MUTED, tfMedium, true);
        date.setGravity(Gravity.END);
        when.addView(date);
        h.addView(when);
        return h;
    }

    // Само заглавната лента се прави наново (например когато се намери нова версия).
    private void refreshHeader() {
        if (appBar != null) appBar.setSection(appSection);
    }

    private static String dateLabel() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return BG_DAYS[c.get(java.util.Calendar.DAY_OF_WEEK) - 1] + ", "
                + c.get(java.util.Calendar.DAY_OF_MONTH) + " "
                + BG_MONTHS[c.get(java.util.Calendar.MONTH)];
    }

    private static String timeAgo(long ts) {
        if (ts <= 0) return "";
        long m = (System.currentTimeMillis() - ts) / 60000L;
        if (m < 1) return "току-що";
        if (m < 60) return "преди " + m + " мин";
        long h = m / 60;
        if (h < 24) return h == 1 ? "преди 1 час" : "преди " + h + " часа";
        long d = h / 24;
        return d == 1 ? "вчера" : "преди " + d + " дни";
    }

    // ---------- Цветове и икони по вид сайтове ----------

    private static int family(String group) {
        String g = group.toLowerCase(Locale.forLanguageTag("bg"));
        if (g.contains("телев") || g.equals("тв") || g.contains("tv")) return 0;
        if (g.contains("филм") || g.contains("кино")) return 1;
        if (g.contains("радио")) return 2;
        return 3;
    }

    private static String groupOf(String[] s) {
        return (s.length > 3 && !s[3].isEmpty()) ? s[3] : "Сайтове";
    }

    private static int famIcon(int fam) {
        return fam == 0 ? IC_TV : fam == 1 ? IC_FILM : fam == 2 ? IC_RADIO : IC_PLAY;
    }

    private static int famBg(int fam) {
        return fam == 0 ? 0xFF0C3550 : fam == 1 ? C_RED_BG : fam == 2 ? C_AMBER_BG : 0xFF2A2250;
    }

    private static int famFg(int fam) {
        return fam == 0 ? 0xFF38BDF8 : fam == 1 ? 0xFFFB7185 : fam == 2 ? C_AMBER_FG : 0xFFA78BFA;
    }

    // Двата цвята на плочката на сайта: според вида и мястото му в групата.
    private int[] siteColors(String[] site) {
        String g = groupOf(site);
        int fam = family(g);
        int idx = 0;
        for (String[] s : sites) {
            if (s == site) break;
            if (groupOf(s).equals(g)) idx++;
        }
        int[][] pal = PALS[fam];
        return pal[idx % pal.length];
    }

    private int[] colorsByName(String name) {
        for (String[] s : sites) {
            if (s[0].equals(name)) return siteColors(s);
        }
        int[][] pal = PALS[3];
        return pal[(name.hashCode() & 0x7fffffff) % pal.length];
    }

    // ---------- Плочки, панели и бутони ----------

    // Квадратче с иконата на сайта; докато я няма, показва буква.
    private View makeIconTile(String host, int bgColor, int letterColor, String letter,
                              int sizePx, int radiusPx) {
        final FrameLayout tile = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(radiusPx);
        tile.setBackground(bg);

        final TextView t = new TextView(this);
        t.setText(letter);
        t.setTextColor(letterColor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * 0.58f);
        t.setTypeface(tfCondBold);
        t.setGravity(Gravity.CENTER);
        t.setIncludeFontPadding(false);
        tile.addView(t, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Math.round(sizePx * 0.14f);
        iv.setPadding(pad, pad, pad, pad);
        iv.setVisibility(View.GONE);
        tile.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (host != null) loadIcon(host, tile, iv, t, radiusPx);
        return tile;
    }

    private void showIcon(FrameLayout tile, ImageView iv, TextView letter, Bitmap bmp, int radiusPx) {
        GradientDrawable white = new GradientDrawable();
        white.setColor(0xFFF1F5FF);
        white.setCornerRadius(radiusPx);
        tile.setBackground(white);
        iv.setImageBitmap(bmp);
        iv.setVisibility(View.VISIBLE);
        letter.setVisibility(View.GONE);
    }

    // Иконата се сваля веднъж и се пази във файловете на приложението (не в кеша).
    // Веднъж прочетена от диска, остава в паметта (няколко малки картинки), за да не се
    // чете наново при всяко отваряне на менюто.
    private void loadIcon(final String host, final FrameLayout tile, final ImageView iv,
                          final TextView letter, final int radiusPx) {
        Bitmap hit = ICONS.get(host);
        if (hit != null) {
            showIcon(tile, iv, letter, hit, radiusPx);
            return;
        }
        final File f = new File(new File(getFilesDir(), "icons"), host + ".png");
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) {
                ICONS.put(host, b);
                showIcon(tile, iv, letter, b, radiusPx);
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
                            if (isFinishing()) return;
                            ICONS.put(host, result);
                            showIcon(tile, iv, letter, result, radiusPx);
                        }
                    });
                }
            }
        }).start();
    }

    // Голяма цветна плочка на сайт.
    private View makeSiteTile(final String[] site, final Runnable action) {
        int[] col = siteColors(site);
        FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(gradBg(new int[]{col[0], col[1]}, 22, 4));

        TextView wm = text(initial(site[0]), 130, 0x29FFFFFF, tfCondBold, false);
        wm.setIncludeFontPadding(false);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        wlp.topMargin = -u(22);
        wlp.rightMargin = u(6);
        t.addView(wm, wlp);

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(u(36), u(36),
                Gravity.TOP | Gravity.START);
        clp.leftMargin = u(16);
        clp.topMargin = u(14);
        t.addView(makeIconTile(hostOf(site[1]), 0xFFF1F5FF, col[1], initial(site[0]),
                u(36), u(10)), clp);

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(site[0], 20, Color.WHITE, Typeface.DEFAULT_BOLD, true));
        tx.addView(text(hostOf(site[1]), 14, 0xDDFFFFFF, tfMedium, true));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        tlp.leftMargin = u(16);
        tlp.rightMargin = u(12);
        tlp.bottomMargin = u(12);
        t.addView(tx, tlp);

        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        t.setOnFocusChangeListener(tileFocus);
        if (firstTile == null) firstTile = t;
        return t;
    }

    // Ред: икона на вида, име, брой сайтове и плочки (по TILES_PER_ROW на ред).
    private View makeLane(String group, ArrayList<String[]> list) {
        int fam = family(group);
        LinearLayout lane = new LinearLayout(this);
        lane.setOrientation(LinearLayout.HORIZONTAL);
        lane.setClipChildren(false);

        LinearLayout lab = new LinearLayout(this);
        lab.setOrientation(LinearLayout.HORIZONTAL);
        lab.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ilp = lp(u(56), u(56));
        ilp.rightMargin = u(14);
        lab.addView(iconCircle(famIcon(fam), famBg(fam), famFg(fam), 56), ilp);
        LinearLayout nt = new LinearLayout(this);
        nt.setOrientation(LinearLayout.VERTICAL);
        nt.addView(text(group, 27, Color.WHITE, tfCondBold, true));
        int n = list.size();
        nt.addView(text(n + (n == 1 ? " сайт" : " сайта"), 15, C_MUTED, tfMedium, true));
        lab.addView(nt);
        lane.addView(lab, lp(u(220), u(112)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        for (int i = 0; i < list.size(); i += TILES_PER_ROW) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            for (int k = 0; k < TILES_PER_ROW; k++) {
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, u(112), 1f);
                tlp.leftMargin = u(16);
                if (i + k < list.size()) {
                    final String[] site = list.get(i + k);
                    row.addView(makeSiteTile(site, new Runnable() {
                        @Override
                        public void run() {
                            openSite(site);
                        }
                    }), tlp);
                } else {
                    row.addView(new View(this), tlp);
                }
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = u(14);
            col.addView(row, rlp);
        }
        lane.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return lane;
    }

    // Голямата карта "Продължи гледане".
    private View makeHero(final String[] site, String label, String sub) {
        int[] col = siteColors(site);
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setMinimumHeight(u(214));
        card.setBackground(gradBg(new int[]{col[0], col[1], darker(col[1], 0.7f)}, 30, 4));

        TextView wm = text(initial(site[0]), 360, 0x1FFFFFFF, tfCondBold, false);
        wm.setIncludeFontPadding(false);
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        wlp.topMargin = -u(60);
        wlp.rightMargin = u(10);
        card.addView(wm, wlp);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(u(36), u(30), u(36), u(30));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chlp = lp(u(56), u(56));
        chlp.rightMargin = u(14);
        top.addView(makeIconTile(hostOf(site[1]), 0xFFF1F5FF, col[1], initial(site[0]),
                u(56), u(16)), chlp);
        top.addView(text(label, 18, 0xFFE6F2FB, tfMedium, true));
        body.addView(top);

        body.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.BOTTOM);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(site[0], 54, Color.WHITE, tfCondBold, true));
        TextView st = text(sub, 19, 0xFFE6F2FB, tfMedium, true);
        st.setPadding(0, u(6), 0, 0);
        tx.addView(st);
        bottom.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(u(24), 0, u(30), 0);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(Color.WHITE);
        pb.setCornerRadius(u(30));
        pill.setBackground(pb);
        int ink = darker(col[1], 0.55f);
        LinearLayout.LayoutParams plp = lp(u(24), u(24));
        plp.rightMargin = u(10);
        pill.addView(new IconView(this, IC_PLAY, ink), plp);
        pill.addView(text("Продължи", 21, ink, Typeface.DEFAULT_BOLD, true));
        bottom.addView(pill, lp(ViewGroup.LayoutParams.WRAP_CONTENT, u(60)));
        body.addView(bottom);

        card.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSite(site);
            }
        });
        card.setTag(R_SCALE, 1.02f);
        card.setOnFocusChangeListener(tileFocus);
        return card;
    }

    // Панел "Последни страници".
    private View makeRecentPanel(ArrayList<String[]> recent) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(u(20), u(16), u(20), u(10));
        p.setBackground(panelBg(30));
        p.setClipChildren(false);

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(u(8), 0, u(8), u(6));
        LinearLayout.LayoutParams iclp = lp(u(22), u(22));
        iclp.rightMargin = u(10);
        title.addView(new IconView(this, IC_CLOCK, C_MUTED), iclp);
        title.addView(text("Последни страници", 18, Color.WHITE, Typeface.DEFAULT_BOLD, true));
        p.addView(title);

        if (recent.isEmpty()) {
            TextView e = text("Още няма страници. Тук ще се появят филмите и каналите, които гледаш.",
                    16, C_MUTED, tfMedium, false);
            e.setPadding(u(8), u(10), u(8), 0);
            p.addView(e);
            return p;
        }
        for (final String[] rec : recent) {
            int[] col = colorsByName(rec[2]);
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(u(8), u(4), u(8), u(4));
            r.setMinimumHeight(u(48));
            r.setFocusable(true);
            r.setClickable(true);
            r.setBackground(stateBg(0, C_PANEL_FOCUS, Color.WHITE, u(16), 0));
            LinearLayout.LayoutParams cl = lp(u(38), u(38));
            cl.rightMargin = u(14);
            r.addView(makeIconTile(hostOf(rec[1]), 0xFFF1F5FF, col[1], initial(rec[2]),
                    u(38), u(11)), cl);
            LinearLayout tx = new LinearLayout(this);
            tx.setOrientation(LinearLayout.VERTICAL);
            tx.addView(text(rec[0], 18, Color.WHITE, tfMedium, true));
            String when = timeAgo(parseLong(rec[3]));
            tx.addView(text(when.isEmpty() ? rec[2] : rec[2] + " · " + when, 14, C_MUTED,
                    tfMedium, true));
            r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openRecent(rec);
                }
            });
            r.setOnFocusChangeListener(focusAnim);
            p.addView(r);
        }
        return p;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    // Голям бутон на дъното: икона, заглавие и поясняваща линия. Тагът пази TextView-то с линията.
    private LinearLayout dockButton(int kind, boolean danger, String title, String sub,
                                    final Runnable action) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(u(14), 0, u(18), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(danger
                ? stateBg(0xFF2A1520, 0xFF3A1D2A, Color.WHITE, u(24), 0xFF5A2438)
                : stateBg(C_PANEL, C_PANEL_FOCUS, Color.WHITE, u(24), C_PANEL_LINE));
        LinearLayout.LayoutParams ilp = lp(u(44), u(44));
        ilp.rightMargin = u(14);
        b.addView(iconCircle(kind, danger ? C_RED_BG : C_ICON_BG, danger ? C_RED_FG : C_ICON_FG, 44),
                ilp);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 19, danger ? 0xFFFECDD3 : Color.WHITE, Typeface.DEFAULT_BOLD, true));
        TextView s = text(sub, 14, danger ? C_RED_FG : C_MUTED, tfMedium, true);
        tx.addView(s);
        b.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        b.setTag(s);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setTag(R_SCALE, 1.04f);
        b.setOnFocusChangeListener(tileFocus);
        return b;
    }

    // Старият прост бутон остава за прозореца за въвеждане на текст.
    private StateListDrawable stateBg(int normalColor, int focusColor, int strokeColor, int radiusDp) {
        return stateBg(normalColor, focusColor, strokeColor, radiusDp, 0);
    }

    private View makeButton(String text, boolean danger, final Runnable action) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(danger ? 0xFFFCA5A5 : 0xFFE3E8F8);
        b.setTextSize(17);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(14), dp(15), dp(14), dp(15));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(danger
                ? stateBg(0xFF24161B, 0xFF3A1D2A, C_DANGER, dp(26))
                : stateBg(C_PANEL, C_PANEL_FOCUS, Color.WHITE, dp(26)));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setOnFocusChangeListener(focusAnim);
        return b;
    }

    private TextView cacheSub;
    private TextView autoSub;

    private String autoSubText() {
        return autoCacheOn() ? "Включено, над 200 МБ" : "Изключено";
    }

    private static final int SECTION_TV = 0, SECTION_LIBRARY = 1, SECTION_RADIO = 2,
            SECTION_SITES = 3, SECTION_SETTINGS = 4, SECTION_HOME = 5;
    private int appSection = SECTION_HOME;
    private AppNavigation appBar;
    private boolean switchingSection;
    private View sectionFocus;
    private TvGuidePanel tvOverviewGuide, tvPlayerGuide;
    private boolean tvFavouritesOnly;
    private AlertDialog sectionDialog,streamDialog,programmeDialog;
    private boolean tvControlsOpen;
    private FrameLayout tvTimelineLayer;
    private TvEpgPanel tvTimeline;

    private void showSectionMenu(){
        if(sectionDialog!=null && sectionDialog.isShowing())return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(u(32),u(27),u(32),u(27));panel.setBackground(TvTheme.panel(u(16),TvTheme.BACKGROUND,TvTheme.OUTLINE,U));
        panel.addView(text("Накъде искаш да отидеш?",30,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true));
        int[] ids={SECTION_HOME,SECTION_TV,SECTION_LIBRARY,SECTION_RADIO,SECTION_SITES,SECTION_SETTINGS};String[] names={"Начало","Телевизия","Видеотека","Радио","Сайтове","Настройки"};int[] icons={IC_HOME,IC_TV,IC_FILM,IC_RADIO,IC_MONITOR,IC_SLIDERS};
        View first=null;LinearLayout row=null;
        for(int i=0;i<6;i++){if(i%3==0){row=new LinearLayout(this);panel.addView(row,lpTopW(MP,u(76),u(18)));}final int section=ids[i];View item=tvDashboardPill(icons[i],names[i],()->{sectionDialog.dismiss();tvCloseTimeline();if(section==SECTION_TV && tvMode && !tvVod)tvShowList();else showSection(section);});LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,MP,1);if(i%3>0)cell.leftMargin=u(14);row.addView(item,cell);if(i==0)first=item;}
        if(web!=null && !tvCapturing)panel.addView(tvDashboardPill(IC_SLIDERS,"Опции за сайта",()->{sectionDialog.dismiss();showLog();}),lpTopW(MP,u(54),u(20)));
        sectionDialog=new AlertDialog.Builder(this).setView(panel).create();sectionDialog.show();if(sectionDialog.getWindow()!=null){sectionDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);sectionDialog.getWindow().setLayout(u(980),WC);}if(first!=null)first.requestFocusFromTouch();
    }

    private void showSectionSearch(){
        if(catShown()){showSearchDialog();return;}
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint(appSection==SECTION_SITES?"Име на сайт":"Име на канал");
        new AlertDialog.Builder(this).setTitle("Търсене").setView(input).setPositiveButton("Търси",(d,w)->{
            String query=input.getText().toString().trim().toLowerCase(Locale.ROOT);ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
            if(appSection==SECTION_SITES){for(String[] site:sites)if(site[0].toLowerCase(Locale.ROOT).contains(query)){labels.add(site[0]);actions.add(()->openSite(site));}}
            else{tvLoad();for(TvChan c:tvChans)if(c.name.toLowerCase(Locale.ROOT).contains(query)){labels.add(c.name);actions.add(()->tvTuneUser(c,-1));}}
            if(labels.isEmpty()){Toast.makeText(this,"Няма намерени резултати",Toast.LENGTH_SHORT).show();return;}
            new AlertDialog.Builder(this).setTitle("Резултати").setItems(labels.toArray(new String[0]),(dialog,index)->actions.get(index).run()).show();
        }).setNeutralButton("Филми и сериали",(d,w)->showCatalog(this::showSearchDialog)).setNegativeButton("Отказ",null).show();
    }

    private void tvCloseTimeline(){
        if(tvTimeline!=null)tvTimeline.release();tvTimeline=null;
        if(tvTimelineLayer!=null && tvTimelineLayer.getParent() instanceof ViewGroup)((ViewGroup)tvTimelineLayer.getParent()).removeView(tvTimelineLayer);tvTimelineLayer=null;
    }
    private void tvShowTimeline(){
        tvCloseTimeline();tvHideListNow();tvHideBannerNow();
        tvTimelineLayer=new FrameLayout(this);tvTimelineLayer.setBackground(TvTheme.background());LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(u(54),u(tvMode?70:128),u(54),u(54));
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);title.addView(text("ТВ програма",36,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true),new LinearLayout.LayoutParams(0,WC,1));title.addView(tvDashboardPill(IC_DOWN,"Назад",()->tvCloseTimeline()),lp(WC,u(50)));content.addView(title,lp(MP,u(66)));
        tvTimeline=new TvEpgPanel(this,U,guideChannels(false),tvGuide,new TvEpgPanel.Actions(){
            @Override public void watch(String id){TvChan c=tvById(id);tvCloseTimeline();if(c!=null){if(tvMode && tvCur==c && tvPlaying)tvShowBanner(4500,null);else tvTuneUser(c,-1);}}
            @Override public void programme(String id,EpgGuide.Event event){TvChan c=tvById(id);if(c!=null)tvProgrammeDialog(c,event);}
        });content.addView(tvTimeline,new LinearLayout.LayoutParams(MP,0,1));tvTimelineLayer.addView(content,new FrameLayout.LayoutParams(MP,MP));root.addView(tvTimelineLayer,new FrameLayout.LayoutParams(MP,MP));if(!tvMode && appBar!=null)appBar.bringToFront();tvTimeline.focusChannels();
    }
    private void tvProgrammeDialog(){if(tvCur!=null)tvProgrammeDialog(tvCur,tvGuide==null?null:tvGuide.current(tvCur.name,System.currentTimeMillis()));}
    private void tvProgrammeDialog(TvChan channel,EpgGuide.Event event){
        String message=event==null?"Няма налична програма за този канал.":tvEventTime(event)+"\n\n"+event.description;
        if(programmeDialog!=null)programmeDialog.dismiss();
        programmeDialog=new AlertDialog.Builder(this).setTitle(event==null?channel.name:event.title+" · "+channel.name).setMessage(message)
                .setPositiveButton("Гледай канала",(d,w)->{tvCloseTimeline();if(tvMode && tvCur==channel && tvPlaying){tvHideListNow();tvShowBanner(4500,null);}else tvTuneUser(channel,-1);})
                .setNeutralButton("Цялата програма",(d,w)->tvShowTimeline()).setNegativeButton("Назад",null).create();programmeDialog.show();
    }

    private void focusAppNavigation(){
        View focused=root.findFocus();if(focused!=null && appBar!=null && !appBar.hasFocus())sectionFocus=focused;
        if(tvOverviewGuide!=null)tvOverviewGuide.rememberPosition();
        if(appBar!=null)appBar.focusSelected();
    }

    private void focusSectionContent(){
        if(tvPanelShown()){if(tvOverviewGuide!=null)tvOverviewGuide.focusChannels();else if(tvMain!=null)tvMain.requestFocusFromTouch();}
        else if(sectionFocus!=null && sectionFocus.isShown())sectionFocus.requestFocusFromTouch();
        else if(catShown()){View target=catMain!=null?catMain:firstFocusable();if(target!=null)target.requestFocusFromTouch();}
        else if(firstTile!=null)firstTile.requestFocusFromTouch();
    }

    private void showSection(int section) {
        tvCloseTimeline();
        if (isFinishing() || switchingSection) return;
        if(section==appSection && (tvPanelShown() || catShown())){
            if(catShown() && !heroActive)catHome();else focusSectionContent();return;
        }
        switchingSection = true;
        try {
            catReturn = null;
            srcQueue = null;
            srcDone();
            teardownSite();
            sectionFocus=null;
            appSection = section;
            if (section == SECTION_TV) tvShowPanel();
            else if (section == SECTION_LIBRARY) showCatalog(null);
            else fillMenu();
            if (appBar != null) { appBar.setSection(section); appBar.setVisibility(View.VISIBLE); appBar.bringToFront(); }
        } finally { switchingSection = false; }
    }

    private void fillMenu() {
        if (menu == null) return;
        menu.removeAllViews();
        capCache = null; cacheSub = null; autoSub = null;
        heroView = null; firstTile = null; catTile = null;
        if (appBar != null) appBar.setSection(appSection);
        boolean busy = tvMode || nativeActive() || web != null || catShown();
        if (appSection == SECTION_TV) {
            if (!busy) tvShowPanel();
            return;
        }
        if (appSection == SECTION_LIBRARY) {
            if (!busy) showCatalog(null);
            return;
        }
        if (!busy) menuHolder.setVisibility(View.VISIBLE);
        menu.setGravity(Gravity.TOP);
        menu.setPadding(u(54),u(128),u(54),u(56));
        if (appSection == SECTION_HOME) fillHomePage();
        else if (appSection == SECTION_RADIO) fillRadioPage();
        else if (appSection == SECTION_SETTINGS) fillSettingsPage();
        else fillSitesPage();
        menu.post(() -> {
            if (menuHolder.getVisibility() == View.VISIBLE && firstTile != null) firstTile.requestFocus();
        });
        updateState();
    }

    private void fillHomePage() {
        int hour=java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        pageTitle(hour<12?"Добро утро":hour<18?"Добър ден":"Добър вечер","");
        tvLoad();TvChan last=tvById(prefs.getString("tvlast",""));
        if(last==null && !tvChans.isEmpty())last=tvChans.get(0);final TvChan channel=last;
        FrameLayout hero=new FrameLayout(this);hero.setClipToOutline(true);hero.setBackground(TvTheme.panel(u(16),TvTheme.SURFACE,TvTheme.OUTLINE,U));
        View gradient=new View(this);gradient.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{TvTheme.SURFACE,0xFF163354}));hero.addView(gradient,new FrameLayout.LayoutParams(MP,MP));
        AppNavigation.NavigationIcon illustration=new AppNavigation.NavigationIcon(this,0);illustration.tint=0x607BA9E3;FrameLayout.LayoutParams symbol=new FrameLayout.LayoutParams(u(235),u(235),Gravity.END|Gravity.CENTER_VERTICAL);symbol.rightMargin=u(90);hero.addView(illustration,symbol);
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setGravity(Gravity.CENTER_VERTICAL);copy.setPadding(u(44),u(28),u(44),u(28));
        copy.addView(text(channel==null?"ТВ САЙТОВЕ":"ПОСЛЕДНО ГЛЕДАН КАНАЛ",17,TvTheme.BLUE_LIGHT,Typeface.DEFAULT_BOLD,true));
        copy.addView(text(channel==null?"Всичко на едно място":channel.name,48,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true),lpTopW(MP,WC,u(16)));
        copy.addView(text(channel==null?"Телевизия, филми, радио и твоите сайтове.":"Продължи гледането или избери друг раздел.",22,TvTheme.MUTED,tfMedium,true),lpTopW(MP,WC,u(14)));
        View resume=tvDashboardPill(IC_PLAY,channel==null?"Отвори телевизията":"Продължи гледането",()->{if(channel==null)showSection(SECTION_TV);else tvTuneUser(channel,-1);});resume.setBackground(TvTheme.control(U,true));copy.addView(resume,lpTopW(WC,u(58),u(27)));
        hero.addView(copy,new FrameLayout.LayoutParams(u(1100),MP));menu.addView(hero,lpTopW(MP,u(385),u(28)));
        LinearLayout cards=new LinearLayout(this);String[] names={"Телевизия","Видеотека","Радио","Сайтове"};String[] descriptions={"Канали и програма","Филми и сериали","Слушай на живо","Всички запазени адреси"};
        for(int i=0;i<4;i++){
            final int section=i;LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(u(25),u(20),u(25),u(20));card.setFocusable(true);card.setFocusableInTouchMode(true);card.setClickable(true);card.setBackground(TvTheme.control(U,false));card.setTag("home:"+i);
            AppNavigation.NavigationIcon icon=new AppNavigation.NavigationIcon(this,i);icon.tint=TvTheme.BLUE;card.addView(icon,lp(u(34),u(34)));card.addView(text(names[i],27,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true),lpTopW(MP,WC,u(12)));card.addView(text(descriptions[i],18,TvTheme.MUTED,tfMedium,true),lpTopW(MP,WC,u(9)));card.setOnClickListener(v->showSection(section));
            LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(0,u(175),1);if(i>0)cl.leftMargin=u(20);cards.addView(card,cl);if(firstTile==null)firstTile=card;
        }
        menu.addView(cards,lpTopW(MP,u(175),u(26)));menu.addView(text("OK Отвори    MENU Всички раздели    Назад Връщане",16,TvTheme.MUTED,tfMedium,true),lpTopW(WC,WC,u(23)));
    }

    private TextView pageTitle(String title, String subtitle) {
        TextView heading = text(title, 38, TvTheme.TEXT, Typeface.DEFAULT_BOLD, true);
        menu.addView(heading);
        TextView sub = text(subtitle, 18, TvTheme.MUTED, tfMedium, false);
        if(!subtitle.isEmpty())menu.addView(sub, lpTopW(MP, WC, u(10)));
        return heading;
    }

    private int siteFilter=-1;
    private View sectionHeader(String title,View action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text(title,38,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true),new LinearLayout.LayoutParams(0,WC,1));
        if(action!=null)row.addView(action,lp(WC,u(48)));menu.addView(row,lp(MP,u(60)));return row;
    }
    private TextView filterChip(String title,boolean selected,Runnable action){
        TextView b=text(title,19,TvTheme.TEXT,tfMedium,true);b.setGravity(Gravity.CENTER);b.setPadding(u(22),0,u(22),0);b.setFocusable(true);b.setFocusableInTouchMode(true);b.setClickable(true);b.setBackground(TvTheme.control(U,selected));b.setOnClickListener(v->action.run());return b;
    }
    private void showManualSites(int family){siteFilter=family;showSection(SECTION_SITES);}
    private void fillSitesPage() {
        View manualAddress=tvDashboardPill(IC_PLUS,"Отвори адрес",()->askAddress());sectionHeader("Сайтове",manualAddress);
        LinearLayout tabs=new LinearLayout(this);String[] labels={"Всички","ТВ","Филми","Радио"};
        for(int i=0;i<labels.length;i++){final int family=i-1;View tab=filterChip(labels[i],siteFilter==family,()->{siteFilter=family;fillMenu();});LinearLayout.LayoutParams tl=lp(WC,u(44));tl.rightMargin=u(12);tabs.addView(tab,tl);}
        menu.addView(tabs,lpTopW(MP,u(44),u(22)));
        ArrayList<ModuleBrowser.Entry> entries=new ArrayList<>();
        for(String[] site:sites){if(siteFilter>=0 && family(groupOf(site))!=siteFilter)continue;entries.add(new ModuleBrowser.Entry(site[0],hostOf(site[1]),groupOf(site),3,"Отвори сайта",()->openSite(site),"",null));}
        ModuleBrowser browser=new ModuleBrowser(this,U,entries);menu.addView(browser,lpTopW(MP,WC,u(26)));firstTile=browser.first()!=null?browser.first():manualAddress;
        ArrayList<String[]> recent=loadRecent();
        if(siteFilter<0 && !recent.isEmpty()){
            menu.addView(text("Последно отваряни",25,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true),lpTopW(MP,WC,u(28)));
            for(String[] entry:recent)menu.addView(tvDashboardPill(IC_CLOCK,entry[0]+" · "+entry[2],()->openRecent(entry)),lpTopW(MP,u(52),u(10)));
        }
    }

    private void askAddress() {
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText("https://"); input.setTextColor(TvTheme.TEXT);
        new AlertDialog.Builder(this).setTitle("Отвори сайт").setView(input)
                .setPositiveButton("Отвори", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
                    if (Uri.parse(url).getHost() != null) openSite(new String[]{hostOf(url), url, "", "Сайтове"});
                }).setNegativeButton("Отказ", null).show();
    }

    private void addWebsiteGrid(LinearLayout parent, ArrayList<String[]> values) {
        LinearLayout row = null;
        for (int i = 0; i < values.size(); i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
                parent.addView(row, lpTopW(MP, u(104), u(16)));
            }
            final String[] site = values.get(i);
            LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_VERTICAL); tile.setPadding(u(22), u(14), u(22), u(14));
            tile.setFocusable(true); tile.setClickable(true); tile.setTag("site:" + site[0]);
            tile.setBackground(stateBg(TvTheme.SURFACE, TvTheme.SURFACE_ALT, TvTheme.BLUE_LIGHT, u(14), TvTheme.OUTLINE));
            tile.addView(text(site[0], 23, TvTheme.TEXT, Typeface.DEFAULT_BOLD, true));
            tile.addView(text(hostOf(site[1]), 15, TvTheme.MUTED, tfMedium, true), lpTopW(MP, WC, u(8)));
            tile.setOnClickListener(v -> openSite(site));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MP, 1f); if (i % 4 > 0) lp.leftMargin = u(14);
            row.addView(tile, lp);
            if (firstTile == null) firstTile = tile;
        }
        if (row != null) while (row.getChildCount() < 4) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
    }

    private RadioStation radioStation;
    private LinearLayout radioLayer;
    private TextView radioStatus, radioPlay;
    private Player.Listener radioListener;

    private void fillRadioPage() {
        sectionHeader("Радио",tvDashboardPill(IC_MONITOR,"Сайтове за радио",()->showManualSites(2)));
        ArrayList<ModuleBrowser.Entry> entries=new ArrayList<>();
        for(RadioStation station:RadioStation.DEFAULTS)entries.add(new ModuleBrowser.Entry(station.name,"На живо","",2,"Слушай",()->playRadio(station),"Отвори сайта",()->openRadioWebsite(station)));
        ModuleBrowser browser=new ModuleBrowser(this,U,entries);menu.addView(browser,lpTopW(MP,WC,u(28)));firstTile=browser.first();
        menu.addView(text("OK Слушай · Задръж OK за сайта на станцията · MENU Всички раздели",16,TvTheme.MUTED,tfMedium,true),lpTopW(MP,WC,u(14)));
        ArrayList<String[]> manual=new ArrayList<>();for(String[] site:sites)if(site.length>3 && "Радио".equals(site[3]))manual.add(site);
        if(!manual.isEmpty()){menu.addView(text("Сайтове за ръчно слушане",25,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true),lpTopW(MP,WC,u(26)));addWebsiteGrid(menu,manual);}
    }

    private void openRadioWebsite(RadioStation station) {
        openSite(new String[]{station.name, station.website, "", "Радио"});
    }
    private void radioAdjacent(int direction){int current=0;for(int i=0;i<RadioStation.DEFAULTS.length;i++)if(RadioStation.DEFAULTS[i].id.equals(radioStation.id))current=i;playRadio(RadioStation.DEFAULTS[(current+direction+RadioStation.DEFAULTS.length)%RadioStation.DEFAULTS.length]);}

    private void editRadioStream(RadioStation station) {
        EditText input = new EditText(this); input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(prefs.getString("radio:" + station.id, station.stream));
        new AlertDialog.Builder(this).setTitle("Адрес на потока · " + station.name).setView(input)
                .setPositiveButton("Запази и пусни", (d, w) -> {
                    String value = input.getText().toString().trim();
                    Uri uri = Uri.parse(value);
                    if (("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null) {
                        prefs.edit().putString("radio:" + station.id, value).apply(); playRadio(station);
                    } else Toast.makeText(this, "Въведи валиден http или https адрес", Toast.LENGTH_LONG).show();
                }).setNeutralButton("По подразбиране", (d,w) -> {
                    prefs.edit().remove("radio:" + station.id).apply(); playRadio(station);
                }).setNegativeButton("Отказ", null).show();
    }

    @android.annotation.SuppressLint("UnsafeOptInUsageError")
    private void playRadio(RadioStation station) {
        teardownSite(); ensureLayers();
        appSection = SECTION_RADIO; radioStation = station;
        menuHolder.setVisibility(View.GONE);
        nativeSurface.setVisibility(View.GONE);
        nativeLayer.setVisibility(View.VISIBLE);
        radioLayer = new LinearLayout(this); radioLayer.setOrientation(LinearLayout.VERTICAL);
        radioLayer.setGravity(Gravity.CENTER); radioLayer.setPadding(u(70),u(40),u(70),u(40));
        radioLayer.setBackground(TvTheme.background());
        TextView stationLogo=text(station.name.contains("NOVA")?"NOVA":station.name.contains("City")?"City":station.name.contains("БНР")?"БНР":"БГ",31,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true);stationLogo.setGravity(Gravity.CENTER);stationLogo.setBackground(TvTheme.panel(u(75),TvTheme.SURFACE_ALT,TvTheme.OUTLINE,U));radioLayer.addView(stationLogo,lp(u(150),u(150)));
        radioLayer.addView(text(station.name, 46, TvTheme.TEXT, Typeface.DEFAULT_BOLD, true), lpTopW(WC,WC,u(24)));
        radioStatus = text("Свързване…", 22, TvTheme.MUTED, tfMedium, false);
        radioStatus.setGravity(Gravity.CENTER); radioLayer.addView(radioStatus,lpTopW(MP,u(70),u(18)));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER);
        radioPlay=text("Пауза / Пусни",20,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true);
        radioPlay.setGravity(Gravity.CENTER);radioPlay.setPadding(u(24),0,u(24),0);
        radioPlay.setFocusable(true);radioPlay.setClickable(true);
        radioPlay.setBackground(TvTheme.control(U,true));
        radioPlay.setOnClickListener(v->{if(player!=null){player.setPlayWhenReady(!player.getPlayWhenReady());radioPlaybackStatus();}});
        controls.addView(tvDashboardPill(IC_DOWN,"Предишна",()->radioAdjacent(-1)),lp(WC,u(60)));
        controls.addView(radioPlay,lp(WC,u(60)));
        controls.addView(tvDashboardPill(IC_PLAY,"Следваща",()->radioAdjacent(1)),lp(WC,u(60)));
        radioLayer.addView(controls,lpTopW(WC,u(60),u(26)));
        controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(tvDashboardPill(IC_MONITOR,"Отвори сайта",()->openRadioWebsite(station)),lp(WC,u(60)));
        controls.addView(tvDashboardPill(IC_SLIDERS,"Адрес на потока",()->editRadioStream(station)),lp(WC,u(60)));
        controls.addView(tvDashboardPill(IC_HOME,"Станции",()->showSection(SECTION_RADIO)),lp(WC,u(60)));
        controls.addView(tvDashboardPill(IC_CLOCK,"Таймер",()->chooseSleep()),lp(WC,u(60)));
        controls.addView(tvDashboardPill(IC_BARS,"Меню",()->showSectionMenu()),lp(WC,u(60)));
        radioLayer.addView(controls,lpTopW(MP,u(60),u(26)));
        nativeLayer.addView(radioLayer,new FrameLayout.LayoutParams(MP,MP));
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12000).setReadTimeoutMs(15000).setUserAgent("TVSites Radio");
        DefaultLoadControl load = new DefaultLoadControl.Builder().setBufferDurationsMs(5000,15000,500,1500)
                .setTargetBufferBytes(1024 * 1024).setPrioritizeTimeOverSizeThresholds(false).build();
        player = new ExoPlayer.Builder(this).setLoadControl(load).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();
        radioListener = new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) { radioPlaybackStatus(); }
            @Override public void onIsPlayingChanged(boolean playing) { radioPlaybackStatus(); }
            @Override public void onPlayerError(PlaybackException error) {
                if(radioStatus!=null)radioStatus.setText("Потокът не е достъпен. Опитай сайта или промени адреса на потока.");
            }
        };
        player.addListener(radioListener);
        player.setMediaItem(MediaItem.fromUri(prefs.getString("radio:"+station.id,station.stream)));
        player.prepare(); player.setPlayWhenReady(true);
        radioPlay.requestFocus(); updateState();
    }

    @android.annotation.SuppressLint("UnsafeOptInUsageError")
    private void radioPlaybackStatus() {
        if(radioStation==null || player==null || radioStatus==null)return;
        int state=player.getPlaybackState();
        radioStatus.setText(player.getPlayerError()!=null?"Потокът не е достъпен. Можеш да отвориш сайта.":
                state==Player.STATE_BUFFERING?"Свързване…":state==Player.STATE_READY?
                (player.getPlayWhenReady()?"Слушаш на живо":"Пауза"):state==Player.STATE_ENDED?"Потокът приключи":"Свързване…");
        if(radioPlay!=null)radioPlay.setText(player.getPlayWhenReady()?"❚❚ Пауза":"▶ Пусни");
    }

    private void fillSettingsPage() {
        pageTitle("Настройки", "Версия "+installedName());
        String[] labels={"Източници и субтитри","Качество на видеото","Таймер за заспиване","Провери за версия","Изчисти кеша","Автоматично чистене","Диагностика","Изход"};
        String[] details={"Филми, сериали и субтитри","Автоматично или предпочитано качество","Спиране след избраното време","Обновяване на приложението","Ръчно освобождаване на памет",autoSubText(),"RAM, журнал и състояние","Затваряне на приложението"};
        int[] icons={IC_FILM,IC_TV,IC_CLOCK,IC_REFRESH,IC_TRASH,IC_CHIP,IC_BARS,IC_POWER};
        Runnable[] actions={()->showSourcesDialog(),()->tvQualityDialog(),()->chooseSleep(),()->checkUpdate(),()->clearCacheNow(),()->{toggleAutoCache();fillMenu();},()->showDiagnostics(),()->exitApp()};
        LinearLayout row=null;
        for(int i=0;i<labels.length;i++){
            if(i%2==0){row=new LinearLayout(this);menu.addView(row,lpTopW(MP,u(108),u(18)));}
            final int action=i;LinearLayout box=new LinearLayout(this);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(u(26),u(19),u(26),u(19));box.setFocusable(true);box.setClickable(true);box.setBackground(TvTheme.control(U,false));box.addView(new IconView(this,icons[i],TvTheme.BLUE_LIGHT),lp(u(30),u(30)));
            LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.addView(text(labels[i],23,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true));copy.addView(text(details[i],18,TvTheme.MUTED,tfMedium,true),lpTopW(MP,WC,u(8)));LinearLayout.LayoutParams copyLp=new LinearLayout.LayoutParams(0,WC,1);copyLp.leftMargin=u(22);box.addView(copy,copyLp);box.setOnClickListener(v->actions[action].run());LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,MP,1);if(i%2>0)cell.leftMargin=u(18);row.addView(box,cell);if(firstTile==null)firstTile=box;
        }
    }

    private void tvCategories() {
        ArrayList<String> labels = new ArrayList<>(); ArrayList<Integer> ids = new ArrayList<>();
        labels.add("Всички канали"); ids.add(TV_HOME);
        for (int i = 0; i < TV_CAT_NAMES.length; i++) if (tvCatCnt[i] > 0) { labels.add(TV_CAT_NAMES[i]); ids.add(i); }
        new AlertDialog.Builder(this).setTitle("Категории").setItems(labels.toArray(new String[0]),
                (d, which) -> { tvFavouritesOnly = false; tvSelect(ids.get(which));tvFillHead(""); }).show();
    }

    private TvChan tvById(String id) {
        for (TvChan c : tvChans) if (c.id.equals(id)) return c;
        return null;
    }

    private TvGuidePanel newGuide(boolean overVideo) {
        return new TvGuidePanel(this, U, new TvGuidePanel.Actions() {
            @Override public void watch(String id) {
                TvChan c = tvById(id); if (c == null) return;
                if (tvMode && c == tvCur && tvPlaying) { tvHideListNow(); tvShowBanner(4500, null); }
                else tvTuneUser(c, -1);
            }
            @Override public void sources(String id) { TvChan c = tvById(id); if (c != null) tvChannelMenu(c); }
            @Override public void favourite(String id) {
                String key = "tvfav:" + id; prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply();
                if (overVideo) tvShowList(); else tvBuildContent();
            }
            @Override public void menu() { focusAppNavigation(); }
            @Override public void retry() { tvGuideLoadedAt=0; tvEpgLoad(); }
            @Override public View logo(String name) { TvPlate logo = new TvPlate(MainActivity.this, 8, 5, !overVideo); logo.showNameWhileLoading=true; logo.setChan(name); return logo; }
        }, overVideo);
    }

    private ArrayList<TvGuidePanel.Channel> guideChannels(boolean filtered) {
        ArrayList<TvGuidePanel.Channel> out = new ArrayList<>();
        for (TvChan c : tvChans) {
            boolean favourite = prefs.getBoolean("tvfav:" + c.id, false);
            if (filtered && tvFavouritesOnly && !favourite) continue;
            if (filtered && tvSel >= 0 && tvCatOfName(c.name) != tvSel) continue;
            out.add(new TvGuidePanel.Channel(c.id, c.name, favourite));
        }
        return out;
    }

    // ---------- Последни страници ----------

    // Всеки ред във файла с настройките: адрес, сайт, време, заглавие (разделени с табулация).
    // Връща масиви {заглавие, адрес, сайт, време}, най-новата е първа.
    private ArrayList<String[]> loadRecent() {
        ArrayList<String[]> out = new ArrayList<>();
        String raw = prefs.getString("recent", "");
        for (String line : raw.split("\n")) {
            String[] p = line.split("\t", 4);
            if (p.length == 4 && p[0].startsWith("http")) {
                out.add(new String[]{p[3], p[0], p[1], p[2]});
            } else if (p.length == 3 && p[0].startsWith("http")) {
                out.add(new String[]{p[2], p[0], p[1], "0"});
            }
        }
        return out;
    }

    private static String oneLine(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    // Заглавието на страницата без името на сайта накрая ("Филм - FilmiZip").
    private static String cleanTitle(String t, Uri uri) {
        if (t != null) {
            t = oneLine(t);
            int cut = -1;
            for (String sep : new String[]{" | ", " - ", " – ", " — ", " » ", " :: "}) {
                int i = t.lastIndexOf(sep);
                if (i > 8 && i > cut) cut = i;
            }
            if (cut > 0) t = t.substring(0, cut).trim();
        }
        if (t == null || t.length() < 2 || t.startsWith("http")) {
            String seg = uri.getLastPathSegment();
            t = seg == null ? String.valueOf(uri.getHost()) : seg.replace('-', ' ').replace('_', ' ');
        }
        if (t.length() > 70) t = t.substring(0, 67) + "...";
        return t;
    }

    // Запомня страницата, на която си в момента (вика се малко след всяка смяна на адреса,
    // при излизане от сайта и при изход). Началната страница на сайта не се записва.
    private void rememberPage() {
        WebView w = web;
        if (w == null || allowedHost == null || menuHolder.getVisibility() == View.VISIBLE) return;
        String u = w.getUrl();
        if (u == null || !u.startsWith("http")) return;
        Uri uri = Uri.parse(u);
        String host = uri.getHost();
        if (host == null || !isOwnHost(host)) return;
        String path = uri.getPath();
        if ((path == null || path.isEmpty() || path.equals("/")) && uri.getQuery() == null) return;
        String title = cleanTitle(w.getTitle(), uri);
        String site = curSiteName.isEmpty() ? hostOf(u) : curSiteName;
        long now = System.currentTimeMillis();

        ArrayList<String[]> l = loadRecent();
        if (!l.isEmpty() && l.get(0)[1].equals(u) && l.get(0)[0].equals(title)
                && now - parseLong(l.get(0)[3]) < 120000L) return;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i)[1].equals(u)) l.remove(i);
        }
        l.add(0, new String[]{title, u, site, String.valueOf(now)});
        while (l.size() > RECENT_MAX) l.remove(l.size() - 1);
        StringBuilder sb = new StringBuilder();
        for (String[] e : l) {
            sb.append(e[1]).append('\t').append(oneLine(e[2])).append('\t')
                    .append(e[3]).append('\t').append(oneLine(e[0])).append('\n');
        }
        prefs.edit().putString("recent", sb.toString()).apply();
    }

    private void openRecent(String[] rec) {
        String[] s = findSite(rec[1], rec[2]);
        openSite(new String[]{s[0], rec[1], s.length > 2 ? s[2] : "", s.length > 3 ? s[3] : ""});
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

    private final AtomicBoolean scanBusy = new AtomicBoolean();
    private volatile boolean scanDecide;

    private void refreshCacheLabel() {
        scanCache(false);
    }

    // Смята размера на кеша на заден план (само едно измерване наведнъж) и го показва.
    // При decide=true решава и дали да чисти: автоматично над 200 МБ (ако е включено),
    // иначе пита над 150 МБ.
    private void scanCache(boolean decide) {
        if (decide) scanDecide = true;
        if (!scanBusy.compareAndSet(false, true)) return;
        disk(new Runnable() {
            @Override
            public void run() {
                long s = 0;
                try {
                    s = dirSize(getCacheDir());
                } finally {
                    scanBusy.set(false);
                }
                final long size = s;
                final boolean d = scanDecide;
                scanDecide = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        showCacheSize(size);
                        if (d) decideCache(size);
                    }
                });
            }
        });
    }

    private void showCacheSize(long size) {
        cacheSizeText = mb(size);
        if (cacheSub != null) cacheSub.setText("Заема " + cacheSizeText);
        if (capCache != null) capCache.setText(cacheSizeText);
        if (logShown()) refreshLog(Math.max(1, logList.getSelectedItemPosition()));
    }

    // Ако кешът е изчистен ръчно и е малък, не се прави нищо.
    private void decideCache(final long size) {
        if (menuHolder.getVisibility() != View.VISIBLE) return;
        if (autoCacheOn()) {
            if (size >= CACHE_AUTO_BYTES) clearCache(true);
            return;
        }
        if (size < CACHE_ASK_BYTES) return;
        long last = prefs.getLong("cacheAsk", 0);
        if (System.currentTimeMillis() - last < CACHE_ASK_EVERY_MS) return;
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

    // Сменя между нормална и мобилна версия за отворения сайт, запомня избора и презарежда.
    private void toggleSiteMode() {
        if (allowedHost == null || web == null) return;
        boolean toMobile = !"mobile".equals(curMode);
        prefs.edit().putString("mode:" + allowedHost, toMobile ? "mobile" : "normal").apply();
        curMode = toMobile ? "mobile" : "";
        web.getSettings().setUserAgentString(toMobile ? MOBILE_UA : null);
        Toast.makeText(this, toMobile ? "Мобилна версия" : "Нормална версия",
                Toast.LENGTH_SHORT).show();
        hideLog();
        web.reload();
    }

    private boolean autoCacheOn() {
        return prefs.getBoolean("autoCache", true);
    }

    private void toggleAutoCache() {
        boolean on = !autoCacheOn();
        prefs.edit().putBoolean("autoCache", on).apply();
        Toast.makeText(this, on ? "Автоматичното чистене е включено (над 200 МБ)"
                : "Автоматичното чистене е изключено", Toast.LENGTH_SHORT).show();
        if (autoSub != null) autoSub.setText(autoSubText());
    }


    private void clearCacheNow() {
        clearCache(false);
    }

    // Обхождането на файлове е на заден план; само самото WebView се чисти от главната нишка.
    private void clearCache(final boolean quiet) {
        final boolean keepUpdates = pendingApk != null;
        disk(new Runnable() {
            @Override
            public void run() {
                final long before = dirSize(getCacheDir());
                // Старият свален файл за актуализация вече не трябва, освен ако чака инсталиране.
                File[] l = getCacheDir().listFiles();
                if (l != null) {
                    for (File c : l) {
                        if (c.getName().equals("WebView")) continue;
                        if (c.getName().equals("updates") && keepUpdates) continue;
                        deleteTree(c, false);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        clearWebCache();
                        // Браузърът трие на заден план, затова размерът се мери малко по-късно.
                        root.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                reportCleared(before, quiet);
                            }
                        }, 1500);
                    }
                });
            }
        });
    }

    private void clearWebCache() {
        if (web != null) {
            web.clearCache(true);
            return;
        }
        // Без отворена страница чистим през кратко живо WebView, за да е безопасно.
        try {
            WebView tmp = new WebView(this);
            tmp.clearCache(true);
            tmp.destroy();
        } catch (Throwable ignored) {
        }
    }

    private void reportCleared(final long before, final boolean quiet) {
        disk(new Runnable() {
            @Override
            public void run() {
                final long after = dirSize(getCacheDir());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        Toast.makeText(MainActivity.this,
                                (quiet ? "Кешът е изчистен автоматично: " : "Изчистено: ")
                                        + mb(Math.max(0, before - after))
                                        + " (остава " + mb(after) + ")", Toast.LENGTH_LONG).show();
                        showCacheSize(after);
                    }
                });
            }
        });
    }

    // ---------- Отваряне на сайт ----------

    private void openSite(String[] site) {
        if (appBar != null) appBar.setVisibility(View.GONE);
        // Switching sections releases the previous section before creating a browser.
        tvReset();
        if (catShown()) hideCatalog();
        closeNative(false);
        destroyWeb();
        String url = site[1];
        String mode = site.length > 2 ? site[2] : "";
        allowedHost = Uri.parse(url).getHost();
        srcQueue = null;
        srcIdx = 0;
        catReturn = null;
        cursorTemp = false;
        scrollMode = false;
        hovering = false;
        cancelOk();
        curSiteName = site[0];
        // Личният избор на потребителя (от журнала) е по-важен от настройката по подразбиране.
        String chosen = prefs.getString("mode:" + allowedHost, null);
        if (chosen != null) mode = chosen.equals("mobile") ? "mobile" : "";
        curMode = mode;
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
        logEvent("Отворен сайт: " + site[0] + (curMode.isEmpty() ? "" : " (" + curMode + ")"), true);

        if (web == null) createWebView();
        applyUserAgent();

        menuHolder.setVisibility(View.GONE);
        showWebsiteToolbar();
        web.setVisibility(View.VISIBLE);
        startWatch();

        // Курсорът започва в средата на екрана.
        cx = (root.getWidth() > 0 ? root.getWidth() : 1920) / 2f;
        cy = (root.getHeight() > 0 ? root.getHeight() : 1080) / 2f;
        updateCursor();
        pokeCursor();

        showSplash(site[0], hostOf(site[1]));
        web.loadUrl(url);
        web.requestFocus();
    }

    // Мобилна или десктоп версия по сайт (според curMode).
    private void applyUserAgent() {
        String ua = null;
        if ("mobile".equals(curMode)) ua = MOBILE_UA;
        else if ("desktop".equals(curMode)) ua = DESKTOP_UA;
        web.getSettings().setUserAgentString(ua);
    }

    private void createWebView() {
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // Ненужни възможности: по-малко работа и по-малко повърхност за реклами.
        s.setGeolocationEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
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
                if(v!=web)return new WebResourceResponse("text/plain","utf-8",new java.io.ByteArrayInputStream(new byte[0]));
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
                // Discovery only needs the addresses. A player in a cross-origin frame
                // must not decode media behind the native player.
                if (v == tvSearchWeb && tvSearchMedia(r.getUrl())) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new java.io.ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(v, r);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                // Вътрешна връзка от страницата за грешка: "Към менюто".
                if ("tvsites".equals(r.getUrl().getScheme())) {
                    root.post(new Runnable() {
                        @Override
                        public void run() {
                            backToMenu();
                        }
                    });
                    return true;
                }
                // Блокира пренасочвания на главната страница към други домейни (реклами).
                String host = r.getUrl().getHost();
                return r.isForMainFrame() && host != null && allowedHost != null
                        && !isOwnHost(host);
            }

            @Override
            public void onPageStarted(WebView v, String u, Bitmap favicon) {
                if(v!=web)return;
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

            // Вика се и при преходи вътре в сайта без ново зареждане. Страницата се запомня
            // след няколко секунди, ако си останал на нея (не при бързо прелистване).
            @Override
            public void doUpdateVisitedHistory(WebView v, String u, boolean isReload) {
                ui.removeCallbacks(rememberTick);
                if (u != null && u.startsWith("http")) ui.postDelayed(rememberTick, 6000);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                if (v != web) return;
                // След връщане към менюто чистим историята, за да не се връщаме в празна страница.
                if (clearHistoryPending && "about:blank".equals(u)) {
                    v.clearHistory();
                    clearHistoryPending = false;
                }
                if (u != null && u.startsWith("http")) hideSplash();
                if (u != null && u.startsWith("http")) srcDone();
                if (tvCapturing && u != null && u.startsWith("http")) tvPageFinished();
            }

            // Страница с код 404 или 5xx (няма такъв филм там): минава на следващия източник.
            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r,
                                            WebResourceResponse resp) {
                if(v!=web)return;
                if (r == null || resp == null || !r.isForMainFrame()) return;
                if (resp.getStatusCode() >= 400 && tvCapturing) {
                    tvCaptureError("код " + resp.getStatusCode());
                    return;
                }
                if (resp.getStatusCode() >= 400 && srcQueue != null) tryNextSource();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError err) {
                if(v!=web)return;
                if (r == null || !r.isForMainFrame()) return;
                String u = r.getUrl().toString();
                if (!u.startsWith("http")) return;
                String desc = err == null ? "" : String.valueOf(err.getDescription());
                if (desc.contains("ERR_ABORTED")) return;
                logEvent("Грешка при зареждане: " + r.getUrl().getHost() + " " + desc);
                if (tvCapturing) {
                    tvCaptureError(desc);
                    return;
                }
                if (tryNextSource()) return;
                showErrorPage(v, u, desc);
            }

            // Системата е спряла процеса на страницата (най-често от липса на памет).
            // Без това приложението се затваря изцяло.
            @Override
            public boolean onRenderProcessGone(WebView v, android.webkit.RenderProcessGoneDetail d) {
                final boolean crashed = d != null && d.didCrash();
                root.post(new Runnable() {
                    @Override
                    public void run() {
                        if(v==web)recoverFromRendererLoss(crashed);
                    }
                });
                return true;
            }
        });

        chromeClient = new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                if (tvCapturing) {
                    // Скритото търсене на поток: цял екран не се позволява.
                    cb.onCustomViewHidden();
                    return;
                }
                // Цял екран за видеото
                customView = view;
                customCallback = cb;
                root.addView(view, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                web.setVisibility(View.GONE);
                if (cursor != null) cursor.bringToFront(); // курсорът е над видеото
                cursorIdle = true; // OK пуска/спира, докато не мръднеш със стрелка
                updateState();
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                endSwipe(true);
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

            @Override
            public void onProgressChanged(WebView v, int p) {
                if(v!=web)return;
                if (loadBar == null) return;
                String u = v.getUrl();
                boolean real = u != null && u.startsWith("http");
                if (real && p < 100) {
                    loadBar.setVisibility(View.VISIBLE);
                    loadBar.setScaleX(p / 100f);
                } else {
                    loadBar.setVisibility(View.GONE);
                }
                if (real && p >= 60) hideSplash();
            }
        };
        web.setWebChromeClient(chromeClient);

        // WebView е винаги веднага над менюто, под курсора и панелите.
        root.addView(web, 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Останалите слоеве се правят само веднъж (WebView може да се създава наново след срив).
        ensureLayers();
    }

    // Страницата е спряна от системата: връщаме се в менюто със съобщение вместо да се затваря всичко.
    private void recoverFromRendererLoss(boolean crashed) {
        if (web == null) return; // страницата вече е затворена
        if (tvCapturing) {
            // Скритата страница за търсене на поток е спряна: пробва се следващата стъпка.
            tvCaptureError("страницата е спряна от системата");
            return;
        }
        root.removeCallbacks(cacheWatch);
        endSwipe(true);
        if (customView != null) {
            root.removeView(customView);
            customView = null;
            customCallback = null;
        }
        logEvent("Страницата е спряна от системата" + (crashed ? " (срив)" : " (липса на памет)"));
        destroyWeb();
        backToMenu();
        showInfo("Страницата беше спряна",
                (crashed ? "Страницата се срина." : "Системата спря страницата, защото приставката остана без памет.")
                        + "\n\nОтвори сайта отново. Ако се повтаря, в Опции (задръж OK) смени "
                        + "\"Версия на сайта\" на мобилна - тя е по-лека.");
    }

    private static String stripWww(String host) {
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    // Затваря WebView и освобождава паметта му (бисквитките остават записани).
    private void showWebsiteToolbar(){
        if(web==null || tvCapturing)return;
        if(websiteToolbar!=null)root.removeView(websiteToolbar);
        websiteToolbar=new LinearLayout(this);websiteToolbar.setGravity(Gravity.CENTER_VERTICAL);websiteToolbar.setPadding(u(32),0,u(32),0);websiteToolbar.setBackgroundColor(TvTheme.BACKGROUND);
        websiteToolbar.addView(tvDashboardPill(IC_HOME,"Сайтове",()->showSection(SECTION_SITES)),lp(WC,u(48)));
        websiteToolbar.addView(tvDashboardPill(IC_REFRESH,"",()->{if(web!=null)web.reload();}),lp(u(56),u(48)));
        TextView address=text(curSiteName+" · "+(allowedHost==null?"":allowedHost),18,TvTheme.MUTED,tfMedium,true);address.setFocusable(true);address.setClickable(true);address.setPadding(u(16),0,u(16),0);address.setBackground(TvTheme.control(U,false));address.setOnClickListener(v->askAddress());LinearLayout.LayoutParams url=new LinearLayout.LayoutParams(0,u(48),1);url.leftMargin=u(12);url.rightMargin=u(12);websiteToolbar.addView(address,url);
        websiteToolbar.addView(tvDashboardPill(IC_CURSOR,"Курсор",()->toggleCursor()),lp(WC,u(48)));websiteToolbar.addView(tvDashboardPill(IC_SLIDERS,"Опции",()->showLog()),lp(WC,u(48)));websiteToolbar.addView(tvDashboardPill(IC_BARS,"Меню",()->showSectionMenu()),lp(WC,u(48)));
        root.addView(websiteToolbar,new FrameLayout.LayoutParams(MP,u(86),Gravity.TOP));FrameLayout.LayoutParams page=(FrameLayout.LayoutParams)web.getLayoutParams();page.topMargin=u(86);web.setLayoutParams(page);
    }

    private void destroyWeb() {
        WebView previous=web;web=null;
        if(websiteToolbar!=null){root.removeView(websiteToolbar);websiteToolbar=null;}
        if(previous==null)return;
        try{CookieManager.getInstance().flush();}catch(Exception ignored){}
        try{previous.stopLoading();}catch(Exception ignored){}
        try{previous.onPause();}catch(Exception ignored){}
        try{previous.setWebViewClient(null);previous.setWebChromeClient(null);}catch(Exception ignored){}
        try{root.removeView(previous);previous.removeAllViews();}catch(Exception ignored){}
        try{previous.destroy();}catch(Exception ignored){}
    }

    // Спира всичко, което върви в сайта, и изхвърля страницата напълно:
    // паметта се освобождава и всеки сайт започва на чисто.
    private void teardownSite() {
        tvReset();
        if (catShown()) hideCatalog();
        endSwipe(true);
        cancelOk();
        cursorTemp = false;
        scrollMode = false;
        hovering = false;
        if (cursor != null) cursor.setScroll(false);
        ui.removeCallbacks(rememberTick);
        rememberPage();
        root.removeCallbacks(cacheWatch);
        closeNative(false);
        if (logPanel != null) logPanel.setVisibility(View.GONE);
        if (customView != null && chromeClient != null) chromeClient.onHideCustomView();
        destroyWeb();
        menuHolder.setVisibility(View.VISIBLE);
        resetOverlays();
        updateState();
    }

    // Връща към менюто и спира всичко, което върви.
    private void backToMenu() {
        if (menuHolder.getVisibility() != View.VISIBLE) logEvent("Връщане към менюто");
        teardownSite();
        fillMenu();
        scanCache(true);
        Runnable back = catReturn;
        catReturn = null;
        srcQueue = null;
        srcDone();
        if (back != null) back.run();
    }

    // Пълно затваряне: приложението и процесът му се прекратяват.
    private void exitApp() {
        logEvent("Изход с бутона");
        catReturn = null;
        ui.removeCallbacks(rememberTick);
        rememberPage();
        exiting = true;
        root.removeCallbacks(cacheWatch);
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
        if (appBar != null) {
            boolean viewing = tvMode || nativeActive() || (web != null && web.getVisibility() == View.VISIBLE) || customView != null;
            appBar.setVisibility(viewing ? View.GONE : View.VISIBLE);
            if (!viewing) appBar.bringToFront();
            updateAppMemory();
        }
        if (cursor != null) {
            boolean can = webActive() || (customView != null && !logShown() && !nativeActive());
            cursor.setVisibility(can && cursorOn() && (!cursorIdle || scrollMode)
                    ? View.VISIBLE : View.GONE);
            if (!can && scrollMode) {
                scrollMode = false;
                endSwipe(true);
                cursor.setScroll(false);
            }
        }
        // Докато гледаш, екранът не заспива.
        boolean inSite = customView != null || nativeActive()
                || (web != null && web.getVisibility() == View.VISIBLE);
        // Флагът се сменя само при промяна. Всяка смяна кара системата да преизчислява
        // прозореца, а тук се минава при всяко натискане на стрелка (движение на курсора).
        if (inSite != keepOn) {
            keepOn = inSite;
            if (inSite) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }

    private boolean keepOn;

    // ---------- Зареждане, грешки и подсказки ----------

    private void buildLoadingUi() {
        splash = new FrameLayout(this);
        splash.setBackground(pageBg());
        splash.setVisibility(View.GONE);
        splashBox = new LinearLayout(this);
        splashBox.setOrientation(LinearLayout.VERTICAL);
        splashBox.setGravity(Gravity.CENTER_HORIZONTAL);
        splash.addView(splashBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        loadBar = new View(this);
        loadBar.setBackgroundColor(C_AMBER);
        loadBar.setPivotX(0f);
        loadBar.setScaleX(0f);
        loadBar.setVisibility(View.GONE);
        root.addView(loadBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        hintChip = new TextView(this);
        hintChip.setTextColor(Color.WHITE);
        hintChip.setTextSize(17);
        hintChip.setPadding(dp(26), dp(13), dp(26), dp(13));
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(0xDD0B1020);
        hb.setCornerRadius(dp(30));
        hb.setStroke(dp(1), 0x55FFFFFF);
        hintChip.setBackground(hb);
        hintChip.setVisibility(View.GONE);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hlp.bottomMargin = dp(44);
        root.addView(hintChip, hlp);
    }

    // Екран "Зареждане" с иконата на сайта, докато страницата се появи.
    private void showSplash(String name, String host) {
        if (splash == null) return;
        splashBox.removeAllViews();
        splashBox.addView(makeIconTile(host, 0xFFF1F5FF, badgeColor(name), initial(name), dp(96), dp(24)),
                new LinearLayout.LayoutParams(dp(96), dp(96)));
        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(Color.WHITE);
        t.setTextSize(32);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(22), 0, dp(2));
        splashBox.addView(t);
        TextView s = new TextView(this);
        s.setText("Зареждане...");
        s.setTextColor(C_MUTED);
        s.setTextSize(17);
        splashBox.addView(s);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        pb.getIndeterminateDrawable().setColorFilter(C_AMBER, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(44), dp(44));
        plp.topMargin = dp(26);
        splashBox.addView(pb, plp);

        splash.animate().cancel();
        splash.setAlpha(1f);
        splash.setVisibility(View.VISIBLE);
        root.removeCallbacks(splashTimeout);
        root.postDelayed(splashTimeout, 15000);
    }

    private void hideSplash() {
        root.removeCallbacks(splashTimeout);
        if (splash == null || splash.getVisibility() != View.VISIBLE) return;
        if (splash.getAlpha() < 0.99f) return; // вече се скрива
        splash.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
            @Override
            public void run() {
                splash.setVisibility(View.GONE);
                maybeShowHint();
            }
        }).start();
    }

    // Скрива всички временни слоеве (при връщане към менюто).
    private void resetOverlays() {
        root.removeCallbacks(splashTimeout);
        root.removeCallbacks(hideHint);
        if (splash != null) {
            splash.animate().cancel();
            splash.setVisibility(View.GONE);
        }
        if (hintChip != null) {
            hintChip.animate().cancel();
            hintChip.setVisibility(View.GONE);
        }
        if (loadBar != null) loadBar.setVisibility(View.GONE);
    }

    // Първите няколко пъти показва кратка подсказка как се управлява сайтът.
    private void maybeShowHint() {
        if (hintChip == null || web == null || web.getVisibility() != View.VISIBLE) return;
        if (menuHolder.getVisibility() == View.VISIBLE) return;
        int n = prefs.getInt("hintCount3", 0);
        if (n >= 4) return;
        prefs.edit().putInt("hintCount3", n + 1).apply();
        hintChip.setText("Назад — към менюто    •    Задръж OK — опции    •    Двойно OK — превъртане");
        hintChip.animate().cancel();
        hintChip.setAlpha(0f);
        hintChip.setVisibility(View.VISIBLE);
        hintChip.animate().alpha(1f).setDuration(250).start();
        root.removeCallbacks(hideHint);
        root.postDelayed(hideHint, 7000);
    }

    // Приятелска страница вместо грозната грешка на браузъра.
    private void showErrorPage(WebView v, String url, String desc) {
        hideSplash();
        if (loadBar != null) loadBar.setVisibility(View.GONE);
        String u = TextUtils.htmlEncode(url);
        String html = "<!doctype html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>"
                + "body{margin:0;min-height:100vh;display:flex;align-items:center;"
                + "justify-content:center;background:linear-gradient(135deg,#140c3a,#241261 55%,#0e1a55);"
                + "font-family:sans-serif;color:#fff;text-align:center}"
                + ".c{max-width:780px;padding:40px}"
                + ".i{font-size:80px;line-height:1;margin-bottom:16px}"
                + "h1{font-size:46px;margin:0 0 16px}"
                + "p{font-size:25px;color:#c9c3f5;line-height:1.45;margin:0 0 40px}"
                + "a{display:inline-block;margin:8px 12px;padding:22px 48px;border-radius:18px;"
                + "font-size:28px;font-weight:700;text-decoration:none;color:#fff;background:#3b3680;"
                + "border:4px solid transparent}"
                + "a.p{background:linear-gradient(90deg,#7c3aed,#d62bb8)}"
                + "a:focus{border-color:#ffb020;outline:none}"
                + "small{display:block;margin-top:44px;color:#7f87b8;font-size:17px}"
                + "</style></head><body><div class='c'><div class='i'>&#9888;</div>"
                + "<h1>Страницата не се зареди</h1>"
                + "<p>Провери дали приставката е свързана с интернет и опитай пак. "
                + "Ако проблемът остане, сайтът може да е временно недостъпен.</p>"
                + "<a class='p' href='" + u + "' autofocus>Опитай пак</a>"
                + "<a href='tvsites://menu'>Към менюто</a>"
                + "<small>" + TextUtils.htmlEncode(desc == null ? "" : desc) + "</small>"
                + "</div></body></html>";
        v.loadDataWithBaseURL(url, html, "text/html", "utf-8", url);
    }

    // ---------- Блокиране на домейни ----------

    // Проверка по домейна и по всичките му родителски домейни (a.b.c.com, b.c.com, c.com).
    private static boolean inSet(java.util.Set<String> set, String host) {
        if (set.isEmpty()) return false;
        String h = host;
        while (true) {
            if (set.contains(h)) return true;
            int i = h.indexOf('.');
            if (i < 0) return false;
            h = h.substring(i + 1);
        }
    }

    // Решението за всеки домейн се помни: една страница праща стотици заявки към едни и същи
    // домейни, а проверката се прави за всяка от тях.
    private final ConcurrentHashMap<String, Boolean> blockCache = new ConcurrentHashMap<>();

    private boolean isBlocked(String host) {
        if (host == null) return false;
        Boolean cached = blockCache.get(host);
        if (cached != null) return cached;
        boolean b;
        if (inSet(userAllowed, host)) b = false;
        else if (inSet(userBlocked, host)) b = true;
        else if (inSet(cfgAllowed, host)) b = false;
        else if (inSet(cfgBlocked, host)) b = true;
        else b = inSet(BLOCKED_SET, host);
        if (blockCache.size() > 3000) blockCache.clear();
        blockCache.put(host, b);
        return b;
    }

    private boolean isOwnHost(String host) {
        if (allowedHost == null) return false;
        String base = stripWww(allowedHost);
        if (host.equals(base) || host.endsWith("." + base)) return true;
        // Източник от каталога: същият сайт е позволен (api.пример.com -> player.пример.com),
        // както и всеки от зададените източници (основен и резервни).
        if (srcQueue != null) {
            if (siteBase(host).equals(siteBase(base))) return true;
            for (String u : srcQueue) {
                String h = Uri.parse(u).getHost();
                if (h != null && (host.equals(h) || siteBase(host).equals(siteBase(h)))) return true;
            }
        }
        return false;
    }

    // Последните две части на адреса (пример.com), без да различава поддомейните.
    private static String siteBase(String host) {
        int last = host.lastIndexOf('.');
        if (last <= 0) return host;
        int prev = host.lastIndexOf('.', last - 1);
        return prev < 0 ? host : host.substring(prev + 1);
    }

    private void loadLists() {
        userBlocked.addAll(prefs.getStringSet("blocked", new HashSet<String>()));
        userAllowed.addAll(prefs.getStringSet("allowed", new HashSet<String>()));
        cursorOff.addAll(prefs.getStringSet("cursorOff", new HashSet<String>()));
        blockCache.clear();
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
        blockCache.clear();
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
    // 1 = плейлист (.m3u8, .mpd), 2 = видео файл (.mp4), 0 = друго.
    // Без копиране на адреса: тази проверка минава за всяка заявка на страницата.
    private static int mediaKind(String url) {
        int kind = 0;
        for (int i = url.indexOf('.'); i >= 0; i = url.indexOf('.', i + 1)) {
            if (url.regionMatches(true, i, ".m3u8", 0, 5)
                    || url.regionMatches(true, i, ".mpd", 0, 4)) {
                return 1;
            }
            if (url.regionMatches(true, i, ".mp4", 0, 4)) kind = 2;
        }
        return kind;
    }

    private void maybeRecordMedia(WebResourceRequest r) {
        String url = r.getUrl().toString();
        int kind = mediaKind(url);
        if (kind == 0) return;
        LinkedHashMap<String, HashMap<String, String>> target =
                kind == 1 ? mediaPlaylists : mediaFiles;
        int cap = kind == 1 ? 15 : 10;
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
        if(kind==1 && tvSearchWeb!=null){final int generation=tvSearchGen;ui.post(()->{
            if(generation!=tvSearchGen || !tvSearching || tvSearchChecking || tvCur==null || tvSearchSi<0 || tvSearchSi>=tvCur.srcs.size())return;
            TvSrc source=tvCur.srcs.get(tvSearchSi);String identity=tvStreamKey(url);boolean known=identity.equals(tvStreamKey(source.stream));
            for(String[] alternate:source.alts)if(identity.equals(tvStreamKey(alternate[0])))known=true;
            if(!known){ui.removeCallbacks(tvSearchEndRun);ui.postDelayed(tvSearchEndRun,700);}
        });}
        // Скритото търсене на канал: след кратко изчакване се проверява новият поток.
        if ((kind == 1 || (kind == 2 && tvVod)) && tvCapturing) {
            ui.removeCallbacks(tvCheckRun);
            ui.postDelayed(tvCheckRun, 350);
        }
    }

    // ---------- Опции (плочки) и списък на домейните ----------

    private ScrollView optPage;
    private LinearLayout optBox;
    private LinearLayout domPage;
    private String optKey = "#reload";

    private void buildLogPanel() {
        logPanel = new LinearLayout(this);
        logPanel.setOrientation(LinearLayout.VERTICAL);
        logPanel.setBackground(pageBg());

        // Страница 1: плочки с опции.
        optPage = new ScrollView(this);
        optPage.setFillViewport(true);
        optPage.setVerticalScrollBarEnabled(false);
        optPage.setClipChildren(false);
        optPage.setClipToPadding(false);
        optBox = new LinearLayout(this);
        optBox.setOrientation(LinearLayout.VERTICAL);
        optBox.setClipChildren(false);
        optBox.setPadding(u(64), u(40), u(64), u(26));
        optPage.addView(optBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        logPanel.addView(optPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Страница 2: домейните, към които праща заявки страницата.
        domPage = new LinearLayout(this);
        domPage.setOrientation(LinearLayout.VERTICAL);
        domPage.setPadding(u(64), u(40), u(64), u(26));
        domPage.addView(text("Реклами и домейни", 44, Color.WHITE, tfCondBold, true));
        TextView hint = text("Натисни домейн, за да го блокираш или разрешиш. После презареди страницата.",
                17, C_MUTED, tfMedium, true);
        hint.setPadding(0, u(6), 0, u(14));
        domPage.addView(hint);

        logList = new ListView(this);
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(0x22FFFFFF);
        sel.setCornerRadius(u(16));
        sel.setStroke(u(3), Color.WHITE);
        logList.setSelector(sel);
        logList.setDividerHeight(0);
        logList.setVerticalScrollBarEnabled(false);
        logAdapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return logRows.size();
            }

            @Override
            public Object getItem(int pos) {
                return logRows.get(pos);
            }

            @Override
            public long getItemId(int pos) {
                return pos;
            }

            @Override
            public boolean areAllItemsEnabled() {
                return false;
            }

            @Override
            public boolean isEnabled(int pos) {
                if (pos < 0 || pos >= logRows.size()) return false;
                return logRows.get(pos).kind == LogRow.DOMAIN;
            }

            @Override
            public View getView(int pos, View convertView, ViewGroup parent) {
                return buildLogRowView(logRows.get(pos));
            }
        };
        logList.setAdapter(logAdapter);
        logList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                onLogItem(pos);
            }
        });
        domPage.addView(logList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView foot = text("OK — блокирай или разреши      Назад — към опциите", 15, 0xFF8E9ABD,
                tfMedium, true);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, u(12), 0, 0);
        domPage.addView(foot);
        domPage.setVisibility(View.GONE);
        logPanel.addView(domPage, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        logPanel.setVisibility(View.GONE);
        root.addView(logPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private boolean logShown() {
        return logPanel != null && logPanel.getVisibility() == View.VISIBLE;
    }

    private boolean domShown() {
        return domPage != null && domPage.getVisibility() == View.VISIBLE;
    }

    private void showLog() {
        if (web == null || logPanel == null) return;
        optKey = "#reload";
        domPage.setVisibility(View.GONE);
        optPage.setVisibility(View.VISIBLE);
        logPanel.setVisibility(View.VISIBLE);logPanel.bringToFront();
        buildOptions();
        refreshCacheLabel();
        updateState();
    }

    private void hideLog() {
        if (logPanel != null) logPanel.setVisibility(View.GONE);
        if (web != null) web.requestFocus();
        updateState();
    }

    private void showDomains() {
        optPage.setVisibility(View.GONE);
        domPage.setVisibility(View.VISIBLE);
        refreshDomains(0);
        logList.requestFocus();
    }

    // От списъка с домейни обратно към плочките.
    private void backToOptions() {
        domPage.setVisibility(View.GONE);
        optPage.setVisibility(View.VISIBLE);
        optKey = "#domains";
        buildOptions();
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

    private static final int C_ON = 0xFF34D399;

    private void addNote(String text) {
        LogRow r = new LogRow();
        r.kind = LogRow.NOTE;
        r.title = text;
        logRows.add(r);
        logKeys.add("#none");
    }

    private void addDomain(String host, String sub, String state, int stateColor) {
        LogRow r = new LogRow();
        r.kind = LogRow.DOMAIN;
        r.title = host;
        r.sub = sub;
        r.state = state;
        r.stateColor = stateColor;
        logRows.add(r);
        logKeys.add(host);
    }

    private String sleepState() {
        return sleepAt == 0 ? "Изключен" : sleepLabel().toLowerCase(Locale.ROOT);
    }

    private static String onOffWord(boolean on, String yes, String no) {
        return on ? yes : no;
    }

    // Плочка с опция: икона, състояние (по желание), заглавие и кратко пояснение.
    // style: 0 неутрално, 1 зелено, 2 кехлибарено.
    private View optTile(String key, int icon, int bg, int fg, String title, String sub,
                         String state, int style) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(u(20), u(18), u(20), u(18));
        t.setFocusable(true);
        t.setClickable(true);
        t.setTag(key);
        t.setBackground(stateBg(C_PANEL, C_PANEL_FOCUS, Color.WHITE, u(26), C_PANEL_LINE));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconCircle(icon, bg, fg, 52), lp(u(52), u(52)));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        if (state != null) {
            int pbg = style == 1 ? C_GREEN_BG : style == 2 ? C_AMBER_FG : C_ICON_BG;
            int pfg = style == 1 ? C_GREEN_FG : style == 2 ? 0xFF2B1B03 : 0xFFC7D2F5;
            TextView pill = text(state, 15, pfg, Typeface.DEFAULT_BOLD, true);
            pill.setGravity(Gravity.CENTER);
            pill.setPadding(u(14), 0, u(14), 0);
            GradientDrawable pg = new GradientDrawable();
            pg.setColor(pbg);
            pg.setCornerRadius(u(15));
            pill.setBackground(pg);
            top.addView(pill, lp(ViewGroup.LayoutParams.WRAP_CONTENT, u(30)));
        }
        t.addView(top);
        t.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        t.addView(text(title, 21, Color.WHITE, Typeface.DEFAULT_BOLD, true));
        t.addView(text(sub, 15, C_MUTED, tfMedium, true));

        final String k = key;
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runOption(k);
            }
        });
        t.setTag(R_SCALE, 1.04f);
        t.setOnFocusChangeListener(tileFocus);
        return t;
    }

    private View findByTag(View v, String key) {
        if (key.equals(v.getTag())) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findByTag(g.getChildAt(i), key);
                if (r != null) return r;
            }
        }
        return null;
    }

    private void buildOptions() {
        if (optBox == null) return;
        View cur = optBox.findFocus();
        if (cur != null && cur.getTag() instanceof String) optKey = (String) cur.getTag();
        optBox.removeAllViews();

        // Шапка: заглавие и полета за състояние.
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.addView(text("Опции", 48, Color.WHITE, tfCondBold, true));
        TextView sub = text((allowedHost == null ? "" : stripWww(allowedHost) + " · ")
                + "Версия " + installedName(), 18, C_MUTED, tfMedium, true);
        sub.setPadding(0, u(8), 0, 0);
        tl.addView(sub);
        head.addView(tl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams gap = lp(ViewGroup.LayoutParams.WRAP_CONTENT, u(64));
        gap.leftMargin = u(12);
        head.addView(capsule(IC_CHIP, C_ICON_BG, C_ICON_FG, "Свободна памет", freeMemText()),
                lp(ViewGroup.LayoutParams.WRAP_CONTENT, u(64)));
        head.addView(capsule(IC_DB, C_ICON_BG, C_ICON_FG, "Кеш",
                cacheSizeText != null ? cacheSizeText : "..."), gap);
        LinearLayout.LayoutParams gap2 = lp(ViewGroup.LayoutParams.WRAP_CONTENT, u(64));
        gap2.leftMargin = u(12);
        head.addView(capsule(IC_MOON, sleepAt != 0 ? C_AMBER_BG : C_ICON_BG,
                sleepAt != 0 ? C_AMBER_FG : C_ICON_FG, "Таймер",
                sleepAt != 0 ? "Остават " + sleepState() : "Изключен"), gap2);
        optBox.addView(head, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Плочки 4 x 2.
        String modeName = "mobile".equals(curMode) ? "Мобилна" : "desktop".equals(curMode)
                ? "Десктоп" : "Нормална";
        final boolean curOn = cursorEnabled || cursorTemp;
        View[] tiles = {
                optTile("#reload", IC_REFRESH, C_ICON_BG, C_ICON_FG, "Презареди",
                        "Опитай страницата пак", null, 0),
                optTile("#search", IC_SEARCH, C_ICON_BG, C_ICON_FG, "Търси на сайта",
                        "Отваря клавиатура", null, 0),
                optTile("#mode", IC_MONITOR, C_ICON_BG, C_ICON_FG, "Версия на сайта",
                        "Мобилната е по-лека", modeName, 0),
                optTile("#cursor", IC_CURSOR, curOn ? C_GREEN_BG : C_ICON_BG,
                        curOn ? C_GREEN_FG : C_ICON_FG, "Курсор",
                        curOn ? "Стрелките го движат" : "Стрелките движат фокуса",
                        cursorTemp && !cursorEnabled ? "Временно"
                                : onOffWord(cursorEnabled, "Включен", "Изключен"), curOn ? 1 : 0),
                optTile("#sleep", IC_MOON, sleepAt != 0 ? C_AMBER_BG : C_ICON_BG,
                        sleepAt != 0 ? C_AMBER_FG : C_ICON_FG, "Таймер за заспиване",
                        "Спира всичко след време", sleepState(), sleepAt != 0 ? 2 : 0),
                optTile("#stats", IC_BARS, C_ICON_BG, C_ICON_FG, "Статистика",
                        "Памет върху екрана", onOffWord(statsOn, "Включена", "Изключена"),
                        statsOn ? 1 : 0),
                optTile("#cache", IC_TRASH, C_ICON_BG, C_ICON_FG, "Изчисти кеша",
                        "Освобождава място", cacheSizeText, 0),
                optTile("#autocache", IC_SPARK, autoCacheOn() ? C_GREEN_BG : C_ICON_BG,
                        autoCacheOn() ? C_GREEN_FG : C_ICON_FG, "Авто чистене",
                        "Над 200 МБ кеш", onOffWord(autoCacheOn(), "Включено", "Изключено"),
                        autoCacheOn() ? 1 : 0),
        };
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            for (int c = 0; c < 4; c++) {
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, u(150), 1f);
                if (c > 0) tlp.leftMargin = u(16);
                row.addView(tiles[r * 4 + c], tlp);
            }
            LinearLayout.LayoutParams rlp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = u(r == 0 ? 28 : 16);
            optBox.addView(row, rlp);
        }

        // Видео на страницата и реклами.
        LinearLayout panels = new LinearLayout(this);
        panels.setOrientation(LinearLayout.HORIZONTAL);
        panels.setClipChildren(false);
        panels.addView(buildVideoPanel(), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.3f));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        alp.leftMargin = u(16);
        panels.addView(buildAdsPanel(), alp);
        LinearLayout.LayoutParams plp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = u(24);
        optBox.addView(panels, plp);

        TextView foot = text("OK — избор      Назад — затвори", 15, 0xFF8E9ABD, tfMedium, true);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, u(18), 0, 0);
        optBox.addView(foot);

        optBox.post(new Runnable() {
            @Override
            public void run() {
                View f = findByTag(optBox, optKey);
                if (f == null) f = findByTag(optBox, "#reload");
                if (f != null) f.requestFocus();
            }
        });
    }

    private View buildVideoPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(u(22), u(20), u(22), u(20));
        p.setMinimumHeight(u(218));
        p.setBackground(panelBg(26));
        p.setClipChildren(false);

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, 0, u(10));
        LinearLayout.LayoutParams iclp = lp(u(24), u(24));
        iclp.rightMargin = u(10);
        title.addView(new IconView(this, IC_PLAY, C_ICON_FG), iclp);
        title.addView(text("Видео на страницата", 20, Color.WHITE, Typeface.DEFAULT_BOLD, true));
        p.addView(title);

        p.addView(qualityRow());

        // Намерени видео потоци (най-новите най-отгоре), най-много 3.
        ArrayList<String> pl = new ArrayList<>();
        ArrayList<String> fl = new ArrayList<>();
        synchronized (mediaPlaylists) {
            pl.addAll(mediaPlaylists.keySet());
        }
        synchronized (mediaFiles) {
            fl.addAll(mediaFiles.keySet());
        }
        int shown = 0;
        for (int i = pl.size() - 1; i >= 0 && shown < 3; i--, shown++) {
            p.addView(mediaLine(pl.get(i), "Пусни в родния плейър"));
        }
        for (int i = fl.size() - 1; i >= 0 && shown < 3; i--, shown++) {
            p.addView(mediaLine(fl.get(i), "Пусни файла в родния плейър"));
        }
        if (shown == 0) {
            p.addView(text("Още няма намерено видео. Пусни канала или филма и отвори Опции пак.",
                    17, C_MUTED, tfMedium, false));
        }
        return p;
    }

    // Ред в панела за видеото: качеството на родния плеър (отваря списък за избор).
    private View qualityRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u(16), 0, u(16), 0);
        r.setMinimumHeight(u(54));
        r.setFocusable(true);
        r.setClickable(true);
        r.setTag("#quality");
        r.setBackground(stateBg(TvTheme.SURFACE, C_PANEL_FOCUS, Color.WHITE, u(18), 0));
        r.addView(text("Качество на родния плеър", 18, Color.WHITE, tfMedium, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(text(qualityName(videoQuality()) + "  ▾", 17, 0xFF8FDBFF, Typeface.DEFAULT_BOLD, true));
        LinearLayout.LayoutParams rlp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = u(4);
        r.setLayoutParams(rlp);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runOption("#quality");
            }
        });
        r.setOnFocusChangeListener(focusAnim);
        return r;
    }

    private View mediaRow(final String url, String label) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u(16), 0, u(16), 0);
        r.setMinimumHeight(u(62));
        r.setFocusable(true);
        r.setClickable(true);
        r.setTag("#media:" + url);
        r.setBackground(stateBg(TvTheme.SURFACE, C_PANEL_FOCUS, Color.WHITE, u(18), 0));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(label, 18, Color.WHITE, tfMedium, true));
        tx.addView(text(shortName(url), 14, C_MUTED, tfMedium, true));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView go = text("Пусни", 16, 0xFF06263A, Typeface.DEFAULT_BOLD, true);
        go.setGravity(Gravity.CENTER);
        go.setPadding(u(16), 0, u(16), 0);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(0xFF38BDF8);
        gb.setCornerRadius(u(17));
        go.setBackground(gb);
        r.addView(go, lp(ViewGroup.LayoutParams.WRAP_CONTENT, u(34)));
        LinearLayout.LayoutParams rlp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = u(8);
        r.setLayoutParams(rlp);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runOption("#media:" + url);
            }
        });
        r.setOnFocusChangeListener(focusAnim);
        return r;
    }

    private View buildAdsPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(u(22), u(20), u(22), u(20));
        p.setMinimumHeight(u(218));
        p.setFocusable(true);
        p.setClickable(true);
        p.setTag("#domains");
        p.setBackground(stateBg(C_PANEL, C_PANEL_FOCUS, Color.WHITE, u(26), C_PANEL_LINE));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams iclp = lp(u(24), u(24));
        iclp.rightMargin = u(10);
        title.addView(new IconView(this, IC_SHIELD, 0xFFFB7185), iclp);
        title.addView(text("Реклами и домейни", 20, Color.WHITE, Typeface.DEFAULT_BOLD, true));
        p.addView(title);

        int domains;
        synchronized (hostCounts) {
            domains = hostCounts.size();
        }
        TextView info = text(reqBlocked.get() + " блокирани заявки от " + domains
                + " домейна. Натисни, за да блокираш или разрешиш домейн.", 17, C_MUTED,
                tfMedium, false);
        info.setPadding(0, u(12), 0, 0);
        p.addView(info);
        p.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        TextView go = text("Виж домейните", 18, 0xFFC7D2F5, Typeface.DEFAULT_BOLD, true);
        go.setGravity(Gravity.CENTER);
        GradientDrawable gb = new GradientDrawable();
        gb.setColor(C_ICON_BG);
        gb.setCornerRadius(u(22));
        go.setBackground(gb);
        p.addView(go, lp(ViewGroup.LayoutParams.MATCH_PARENT, u(44)));

        p.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runOption("#domains");
            }
        });
        p.setOnFocusChangeListener(focusAnim);
        return p;
    }

    private void runOption(String key) {
        optKey = key;
        if (key.equals("#search")) {
            startSiteSearch();
        } else if (key.equals("#reload")) {
            hideLog();
            if (web != null) web.reload();
        } else if (key.equals("#cursor")) {
            if (cursorTemp) {
                cursorTemp = false;
                Toast.makeText(this, "Временният курсор е изключен", Toast.LENGTH_SHORT).show();
                updateState();
            } else {
                toggleCursor();
            }
            refreshLog(0);
        } else if (key.equals("#sleep")) {
            chooseSleep();
        } else if (key.equals("#mode")) {
            toggleSiteMode();
        } else if (key.equals("#cache")) {
            clearCacheNow();
        } else if (key.equals("#autocache")) {
            toggleAutoCache();
            refreshLog(0);
        } else if (key.equals("#stats")) {
            toggleStats();
            refreshLog(0);
        } else if (key.equals("#quality")) {
            tvQualityDialog();
        } else if (key.equals("#domains")) {
            showDomains();
        } else if (key.startsWith("#media:")) {
            hideLog();
            playNative(key.substring(7));
        } else if (key.startsWith("#tvsave:")) {
            tvSaveFlow(key.substring(8));
        }
    }

    // Обновява това, което е на екрана: плочките или списъка с домейни.
    private void refreshLog(int keepPos) {
        if (domShown()) refreshDomains(keepPos);
        else buildOptions();
    }

    private void refreshDomains(int keepPos) {
        logKeys.clear();
        logRows.clear();
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>();
        synchronized (hostCounts) {
            for (Map.Entry<String, Integer> en : hostCounts.entrySet()) {
                list.add(new AbstractMap.SimpleEntry<String, Integer>(en.getKey(), en.getValue()));
            }
        }
        Collections.sort(list, (a, b) -> b.getValue() - a.getValue());
        if (list.isEmpty()) {
            addNote("Още няма заявки. Отвори страница и опитай пак.");
        }
        int n = 0;
        for (Map.Entry<String, Integer> en : list) {
            if (n++ >= 80) break;
            String h = en.getKey();
            if (isOwnHost(h)) {
                addDomain(h, en.getValue() + " заявки", "САЙТЪТ", 0xFF93C5FD);
            } else if (isBlocked(h)) {
                addDomain(h, en.getValue() + " заявки", "БЛОКИРАН", C_DANGER);
            } else {
                addDomain(h, en.getValue() + " заявки", "РАЗРЕШЕН", C_ON);
            }
        }
        logAdapter.notifyDataSetChanged();
        int pos = Math.max(0, Math.min(keepPos, logRows.size() - 1));
        logList.setSelection(pos);
    }

    private TextView pill(String text, int color) {
        TextView p = new TextView(this);
        p.setText(text);
        p.setTextColor(color);
        p.setTextSize(13);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setLetterSpacing(0.08f);
        p.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x26FFFFFF);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), (color & 0x00FFFFFF) | 0x66000000);
        p.setBackground(bg);
        return p;
    }

    private View buildLogRowView(LogRow r) {
        if (r.kind == LogRow.NOTE) {
            TextView h = new TextView(this);
            h.setText(r.title);
            h.setTextColor(C_MUTED);
            h.setTextSize(16);
            h.setPadding(dp(16), dp(10), dp(16), dp(10));
            return h;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(11), dp(16), dp(11));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView tt = new TextView(this);
        tt.setText(r.title);
        tt.setTextColor(r.stateColor == C_DANGER ? C_DANGER : Color.WHITE);
        tt.setTextSize(17);
        tt.setSingleLine(true);
        tt.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(tt);
        if (r.sub != null && !r.sub.isEmpty()) {
            TextView st = new TextView(this);
            st.setText(r.sub);
            st.setTextColor(C_MUTED);
            st.setTextSize(13);
            st.setSingleLine(true);
            st.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(st);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (r.state != null && !r.state.isEmpty()) {
            row.addView(pill(r.state, r.stateColor));
        }
        return row;
    }

    // Избор от списъка с домейни.
    private void onLogItem(int pos) {
        if (pos < 0 || pos >= logKeys.size()) return;
        String key = logKeys.get(pos);
        if (key.equals("#none")) return;
        if (isOwnHost(key)) {
            Toast.makeText(this, "Това е самият сайт - не се блокира", Toast.LENGTH_SHORT).show();
            return;
        }
        toggleBlock(key);
        refreshDomains(pos);
    }

    // ---------- Таймер за заспиване (само ръчно, по желание) ----------

    private String sleepLabel() {
        if (sleepAt == 0) return "ИЗКЛ";
        long min = Math.max(1, (sleepAt - SystemClock.uptimeMillis() + 59999) / 60000);
        return min + " МИН";
    }

    private static String sleepText(int min) {
        if (min == 60) return "1 час";
        if (min == 90) return "1 час и 30 минути";
        if (min == 120) return "2 часа";
        return min + " минути";
    }

    private void chooseSleep() {
        String[] items = new String[SLEEP_MIN.length];
        for (int i = 0; i < SLEEP_MIN.length; i++) {
            items[i] = SLEEP_MIN[i] == 0 ? (sleepAt == 0 ? "Изключен" : "Изключи таймера")
                    : sleepText(SLEEP_MIN[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Таймер за заспиване" + (sleepAt == 0 ? "" : " (остават "
                        + sleepLabel().toLowerCase(Locale.ROOT) + ")"))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        setSleep(SLEEP_MIN[which]);
                    }
                })
                .show();
    }

    // Поставя таймера (0 = изключва го). Минута преди края пита дали да продължи.
    private void setSleep(int min) {
        ui.removeCallbacks(sleepWarn);
        ui.removeCallbacks(sleepFire);
        if (min <= 0) {
            sleepAt = 0;
            Toast.makeText(this, "Таймерът е изключен", Toast.LENGTH_SHORT).show();
        } else {
            long ms = min * 60000L;
            sleepAt = SystemClock.uptimeMillis() + ms;
            if (ms > 60000) ui.postDelayed(sleepWarn, ms - 60000);
            ui.postDelayed(sleepFire, ms);
            Toast.makeText(this, "Приложението ще спре след " + sleepText(min),
                    Toast.LENGTH_LONG).show();
        }
        logEvent("Таймер за заспиване: " + (min <= 0 ? "изключен" : min + " мин"));
        if (logShown()) refreshLog(Math.max(1, logList.getSelectedItemPosition()));
    }

    // ---------- Актуализации ----------

    private void showInfo(String title, String msg) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    private static class Release {
        int code;
        String apkUrl;
        String err;
    }

    // Пита GitHub за последната версия. Вика се само от фонова нишка.
    private Release fetchLatestRelease() {
        Release r = new Release();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(
                    "https://api.github.com/repos/" + UPDATE_REPO + "/releases/latest")
                    .openConnection();
            c.setRequestProperty("Accept", "application/vnd.github+json");
            c.setRequestProperty("User-Agent", "TvSites");
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            int rc = c.getResponseCode();
            if (rc != 200) {
                r.err = "GitHub отговори с код " + rc
                        + (rc == 404 ? " (няма публикувана версия или хранилището не е публично)" : "");
            } else {
                JSONObject o = new JSONObject(readAll(c.getInputStream()));
                String digits = o.optString("tag_name", "").replaceAll("[^0-9]", "");
                r.code = digits.isEmpty() ? 0 : Integer.parseInt(digits);
                JSONArray assets = o.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        if (a.optString("name", "").endsWith(".apk")) {
                            r.apkUrl = a.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                if (r.apkUrl == null) r.err = "В последната версия няма APK файл.";
            }
        } catch (Exception e) {
            r.err = "Няма връзка или грешка при проверката.";
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    // Проверява, когато потребителят го поиска (бутон "Обнови" или Опции).
    private void checkUpdate() {
        Toast.makeText(this, "Проверявам за нова версия...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Release r = fetchLatestRelease();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (r.err != null) {
                            showInfo("Проверка за актуализации", r.err);
                        } else if (r.code > installedCode()) {
                            askUpdate(r.code, r.apkUrl);
                        } else {
                            showInfo("Проверка за актуализации",
                                    "Имаш последната версия (" + installedName() + ").");
                        }
                    }
                });
            }
        }).start();
    }

    // Една тиха проверка при пускане: нищо не показва, освен ако има по-нова версия -
    // тогава излиза съобщение и лента най-горе в менюто.
    private void checkUpdateQuiet() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final Release r = fetchLatestRelease();
                if (r.err != null) {
                    logEvent("Проверка за версия при пускане: " + r.err);
                    return;
                }
                if (r.code <= installedCode()) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            versionOk = true;
                            if (!isFinishing() && menuHolder.getVisibility() == View.VISIBLE) {
                                refreshHeader();
                            }
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        updateAvail = r.code;
                        updateUrl = r.apkUrl;
                        logEvent("Има нова версия 2." + r.code);
                        Toast.makeText(MainActivity.this, "Има нова версия 2." + r.code
                                + " - виж жълтото поле най-горе в менюто", Toast.LENGTH_LONG).show();
                        if (menuHolder.getVisibility() == View.VISIBLE) refreshHeader();
                    }
                });
            }
        });
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
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
            root.postDelayed(statsTick, 3000);
        }
    }

    private void updateStats() {
        if (statsView == null) return;
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long freeMb = mi.availMem / 1048576L;
        long totalMb = mi.totalMem / 1048576L;
        // Паметта на приложението (PSS) е скъпа за измерване: на заден план и на 10 секунди.
        long now = SystemClock.uptimeMillis();
        if (!statsPssBusy && (statsPssMb < 0 || now - statsPssAt > 10000)) {
            statsPssBusy = true;
            statsPssAt = now;
            bg(new Runnable() {
                @Override
                public void run() {
                    try {
                        statsPssMb = pssMb();
                    } finally {
                        statsPssBusy = false;
                    }
                }
            });
        }
        statsView.setText("Свободна RAM: " + freeMb + " / " + totalMb + " МБ   Приложение: "
                + (statsPssMb < 0 ? "..." : statsPssMb + " МБ")
                + "   Заявки: " + reqTotal.get() + " (блок. " + reqBlocked.get() + ")");
    }

    private boolean memoryForeground=true;
    private final Runnable appMemoryTick=()->updateAppMemory();
    private void updateAppMemory(){
        ui.removeCallbacks(appMemoryTick);
        if(!memoryForeground || isFinishing() || appBar==null || appBar.getVisibility()!=View.VISIBLE)return;
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        appBar.setMemory(statsPssMb,info.availMem/1048576L,info.totalMem/1048576L);
        long now=SystemClock.uptimeMillis();
        if(!statsPssBusy && (statsPssMb<0 || now-statsPssAt>=10000)){
            statsPssBusy=true;statsPssAt=now;
            bg(()->{try{statsPssMb=pssMb();}finally{statsPssBusy=false;}
                ui.post(()->{if(memoryForeground && appBar!=null && appBar.getVisibility()==View.VISIBLE){
                    ActivityManager.MemoryInfo fresh=new ActivityManager.MemoryInfo();
                    ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(fresh);
                    appBar.setMemory(statsPssMb,fresh.availMem/1048576L,fresh.totalMem/1048576L);
                }});
            });
        }
        ui.postDelayed(appMemoryTick,10000);
    }

    private volatile long statsPssMb = -1;
    private volatile boolean statsPssBusy;
    private long statsPssAt;

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

        // Страницата се затваря напълно: паметта ѝ е нужна на плеъра, а при "Назад" се отваря пак.
        String cur = web.getUrl();
        resumeUrl = (cur != null && cur.startsWith("http")) ? cur : prefs.getString("lastUrl", null);
        if (customView != null && chromeClient != null) chromeClient.onHideCustomView();
        root.removeCallbacks(cacheWatch);
        ui.removeCallbacks(rememberTick);
        rememberPage();
        destroyWeb();

        closeNative(false);
        nativeLayer.setVisibility(View.VISIBLE);

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setUserAgent(ua);
        http.setDefaultRequestProperties(props);

        // По-малък буфер от стандартния (50 сек.): по-малко памет, а за телевизия 40 сек. стигат.
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(20000, 40000, 2000, 4000)
                .build();
        vTrackSel = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this);
        applyQuality();
        player = new ExoPlayer.Builder(this)
                .setLoadControl(load)
                .setTrackSelector(vTrackSel)
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

        showNativeStatus("Зареждане...  (OK = пауза, задръж OK = качество, ◀ ▶ = превъртане, Назад = към страницата)", 5000);
        updateState();
    }

    // Ограничение на качеството на видеото: 0 = оригинал, 1 = до 720p, 2 = до 576p.
    private androidx.media3.exoplayer.trackselection.DefaultTrackSelector vTrackSel;
    private boolean okWasPlaying;

    private int videoQuality() {
        int q = prefs.getInt("vq", 0);
        return q < 0 || q > 3 ? 0 : q;
    }

    private void applyQuality() {
        if (vTrackSel == null) return;
        try {
            int q = videoQuality();
            androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder b =
                    vTrackSel.buildUponParameters();
            if (q == 1) b.setMaxVideoSize(1280, 720);
            else if (q == 2) b.setMaxVideoSize(1024, 576);
            else if (q == 3) b.setMaxVideoSize(854, 480);
            else b.clearVideoSizeConstraints();
            vTrackSel.setParameters(b);
        } catch (Exception ignored) {
            // качеството остава каквото е
        }
    }

    private static String qualityName(int q) {
        return q == 0 ? "оригинал" : q == 1 ? "до 720p" : q == 2 ? "до 576p" : "до 480p";
    }

    private void cycleQuality() {
        int q = (videoQuality() + 1) % 4;
        prefs.edit().putInt("vq", q).apply();
        applyQuality();
        showNativeStatus("Качество: " + qualityName(q), 2200);
    }

    private void closeNative(boolean backToPage) {
        boolean wasRadio = radioStation != null;
        if (player != null && radioListener != null) player.removeListener(radioListener);
        radioListener = null; radioStation = null; radioStatus = null; radioPlay = null;
        if (radioLayer != null && nativeLayer != null) nativeLayer.removeView(radioLayer);
        radioLayer = null;
        if (nativeSurface != null) nativeSurface.setVisibility(View.VISIBLE);
        if (player != null) {
            player.removeListener(nativeListener);
            player.release();
            player = null;
        }
        if (nativeStatus != null) nativeStatus.removeCallbacks(hideNativeStatus);
        boolean wasActive = nativeActive();
        if (nativeLayer != null) nativeLayer.setVisibility(View.GONE);
        if (backToPage && wasActive) { if (wasRadio) showSection(SECTION_RADIO); else resumePage(); }
        updateState();
    }

    // След родния плеър страницата се създава наново и се зарежда от мястото, откъдето е тръгнала.
    private void resumePage() {
        String u = resumeUrl;
        if (u == null || u.isEmpty()) u = prefs.getString("lastUrl", null);
        if (u == null) {
            backToMenu();
            return;
        }
        if (web == null) createWebView();
        applyUserAgent();
        menuHolder.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        startWatch();
        showSplash(prefs.getString("lastName", hostOf(u)), hostOf(u));
        web.loadUrl(u);
        web.requestFocus();
        pokeCursor();
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
        // Истинско движение на мишката към страницата: плеърите показват лентата си.
        hoverAt(customView != null ? customView : web, cx, cy);
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

    // Режим превъртане: включва се с двойно OK там, където искаш да превъртиш (списък със
    // субтитри, качество и т.н. или цялата страница). Тогава стрелките нагоре и надолу
    // плъзгат под курсора като с пръст, наляво и надясно движат курсора, а едно OK или
    // Назад излизат от режима. Курсорът става оранжев със стрелки.
    private boolean scrollMode;
    // Курсор, включен временно с двойно OK, когато сайтът е в режим без курсор.
    private boolean cursorTemp;
    private boolean hovering;
    private long scrollExitAt;
    private final MotionEvent.PointerProperties[] mouseProps = {new MotionEvent.PointerProperties()};
    private final MotionEvent.PointerCoords[] mouseCoords = {new MotionEvent.PointerCoords()};

    // В цял екран курсорът е винаги на разположение (там няма какво друго да се управлява).
    private boolean cursorOn() {
        return cursorEnabled || cursorTemp || customView != null;
    }

    // Движение на мишка към страница или цял екран: плеърите показват лентата си.
    private void mouseEvent(View target, int action, float x, float y) {
        if (target == null) return;
        long t = SystemClock.uptimeMillis();
        MotionEvent.PointerProperties pp = mouseProps[0];
        pp.id = 0;
        pp.toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords pc = mouseCoords[0];
        pc.clear();
        pc.x = x;
        pc.y = y;
        MotionEvent ev = MotionEvent.obtain(t, t, action, 1, mouseProps, mouseCoords,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        target.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    private void hoverAt(View target, float x, float y) {
        mouseEvent(target, hovering ? MotionEvent.ACTION_HOVER_MOVE
                : MotionEvent.ACTION_HOVER_ENTER, x, y);
        hovering = true;
    }

    // ---- Превъртане като с пръст ----
    // Докато държиш стрелката, един непрекъснат допир се плъзга по екрана на мястото на
    // курсора (точно както при телефон), а когато я пуснеш, допирът се вдига.
    private boolean swiping;
    private View swipeTarget;
    private long swipeDownAt;
    private long swipeLastAt;
    private long swipeEndAt;
    private float swipeX;
    private float swipeY;
    private float swipeTravel;
    private final Runnable swipeEnd = new Runnable() {
        @Override
        public void run() {
            endSwipe(false);
        }
    };

    private void touch(View target, int action, long downAt, long at, float x, float y) {
        MotionEvent ev = MotionEvent.obtain(downAt, at, action, x, y, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(ev);
        ev.recycle();
    }

    // dir: 1 = стрелка надолу (съдържанието върви нагоре), -1 = стрелка нагоре.
    private void swipeStep(View target, int dir, int rep) {
        if (target == null || target.getHeight() <= 0) return;
        if (swiping && (swipeTarget != target
                || Math.abs(swipeTravel) > target.getHeight() * 0.7f)) {
            endSwipe(false);
        }
        long now = SystemClock.uptimeMillis();
        if (!swiping) {
            // Първото плъзгане е дълго, за да надвиши прага на допира (иначе би станало клик).
            // При продължително държане (rep > 0) е по-късо, за да няма голям скок.
            swiping = true;
            swipeTarget = target;
            swipeX = cx;
            swipeY = cy;
            swipeTravel = 0;
            swipeDownAt = Math.max(now - 70, swipeEndAt + 1); // времето само расте
            swipeLastAt = swipeDownAt;
            touch(target, MotionEvent.ACTION_DOWN, swipeDownAt, swipeLastAt, swipeX, swipeY);
            int steps = rep == 0 ? 7 : 4;
            for (int i = 0; i < steps; i++) {
                float d = dir * 27f;
                swipeY -= d;
                swipeTravel -= d;
                swipeLastAt += 10;
                touch(target, MotionEvent.ACTION_MOVE, swipeDownAt, swipeLastAt, swipeX, swipeY);
            }
        } else {
            // Колкото по-дълго държиш, толкова по-бързо върти.
            float d = dir * (26f + Math.min(rep, 10) * 4f);
            swipeY -= d;
            swipeTravel -= d;
            swipeLastAt = Math.max(now, swipeLastAt + 1);
            touch(target, MotionEvent.ACTION_MOVE, swipeDownAt, swipeLastAt, swipeX, swipeY);
        }
        root.removeCallbacks(swipeEnd);
        root.postDelayed(swipeEnd, 220);
    }

    // Вдига допира (или го отменя). Без движение в последните 220 мс няма инерция.
    private void endSwipe(boolean cancel) {
        root.removeCallbacks(swipeEnd);
        if (!swiping) return;
        swiping = false;
        View t = swipeTarget;
        swipeTarget = null;
        if (t == null) return;
        long at = Math.max(SystemClock.uptimeMillis(), swipeLastAt + 1);
        swipeEndAt = at;
        touch(t, cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP,
                swipeDownAt, at, swipeX, swipeY);
    }

    private void setScrollMode(boolean on) {
        if (scrollMode == on) return;
        scrollMode = on;
        if (!on) {
            endSwipe(false);
            scrollExitAt = SystemClock.uptimeMillis();
        }
        if (cursor != null) cursor.setScroll(on);
        Toast.makeText(this, on ? "Превъртане: стрелки нагоре и надолу. Едно OK го спира."
                : "Превъртането е спряно", Toast.LENGTH_SHORT).show();
        pokeCursor();
    }

    // Двойно OK: включва превъртането. Ако сайтът е без курсор, първо включва курсора
    // временно (до излизане от сайта), за да го сложиш на мястото.
    private void doubleOk() {
        if (!cursorOn()) {
            cursorTemp = true;
            pokeCursor();
            Toast.makeText(this, "Курсорът е включен. Сложи го на списъка и натисни OK два пъти.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        setScrollMode(true);
    }

    // Задържане на OK: менюто с опции на страницата. От цял екран излизаме първо.
    private void openOptions() {
        endSwipe(true);
        if (customView != null && chromeClient != null) chromeClient.onHideCustomView();
        showLog();
    }

    private void cursorArrow(View target, int code, int rep) {
        boolean vert = code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN;
        if (scrollMode && vert) {
            swipeStep(target, code == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1, rep);
            pokeCursor();
            return;
        }
        if (swiping) endSwipe(false);
        moveCursor(code, rep);
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

    // ---------- Въвеждане на текст (търсачки и полета) ----------

    // След клик проверява дали е фокусирано поле за текст и отваря собствено поле за въвеждане.
    private void checkEditableSoon() {
        root.removeCallbacks(editableCheck);
        root.postDelayed(editableCheck, 450);
    }

    private void probeEditable(String js, final boolean fromSearchButton) {
        if (web == null) return;
        web.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                handleEditableResult(value, fromSearchButton);
            }
        });
    }

    private void handleEditableResult(String value, boolean fromSearchButton) {
        String inner = "";
        try {
            if (value != null && !value.equals("null")) {
                inner = new JSONArray("[" + value + "]").getString(0);
            }
        } catch (Exception ignored) {
        }
        if (inner.isEmpty()) {
            if (fromSearchButton) {
                Toast.makeText(this, "Не намерих поле за търсене. Натисни лупата или полето с курсора.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        try {
            JSONObject o = new JSONObject(inner);
            showTextDialog(o.optString("t", "text"), o.optString("v", ""), o.optString("h", ""),
                    o.optBoolean("s", false), o.optBoolean("m", false));
        } catch (Exception ignored) {
        }
    }

    // Бутон "Търси на сайта" от опциите.
    private void startSiteSearch() {
        hideLog();
        probeEditable(JS_FIND_SEARCH, true);
    }

    private StateListDrawable fieldBg() {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(0x26FFFFFF);
        normal.setCornerRadius(dp(14));
        normal.setStroke(dp(2), 0x55FFFFFF);
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(0x33FFFFFF);
        focused.setCornerRadius(dp(14));
        focused.setStroke(dp(3), C_AMBER);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, focused);
        sl.addState(new int[]{}, normal);
        return sl;
    }

    // Собствено поле за въвеждане: работи надеждно на телевизор и показва клавиатурата.
    private void showTextDialog(String type, String current, String hint,
                                final boolean isSearch, boolean multi) {
        if (isFinishing()) return;
        if (textDialog != null && textDialog.isShowing()) return;
        final InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && web != null) imm.hideSoftInputFromWindow(web.getWindowToken(), 0);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(30), dp(26), dp(30), dp(26));
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2A1B6B, 0xFF171C3F});
        pbg.setCornerRadius(dp(24));
        pbg.setStroke(dp(1), 0x44FFFFFF);
        panel.setBackground(pbg);

        TextView title = new TextView(this);
        title.setText(isSearch ? "Търсене" : "Въведи текст");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Напиши с клавиатурата. Ако не се показва, натисни OK върху полето.");
        sub.setTextColor(C_MUTED);
        sub.setTextSize(14);
        sub.setPadding(0, dp(4), 0, dp(16));
        panel.addView(sub);

        final EditText et = new EditText(this);
        et.setText(current);
        et.setSelection(et.getText().length());
        et.setHint(hint.isEmpty() ? (isSearch ? "Какво търсиш?" : "") : hint);
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0xFF9AA0AB);
        et.setTextSize(24);
        et.setPadding(dp(18), dp(14), dp(18), dp(14));
        et.setBackground(fieldBg());
        int it = InputType.TYPE_CLASS_TEXT;
        if ("email".equals(type)) it |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        else if ("url".equals(type)) it |= InputType.TYPE_TEXT_VARIATION_URI;
        else if ("password".equals(type)) it |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
        else if ("number".equals(type) || "tel".equals(type)) it = InputType.TYPE_CLASS_NUMBER;
        if (multi) it |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        et.setInputType(it);
        et.setImeOptions(isSearch ? EditorInfo.IME_ACTION_SEARCH : EditorInfo.IME_ACTION_DONE);
        panel.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Dialog dlg = new Dialog(this);
        final Runnable confirm = new Runnable() {
            @Override
            public void run() {
                String text = et.getText().toString();
                dlg.dismiss();
                applyText(text, isSearch);
            }
        };
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && ev.getAction() == KeyEvent.ACTION_DOWN;
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_GO || enter) {
                    confirm.run();
                    return true;
                }
                return false;
            }
        });

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.setMargins(dp(6), dp(18), dp(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.setMargins(dp(6), dp(18), dp(6), 0);
        btns.addView(makeButton(isSearch ? "Търси" : "Готово", false, confirm), l1);
        btns.addView(makeButton("Отказ", false, new Runnable() {
            @Override
            public void run() {
                dlg.dismiss();
            }
        }), l2);
        panel.addView(btns);

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(panel);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(dp(760), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                textDialog = null;
                hideSystemBars();
                if (web != null) web.requestFocus();
            }
        });
        textDialog = dlg;
        dlg.show();
        et.requestFocus();
        et.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (imm != null && dlg.isShowing()) {
                    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 250);
    }

    private void applyText(String text, boolean submit) {
        if (web == null) return;
        web.evaluateJavascript(jsApplyText(text, submit), null);
        web.requestFocus();
        pokeCursor();
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

        // Чакащ клик се изпълнява веднага, ако е натиснат друг бутон (на мястото на курсора).
        if (okPending && down && !okKey) flushOk();
        if(code==KeyEvent.KEYCODE_MENU){if(down && e.getRepeatCount()==0)showSectionMenu();return true;}
        if(tvTimelineLayer!=null)return super.dispatchKeyEvent(e);

        // Моята телевизия: стрелките сменят канала, OK отваря списъка.
        if (tvMode) {
            if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
            return tvKey(e);
        }

        if (radioStation != null && nativeActive()) {
            if (playKey) { if (down && e.getRepeatCount()==0 && player!=null) {
                player.setPlayWhenReady(!player.getPlayWhenReady()); radioPlaybackStatus(); } return true; }
            return super.dispatchKeyEvent(e);
        }
        // Родният плеър: OK = пауза/пускане, наляво/надясно = превъртане.
        if (nativeActive()) {
            if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
            if (down && player != null) {
                if ((okKey || playKey) && e.getRepeatCount() == 0) {
                    okWasPlaying = player.getPlayWhenReady();
                    boolean play = !okWasPlaying;
                    player.setPlayWhenReady(play);
                    showNativeStatus(play ? "▶" : "❚❚  Пауза", 1200);
                } else if (okKey && e.getRepeatCount() == 10) {
                    // Задържане на OK: избор на качество (паузата от първото натискане се връща).
                    player.setPlayWhenReady(okWasPlaying);
                    tvQualityDialog();
                } else if (code == KeyEvent.KEYCODE_MENU) {
                    if (e.getRepeatCount() == 0) tvQualityDialog();
                } else if (code == KeyEvent.KEYCODE_DPAD_UP
                        || code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (e.getRepeatCount() == 0) cycleQuality();
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

        // Видеото на страницата е на цял екран. Със стрелките се движи курсорът (за лентата
        // с времето, субтитрите и т.н.). Кратко OK: натиска там, където е курсорът, а при
        // скрит курсор пуска/спира. Двойно OK: превъртане на списък. Задържане на OK: опции.
        if (customView != null) {
            if (code == KeyEvent.KEYCODE_MENU) {
                if (down && e.getRepeatCount() == 0) openOptions();
                return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN
                    || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (down) cursorArrow(customView, code, e.getRepeatCount());
                return true;
            }
            if (okKey) {
                handleOk(e);
                return true;
            }
            if (playKey && !e.isCanceled()) {
                if (down && e.getRepeatCount() == 0) togglePlayback();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }

        // Страницата е отворена. С курсор: стрелките го местят, OK е клик.
        // Без курсор: стрелките местят фокуса по страницата, OK натиска.
        // Двойно OK: превъртане (при сайт без курсор - първо временен курсор).
        // Задържане на OK (или бутон Menu): менюто на страницата.
        if (webActive()) {
            if (code == KeyEvent.KEYCODE_MENU) {
                if (down && e.getRepeatCount() == 0) openOptions();
                return true;
            }
            if (cursorOn()) {
                switch (code) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (down) cursorArrow(web, code, e.getRepeatCount());
                        return true;
                    default:
                        break;
                }
            }
            if (okKey) {
                handleOk(e);
                return true;
            }
            if (playKey) {
                if (down && e.getRepeatCount() == 0) togglePlayback();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ---- OK: клик, двойно OK и задържане ----

    private static final int DOUBLE_OK_MS = 350;
    private boolean okPending;
    private boolean okWasIdle;
    private long okPassedAt;

    // Кратко OK с курсор: клик на мястото на курсора. Изпълнява се след кратко изчакване,
    // за да може второто натискане да се разпознае като двойно OK (иначе би кликнало два пъти).
    private final Runnable okSingle = new Runnable() {
        @Override
        public void run() {
            okPending = false;
            if (customView != null) {
                if (!okWasIdle) {
                    tapAt(customView, cx, cy);
                    pokeCursor();
                } else {
                    togglePlayback();
                }
            } else if (webActive()) {
                if (okWasIdle) {
                    // Ако курсорът е бил скрит, първото натискане само го показва.
                    pokeCursor();
                } else {
                    tapAt(web, cx, cy);
                    pokeCursor();
                    checkEditableSoon();
                }
            }
        }
    };

    // Изпълнява чакащия клик веднага (когато веднага след него е натиснат друг бутон).
    private void flushOk() {
        if (!okPending) return;
        root.removeCallbacks(okSingle);
        okSingle.run();
    }

    private void cancelOk() {
        okPending = false;
        okPassedAt = 0;
        root.removeCallbacks(okSingle);
    }

    // OK в страница или на цял екран:
    //   кратко     - клик там, където е курсорът;
    //   двойно     - превъртане (едно OK го спира);
    //   задържане  - менюто с опции на страницата.
    private void handleOk(KeyEvent e) {
        long now = SystemClock.uptimeMillis();
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getRepeatCount() == 0) {
                okLongHandled = false;
                // Второ натискане скоро след първото: двойно OK.
                if (okPending || (!cursorOn() && now - okPassedAt < DOUBLE_OK_MS)) {
                    cancelOk();
                    okLongHandled = true; // при отпускане не е нито клик, нито задържане
                    doubleOk();
                }
            } else if (!okLongHandled) {
                okLongHandled = true;
                cancelOk();
                openOptions();
            }
            return;
        }
        // Отпускане на бутона.
        boolean skip = okLongHandled || e.isCanceled();
        okLongHandled = false;
        if (skip) return;
        if (scrollMode) {
            setScrollMode(false);
        } else if (now - scrollExitAt < 600) {
            // Второ натискане веднага след спиране на превъртането: не е клик.
            return;
        } else if (!cursorOn()) {
            sendOkToWeb();
            checkEditableSoon();
            okPassedAt = now;
        } else {
            okPending = true;
            okWasIdle = cursorIdle;
            root.postDelayed(okSingle, DOUBLE_OK_MS);
        }
    }

    private void handleBack() {
        if(tvTimelineLayer!=null){tvCloseTimeline();return;}
        if(tvMode && !tvVod && !tvListShown() && tvBanner!=null && tvControlsOpen){tvHideBannerNow();nativeLayer.setFocusableInTouchMode(true);nativeLayer.requestFocusFromTouch();return;}
        if (scrollMode) {
            setScrollMode(false);
            return;
        }
        if (tvMode) {
            if (tvVod) vodBackPressed();
            else if (tvListShown()) tvHideListNow();
            else tvLeave(true);
            return;
        }
        if (tvPanelShown()) {
            if(appBar!=null && !appBar.hasFocus()){focusAppNavigation();return;}
            if(tvSel!=TV_HOME || tvFavouritesOnly){tvSel=TV_HOME;tvFavouritesOnly=false;tvFill(null);}
            else showSection(SECTION_HOME);
            return;
        }
        if (catShown() && (web == null || web.getVisibility() != View.VISIBLE)) {
            if (srcDialog != null && srcDialog.isShowing()) return;
            if (catUp != null) catUp.run();
            else hideCatalog();
            return;
        }
        if (nativeActive()) {
            closeNative(true);
            return;
        }
        if (logShown()) {
            if (domShown()) backToOptions();
            else hideLog();
            return;
        }
        if (customView != null) {
            chromeClient.onHideCustomView();
            return;
        }
        if (web != null && web.getVisibility() == View.VISIBLE) {
            if (web.canGoBack()) web.goBack();
            else backToMenu();
            return;
        }
        if (appSection != SECTION_HOME) { showSection(SECTION_HOME); return; }
        new AlertDialog.Builder(this).setTitle("Изход от ТВ Сайтове?")
                .setPositiveButton("Изход",(d,w)->exitApp()).setNegativeButton("Остани",null).show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Задържането на Назад не трябва да връща няколко стъпки наведнъж.
            if (event.getRepeatCount() == 0) handleBack();
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
        endSwipe(true);
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        memoryForeground=true;updateAppMemory();
        if (web != null) web.onResume();
        hideSystemBars();
        // Менюто се подготвя при връщане, а не докато системата още сменя екрана.
        if (menuStale) {
            menuStale = false;
            fillMenu();
            scanCache(true);
        }
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
        memoryForeground=false;ui.removeCallbacks(appMemoryTick);
        // Когато излезеш (Home), всичко се спира и при връщане започваш от менюто.
        if (!exiting && !isFinishing() && !isChangingConfigurations()) {
            logEvent("Приложението е скрито (Home, друго приложение или угаснал екран)", true);
            catReturn = null;
            teardownSite();
            menuStale = true; // менюто се обновява при връщане (onResume)
        }
    }

    private boolean menuStale;

    @Override
    protected void onDestroy() {
        if(sectionDialog!=null)sectionDialog.dismiss();
        if(streamDialog!=null)streamDialog.dismiss();
        if(programmeDialog!=null)programmeDialog.dismiss();
        tvCloseTimeline();
        ui.removeCallbacksAndMessages(null);
        tvReset();
        catGen++;
        catCancelRequests();
        tvLogoTrim();
        if (tvLogoPool != null) tvLogoPool.shutdownNow();
        closeNative(false);
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
        // При Изход прекратяваме и процеса, за да не остава нищо на заден фон.
        if (exiting) {
            try {
                // Последните записи на събитията се довършват преди края.
                IO.shutdown();
                IO.awaitTermination(500, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    // ---------- Наши филми (каталог от TMDB) ----------

    // Отговорите идват винаги в главната нишка.
    private interface Done {
        void run(String json, String err);
    }

    private static class CTitle {
        int id;
        boolean tv;
        String name = "", poster = "", backdrop = "", year = "", overview = "", imdb = "", genres = "";
        String orig = ""; // оригиналното заглавие (за търсене на субтитри)
        double rating;
        int runtime;
        final ArrayList<int[]> seasons = new ArrayList<>(); // {номер, брой епизоди}
    }

    private static class CEpisode {
        int num;
        String name = "", date = "", overview = "", still = "";
        int runtime;
    }

        // Малка мрежова група за каталога: до 3 нишки с нисък приоритет, които се затварят,
    // когато няма работа (не държат памет, докато не гледаш каталога).
    private static final ExecutorService NET = newNetPool();
    private final HashSet<HttpURLConnection> catConnections = new HashSet<>();

    private HttpURLConnection catConnect(String url, int generation) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        synchronized (catConnections) {
            if (generation != catGen) throw new IOException("catalogue closed");
            catConnections.add(connection);
        }
        return connection;
    }

    private void catDisconnect(HttpURLConnection connection) {
        if (connection == null) return;
        synchronized (catConnections) { catConnections.remove(connection); }
    }

    private void catCancelRequests() {
        ((ThreadPoolExecutor) NET).getQueue().clear();
        synchronized (catConnections) {
            for (HttpURLConnection connection : catConnections) connection.disconnect();
            catConnections.clear();
        }
    }

    private static ExecutorService newNetPool() {
        ThreadPoolExecutor p = new ThreadPoolExecutor(3, 3, 20, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), lowPriority("tvs-net"));
        p.allowCoreThreadTimeOut(true);
        return p;
    }

    private static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    // Цветове на каталога: тъмно синьо-зелено (като киносалон) и кехлибарено за избраното.
    private static final int K_BG = TvTheme.BACKGROUND;
    private static final int K_SURF = TvTheme.SURFACE;
    private static final int K_SURF2 = TvTheme.SURFACE_ALT;
    private static final int K_LINE = TvTheme.OUTLINE;
    private static final int K_TEXT = TvTheme.TEXT;
    private static final int K_TEXT2 = TvTheme.MUTED;
    private static final int K_TEXT3 = TvTheme.MUTED;
    private static final int K_AMBER = TvTheme.BLUE;
    private static final int K_AMBER2 = TvTheme.BLUE_LIGHT;
    private static final int K_EMBER = TvTheme.BLUE;
    private static final int K_INK = Color.WHITE;
    private static final int K_ERR = 0xFFFF9A8A;
    private static final int R_TITLE = 0x7f0b0002; // ключ за таг: заглавието на клетката


    private Typeface tfSerif = Typeface.SERIF;

    private FrameLayout catPanel;
    private LinearLayout catToolbar;
    private ScrollView catScroll;
    private LinearLayout catBox;
    private Runnable catUp;      // какво прави "Назад" на текущия екран
    private Runnable catReturn;  // къде се връщаме, когато излезем от сайта
    private View catTile;        // плочката в менюто (за фокус при връщане)
    private View catMain;        // главният бутон на екрана
    private String catFocusKey;  // коя корица да е на фокус при връщане към списъка
    private volatile int catGen;  // номер на екрана: закъснели отговори се отхвърлят
    private ArrayList<String> srcQueue; // адреси на източниците за отворения сайт
    private int srcIdx;
    private Dialog srcDialog;

    // Отговорите на TMDB се пазят 30 минути, за да не се тегли едно и също.
    private static final HashMap<String, String> TMDB_JSON = new HashMap<>();
    private static final HashMap<String, Long> TMDB_AT = new HashMap<>();
    // Кориците и снимките се пазят само в паметта и се чистят при излизане от каталога.
    private static final LruCache<String, Bitmap> POSTERS = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final LruCache<String, Bitmap> BACKDROPS = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };

    private String tmdbKey() {
        String k = prefs.getString("tmdbKey", "").trim();
        return k.isEmpty() ? TMDB_KEY.trim() : k;
    }

    // Въведеното на кутията е с предимство пред адресите в кода.
    private String sourceTpl(boolean tv, int i) {
        String s = prefs.getString((tv ? "tsrc" : "fsrc") + i, "").trim();
        String[] code = tv ? TV_SOURCES : FILM_SOURCES;
        if (s.isEmpty() && i < code.length) s = code[i].trim();
        return s;
    }

    private static final int SRC_SLOTS = 10; // основен + 9 резервни

    private int srcPref(boolean tv) {
        int p = prefs.getInt(tv ? "tvSrcPref" : "mvSrcPref", 0);
        if (p < 0 || p >= SRC_SLOTS || sourceTpl(tv, p).isEmpty()) {
            for (int i = 0; i < SRC_SLOTS; i++) {
                if (!sourceTpl(tv, i).isEmpty()) return i;
            }
            return 0;
        }
        return p;
    }

    private void srcSetPref(boolean tv, int slot) {
        prefs.edit().putInt(tv ? "tvSrcPref" : "mvSrcPref", slot).apply();
    }

    // Номерата на попълнените места за адреси (по ред).
    private int[] srcSlots(boolean tv) {
        int[] tmp = new int[SRC_SLOTS];
        int n = 0;
        for (int i = 0; i < SRC_SLOTS; i++) {
            if (!sourceTpl(tv, i).isEmpty()) tmp[n++] = i;
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }

    // Само името на сайта (без http и без {tmdb} и т.н.), за надписа на билетчето.
    private String srcHost(boolean tv, int slot) {
        String tpl = sourceTpl(tv, slot).replace("{", "").replace("}", "");
        String h = hostOf(tpl);
        return h == null || h.isEmpty() ? "източник " + (slot + 1) : h;
    }

    private int sourceCount(boolean tv) {
        return srcSlots(tv).length;
    }

    // Готовите адреси (основен, после резервните) за филм или епизод.
    private ArrayList<String> sourceUrls(CTitle t, int season, int episode) {
        ArrayList<String> out = new ArrayList<>();
        int pref = srcPref(t.tv);
        for (int k = 0; k < SRC_SLOTS; k++) {
            int i = (pref + k) % SRC_SLOTS; // избраният източник е пръв, другите са резервни
            String s = sourceTpl(t.tv, i);
            if (s.isEmpty()) continue;
            if (s.contains("{imdb}") && t.imdb.isEmpty()) continue; // няма IMDb номер
            String url = s.replace("{tmdb}", String.valueOf(t.id))
                    .replace("{imdb}", t.imdb)
                    .replace("{season}", String.valueOf(season))
                    .replace("{episode}", String.valueOf(episode));
            if (url.startsWith("http://") || url.startsWith("https://")) out.add(url);
        }
        return out;
    }

        private static String tmdbCached(String path) {
        synchronized (TMDB_JSON) {
            Long at = TMDB_AT.get(path);
            if (at != null && SystemClock.elapsedRealtime() - at < 30L * 60 * 1000) {
                return TMDB_JSON.get(path);
            }
        }
        return null;
    }

    // Заявка към TMDB на заден план. Дълъг ключ (започва с "eyJ") се праща като токен,
    // късият (32 знака) като api_key.
    private void tmdbFetch(final String path, final Done done) {
        final int generation = catGen;
        final String hit = tmdbCached(path);
        if (hit != null) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    if (generation == catGen) done.run(hit, null);
                }
            });
            return;
        }
        final String key = tmdbKey();
        if (key.isEmpty()) {
            done.run(null, "Няма ключ за TMDB");
            return;
        }
        submit(NET, new Runnable() {
            @Override
            public void run() {
                String json = null;
                String err = null;
                HttpURLConnection c = null;
                boolean reuse = false;
                try {
                    boolean bearer = key.length() > 60;
                    String url = "https://api.themoviedb.org/3" + path;
                    if (!bearer) {
                        url += (path.contains("?") ? "&" : "?") + "api_key="
                                + java.net.URLEncoder.encode(key, "UTF-8");
                    }
                    c = catConnect(url, generation);
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    c.setRequestProperty("Accept", "application/json");
                    if (bearer) c.setRequestProperty("Authorization", "Bearer " + key);
                    int rc = c.getResponseCode();
                    if (rc == 200) {
                        json = readAll(c.getInputStream());
                        reuse = true; // връзката се връща в групата за следваща заявка
                    } else if (rc == 401) {
                        err = "TMDB не прие ключа (код 401). Провери го от бутона Източници.";
                    } else {
                        err = "TMDB отговори с код " + rc;
                    }
                } catch (Exception e) {
                    err = "Няма връзка с TMDB";
                } finally {
                    if (c != null && !reuse) c.disconnect();
                }
                catDisconnect(c);
                if (generation != catGen) return;
                if (json != null) {
                    synchronized (TMDB_JSON) {
                        if (TMDB_JSON.size() > 60) {
                            TMDB_JSON.clear();
                            TMDB_AT.clear();
                        }
                        TMDB_JSON.put(path, json);
                        TMDB_AT.put(path, SystemClock.elapsedRealtime());
                    }
                }
                final String j = json;
                final String e = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || generation != catGen) return;
                        done.run(j, e);
                    }
                });
            }
        });
    }

    private static String jstr(JSONObject o, String k) {
        return o.isNull(k) ? "" : o.optString(k, "");
    }

    private static ArrayList<CTitle> parseList(String json, boolean tv) {
        return parseList(json, tv, 14);
    }

    private static ArrayList<CTitle> parseList(String json, boolean tv, int max) {
        ArrayList<CTitle> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(json).optJSONArray("results");
            if (a == null) return out;
            for (int i = 0; i < a.length() && out.size() < max; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                CTitle t = new CTitle();
                t.id = o.optInt("id");
                t.tv = tv;
                t.name = jstr(o, tv ? "name" : "title");
                t.orig = jstr(o, tv ? "original_name" : "original_title");
                t.poster = jstr(o, "poster_path");
                t.backdrop = jstr(o, "backdrop_path");
                String d = jstr(o, tv ? "first_air_date" : "release_date");
                t.year = d.length() >= 4 ? d.substring(0, 4) : "";
                t.rating = o.optDouble("vote_average", 0);
                t.overview = jstr(o, "overview");
                if (t.id != 0 && !t.name.isEmpty() && !t.poster.isEmpty()) out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // Допълва заглавието с описание, IMDb номер, жанрове и сезони.
    private static void parseDetail(String json, CTitle t) {
        try {
            JSONObject o = new JSONObject(json);
            String ov = jstr(o, "overview");
            if (!ov.isEmpty()) t.overview = ov;
            String og = jstr(o, t.tv ? "original_name" : "original_title");
            if (!og.isEmpty()) t.orig = og;
            String p = jstr(o, "poster_path");
            if (!p.isEmpty()) t.poster = p;
            String bd = jstr(o, "backdrop_path");
            if (!bd.isEmpty()) t.backdrop = bd;
            t.rating = o.optDouble("vote_average", t.rating);
            String d = jstr(o, t.tv ? "first_air_date" : "release_date");
            if (d.length() >= 4) t.year = d.substring(0, 4);
            if (t.tv) {
                JSONObject ex = o.optJSONObject("external_ids");
                if (ex != null) t.imdb = jstr(ex, "imdb_id");
                JSONArray rt = o.optJSONArray("episode_run_time");
                if (rt != null && rt.length() > 0) t.runtime = rt.optInt(0);
            } else {
                t.imdb = jstr(o, "imdb_id");
                t.runtime = o.optInt("runtime");
            }
            StringBuilder sb = new StringBuilder();
            JSONArray g = o.optJSONArray("genres");
            if (g != null) {
                for (int i = 0; i < g.length() && i < 4; i++) {
                    JSONObject go = g.optJSONObject(i);
                    if (go == null) continue;
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(jstr(go, "name"));
                }
            }
            t.genres = sb.toString();
            t.seasons.clear();
            JSONArray ss = o.optJSONArray("seasons");
            if (t.tv && ss != null) {
                for (int i = 0; i < ss.length(); i++) {
                    JSONObject so = ss.optJSONObject(i);
                    if (so == null) continue;
                    int n = so.optInt("season_number");
                    if (n > 0) t.seasons.add(new int[]{n, so.optInt("episode_count")});
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static ArrayList<CEpisode> parseEpisodes(String json) {
        ArrayList<CEpisode> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(json).optJSONArray("episodes");
            if (a == null) return out;
            for (int i = 0; i < a.length() && out.size() < 80; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                CEpisode e = new CEpisode();
                e.num = o.optInt("episode_number");
                e.name = jstr(o, "name");
                e.date = jstr(o, "air_date");
                e.runtime = o.optInt("runtime");
                e.still = jstr(o, "still_path");
                String ov = jstr(o, "overview");
                e.overview = ov.length() > 220 ? ov.substring(0, 220) : ov;
                if (e.num > 0) out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // Заглавия от резултат (търсене смесва филми и сериали; хората се пропускат).
    private static ArrayList<CTitle> parseMixed(String json) {
        ArrayList<CTitle> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(json).optJSONArray("results");
            if (a == null) return out;
            for (int i = 0; i < a.length() && out.size() < 28; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String mt = jstr(o, "media_type");
                boolean tv = mt.equals("tv");
                if (!tv && !mt.equals("movie")) continue;
                CTitle t = new CTitle();
                t.id = o.optInt("id");
                t.tv = tv;
                t.name = jstr(o, tv ? "name" : "title");
                t.orig = jstr(o, tv ? "original_name" : "original_title");
                t.poster = jstr(o, "poster_path");
                t.backdrop = jstr(o, "backdrop_path");
                String d = jstr(o, tv ? "first_air_date" : "release_date");
                t.year = d.length() >= 4 ? d.substring(0, 4) : "";
                t.rating = o.optDouble("vote_average", 0);
                t.overview = jstr(o, "overview");
                if (t.id != 0 && !t.name.isEmpty()) out.add(t);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // "2019-05-06" става "6 май 2019".
    private static String bgDate(String iso) {
        try {
            if (iso == null || iso.length() < 10) return "";
            int y = Integer.parseInt(iso.substring(0, 4));
            int m = Integer.parseInt(iso.substring(5, 7));
            int d = Integer.parseInt(iso.substring(8, 10));
            if (m < 1 || m > 12) return "";
            return d + " " + BG_MONTHS[m - 1] + " " + y;
        } catch (Exception e) {
            return "";
        }
    }

    private static String fmtRuntime(int min) {
        if (min <= 0) return "";
        if (min < 60) return min + " мин";
        int h = min / 60;
        int m = min % 60;
        return h + " ч" + (m > 0 ? " " + m + " мин" : "");
    }

    // ---- картини ----

    private interface ImageSink {
        void set(Bitmap b, boolean instant);
    }

    // Размерът на корицата за редовете: на много големи екрани е по-едър.
    private String rowSize() {
        return getResources().getDisplayMetrics().widthPixels >= 2400 ? "w342" : "w185";
    }

    private String backdropSize() {
        return getResources().getDisplayMetrics().widthPixels >= 1700 ? "w1280" : "w780";
    }

    // Сваля и подава картината; ако вече е в паметта, подава я веднага.
    private void loadImage(final String size, final String path, final int gen, final boolean wide,
                           final ImageSink sink) {
        if (path == null || path.isEmpty()) return;
        final String key = size + path;
        final LruCache<String, Bitmap> cache = wide ? BACKDROPS : POSTERS;
        Bitmap hit = cache.get(key);
        if (hit != null) {
            sink.set(hit, true);
            return;
        }
        submit(NET, new Runnable() {
            @Override
            public void run() {
                if (gen != catGen) return; // екранът вече е сменен: не свалям напразно
                Bitmap b = null;
                HttpURLConnection c = null;
                boolean reuse = false;
                try {
                    c = catConnect("https://image.tmdb.org/t/p/" + size + path, gen);
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    if (c.getResponseCode() == 200) {
                        // Целият отговор се чете и потокът се затваря: връзката се използва
                        // повторно за следващата картина (без ново договаряне на защитата).
                        byte[] data = readBytes(c.getInputStream(), wide ? 900000 : 600000);
                        reuse = true;
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inPreferredConfig = Bitmap.Config.RGB_565; // наполовина памет
                        b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (c != null && !reuse) c.disconnect();
                }
                catDisconnect(c);
                if (b == null || gen != catGen) return;
                final Bitmap fb = b;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != catGen || isFinishing()) return;
                        cache.put(key, fb);
                        sink.set(fb, false);
                    }
                });
            }
        });
    }

        private static void roundClip(View v, final int radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    // ---- изгледи на каталога (рисуват се сами: малко изгледи, леко за кутията) ----

    // Звезда на мрежа 24x24 (за оценката).
    private static final float[] STAR_XY = {12f, 2.8f, 14.9f, 8.8f, 21.5f, 9.7f, 16.7f, 14.3f,
            17.9f, 20.8f, 12f, 17.7f, 6.1f, 20.8f, 7.3f, 14.3f, 2.5f, 9.7f, 9.1f, 8.8f};

    private static void starPath(Path p) {
        p.reset();
        p.moveTo(STAR_XY[0], STAR_XY[1]);
        for (int i = 2; i + 1 < STAR_XY.length; i += 2) p.lineTo(STAR_XY[i], STAR_XY[i + 1]);
        p.close();
    }

    // Прокрутки с малко повече поле: при фокус се вижда целият уголемен елемент, а в редовете
    // се показва и част от следващата корица.
    private class CatScroll extends ScrollView {
        CatScroll(Context c) {
            super(c);
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            Rect r = new Rect(rect);
            r.top -= u(30);
            r.bottom += u(30);
            return super.computeScrollDeltaToGetChildRectOnScreen(r);
        }
    }

    private class RowScroll extends HorizontalScrollView {
        RowScroll(Context c) {
            super(c);
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            Rect r = new Rect(rect);
            r.left -= u(90);
            r.right += u(90);
            return super.computeScrollDeltaToGetChildRectOnScreen(r);
        }
    }

    // Мек преход (градиент) върху картина: за да се чете текстът.
    private static class ScrimView extends View {
        private final Paint p = new Paint();
        private final boolean vertical;
        private final int[] colors;
        private final float[] pos;

        ScrimView(Context c, boolean vertical, int[] colors, float[] pos) {
            super(c);
            this.vertical = vertical;
            this.colors = colors;
            this.pos = pos;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w <= 0 || h <= 0) {
                p.setShader(null);
                return;
            }
            p.setShader(new LinearGradient(0, 0, vertical ? 0 : w, vertical ? h : 0, colors, pos,
                    Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            c.drawRect(0, 0, getWidth(), getHeight(), p);
        }
    }

    // Широка картина във фона, с плавно появяване. Без картина е меко цветно петно.
    private static class BackdropView extends View {
        private Bitmap cur;
        private Bitmap prev;
        private float t = 1f;
        private int tint = TvTheme.SURFACE_ALT;
        private Shader glow;
        private ValueAnimator anim;
        private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint gp = new Paint();
        private final Rect src = new Rect();
        private final RectF dst = new RectF();

        BackdropView(Context c) {
            super(c);
        }

        void setTint(int c) {
            tint = c;
            rebuildGlow();
            invalidate();
        }

        private void rebuildGlow() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                glow = null;
                return;
            }
            glow = new RadialGradient(w * 0.65f, h * 0.38f, w * 0.62f,
                    new int[]{tint, tint & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            rebuildGlow();
        }

        // b може да е null (празно). Плавният преход се прави само при нова картина.
        void setImage(Bitmap b, boolean fade) {
            if (b == cur) return;
            if (anim != null) anim.cancel();
            prev = fade ? cur : null;
            cur = b;
            if (fade && b != null) {
                t = 0f;
                anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(300);
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        t = (Float) a.getAnimatedValue();
                        if (t >= 1f) prev = null;
                        invalidate();
                    }
                });
                anim.start();
            } else {
                prev = null;
                t = 1f;
            }
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            prev = null;
            t = 1f;
            super.onDetachedFromWindow();
        }

        private void cover(Canvas c, Bitmap b, int alpha) {
            int w = getWidth();
            int h = getHeight();
            float vr = (float) w / h;
            float br = (float) b.getWidth() / b.getHeight();
            int sw;
            int sh;
            int sx;
            int sy;
            if (br > vr) { // картината е по-широка: режат се страните
                sh = b.getHeight();
                sw = Math.round(sh * vr);
                sx = (b.getWidth() - sw) / 2;
                sy = 0;
            } else { // по-висока: реже се отгоре и отдолу, лицата са по-често над средата
                sw = b.getWidth();
                sh = Math.round(sw / vr);
                sx = 0;
                sy = Math.round((b.getHeight() - sh) * 0.25f);
            }
            src.set(sx, sy, sx + sw, sy + sh);
            dst.set(0, 0, w, h);
            p.setAlpha(alpha);
            c.drawBitmap(b, src, dst, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            c.drawColor(TvTheme.BACKGROUND_TOP);
            if (glow != null) {
                gp.setShader(glow);
                c.drawRect(0, 0, w, h, gp);
            }
            if (prev != null) cover(c, prev, 255);
            if (cur != null) cover(c, cur, Math.round(255 * t));
        }
    }

    // Корица, снимка от епизод или цветна плочка. На избираемата корица има рамка при фокус,
    // а когато не е на фокус, е леко притъмнена: "осветено е само избраното".
    private class ArtView extends View {
        private final int radius;
        private final boolean cell;
        private final int c0;
        private final int c1;
        private final String letter;
        private String rating = "";
        private String number = "";
        private boolean play;
        private Bitmap bmp;
        private boolean containImage;
        private final RectF imageFrame=new RectF();
        private BitmapShader shader;
        private float fade = 1f;
        private ValueAnimator anim;
        private Shader grad;
        private Shader shade;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final Matrix mx = new Matrix();
        private final Path pth = new Path();

        ArtView(Context ctx, int id, String name, int radiusPx, boolean cell) {
            super(ctx);
            this.c0 = TvTheme.SURFACE_ALT;
            this.c1 = TvTheme.BACKGROUND_TOP;
            this.letter = initial(name);
            this.radius = radiusPx;
            this.cell = cell;
            if (cell) {
                setFocusable(true);
                setClickable(true);
            }
            final int rad = radiusPx;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad);
                }
            });
        }

        void setRating(double r) {
            rating = r > 0 ? String.format(Locale.US, "%.1f", r) : "";
            invalidate();
        }

        void setNumber(String n) {
            number = n;
            invalidate();
        }

        void setPlay(boolean on) {
            play = on;
            invalidate();
        }

        void setBitmap(Bitmap b, boolean animate) {
            if (b == null) { if(anim!=null)anim.cancel();bmp=null;shader=null;img.setShader(null);fade=1f;invalidate();return; }
            bmp = b;
            shader = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            img.setShader(shader);
            fitShader();
            if (anim != null) anim.cancel();
            if (animate) {
                fade = 0f;
                anim = ValueAnimator.ofFloat(0f, 1f);
                anim.setDuration(220);
                anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator a) {
                        fade = (Float) a.getAnimatedValue();
                        invalidate();
                    }
                });
                anim.start();
            } else {
                fade = 1f;
            }
            invalidate();
        }

        private void fitShader() {
            if (shader == null || bmp == null || getWidth() <= 0 || getHeight() <= 0) return;
            float sx=(float)getWidth()/bmp.getWidth(),sy=(float)getHeight()/bmp.getHeight();
            float s=containImage?Math.min(sx,sy):Math.max(sx,sy);
            float dx=(getWidth()-bmp.getWidth()*s)/2f,dy=(getHeight()-bmp.getHeight()*s)/2f;
            imageFrame.set(dx,dy,dx+bmp.getWidth()*s,dy+bmp.getHeight()*s);
            mx.reset();
            mx.setScale(s, s);
            mx.postTranslate((getWidth() - bmp.getWidth() * s) / 2f,
                    (getHeight() - bmp.getHeight() * s) / 2f);
            shader.setLocalMatrix(mx);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w > 0 && h > 0) {
                grad = new LinearGradient(0, 0, w, h, c0, c1, Shader.TileMode.CLAMP);
                shade = new LinearGradient(0, h * 0.40f, 0, h, 0x00000000, 0xB0000000,
                        Shader.TileMode.CLAMP);
            }
            fitShader();
        }

        @Override
        protected void onDetachedFromWindow() {
            if (anim != null) anim.cancel();
            fade = 1f;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            rf.set(0, 0, w, h);
            fill.setStyle(Paint.Style.FILL);
            fill.setShader(grad);
            c.drawRoundRect(rf, radius, radius, fill);
            fill.setShader(null);
            if (bmp == null || fade < 1f) {
                // докато няма корица: голяма буква
                tp.setTypeface(tfSerif);
                tp.setTextAlign(Paint.Align.CENTER);
                tp.setTextSize(Math.min(w, h) * 0.42f);
                tp.setColor(((int) (0x59 * (1f - (bmp == null ? 0f : fade))) << 24) | 0x00FFFFFF);
                c.drawText(letter, w / 2f, h / 2f + tp.getTextSize() * 0.35f, tp);
            }
            if (bmp != null) {
                img.setAlpha(Math.round(255 * fade));
                c.drawRoundRect(containImage?imageFrame:rf, radius, radius, img);
            }
            if (!number.isEmpty() || play) {
                fill.setShader(shade);
                c.drawRoundRect(rf, radius, radius, fill);
                fill.setShader(null);
            }
            if (!number.isEmpty()) {
                tp.setTypeface(tfSerif);
                tp.setTextAlign(Paint.Align.LEFT);
                tp.setTextSize(h * 0.27f);
                tp.setColor(Color.WHITE);
                c.drawText(number, h * 0.09f, h - h * 0.08f, tp);
            }
            if (play) {
                float r = h * 0.14f;
                float cx = w - h * 0.08f - r;
                float cy = h - h * 0.08f - r;
                fill.setColor(0xA00D1119);
                c.drawCircle(cx, cy, r, fill);
                fill.setColor(K_AMBER2);
                pth.reset();
                pth.moveTo(cx - r * 0.28f, cy - r * 0.44f);
                pth.lineTo(cx - r * 0.28f, cy + r * 0.44f);
                pth.lineTo(cx + r * 0.46f, cy);
                pth.close();
                c.drawPath(pth, fill);
            }
            if (!rating.isEmpty()) {
                float ph = u(24);
                float padL = u(7);
                float sz = u(13);
                float gap = u(4);
                float padR = u(8);
                tp.setTypeface(Typeface.DEFAULT_BOLD);
                tp.setTextAlign(Paint.Align.LEFT);
                tp.setTextSize(u(14));
                float tw = tp.measureText(rating);
                float pw = padL + sz + gap + tw + padR;
                float px = w - u(8) - pw;
                float py = u(8);
                fill.setColor(0xBA0D1119);
                c.drawRoundRect(px, py, px + pw, py + ph, ph / 2f, ph / 2f, fill);
                fill.setColor(K_AMBER);
                starPath(pth);
                c.save();
                c.translate(px + padL, py + (ph - sz) / 2f);
                c.scale(sz / 24f, sz / 24f);
                c.drawPath(pth, fill);
                c.restore();
                tp.setColor(K_TEXT);
                c.drawText(rating, px + padL + sz + gap, py + ph / 2f + tp.getTextSize() * 0.35f, tp);
            }
            if (cell) {
                if (isFocused() || isActivated()) {
                    float sw = u(3);
                    fill.setStyle(Paint.Style.STROKE);
                    fill.setStrokeWidth(sw);
                    fill.setColor(TvTheme.FOCUS);
                    rf.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
                    c.drawRoundRect(rf, radius - sw / 2f, radius - sw / 2f, fill);
                    fill.setStyle(Paint.Style.FILL);
                } else {
                    fill.setColor(0x100D1119);
                    c.drawRoundRect(rf, radius, radius, fill);
                }
            }
        }
    }

    // Билетче за източник: прозорче с номер, прекъсната линия и име на сайта.
    private class TicketView extends View {
        private final int num;
        private final String host;
        private String caption = "";
        private boolean preferred;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Path shape = new Path();
        private final Path notch = new Path();
        private final Path perf = new Path();
        private final RectF rf = new RectF();
        private final DashPathEffect dash = new DashPathEffect(new float[]{3f * U, 6f * U}, 0f);
        private float s = 1f;
        private float px;

        TicketView(Context ctx, int num, String host) {
            super(ctx);
            this.num = num;
            this.host = host;
            setFocusable(true);
            setClickable(true);
            line.setStyle(Paint.Style.STROKE);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                            14f * view.getWidth() / 232f);
                }
            });
        }

        void setPreferred(boolean on, String caption) {
            this.preferred = on;
            this.caption = caption;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w <= 0 || h <= 0) return;
            s = w / 232f;
            float rr = 14f * s;
            px = 64f * s;
            float nr = 9f * s;
            rf.set(0, 0, w, h);
            shape.reset();
            shape.addRoundRect(rf, rr, rr, Path.Direction.CW);
            notch.reset();
            notch.addCircle(px, 0, nr, Path.Direction.CW);
            notch.addCircle(px, h, nr, Path.Direction.CW);
            shape.op(notch, Path.Op.DIFFERENCE);
            perf.reset();
            perf.moveTo(px, nr + 6f * s);
            perf.lineTo(px, h - nr - 6f * s);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            boolean f = isFocused();
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(f ? TvTheme.SURFACE_ALT : K_SURF);
            c.drawPath(shape, fill);
            line.setPathEffect(null);
            line.setStrokeWidth(f ? 3f * U : (preferred ? 2f * U : 1.5f * U));
            line.setColor(f ? TvTheme.FOCUS : preferred ? TvTheme.BLUE_LIGHT : TvTheme.OUTLINE);
            c.drawPath(shape, line);
            line.setStrokeWidth(2f * U);
            line.setColor(TvTheme.OUTLINE);
            line.setPathEffect(dash);
            c.drawPath(perf, line);
            line.setPathEffect(null);
            // номер
            tp.setTypeface(tfSerif);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(38f * s);
            tp.setColor(preferred ? K_AMBER : 0xFF9FB6BD);
            c.drawText(String.valueOf(num), px / 2f, h / 2f + tp.getTextSize() * 0.34f, tp);
            // име на сайта и поясняващ надпис
            float x = px + 16f * s;
            float avail = w - x - 12f * s;
            float hostSize = Math.max(17f * U, 19f * s);
            float capSize = Math.max(14f * U, 14f * s);
            float total = hostSize + capSize + 3f * U;
            float top = (h - total) / 2f;
            tp.setTypeface(tfMedium);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTextSize(hostSize);
            tp.setColor(K_TEXT);
            CharSequence hs = TextUtils.ellipsize(host, tp, avail, TextUtils.TruncateAt.END);
            c.drawText(hs, 0, hs.length(), x, top + hostSize * 0.84f, tp);
            tp.setTextSize(capSize);
            tp.setColor(preferred ? K_AMBER : K_TEXT3);
            c.drawText(caption, x, top + hostSize + 3f * U + capSize * 0.84f, tp);
        }
    }

    // Цветове на жанровете: две бои за преливане.
    private static int[] genreCols(int gid) {
        switch (gid) {
            case 28: case 10759: return new int[]{0xFFF2711C, 0xFF6B1608};
            case 12: return new int[]{0xFFE8A53A, 0xFF2D5B3B};
            case 16: return new int[]{0xFFF472B6, 0xFF5B21B6};
            case 35: return new int[]{0xFFFFC93C, 0xFFE8590C};
            case 80: return new int[]{0xFF5D7A96, 0xFF1B2147};
            case 99: return new int[]{0xFF2E8F9E, 0xFF0C2A4A};
            case 18: return new int[]{0xFFA8456B, 0xFF2A0F26};
            case 10751: return new int[]{0xFF4FB6E8, 0xFF16607A};
            case 14: case 10765: return new int[]{0xFF8B5CF6, 0xFF1E1B6B};
            case 36: return new int[]{0xFFB98B52, 0xFF3B2410};
            case 27: return new int[]{0xFFB91C1C, 0xFF150607};
            case 10402: return new int[]{0xFFD946EF, 0xFF4A1572};
            case 9648: return new int[]{0xFF4F46E5, 0xFF0E0C2E};
            case 10749: return new int[]{0xFFFB7185, 0xFF7A1233};
            case 878: return new int[]{0xFF22D3EE, 0xFF0B3A66};
            case 53: return new int[]{0xFF64748B, 0xFF0B1220};
            case 10752: case 10768: return new int[]{0xFF7A8B4A, 0xFF232A14};
            case 37: return new int[]{0xFFD9A066, 0xFF6B2D12};
            case 10762: return new int[]{0xFF34D399, 0xFF0F5C4D};
            case 0: return new int[]{0xFFFFB547, 0xFFC2410C};
            default: return new int[]{0xFF3B6E8F, 0xFF12263A};
        }
    }

    // Плочка на жанр: цветно преливане, голяма бледа буква и име с главни и малки букви.
    private class GenreView extends View {
        private final String name;
        private final String letter;
        private final int[] cols;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint ghost = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rf = new RectF();
        private final Path clip = new Path();
        private Shader grad;
        private Shader shade;
        private StaticLayout layout;
        private final int radius;

        GenreView(Context ctx, String name, int gid) {
            super(ctx);
            this.name = name;
            this.letter = initial(name);
            this.cols = new int[]{TvTheme.SURFACE_ALT,TvTheme.BACKGROUND_TOP};
            this.radius = u(18);
            setFocusable(true);
            setClickable(true);
            final int rad = radius;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad);
                }
            });
            ghost.setTypeface(tfSerif);
            ghost.setTextAlign(Paint.Align.RIGHT);
            ghost.setColor(0x24FFFFFF);
            namePaint.setTypeface(tfSerif);
            namePaint.setColor(Color.WHITE);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w <= 0 || h <= 0) return;
            grad = new LinearGradient(0, 0, w, h, cols[0], cols[1], Shader.TileMode.CLAMP);
            shade = new LinearGradient(0, h * 0.35f, 0, h, 0x00000000, 0x59000000,
                    Shader.TileMode.CLAMP);
            rf.set(0, 0, w, h);
            clip.reset();
            clip.addRoundRect(rf, radius, radius, Path.Direction.CW);
            ghost.setTextSize(h * 1.28f);
            float size = 28f * U;
            int avail = Math.max(1, w - u(32));
            do {
                namePaint.setTextSize(size);
                layout = new StaticLayout(name, namePaint, avail, Layout.Alignment.ALIGN_NORMAL,
                        1f, 0f, false);
                size *= 0.92f;
            } while (layout.getLineCount() > 2 && size > 15f * U);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            rf.set(0, 0, w, h);
            fill.setStyle(Paint.Style.FILL);
            fill.setShader(grad);
            c.drawRoundRect(rf, radius, radius, fill);
            // голяма бледа буква, отрязана от ръба на плочката
            c.save();
            c.clipPath(clip);
            c.drawText(letter, w + h * 0.10f, h * 1.16f, ghost);
            c.restore();
            fill.setShader(shade);
            c.drawRoundRect(rf, radius, radius, fill);
            fill.setShader(null);
            if (layout != null) {
                c.save();
                c.translate(u(16), h - u(14) - layout.getHeight());
                layout.draw(c);
                c.restore();
            }
            if (isFocused()) {
                float sw = u(3);
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(sw);
                fill.setColor(TvTheme.FOCUS);
                rf.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
                c.drawRoundRect(rf, radius - sw / 2f, radius - sw / 2f, fill);
                fill.setStyle(Paint.Style.FILL);
            } else {
                fill.setColor(0x260D1119);
                c.drawRoundRect(rf, radius, radius, fill);
            }
        }
    }

    // ---- общи елементи ----

    private Runnable catParent;   // екранът, от който е отворена страницата на заглавие
    private String catSrcKey;     // кое билетче да е на фокус при връщане от сайта
    private CTitle focusTitle;    // заглавието, на което е фокусът
    private Runnable focusAct;    // обновява информацията за него (начало, предложения)
    private final Runnable focusTick = new Runnable() {
        @Override
        public void run() {
            if (focusAct != null && focusTitle != null && catShown()) focusAct.run();
        }
    };

    // Фокусът в каталога: леко уголемяване, кехлибарено сияние (на по-нови версии на Андроид).
    private final View.OnFocusChangeListener kFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            float k = v.getTag(R_SCALE) instanceof Float ? (Float) v.getTag(R_SCALE) : 1.07f;
            v.animate().scaleX(has ? k : 1f).scaleY(has ? k : 1f).setDuration(130).start();
            v.setTranslationZ(has ? 16 * U : 0f);
            if (Build.VERSION.SDK_INT >= 28) {
                v.setOutlineSpotShadowColor(has ? K_AMBER : Color.BLACK);
                v.setOutlineAmbientShadowColor(has ? 0x665F536F : Color.BLACK);
            }
        }
    };

    // Същото за корица, но и съобщава кое заглавие е на фокус (с малко изчакване).
    private final View.OnFocusChangeListener cellFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            kFocus.onFocusChange(v, has);
            if (has && focusAct != null && v.getTag(R_TITLE) instanceof CTitle) {
                focusTitle = (CTitle) v.getTag(R_TITLE);
                ui.removeCallbacks(focusTick);
                ui.postDelayed(focusTick, 170);
            }
        }
    };


    // Заглавие с шрифт със засечки: без излишно място отгоре и отдолу.
    private TextView serifText(String s, float sizeUnits, int color, boolean oneLine) {
        TextView t = text(s, sizeUnits, color, tfSerif, oneLine);
        t.setIncludeFontPadding(false);
        if (!oneLine) t.setLineSpacing(0, 0.95f);
        return t;
    }

    private StateListDrawable amberBg(int radiusPx) {
        return TvTheme.control(U, true);
    }

    // Бутон-хапче: икона и надпис; на фокус е с кехлибарена рамка.
    private LinearLayout kPill(int icon, String label, final Runnable action) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(u(icon >= 0 ? 20 : 24), 0, u(24), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(TvTheme.control(U,false));
        if (icon >= 0) {
            LinearLayout.LayoutParams ilp = lp(u(22), u(22));
            ilp.rightMargin = u(10);
            b.addView(new IconView(this, icon, K_TEXT2), ilp);
        }
        b.addView(text(label, 19, K_TEXT, tfMedium, true));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setTag(R_SCALE, 1.06f);
        b.setOnFocusChangeListener(kFocus);
        return b;
    }

    // Главният бутон: кехлибарен, голям.
    private LinearLayout kPrimary(int icon, String label, final Runnable action) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(u(26), 0, u(34), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(amberBg(u(36)));
        LinearLayout.LayoutParams ilp = lp(u(26), u(26));
        ilp.rightMargin = u(14);
        b.addView(new IconView(this, icon, K_INK), ilp);
        b.addView(text(label, 23, K_INK, Typeface.DEFAULT_BOLD, true));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setTag(R_SCALE, 1.04f);
        b.setOnFocusChangeListener(kFocus);
        return b;
    }

    private StateListDrawable tabBg() {
        int rad = u(26);
        GradientDrawable fsel = new GradientDrawable();
        fsel.setColor(K_AMBER);
        fsel.setCornerRadius(rad);
        fsel.setStroke(u(3), Color.WHITE);
        GradientDrawable fo = new GradientDrawable();
        fo.setColor(K_SURF2);
        fo.setCornerRadius(rad);
        fo.setStroke(u(2), TvTheme.FOCUS);
        GradientDrawable sel = new GradientDrawable();
        sel.setColor(K_AMBER);
        sel.setCornerRadius(rad);
        GradientDrawable n = new GradientDrawable();
        n.setColor(0x14FFFFFF);
        n.setCornerRadius(rad);
        n.setStroke(Math.max(1, u(1)), 0x29FFFFFF);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused, android.R.attr.state_selected}, fsel);
        sl.addState(new int[]{android.R.attr.state_focused}, fo);
        sl.addState(new int[]{android.R.attr.state_selected}, sel);
        sl.addState(new int[]{}, n);
        return sl;
    }

    private void tabSet(TextView t, boolean on) {
        t.setSelected(on);
        t.setTextColor(on ? K_INK : K_TEXT);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : tfMedium);
    }

    // Раздел (Филми или Сериали): избраният е кехлибарен.
    private TextView kTab(String label, boolean on) {
        TextView t = text(label, 20, K_TEXT, tfMedium, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(u(28), 0, u(28), 0);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(tabBg());
        t.setTag(R_SCALE, 1.06f);
        t.setOnFocusChangeListener(kFocus);
        tabSet(t, on);
        return t;
    }

    // Кехлибарена значка с оценката.
    private View ratingChip(double r) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(u(9), 0, u(11), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(K_AMBER);
        g.setCornerRadius(u(9));
        b.setBackground(g);
        LinearLayout.LayoutParams ilp = lp(u(16), u(16));
        ilp.rightMargin = u(6);
        b.addView(new IconView(this, IC_STAR, K_INK), ilp);
        b.addView(text(String.format(Locale.US, "%.1f", r), 17, K_INK, Typeface.DEFAULT_BOLD, true));
        return b;
    }

    // Малък надпис с контур (Филм, Сериал).
    private TextView kTag(String label) {
        TextView t = text(label, 16, K_TEXT2, tfMedium, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(u(12), 0, u(12), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x00000000);
        g.setCornerRadius(u(15));
        g.setStroke(Math.max(1, u(1)), 0x47FFFFFF);
        t.setBackground(g);
        return t;
    }

    // Жанр като малка значка (само за гледане).
    private TextView kChip(String label) {
        TextView t = text(label, 17, K_TEXT2, tfMedium, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(u(15), 0, u(15), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x12FFFFFF);
        g.setCornerRadius(u(17));
        g.setStroke(Math.max(1, u(1)), 0x29FFFFFF);
        t.setBackground(g);
        return t;
    }

    private View genreChips(String genres) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        String[] parts = genres.split(", ");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].trim().isEmpty()) continue;
            LinearLayout.LayoutParams l = lp(WC, u(34));
            if (i > 0) l.leftMargin = u(10);
            r.addView(kChip(parts[i].trim()), l);
        }
        return r;
    }

    private void addFact(LinearLayout r, String s) {
        LinearLayout.LayoutParams l = lp(WC, WC);
        l.leftMargin = u(14);
        r.addView(text(s, 19, K_TEXT, tfMedium, true), l);
    }

    // Оценка, година, времетраене и вид (Филм или Сериал).
    private void fillFacts(LinearLayout r, CTitle t) {
        r.removeAllViews();
        if (t.rating > 0) r.addView(ratingChip(t.rating), lp(WC, u(30)));
        if (!t.year.isEmpty()) addFact(r, t.year);
        String rt = fmtRuntime(t.runtime);
        if (!rt.isEmpty()) addFact(r, rt);
        LinearLayout.LayoutParams l = lp(WC, u(30));
        l.leftMargin = u(14);
        r.addView(kTag(t.tv ? "Сериал" : "Филм"), l);
    }

    private LinearLayout factsRow(CTitle t) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setClipChildren(false);
        fillFacts(r, t);
        return r;
    }

    private View brandMark(int size) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{K_AMBER2, K_EMBER});
        g.setCornerRadius(u(size * 0.3f));
        f.setBackground(g);
        int s = u(size * 0.5f);
        f.addView(new IconView(this, IC_PLAY, K_INK),
                new FrameLayout.LayoutParams(s, s, Gravity.CENTER));
        return f;
    }

    // Заглавие на екран: знак, голямо заглавие, поясняващ ред и (по желание) бутони вдясно.
    private View pageHeader(String title, String sub, View right) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setClipChildren(false);
        h.setPadding(u(16), u(24), u(16), 0);
        LinearLayout.LayoutParams mlp = lp(u(46), u(46));
        mlp.rightMargin = u(16);
        h.addView(brandMark(46), mlp);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(serifText(title, 44, K_TEXT, true));
        if (sub != null && !sub.isEmpty()) tx.addView(text(sub, 19, K_TEXT2, tfMedium, true));
        h.addView(tx, new LinearLayout.LayoutParams(0, WC, 1f));
        if (right != null) h.addView(right);
        return h;
    }

    private View skeleton(int wUnits, int hUnits, int radiusUnits) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(K_SURF);
        g.setCornerRadius(u(radiusUnits));
        v.setBackground(g);
        v.setMinimumWidth(u(wUnits));
        v.setMinimumHeight(u(hUnits));
        return v;
    }

    // Празни плочки, докато идват данните (без прескачане на изгледа).
    private void fillSkeleton(LinearLayout strip, int n, int wUnits, int hUnits, int gapUnits) {
        for (int i = 0; i < n; i++) {
            LinearLayout.LayoutParams l = lp(u(wUnits), u(hUnits));
            l.rightMargin = u(gapUnits);
            strip.addView(skeleton(wUnits, hUnits, 14), l);
        }
    }

    // Грешка или празен резултат: какво се случи и бутон "Опитай пак".
    private View errorBlock(String msg, final Runnable retry) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(0, u(16), 0, u(16));
        TextView m = text(msg, 19, K_ERR, tfMedium, false);
        m.setMaxWidth(u(900));
        b.addView(m);
        b.addView(kPill(IC_REFRESH, "Опитай пак", retry), lpTopW(WC, u(52), u(14)));
        return b;
    }

    private View firstFocusable() {
        ArrayList<View> fs = new ArrayList<>();
        catBox.addFocusables(fs, View.FOCUS_DOWN);
        for (View v : fs) {
            if (v != null) return v;
        }
        return null;
    }

    private LinearLayout.LayoutParams lpTopW(int w, int h, int topPx) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        l.topMargin = topPx;
        return l;
    }

    private LinearLayout.LayoutParams lpTop(int topPx) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(MP, WC);
        l.topMargin = topPx;
        return l;
    }

    // ---- панел на каталога и горната картина ----

    private boolean catShown() {
        return catPanel != null && catPanel.getVisibility() == View.VISIBLE;
    }

    private boolean heroActive;
    private FrameLayout libraryBrowser;
    private GridView libraryGrid;
    private BaseAdapter libraryAdapter;
    private LinearLayout libraryMessage;
    private TextView libraryStatus;
    private View libraryRetry;
    private final ArrayList<CTitle> libraryItems=new ArrayList<>();
    private boolean librarySeries,libraryTopRated,libraryBusy,libraryDone;
    private int libraryPage,libraryRestorePages=1;

    private void buildCatalogUi() {
        tfSerif = Typeface.create("sans-serif", Typeface.BOLD);
        catPanel = new FrameLayout(this);
        catPanel.setBackground(TvTheme.background());
        catScroll = new CatScroll(this);
        catScroll.setFillViewport(true);
        catScroll.setVerticalScrollBarEnabled(false);
        catScroll.setClipChildren(false);
        catScroll.setClipToPadding(false);
        catBox = new LinearLayout(this);
        catBox.setOrientation(LinearLayout.VERTICAL);
        catBox.setClipChildren(false);
        catScroll.addView(catBox, new ViewGroup.LayoutParams(MP, WC));
        catPanel.addView(catScroll, new FrameLayout.LayoutParams(MP, MP));
        buildLibraryToolbar();
        libraryBrowser=new FrameLayout(this);FrameLayout.LayoutParams browserLp=new FrameLayout.LayoutParams(MP,MP);
        browserLp.leftMargin=u(54);browserLp.rightMargin=u(54);browserLp.topMargin=u(214);browserLp.bottomMargin=u(56);catPanel.addView(libraryBrowser,browserLp);
        catPanel.setVisibility(View.GONE);
        root.addView(catPanel, new FrameLayout.LayoutParams(MP, MP));
    }

    private void buildLibraryToolbar(){
        catToolbar=new LinearLayout(this);catToolbar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams l=new FrameLayout.LayoutParams(MP,u(64));l.leftMargin=u(54);l.rightMargin=u(54);l.topMargin=u(126);catPanel.addView(catToolbar,l);
    }
    private void libraryToolbar(){
        catToolbar.removeAllViews();catToolbar.addView(text("Видеотека",36,K_TEXT,Typeface.DEFAULT_BOLD,true),new LinearLayout.LayoutParams(0,WC,1));
        View movies=filterChip("Филми",!librarySeries,()->{librarySeries=false;catFocusKey=null;catHome();});catToolbar.addView(movies,lp(WC,u(46)));
        LinearLayout.LayoutParams tab=lp(WC,u(46));tab.leftMargin=u(10);catToolbar.addView(filterChip("Сериали",librarySeries,()->{librarySeries=true;catFocusKey=null;catHome();}),tab);
        LinearLayout.LayoutParams search=lp(WC,u(48));search.leftMargin=u(24);catToolbar.addView(kPill(IC_SEARCH,"Търси…",()->showSearchDialog()),search);
        LinearLayout.LayoutParams sources=lp(WC,u(48));sources.leftMargin=u(12);catToolbar.addView(kPill(IC_SLIDERS,"Източници",()->showSourcesDialog()),sources);
        LinearLayout.LayoutParams more=lp(WC,u(48));more.leftMargin=u(12);catToolbar.addView(kPill(-1,"⋯",()->libraryOptions()),more);
    }
    private void libraryOptions(){
        new AlertDialog.Builder(this).setTitle("Видеотека").setItems(new String[]{"Популярни","Най-добре оценени","Категории","Предложи ми","Сайтове за филми"},(d,i)->{
            if(i<2){libraryTopRated=i==1;catFocusKey=null;catHome();}else if(i==2)catGenres(false);else if(i==3)catGenres(true);else showManualSites(1);
        }).show();
    }
    private void libraryRelease(){
        if(libraryGrid!=null){libraryGrid.setOnFocusChangeListener(null);libraryGrid.setOnItemSelectedListener(null);libraryGrid.setAdapter(null);}libraryGrid=null;libraryAdapter=null;libraryItems.clear();libraryBusy=false;
        if(libraryBrowser!=null)libraryBrowser.removeAllViews();libraryMessage=null;libraryStatus=null;libraryRetry=null;
    }
    private void catMode(boolean home){
        catCancelRequests();
        FrameLayout.LayoutParams l=(FrameLayout.LayoutParams)catScroll.getLayoutParams();l.topMargin=u(126);l.bottomMargin=u(56);l.leftMargin=u(54);l.rightMargin=u(54);catScroll.setLayoutParams(l);
        catScroll.setVisibility(home?View.GONE:View.VISIBLE);catToolbar.setVisibility(home?View.VISIBLE:View.GONE);libraryBrowser.setVisibility(home?View.VISIBLE:View.GONE);
        if(!home)libraryRelease();heroActive=home;catBox.setPadding(0,u(8),0,u(40));updateState();
        ui.removeCallbacks(focusTick);focusAct=null;focusTitle=null;
    }
    private final class LibraryCell extends LinearLayout {
        final ArtView art;final TextView title,year;
        String boundKey;
        LibraryCell(){super(MainActivity.this);setOrientation(VERTICAL);setPadding(u(3),u(3),u(3),u(6));setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            art=new ArtView(MainActivity.this,0,"",u(12),true);art.setFocusable(false);art.setClickable(false);art.containImage=true;addView(art,lp(MP,u(345)));
            title=text("",21,K_TEXT,tfMedium,true);addView(title,lpTopW(MP,WC,u(10)));year=text("",17,K_TEXT2,tfMedium,true);addView(year,lpTopW(MP,WC,u(4)));
        }
    }
    private void libraryLoadPage(int gen){
        if(libraryBusy || libraryDone || gen!=catGen || !heroActive)return;libraryBusy=true;
        final int page=libraryPage+1;String path=(librarySeries?"/tv/":"/movie/")+(libraryTopRated?"top_rated":"popular")+"?language=bg-BG&page="+page;
        tmdbFetch(path,(json,error)->{
            if(gen!=catGen || !heroActive || libraryGrid==null)return;libraryBusy=false;
            ArrayList<CTitle> items=json==null?null:parseList(json,librarySeries,20);
            if(items==null || items.isEmpty()){
                libraryDone=error==null;if(libraryItems.isEmpty()){libraryStatus.setText(error!=null?error:"Няма заглавия в този списък.");libraryRetry.setVisibility(View.VISIBLE);catMain=libraryRetry;}
                else if(error!=null)Toast.makeText(this,error,Toast.LENGTH_SHORT).show();return;
            }
            libraryPage=page;libraryDone=page>=5 || items.size()<20;libraryItems.addAll(items);libraryAdapter.notifyDataSetChanged();catMain=libraryGrid;
            int restore=-1;for(int i=0;i<libraryItems.size();i++){CTitle t=libraryItems.get(i);if(("t"+t.id+(t.tv?"t":"m")).equals(catFocusKey))restore=i;}
            if(restore>=0){libraryGrid.setSelection(restore);libraryGrid.requestFocusFromTouch();catFocusKey=null;libraryRestorePages=1;}
            else if(catFocusKey!=null && page<libraryRestorePages && !libraryDone){libraryLoadPage(gen);return;}
            else if(page==1 && catPanel.findFocus()==null && (appBar==null || !appBar.hasFocus())){libraryGrid.setSelection(0);libraryGrid.requestFocusFromTouch();}
        });
    }

    // Показва каталога. first е екранът, с който да започне (по подразбиране началото).
    private void showCatalog(Runnable first) {
        if (isFinishing()) return;
        appSection = SECTION_LIBRARY;
        if (appBar != null) appBar.setSection(appSection);
        tvReset();
        closeNative(false);
        destroyWeb();
        if (catPanel == null) buildCatalogUi();
        menuHolder.setVisibility(View.GONE);
        // Докато каталогът е отгоре, менюто отдолу не взима фокус.
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        catPanel.setVisibility(View.VISIBLE);
        logEvent("Отворен каталог Наши филми");
        if (first != null) first.run();
        else catHome();
        updateState();
    }

    private void hideCatalog() {
        catGen++;
        catCancelRequests();
        catUp = null;
        ui.removeCallbacks(focusTick);
        focusAct = null;
        focusTitle = null;
        heroActive = false;
        if (catPanel != null) {
            catPanel.setVisibility(View.GONE);
            catBox.removeAllViews();
            libraryRelease();
        }
        catMain = null;
        synchronized (TMDB_JSON) { TMDB_JSON.clear(); TMDB_AT.clear(); }
        menuHolder.setVisibility(View.VISIBLE);
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        POSTERS.evictAll();
        BACKDROPS.evictAll();
        View f = catTile != null ? catTile : (heroView != null ? heroView : firstTile);
        if (f != null) f.requestFocus();
        updateState();
    }

    // ---- редове с корици ----

    // Нов ред със заглавие; връща лентата, в която се слагат корици.
    private LinearLayout addRowShell(String title) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setClipChildren(false);
        if (!title.isEmpty()) {
            TextView h = serifText(title, 28, K_TEXT, true);
            h.setPadding(u(16), 0, u(16), u(2));
            block.addView(h);
        }
        HorizontalScrollView hs = new RowScroll(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setPadding(u(16), u(14), u(16), u(20)); // място за уголемяването на фокуса
        hs.addView(strip, new ViewGroup.LayoutParams(WC, WC));
        block.addView(hs);
        catBox.addView(block, lpTop(u(title.isEmpty() ? 0 : 8)));
        return strip;
    }

    private LinearLayout.LayoutParams posterLp(int wUnits, int hUnits, int gapUnits) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(u(wUnits), u(hUnits));
        l.rightMargin = u(gapUnits);
        return l;
    }

    // Избираема корица (на нея е фокусът).
    private ArtView posterCard(final CTitle t, String size, final Runnable onClick) {
        final ArtView a = new ArtView(this, t.id, t.name, u(14), true);
        a.setRating(t.rating);
        a.setTag("t" + t.id + (t.tv ? "t" : "m"));
        a.setTag(R_TITLE, t);
        a.setTag(R_SCALE, 1.08f);
        loadImage(size, t.poster, catGen, false, new ImageSink() {
            @Override
            public void set(Bitmap b, boolean instant) {
                a.setBitmap(b, !instant);
            }
        });
        a.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                catFocusKey = (String) a.getTag();
                onClick.run();
            }
        });
        a.setOnFocusChangeListener(cellFocus);
        return a;
    }

    // Когато фокусът е близо до края на вече заредените корици, се зарежда следващата страница.
    private void watchEnd(View card, final int idx, final int[] count, final int ahead,
                          final Runnable more) {
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                cellFocus.onFocusChange(v, has);
                if (has && idx >= count[0] - ahead) more.run();
            }
        });
    }

    private void catHome(){
        final int gen=++catGen;
        catUp=()->{if(appBar!=null && !appBar.hasFocus())focusAppNavigation();else showSection(SECTION_HOME);};catParent=null;catBox.removeAllViews();libraryRelease();catMode(true);libraryToolbar();
        libraryPage=0;libraryDone=false;libraryGrid=new GridView(this);libraryGrid.setNumColumns(6);libraryGrid.setHorizontalSpacing(u(22));libraryGrid.setVerticalSpacing(u(26));libraryGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        libraryGrid.setPadding(0,u(3),0,u(12));libraryGrid.setClipToPadding(false);libraryGrid.setVerticalScrollBarEnabled(false);libraryGrid.setSelector(new ColorDrawable(Color.TRANSPARENT));libraryGrid.setFocusable(true);libraryGrid.setFocusableInTouchMode(true);
        libraryAdapter=new BaseAdapter(){
            @Override public int getCount(){return libraryItems.size();}
            @Override public Object getItem(int position){return libraryItems.get(position);}
            @Override public long getItemId(int position){return libraryItems.get(position).id;}
            @Override public View getView(int position,View convert,ViewGroup parent){
                LibraryCell cell=convert instanceof LibraryCell?(LibraryCell)convert:new LibraryCell();CTitle t=libraryItems.get(position);
                String key="t"+t.id+(t.tv?"t":"m");cell.setTag(key);cell.setContentDescription(t.name+", "+t.year);cell.title.setText(t.name);cell.year.setText(t.year);cell.art.setRating(t.rating);cell.art.setActivated(position==libraryGrid.getSelectedItemPosition() && libraryGrid.hasFocus());
                if(!key.equals(cell.boundKey)){cell.boundKey=key;cell.art.setBitmap(null,false);loadImage("w342",t.poster,gen,false,(bitmap,instant)->{if(key.equals(cell.boundKey))cell.art.setBitmap(bitmap,!instant);});}return cell;
            }
        };
        libraryGrid.setAdapter(libraryAdapter);libraryBrowser.addView(libraryGrid,new FrameLayout.LayoutParams(MP,MP));
        libraryMessage=new LinearLayout(this);libraryMessage.setOrientation(LinearLayout.VERTICAL);libraryMessage.setGravity(Gravity.CENTER);
        libraryStatus=text("Зареждане на видеотеката…",23,K_TEXT2,tfMedium,false);libraryStatus.setGravity(Gravity.CENTER);libraryMessage.addView(libraryStatus);
        libraryRetry=kPill(IC_REFRESH,"Опитай пак",()->{libraryDone=false;libraryLoadPage(gen);});libraryRetry.setVisibility(View.GONE);libraryMessage.addView(libraryRetry,lpTopW(WC,u(52),u(24)));
        libraryBrowser.addView(libraryMessage,new FrameLayout.LayoutParams(MP,MP));libraryGrid.setEmptyView(libraryMessage);
        libraryGrid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){@Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){if(gen!=catGen || parent!=libraryGrid)return;libraryUpdateFocus();if(position>=libraryItems.size()-6)libraryLoadPage(gen);}@Override public void onNothingSelected(AdapterView<?> parent){}});
        libraryGrid.setOnFocusChangeListener((v,focus)->libraryUpdateFocus());
        libraryGrid.setOnItemClickListener((parent,view,position,id)->{CTitle t=libraryItems.get(position);catFocusKey="t"+t.id+(t.tv?"t":"m");libraryRestorePages=libraryPage;catDetail(t);});
        libraryGrid.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_DPAD_UP && libraryGrid.getSelectedItemPosition()<6){if(event.getAction()==KeyEvent.ACTION_DOWN)catToolbar.getChildAt(1).requestFocusFromTouch();return true;}return false;});
        catMain=libraryGrid;
        if(tmdbKey().isEmpty()){libraryStatus.setText("Въведи ключ за TMDB от Източници, за да заредиш видеотеката.");libraryRetry.setVisibility(View.VISIBLE);libraryRetry.setOnClickListener(v->showSourcesDialog());catMain=libraryRetry;}else libraryLoadPage(gen);
    }

    private void libraryUpdateFocus(){
        if(libraryGrid==null)return;
        int first=libraryGrid.getFirstVisiblePosition(),selected=libraryGrid.getSelectedItemPosition();
        for(int i=0;i<libraryGrid.getChildCount();i++){View child=libraryGrid.getChildAt(i);if(child instanceof LibraryCell){((LibraryCell)child).art.setActivated(libraryGrid.hasFocus() && first+i==selected);child.invalidate();}}
    }

    // ---- страница на филм или сериал ----

    private Runnable backTo(final Runnable screen) {
        return new Runnable() {
            @Override
            public void run() {
                showCatalog(screen);
            }
        };
    }

    private void loadBackdropInto(final BackdropView bd, CTitle t, int gen) {
        if (t.backdrop.isEmpty()) return;
        loadImage(backdropSize(), t.backdrop, gen, true, new ImageSink() {
            @Override
            public void set(Bitmap b, boolean instant) {
                bd.setImage(b, !instant);
            }
        });
    }

    private void catDetail(final CTitle t) {
        final int gen = ++catGen;
        catUp = catParent != null ? catParent : new Runnable() {
            @Override
            public void run() {
                catHome();
            }
        };
        catBox.removeAllViews();
        catMain = null;
        catMode(false);

        final FrameLayout page = new FrameLayout(this);
        page.setClipChildren(false);
        final BackdropView bd = new BackdropView(this);
        bd.setTint(TvTheme.BLUE);
        page.addView(bd, new FrameLayout.LayoutParams(u(1320), u(660), Gravity.TOP | Gravity.END));
        page.addView(new ScrimView(this, false,
                new int[]{0xFF0D1119, 0xFF0D1119, 0xC70D1119, 0x400D1119, 0x1A0D1119},
                new float[]{0f, 0.26f, 0.52f, 0.82f, 1f}), new FrameLayout.LayoutParams(MP, u(660)));
        page.addView(new ScrimView(this, true,
                new int[]{0x000D1119, 0x000D1119, 0xE60D1119, 0xFF0D1119},
                new float[]{0f, 0.38f, 0.82f, 1f}), new FrameLayout.LayoutParams(MP, u(660)));
        page.addView(new ScrimView(this, true,
                new int[]{0x990D1119, 0x000D1119, 0x000D1119},
                new float[]{0f, 0.22f, 1f}), new FrameLayout.LayoutParams(MP, u(660)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(u(16), u(88), u(16), u(56));
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setClipChildren(false);
        final ArtView poster = new ArtView(this, t.id, t.name, u(16), false);
        poster.setElevation(u(10));
        loadImage("w342", t.poster, gen, false, new ImageSink() {
            @Override
            public void set(Bitmap b, boolean instant) {
                poster.setBitmap(b, !instant);
            }
        });
        top.addView(poster, lp(u(252), u(378)));
        final LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setClipChildren(false);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, WC, 1f);
        ilp.leftMargin = u(48);
        top.addView(info, ilp);
        content.addView(top, new LinearLayout.LayoutParams(MP, WC));
        final LinearLayout band = new LinearLayout(this);
        band.setOrientation(LinearLayout.VERTICAL);
        band.setClipChildren(false);
        content.addView(band, lpTop(u(34)));
        page.addView(content, new FrameLayout.LayoutParams(MP, WC));
        catBox.addView(page, new LinearLayout.LayoutParams(MP, WC));
        catScroll.scrollTo(0, 0);

        loadBackdropInto(bd, t, gen);
        fillInfo(t, info, false);
        band.addView(text("Зареждане...", 18, K_TEXT3, tfMedium, false));
        String path = (t.tv ? "/tv/" : "/movie/") + t.id + "?language=bg-BG"
                + (t.tv ? "&append_to_response=external_ids" : "");
        tmdbFetch(path, new Done() {
            @Override
            public void run(String json, String err) {
                if (gen != catGen) return;
                band.removeAllViews();
                if (json == null) {
                    View eb = errorBlock(err == null ? "Грешка при зареждане" : err, new Runnable() {
                        @Override
                        public void run() {
                            catDetail(t);
                        }
                    });
                    band.addView(eb);
                    ((ViewGroup) eb).getChildAt(1).requestFocus();
                    return;
                }
                parseDetail(json, t);
                loadBackdropInto(bd, t, gen);
                fillInfo(t, info, true);
                fillBand(t, band);
                if (catMain != null) catMain.requestFocus();
                loadSimilar(t, gen, "recommendations");
            }
        });
    }

    // Ред "Подобни" под страницата; ако няма препоръки, пробва "similar".
    private void loadSimilar(final CTitle t, final int gen, final String kind) {
        tmdbFetch((t.tv ? "/tv/" : "/movie/") + t.id + "/" + kind + "?language=bg-BG&page=1",
                new Done() {
                    @Override
                    public void run(String json, String err) {
                        if (gen != catGen) return;
                        ArrayList<CTitle> list = json == null ? null : parseList(json, t.tv, 20);
                        if (list == null || list.isEmpty()) {
                            if (kind.equals("recommendations") && err == null) {
                                loadSimilar(t, gen, "similar");
                            }
                            return;
                        }
                        final Runnable up = catUp;
                        LinearLayout strip = addRowShell(t.tv ? "Подобни сериали" : "Подобни филми");
                        for (int i = 0; i < list.size(); i++) {
                            final CTitle s = list.get(i);
                            strip.addView(posterCard(s, rowSize(), new Runnable() {
                                @Override
                                public void run() {
                                    catParent = new Runnable() {
                                        @Override
                                        public void run() {
                                            catParent = up;
                                            catDetail(t);
                                        }
                                    };
                                    catDetail(s);
                                }
                            }), posterLp(168, 252, 16));
                        }
                    }
                });
    }

    // Дясната колона: данни, заглавие, жанрове, описание (и за сериал: бутон и сезони).
    private void fillInfo(CTitle t, LinearLayout info, boolean loaded) {
        info.removeAllViews();
        catMain = null;
        info.addView(factsRow(t), lp(WC, u(30)));
        int n = t.name.length();
        TextView title = serifText(t.name, n <= 24 ? 58 : (n <= 36 ? 48 : 40), K_TEXT, false);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(title, lpTop(u(14)));
        if (!t.genres.isEmpty()) info.addView(genreChips(t.genres), lpTopW(WC, u(34), u(14)));
        boolean noOv = t.overview.isEmpty();
        TextView ov = text(noOv ? "Няма описание на български." : t.overview, 20,
                noOv ? K_TEXT3 : K_TEXT2, tfMedium, false);
        ov.setMaxLines(t.tv ? 3 : 4);
        ov.setEllipsize(TextUtils.TruncateAt.END);
        ov.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(MP, WC);
        olp.topMargin = u(16);
        info.addView(ov, olp);
        if (loaded && t.tv) seriesActions(t, info);
    }

    // Сериал: "Продължи" или "Гледай от началото", и сезоните.
    private void seriesActions(final CTitle t, LinearLayout info) {
        int s0 = t.seasons.isEmpty() ? 1 : t.seasons.get(0)[0];
        int ls = s0;
        int le = 1;
        boolean resume = false;
        String last = prefs.getString("tvlast:" + t.id, "");
        int ci = last.indexOf(':');
        if (ci > 0) {
            int a = (int) parseLong(last.substring(0, ci));
            int b = (int) parseLong(last.substring(ci + 1));
            if (a > 0 && b > 0) {
                ls = a;
                le = b;
                resume = true;
            }
        }
        final int fs = ls;
        final int fe = le;
        LinearLayout primary;
        if (sourceCount(true) > 0) {
            primary = kPrimary(IC_PLAY, resume ? "Продължи: сезон " + ls + ", епизод " + le
                    : "Гледай от началото", new Runnable() {
                @Override
                public void run() {
                    catPlay(t, fs, fe, backTo(new Runnable() {
                        @Override
                        public void run() {
                            catEpisodes(t, fs);
                        }
                    }));
                }
            });
        } else {
            primary = kPrimary(IC_SLIDERS, "Добави източник", new Runnable() {
                @Override
                public void run() {
                    showSourcesDialog();
                }
            });
        }
        catMain = primary;
        info.addView(primary, lpTopW(WC, u(72), u(26)));
        if (t.seasons.isEmpty()) {
            TextView none = text("Няма данни за сезоните.", 17, K_TEXT3, tfMedium, true);
            info.addView(none, lpTop(u(18)));
        } else {
            info.addView(seasonRow(t, resume ? ls : s0, 0), lpTop(u(14)));
        }
    }

    // Сезоните като хапчета; текущият е с кехлибарен контур.
    private View seasonRow(final CTitle t, int current, int padLeftUnits) {
        HorizontalScrollView hs = new RowScroll(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(u(padLeftUnits), u(8), u(16), u(10));
        for (int i = 0; i < t.seasons.size(); i++) {
            final int sn = t.seasons.get(i)[0];
            int ec = t.seasons.get(i)[1];
            LinearLayout b = new LinearLayout(this);
            b.setOrientation(LinearLayout.HORIZONTAL);
            b.setGravity(Gravity.CENTER_VERTICAL);
            b.setPadding(u(22), 0, u(22), 0);
            b.setFocusable(true);
            b.setClickable(true);
            boolean cur = sn == current;
            b.setBackground(stateBg(0x14FFFFFF, K_SURF2, K_AMBER, u(26),
                    cur ? K_AMBER : 0x29FFFFFF));
            b.addView(text("Сезон " + sn, 19, cur ? K_AMBER2 : K_TEXT, Typeface.DEFAULT_BOLD, true));
            if (ec > 0) {
                TextView e = text(ec + (ec == 1 ? " епизод" : " епизода"), 16, K_TEXT3, tfMedium,
                        true);
                e.setPadding(u(10), 0, 0, 0);
                b.addView(e);
            }
            b.setTag("sea" + sn);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    catEpisodes(t, sn);
                }
            });
            b.setTag(R_SCALE, 1.05f);
            b.setOnFocusChangeListener(kFocus);
            LinearLayout.LayoutParams l = lp(WC, u(52));
            if (i > 0) l.leftMargin = u(12);
            row.addView(b, l);
        }
        hs.addView(row, new ViewGroup.LayoutParams(WC, WC));
        return hs;
    }

    // Билетчета: източниците един до друг. При филм (play) натискането пуска филма от този
    // източник; при сериал само избира откъде да започват епизодите.
    private LinearLayout ticketRow(final CTitle t, int wUnits, int hUnits, int gapUnits,
                                   final boolean play) {
        final int[] slots = srcSlots(t.tv);
        final TicketView[] tix = new TicketView[slots.length];
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setPadding(0, u(10), u(6), u(14)); // място за уголемяването на фокуса
        int pref = srcPref(t.tv);
        View prefView = null;
        for (int i = 0; i < slots.length; i++) {
            final int slot = slots[i];
            TicketView tv = new TicketView(this, i + 1, srcHost(t.tv, slot));
            tv.setPreferred(slot == pref, slot == pref ? "Избран" : "Резервен");
            tv.setTag("src" + slot);
            tv.setTag(R_SCALE, 1.06f);
            tv.setOnFocusChangeListener(kFocus);
            tix[i] = tv;
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    srcSetPref(t.tv, slot);
                    for (int k = 0; k < tix.length; k++) {
                        boolean on = slots[k] == slot;
                        tix[k].setPreferred(on, on ? "Избран" : "Резервен");
                    }
                    if (play) {
                        catSrcKey = "src" + slot;
                        catPlay(t, 1, 1, backTo(new Runnable() {
                            @Override
                            public void run() {
                                catDetail(t);
                            }
                        }));
                    }
                }
            });
            LinearLayout.LayoutParams l = lp(u(wUnits), u(hUnits));
            if (i > 0) l.leftMargin = u(gapUnits);
            row.addView(tv, l);
            if (slot == pref) prefView = tv;
        }
        if (play) {
            View want = catSrcKey != null ? findByTag(row, catSrcKey) : null;
            catSrcKey = null;
            catMain = want != null ? want : prefView;
        }
        return row;
    }

    // Билетчетата в лента, която се върти настрани, когато са много.
    private View tickScroll(LinearLayout row) {
        HorizontalScrollView hs = new RowScroll(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.addView(row, new ViewGroup.LayoutParams(WC, WC));
        return hs;
    }

    // Нищо за пускане: покана да се добави източник.
    private View noSourceBlock(boolean tv) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setClipChildren(false);
        b.addView(serifText("Още няма източник", 26, K_TEXT, true));
        TextView d = text("Добави адрес на сайт и " + (tv ? "сериалът" : "филмът")
                + " ще може да се пусне.", 18, K_TEXT2, tfMedium, false);
        d.setPadding(0, u(6), 0, 0);
        b.addView(d);
        LinearLayout add = kPrimary(IC_SLIDERS, "Добави източник", new Runnable() {
            @Override
            public void run() {
                showSourcesDialog();
            }
        });
        catMain = add;
        b.addView(add, lpTopW(WC, u(72), u(16)));
        return b;
    }

    // Долната лента: за филм са билетчетата (или бутон), за сериал изборът на източник.
    private void fillBand(final CTitle t, LinearLayout band) {
        band.removeAllViews();
        int[] slots = srcSlots(t.tv);
        if (!t.tv) {
            if (slots.length == 0) {
                band.addView(noSourceBlock(false));
            } else if (slots.length == 1) {
                final int slot = slots[0];
                LinearLayout play = kPrimary(IC_PLAY, "Гледай", new Runnable() {
                    @Override
                    public void run() {
                        srcSetPref(false, slot);
                        catPlay(t, 1, 1, backTo(new Runnable() {
                            @Override
                            public void run() {
                                catDetail(t);
                            }
                        }));
                    }
                });
                catMain = play;
                band.addView(play, lpTopW(WC, u(72), 0));
            } else {
                band.addView(text("Гледай от", 19, K_TEXT2, tfMedium, true));
                band.addView(tickScroll(ticketRow(t, 232, 84, 16, true)), lpTop(u(4)));
            }
        } else if (slots.length >= 2) {
            band.addView(text("Източник за епизодите", 19, K_TEXT2, tfMedium, true));
            band.addView(tickScroll(ticketRow(t, 232, 84, 16, false)), lpTop(u(4)));
        }
    }

    // ---- епизоди ----

    private void catEpisodes(final CTitle t, final int season) {
        final int gen = ++catGen;
        catUp = new Runnable() {
            @Override
            public void run() {
                catDetail(t);
            }
        };
        catBox.removeAllViews();
        catMain = null;
        catMode(false);
        catBox.addView(pageHeader(t.name, "Сезон " + season, null));
        if (!t.seasons.isEmpty()) catBox.addView(seasonRow(t, season, 64), lpTop(u(16)));
        if (sourceCount(true) >= 2) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setClipChildren(false);
            line.setPadding(u(16), 0, u(16), 0);
            TextView lab = text("Източник", 18, K_TEXT2, tfMedium, true);
            line.addView(lab, lp(u(104), WC));
            line.addView(tickScroll(ticketRow(t, 196, 64, 12, false)),
                    new LinearLayout.LayoutParams(0, WC, 1f));
            catBox.addView(line, lpTop(u(2)));
        }
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setPadding(u(16), u(14), u(16), 0);
        for (int i = 0; i < 3; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, u(132), 1f);
                if (k == 0) l.rightMargin = u(20);
                r.addView(skeleton(300, 132, 18), l);
            }
            list.addView(r, lpTop(u(i == 0 ? 0 : 16)));
        }
        catBox.addView(list, lpTop(0));
        catScroll.scrollTo(0, 0);
        tmdbFetch("/tv/" + t.id + "/season/" + season + "?language=bg-BG", new Done() {
            @Override
            public void run(String json, String err) {
                if (gen != catGen) return;
                list.removeAllViews();
                ArrayList<CEpisode> eps = json == null ? null : parseEpisodes(json);
                if (eps == null || eps.isEmpty()) {
                    View eb = errorBlock(err != null ? err : "Няма епизоди.", new Runnable() {
                        @Override
                        public void run() {
                            catEpisodes(t, season);
                        }
                    });
                    list.addView(eb);
                    ((ViewGroup) eb).getChildAt(1).requestFocus();
                    return;
                }
                String lastKey = prefs.getString("tvlast:" + t.id, "");
                View focus = null;
                LinearLayout row = null;
                for (int i = 0; i < eps.size(); i++) {
                    CEpisode ep = eps.get(i);
                    boolean last = lastKey.equals(season + ":" + ep.num);
                    View card = epCard(t, ep, season, last, gen);
                    if (i % 2 == 0) {
                        row = new LinearLayout(MainActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setClipChildren(false);
                        list.addView(row, lpTop(u(i == 0 ? 0 : 16)));
                    }
                    LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, u(132), 1f);
                    if (i % 2 == 0) l.rightMargin = u(20);
                    row.addView(card, l);
                    if (focus == null) focus = card;
                    if (last) focus = card;
                }
                if (eps.size() % 2 == 1 && row != null) {
                    row.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(0, u(132), 1f));
                }
                if (focus != null) focus.requestFocus();
            }
        });
    }

    // Епизод: снимка с номер, заглавие, дата, времетраене и кратко описание.
    private View epCard(final CTitle t, final CEpisode ep, final int season, boolean last, int gen) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(u(9), u(9), u(18), u(9));
        c.setFocusable(true);
        c.setClickable(true);
        c.setBackground(stateBg(K_SURF, K_SURF2, K_AMBER, u(18), K_LINE));
        final ArtView still = new ArtView(this, ep.num * 7 + season, ep.name.isEmpty() ? "Е" : ep.name,
                u(11), false);
        still.setNumber(String.valueOf(ep.num));
        still.setPlay(true);
        loadImage("w300", ep.still, gen, false, new ImageSink() {
            @Override
            public void set(Bitmap b, boolean instant) {
                still.setBitmap(b, !instant);
            }
        });
        c.addView(still, lp(u(200), u(112)));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setGravity(Gravity.CENTER_VERTICAL);
        tx.setPadding(u(16), 0, 0, 0);
        tx.addView(text(ep.name.isEmpty() ? "Епизод " + ep.num : ep.name, 21, K_TEXT, tfMedium,
                true));
        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        String date = bgDate(ep.date);
        if (!date.isEmpty()) meta.addView(text(date, 16, K_TEXT3, tfMedium, true));
        if (ep.runtime > 0) {
            TextView rt = text(ep.runtime + " мин", 15, K_TEXT2, tfMedium, true);
            rt.setGravity(Gravity.CENTER);
            rt.setPadding(u(9), 0, u(9), 0);
            GradientDrawable g = new GradientDrawable();
            g.setColor(0x00000000);
            g.setCornerRadius(u(11));
            g.setStroke(Math.max(1, u(1)), 0x33FFFFFF);
            rt.setBackground(g);
            LinearLayout.LayoutParams l = lp(WC, u(22));
            l.leftMargin = date.isEmpty() ? 0 : u(10);
            meta.addView(rt, l);
        }
        if (last) {
            LinearLayout.LayoutParams l = lp(WC, WC);
            l.leftMargin = u(10);
            meta.addView(text("Последно гледан", 15, K_AMBER, Typeface.DEFAULT_BOLD, true), l);
        }
        tx.addView(meta, lpTop(u(3)));
        if (!ep.overview.isEmpty()) {
            TextView ov = text(ep.overview, 16, K_TEXT2, tfMedium, false);
            ov.setMaxLines(2);
            ov.setEllipsize(TextUtils.TruncateAt.END);
            tx.addView(ov, lpTop(u(4)));
        }
        c.addView(tx, new LinearLayout.LayoutParams(0, WC, 1f));
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                catPlay(t, season, ep.num, backTo(new Runnable() {
                    @Override
                    public void run() {
                        catEpisodes(t, season);
                    }
                }));
            }
        });
        c.setTag(R_SCALE, 1.025f);
        c.setOnFocusChangeListener(kFocus);
        return c;
    }

        // Пуска избраното: отваря първия източник; при грешка се минава на резервния.
    private void catPlay(CTitle t, int season, int episode, Runnable back) {
        ArrayList<String> urls = sourceUrls(t, season, episode);
        if (urls.isEmpty()) {
            Toast.makeText(this, "Няма настроен източник. Добави го от бутона Източници.",
                    Toast.LENGTH_LONG).show();
            showSourcesDialog();
            return;
        }
        if (t.tv) prefs.edit().putString("tvlast:" + t.id, season + ":" + episode).apply();
        String label = t.tv ? t.name + " (С" + season + " Е" + episode + ")" : t.name;
        // Филмът се търси на заден план през скритата страница и тръгва в родния плеър.
        if (vodStart(t, season, episode, back)) return;
        hideCatalog();
        openSite(new String[]{label, urls.get(0), "", "Наши филми"});
        srcQueue = urls;
        srcIdx = 0;
        catReturn = back;
        srcArm();
    }

    // Грешка при зареждане: опитва следващия източник, ако има такъв.
    private final Runnable srcTimeout = new Runnable() {
        @Override
        public void run() {
            tryNextSource(); // източникът не се е отворил навреме
        }
    };

    private void srcArm() {
        ui.removeCallbacks(srcTimeout);
        if (srcQueue != null && srcIdx + 1 < srcQueue.size()) ui.postDelayed(srcTimeout, 25000);
    }

    private void srcDone() {
        ui.removeCallbacks(srcTimeout);
    }

    private boolean tryNextSource() {
        ui.removeCallbacks(srcTimeout);
        if (srcQueue == null || web == null || srcIdx + 1 >= srcQueue.size()) return false;
        srcIdx++;
        String next = srcQueue.get(srcIdx);
        allowedHost = Uri.parse(next).getHost();
        pageUrl = null;
        Toast.makeText(this, "Опитвам резервен източник " + (srcIdx + 1), Toast.LENGTH_SHORT).show();
        logEvent("Резервен източник " + (srcIdx + 1) + ": " + hostOf(next));
        web.loadUrl(next);
        srcArm();
        return true;
    }

    // ---- категории, предложения, търсене ----

        private static final int[] MOVIE_GENRE_ID = {28, 12, 16, 35, 80, 99, 18, 10751, 14, 36, 27,
            10402, 9648, 10749, 878, 53, 10752, 37};
    private static final String[] MOVIE_GENRE_NAME = {"Екшън", "Приключенски", "Анимация",
            "Комедия", "Криминален", "Документален", "Драма", "Семеен", "Фентъзи", "Исторически",
            "Ужаси", "Музикален", "Мистерия", "Романтичен", "Фантастика", "Трилър", "Военен",
            "Уестърн"};
    private static final int[] TV_GENRE_ID = {10759, 16, 35, 80, 99, 18, 10751, 10762, 9648,
            10765, 10768, 37};
    private static final String[] TV_GENRE_NAME = {"Екшън и приключения", "Анимация", "Комедия",
            "Криминални", "Документални", "Драма", "Семейни", "Детски", "Мистерия",
            "Фантастика и фентъзи", "Война и политика", "Уестърн"};

    private boolean catGenreTv; // кой раздел е избран в категориите (филми или сериали)

    private void fillGenreGrid(LinearLayout grid, final boolean tv, final boolean suggest) {
        grid.removeAllViews();
        int[] ids = tv ? TV_GENRE_ID : MOVIE_GENRE_ID;
        String[] names = tv ? TV_GENRE_NAME : MOVIE_GENRE_NAME;
        int n = ids.length + (suggest ? 1 : 0);
        LinearLayout row = null;
        boolean firstCard = true;
        for (int i = 0; i < n; i++) {
            if (i % 5 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setClipChildren(false);
                grid.addView(row, lpTop(u(i == 0 ? 0 : 18)));
            }
            final int gid = suggest ? (i == 0 ? 0 : ids[i - 1]) : ids[i];
            final String gname = suggest ? (i == 0 ? "Всякакви" : names[i - 1]) : names[i];
            GenreView g = new GenreView(this, gname, gid);
            g.setTag(R_SCALE, 1.07f);
            g.setOnFocusChangeListener(kFocus);
            g.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    catFocusKey = null;
                    if (suggest) catSuggest(tv, gid, gname, true);
                    else catGenre(tv, gid, gname);
                }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, u(118), 1f);
            if (i % 5 != 4) l.rightMargin = u(18);
            row.addView(g, l);
            if (firstCard) {
                catMain = g;
                firstCard = false;
            }
        }
        // последният ред се допълва с празни места, за да са плочките с един и същи размер
        int rest = (5 - n % 5) % 5;
        for (int k = 0; k < rest && row != null; k++) {
            int col = n % 5 + k;
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, u(118), 1f);
            if (col < 4) l.rightMargin = u(18);
            row.addView(new View(this), l);
        }
    }

    private void catGenres(final boolean suggest) {
        ++catGen;
        catUp = new Runnable() {
            @Override
            public void run() {
                catHome();
            }
        };
        catParent = null;
        catBox.removeAllViews();
        catMain = null;
        catMode(false);
        final LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setPadding(u(16), u(10), u(16), 0);
        final TextView tMov = kTab("Филми", !catGenreTv);
        final TextView tTv = kTab("Сериали", catGenreTv);
        tMov.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!catGenreTv) return;
                catGenreTv = false;
                tabSet(tMov, true);
                tabSet(tTv, false);
                fillGenreGrid(grid, false, suggest);
            }
        });
        tTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (catGenreTv) return;
                catGenreTv = true;
                tabSet(tMov, false);
                tabSet(tTv, true);
                fillGenreGrid(grid, true, suggest);
            }
        });
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setClipChildren(false);
        tabs.addView(tMov, lp(WC, u(52)));
        LinearLayout.LayoutParams tl = lp(WC, u(52));
        tl.leftMargin = u(10);
        tabs.addView(tTv, tl);
        catBox.addView(pageHeader(suggest ? "Предложи ми" : "Категории",
                suggest ? "Избери жанр и ще хвърля зара" : "Избери жанр", tabs));
        catBox.addView(grid, lpTop(u(20)));
        fillGenreGrid(grid, catGenreTv, suggest);
        catScroll.scrollTo(0, 0);
        if (catMain != null) catMain.requestFocus();
    }

    private ArrayList<CTitle> catPicks;
    private LinearLayout sgInfo;
    private LinearLayout sgFacts;
    private TextView sgTitle;
    private TextView sgOv;

    // Предложения: 5 случайни заглавия от избрания жанр; "Хвърли пак" дава други 5.
    private void catSuggest(final boolean tv, final int gid, final String gname,
                            final boolean roll) {
        final int gen = ++catGen;
        catUp = new Runnable() {
            @Override
            public void run() {
                catHome();
            }
        };
        catParent = new Runnable() {
            @Override
            public void run() {
                catSuggest(tv, gid, gname, false);
            }
        };
        catBox.removeAllViews();
        catMain = null;
        catMode(false);
        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.HORIZONTAL);
        acts.setClipChildren(false);
        LinearLayout dice = kPill(IC_DICE, "Хвърли пак", new Runnable() {
            @Override
            public void run() {
                catFocusKey = null;
                catSuggest(tv, gid, gname, true);
            }
        });
        LinearLayout other = kPill(IC_GRID, "Друг жанр", new Runnable() {
            @Override
            public void run() {
                catGenres(true);
            }
        });
        acts.addView(dice, lp(WC, u(52)));
        LinearLayout.LayoutParams ol = lp(WC, u(52));
        ol.leftMargin = u(12);
        acts.addView(other, ol);
        catBox.addView(pageHeader("Предложи ми", (tv ? "Сериали" : "Филми") + ": " + gname, acts));
        final LinearLayout strip = addRowShell("");
        fillSkeleton(strip, 5, 268, 402, 24);
        // данни за заглавието на фокус (без втора заявка: идват със списъка)
        sgInfo = new LinearLayout(this);
        sgInfo.setOrientation(LinearLayout.VERTICAL);
        sgInfo.setPadding(u(16), u(6), u(16), 0);
        sgFacts = new LinearLayout(this);
        sgFacts.setOrientation(LinearLayout.HORIZONTAL);
        sgFacts.setGravity(Gravity.CENTER_VERTICAL);
        sgInfo.addView(sgFacts, lp(WC, u(30)));
        sgTitle = serifText("", 36, K_TEXT, true);
        sgInfo.addView(sgTitle, lpTop(u(10)));
        sgOv = text("", 19, K_TEXT2, tfMedium, false);
        sgOv.setMaxLines(3);
        sgOv.setEllipsize(TextUtils.TruncateAt.END);
        sgOv.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams ovl = new LinearLayout.LayoutParams(u(1000), WC);
        ovl.topMargin = u(8);
        sgInfo.addView(sgOv, ovl);
        sgInfo.setVisibility(View.INVISIBLE);
        catBox.addView(sgInfo, lpTop(0));
        focusAct = new Runnable() {
            @Override
            public void run() {
                CTitle ft = focusTitle;
                if (ft == null) return;
                sgInfo.setVisibility(View.VISIBLE);
                fillFacts(sgFacts, ft);
                sgTitle.setText(ft.name);
                sgOv.setText(ft.overview);
            }
        };
        catScroll.scrollTo(0, 0);
        dice.requestFocus();
        if (!roll && catPicks != null) {
            showPicks(strip, catPicks);
            return;
        }
        String path = "/discover/" + (tv ? "tv" : "movie") + "?language=bg-BG"
                + "&sort_by=popularity.desc&vote_count.gte=200&page=" + (1 + new Random().nextInt(10))
                + (gid > 0 ? "&with_genres=" + gid : "");
        tmdbFetch(path, new Done() {
            @Override
            public void run(String json, String err) {
                if (gen != catGen) return;
                ArrayList<CTitle> list = json == null ? null : parseList(json, tv, 20);
                strip.removeAllViews();
                if (list == null || list.isEmpty()) {
                    strip.addView(errorBlock(err != null ? err : "Няма предложения.",
                            new Runnable() {
                                @Override
                                public void run() {
                                    catSuggest(tv, gid, gname, true);
                                }
                            }));
                    return;
                }
                Collections.shuffle(list);
                catPicks = new ArrayList<>(list.subList(0, Math.min(5, list.size())));
                showPicks(strip, catPicks);
            }
        });
    }

    private void showPicks(LinearLayout strip, ArrayList<CTitle> picks) {
        strip.removeAllViews();
        View first = null;
        View want = null;
        for (final CTitle t : picks) {
            ArtView a = posterCard(t, "w342", new Runnable() {
                @Override
                public void run() {
                    catDetail(t);
                }
            });
            strip.addView(a, posterLp(268, 402, 24));
            if (first == null) first = a;
            if (catFocusKey != null && catFocusKey.equals(a.getTag())) want = a;
        }
        catFocusKey = null;
        View f = want != null ? want : first;
        if (f != null) f.requestFocus();
    }

    private void catGenre(final boolean tv, int genreId, final String genreName) {
        String path = "/discover/" + (tv ? "tv" : "movie") + "?language=bg-BG&sort_by=popularity.desc"
                + "&vote_count.gte=50&with_genres=" + genreId;
        catGrid(genreName, tv ? "Сериали" : "Филми", path, tv, new Runnable() {
            @Override
            public void run() {
                catGenres(false);
            }
        });
    }

    // Екран с мрежа от корици (резултат от търсене или жанр). Дозарежда се при скрол надолу.
    private void catGrid(final String title, final String sub, final String path,
                         final boolean tv, final Runnable up) {
        final int gen = ++catGen;
        final Runnable self = new Runnable() {
            @Override
            public void run() {
                catGrid(title, sub, path, tv, up);
            }
        };
        catUp = up;
        catParent = self;
        catBox.removeAllViews();
        catMain = null;
        catMode(false);
        catBox.addView(pageHeader(title, sub, null));
        final LinearLayout skel = new LinearLayout(this);
        skel.setOrientation(LinearLayout.HORIZONTAL);
        skel.setPadding(u(16), u(24), u(16), 0);
        fillSkeleton(skel, 8, 168, 252, 16);
        catBox.addView(skel, lpTop(0));
        catScroll.scrollTo(0, 0);
        final int[] page = {0};
        final int[] count = {0};
        final boolean[] busy = {false};
        final boolean[] done = {false};
        final LinearLayout[] strip = {null};
        final Runnable[] more = new Runnable[1];
        more[0] = new Runnable() {
            @Override
            public void run() {
                if (busy[0] || done[0] || gen != catGen) return;
                busy[0] = true;
                final int p = ++page[0];
                tmdbFetch(path + "&page=" + p, new Done() {
                    @Override
                    public void run(String json, String err) {
                        busy[0] = false;
                        if (gen != catGen) return;
                        if (p == 1) catBox.removeView(skel);
                        ArrayList<CTitle> list = json == null ? null
                                : (path.startsWith("/search") ? parseMixed(json)
                                : parseList(json, tv, 20));
                        if (list == null || list.isEmpty()) {
                            if (p == 1 || err == null) done[0] = true;
                            else page[0]--;
                            if (p == 1) {
                                LinearLayout hold = new LinearLayout(MainActivity.this);
                                hold.setPadding(u(16), u(20), u(16), 0);
                                hold.addView(errorBlock(err != null ? err : "Няма резултати.",
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                self.run();
                                            }
                                        }));
                                catBox.addView(hold, lpTop(0));
                                if (err != null) {
                                    ((ViewGroup) hold.getChildAt(0)).getChildAt(1).requestFocus();
                                }
                            }
                            return;
                        }
                        if (p >= 8) done[0] = true; // най-много около 160 заглавия
                        View focus = null;
                        int base = count[0];
                        for (int i = 0; i < list.size(); i++) {
                            if ((base + i) % 8 == 0) strip[0] = addRowShell("");
                            final CTitle t = list.get(i);
                            ArtView cell = posterCard(t, rowSize(), new Runnable() {
                                @Override
                                public void run() {
                                    catDetail(t);
                                }
                            });
                            strip[0].addView(cell, posterLp(168, 252, 16));
                            watchEnd(cell, base + i, count, 16, more[0]);
                            String key = "t" + t.id + (t.tv ? "t" : "m");
                            if (p == 1) {
                                if (focus == null) focus = cell;
                                if (key.equals(catFocusKey)) focus = cell;
                            }
                        }
                        count[0] += list.size();
                        if (p == 1) {
                            catFocusKey = null;
                            if (focus != null) focus.requestFocus();
                        }
                    }
                });
            }
        };
        more[0].run();
    }


    // Бутон в прозорците: кехлибарен (основният) или тъмен.
    private View catButton(String label, boolean primary, final Runnable action) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(primary ? K_INK : K_TEXT);
        b.setTextSize(17);
        b.setTypeface(primary ? Typeface.DEFAULT_BOLD : tfMedium);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(14), dp(14), dp(14), dp(14));
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(primary ? amberBg(dp(26)) : stateBg(0x1AFFFFFF, K_SURF2, K_AMBER, dp(26)));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setOnFocusChangeListener(focusAnim);
        return b;
    }

    private void showSearchDialog() {
        if (isFinishing() || (srcDialog != null && srcDialog.isShowing())) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(22), dp(28), dp(22));
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{TvTheme.BACKGROUND_TOP,TvTheme.BACKGROUND});
        pbg.setCornerRadius(dp(24));
        pbg.setStroke(dp(1), 0x44FFFFFF);
        panel.setBackground(pbg);
        TextView title = new TextView(this);
        title.setText("Търсене");
        title.setTextColor(K_TEXT);
        title.setTextSize(26);
        title.setTypeface(tfSerif);
        panel.addView(title);
        final EditText et = srcField("Име на филм или сериал", "", false);
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(14);
        panel.addView(et, elp);

        final Dialog dlg = new Dialog(this);
        final Runnable go = new Runnable() {
            @Override
            public void run() {
                String q = et.getText().toString().trim();
                if (q.isEmpty()) return;
                dlg.dismiss();
                catFocusKey = null;
                String path;
                try {
                    path = "/search/multi?language=bg-BG&include_adult=false&query="
                            + java.net.URLEncoder.encode(q, "UTF-8");
                } catch (Exception e) {
                    return;
                }
                catGrid("Търсене: " + q, "Резултати", path, false, new Runnable() {
                    @Override
                    public void run() {
                        catHome();
                    }
                });
            }
        };
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                go.run();
                return true;
            }
        });
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.setMargins(dp(6), dp(14), dp(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.setMargins(dp(6), dp(14), dp(6), 0);
        btns.addView(catButton("Търси", true, go), l1);
        btns.addView(catButton("Отказ", false, new Runnable() {
            @Override
            public void run() {
                dlg.dismiss();
            }
        }), l2);
        panel.addView(btns);

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(panel);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(dp(720), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                srcDialog = null;
                hideSystemBars();
            }
        });
        srcDialog = dlg;
        dlg.show();
        et.requestFocus();
    }

    // ---- ключ и източници ----

    private EditText srcField(String hint, String value, boolean url) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextColor(K_TEXT);
        et.setHintTextColor(K_TEXT3);
        et.setTextSize(17);
        et.setPadding(dp(16), dp(10), dp(16), dp(10));
        et.setBackground(fieldBg());
        et.setInputType(InputType.TYPE_CLASS_TEXT | (url
                ? InputType.TYPE_TEXT_VARIATION_URI : InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        et.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        return et;
    }

    private void addLabeled(LinearLayout col, String label, EditText et) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(K_TEXT2);
        l.setTextSize(14);
        l.setPadding(dp(4), dp(12), 0, dp(4));
        col.addView(l);
        col.addView(et, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void showSourcesDialog() {
        if (isFinishing() || (srcDialog != null && srcDialog.isShowing())) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(22), dp(28), dp(22));
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{TvTheme.BACKGROUND_TOP,TvTheme.BACKGROUND});
        pbg.setCornerRadius(dp(24));
        pbg.setStroke(dp(1), 0x44FFFFFF);
        panel.setBackground(pbg);

        TextView title = new TextView(this);
        title.setText("Източници и ключове");
        title.setTextColor(K_TEXT);
        title.setTextSize(26);
        title.setTypeface(tfSerif);
        panel.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Ключ за TMDB и до десет адреса за филми и десет за сериали (основен и резервни). В адреса се слагат: "
                + "{tmdb} {imdb} {season} {episode}. Празно поле = не се ползва. "
                + "Пример: https://example.com/embed/movie/{tmdb}");
        sub.setTextColor(K_TEXT2);
        sub.setTextSize(14);
        sub.setPadding(0, dp(4), 0, dp(8));
        panel.addView(sub);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        final EditText key = srcField("Ключ за TMDB (остава само на тази кутия)",
                prefs.getString("tmdbKey", ""), false);
        addLabeled(col, "Ключ за TMDB", key);
        final EditText[] ff = new EditText[SRC_SLOTS];
        final EditText[] tf = new EditText[SRC_SLOTS];
        String[] names = {"основен", "резервен 1", "резервен 2", "резервен 3", "резервен 4",
                "резервен 5", "резервен 6", "резервен 7", "резервен 8", "резервен 9"};
        for (int i = 0; i < SRC_SLOTS; i++) {
            ff[i] = srcField("https://.../movie/{tmdb}", prefs.getString("fsrc" + i, ""), true);
            addLabeled(col, "Филми: " + names[i], ff[i]);
        }
        for (int i = 0; i < SRC_SLOTS; i++) {
            tf[i] = srcField("https://.../tv/{tmdb}/{season}/{episode}",
                    prefs.getString("tsrc" + i, ""), true);
            addLabeled(col, "Сериали: " + names[i], tf[i]);
        }
        TextView subHead = new TextView(this);
        subHead.setText("Субтитри (по желание). Subs.sab.bz работи без ключ; другите три искат безплатен ключ "
                + "от сайта си. Празно поле = този източник не се ползва.");
        subHead.setTextColor(K_TEXT2);
        subHead.setTextSize(14);
        subHead.setPadding(dp(4), dp(22), 0, dp(2));
        col.addView(subHead);
        final EditText osk = srcField("Api-Key от opensubtitles.com", prefs.getString("osKey", ""), false);
        addLabeled(col, "OpenSubtitles: ключ (Api-Key)", osk);
        final EditText osu = srcField("потребител в opensubtitles.com", prefs.getString("osUser", ""), false);
        addLabeled(col, "OpenSubtitles: потребител", osu);
        final EditText osp = srcField("парола", prefs.getString("osPass", ""), false);
        osp.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        addLabeled(col, "OpenSubtitles: парола", osp);
        final EditText sdk = srcField("ключ от subdl.com", prefs.getString("subdlKey", ""), false);
        addLabeled(col, "Subdl: ключ", sdk);
        final EditText ssk = srcField("ключ от subsource.net", prefs.getString("ssKey", ""), false);
        addLabeled(col, "SubSource: ключ", ssk);
        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));

        final Dialog dlg = new Dialog(this);
        final Runnable save = new Runnable() {
            @Override
            public void run() {
                SharedPreferences.Editor ed = prefs.edit();
                ed.putString("tmdbKey", key.getText().toString().trim());
                ed.putString("osKey", osk.getText().toString().trim());
                ed.putString("osUser", osu.getText().toString().trim());
                ed.putString("osPass", osp.getText().toString().trim());
                ed.putString("subdlKey", sdk.getText().toString().trim());
                ed.putString("ssKey", ssk.getText().toString().trim());
                for (int i = 0; i < SRC_SLOTS; i++) {
                    ed.putString("fsrc" + i, ff[i].getText().toString().trim());
                    ed.putString("tsrc" + i, tf[i].getText().toString().trim());
                }
                ed.apply();
                synchronized (TMDB_JSON) {
                    TMDB_JSON.clear();
                    TMDB_AT.clear();
                }
                dlg.dismiss();
                Toast.makeText(MainActivity.this, "Запазено", Toast.LENGTH_SHORT).show();
                if (catShown()) {
                    catFocusKey = null;
                    catHome();
                }
            }
        };
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l1.setMargins(dp(6), dp(14), dp(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        l2.setMargins(dp(6), dp(14), dp(6), 0);
        btns.addView(catButton("Запази", true, save), l1);
        btns.addView(catButton("Отказ", false, new Runnable() {
            @Override
            public void run() {
                dlg.dismiss();
            }
        }), l2);
        panel.addView(btns);

        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(panel);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(dp(860), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                srcDialog = null;
                hideSystemBars();
            }
        });
        srcDialog = dlg;
        dlg.show();
    }

    // ====================== МОЯТА ТЕЛЕВИЗИЯ ======================
    // Канали от няколко сайта. Всеки канал има един или повече източника (сайт и запазен поток).
    // Пускането минава по ред: запазеният поток -> нов поток, намерен през страницата на сайта ->
    // следващият източник -> чак накрая се отваря самият сайт.

    private static final int T_BG0 = TvTheme.BACKGROUND_TOP;
    private static final int T_BG1 = TvTheme.BACKGROUND;
    private static final int T_SURF = TvTheme.SURFACE;
    private static final int T_SURF2 = TvTheme.SURFACE_ALT;
    private static final int T_LINE = TvTheme.OUTLINE;
    private static final int T_TEXT = TvTheme.TEXT;
    private static final int T_TEXT2 = TvTheme.MUTED;
    private static final int T_TEXT3 = TvTheme.MUTED;
    private static final int T_SKY = TvTheme.BLUE;
    private static final int T_SKY2 = TvTheme.BLUE_LIGHT;
    private static final int T_INK = Color.WHITE;
    private static final int T_LIVE = 0xFFFF4D5E;
    private static final int T_OK = 0xFF34D399;
    private static final int[] T_CHAN = {0xFF2563EB, 0xFF0EA5E9, 0xFF6366F1, 0xFF14B8A6,
            0xFFF43F5E, 0xFF8B5CF6, 0xFFF59E0B, 0xFF10B981, 0xFFEAB308, 0xFF3B82F6, 0xFF06B6D4,
            0xFFEF4444};
    private static final int TV_DIRECT = 0, TV_CAPTURE = 1;

    // Един източник на канала: от кой сайт е, на коя страница е гледан и кой поток е записан.
    private static class TvSrc {
        String site = "", page = "", stream = "", ref = "", org = "", ua = "", mode = "";
        long ok;     // кога последно е тръгнал
        int fails;   // колко гледания поред основният поток не е тръгнал или е заседнал
        long failAt; // кога последно основният поток е вдигнал неуспех (сайтът с пресен неуспех се пробва последен)
        int bad;     // колко проверки поред източникът не е отговорил
        long chk;    // кога е броена последната грешка/успех от проверка
        // Резервни потоци на същия сайт: {адрес, Referer, Origin, User-Agent, неуспехи поред}.
        final ArrayList<String[]> alts = new ArrayList<>();
    }

    private static final int TV_MAX_ALTS = 4; // резервни потоци на един сайт (освен основния)

    private static class TvChan {
        String id = "", name = "";
        long last;   // кога последно е гледан
        final ArrayList<TvSrc> srcs = new ArrayList<>();
    }

    private final ArrayList<TvChan> tvChans = new ArrayList<>();
    private boolean tvLoaded;

    private void tvLoad() {
        if (tvLoaded) return;
        tvLoaded = true;
        tvChans.clear();
        String raw = prefs.getString("tvchans", "");
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                TvChan c = new TvChan();
                c.id = o.optString("i");
                c.name = o.optString("n");
                c.last = o.optLong("t");
                JSONArray s = o.optJSONArray("s");
                if (s != null) {
                    for (int j = 0; j < s.length(); j++) {
                        JSONObject so = s.getJSONObject(j);
                        TvSrc x = new TvSrc();
                        x.site = so.optString("a");
                        x.page = so.optString("p");
                        x.stream = so.optString("u");
                        x.ref = so.optString("r");
                        x.org = so.optString("o");
                        x.ua = so.optString("g");
                        x.mode = so.optString("m");
                        x.ok = so.optLong("k");
                        x.fails = so.optInt("f");
                        x.failAt = so.optLong("l");
                        x.bad = so.optInt("b");
                        x.chk = so.optLong("c");
                        JSONArray xa = so.optJSONArray("x");
                        if (xa != null) {
                            for (int k = 0; k < xa.length() && k < TV_MAX_ALTS; k++) {
                                JSONObject ao = xa.getJSONObject(k);
                                String au = ao.optString("u");
                                if (au.isEmpty()) continue;
                                x.alts.add(new String[]{au, ao.optString("r"), ao.optString("o"),
                                        ao.optString("g"), ao.optString("f", "0")});
                            }
                        }
                        c.srcs.add(x);
                    }
                }
                if (!c.id.isEmpty() && !c.name.isEmpty() && !c.srcs.isEmpty()) tvChans.add(c);
            }
        } catch (Exception ignored) {
            // повреден запис: започва се с празен списък
        }
    }

    private void tvSave() {
        JSONArray a = new JSONArray();
        try {
            for (TvChan c : tvChans) {
                JSONObject o = new JSONObject();
                o.put("i", c.id);
                o.put("n", c.name);
                o.put("t", c.last);
                JSONArray s = new JSONArray();
                for (TvSrc x : c.srcs) {
                    JSONObject so = new JSONObject();
                    so.put("a", x.site);
                    so.put("p", x.page);
                    so.put("u", x.stream);
                    so.put("r", x.ref);
                    so.put("o", x.org);
                    so.put("g", x.ua);
                    so.put("m", x.mode);
                    so.put("k", x.ok);
                    so.put("f", x.fails);
                    if (x.failAt > 0) so.put("l", x.failAt);
                    if (x.bad > 0) so.put("b", x.bad);
                    if (x.chk > 0) so.put("c", x.chk);
                    if (!x.alts.isEmpty()) {
                        JSONArray xa = new JSONArray();
                        for (String[] al : x.alts) {
                            JSONObject ao = new JSONObject();
                            ao.put("u", al[0]);
                            ao.put("r", al[1]);
                            ao.put("o", al[2]);
                            ao.put("g", al[3]);
                            ao.put("f", al[4]);
                            xa.put(ao);
                        }
                        so.put("x", xa);
                    }
                    s.put(so);
                }
                o.put("s", s);
                a.put(o);
            }
        } catch (Exception ignored) {
            return;
        }
        prefs.edit().putString("tvchans", a.toString()).apply();
    }

    private static int tvColor(TvChan c) {
        return T_CHAN[(c.name.hashCode() & 0x7fffffff) % T_CHAN.length];
    }

    private TvChan tvLastChan() {
        String id = prefs.getString("tvlast", "");
        for (TvChan c : tvChans) {
            if (c.id.equals(id)) return c;
        }
        return tvChans.isEmpty() ? null : tvChans.get(0);
    }

    private TvChan tvFindByName(String name) {
        for (TvChan c : tvChans) {
            if (c.name.equalsIgnoreCase(name.trim())) return c;
        }
        return null;
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private int tvSourceTotal() {
        int n = 0;
        for (TvChan c : tvChans) n += c.srcs.size();
        return n;
    }

    // Всички сайтове за телевизия (групата "Телевизия"); ако няма такива - всички сайтове.
    private ArrayList<String[]> tvSites() {
        ArrayList<String[]> l = new ArrayList<>();
        for (String[] s : sites) {
            if (family(groupOf(s)) == 0) l.add(s);
        }
        if (l.isEmpty()) l.addAll(sites);
        return l;
    }

    // Предлага име на канала от заглавието на страницата ("bTV - гледай на живо - Сайт" -> "bTV").
    private static String tvGuessName(String title, Uri uri) {
        String t = title == null ? "" : oneLine(title);
        for (String sep : new String[]{" - ", " | ", " – ", " — ", " » ", " :: ", ": "}) {
            int i = t.indexOf(sep);
            if (i > 1) t = t.substring(0, i);
        }
        t = t.trim();
        String low = t.toLowerCase(Locale.ROOT);
        for (String w : new String[]{"гледай ", "гледайте ", "watch "}) {
            if (low.startsWith(w)) {
                t = t.substring(w.length()).trim();
                low = t.toLowerCase(Locale.ROOT);
            }
        }
        for (String w : new String[]{" на живо", " онлайн", " online", " live", " директно"}) {
            int k = low.indexOf(w);
            if (k > 1) {
                t = t.substring(0, k).trim();
                low = t.toLowerCase(Locale.ROOT);
            }
        }
        if (t.length() > 28) t = t.substring(0, 28).trim();
        String slug = tvSlugName(uri);
        String norm = t.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-я0-9]", "");
        String host = uri == null || uri.getHost() == null ? ""
                : uri.getHost().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        boolean generic = norm.length() < 2
                || (!host.isEmpty() && (host.contains(norm) || norm.contains(host.replace("www", ""))))
                || norm.contains("телевизия") || norm.contains("канали") || norm.contains("програма");
        // Заглавието на много сайтове е общо (името на сайта): тогава името идва от адреса.
        if (generic && !slug.isEmpty()) return slug;
        return t.isEmpty() ? slug : t;
    }

    // "btv-comedy-online" -> "bTV Comedy"
    private static String tvSlugName(Uri uri) {
        String seg = uri == null ? null : uri.getLastPathSegment();
        if (seg == null) return "";
        int dot = seg.indexOf('.');
        if (dot > 0) seg = seg.substring(0, dot);
        StringBuilder sb = new StringBuilder();
        for (String w : seg.toLowerCase(Locale.ROOT).split("[-_+ ]+")) {
            if (w.isEmpty() || w.equals("online") || w.equals("live") || w.equals("tv")
                    && sb.length() == 0 || w.equals("gledai") || w.equals("watch")
                    || w.equals("live-stream") || w.equals("stream") || w.equals("kanal")
                    || w.equals("channel") || w.equals("onlayn")) {
                continue;
            }
            String out;
            if (w.equals("btv")) out = "bTV";
            else if (w.equals("tv") || w.equals("hd") || w.equals("bnt") || w.equals("hbo")
                    || w.equals("cnn") || w.equals("mtv") || w.equals("rtl") || w.equals("axn")
                    || w.equals("tlc") || w.equals("amc") || w.equals("bg") || w.equals("nat")
                    || w.equals("ntv") || w.equals("rt")) out = w.toUpperCase(Locale.ROOT);
            else out = Character.toUpperCase(w.charAt(0)) + w.substring(1);
            if (sb.length() > 0) sb.append(' ');
            sb.append(out);
        }
        String r = sb.toString().trim();
        if (r.length() > 28) r = r.substring(0, 28).trim();
        return r.length() < 2 ? "" : r;
    }

    // Страницата на канала може да се отваря сама само ако сочи точно към него
    // (не към началото на сайта, където каналът се избира с клик).
    private static boolean tvCapturable(TvSrc s) {
        if (s.page == null || !s.page.startsWith("http")) return false;
        Uri u = Uri.parse(s.page);
        String path = u.getPath();
        boolean root = path == null || path.isEmpty() || path.equals("/");
        return !root || u.getQuery() != null || u.getFragment() != null;
    }

    // Фон на екраните: нощно синьо с две меки петна светлина.
    private static class TvBgView extends View {
        private final Paint p = new Paint();
        private Shader base, glow1, glow2;

        TvBgView(Context c) {
            super(c);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (w <= 0 || h <= 0) {
                base = null;
                glow1 = null;
                glow2 = null;
                return;
            }
            base = new LinearGradient(0, 0, 0, h, T_BG0, T_BG1, Shader.TileMode.CLAMP);
            float r = Math.max(w, h);
            glow1 = new RadialGradient(w * 0.88f, -h * 0.06f, r * 0.56f, 0x10136BFF, 0x00136BFF,
                    Shader.TileMode.CLAMP);
            glow2 = new RadialGradient(0, h * 1.05f, r * 0.44f, 0x08136BFF, 0x00136BFF,
                    Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (base == null) return;
            int w = getWidth();
            int h = getHeight();
            p.setShader(base);
            c.drawRect(0, 0, w, h, p);
            p.setShader(glow1);
            c.drawRect(0, 0, w, h, p);
            p.setShader(glow2);
            c.drawRect(0, 0, w, h, p);
        }
    }

    // Червена лента "В РАЗРАБОТКА" в левия горен ъгъл на плочка с височина h.
    private static void drawDevRibbon(Canvas c, Paint p, Paint tp, Path path, float h) {
        float aIn = h * 0.46f;
        float aOut = h * 0.98f;
        path.reset();
        path.moveTo(aIn, 0);
        path.lineTo(aOut, 0);
        path.lineTo(0, aOut);
        path.lineTo(0, aIn);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFDC2626);
        c.drawPath(path, p);
        float ac = (aIn + aOut) / 2f;
        float maxW = ac * 1.4142f * 0.80f;
        tp.setTextSize(h * 0.135f);
        String txt = "В РАЗРАБОТКА";
        float tw = tp.measureText(txt);
        if (tw > maxW) tp.setTextSize(tp.getTextSize() * maxW / tw);
        c.save();
        c.rotate(-45f, ac / 2f, ac / 2f);
        c.drawText(txt, ac / 2f, ac / 2f + tp.getTextSize() * 0.35f, tp);
        c.restore();
    }

    // Картинка на плочката "Моята телевизия": три реда канали, лента със седмица отдолу
    // и червена лента "В РАЗРАБОТКА".
    private static class TvArt extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rf = new RectF();

        TvArt(Context c, Typeface tf) {
            super(c);
            tp.setTypeface(tf);
            tp.setColor(Color.WHITE);
            tp.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x22FFFFFF);
            c.drawCircle(w * 0.34f, h * 0.46f, h * 0.95f, p);
            p.setColor(0x16FFFFFF);
            c.drawCircle(w * 0.34f, h * 0.46f, h * 0.62f, p);
            // програма по часове (долна лента)
            float stripH = h * 0.22f;
            p.setColor(0x55000000);
            c.drawRect(0, h - stripH, w, h, p);
            float seg = stripH * 0.34f;
            float top = h - stripH + (stripH - seg) / 2f;
            float[][] parts = {{0.05f, 0.27f}, {0.30f, 0.58f}, {0.61f, 0.80f}, {0.83f, 0.97f}};
            for (int i = 0; i < parts.length; i++) {
                rf.set(w * parts[i][0], top, w * parts[i][1], top + seg);
                p.setColor(i == 1 ? 0xFF38BDF8 : 0x66FFFFFF);
                c.drawRoundRect(rf, seg / 2f, seg / 2f, p);
            }
            // три реда канали
            float bx = w * 0.24f;
            float bw = w * 0.20f;
            float bh = h * 0.105f;
            float gap = h * 0.05f;
            float by = h * 0.46f - (3 * bh + 2 * gap) / 2f;
            for (int i = 0; i < 3; i++) {
                float t = by + i * (bh + gap);
                rf.set(bx, t, bx + bw, t + bh);
                p.setColor(i == 0 ? 0xFF38BDF8 : 0x38FFFFFF);
                c.drawRoundRect(rf, bh / 2f, bh / 2f, p);
                rf.set(bx, t, bx + bw * 0.18f, t + bh);
                p.setColor(i == 0 ? 0xFF8FDBFF : 0x59FFFFFF);
                c.drawRoundRect(rf, bh / 2f, bh / 2f, p);
            }
            drawDevRibbon(c, p, tp, path, h);
        }
    }

    // ---- общи части на екраните ----

    private StateListDrawable tvPrimaryBg(int radiusPx) {
        return TvTheme.control(U, true);
    }

    private final View.OnFocusChangeListener tvFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            float k = v.getTag(R_SCALE) instanceof Float ? (Float) v.getTag(R_SCALE) : 1.04f;
            v.animate().scaleX(has ? k : 1f).scaleY(has ? k : 1f).setDuration(130).start();
            v.setTranslationZ(has ? 14 * U : 0f);
            if (Build.VERSION.SDK_INT >= 28) {
                v.setOutlineSpotShadowColor(has ? T_SKY : Color.BLACK);
                v.setOutlineAmbientShadowColor(has ? 0x665F536F : Color.BLACK);
            }
        }
    };

    // Главен бутон: светлосин, с тъмен надпис.
    private LinearLayout tvPrimary(int icon, String label, float heightU, float textU,
                                   final Runnable action) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(u(28), 0, u(36), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(tvPrimaryBg(u(heightU / 2f)));
        LinearLayout.LayoutParams ilp = lp(u(textU + 2), u(textU + 2));
        ilp.rightMargin = u(14);
        b.addView(new IconView(this, icon, T_INK), ilp);
        b.addView(text(label, textU, T_INK, Typeface.DEFAULT_BOLD, true));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setTag(R_SCALE, 1.05f);
        b.setOnFocusChangeListener(tvFocus);
        return b;
    }

    // Втори бутон: хапче с икона и надпис.
    private LinearLayout tvPill(int icon, String label, final Runnable action) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(u(icon >= 0 ? 20 : 26), 0, u(26), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(TvTheme.control(U,false));
        if (icon >= 0) {
            LinearLayout.LayoutParams ilp = lp(u(22), u(22));
            ilp.rightMargin = u(10);
            b.addView(new IconView(this, icon, T_SKY2), ilp);
        }
        b.addView(text(label, 19, T_TEXT, tfMedium, true));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setTag(R_SCALE, 1.06f);
        b.setOnFocusChangeListener(tvFocus);
        return b;
    }

    // Червена точка с мек ореол ("на живо").
    private View tvLiveDot(float sizeU) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x38FF4D5E);
        View r = new View(this);
        r.setBackground(ring);
        f.addView(r, new FrameLayout.LayoutParams(MP, MP));
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(T_LIVE);
        View d = new View(this);
        d.setBackground(dot);
        int ds = u(sizeU * 0.56f);
        f.addView(d, new FrameLayout.LayoutParams(ds, ds, Gravity.CENTER));
        return f;
    }


    // Голямата сянка с номера на канала зад картата (като на картинката: наполовина скрита).
    private static class GhostNum extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String s = "";

        GhostNum(Context c, Typeface tf, String s) {
            super(c);
            this.s = s;
            p.setTypeface(tf);
            p.setColor(0x11FFFFFF);
            p.setTextAlign(Paint.Align.RIGHT);
        }

        void set(String s) {
            if (s.equals(this.s)) return;
            this.s = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            p.setTextSize(h * 2.33f);
            c.drawText(s, getWidth() - h * 1.10f, h * 1.372f, p);
        }
    }

    // ---- менюто: плочката ----

    private TextView sectionSub;   // подзаглавието на последно направената плочка
    private TextView tvTileSub;    // подзаглавието на плочката "Моята телевизия"

    private String tvTileSubText() {
        int n = tvChans.size();
        return n == 0 ? "събери каналите си" : plural(n, "канал", "канала");
    }

    // Малка плочка в реда "Мои раздели": картина, заглавие и пояснение отдясно.
    private FrameLayout sectionTile(View art, String title, String sub, int[] colors,
                                    final Runnable action) {
        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackground(gradBg(colors, 22, 4));
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(MP, MP);
        alp.setMargins(u(4), u(4), u(4), u(4));
        roundClip(art, u(18));
        tile.addView(art, alp);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 20, Color.WHITE, Typeface.DEFAULT_BOLD, true));
        TextView subView = text(sub, 14, 0xDDFFFFFF, tfMedium, true);
        sectionSub = subView;
        tx.addView(subView);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(WC, WC,
                Gravity.BOTTOM | Gravity.END);
        tlp.rightMargin = u(18);
        tlp.bottomMargin = u(30);
        tile.addView(tx, tlp);
        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        tile.setOnFocusChangeListener(tileFocus);
        return tile;
    }

    // ---- екранът „Моята телевизия“: лява лента с категории, карта, редове и мрежа с лога ----

    private FrameLayout tvPanel;
    private LinearLayout tvHead;
    private TextView tvHeadClock;
    private LinearLayout tvRail;
    private ScrollView tvScroll;
    private LinearLayout tvBox;
    private TextView tvHint;
    private View tvFade;
    private View tvTile;
    private View tvMain;                 // първият елемент в съдържанието: там отива фокусът
    private int tvSel = TV_HOME;         // какво е избрано в лентата: Начало, Всички или категория
    private int tvRailPend = TV_HOME;
    private int[] tvCatIdx = new int[0]; // категория на всеки канал (по реда в tvChans)
    private int[] tvCatCnt = new int[TV_CAT_NAMES.length];

    private boolean tvPanelShown() {
        return tvPanel != null && tvPanel.getVisibility() == View.VISIBLE;
    }

    private void tvBuildPanel() {
        tvPanel = new FrameLayout(this);
        tvPanel.setBackground(TvTheme.background());

        tvScroll = new CatScroll(this);
        tvScroll.setVerticalScrollBarEnabled(false);
        tvScroll.setClipChildren(false);
        tvScroll.setClipToPadding(false);
        tvScroll.setFillViewport(true);
        tvBox = new LinearLayout(this);
        tvBox.setOrientation(LinearLayout.VERTICAL);
        tvBox.setClipChildren(false);
        tvScroll.addView(tvBox, new ViewGroup.LayoutParams(MP, WC));
        tvPanel.addView(tvScroll, new FrameLayout.LayoutParams(MP, MP));

        // Мек преход най-долу, за да се чете подсказката.
        tvFade = new ScrimView(this, true, new int[]{0x000D1119, 0xEB0D1119}, new float[]{0f, 1f});
        tvPanel.addView(tvFade, new FrameLayout.LayoutParams(MP, u(80), Gravity.BOTTOM));
        tvHint = text("", 16, T_TEXT3, tfMedium, true);
        tvHint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM);
        hl.bottomMargin = u(22);
        tvPanel.addView(tvHint, hl);

        tvRail = new LinearLayout(this);
        tvRail.setOrientation(LinearLayout.VERTICAL);
        tvRail.setPadding(u(10), u(10), u(10), u(12));
        GradientDrawable rg = new GradientDrawable();
        rg.setColor(0x09FFFFFF);
        rg.setCornerRadius(u(26));
        rg.setStroke(Math.max(1, u(1)), 0x14FFFFFF);
        tvRail.setBackground(rg);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(u(292), WC,
                Gravity.TOP | Gravity.START);
        rl.leftMargin = u(64);
        rl.topMargin = u(112);
        tvPanel.addView(tvRail, rl);

        tvHead = new LinearLayout(this);
        tvHead.setOrientation(LinearLayout.HORIZONTAL);
        tvHead.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams hdl = new FrameLayout.LayoutParams(MP, u(84),
                Gravity.TOP | Gravity.START);
        hdl.leftMargin = u(64);
        hdl.rightMargin = u(64);
        hdl.topMargin = u(16);
        tvPanel.addView(tvHead, hdl);

        tvPanel.setVisibility(View.GONE);
        root.addView(tvPanel, new FrameLayout.LayoutParams(MP, MP));
    }

    // Празно състояние: целият екран за съдържанието; иначе съдържанието е вдясно от лентата.
    private void tvLayoutFor(boolean empty) {
        FrameLayout.LayoutParams sl=(FrameLayout.LayoutParams)tvScroll.getLayoutParams();sl.leftMargin=u(54);sl.rightMargin=u(54);sl.topMargin=u(214);sl.bottomMargin=u(56);tvScroll.setLayoutParams(sl);
        tvRail.setVisibility(View.GONE);tvRail.removeAllViews();tvBox.setPadding(0,0,0,0);
        FrameLayout.LayoutParams h=(FrameLayout.LayoutParams)tvHead.getLayoutParams();h.topMargin=u(126);h.height=u(60);h.leftMargin=u(54);h.rightMargin=u(54);tvHead.setLayoutParams(h);
    }

    private void tvShowPanel() {
        if (isFinishing()) return;
        appSection = SECTION_TV;
        if (appBar != null) appBar.setSection(appSection);
        if (tvMode) tvLeave(false);
        if (catShown()) hideCatalog();
        closeNative(false);
        destroyWeb();
        menuHolder.setVisibility(View.GONE);
        tvLoad();
        if (tvPanel == null) tvBuildPanel();
        // Докато екранът е отгоре, менюто отдолу не взима фокус.
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        tvPanel.setVisibility(View.VISIBLE);
        logEvent("Отворена Моята телевизия");
        tvLogoMissing.clear();
        tvLogoOffline = false;
        tvLogoErrRun = 0;
        // При връщане от гледане в категория фокусът отива на последния гледан канал.
        TvChan last = tvLastChan();
        tvFill(tvSel == TV_HOME || last == null ? null : last.id);
        tvEpgLoad();
        updateState();
    }

    // Докато се гледа канал или се отваря друг екран, този екран не държи памет:
    // изгледите се махат, а логата се освобождават (на диска остават).
    private void tvPanelRelease() {
        tvCloseTimeline();
        if (!tvMode || tvVod) tvEpgStop();
        tvOverviewGuide=null;
        if (tvHeroArtwork != null) tvHeroArtwork.clear();
        tvHeroArtwork = null;
        ui.removeCallbacks(tvHeroRun);
        ui.removeCallbacks(tvRailRun);
        ui.removeCallbacks(tvPanelClockRun);
        if (tvPanel != null) {
            tvPanel.setVisibility(View.GONE);
            tvBox.removeAllViews();
            tvHead.removeAllViews();
            tvRail.removeAllViews();
        }
        tvMain = null;
        tvHeroCard = null;
        tvHeroPlate = null;
        tvHeroWant = null;
        tvHeroGhost = null;
        tvHeroName = null;
        tvHeroChips = null;
        tvHeroLabTxt = null;
        tvHeroCatDot = null;
        tvHeroCatTxt = null;
        tvHeroDescription = null;
        tvHeroProgress = null;
        tvHeroTime = null;
        tvHeroWatch = null;
        tvHeroFavourite = null;
        tvEpgBox = null;
        tvLiveRow = null;
        tvHeadClock = null;
        tvLogoTrim();
    }

    // Скрива екрана, без да връща фокуса в менюто (когато се отваря друг екран).
    private void tvHidePanelQuiet() {
        tvPanelRelease();
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // Броят на каналите в плочката на менюто следва промените.
        if (tvTileSub != null) tvTileSub.setText(tvTileSubText());
    }

    private void tvHidePanel() {
        menuHolder.setVisibility(View.VISIBLE);
        tvSel = TV_HOME; // следващото отваряне от менюто започва от „Начало“
        tvHidePanelQuiet();
        View f = tvTile != null ? tvTile : (heroView != null ? heroView : firstTile);
        if (f != null) f.requestFocus();
        updateState();
    }

    // Часът в горния десен ъгъл: обновява се в началото на всяка минута, само докато екранът се вижда.
    private final Runnable tvPanelClockRun = new Runnable() {
        @Override
        public void run() {
            tvPanelClockTick();
        }
    };

    private void tvPanelClockTick() {
        ui.removeCallbacks(tvPanelClockRun);
        if(!tvPanelShown() && !tvListShown())return;
        if(tvOverviewGuide!=null)tvOverviewGuide.refresh();
        if(tvPlayerGuide!=null)tvPlayerGuide.refresh();
        if(tvGuide!=null && SystemClock.elapsedRealtime()-tvGuideLoadedAt>60L*60*1000)tvEpgLoad();
        long now=System.currentTimeMillis();
        ui.postDelayed(tvPanelClockRun,60000-now%60000+150);
    }

    private void tvFillHead(String sub){
        tvHead.removeAllViews();tvHead.addView(text("Телевизия",36,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true),new LinearLayout.LayoutParams(0,WC,1));
        tvHead.addView(filterChip("Всички",!tvFavouritesOnly && tvSel<0,()->{tvFavouritesOnly=false;tvSelect(TV_HOME);tvFillHead("");}),lp(WC,u(46)));
        LinearLayout.LayoutParams fav=lp(WC,u(46));fav.leftMargin=u(12);tvHead.addView(filterChip("Любими",tvFavouritesOnly,()->{tvFavouritesOnly=true;tvSelect(TV_HOME);tvFillHead("");}),fav);
        LinearLayout.LayoutParams cat=lp(WC,u(46));cat.leftMargin=u(12);tvHead.addView(filterChip("Категории",!tvFavouritesOnly && tvSel>=0,()->tvCategories()),cat);
        LinearLayout.LayoutParams epg=lp(WC,u(46));epg.leftMargin=u(16);tvHead.addView(tvDashboardPill(IC_CLOCK,"Пълна програма",()->tvShowTimeline()),epg);
        LinearLayout.LayoutParams add=lp(WC,u(46));add.leftMargin=u(12);tvHead.addView(tvDashboardPill(IC_PLUS,"Добави",()->tvAddFromSite()),add);tvPanelClockTick();
    }

    // Пълни екрана: празно състояние или списък. focusId е каналът, който да остане на фокус.
    private void tvFill(final String focusId) {
        tvCount(); tvLayoutFor(tvChans.isEmpty()); tvFillHead(""); tvBuildContent();
        tvHint.setText("↑ ↓ Канали    OK Гледай    MENU Всички раздели    → Програма");
        tvPanel.post(()->{if(tvPanelShown())tvFocusContent();});
    }

    // Колко канала има във всяка категория.
    private void tvCount() {
        int n = tvChans.size();
        tvCatIdx = new int[n];
        tvCatCnt = new int[TV_CAT_NAMES.length];
        for (int i = 0; i < n; i++) {
            int c = tvCatOfName(tvChans.get(i).name);
            tvCatIdx[i] = c;
            tvCatCnt[c]++;
        }
    }

    private String tvSrcNames(TvChan c) {
        StringBuilder sb = new StringBuilder();
        for (TvSrc s : c.srcs) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(s.site);
        }
        return sb.toString();
    }

    // ---- лявата лента: Начало, Всички канали, категории ----

    private void tvFillRail() {
        tvRail.removeAllViews();

        tvRail.addView(tvRailEntry(TV_HOME, "Всички", IC_GRID, tvChans.size(), 0), tvRailLp());
        View sep = new View(this);
        sep.setBackgroundColor(0x1AFFFFFF);
        LinearLayout.LayoutParams sl = lp(MP, Math.max(1, u(1)));
        sl.leftMargin = u(14);
        sl.rightMargin = u(14);
        sl.topMargin = u(2);
        sl.bottomMargin = u(8);
        tvRail.addView(sep, sl);
        for (int i = 0; i < TV_CAT_NAMES.length; i++) {
            if (tvCatCnt[i] > 0) {
                tvRail.addView(tvRailEntry(i, TV_CAT_NAMES[i], -1, tvCatCnt[i], TV_CAT_COLORS[i]),
                        tvRailLp());
            }
        }
        tvRailMark();
    }

    private LinearLayout.LayoutParams tvRailLp() {
        LinearLayout.LayoutParams l = lp(MP, u(48));
        l.bottomMargin = u(6);
        return l;
    }

    private View tvRailEntry(final int sel, String label, int icon, int count, int color) {
        TvRailItem it = new TvRailItem(this, sel, label, icon, count, color);
        it.setOnFocusChangeListener(tvRailFocus);
        it.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvSelect(sel);
                tvFocusContent();
            }
        });
        // ▶ приема избраното и отива в съдържанието.
        it.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int code, KeyEvent e) {
                if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    tvSelect(sel);
                    tvFocusContent();
                    return true;
                }
                return false;
            }
        });
        return it;
    }

    private void tvRailMark() {
        for (int i = 0; i < tvRail.getChildCount(); i++) {
            View v = tvRail.getChildAt(i);
            if (v instanceof TvRailItem) ((TvRailItem) v).mark(((TvRailItem) v).sel == tvSel);
        }
    }

    // Докато се върви по лентата, съдържанието се сменя чак когато спреш.
    private final View.OnFocusChangeListener tvRailFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            if (!has || !(v instanceof TvRailItem)) return;
            tvRailPend = ((TvRailItem) v).sel;
            ui.removeCallbacks(tvRailRun);
            if (tvRailPend != tvSel) ui.postDelayed(tvRailRun, 200);
        }
    };

    private final Runnable tvRailRun = new Runnable() {
        @Override
        public void run() {
            if (!tvPanelShown() || tvRail == null || !tvRail.hasFocus()) return;
            if (tvRailPend != tvSel) tvSelect(tvRailPend);
        }
    };

    private void tvSelect(int sel) {
        ui.removeCallbacks(tvRailRun);
        tvSel = sel;
        tvRailPend = sel;
        tvRailMark();
        tvBuildContent();
        tvScroll.scrollTo(0, 0);
    }

    private void tvFocusContent() {
        if(tvOverviewGuide!=null)tvOverviewGuide.focusChannels();
        else if (tvMain != null) tvMain.requestFocus();
    }

    private void tvFocusRail() {
        for (int i = 0; i < tvRail.getChildCount(); i++) {
            View v = tvRail.getChildAt(i);
            if (v instanceof TvRailItem && ((TvRailItem) v).sel == tvSel) {
                v.requestFocus();
                return;
            }
        }
    }

    // ---- общи слушатели на плочките ----

    private final View.OnClickListener tvCardClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v instanceof TvCard) tvTuneUser(((TvCard) v).chan, -1);
        }
    };

    private final View.OnLongClickListener tvCardLong = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            if (!(v instanceof TvCard)) return false;
            tvChannelMenu(((TvCard) v).chan);
            return true;
        }
    };

    // ◀ от първата плочка в реда води обратно към лентата с категории.
    private final View.OnKeyListener tvCardKeys = new View.OnKeyListener() {
        @Override
        public boolean onKey(View v, int code, KeyEvent e) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_LEFT
                    && tvRail != null && tvRail.getVisibility() == View.VISIBLE
                    && v instanceof TvCard && ((TvCard) v).edge) {
                // Само ново натискане: при задържане стрелката спира в началото на реда.
                if (e.getRepeatCount() == 0) tvFocusRail();
                return true;
            }
            return false;
        }
    };

    // Увеличение и сянка, както навсякъде; на екрана „Начало“ картата показва канала на фокус.
    private final View.OnFocusChangeListener tvCardFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            tvFocus.onFocusChange(v, has);
            if (has && v instanceof TvCard && tvHeroCard != null) tvHeroShow(((TvCard) v).chan);
        }
    };

    private TvCard tvCardFor(int i, float wU, float plateU) {
        TvChan c = tvChans.get(i);
        TvCard t = new TvCard(this, c, tvPicFor(c.name), i + 1, wU, plateU);
        t.setOnClickListener(tvCardClick);
        t.setOnLongClickListener(tvCardLong);
        t.setOnKeyListener(tvCardKeys);
        t.setTag(R_SCALE, 1.06f);
        t.setOnFocusChangeListener(tvCardFocus);
        return t;
    }

    // ---- съдържанието: „Начало“ (карта и редове) или мрежа ----

    private void tvBuildContent() {
        String selected=tvOverviewGuide==null?null:tvOverviewGuide.selection();
        ui.removeCallbacks(tvHeroRun);
        if(tvHeroArtwork!=null)tvHeroArtwork.clear();
        tvHeroArtwork=null; tvEpgBox=null; tvLiveRow=null; tvMain=null;
        tvBox.removeAllViews(); tvOverviewGuide=null;
        if(tvChans.isEmpty()) {
            LinearLayout empty=new LinearLayout(this);empty.setOrientation(LinearLayout.VERTICAL);empty.setGravity(Gravity.CENTER);
            empty.addView(text("Твоите канали са тук",38,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true));
            empty.addView(text("Добави списък или запази канал от познат сайт.",21,T_TEXT2,tfMedium,false),lpTopW(WC,WC,u(18)));
            tvMain=tvDashboardPill(IC_PLUS,"Добави канали",()->tvAddFromSite());
            empty.addView(tvMain,lpTopW(WC,u(56),u(24)));
            tvBox.addView(empty,lp(MP,u(550))); return;
        }
        tvOverviewGuide=newGuide(false);
        TvChan last=tvLastChan();
        tvOverviewGuide.setChannels(guideChannels(true),selected!=null?selected:last==null?null:last.id);
        tvOverviewGuide.setGuide(tvGuide,tvEpgError);
        int height=Math.max(u(450),(root.getHeight()>0?root.getHeight():u(900))-u(232));
        tvBox.addView(tvOverviewGuide,lp(MP,height)); tvMain=tvOverviewGuide;
    }

    private void tvBuildHome() {
        TvChan last = tvLastChan();
        if (last == null && !tvChans.isEmpty()) last = tvChans.get(0);
        tvHeroLast = last;
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        View hero = tvHeroBuild(last);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(0, u(350), 3f);
        heroLp.rightMargin = u(18);
        top.addView(hero, heroLp);
        tvEpgBox = new LinearLayout(this);
        tvEpgBox.setOrientation(LinearLayout.VERTICAL);
        tvEpgBox.setPadding(u(16), u(14), u(16), u(12));
        tvEpgBox.setBackground(tvHeroBg());
        top.addView(tvEpgBox, new LinearLayout.LayoutParams(0, u(350), 2f));
        tvBox.addView(top, lp(MP, u(350)));
        tvMain = tvHeroWatch;
        tvEpgRender();

        LinearLayout channelsTitle = new LinearLayout(this);
        channelsTitle.setGravity(Gravity.CENTER_VERTICAL);
        channelsTitle.addView(text("На живо по телевизията", 24, TvTheme.TEXT, Typeface.DEFAULT_BOLD, true),
                new LinearLayout.LayoutParams(0, WC, 1f));
        channelsTitle.addView(tvDashboardPill(IC_GRID, "Всички канали", () -> tvSelect(TV_ALL)), lp(WC, u(42)));
        tvBox.addView(channelsTitle, lpTopW(MP, u(50), u(12)));
        HorizontalScrollView live = new HorizontalScrollView(this);
        live.setHorizontalScrollBarEnabled(false);
        live.setClipToPadding(false);
        LinearLayout liveRow = new LinearLayout(this);
        tvLiveRow = liveRow;
        liveRow.setPadding(u(4), u(6), u(4), u(8));
        for (int i = 0; i < Math.min(tvChans.size(), 16); i++) {
            TvChan channel = tvChans.get(i);
            LinearLayout tile = tvLiveCard(channel);
            LinearLayout.LayoutParams l = lp(u(225), u(146));
            l.rightMargin = u(12);
            liveRow.addView(tile, l);
        }
        live.addView(liveRow);
        tvBox.addView(live, lp(MP, WC));

        ArrayList<Integer> favourites = new ArrayList<>();
        for (int i = 0; i < tvChans.size(); i++) if (prefs.getBoolean("tvfav:" + tvChans.get(i).id, false)) favourites.add(i);
        if (!favourites.isEmpty()) tvBox.addView(tvRow("Любими канали", IC_STAR, 0, favourites), lpTopW(MP, WC, u(12)));

        ArrayList<Integer> recent = new ArrayList<>();
        for (int i = 0; i < tvChans.size(); i++) if (tvChans.get(i).last > 0) recent.add(i);
        Collections.sort(recent, (a, b) -> Long.compare(tvChans.get(b).last, tvChans.get(a).last));
        while (recent.size() > 12) recent.remove(recent.size() - 1);
        if (!recent.isEmpty()) tvBox.addView(tvRow("Последно гледани", IC_CLOCK, 0, recent), lpTopW(MP, WC, u(12)));

        // Shortcuts do not start the movie catalogue or any WebView in the background.
        tvBox.addView(text("Филми и сериали", 22, TvTheme.TEXT, Typeface.DEFAULT_BOLD, true), lpTopW(MP, WC, u(12)));
        LinearLayout movies = new LinearLayout(this);
        movies.addView(tvDashboardLink(IC_FILM, "Отвори каталога", () -> showCatalog(null)),
                new LinearLayout.LayoutParams(0, u(66), 1f));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, u(66), 1f);
        searchLp.leftMargin = u(12);
        movies.addView(tvDashboardLink(IC_SEARCH, "Намери филм или сериал", () -> {
            showCatalog(null); showSearchDialog();
        }), searchLp);
        tvBox.addView(movies, lpTopW(MP, WC, u(8)));
        tvBox.addView(text("Сайтове", 22, TvTheme.TEXT, Typeface.DEFAULT_BOLD, true), lpTopW(MP, WC, u(14)));
        HorizontalScrollView siteScroll = new HorizontalScrollView(this);
        siteScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout siteRow = new LinearLayout(this);
        for (int i = 0; i < Math.min(sites.size(), 10); i++) {
            final String[] site = sites.get(i);
            LinearLayout.LayoutParams l = lp(u(180), u(58));
            l.rightMargin = u(12);
            siteRow.addView(tvDashboardLink(IC_MONITOR, site[0], () -> openSite(site)), l);
        }
        siteRow.addView(tvDashboardLink(IC_GRID, "Всички сайтове", () -> { tvHidePanel(); backToMenu(); }), lp(u(210), u(58)));
        siteScroll.addView(siteRow);
        tvBox.addView(siteScroll, lpTopW(MP, WC, u(8)));
    }

    private StateListDrawable tvDashboardPrimaryBg(int radiusPx) {
        return TvTheme.control(U,true);
    }

    private LinearLayout tvDashboardPill(int icon, String title, Runnable action) {
        LinearLayout button = tvPill(icon, title, action);
        // The shared focus border stays inside tight toolbar and dialog rows.
        button.setOnFocusChangeListener(null);
        button.setBackground(TvTheme.control(U,false));
        for (int i = 0; i < button.getChildCount(); i++) {
            View child = button.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(TvTheme.TEXT);
        }
        return button;
    }

    private LinearLayout tvDashboardLink(int icon, String title, Runnable action) {
        LinearLayout v = tvDashboardPill(icon, title, action);
        v.setBackground(stateBg(TvTheme.SURFACE, 0xFF232238, TvTheme.FOCUS, u(14), TvTheme.OUTLINE));
        return v;
    }

    private LinearLayout tvLiveCard(final TvChan channel) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(u(16), u(10), u(16), u(10));
        tile.setBackground(stateBg(TvTheme.SURFACE, 0xFF232238, TvTheme.FOCUS, u(16), TvTheme.OUTLINE));
        tile.setFocusable(true); tile.setClickable(true); tile.setLongClickable(true);
        tile.setTag("ch:" + channel.id);
        LinearLayout logoRow = new LinearLayout(this);
        TvPlate logo = new TvPlate(this, 8, 4, true);
        logo.setChan(channel.name);
        logoRow.addView(logo, new LinearLayout.LayoutParams(0, u(42), 1f));
        TextView badge = text("НА ЖИВО", 11, Color.WHITE, tfMedium, true);
        badge.setPadding(u(6), u(4), u(6), u(4));
        badge.setBackground(stateBg(0xFFC91D38, 0xFFC91D38, 0, u(6), 0));
        logoRow.addView(badge, lp(WC, WC));
        tile.addView(logoRow);
        EpgGuide.Event e = tvGuide == null ? null : tvGuide.current(channel.name, System.currentTimeMillis());
        tile.addView(text(e == null ? channel.name : e.title, 17, TvTheme.TEXT, tfMedium, true), lpTopW(MP, WC, u(8)));
        tile.addView(text(e == null ? "Програма няма" : tvEventTime(e), 15, TvTheme.MUTED, tfMedium, true));
        ProgressBar progress = tvProgress();
        if (e != null) progress.setProgress(tvEventProgress(e));
        tile.addView(progress, lpTopW(MP, u(4), u(8)));
        tile.setOnFocusChangeListener((v, focused) -> { tvFocus.onFocusChange(v, focused); if (focused) tvHeroShow(channel); });
        tile.setOnClickListener(v -> tvTuneUser(channel, -1));
        tile.setOnLongClickListener(v -> { tvChannelMenu(channel); return true; });
        return tile;
    }

    private int firstCat() {
        for (int c = 0; c < TV_CAT_NAMES.length; c++) {
            if (tvCatCnt[c] > 0) return c;
        }
        return -1;
    }

    // Ред: заглавие с брой и лента от плочки, която се върти настрани.
    private View tvRow(String title, int icon, int color, ArrayList<Integer> idx) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout hd = new LinearLayout(this);
        hd.setOrientation(LinearLayout.HORIZONTAL);
        hd.setGravity(Gravity.CENTER_VERTICAL);
        hd.setPadding(u(24), 0, 0, 0);
        if (icon >= 0) {
            LinearLayout.LayoutParams ilp = lp(u(24), u(24));
            ilp.rightMargin = u(12);
            hd.addView(new IconView(this, icon, T_SKY2), ilp);
        } else {
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setColor(TvTheme.MUTED);
            dg.setCornerRadius(u(4));
            dot.setBackground(dg);
            LinearLayout.LayoutParams dlp = lp(u(12), u(12));
            dlp.rightMargin = u(12);
            hd.addView(dot, dlp);
        }
        hd.addView(text(title, 30, T_TEXT, tfCondBold, true));
        TextView cnt = text(String.valueOf(idx.size()), 18, T_TEXT3, tfMedium, true);
        cnt.setPadding(u(12), u(4), 0, 0);
        hd.addView(cnt);
        col.addView(hd, lp(MP, u(40)));

        RowScroll hs = new RowScroll(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setPadding(u(24), u(18), u(24), u(10)); // сверху място за ореола при фокус
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        for (int k = 0; k < idx.size(); k++) {
            TvCard t = tvCardFor(idx.get(k), 212, 118);
            LinearLayout.LayoutParams tl = lp(t.wPx, t.hPx);
            if (k > 0) tl.leftMargin = u(16);
            else t.edge = true;
            strip.addView(t, tl);
        }
        hs.addView(strip, new ViewGroup.LayoutParams(WC, WC));
        col.addView(hs, lp(MP, u(18 + 160 + 10)));
        return col;
    }

    // Мрежа: всички канали или една категория, по пет в ред.
    private void tvBuildGrid() {
        int n = tvChans.size();
        ArrayList<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (tvSel == TV_ALL || tvCatIdx[i] == tvSel) idx.add(i);
        }
        LinearLayout gh = new LinearLayout(this);
        gh.setOrientation(LinearLayout.HORIZONTAL);
        gh.setGravity(Gravity.CENTER_VERTICAL);
        gh.setPadding(u(24), 0, u(64), 0);
        if (tvSel == TV_ALL) {
            LinearLayout.LayoutParams ilp = lp(u(30), u(30));
            ilp.rightMargin = u(14);
            gh.addView(new IconView(this, IC_GRID, T_SKY2), ilp);
        } else {
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setColor(TV_CAT_COLORS[tvSel]);
            dg.setCornerRadius(u(5));
            dot.setBackground(dg);
            LinearLayout.LayoutParams dlp = lp(u(14), u(14));
            dlp.rightMargin = u(14);
            gh.addView(dot, dlp);
        }
        TextView title = text(tvSel == TV_ALL ? "Всички канали" : TV_CAT_NAMES[tvSel], 46, T_TEXT,
                tfCondBold, true);
        title.setIncludeFontPadding(false);
        gh.addView(title);
        TextView cnt = text(plural(idx.size(), "канал", "канала"), 19, T_TEXT3, tfMedium, true);
        cnt.setPadding(u(16), u(8), 0, 0);
        gh.addView(cnt);
        gh.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        gh.addView(tvKeyChip("◀", "категории"), lp(WC, u(38)));
        tvBox.addView(gh, lp(MP, u(56)));

        int cols = 5;
        for (int r = 0; r * cols < idx.size(); r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setPadding(u(24), 0, 0, 0);
            for (int k = 0; k < cols && r * cols + k < idx.size(); k++) {
                TvCard t = tvCardFor(idx.get(r * cols + k), 206, 110);
                LinearLayout.LayoutParams tl = lp(t.wPx, t.hPx);
                if (k > 0) tl.leftMargin = u(21);
                else t.edge = true;
                if (r == 0 && k == 0) tvMain = t;
                row.addView(t, tl);
            }
            tvBox.addView(row, lpTop(u(r == 0 ? 22 : 20)));
        }
    }

    // ---- голямата карта на „Начало“ ----

    private FrameLayout tvHeroCard;
    private TvPlate tvHeroPlate;
    private GhostNum tvHeroGhost;
    private TextView tvHeroName, tvHeroLabTxt, tvHeroCatTxt;
    private View tvHeroCatDot;
    private LinearLayout tvHeroChips;
    private TvChan tvHeroChan, tvHeroWant, tvHeroLast;

    private StateListDrawable tvHeroBg() {
        int[] cols = {0xFF16142E, 0xFF0E1026, TvTheme.SURFACE};
        GradientDrawable n = new GradientDrawable(GradientDrawable.Orientation.TL_BR, cols);
        n.setCornerRadius(u(30));
        n.setStroke(Math.max(1, u(1)), 0x408FDBFF);
        GradientDrawable f = new GradientDrawable(GradientDrawable.Orientation.TL_BR, cols);
        f.setCornerRadius(u(30));
        f.setStroke(u(3), T_SKY);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, f);
        sl.addState(new int[]{}, n);
        return sl;
    }

    private EpgLoader tvEpgLoader;
    private EpgGuide tvGuide;
    private int tvEpgGeneration;
    private String tvEpgError;
    private long tvGuideLoadedAt;
    private LinearLayout tvEpgBox, tvLiveRow;
    private ScrollView tvEpgScroll;
    private TextView tvHeroDescription, tvHeroTime;
    private ProgressBar tvHeroProgress;
    private View tvHeroWatch;
    private TvArtwork tvHeroArtwork;
    private LinearLayout tvHeroFavourite;

    private void tvEpgLoad() {
        if((!tvPanelShown() && !(tvMode && !tvVod)) || tvChans.isEmpty() || tvEpgLoader!=null)return;
        if(tvGuide!=null && SystemClock.elapsedRealtime()-tvGuideLoadedAt<60L*60*1000)return;
        final int generation=++tvEpgGeneration;
        ArrayList<String> names=new ArrayList<>();for(TvChan c:tvChans)names.add(c.name);
        tvEpgError=null;tvEpgLoader=new EpgLoader();
        tvEpgLoader.load(getCacheDir(),names,(guide,error)->ui.post(()->{
            if(generation!=tvEpgGeneration || isFinishing() || (!tvPanelShown() && !(tvMode && !tvVod)))return;
            tvEpgLoader=null;tvGuide=guide;tvGuideLoadedAt=SystemClock.elapsedRealtime();tvEpgError=error;
            if(tvOverviewGuide!=null)tvOverviewGuide.setGuide(guide,error);
            if(tvPlayerGuide!=null)tvPlayerGuide.setGuide(guide,error);
            if(tvTimeline!=null)tvTimeline.setGuide(guide);
            if(tvMode && !tvVod && tvCur!=null && tvBanner!=null && tvBanner.getVisibility()==View.VISIBLE)tvBannerRefresh();
        }));
    }

    private void tvEpgStop() {
        tvEpgGeneration++;
        if (tvEpgLoader != null) tvEpgLoader.cancel();
        tvEpgLoader = null;
        tvEpgScroll = null;
        tvGuide = null;
        tvEpgError = null;
    }

    private String tvEventTime(EpgGuide.Event event) {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        return format.format(new java.util.Date(event.start)) + " – " + format.format(new java.util.Date(event.end));
    }

    private int tvEventProgress(EpgGuide.Event e) {
        return (int) Math.max(0, Math.min(1000, (System.currentTimeMillis() - e.start) * 1000 / Math.max(1, e.end - e.start)));
    }

    private ProgressBar tvProgress() {
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(TvTheme.FOCUS));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2D3554));
        return progress;
    }

    private void tvEpgRender() {
        if (tvEpgBox == null || !tvPanelShown()) return;
        View focus = getCurrentFocus();
        String focusTag = focus != null && focus.getTag() instanceof String
                && ((String) focus.getTag()).startsWith("epg:") ? (String) focus.getTag() : null;
        int scrollY = tvEpgScroll == null ? 0 : tvEpgScroll.getScrollY();
        tvEpgScroll = null;
        tvEpgBox.removeAllViews();
        LinearLayout title = new LinearLayout(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(text("EPG", 26, TvTheme.TEXT, tfCondBold, true), new LinearLayout.LayoutParams(0, WC, 1f));
        title.addView(text("Днес · " + new java.text.SimpleDateFormat("d MMM", new Locale("bg")).format(new java.util.Date()),
                15, TvTheme.MUTED, tfMedium, true));
        tvEpgBox.addView(title, lp(MP, u(36)));
        if (tvEpgError != null) {
            TextView error = text(tvEpgError, 14, TvTheme.MUTED, tfMedium, false);
            error.setMaxLines(2);
            tvEpgBox.addView(error, lp(MP, WC));
        }
        if (tvGuide == null) {
            tvEpgBox.addView(text(tvEpgError == null ? "Зареждане на програмата…" : "Гледането не зависи от EPG.",
                    17, TvTheme.MUTED, tfMedium, false), lpTopW(MP, WC, u(24)));
            if (tvEpgError != null) tvEpgBox.addView(tvDashboardPill(IC_REFRESH, "Опитай отново", () -> { tvEpgLoad(); tvEpgRender(); }), lpTopW(MP, u(44), u(16)));
            return;
        }
        long now = System.currentTimeMillis();
        long halfHour = 30 * 60 * 1000L;
        long windowStart = now / halfHour * halfHour;
        long windowEnd = windowStart + 3 * halfHour;
        LinearLayout timeline = new LinearLayout(this);
        timeline.setPadding(u(94), u(4), 0, u(4));
        java.text.SimpleDateFormat clock = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
        for (int i = 0; i < 3; i++) timeline.addView(text(clock.format(new java.util.Date(windowStart + i * halfHour)),
                13, TvTheme.MUTED, tfMedium, true), new LinearLayout.LayoutParams(0, WC, 1f));
        tvEpgBox.addView(timeline, lp(MP, u(28)));
        ScrollView scroll = new ScrollView(this);
        tvEpgScroll = scroll;
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (TvChan c : tvChans) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setFocusable(true); row.setClickable(true); row.setLongClickable(true);
            row.setTag("epg:" + c.id);
            row.setBackground(stateBg(TvTheme.SURFACE, 0xFF232238, TvTheme.FOCUS, u(6), TvTheme.OUTLINE));
            TvPlate logo = new TvPlate(this, 6, 5, true);
            logo.setChan(c.name);
            row.addView(logo, lp(u(90), u(48)));
            LinearLayout events = new LinearLayout(this);
            long cursorTime = windowStart;
            for (EpgGuide.Event e : tvGuide.forChannel(c.name, now)) {
                if (e.start >= windowEnd) break;
                long start = Math.max(windowStart, e.start), end = Math.min(windowEnd, e.end);
                if (end <= cursorTime) continue;
                if (start > cursorTime) events.addView(new View(this), new LinearLayout.LayoutParams(0, 1, start - cursorTime));
                start = Math.max(start, cursorTime);
                LinearLayout cell = new LinearLayout(this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setPadding(u(8), u(5), u(4), u(4));
                cell.setBackgroundColor(e.start <= now && e.end > now ? 0xB3444153 : TvTheme.SURFACE);
                cell.addView(text(e.title, 14, TvTheme.TEXT, tfMedium, true));
                cell.addView(text(tvEventTime(e), 12, TvTheme.MUTED, tfMedium, true));
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, MP, end - start);
                cp.rightMargin = u(2);
                events.addView(cell, cp);
                cursorTime = end;
            }
            if (cursorTime == windowStart) {
                events.addView(text("Няма програма за този канал", 14, TvTheme.MUTED, tfMedium, true), lp(MP, WC));
            } else if (cursorTime < windowEnd) events.addView(new View(this), new LinearLayout.LayoutParams(0, 1, windowEnd - cursorTime));
            row.addView(events, new LinearLayout.LayoutParams(0, u(48), 1f));
            row.setOnClickListener(v -> tvTuneUser(c, -1));
            row.setOnLongClickListener(v -> { tvChannelMenu(c); return true; });
            row.setOnFocusChangeListener((v, focused) -> { if (focused) tvHeroShow(c); });
            rows.addView(row, lpTopW(MP, u(48), u(4)));
        }
        scroll.addView(rows);
        tvEpgBox.addView(scroll, new LinearLayout.LayoutParams(MP, 0, 1f));
        if (focusTag != null) {
            View target = findByTag(rows, focusTag);
            if (target != null) target.requestFocus();
        }
        scroll.post(() -> scroll.scrollTo(0, scrollY));
    }

    private View tvHeroBuild(TvChan channel) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(tvHeroBg());
        roundClip(card, u(22));
        // Native gradient artwork keeps the overview light; no hidden player or movie requests.
        IconView artwork = new IconView(this, IC_TV, 0x33B6B2C3);
        FrameLayout.LayoutParams art = new FrameLayout.LayoutParams(u(300), u(260), Gravity.END | Gravity.CENTER_VERTICAL);
        art.rightMargin = u(24);
        card.addView(artwork, art);
        tvHeroArtwork = new TvArtwork(this);
        card.addView(tvHeroArtwork, new FrameLayout.LayoutParams(MP, MP));
        card.addView(new ScrimView(this, false, new int[]{0xFF0D1119, 0xE60D1119, 0x260D1119},
                new float[]{0f, 0.45f, 1f}), new FrameLayout.LayoutParams(MP, MP));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(u(26), u(20), u(26), u(20));
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setGravity(Gravity.CENTER_VERTICAL);
        tvHeroPlate = new TvPlate(this, 8, 4, true);
        badgeRow.addView(tvHeroPlate, lp(u(136), u(52)));
        tvHeroLabTxt = text("НА ЖИВО", 14, Color.WHITE, tfMedium, true);
        tvHeroLabTxt.setPadding(u(10), u(6), u(10), u(6));
        tvHeroLabTxt.setBackground(stateBg(0xFFBD1737, 0xFFBD1737, 0, u(6), 0));
        badgeRow.addView(tvHeroLabTxt, lp(WC, WC));
        content.addView(badgeRow, lp(MP, u(52)));
        tvHeroName = text("", 34, TvTheme.TEXT, Typeface.DEFAULT_BOLD, true);
        content.addView(tvHeroName, lpTopW(MP, WC, u(10)));
        tvHeroTime = text("", 19, TvTheme.MUTED, tfMedium, true);
        content.addView(tvHeroTime, lpTopW(MP, WC, u(8)));
        tvHeroProgress = tvProgress();
        content.addView(tvHeroProgress, lpTopW(u(420), u(5), u(10)));
        tvHeroDescription = text("", 19, TvTheme.MUTED, tfMedium, false);
        tvHeroDescription.setMaxLines(3);
        tvHeroDescription.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(tvHeroDescription, new LinearLayout.LayoutParams(MP, 0, 1f));
        LinearLayout buttons = new LinearLayout(this);
        tvHeroWatch = tvDashboardPill(IC_PLAY, "Гледай", () -> { if (tvHeroChan != null) tvTuneUser(tvHeroChan, -1); });
        tvHeroWatch.setBackground(tvDashboardPrimaryBg(u(14)));
        tvHeroWatch.setTag("tv:watch");
        tvHeroWatch.setOnLongClickListener(v -> { if (tvHeroChan != null) tvChannelMenu(tvHeroChan); return true; });
        buttons.addView(tvHeroWatch, lp(WC, u(54)));
        LinearLayout.LayoutParams pl = lp(WC, u(54)); pl.leftMargin = u(12);
        buttons.addView(tvDashboardPill(IC_BARS, "Програма", () -> tvShowProgramme(tvHeroChan)), pl);
        LinearLayout.LayoutParams ml = lp(WC, u(54)); ml.leftMargin = u(12);
        tvHeroFavourite = tvDashboardPill(IC_STAR, "Добави в любими", () -> {
            if (tvHeroChan == null) return;
            String key = "tvfav:" + tvHeroChan.id;
            prefs.edit().putBoolean(key, !prefs.getBoolean(key, false)).apply();
            tvHeroApply(tvHeroChan);
        });
        tvHeroFavourite.setOnLongClickListener(v -> { if (tvHeroChan != null) tvChannelMenu(tvHeroChan); return true; });
        buttons.addView(tvHeroFavourite, ml);
        content.addView(buttons, lpTopW(MP, u(54), u(12)));
        card.addView(content, new FrameLayout.LayoutParams(MP, MP));
        tvHeroCard = card;
        tvHeroApply(channel);
        return card;
    }

    private void tvShowProgramme(TvChan channel) {
        if (channel == null) return;
        StringBuilder lines = new StringBuilder();
        if (tvGuide != null) for (EpgGuide.Event e : tvGuide.forChannel(channel.name, System.currentTimeMillis())) {
            lines.append(tvEventTime(e)).append("  ").append(e.title).append("\n\n");
        }
        showInfo("Програма · " + channel.name, lines.length() == 0 ? "Няма налична програма за този канал." : lines.toString());
    }

    // Картата показва канала на фокус; смяната се изчаква малко, за да не се бави върху бързо въртене.
    private final Runnable tvHeroRun = new Runnable() {
        @Override
        public void run() {
            if (tvHeroWant != null && tvPanelShown()) tvHeroApply(tvHeroWant);
        }
    };

    private void tvHeroShow(TvChan c) {
        tvHeroWant = c;
        ui.removeCallbacks(tvHeroRun);
        ui.postDelayed(tvHeroRun, 130);
    }

    private void tvHeroApply(TvChan c) {
        if (c == null || tvHeroCard == null) return;
        tvHeroChan = c;
        if (tvHeroFavourite != null) {
            ((TextView) tvHeroFavourite.getChildAt(1)).setText(prefs.getBoolean("tvfav:" + c.id, false)
                    ? "В любими" : "Добави в любими");
        }
        tvHeroPlate.setChan(c.name);
        long now = System.currentTimeMillis();
        EpgGuide.Event event = tvGuide == null ? null : tvGuide.current(c.name, now);
        if (tvHeroArtwork != null) tvHeroArtwork.show(event == null ? "" : event.image);
        tvHeroName.setText(event == null ? c.name : event.title);
        tvHeroTime.setText(event == null ? c.name + " · Телевизия на живо" : tvEventTime(event)
                + "  ·  Остават " + Math.max(1, (event.end - now + 59999) / 60000) + " мин");
        tvHeroDescription.setText(event == null
                ? "Избери Гледай, за да пуснеш канала. Програмата се зарежда отделно от видеото."
                : event.description.isEmpty() ? c.name : event.description);
        tvHeroProgress.setProgress(event == null ? 0 : tvEventProgress(event));
    }

    private CharSequence tvEmph(String text, String... bold) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(text);
        for (String b : bold) {
            int i = text.indexOf(b);
            if (i < 0) continue;
            sb.setSpan(new android.text.style.ForegroundColorSpan(T_TEXT), i, i + b.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), i, i + b.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private View tvStepRow(int n, CharSequence msg) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u(26), 0, u(26), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(T_SURF);
        g.setCornerRadius(u(20));
        g.setStroke(Math.max(1, u(1.5f)), T_LINE);
        r.setBackground(g);
        TextView num = text(String.valueOf(n), 21, T_SKY2, tfCondBold, true);
        num.setGravity(Gravity.CENTER);
        GradientDrawable ng = new GradientDrawable();
        ng.setShape(GradientDrawable.OVAL);
        ng.setColor(0x2938BDF8);
        ng.setStroke(Math.max(1, u(1.5f)), T_SKY);
        num.setBackground(ng);
        LinearLayout.LayoutParams nlp = lp(u(38), u(38));
        nlp.rightMargin = u(20);
        r.addView(num, nlp);
        TextView t = text("", 21, T_TEXT2, tfMedium, true);
        t.setText(msg);
        r.addView(t, new LinearLayout.LayoutParams(0, WC, 1f));
        return r;
    }

    private void tvFillEmpty() {
        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER_HORIZONTAL);
        mid.setClipChildren(false);

        FrameLayout big = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6FD3FF, 0xFF4F46E5});
        bg.setCornerRadius(u(36));
        big.setBackground(bg);
        big.addView(new IconView(this, IC_TV, T_INK),
                new FrameLayout.LayoutParams(u(66), u(66), Gravity.CENTER));
        mid.addView(big, lp(u(128), u(128)));

        TextView title = text("Събери каналите си на едно място", 56, T_TEXT, tfCondBold, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = lp(MP, WC);
        tl.topMargin = u(24);
        mid.addView(title, tl);

        LinearLayout steps = new LinearLayout(this);
        steps.setOrientation(LinearLayout.VERTICAL);
        steps.addView(tvStepRow(1, "Отвори сайт с телевизия и пусни канал"), lp(MP, u(70)));
        LinearLayout.LayoutParams s2 = lp(MP, u(70));
        s2.topMargin = u(14);
        steps.addView(tvStepRow(2, tvEmph("Задръж OK и в „Видео на страницата“ избери В ТВ",
                "OK", "В ТВ")), s2);
        LinearLayout.LayoutParams s3 = lp(MP, u(70));
        s3.topMargin = u(14);
        steps.addView(tvStepRow(3, "Дай име. Същият канал от друг сайт става резервен"), s3);
        LinearLayout.LayoutParams sl = lp(u(820), WC);
        sl.topMargin = u(30);
        mid.addView(steps, sl);

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setClipChildren(false);
        ArrayList<String[]> tsites = tvSites();
        for (int i = 0; i < tsites.size() && i < 3; i++) {
            final String[] site = tsites.get(i);
            View b;
            if (i == 0) {
                b = tvPrimary(IC_PLAY, "Отвори " + site[0], 72, 22, new Runnable() {
                    @Override
                    public void run() {
                        tvOpenSite(site);
                    }
                });
                tvMain = b;
            } else {
                b = tvPill(-1, site[0], new Runnable() {
                    @Override
                    public void run() {
                        tvOpenSite(site);
                    }
                });
            }
            LinearLayout.LayoutParams bl = lp(WC, u(72));
            if (i > 0) bl.leftMargin = u(14);
            btns.addView(b, bl);
        }
        LinearLayout.LayoutParams bll = lp(WC, u(72));
        bll.topMargin = u(30);
        mid.addView(btns, bll);
        tvBox.addView(mid, lp(MP, WC));
    }

    // Отваря сайт, за да се запази канал от него. При "Назад" се връща към този екран.
    private void tvOpenSite(String[] site) {
        tvHidePanel();
        openSite(site);
        catReturn = new Runnable() {
            @Override
            public void run() {
                tvShowPanel();
            }
        };
        Toast.makeText(this, "Пусни канал, задръж OK и в „Видео на страницата“ избери „В ТВ“",
                Toast.LENGTH_LONG).show();
    }

    // ---- прозорчета (запазване, преименуване, източници) ----

    private Dialog tvDialog;

    private void tvDialogClose() {
        Dialog d = tvDialog;
        tvDialog = null;
        if (d != null) {
            try {
                d.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // Карта на прозорче: заглавие и по желание пояснение под него.
    private LinearLayout tvCard(String title, String sub) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(u(40), u(32), u(40), u(34));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF15264A, 0xFF0A1226});
        bg.setCornerRadius(u(30));
        bg.setStroke(Math.max(1, u(1)), 0x408FDBFF);
        card.setBackground(bg);
        card.addView(text(title, 42, T_TEXT, tfCondBold, true));
        if (sub != null) {
            TextView s = text(sub, 18, T_TEXT2, tfMedium, false);
            s.setPadding(0, u(8), 0, 0);
            card.addView(s);
        }
        return card;
    }

    private void tvDialogShow(LinearLayout card, int widthUnits, final View focus) {
        if (isFinishing()) return;
        tvDialogClose();
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(card);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(u(widthUnits), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                if (tvDialog == dlg) tvDialog = null;
                hideSystemBars();
            }
        });
        tvDialog = dlg;
        dlg.show();
        if (focus != null) focus.requestFocus();
    }

    // Ред за избор в прозорче. style: 0 обикновен, 1 главен (светлосин), 2 опасен (червеникав).
    private LinearLayout tvChoice(String num, String label, String right, int style,
                                  final Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u(24), 0, u(24), 0);
        r.setFocusable(true);
        r.setClickable(true);
        if (style == 1) {
            r.setBackground(tvPrimaryBg(u(20)));
        } else if (style == 2) {
            r.setBackground(stateBg(0x1AFB7185, 0x33FB7185, Color.WHITE, u(20), 0x33FB7185));
        } else {
            r.setBackground(stateBg(0x0FFFFFFF, T_SURF2, Color.WHITE, u(20), 0x1AFFFFFF));
        }
        int fg = style == 1 ? T_INK : style == 2 ? 0xFFFDA4AF : T_TEXT;
        if (num != null) {
            r.addView(text(num, 30, style == 1 ? T_INK : T_SKY2, tfCondBold, true),
                    lp(u(44), WC));
        }
        r.addView(text(label, 28, fg, tfCondBold, true),
                new LinearLayout.LayoutParams(0, WC, 1f));
        if (right != null) {
            TextView rt = text(right, 16, style == 1 ? 0xAA04121F : T_TEXT3, tfMedium, true);
            LinearLayout.LayoutParams rl = lp(WC, WC);
            rl.leftMargin = u(14);
            r.addView(rt, rl);
        }
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return r;
    }

    // Прозорче с поле за име: Запази / Отказ.
    private interface TvNameCb {
        void done(String name);
    }

    private void tvAskName(String title, String sub, String initial, String okLabel,
                           final TvNameCb cb) {
        LinearLayout card = tvCard(title, sub);
        final EditText et = new EditText(this);
        et.setBackground(stateBg(0x14FFFFFF, 0x1FFFFFFF, T_SKY, u(18), 0x40FFFFFF));
        et.setPadding(u(22), u(10), u(22), u(10));
        et.setText(initial);
        et.setSelection(et.getText().length());
        et.setSingleLine(true);
        et.setHint("Име на канала");
        et.setTextColor(T_TEXT);
        et.setHintTextColor(T_TEXT3);
        ts(et, 28);
        et.setTypeface(tfCondBold);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        card.addView(et, lpTopW(MP, u(68), u(22)));

        final Runnable ok = new Runnable() {
            @Override
            public void run() {
                String name = oneLine(et.getText().toString());
                if (name.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Напиши име на канала", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (name.length() > 40) name = name.substring(0, 40).trim();
                tvDialogClose();
                cb.done(name);
            }
        };
        et.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent ev) {
                ok.run();
                return true;
            }
        });
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setClipChildren(false);
        LinearLayout save = tvPrimary(IC_CHECK, okLabel, 64, 22, ok);
        btns.addView(save, lp(WC, u(64)));
        LinearLayout.LayoutParams cl = lp(WC, u(64));
        cl.leftMargin = u(14);
        btns.addView(tvPill(-1, "Отказ", new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
            }
        }), cl);
        card.addView(btns, lpTopW(WC, u(64), u(22)));
        tvDialogShow(card, 760, save);
    }

    // ---- запазване на канал от страница (бутон "В ТВ" в опциите) ----

    private View tvSaveBtn(final String url) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(u(16), 0, u(18), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setTag("#tvsave:" + url);
        b.setBackground(stateBg(0xFF0C3550, 0xFF124B73, Color.WHITE, u(18), 0xFF1E5A85));
        LinearLayout.LayoutParams ilp = lp(u(22), u(22));
        ilp.rightMargin = u(8);
        b.addView(new IconView(this, IC_TV, TvTheme.TEXT), ilp);
        b.addView(text("В ТВ", 17, Color.WHITE, Typeface.DEFAULT_BOLD, true));
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runOption("#tvsave:" + url);
            }
        });
        b.setOnFocusChangeListener(focusAnim);
        return b;
    }

    // Ред във "Видео на страницата": потокът, бутон "Пусни" и бутон "В ТВ".
    private View mediaLine(String url, String label) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setClipChildren(false);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(0, WC, 1f);
        rl.topMargin = u(8);
        line.addView(mediaRow(url, label), rl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(WC, MP);
        bl.topMargin = u(8);
        bl.leftMargin = u(10);
        line.addView(tvSaveBtn(url), bl);
        return line;
    }

    private void tvSaveFlow(final String streamUrl) {
        if (web == null) return;
        tvLoad();
        HashMap<String, String> h;
        synchronized (mediaPlaylists) {
            h = mediaPlaylists.get(streamUrl);
        }
        if (h == null) {
            synchronized (mediaFiles) {
                h = mediaFiles.get(streamUrl);
            }
        }
        final TvSrc src = new TvSrc();
        src.stream = streamUrl;
        if (h != null) {
            src.ref = nz(h.get("Referer"));
            src.org = nz(h.get("Origin"));
            src.ua = nz(h.get("User-Agent"));
        }
        if (src.ua.isEmpty()) src.ua = nz(web.getSettings().getUserAgentString());
        String cur = web.getUrl();
        if (cur == null || !cur.startsWith("http")) cur = pageUrl != null ? pageUrl : "";
        src.page = cur;
        src.site = curSiteName.isEmpty() ? (cur.isEmpty() ? "Сайт" : hostOf(cur)) : curSiteName;
        src.mode = curMode;
        final String guess = tvGuessName(web.getTitle(), cur.isEmpty() ? null : Uri.parse(cur));

        LinearLayout card = tvCard("Запази в Моята телевизия", null);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(u(22), 0, u(22), 0);
        GradientDrawable ig = new GradientDrawable();
        ig.setColor(0x0FFFFFFF);
        ig.setCornerRadius(u(18));
        ig.setStroke(Math.max(1, u(1)), 0x1AFFFFFF);
        info.setBackground(ig);
        info.addView(text("Сайт ", 18, T_TEXT2, tfMedium, true));
        info.addView(text(src.site, 18, T_TEXT, Typeface.DEFAULT_BOLD, true));
        info.addView(text("   ·   поток ", 18, T_TEXT2, tfMedium, true));
        info.addView(text(shortName(streamUrl), 18, T_TEXT, Typeface.DEFAULT_BOLD, true),
                new LinearLayout.LayoutParams(0, WC, 1f));
        card.addView(info, lpTopW(MP, u(64), u(16)));

        TextView lab = text("Към кой канал да се добави?", 18, T_TEXT3, tfMedium, true);
        lab.setPadding(0, u(22), 0, u(10));
        card.addView(lab);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout newRow = tvChoice("+", "Нов канал", null, 1, new Runnable() {
            @Override
            public void run() {
                tvAskName("Име на канала", "Така ще се вижда в списъка", guess, "Запази",
                        new TvNameCb() {
                            @Override
                            public void done(String name) {
                                tvStoreSource(null, name, src);
                            }
                        });
            }
        });
        list.addView(newRow, lp(MP, u(62)));
        for (int i = 0; i < tvChans.size(); i++) {
            final TvChan c = tvChans.get(i);
            list.addView(tvChoice(String.valueOf(i + 1), c.name,
                    plural(c.srcs.size(), "източник", "източника"), 0, new Runnable() {
                        @Override
                        public void run() {
                            tvDialogClose();
                            tvStoreSource(c, c.name, src);
                        }
                    }), lpTopW(MP, u(62), u(10)));
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new ViewGroup.LayoutParams(MP, WC));
        int hh = Math.min(u(340), u(72) * (tvChans.size() + 1));
        card.addView(sv, lp(MP, hh));
        tvDialogShow(card, 840, newRow);
    }

    // Записва източника в канала. Същото име = същият канал; същият сайт = заменя стария източник.
    // Избор на качество за родния плеър: оригинал или най-много 720p, 576p, 480p.
    private void tvQualityDialog() {
        LinearLayout card = tvCard("Качество на видеото",
                "По-ниското качество е по-леко за кутията. Важи за всички канали и филми.");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        int cur = videoQuality();
        for (int q = 0; q < 4; q++) {
            final int qq = q;
            LinearLayout row = tvChoice(null, q == 0 ? "Оригинал" : "До " + (q == 1 ? "720p"
                    : q == 2 ? "576p" : "480p"), q == cur ? "● избрано" : null,
                    q == cur ? 1 : 0, new Runnable() {
                        @Override
                        public void run() {
                            prefs.edit().putInt("vq", qq).apply();
                            applyQuality();
                            tvDialogClose();
                            Toast.makeText(MainActivity.this, "Качество: " + qualityName(qq),
                                    Toast.LENGTH_SHORT).show();
                            if (optBox != null && logShown() && !domShown()) buildOptions();
                        }
                    });
            list.addView(row, lpTopW(MP, u(62), q == 0 ? 0 : u(10)));
            if (q == cur) first = row;
        }
        if (first == null) first = list.getChildAt(0);
        card.addView(list, lpTopW(MP, WC, u(24)));
        tvDialogShow(card, 700, first);
    }

    private static boolean samePage(String a, String b) {
        return trimPage(a).equals(trimPage(b));
    }

    private static String trimPage(String u) {
        String s = u == null ? "" : u;
        int h = s.indexOf('#');
        if (h >= 0) s = s.substring(0, h);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.toLowerCase(Locale.ROOT);
    }

    private void tvStoreSource(TvChan c, String name, TvSrc src) {
        if (c == null) c = tvFindByName(name);
        boolean created = false;
        if (c == null) {
            c = new TvChan();
            c.id = Long.toString(System.currentTimeMillis(), 36) + tvChans.size();
            c.name = name;
            tvChans.add(c);
            created = true;
        }
        boolean replaced = false;
        boolean extra = false;
        for (int i = 0; i < c.srcs.size(); i++) {
            TvSrc old = c.srcs.get(i);
            if (old.site.equalsIgnoreCase(src.site)) {
                if (!old.stream.isEmpty() && !src.stream.isEmpty()
                        && !old.stream.equals(src.stream)) {
                    // Същата страница, друг поток: пази се като резервен за този източник.
                    boolean have = false;
                    for (String[] al : old.alts) {
                        if (al[0].equals(src.stream)) have = true;
                    }
                    if (!have) {
                        if (old.alts.size() >= TV_MAX_ALTS) old.alts.remove(0);
                        old.alts.add(new String[]{src.stream, src.ref, src.org, src.ua, "0"});
                    }
                    extra = true;
                } else {
                    src.alts.addAll(old.alts);
                    src.alts.remove(null);
                    c.srcs.set(i, src);
                }
                replaced = true;
                break;
            }
        }
        if (!replaced) c.srcs.add(src);
        tvSave();
        String msg = created ? "Запазено: „" + c.name + "“ е канал " + tvChans.size()
                : extra ? "Добавен резервен поток към " + src.site + " на „" + c.name + "“"
                : replaced ? "Обновен източник " + src.site + " на „" + c.name + "“"
                : "Добавен източник " + src.site + " към „" + c.name + "“";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        logEvent("ТВ: " + msg);
    }

    // ---- промяна на канал (задържане на OK върху канал) ----

    private void tvChannelMenu(final TvChan c) {
        final int idx = tvChans.indexOf(c);
        if (idx < 0) return;
        LinearLayout card = tvCard(c.name, plural(c.srcs.size(), "източник", "източника")
                + ": " + tvSrcNames(c));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout first = tvChoice(null, "Преименувай", null, 0, new Runnable() {
            @Override
            public void run() {
                tvAskName("Преименувай канала", null, c.name, "Запази", new TvNameCb() {
                    @Override
                    public void done(String name) {
                        TvChan other = tvFindByName(name);
                        if (other != null && other != c) {
                            Toast.makeText(MainActivity.this, "Вече има канал с това име",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        c.name = name;
                        tvSave();
                        if (tvPanelShown()) tvFill(c.id);
                    }
                });
            }
        });
        list.addView(first, lp(MP, u(62)));
        if (idx > 0) {
            list.addView(tvChoice(null, "Премести по-горе", "ще е канал " + idx, 0, new Runnable() {
                @Override
                public void run() {
                    tvDialogClose();
                    Collections.swap(tvChans, idx, idx - 1);
                    tvSave();
                    if (tvPanelShown()) tvFill(c.id);
                }
            }), lpTopW(MP, u(62), u(10)));
        }
        if (idx < tvChans.size() - 1) {
            list.addView(tvChoice(null, "Премести по-долу", "ще е канал " + (idx + 2), 0,
                    new Runnable() {
                        @Override
                        public void run() {
                            tvDialogClose();
                            Collections.swap(tvChans, idx, idx + 1);
                            tvSave();
                            if (tvPanelShown()) tvFill(c.id);
                        }
                    }), lpTopW(MP, u(62), u(10)));
        }
        list.addView(tvChoice(null, "Обнови потоците", "пуска канала и търси още", 0, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                tvMoreChan = c;
                tvTuneUser(c, -1);
            }
        }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Източници", String.valueOf(c.srcs.size()), 0, new Runnable() {
            @Override
            public void run() {
                tvSourcesDialog(c);
            }
        }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Изтрий канала", null, 2, new Runnable() {
            @Override
            public void run() {
                tvConfirmDelete(c);
            }
        }), lpTopW(MP, u(62), u(10)));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new ViewGroup.LayoutParams(MP, WC));
        card.addView(sv, lpTopW(MP, Math.min(u(380), u(72) * 5), u(24)));
        tvDialogShow(card, 760, first);
    }

    private void tvConfirmDelete(final TvChan c) {
        LinearLayout card = tvCard("Да изтрия ли „" + c.name + "“?",
                "Каналът и запазените му източници ще се премахнат от списъка.");
        LinearLayout no = tvChoice(null, "Не, остави го", null, 1, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
            }
        });
        card.addView(no, lpTopW(MP, u(62), u(24)));
        card.addView(tvChoice(null, "Изтрий", null, 2, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                tvChans.remove(c);
                tvSave();
                if (tvPanelShown()) tvFill(null);
            }
        }), lpTopW(MP, u(62), u(10)));
        tvDialogShow(card, 760, no);
    }

    private void tvSourcesDialog(final TvChan c) {
        LinearLayout card = tvCard("Източници на „" + c.name + "“",
                "Първият се пробва първи. Ако не тръгне, се минава на следващия.");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        for (int i = 0; i < c.srcs.size(); i++) {
            final int si = i;
            TvSrc s = c.srcs.get(i);
            String right = (i == 0 ? "основен" : "резервен") + " · "
                    + (s.stream.isEmpty() ? "без поток" : "с поток");
            LinearLayout r = tvChoice(String.valueOf(i + 1), s.site, right, 0, new Runnable() {
                @Override
                public void run() {
                    tvSourceMenu(c, si);
                }
            });
            if (first == null) first = r;
            list.addView(r, lpTopW(MP, u(62), i == 0 ? 0 : u(10)));
        }
        LinearLayout back = tvChoice(null, "Готово", null, 1, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                if (tvPanelShown()) tvFill(c.id);
            }
        });
        list.addView(back, lpTopW(MP, u(62), u(18)));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new ViewGroup.LayoutParams(MP, WC));
        card.addView(sv, lpTopW(MP, Math.min(u(400), u(72) * (c.srcs.size() + 1) + u(8)), u(24)));
        tvDialogShow(card, 760, first != null ? first : back);
    }

    private void tvSourceMenu(final TvChan c, final int i) {
        if (i < 0 || i >= c.srcs.size()) return;
        TvSrc s = c.srcs.get(i);
        LinearLayout card = tvCard(s.site, s.stream.isEmpty() ? "Само страница, без записан поток"
                : "Поток: " + shortName(s.stream)
                + (s.alts.isEmpty() ? "" : "  ·  още " + s.alts.size() + " резервни"));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        if (!s.stream.isEmpty()) {
            for (int k = -1; k < s.alts.size(); k++) {
                final int kk = k;
                String u = k < 0 ? s.stream : s.alts.get(k)[0];
                LinearLayout row = tvChoice(null, (k < 0 ? "Основен: " : "Резервен " + (k + 1) + ": ")
                        + shortName(u), tvStreamActive(c, i, k) ? "● пуска се" : null,
                        first == null ? 1 : 0, new Runnable() {
                    @Override
                    public void run() {
                        tvStreamMenu(c, i, kk);
                    }
                });
                list.addView(row, lpTopW(MP, u(62), first == null ? 0 : u(10)));
                if (first == null) first = row;
            }
        }
        if (i > 0) {
            LinearLayout mk = tvChoice(null, "Направи основен", null, 1, new Runnable() {
                @Override
                public void run() {
                    TvSrc x = c.srcs.remove(i);
                    c.srcs.add(0, x);
                    tvSave();
                    tvSourcesDialog(c);
                }
            });
            list.addView(mk, lpTopW(MP, u(62), first == null ? 0 : u(10)));
            if (first == null) first = mk;
        }
        if (c.srcs.size() > 1) {
            LinearLayout del = tvChoice(null, "Изтрий източника", null, 2, new Runnable() {
                @Override
                public void run() {
                    c.srcs.remove(i);
                    tvSave();
                    tvSourcesDialog(c);
                }
            });
            if (first == null) first = del;
            list.addView(del, lpTopW(MP, u(62), first == del ? 0 : u(10)));
        }
        LinearLayout back = tvChoice(null, "Назад", null, 0, new Runnable() {
            @Override
            public void run() {
                tvSourcesDialog(c);
            }
        });
        if (first == null) first = back;
        list.addView(back, lpTopW(MP, u(62), first == back ? 0 : u(10)));
        ScrollView ssv = new ScrollView(this);
        ssv.setVerticalScrollBarEnabled(false);
        ssv.addView(list, new ViewGroup.LayoutParams(MP, WC));
        card.addView(ssv, lpTopW(MP, Math.min(u(440), u(72) * (s.alts.size() + 4)), u(24)));
        tvDialogShow(card, 760, first);
    }

    private boolean tvStreamActive(TvChan c, int i, int k) {
        return tvMode && tvPlaying && tvCur == c && tvSrcNowIdx() == i && tvStreamNowIdx() == k + 1;
    }

    // Един поток на източника: пускане, повишаване, изтриване.
    private void tvStreamMenu(final TvChan c, final int i, final int k) {
        if (i < 0 || i >= c.srcs.size()) return;
        final TvSrc s = c.srcs.get(i);
        if (k >= s.alts.size()) {
            tvSourceMenu(c, i);
            return;
        }
        String u = k < 0 ? s.stream : s.alts.get(k)[0];
        LinearLayout card = tvCard(k < 0 ? "Основен поток" : "Резервен поток " + (k + 1),
                s.site + "  ·  " + shortName(u));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout play = tvChoice(null, "Пусни този поток", null, 1, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                tvTuneStream(c, i, k);
            }
        });
        list.addView(play, lp(MP, u(62)));
        if (k >= 0) {
            list.addView(tvChoice(null, "Направи основен", null, 0, new Runnable() {
                @Override
                public void run() {
                    String[] al = s.alts.get(k);
                    s.alts.set(k, new String[]{s.stream, s.ref, s.org, s.ua, "0"});
                    s.stream = al[0];
                    s.ref = al[1];
                    s.org = al[2];
                    s.ua = al[3];
                    s.fails = 0;
                    tvSave();
                    tvSourceMenu(c, i);
                }
            }), lpTopW(MP, u(62), u(10)));
        }
        list.addView(tvChoice(null, "Изтрий този поток", null, 2, new Runnable() {
            @Override
            public void run() {
                if (k >= 0) {
                    s.alts.remove(k);
                } else if (!s.alts.isEmpty()) {
                    String[] al = s.alts.remove(0);
                    s.stream = al[0];
                    s.ref = al[1];
                    s.org = al[2];
                    s.ua = al[3];
                } else {
                    s.stream = "";
                    s.ref = "";
                    s.org = "";
                    s.ua = "";
                }
                s.fails = 0;
                tvSave();
                tvSourceMenu(c, i);
            }
        }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Назад", null, 0, new Runnable() {
            @Override
            public void run() {
                tvSourceMenu(c, i);
            }
        }), lpTopW(MP, u(62), u(10)));
        card.addView(list, lpTopW(MP, WC, u(24)));
        tvDialogShow(card, 760, play);
    }

    // "Добави от сайт": избор на сайт, който да се отвори.
    private void tvAddFromSite() {
        LinearLayout card = tvCard("Добави и провери",
                "Или отвори сайт, пусни канал, задръж OK и в „Видео на страницата“ избери „В ТВ“.");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = tvManageRows(list);
        for (final String[] site : tvSites()) {
            LinearLayout r = tvChoice(null, site[0], hostOf(site[1]), first == null ? 1 : 0,
                    new Runnable() {
                        @Override
                        public void run() {
                            tvDialogClose();
                            tvOpenSite(site);
                        }
                    });
            list.addView(r, lpTopW(MP, u(62), first == null ? 0 : u(10)));
            if (first == null) first = r;
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new ViewGroup.LayoutParams(MP, WC));
        card.addView(sv, lpTopW(MP, Math.min(u(440), u(72) * (Math.max(1, tvSites().size()) + 3)), u(24)));
        tvDialogShow(card, 720, first);
    }

    // ---- пускане на канал, смяна на канали ----

    private boolean tvMode;                 // гледаме или търсим канал
    private TvChan tvCur;                   // текущият канал
    private int tvStepGen;                  // номер на стъпката: закъснели събития се пренебрегват
    private int tvTryGen;                   // номер на опита с плеъра
    private final ArrayList<int[]> tvPlan = new ArrayList<>(); // {източник, вид}
    private int tvStep = -1;                // коя стъпка от плана върви
    private int tvFirstSrc;                 // кой източник е избран първи
    private boolean tvPlaying;              // има картина
    private boolean tvTrying;               // плеърът се пробва в момента
    private String tvTryUrl;
    private volatile boolean tvCapturing;   // скритата страница търси поток
    private int tvCapSrc;
    private int tvPokeN;
    private int tvRestarts;                 // колко пъти поред връзката се е скъсала
    private int tvSwitches;                 // колко пъти сме сменяли източника заради скъсване
    private long tvPlayedAt;
    private int tvZapIdx = -1;              // канал, към който се превключва
    private final HashSet<String> tvBad = new HashSet<>();
    private Player.Listener tvLis;

    // Едно гледане = от избора на канала до следващия избор от потребителя. В него потоците, които
    // не тръгнаха или заседнаха, не се пробват пак, а търсенето в един сайт е най-много два пъти.
    private final HashSet<String> tvSessBad = new HashSet<>();
    private final HashMap<Integer, Integer> tvCapMap = new HashMap<>();
    private static final int TV_CAP_MAX = 2;               // търсения по страницата на сайт за едно гледане
    private static final int TV_MAX_RESTARTS = 8;          // най-много рестарти за едно гледане
    private static final long TV_STABLE_MS = 45000;        // толкова добра картина = потокът е здрав
    private static final long TV_SAVED_START_MS = 8000;    // колко се чака запазен поток да тръгне
    private static final long TV_STALL_MS = 10000;         // колко се търпи заседнала картина (на живо)
    private static final long TV_FAIL_MEMORY = 6 * 3600 * 1000L; // толкова време сайтът с неуспех се пробва последен
    private String tvStableUrl = "";
    private int tvStableSi = -1;

    private int tvCapUsed(int si) {
        Integer v = tvCapMap.get(si);
        return v == null ? 0 : v;
    }

    private boolean tvIsBad(String u) {
        return tvBad.contains(u) || tvSessBad.contains(u);
    }

    // Източник без пресен неуспех: нито заседнал поток скоро, нито провал на проверката.
    private static boolean tvSrcSound(TvSrc s) {
        if (s.bad > 0) return false;
        return s.fails == 0 || System.currentTimeMillis() - s.failAt > TV_FAIL_MEMORY;
    }

    // Скритата страница се "бутва" да пусне видеото: пуска <video> без звук и натиска големия бутон
    // за пускане, но само ако е върху самия плеър (не бутоните на другите канали в страницата).
    private static final String TV_PLAY_JS =
            "(function(){function go(d,n){try{"
                    + "var vs=d.querySelectorAll('video');"
                    + "for(var i=0;i<vs.length;i++){var v=vs[i];try{v.muted=true;var p=v.play();"
                    + "if(p&&p.catch){p.catch(function(){});}}catch(e){}}"
                    + "var R=null,ba=0,pl=d.querySelectorAll('video,iframe');"
                    + "for(var k=0;k<pl.length;k++){var q=pl[k].getBoundingClientRect(),"
                    + "a=q.width*q.height;if(a>ba){ba=a;R=q;}}"
                    + "if(R){var bs=d.querySelectorAll('.vjs-big-play-button,.jw-icon-display,"
                    + ".jw-display-icon-container,.plyr__control--overlaid,.ytp-large-play-button,"
                    + "[aria-label=\"Play\"],[title=\"Play\"]'),c=0;"
                    + "for(var j=0;j<bs.length&&c<2;j++){var r=bs[j].getBoundingClientRect(),"
                    + "x=(r.left+r.right)/2,y=(r.top+r.bottom)/2;"
                    + "if(r.width>4&&r.height>4&&x>=R.left&&x<=R.right&&y>=R.top&&y<=R.bottom)"
                    + "{bs[j].click();c++;}}}"
                    + "if(n<3){var fr=d.querySelectorAll('iframe');"
                    + "for(var m=0;m<fr.length;m++){try{if(fr[m].contentDocument)"
                    + "go(fr[m].contentDocument,n+1);}catch(e){}}}"
                    + "}catch(e){}}go(document,0);})()";

    private int tvSrcNowIdx() {
        if (tvStep >= 0 && tvStep < tvPlan.size()) return tvPlan.get(tvStep)[0];
        return tvFirstSrc;
    }

    private String tvLine() {
        TvChan c = tvCur;
        if (c == null || c.srcs.isEmpty()) return "";
        int i = tvSrcNowIdx();
        if (i < 0 || i >= c.srcs.size()) i = 0;
        String r = c.srcs.get(i).site + "  ·  сайт " + (i + 1) + " от " + c.srcs.size();
        int m = tvStreamCount();
        if (m > 1) r += "  ·  поток " + (tvStreamNowIdx() + 1) + " от " + m;
        return r;
    }

    // Слоевете (курсор, родният слой и др.) се правят само веднъж. Нужни са и без страница.
    private void ensureLayers() {
        if (cursor != null) return;
        cursor = new CursorView(this);
        root.addView(cursor, new FrameLayout.LayoutParams(CURSOR_SIZE, CURSOR_SIZE));
        buildLoadingUi();
        buildLogPanel();
        buildNativeLayer();
        buildStatsView();
    }

    private static int tvAltFails(String[] al) {
        try {
            return Integer.parseInt(al[4]);
        } catch (Exception e) {
            return 0;
        }
    }

    private int[] tvForce;
    private boolean tvPinned;   // {източник, -1 = основен поток | номер на резервния}

    // Пуска точно определен поток на източника (k = -1 основен, 0.. резервните).
    private void tvTuneStream(TvChan c, int src, int k) {
        tvForce = new int[]{src, k};
        tvTuneUser(c, src);
    }

    // Потоците на текущия източник: основният и резервните.
    // Кой поток на източника е в ход: 0 = основният, 1.. = резервните.
    private int tvStreamNowIdx() {
        if (tvCur == null) return 0;
        int i = tvSrcNowIdx();
        if (i < 0 || i >= tvCur.srcs.size()) return 0;
        TvSrc s = tvCur.srcs.get(i);
        String now = nz(tvTryUrl);
        for (int k = 0; k < s.alts.size(); k++) {
            if (s.alts.get(k)[0].equals(now)) return k + 1;
        }
        return 0;
    }

    private int tvStreamCount() {
        if (tvCur == null) return 0;
        int i = tvSrcNowIdx();
        if (i < 0 || i >= tvCur.srcs.size()) return 0;
        TvSrc s = tvCur.srcs.get(i);
        return s.stream.isEmpty() ? 0 : 1 + s.alts.size();
    }

    // ◀: следващ поток на същия сайт. Ако няма друг, сайтът се търси за още потоци (само сега, при нужда).
    private void tvSwitchStream(int dir) {
        TvChan c = tvCur;
        int m = tvStreamCount();
        if (c == null) return;
        if (m < 2) {
            tvMoreStart(true);
            return;
        }
        int i = tvSrcNowIdx();
        TvSrc s = c.srcs.get(i);
        int idx = 0;
        String now = nz(tvTryUrl);
        for (int k = 0; k < s.alts.size(); k++) {
            if (s.alts.get(k)[0].equals(now)) idx = k + 1;
        }
        int next = ((idx + dir) % m + m) % m;
        tvShowBanner(2500, "Поток " + (next + 1) + " от " + m);
        tvTuneStream(c, i, next - 1);
    }

    // Избор на канал от потребителя (клик, смяна на източник): започва отначало.
    private void tvTuneUser(TvChan c, int firstSrc) {
        tvRestarts = 0;
        tvSwitches = 0;
        tvSessBad.clear();
        tvCapMap.clear();
        tvSearched.clear();
        if (tvMoreChan != null && tvMoreChan != c) tvMoreChan = null;
        tvTune(c, firstSrc);
    }

    // Пуска канала. firstSrc е източникът, с който да започне (-1 = основният).
    private void tvTune(TvChan c, int firstSrc) {
        tvCloseTimeline();
        if (c == null || c.srcs.isEmpty() || isFinishing()) return;
        ensureLayers();
        tvBuildOsd();
        if (!tvMode && player != null) closeNative(false); // чужд плеър (не е на телевизията)
        ui.removeCallbacks(tvZapGo);
        tvZapIdx = -1;
        tvStopAttempt(false);
        tvStepGen++;
        if(!tvMode && web!=null)destroyWeb();
        tvMode = true;
        tvCur = c;
        tvPlaying = false;
        int n = c.srcs.size();
        int first = firstSrc >= 0 && firstSrc < n ? firstSrc : 0;
        if (firstSrc < 0 && !tvSrcSound(c.srcs.get(first))) {
            // Основният сайт скоро е имал неуспех: започва се със сайт без пресен неуспех, ако има такъв.
            for (int i = 0; i < n; i++) {
                if (tvSrcSound(c.srcs.get(i))) {
                    first = i;
                    break;
                }
            }
        }
        tvFirstSrc = first;
        // План: за всеки източник първо запазените потоци, после търсене през страницата му.
        // Потоците, които в това гледане вече не тръгнаха или заседнаха, се прескачат.
        tvPlan.clear();
        ArrayList<Integer> order = new ArrayList<>();
        order.add(tvFirstSrc);
        // Сайтовете с пресен неуспех се пробват последни.
        for (int i = 0; i < n; i++) {
            if (i != tvFirstSrc && tvSrcSound(c.srcs.get(i))) order.add(i);
        }
        for (int i = 0; i < n; i++) {
            if (i != tvFirstSrc && !tvSrcSound(c.srcs.get(i))) order.add(i);
        }
        ArrayList<int[]> captures=new ArrayList<>();
        for (int i : order) {
            TvSrc s = c.srcs.get(i);
            boolean cap = tvCapturable(s);
            boolean capOk = cap && (tvVod || tvCapUsed(i) < TV_CAP_MAX);
            // Поток, който няколко пъти поред не е тръгнал, вероятно е изтекъл: веднага към търсене.
            if (!s.stream.isEmpty() && !tvSessBad.contains(s.stream) && (s.fails < 3 || !cap)) {
                tvPlan.add(new int[]{i, TV_DIRECT, -1});
            }
            // Резервните потоци на същата страница се пробват, преди да се търси или смени сайтът.
            if (!s.stream.isEmpty()) {
                for (int k = 0; k < s.alts.size(); k++) {
                    if (!tvSessBad.contains(s.alts.get(k)[0])) tvPlan.add(new int[]{i, TV_DIRECT, k});
                }
            }
            if (capOk) {
                if(tvVod)tvPlan.add(new int[]{i, TV_CAPTURE});
                else captures.add(new int[]{i, TV_CAPTURE});
            }
        }
        tvPlan.addAll(captures); // TV: exhaust saved streams before opening any website.
        if (tvForce != null && tvForce[0] >= 0 && tvForce[0] < n) {
            // Избран поток (от менюто или със ▲▼): пробва се пръв, дори да е бил отбелязан за счупен.
            TvSrc fs = c.srcs.get(tvForce[0]);
            if (!fs.stream.isEmpty() && tvForce[1] < fs.alts.size()) {
                tvPlan.add(0, new int[]{tvForce[0], TV_DIRECT, tvForce[1]});
            }
        }
        // Потокът е избран от потребителя: редът на потоците не се променя.
        tvPinned = tvForce != null;
        tvForce = null;
        tvStep = -1;
        // Докато се гледа, екранът със списъка не държи памет (изгледи и лога).
        tvPanelRelease();
        if (!tvVod) tvEpgLoad();
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        nativeLayer.setVisibility(View.VISIBLE);
        tvHideListNow();
        tvHideBannerNow();
        if (tvVod) {
            subHide();
            tvDialogClose();
        }
        tvWaitShow(c);
        updateState();
        logEvent((tvVod ? "Филм: пускам " : "ТВ: пускам ") + c.name, true);
        tvNextStep();
    }

    // Спира текущия опит: плеърът (или се освобождава) и скритата страница.
    private void tvStopAttempt(boolean release) {
        ui.removeCallbacks(tvStepTimeout);
        ui.removeCallbacks(tvTryTimeout);
        ui.removeCallbacks(tvCheckRun);
        ui.removeCallbacks(tvPokeRun);
        ui.removeCallbacks(tvPokeFallback);
        ui.removeCallbacks(tvStallRun);
        ui.removeCallbacks(tvStableRun);
        // Търсенето на още потоци (ако върви) се прекратява; закъснелият му резултат се пренебрегва.
        tvSearchGen++;
        tvSearching = false;
        tvSearchChecking = false;
        tvSearchTune = false;
        tvSearchPlayer = null;
        tvSearchWeb = null;
        if(tvSearchTask!=null){tvSearchTask.cancel(true);tvSearchTask=null;}
        TV_SEARCH_NET.purge();
        ui.removeCallbacks(tvSearchEndRun);
        ui.removeCallbacks(tvSearchProbeTimeout);
        ui.removeCallbacks(tvMoreStartRun);
        tvTryGen++;
        tvTrying = false;
        if (player != null) {
            if (tvLis != null) player.removeListener(tvLis);
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            if (release) {
                try {
                    player.release();
                } catch (Exception ignored) {
                }
                player = null;
            }
        }
        tvLis = null;
        if (tvCapturing) {
            tvCapturing = false;
            tvDropWeb();
        }
    }

    // Затваря скритата страница (и цял екран, ако тя е поискала такъв).
    private void tvDropWeb() {
        if (customView != null && chromeClient != null) chromeClient.onHideCustomView();
        destroyWeb();
    }

    // Картината се побира в екрана със запазени пропорции (4:3 каналите не се разтягат).
    private void tvFitVideo(VideoSize vs) {
        if (nativeSurface == null || nativeLayer == null || vs == null) return;
        if (vs.width <= 0 || vs.height <= 0) return;
        int lw = nativeLayer.getWidth();
        int lh = nativeLayer.getHeight();
        if (lw <= 0 || lh <= 0) return;
        float ratio = vs.width * Math.max(vs.pixelWidthHeightRatio, 0.1f) / vs.height;
        int w = lw;
        int h = lh;
        if (lw / (float) lh > ratio) w = Math.round(lh * ratio);
        else h = Math.round(lw / ratio);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, h, Gravity.CENTER);
        nativeSurface.setLayoutParams(p);
    }

    private void tvFullVideo() {
        if (nativeSurface == null) return;
        nativeSurface.setLayoutParams(new FrameLayout.LayoutParams(MP, MP));
    }

    private void tvNextStep() {
        if(tvVod && !tvPlaying && tvCur!=null && tvStep>=0 && tvStep<tvPlan.size()){
            int source=tvPlan.get(tvStep)[0];
            if(source>=0 && source<tvCur.srcs.size())prefs.edit().putLong("vodBad:"+hostOf(tvCur.srcs.get(source).page),System.currentTimeMillis()).apply();
        }
        tvStopAttempt(false);
        tvStepGen++;
        tvStep++;
        tvPokeN = 0;
        if (tvCur == null || tvStep >= tvPlan.size()) {
            tvGiveUp();
            return;
        }
        int[] st = tvPlan.get(tvStep);
        TvSrc s = tvCur.srcs.get(st[0]);
        tvRefreshWait();
        if (st[1] == TV_DIRECT) {
            int alt = st.length > 2 ? st[2] : -1;
            if (alt >= 0 && alt < s.alts.size()) {
                String[] al = s.alts.get(alt);
                TvSrc t = new TvSrc();
                t.stream = al[0];
                t.ref = al[1];
                t.org = al[2];
                t.ua = al[3];
                tvWaitText("Пробвам резервен поток от " + s.site + "…");
                tvTryUrl(t.stream, tvHeadersOf(t), tvUaOf(t), st[0], false);
            } else {
                tvWaitText("Пускам запазения поток от " + s.site + "…");
                tvTryUrl(s.stream, tvHeadersOf(s), tvUaOf(s), st[0], false);
            }
        } else {
            tvWaitText(s.stream.isEmpty() ? (tvVod ? "Търся филма в " : "Търся канала в ") + s.site + "…"
                    : "Запазеният поток не тръгна. Търся нов в " + s.site + "…");
            tvStartCapture(s, st[0]);
        }
    }

    // Заглавията на заявката към потока. Бисквитките не са тук: за всяка заявка (плейлист и части)
    // се вземат наново от бисквитките на страницата и се обновяват от отговорите (TvCookies).
    private HashMap<String, String> tvHeadersOf(TvSrc s) {
        HashMap<String, String> p = new HashMap<>();
        if (!s.ref.isEmpty()) p.put("Referer", s.ref);
        if (!s.org.isEmpty()) p.put("Origin", s.org);
        tvCommonHeaders(p, s.stream);
        return p;
    }

    // По-пълни заглавия, като в истинския браузър: приемани типове, език и откъде идва заявката.
    private static void tvCommonHeaders(HashMap<String, String> p, String url) {
        if (!p.containsKey("Accept")) p.put("Accept", "*/*");
        if (!p.containsKey("Accept-Language")) {
            p.put("Accept-Language", "bg-BG,bg;q=0.9,en-US;q=0.8,en;q=0.7");
        }
        String org = p.get("Origin");
        if (org == null || org.isEmpty() || url == null || !url.startsWith("http")) return;
        String oh = nz(Uri.parse(org).getHost()).toLowerCase(Locale.ROOT);
        String uh = nz(Uri.parse(url).getHost()).toLowerCase(Locale.ROOT);
        if (oh.isEmpty() || uh.isEmpty()) return;
        String rel = "cross-site";
        if (oh.equals(uh)) rel = "same-origin";
        else if (siteBase(oh).equals(siteBase(uh))) rel = "same-site";
        p.put("Sec-Fetch-Site", rel);
        p.put("Sec-Fetch-Mode", "cors");
        p.put("Sec-Fetch-Dest", "empty");
    }

    // Родният плеър ползва същите бисквитки като скритата страница и ги обновява от отговорите:
    // някои сайтове сменят бисквитката при всеки плейлист и режат връзката, ако не я върнеш.
    private static class TvCookies extends java.net.CookieHandler {
        @Override
        public java.util.Map<String, java.util.List<String>> get(java.net.URI uri,
                java.util.Map<String, java.util.List<String>> requestHeaders) {
            java.util.Map<String, java.util.List<String>> m = new HashMap<>();
            try {
                String ck = CookieManager.getInstance().getCookie(uri.toString());
                if (ck != null && !ck.isEmpty()) m.put("Cookie", java.util.Collections.singletonList(ck));
            } catch (Exception ignored) {
                // без бисквитки
            }
            return m;
        }

        @Override
        public void put(java.net.URI uri, java.util.Map<String, java.util.List<String>> responseHeaders) {
            if (responseHeaders == null) return;
            try {
                CookieManager cm = CookieManager.getInstance();
                for (java.util.Map.Entry<String, java.util.List<String>> en : responseHeaders.entrySet()) {
                    String k = en.getKey();
                    if (k == null || !(k.equalsIgnoreCase("Set-Cookie")
                            || k.equalsIgnoreCase("Set-Cookie2"))) {
                        continue;
                    }
                    for (String v : en.getValue()) cm.setCookie(uri.toString(), v);
                }
            } catch (Exception ignored) {
                // бисквитката не се записва
            }
        }
    }

    private static boolean tvCookiesOn;

    private static void tvCookiesInstall() {
        if (tvCookiesOn) return;
        tvCookiesOn = true;
        try {
            java.net.CookieHandler.setDefault(new TvCookies());
        } catch (Exception ignored) {
            // остават заглавията от записа
        }
    }

    private String tvUaOf(TvSrc s) {
        if (!s.ua.isEmpty()) return s.ua;
        try {
            return WebSettings.getDefaultUserAgent(this);
        } catch (Exception e) {
            return MOBILE_UA;
        }
    }

    // Пуска адрес в родния плеър. Ако тръгне (има картина), опитът е успешен.
    private void tvTryUrl(final String url, HashMap<String, String> props, String ua,
                          final int si, final boolean viaCapture) {
        if (player != null && tvLis != null) player.removeListener(tvLis);
        tvLis = null;
        ui.removeCallbacks(tvTryTimeout);
        final int my = ++tvTryGen;
        tvTrying = true;
        tvTryUrl = url;
        vodSizeRetry = false;
        final HashMap<String, String> fprops = props;
        final String fua = ua;
        try {
            tvCookiesInstall();
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(12000)
                    .setReadTimeoutMs(12000)
                    .setUserAgent(ua);
            http.setDefaultRequestProperties(props);
            if (player == null) {
                // По-малък буфер: по-малко памет, а за телевизия няколко секунди стигат.
                DefaultLoadControl load=tvLoadControl();
                vTrackSel = new androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this);
                applyQuality();
                player = new ExoPlayer.Builder(this).setLoadControl(load)
                        .setTrackSelector(vTrackSel).build();
                player.setVideoSurfaceView(nativeSurface);
            }
            // Докато се проверява поток, намерен през страницата, плеърът е без звук:
            // страницата може да свири сама и два звука заедно не трябва да се чуват.
            player.setVolume(viaCapture ? 0f : 1f);
            tvLis = new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (my != tvTryGen || !tvMode) return;
                    if (state == Player.STATE_READY) {
                        tvReady(url, fprops, fua, si, viaCapture);
                    } else if (state == Player.STATE_BUFFERING) {
                        if (tvPlaying) tvBuffering();
                    } else if (state == Player.STATE_ENDED) {
                        if (tvVod && tvPlaying) vodEnded();
                        else tvTryFailed("потокът свърши");
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (my != tvTryGen || !tvMode) return;
                    tvTryFailed(tvErrInfo(error));
                }

                @Override
                public void onVideoSizeChanged(VideoSize vs) {
                    if (my != tvTryGen || !tvMode) return;
                    tvFitVideo(vs);
                }
            };
            player.addListener(tvLis);
            MediaItem.Builder mib = new MediaItem.Builder().setUri(url);
            String low = url.toLowerCase(Locale.ROOT);
            if (low.contains(".m3u8")) mib.setMimeType(MimeTypes.APPLICATION_M3U8);
            else if (low.contains(".mpd")) mib.setMimeType(MimeTypes.APPLICATION_MPD);
            else if (viaCapture && !low.contains(".mp4")) mib.setMimeType(MimeTypes.APPLICATION_M3U8);
            player.setMediaSource(new DefaultMediaSourceFactory(http).createMediaSource(mib.build()));
            player.prepare();
            player.setPlayWhenReady(true);
            tvTryTimeoutGen = my;
            // Запазеният поток или тръгва бързо, или не си струва да се чака: хванатият през
            // страницата има малко повече време, а филмът - най-много.
            ui.postDelayed(tvTryTimeout, tvVod ? 25000 : viaCapture ? 15000 : TV_SAVED_START_MS);
        } catch (Exception e) {
            tvTryFailed("грешка " + e.getClass().getSimpleName());
        }
    }

    @androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
    private DefaultLoadControl tvLoadControl(){
        return (tvVod
                ? new DefaultLoadControl.Builder().setBufferDurationsMs(30000,60000,1500,5000).setTargetBufferBytes(64*1024*1024).setPrioritizeTimeOverSizeThresholds(true)
                : new DefaultLoadControl.Builder().setBufferDurationsMs(15000,30000,1500,3000).setTargetBufferBytes(32*1024*1024).setPrioritizeTimeOverSizeThresholds(true)).build();
    }

    private int tvTryTimeoutGen;
    private final Runnable tvTryTimeout = new Runnable() {
        @Override
        public void run() {
            if (!tvMode || !tvTrying || tvTryTimeoutGen != tvTryGen) return;
            tvTryFailed("потокът не тръгна навреме");
        }
    };

    private int tvTimeoutGen;
    private final Runnable tvStepTimeout = new Runnable() {
        @Override
        public void run() {
            if (!tvMode || tvPlaying || tvTimeoutGen != tvStepGen) return;
            logEvent("ТВ: не намерих поток навреме");
            tvNextStep();
        }
    };

    private void tvArmStep(long ms) {
        ui.removeCallbacks(tvStepTimeout);
        tvTimeoutGen = tvStepGen;
        ui.postDelayed(tvStepTimeout, ms);
    }

    // Опитът с плеъра не е успял.
    private void tvTryFailed(String why) {
        if (!tvMode) return;
        logEvent("ТВ: опитът не успя (" + why + ")");
        if (tvPlaying) {
            tvDropped(why);
            return;
        }
        // В това гледане потокът, който не тръгна, не се пробва втори път.
        if (!tvVod && tvTryUrl != null && !tvTryUrl.isEmpty()) tvSessBad.add(tvTryUrl);
        if (tvCapturing) {
            // Намереният в страницата адрес не е тръгнал: пробва се следващият.
            tvBad.add(nz(tvTryUrl));
            ui.removeCallbacks(tvTryTimeout);
            tvTryGen++;
            tvTrying = false;
            if (player != null) {
                if (tvLis != null) player.removeListener(tvLis);
                try {
                    player.stop();
                } catch (Exception ignored) {
                }
            }
            tvLis = null;
            tvWaitText("Този поток не тръгна. Търся друг…");
            ui.postDelayed(tvCheckRun, 200);
            return;
        }
        if (tvStep >= 0 && tvStep < tvPlan.size() && tvCur != null) {
            int[] st = tvPlan.get(tvStep);
            // Само основният поток се брои за счупен; резервните са допълнение.
            if (st[1] == TV_DIRECT && (st.length < 3 || st[2] < 0)) {
                TvSrc ms = tvCur.srcs.get(st[0]);
                ms.fails++;
                ms.failAt = System.currentTimeMillis();
                tvSave();
            } else if (st[1] == TV_DIRECT && st[2] >= 0) {
                TvSrc fs = tvCur.srcs.get(st[0]);
                if (st[2] < fs.alts.size()) {
                    String[] al = fs.alts.get(st[2]);
                    int f = tvAltFails(al) + 1;
                    if (f >= 3) {
                        // Резервен поток, който три пъти поред не тръгва, се маха.
                        fs.alts.remove(st[2]);
                        logEvent("ТВ: махнах счупен резервен поток от " + fs.site);
                    } else {
                        al[4] = String.valueOf(f);
                    }
                    tvSave();
                }
            }
        }
        tvNextStep();
    }

    // Картината тръгна.
    private void tvReady(String url, HashMap<String, String> props, String ua, int si,
                         boolean viaCapture) {
        if (tvPlaying) {
            // Върнало се е от буфериране.
            ui.removeCallbacks(tvStallRun);
            if(tvSearchWeb!=null)return;
            tvShowBanner(1800, null);
            return;
        }
        if (tvCur == null || si < 0 || si >= tvCur.srcs.size()) return;
        if (tvVod) {
            // Филмът е цял, не е на живо, има картина (рекламите и клиповете в страницата са кратки).
            if (!vodAccept(url, props, ua, si, viaCapture)) return;
        } else if (viaCapture && player != null) {
            // Рекламата или кратко клипче в страницата не е канал: каналът е на живо.
            boolean live = player.isCurrentMediaItemLive() || player.isCurrentMediaItemDynamic()
                    || player.getDuration() == C.TIME_UNSET;
            if (!live) {
                tvTryFailed("не е на живо");
                return;
            }
        }
        tvPlaying = true;
        tvTrying = false;
        ui.removeCallbacks(tvStepTimeout);
        ui.removeCallbacks(tvTryTimeout);
        ui.removeCallbacks(tvCheckRun);
        ui.removeCallbacks(tvPokeRun);
        TvSrc s = tvCur.srcs.get(si);
        if(tvVod && vodT!=null)prefs.edit().putString("vodGood:"+(vodT.tv?"t":"m"),hostOf(s.page)).remove("vodBad:"+hostOf(s.page)).apply();
        long now = System.currentTimeMillis();
        s.bad = 0;
        boolean fresh = false;
        if (!viaCapture && !tvPinned && !s.stream.equals(url)) {
            // Тръгна резервен поток: става основен, старият основен отива в резервните.
            for (int k = 0; k < s.alts.size(); k++) {
                String[] al = s.alts.get(k);
                if (al[0].equals(url)) {
                    // Броячът на неуспехите върви със самия поток.
                    int altFails = tvAltFails(al);
                    String[] oldMain = {s.stream, s.ref, s.org, s.ua, String.valueOf(s.fails)};
                    s.alts.set(k, oldMain);
                    s.stream = al[0];
                    s.ref = al[1];
                    s.org = al[2];
                    s.ua = al[3];
                    s.fails = altFails;
                    break;
                }
            }
        }
        if (viaCapture) {
            // Новият поток се запомня: следващия път тръгва направо.
            fresh = !s.stream.isEmpty() && !s.stream.equals(url);
            // Същият поток с нов знак (token) в адреса е същият поток: старите копия не са резервни.
            String newKey = tvStreamKey(url);
            for (int k = s.alts.size() - 1; k >= 0; k--) {
                String au = s.alts.get(k)[0];
                if (au.equals(url) || tvStreamKey(au).equals(newKey)) s.alts.remove(k);
            }
            // Старият основен поток отива в резервните, освен ако е счупен (3 пъти поред)
            // или е същият поток с по-стар знак.
            if (fresh && s.fails < 3 && !tvStreamKey(s.stream).equals(newKey)) {
                s.alts.add(0, new String[]{s.stream, s.ref, s.org, s.ua, String.valueOf(s.fails)});
            }
            // Счупените резервни се махат; когато са повече от 3, отпада най-счупеният.
            for (int k = s.alts.size() - 1; k >= 0; k--) {
                if (tvAltFails(s.alts.get(k)) >= 3) s.alts.remove(k);
            }
            while (s.alts.size() > TV_MAX_ALTS) {
                int worst = 0;
                for (int k = 1; k < s.alts.size(); k++) {
                    if (tvAltFails(s.alts.get(k)) >= tvAltFails(s.alts.get(worst))) worst = k;
                }
                s.alts.remove(worst);
            }
            s.stream = url;
            s.ref = nz(props.get("Referer"));
            s.org = nz(props.get("Origin"));
            s.ua = nz(ua);
            s.fails = 0; // нов поток: започва с чист лист
            tvCapturing = false;
            tvDropWeb();
        }
        // The page is destroyed before native audio is enabled.
        if (player != null) player.setVolume(1f);
        s.ok = now;
        tvPlayedAt = SystemClock.uptimeMillis();
        // Неуспехите на потока се зачеркват чак след доста добра картина, а не при първия кадър:
        // така поток, който тръгва и заседва, не се лекува сам.
        tvStableUrl = url;
        tvStableSi = si;
        ui.removeCallbacks(tvStableRun);
        if (!tvVod) ui.postDelayed(tvStableRun, TV_STABLE_MS);
        if (tvVod) {
            logEvent("Филм: тръгна " + tvCur.name + " от " + s.site + (viaCapture ? " (хванат поток)" : ""), true);
            tvWaitHide();
            vodOnPlaying();
            updateState();
            return;
        }
        tvCur.last = now;
        tvSave();
        prefs.edit().putString("tvlast", tvCur.id).apply();
        logEvent("ТВ: тръгна " + tvCur.name + " от " + s.site + (viaCapture ? " (нов поток)" : ""), true);
        tvWaitHide();
        tvShowBanner(5000, fresh ? "Запазеният поток не тръгна. Намерих нов и го записах." : null);
        updateState();
        if (tvMoreChan == tvCur) {
            // „Обнови потоците“ от менюто: каналът тръгна, сега се търсят още потоци в сайта му.
            tvMoreChan = null;
            ui.removeCallbacks(tvMoreStartRun);
            ui.postDelayed(tvMoreStartRun, 3500);
        }
    }

    private final Runnable tvMoreStartRun = new Runnable() {
        @Override
        public void run() {
            if (tvMode && tvPlaying && !tvVod) tvMoreStart(false);
        }
    };

    private final Runnable tvStallRun = new Runnable() {
        @Override
        public void run() {
            if (!tvMode || !tvPlaying) return;
            String info = "";
            try {
                if (player != null) {
                    info = ", в буфера " + Math.max(0, player.getBufferedPosition()
                            - player.getCurrentPosition()) / 1000 + " сек.";
                }
            } catch (Exception ignored) {
                // без подробност в записа
            }
            tvDropped("потокът заседна" + info);
        }
    };

    private final Runnable tvStableRun = new Runnable() {
        @Override
        public void run() {
            if (!tvMode || !tvPlaying || tvVod || tvCur == null) return;
            if (tvStableSi < 0 || tvStableSi >= tvCur.srcs.size()) return;
            TvSrc s = tvCur.srcs.get(tvStableSi);
            boolean changed = false;
            if (s.stream.equals(tvStableUrl)) {
                if (s.fails != 0) {
                    s.fails = 0;
                    changed = true;
                }
            } else {
                for (String[] al : s.alts) {
                    if (al[0].equals(tvStableUrl) && tvAltFails(al) != 0) {
                        al[4] = "0";
                        changed = true;
                    }
                }
            }
            if (changed) tvSave();
        }
    };

    private void tvBuffering() {
        if(tvSearchWeb!=null)return;
        tvShowBanner(0, "Зареждане…");
        ui.removeCallbacks(tvStallRun);
        // На живо картина, която не помръдва 10 секунди, е заседнала; филмът има повече търпение.
        ui.postDelayed(tvStallRun, tvVod ? 25000 : TV_STALL_MS);
    }

    // Отбелязва заседнал поток: в това гледане не се пробва пак, а неуспехът му се записва
    // (три такива в отделни гледания и резервният поток се маха, основният се търси наново).
    private void tvMarkStall(TvChan c, int si, String url, long ranMs) {
        if (url == null || url.isEmpty() || si < 0 || si >= c.srcs.size()) return;
        if (!tvSessBad.add(url)) return; // в това гледане вече е отбелязан
        TvSrc s = c.srcs.get(si);
        int count = 0;
        boolean known = false;
        if (s.stream.equals(url)) {
            s.fails++;
            s.failAt = System.currentTimeMillis();
            count = s.fails;
            known = true;
        } else {
            for (int k = 0; k < s.alts.size(); k++) {
                String[] al = s.alts.get(k);
                if (!al[0].equals(url)) continue;
                int f = tvAltFails(al) + 1;
                count = f;
                known = true;
                if (f >= 3) {
                    s.alts.remove(k);
                    logEvent("ТВ: махнах нестабилен резервен поток от " + s.site);
                } else {
                    al[4] = String.valueOf(f);
                }
                break;
            }
        }
        if (known) tvSave();
        logEvent("ТВ: потокът " + shortName(url) + " заседна след " + (ranMs / 1000) + " сек. (" + s.site
                + ", неуспех " + count + " от 3)");
    }

    // При прекъсване първо се пробват запазените резерви за същия канал.
    // Само кратките неуспешни опити влошават трайната оценка на потока.
    // Възстановяването на филмите запазва досегашното поведение.
    private void tvDropped(String why) {
        long now = SystemClock.uptimeMillis();
        long ran = now - tvPlayedAt;
        if (ran > 120000) {
            tvRestarts = 0;
            tvSwitches = 0;
        }
        tvRestarts++;
        TvChan c = tvCur;
        if (c == null) return;
        if (tvVod && player != null) {
            long p = player.getCurrentPosition();
            if (p > 0) vodResume = p;
        }
        int cur = tvSrcNowIdx();
        logEvent("ТВ: връзката се скъса (" + why + "), опит " + tvRestarts + ", вървя " + (ran / 1000) + " сек.");
        if (tvVod) {
            if (tvRestarts <= 3) {
                tvTune(c, cur);
                return;
            }
            tvRestarts = 0;
            if (c.srcs.size() > 1 && ++tvSwitches <= 2) tvTune(c, (cur + 1) % c.srcs.size());
            else tvGiveUp();
            return;
        }
        // Докато върви търсенето на още потоци, скритата страница може да натовари кутията и картината
        // да заседне заради нея: тогава потокът не се обвинява.
        boolean searchBusy = now < tvSearchBusyUntil;
        if (ran < TV_STABLE_MS && !searchBusy) tvMarkStall(c, cur, nz(tvTryUrl), ran);
        // Even a stream that ran well must yield to its alternatives after failure.
        // Only the short failures above affect persistent reliability counters.
        if(!nz(tvTryUrl).isEmpty())tvSessBad.add(tvTryUrl);
        if (tvRestarts <= TV_MAX_RESTARTS) tvTune(c, cur);
        else tvGiveUp();
    }

    // Нищо не тръгна: чак сега се отваря самият сайт на основния източник.
    private void tvGiveUp() {
        if (tvVod) {
            vodGiveUp(tvFirstSrc);
            return;
        }
        final TvChan c = tvCur;
        int idx = tvFirstSrc;
        tvLeave(false);
        if (c == null || c.srcs.isEmpty()) {
            tvShowPanel();
            return;
        }
        if (idx < 0 || idx >= c.srcs.size()) idx = 0;
        TvSrc s = c.srcs.get(idx);
        String page = s.page;
        if (page == null || !page.startsWith("http")) {
            String[] found = null;
            for (String[] st : sites) {
                if (st[0].equals(s.site)) found = st;
            }
            if (found == null) {
                Toast.makeText(this, "„" + c.name + "“ не тръгна.", Toast.LENGTH_LONG).show();
                tvShowPanel();
                return;
            }
            page = found[1];
        }
        logEvent("ТВ: " + c.name + " не тръгна, отварям сайта " + s.site, true);
        Toast.makeText(this, "„" + c.name + "“ не тръгна сам. Отварям сайта " + s.site + ".",
                Toast.LENGTH_LONG).show();
        openSite(new String[]{s.site, page, s.mode, "Телевизия"});
        catReturn = new Runnable() {
            @Override
            public void run() {
                tvShowPanel();
            }
        };
    }

    // Излиза от гледането: спира всичко. showPanel връща към списъка с канали.
    private void tvLeave(boolean showPanel) {
        tvCloseTimeline();
        if(streamDialog!=null)streamDialog.dismiss();
        boolean was = tvMode;
        vodCleanup(); // преди плеърът да се затвори: пази се мястото във филма
        tvMode = false;
        tvEpgStop();
        ui.removeCallbacks(tvZapGo);
        ui.removeCallbacks(tvBannerHideRun);
        ui.removeCallbacks(tvClockRun);
        tvMoreChan = null;
        tvZapIdx = -1;
        tvStepGen++;
        tvStopAttempt(true);
        tvFullVideo();
        if (nativeLayer != null) nativeLayer.setVisibility(View.GONE);
        tvHideWaitNow();
        tvHideBannerNow();
        tvHideListNow();
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        updateState();
        if (showPanel && was) tvShowPanel();
    }

    // Вика се при връщане към менюто или излизане от приложението.
    private void tvReset() {
        if (tvMode) tvLeave(false);
        tvEpgStop();
        if (tvPanelShown()) tvHidePanelQuiet();
        tvDialogClose();
    }

    // ---- търсене на нов поток през страницата на сайта (скрита, под екрана за чакане) ----

    private void tvStartCapture(TvSrc s, int si) {
        tvCapturing = true;
        tvReloaded = false;
        tvCapSrc = si;
        tvBad.clear();
        tvTrying = false;
        tvCapMap.put(si, tvCapUsed(si) + 1);
        tvOpenHidden(s, false);
        tvArmStep(tvVod ? 32000 : 25000);
    }

    // Отваря страницата на източника в скритата страница. Вика се и от търсенето на още потоци
    // (quiet = плеърът на канала свири през това време: страницата не бива да пуска звук сама).
    private void tvOpenHidden(TvSrc s, boolean quiet) {
        String url = s.page;
        allowedHost = Uri.parse(url).getHost();
        srcQueue = null;
        srcIdx = 0;
        catReturn = null;
        cursorTemp = false;
        scrollMode = false;
        hovering = false;
        cancelOk();
        curSiteName = s.site;
        String mode = s.mode;
        String chosen = prefs.getString("mode:" + allowedHost, null);
        if (chosen != null) mode = chosen.equals("mobile") ? "mobile" : "";
        curMode = mode;
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
        logEvent("ТВ: търся поток през " + s.site, true);
        if (web == null) createWebView();
        applyUserAgent();
        tvNoImages(web);
        tvSearchWeb=quiet?web:null;
        if (quiet) {
            try {
                web.getSettings().setMediaPlaybackRequiresUserGesture(true);
            } catch (Exception ignored) {
                // остава позволеното самопускане
            }
        }
        web.setVisibility(View.VISIBLE);
        web.loadUrl(url);
        // Ако страницата не съобщи, че е готова (реклами, които висят), "буткането" започва пак.
        ui.removeCallbacks(tvPokeFallback);
        ui.postDelayed(tvPokeFallback, 8000);
    }

    // Скритата страница никой не я гледа: без картинки тя се зарежда по-бързо и товари по-малко.
    private static void tvNoImages(WebView w) {
        try {
            WebSettings ws = w.getSettings();
            ws.setLoadsImagesAutomatically(false);
            ws.setBlockNetworkImage(true);
        } catch (Exception ignored) {
            // настройката не се прилага: страницата се зарежда с картинки
        }
    }

    private final Runnable tvPokeFallback = new Runnable() {
        @Override
        public void run() {
            if (tvMode && tvCapturing) tvPageFinished();
        }
    };

    // Страницата се е заредила: започва да се "буткат" плеърите да тръгнат.
    private void tvPageFinished() {
        if (tvPokeN > 0 || !tvCapturing) return;
        tvPokeN = 1;
        ui.removeCallbacks(tvPokeRun);
        ui.postDelayed(tvPokeRun, 1200);
    }

    private final Runnable tvPokeRun = new Runnable() {
        @Override
        public void run() {
            if (!tvMode || !tvCapturing || web == null || (tvPlaying && !tvSearching)) return;
            int n = tvPokeN++;
            if (!tvTrying) {
                web.evaluateJavascript(TV_MUTE_JS, null);
                web.evaluateJavascript(TV_PLAY_JS, null);
                tvScanResources();
                // Страница, която виси или не е показала плеър: едно презареждане често я оправя.
                if (n == 4 && !tvReloaded && !tvHasCandidate()) {
                    tvReloaded = true;
                    logEvent("ТВ: презареждам страницата (няма поток)");
                    try {
                        web.reload();
                    } catch (Exception ignored) {
                        // страницата не може да се презареди
                    }
                    tvPokeN = 1;
                }
                // Плеър в чужда рамка не се достига със скрипт: натискаме върху него като потребител.
                if (n == 3 || n == 6) tvTapPlayer();
            }
            if (n < 9) ui.postDelayed(this, n < 3 ? 2300 : 3500);
        }
    };

    // Име на грешката на плеъра и, ако сървърът е отказал, кодът му (403, 404...).
    private static String tvErrInfo(PlaybackException e) {
        Throwable t = e;
        for (int i = 0; t != null && i < 6; i++, t = t.getCause()) {
            if (t instanceof androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                return e.getErrorCodeName() + ", HTTP "
                        + ((androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) t).responseCode;
            }
        }
        return e.getErrorCodeName();
    }

    private boolean tvReloaded;

    private boolean tvHasCandidate() {
        synchronized (mediaPlaylists) {
            for (String u : mediaPlaylists.keySet()) {
                if (!tvIsBad(u)) return true;
            }
        }
        return false;
    }

    // Адреси на плейлисти, които не завършват на .m3u8 (напр. playlist.php), се виждат в списъка на заредените ресурси.
    private static final String TV_RES_JS =
            "(function(){try{var o=[],e=performance.getEntriesByType('resource');"
                    + "for(var i=0;i<e.length;i++){var n=e[i].name;"
                    + "if(/\\.(js|css|png|jpe?g|gif|svg|webp|woff2?|ico|ts|m4s|aac|mp4)(\\?|$)/i.test(n))continue;"
                    + "if(/m3u8|\\.mpd|playlist|manifest|chunklist|master|hls/i.test(n))o.push(n);}"
                    + "return o.slice(-6).join('\\n');}catch(x){return '';}})()";

    private void tvScanResources() {
        final WebView w = web;
        if (w == null) return;
        w.evaluateJavascript(TV_RES_JS, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (!tvMode || !tvCapturing || web != w || v == null || v.length() < 6) return;
                String t = v.substring(1, v.length() - 1).replace("\\n", "\n").replace("\\/", "/")
                        .replace("\\u0026", "&");
                boolean added = false;
                for (String u : t.split("\n")) {
                    u = u.trim();
                    if (!u.startsWith("http")) continue;
                    synchronized (mediaPlaylists) {
                        if (mediaPlaylists.containsKey(u) || mediaPlaylists.size() >= 15) continue;
                        HashMap<String, String> h = new HashMap<>();
                        String pu = w.getUrl();
                        if (pu != null) h.put("Referer", pu);
                        mediaPlaylists.put(u, h);
                        added = true;
                    }
                }
                if (added) ui.postDelayed(tvCheckRun, 300);
            }
        });
    }

    // Намира най-големия плеър (видео или рамка) и връща центъра му като дял от видимата част.
    private static final String TV_RECT_JS =
            "(function(){try{var best='',ba=0,e=document.querySelectorAll('video,iframe');"
                    + "var W=window.innerWidth,H=window.innerHeight;"
                    + "for(var i=0;i<e.length;i++){var r=e[i].getBoundingClientRect();"
                    + "var l=Math.max(r.left,0),t=Math.max(r.top,0),"
                    + "rr=Math.min(r.right,W),b=Math.min(r.bottom,H);"
                    + "var w=rr-l,h=b-t;"
                    + "if(w>120&&h>80&&w*h>ba){ba=w*h;"
                    + "best=((l+rr)/2/W).toFixed(3)+','+((t+b)/2/H).toFixed(3);}}"
                    + "return best;}catch(x){return '';}})()";

    private void tvTapPlayer() {
        final WebView w = web;
        if (w == null) return;
        w.evaluateJavascript(TV_RECT_JS, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String v) {
                if (!tvMode || !tvCapturing || tvTrying || web != w || v == null) return;
                String t = v.replace("\"", "").trim();
                int k = t.indexOf(',');
                if (k < 1) return;
                try {
                    float fx = Float.parseFloat(t.substring(0, k));
                    float fy = Float.parseFloat(t.substring(k + 1));
                    tapAt(w, fx * w.getWidth(), fy * w.getHeight());
                } catch (NumberFormatException ignored) {
                    // няма достоверен център: не се натиска
                }
            }
        });
    }

    // Страницата е заредена с грешка: няма смисъл да се чака.
    private void tvCaptureError(String why) {
        logEvent("ТВ: страницата не се зареди (" + why + ")");
        if (tvSearching) {
            // Търсенето на още потоци: каналът си свири, няма стъпка за прескачане.
            ui.post(tvSearchEndRun);
            return;
        }
        final int gen = tvStepGen;
        ui.post(new Runnable() {
            @Override
            public void run() {
                // Две грешки подред (код и мрежа) не трябва да прескачат две стъпки.
                if (tvMode && tvCapturing && !tvTrying && gen == tvStepGen) tvNextStep();
            }
        });
    }

    private final Runnable tvCheckRun = new Runnable() {
        @Override
        public void run() {
            tvCheck();
        }
    };

    // Проверява дали страницата вече е поискала видео поток и пробва най-вероятния.
    private void tvCheck() {
        if (!tvMode || !tvCapturing || tvTrying || tvPlaying || tvCur == null) return;
        if (tvCapSrc < 0 || tvCapSrc >= tvCur.srcs.size()) return;
        TvSrc s = tvCur.srcs.get(tvCapSrc);
        String pref = s.stream.isEmpty() ? "" : hostOf(s.stream);
        String cand = null;
        HashMap<String, String> h = null;
        synchronized (mediaPlaylists) {
            String first = null;
            HashMap<String, String> firstH = null;
            for (Map.Entry<String, HashMap<String, String>> en : mediaPlaylists.entrySet()) {
                String u = en.getKey();
                if (tvIsBad(u)) continue;
                if (first == null) {
                    first = u;
                    firstH = en.getValue();
                }
                if (!pref.isEmpty() && hostOf(u).equals(pref)) {
                    cand = u;
                    h = en.getValue();
                    break;
                }
            }
            if (cand == null) {
                cand = first;
                h = firstH;
            }
        }
        if (cand == null && tvVod) {
            synchronized (mediaFiles) {
                for (Map.Entry<String, HashMap<String, String>> en : mediaFiles.entrySet()) {
                    if (tvIsBad(en.getKey())) continue;
                    cand = en.getKey();
                    h = en.getValue();
                    break;
                }
            }
        }
        if (cand == null) return;
        HashMap<String, String> props = new HashMap<>();
        String ua = null;
        if (h != null) {
            if (h.get("Referer") != null) props.put("Referer", h.get("Referer"));
            if (h.get("Origin") != null) props.put("Origin", h.get("Origin"));
            ua = h.get("User-Agent");
        }
        if (ua == null && web != null) ua = web.getSettings().getUserAgentString();
        if (ua == null) ua = MOBILE_UA;
        tvCommonHeaders(props, cand);
        tvWaitText("Намерих поток, пускам…");
        tvTryUrl(cand, props, ua, tvCapSrc, true);
    }

    // ---- екраните върху видеото: чакане, табела с канала, списък ----

    private FrameLayout tvWait;
    private TextView tvWaitNo, tvWaitName, tvWaitMsg;
    private LinearLayout tvWaitChips;
    private FrameLayout tvBanner;
    private LinearLayout tvKeys;
    private View tvChipZap, tvChipSrc, tvChipStream;
    private TextView tvBnNo, tvBnName, tvBnSub, tvBnCat;
    private TvPlate tvBnPlate;
    private View tvBnCatDot;
    private FrameLayout tvListBox;
    private LinearLayout tvListCol;
    private ScrollView tvListScroll;
    private final Runnable tvBannerHideRun = new Runnable() {
        @Override
        public void run() {
            tvHideBanner();
        }
    };

    private View tvKeyChip(String key, String label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(u(16), 0, u(18), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x5009081C);
        g.setCornerRadius(u(19));
        g.setStroke(Math.max(1, u(1)), 0x1FFFFFFF);
        c.setBackground(g);
        LinearLayout.LayoutParams kl = lp(WC, WC);
        kl.rightMargin = u(10);
        c.addView(text(key, 16, T_SKY2, Typeface.DEFAULT_BOLD, true), kl);
        c.addView(text(label, 17, T_TEXT2, tfMedium, true));
        return c;
    }

    private void tvBuildOsd() {
        if (tvWait != null) return;
        // Екран за чакане: име на канала, какво се опитва, и по един етикет за всеки източник.
        tvWait = new FrameLayout(this);
        tvWait.addView(new TvBgView(this), new FrameLayout.LayoutParams(MP, MP));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(u(80), 0, u(80), 0);
        tvWaitNo = text("", 30, T_SKY2, tfCondBold, true);
        tvWaitNo.setGravity(Gravity.CENTER);
        c.addView(tvWaitNo, lp(MP, WC));
        tvWaitName = text("", 110, T_TEXT, tfCondBold, true);
        tvWaitName.setIncludeFontPadding(false);
        tvWaitName.setGravity(Gravity.CENTER);
        c.addView(tvWaitName, lpTopW(MP, WC, u(4)));
        tvWaitMsg = text("", 24, T_TEXT2, tfMedium, true);
        tvWaitMsg.setGravity(Gravity.CENTER);
        c.addView(tvWaitMsg, lpTopW(MP, WC, u(22)));
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        pb.getIndeterminateDrawable().setColorFilter(T_SKY, PorterDuff.Mode.SRC_IN);
        c.addView(pb, lpTopW(u(64), u(64), u(36)));
        tvWaitChips = new LinearLayout(this);
        tvWaitChips.setOrientation(LinearLayout.HORIZONTAL);
        tvWaitChips.setGravity(Gravity.CENTER);
        c.addView(tvWaitChips, lpTopW(MP, WC, u(34)));
        tvWait.addView(c, new FrameLayout.LayoutParams(MP, WC, Gravity.CENTER));
        TextView hint = text("OK — отвори сайта  ·  ◀ ▶ друг източник  ·  Назад — отказ", 17, T_TEXT3, tfMedium, true);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(WC, WC,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hl.bottomMargin = u(44);
        tvWait.addView(hint, hl);
        tvWait.setVisibility(View.GONE);
        nativeLayer.addView(tvWait, new FrameLayout.LayoutParams(MP, MP));

        tvBanner=new FrameLayout(this);
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(u(54),u(53),u(54),u(24));bottom.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x000D1119,0xEE0D1119,0xFF0D1119}));
        LinearLayout headline=new LinearLayout(this);headline.setGravity(Gravity.BOTTOM);LinearLayout programme=new LinearLayout(this);programme.setOrientation(LinearLayout.VERTICAL);
        LinearLayout channel=new LinearLayout(this);channel.setGravity(Gravity.CENTER_VERTICAL);TextView live=text("НА ЖИВО",14,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true);live.setPadding(u(9),u(5),u(9),u(5));live.setBackground(TvTheme.panel(u(5),T_LIVE,0,0));channel.addView(live,lp(WC,WC));tvBnNo=text("",17,TvTheme.MUTED,tfMedium,true);LinearLayout.LayoutParams no=lp(WC,WC);no.leftMargin=u(16);channel.addView(tvBnNo,no);tvBnName=text("",19,TvTheme.MUTED,tfMedium,true);LinearLayout.LayoutParams name=lp(WC,WC);name.leftMargin=u(10);channel.addView(tvBnName,name);programme.addView(channel);
        tvBnProgramme=text("",34,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true);programme.addView(tvBnProgramme,lpTopW(MP,WC,u(12)));tvBnNext=text("",19,TvTheme.MUTED,tfMedium,true);programme.addView(tvBnNext,lpTopW(MP,WC,u(10)));headline.addView(programme,new LinearLayout.LayoutParams(0,WC,1));
        tvBnSub=text("",19,TvTheme.BLUE_LIGHT,tfMedium,false);tvBnSub.setGravity(Gravity.END|Gravity.BOTTOM);tvBnSub.setMaxLines(2);tvBnSub.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams status=lp(u(450),u(70));status.leftMargin=u(24);headline.addView(tvBnSub,status);bottom.addView(headline,lp(MP,WC));
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);tvControlsFirst=tvDashboardPill(IC_BARS,"Канали",()->tvShowList());actions.addView(tvControlsFirst,lp(WC,u(55)));
        LinearLayout.LayoutParams programmeLp=lp(WC,u(55));programmeLp.leftMargin=u(14);actions.addView(tvDashboardPill(IC_CLOCK,"Програма",()->tvProgrammeDialog()),programmeLp);
        tvStreamControl=text("Потоци",20,TvTheme.TEXT,tfMedium,true);tvStreamControl.setGravity(Gravity.CENTER);tvStreamControl.setPadding(u(24),0,u(24),0);tvStreamControl.setFocusable(true);tvStreamControl.setClickable(true);tvStreamControl.setBackground(TvTheme.control(U,false));tvStreamControl.setOnClickListener(v->tvStreamPicker());tvStreamControl.setOnLongClickListener(v->{if(tvCur!=null)tvChannelMenu(tvCur);return true;});LinearLayout.LayoutParams stream=lp(WC,u(55));stream.leftMargin=u(14);actions.addView(tvStreamControl,stream);
        LinearLayout.LayoutParams quality=lp(WC,u(55));quality.leftMargin=u(14);actions.addView(tvDashboardPill(IC_SLIDERS,"Качество",()->tvQualityDialog()),quality);
        tvPauseControl=playerControl("❚❚","Пауза / Пусни",()->tvTogglePause(),false);LinearLayout.LayoutParams pauseLp=lp(u(60),u(55));pauseLp.leftMargin=u(14);actions.addView(tvPauseControl,pauseLp);
        tvFavouriteControl=playerControl("☆","Любим канал",()->{if(tvCur!=null){String key="tvfav:"+tvCur.id;prefs.edit().putBoolean(key,!prefs.getBoolean(key,false)).apply();tvShowBanner(0,null);}},false);LinearLayout.LayoutParams favouriteLp=lp(u(60),u(55));favouriteLp.leftMargin=u(14);actions.addView(tvFavouriteControl,favouriteLp);bottom.addView(actions,lpTopW(MP,u(55),u(25)));
        for(int i=0;i<actions.getChildCount();i++){View control=actions.getChildAt(i);control.setId(View.generateViewId());control.setNextFocusUpId(control.getId());control.setNextFocusDownId(control.getId());if(i>0){control.setNextFocusLeftId(actions.getChildAt(i-1).getId());actions.getChildAt(i-1).setNextFocusRightId(control.getId());}}
        tvKeys=new LinearLayout(this);tvKeys.setGravity(Gravity.CENTER_VERTICAL);tvChipZap=text("↑↓ Канал",16,TvTheme.MUTED,tfMedium,true);tvChipStream=text("← Поток",16,TvTheme.MUTED,tfMedium,true);tvChipSrc=text("→ Сайт",16,TvTheme.MUTED,tfMedium,true);tvKeyEnd=text("OK Управление · MENU Раздели",16,TvTheme.MUTED,tfMedium,true);
        for(View hintView:new View[]{tvChipZap,tvChipStream,tvChipSrc,tvKeyEnd}){LinearLayout.LayoutParams hintLp=lp(WC,WC);hintLp.rightMargin=u(28);tvKeys.addView(hintView,hintLp);}bottom.addView(tvKeys,lpTopW(MP,WC,u(22)));tvBanner.addView(bottom,new FrameLayout.LayoutParams(MP,WC,Gravity.BOTTOM));
        tvBnClock=text("",24,TvTheme.TEXT,tfMedium,true);FrameLayout.LayoutParams clockLp=new FrameLayout.LayoutParams(WC,WC,Gravity.TOP|Gravity.END);clockLp.topMargin=u(36);clockLp.rightMargin=u(48);tvBanner.addView(tvBnClock,clockLp);tvBanner.setVisibility(View.GONE);nativeLayer.addView(tvBanner,new FrameLayout.LayoutParams(MP,MP));
        tvListBox=new FrameLayout(this);tvListBox.setBackgroundColor(0xE50D1119);tvListBox.setVisibility(View.GONE);
        nativeLayer.addView(tvListBox,new FrameLayout.LayoutParams(MP,MP));
    }

    private TextView tvPauseControl,tvFavouriteControl,vodPauseControl,tvStreamControl,tvKeyEnd;
    private View tvControlsFirst;
    private String tvBannerStatus;
    private long tvBannerStatusUntil;
    private TextView playerControl(String symbol,String description,Runnable action,boolean main){
        TextView b=text(symbol,main?27:23,TvTheme.TEXT,tfMedium,true);b.setGravity(Gravity.CENTER);b.setContentDescription(description);b.setClickable(true);b.setFocusable(true);
        b.setBackground(TvTheme.control(U,false));
        LinearLayout.LayoutParams l=lp(u(main?72:60),u(main?72:60));l.leftMargin=u(10);l.rightMargin=u(10);b.setLayoutParams(l);b.setOnClickListener(v->action.run());return b;
    }

    private TextView tvBnProgramme,tvBnNext;
    private ProgressBar tvBnProgress;

    private void tvFadeIn(View v) {
        if (v == null) return;
        v.animate().cancel();
        if (v.getVisibility() != View.VISIBLE) {
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
        }
        v.animate().alpha(1f).setDuration(180).start();
        if (v == tvBanner || v == vodBar) tvClockTick();
    }

    // Часовникът в лентата на канала и в лентата на филма: върви само докато лентата се вижда,
    // обновява се в началото на всяка минута и после не държи нищо.
    private TextView tvBnClock;

    private final Runnable tvClockRun = new Runnable() {
        @Override
        public void run() {
            tvClockTick();
        }
    };

    private void tvClockTick() {
        ui.removeCallbacks(tvClockRun);
        boolean live = tvBanner != null && tvBanner.getVisibility() == View.VISIBLE && !tvVod;
        boolean film = vodBar != null && vodBar.getVisibility() == View.VISIBLE && tvVod;
        if (!live && !film) return;
        long now = System.currentTimeMillis();
        String t = new java.text.SimpleDateFormat("HH:mm", Locale.US).format(new java.util.Date(now));
        if (tvBnClock != null) tvBnClock.setText(t);
        if(live && tvCur!=null)tvBannerRefresh();
        if (vodBarClock != null) vodBarClock.setText(t);
        ui.postDelayed(tvClockRun, 60000 - now % 60000 + 150);
    }

    private void tvFadeOut(final View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) return;
        v.animate().cancel();
        v.animate().alpha(0f).setDuration(260).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (v.getAlpha() < 0.05f) v.setVisibility(View.GONE);
            }
        }).start();
    }

    private void tvHideNow(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setVisibility(View.GONE);
        v.setAlpha(1f);
    }

    private void tvHideWaitNow() {
        tvHideNow(tvWait);
    }

    private void tvHideBannerNow() {
        tvControlsOpen=false;
        ui.removeCallbacks(tvBannerHideRun);
        tvBannerStatus=null;tvBannerStatusUntil=0;
        tvHideNow(tvBanner);
        tvHideNow(tvKeys);
        vodHideBarNow();
    }

    private void tvHideListNow() {
        tvHideNow(tvListBox);
        if(tvListBox!=null)tvListBox.removeAllViews();
        tvPlayerGuide=null;
        if(!tvPanelShown())ui.removeCallbacks(tvPanelClockRun);
    }

    private boolean tvListShown() {
        return tvListBox != null && tvListBox.getVisibility() == View.VISIBLE;
    }

    // Екранът за чакане: показва се докато каналът не тръгне.
    private void tvWaitShow(TvChan c) {
        tvWait.animate().cancel();
        tvWait.setAlpha(1f);
        tvWait.setVisibility(View.VISIBLE);
        if (tvVod && vodT != null) {
            tvWaitNo.setText(vodT.tv ? "Сезон " + vodSeason + "  ·  Епизод " + vodEpisode : "Филм");
        } else {
            tvWaitNo.setText("Канал " + (tvChans.indexOf(c) + 1));
        }
        tvWaitName.setText(c.name);
        ts(tvWaitName, c.name.length() > 22 ? 64 : c.name.length() > 14 ? 84 : 110);
        tvWaitMsg.setText("");
        tvRefreshWait();
    }

    private void tvWaitHide() {
        tvFadeOut(tvWait);
    }

    private void tvWaitText(String s) {
        if (tvWaitMsg != null) tvWaitMsg.setText(s);
    }

    // По един етикет за всеки източник: чака, търси или не е тръгнал.
    private void tvRefreshWait() {
        if (tvWaitChips == null || tvCur == null) return;
        if (tvVod) {
            vodRefreshWait();
            return;
        }
        tvWaitChips.removeAllViews();
        int n = tvCur.srcs.size();
        int cur = tvStep >= 0 && tvStep < tvPlan.size() ? tvPlan.get(tvStep)[0] : -1;
        int curKind = tvStep >= 0 && tvStep < tvPlan.size() ? tvPlan.get(tvStep)[1] : TV_DIRECT;
        int shown = Math.min(n, 4);
        for (int i = 0; i < shown; i++) {
            boolean has = false, later = false;
            for (int k = 0; k < tvPlan.size(); k++) {
                if (tvPlan.get(k)[0] != i) continue;
                has = true;
                if (k > tvStep) later = true;
            }
            int state;
            String word;
            if (i == cur) {
                state = 1;
                word = curKind == TV_DIRECT ? "пускам" : "търся";
            } else if (!has) {
                state = 2;
                word = "няма поток";
            } else if (later) {
                state = 0;
                word = "чака";
            } else {
                state = 2;
                word = "не тръгна";
            }
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(u(20), 0, u(22), 0);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(u(22));
            if (state == 1) {
                g.setColor(0x1F38BDF8);
                g.setStroke(Math.max(1, u(1.5f)), T_SKY);
            } else {
                g.setColor(0x12FFFFFF);
                g.setStroke(Math.max(1, u(1.5f)), 0x1FFFFFFF);
            }
            chip.setBackground(g);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(state == 1 ? T_SKY : state == 2 ? 0xFFFF6B7A : T_TEXT3);
            View dot = new View(this);
            dot.setBackground(dg);
            LinearLayout.LayoutParams dl = lp(u(10), u(10));
            dl.rightMargin = u(10);
            chip.addView(dot, dl);
            chip.addView(text(tvCur.srcs.get(i).site + "  ·  " + word, 18,
                    state == 1 ? T_TEXT : T_TEXT3, tfMedium, true));
            LinearLayout.LayoutParams cl = lp(WC, u(44));
            if (i > 0) cl.leftMargin = u(12);
            tvWaitChips.addView(chip, cl);
        }
        if (n > shown) {
            TextView more = text("+" + (n - shown), 18, T_TEXT3, tfMedium, true);
            LinearLayout.LayoutParams ml = lp(WC, u(44));
            ml.leftMargin = u(12);
            more.setGravity(Gravity.CENTER);
            tvWaitChips.addView(more, ml);
        }
    }

    // Табелата с номера и името на канала (и бутоните за управление над нея).
    private void tvShowBanner(long hideMs, String status) {
        if (tvVod) {
            vodShowBar(hideMs, status);
            return;
        }
        if (tvBanner == null || tvCur == null) return;
        if(tvControlsOpen)hideMs=0;
        tvBannerStatus=status;
        tvBannerStatusUntil=status==null?0:hideMs>0?SystemClock.uptimeMillis()+hideMs:Long.MAX_VALUE;
        tvBannerRefresh();
        if(!tvControlsOpen){((TextView)tvChipZap).setText("↑↓ Канал");((TextView)tvChipStream).setText("← Поток");((TextView)tvChipSrc).setText("→ Сайт");tvKeyEnd.setText("OK Управление · MENU Раздели");}
        // Подсказки само за това, което има смисъл: един канал не се сменя, един източник също.
        tvChipZap.setVisibility(tvChans.size() > 1 ? View.VISIBLE : View.GONE);
        // ◀ има смисъл и когато няма втори поток: тогава търси още в сайта.
        tvChipStream.setVisibility(tvStreamCount() > 1 || tvSearchable() ? View.VISIBLE : View.GONE);
        tvChipSrc.setVisibility(tvCur.srcs.size() > 1 ? View.VISIBLE : View.GONE);
        tvFadeIn(tvBanner);
        tvFadeIn(tvKeys);
        ui.removeCallbacks(tvBannerHideRun);
        if (hideMs > 0) ui.postDelayed(tvBannerHideRun, hideMs);
    }

    private void tvBannerRefresh(){
        TvChan channel=tvZapIdx>=0 && tvZapIdx<tvChans.size()?tvChans.get(tvZapIdx):tvCur;if(channel==null)return;
        if(tvBannerStatus!=null && SystemClock.uptimeMillis()>=tvBannerStatusUntil)tvBannerStatus=null;
        tvFillBanner(channel,tvBannerStatus!=null?tvBannerStatus:tvLine());
    }

    private void tvShowControls(){
        tvControlsOpen=true;tvShowBanner(0,null);if(tvControlsFirst!=null)tvControlsFirst.requestFocusFromTouch();
        tvChipZap.setVisibility(View.VISIBLE);tvChipStream.setVisibility(View.VISIBLE);tvChipSrc.setVisibility(View.VISIBLE);
        ((TextView)tvChipZap).setText("←→ Избор");((TextView)tvChipStream).setText("OK Отвори");((TextView)tvChipSrc).setText("Назад Чиста картина");tvKeyEnd.setText("MENU Всички раздели");
    }

    private void tvFillBanner(TvChan c, String line) {
        if(tvPauseControl!=null)tvPauseControl.setText(player!=null && player.getPlayWhenReady()?"❚❚":"▶");
        if(tvFavouriteControl!=null){boolean fav=prefs.getBoolean("tvfav:"+c.id,false);tvFavouriteControl.setText(fav?"★":"☆");tvFavouriteControl.setTextColor(fav?TvTheme.ACCENT:TvTheme.TEXT);}
        tvBnNo.setText(String.valueOf(tvChans.indexOf(c)+1));tvBnName.setText(c.name);if(tvBnPlate!=null)tvBnPlate.setChan(c.name);
        tvBnSub.setText(line==null?"":line);
        long now=System.currentTimeMillis();
        EpgGuide.Event event=tvGuide==null?null:tvGuide.current(c.name,now);
        tvBnProgramme.setText(event==null?"На живо · "+c.name:event.title);
        if(tvBnProgress!=null)tvBnProgress.setProgress(event==null?0:tvEventProgress(event));
        if(tvStreamControl!=null){int count=tvStreamCount();tvStreamControl.setText(count>0?"Потоци · "+(tvStreamNowIdx()+1)+"/"+count:"Потоци");}
        EpgGuide.Event next=null;if(tvGuide!=null)for(EpgGuide.Event e:tvGuide.forChannel(c.name,now))if(e.start>now){next=e;break;}
        tvBnNext.setText((event==null?"Програмата временно не е достъпна":tvEventTime(event))+(next==null?"":" · Следва: "+next.title));
    }

    private void tvHideBanner() {
        tvControlsOpen=false;
        ui.removeCallbacks(tvBannerHideRun);
        tvFadeOut(tvBanner);
        tvFadeOut(tvKeys);
        if (tvVod) vodHideBar();
    }

    // Смяна на канала със стрелките: табелата показва избрания веднага, а пускането
    // започва, когато спреш да натискаш.
    private void tvZap(int dir) {
        int n = tvChans.size();
        if (n < 2 || tvCur == null) {
            tvShowBanner(2500, "Няма друг канал");
            return;
        }
        int base = tvZapIdx >= 0 ? tvZapIdx : tvChans.indexOf(tvCur);
        tvZapIdx = ((base + dir) % n + n) % n;
        TvChan t = tvChans.get(tvZapIdx);
        tvBannerStatus=t.srcs.get(0).site+" · сайт 1 от "+t.srcs.size();tvBannerStatusUntil=SystemClock.uptimeMillis()+2500;
        tvBannerRefresh();
        tvFadeIn(tvBanner);
        tvFadeIn(tvKeys);
        ui.removeCallbacks(tvBannerHideRun);
        ui.removeCallbacks(tvZapGo);
        ui.postDelayed(tvZapGo, 550);
    }

    private final Runnable tvZapGo = new Runnable() {
        @Override
        public void run() {
            if (!tvMode || tvZapIdx < 0 || tvZapIdx >= tvChans.size()) return;
            TvChan t = tvChans.get(tvZapIdx);
            tvZapIdx = -1;
            if (t == tvCur && tvPlaying) {
                // Върнал си се на същия канал: не се пуска наново.
                tvShowBanner(3000, null);
                return;
            }
            tvTuneUser(t, -1);
        }
    };

    // Ляво и дясно: същият канал от друг сайт.
    private void tvSwitchSource(int dir) {
        TvChan c = tvCur;
        if (c == null) return;
        int n = c.srcs.size();
        if (n < 2) {
            tvShowBanner(2500, "Каналът има само един източник");
            return;
        }
        int next = ((tvSrcNowIdx() + dir) % n + n) % n;
        tvTuneUser(c, next);
    }

    private void tvTogglePause() {
        if (player == null || !tvPlaying) return;
        if(tvSearchWeb!=null){tvSearchResumePlayback=!tvSearchResumePlayback;tvShowBanner(0,"Търся още потоци…");return;}
        boolean play = !player.getPlayWhenReady();
        player.setPlayWhenReady(play);
        tvShowBanner(2500, play ? "▶  Пуснато" : "❚❚  Пауза");
    }

    private void tvStreamPicker(){
        final TvChan channel=tvCur;if(channel==null || channel.srcs.isEmpty())return;
        if(streamDialog!=null && streamDialog.isShowing())streamDialog.dismiss();
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(u(32),u(28),u(32),u(28));panel.setBackground(TvTheme.panel(u(16),TvTheme.BACKGROUND,TvTheme.OUTLINE,U));
        panel.addView(text("Потоци на „"+channel.name+"“",32,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true));panel.addView(text("Първо резервните потоци · каналът остава същият",19,TvTheme.MUTED,tfMedium,true),lpTopW(MP,WC,u(12)));
        LinearLayout body=new LinearLayout(this);LinearLayout sitesPanel=new LinearLayout(this);sitesPanel.setOrientation(LinearLayout.VERTICAL);LinearLayout streamsPanel=new LinearLayout(this);streamsPanel.setOrientation(LinearLayout.VERTICAL);body.addView(sitesPanel,lp(u(285),WC));LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,WC,1);right.leftMargin=u(24);body.addView(streamsPanel,right);ScrollView scroll=new ScrollView(this);scroll.addView(body);panel.addView(scroll,lpTopW(MP,u(350),u(24)));
        final int[] selected={Math.max(0,Math.min(tvSrcNowIdx(),channel.srcs.size()-1))};final Runnable[] refill={null};
        refill[0]=()->{
            sitesPanel.removeAllViews();streamsPanel.removeAllViews();
            for(int i=0;i<channel.srcs.size();i++){final int source=i;TvSrc s=channel.srcs.get(i);TextView site=filterChip(s.site+"\n"+(s.stream.isEmpty()?"Търсене от сайта":(1+s.alts.size())+" потока"),i==selected[0],()->{selected[0]=source;refill[0].run();sitesPanel.getChildAt(source).requestFocusFromTouch();});site.setSingleLine(false);site.setMaxLines(2);site.setGravity(Gravity.CENTER_VERTICAL);sitesPanel.addView(site,lpTopW(MP,u(80),u(i==0?0:12)));}
            TvSrc source=channel.srcs.get(selected[0]);int count=source.stream.isEmpty()?0:1+source.alts.size();
            for(int i=0;i<count;i++){final int stream=i;boolean active=tvSrcNowIdx()==selected[0] && tvStreamNowIdx()==i;View choice=tvDashboardPill(active?IC_CHECK:IC_PLAY,"Поток "+(i+1)+(active?" · избран":" · резервен"),()->{int site=selected[0];streamDialog.dismiss();tvTuneStream(channel,site,stream-1);});choice.setBackground(TvTheme.control(U,active));streamsPanel.addView(choice,lpTopW(MP,u(68),u(i==0?0:12)));}
            streamsPanel.addView(tvDashboardPill(IC_REFRESH,"Провери за още потоци",()->{int site=selected[0];streamDialog.dismiss();if(tvMode && tvCur==channel && tvSrcNowIdx()==site && tvPlaying)tvMoreStart(true);else{tvMoreChan=channel;tvTuneUser(channel,site);}}),lpTopW(MP,u(56),u(16)));
        };refill[0].run();
        panel.addView(text("При прекъсване: потоците тук → другите сайтове за същия канал",17,TvTheme.MUTED,tfMedium,true),lpTopW(MP,WC,u(20)));
        LinearLayout footer=new LinearLayout(this);footer.addView(tvDashboardPill(IC_SLIDERS,"Управление на канала",()->{streamDialog.dismiss();tvChannelMenu(channel);}),lp(WC,u(52)));LinearLayout.LayoutParams back=lp(WC,u(52));back.leftMargin=u(16);footer.addView(tvDashboardPill(IC_CHECK,"Готово",()->streamDialog.dismiss()),back);panel.addView(footer,lpTopW(MP,u(52),u(22)));
        streamDialog=new AlertDialog.Builder(this).setView(panel).create();streamDialog.show();if(streamDialog.getWindow()!=null){streamDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);streamDialog.getWindow().setLayout(u(1080),WC);}if(streamsPanel.getChildCount()>0)streamsPanel.getChildAt(0).requestFocusFromTouch();
    }

    // Списъкът на каналите върху видеото.
    private void tvShowList() {
        if(tvChans.isEmpty() || tvListBox==null || !tvMode || tvVod)return;
        tvHideBannerNow();ui.removeCallbacks(tvZapGo);tvZapIdx=-1;
        tvListBox.removeAllViews();
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(u(48),u(32),u(48),u(32));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("Канали и програма",32,TvTheme.TEXT,Typeface.DEFAULT_BOLD,true),new LinearLayout.LayoutParams(0,WC,1f));
        header.addView(tvDashboardPill(IC_PLAY,"Пауза / Пусни",()->tvTogglePause()),lp(WC,u(48)));
        header.addView(tvDashboardPill(IC_SLIDERS,"Качество",()->tvQualityDialog()),lp(WC,u(48)));
        header.addView(tvDashboardPill(IC_TV,"Към видеото",()->tvHideListNow()),lp(WC,u(48)));
        content.addView(header,lp(MP,u(64)));
        tvPlayerGuide=newGuide(true);tvPlayerGuide.setChannels(guideChannels(false),tvCur==null?null:tvCur.id);
        tvPlayerGuide.setGuide(tvGuide,tvEpgError);
        LinearLayout.LayoutParams panel=new LinearLayout.LayoutParams(MP,0,1f);panel.topMargin=u(18);content.addView(tvPlayerGuide,panel);
        content.addView(text("↑ ↓ Избери канал   OK Гледай   → Програма и източници   Назад Затвори",17,TvTheme.MUTED,tfMedium,true),lpTopW(MP,WC,u(14)));
        tvListBox.addView(content,new FrameLayout.LayoutParams(MP,MP));
        tvListBox.setAlpha(1f);tvListBox.setVisibility(View.VISIBLE);tvListBox.bringToFront();
        tvPlayerGuide.focusChannels();
        tvEpgLoad();tvPanelClockTick();
    }

    // Клавиши, докато се гледа канал (списъкът върху видеото сам си взима стрелките и OK).
    private boolean tvKey(KeyEvent e) {
        if (tvVod) return vodKey(e);
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        // Задържане на OK: избор на качество.
        if (down && e.getRepeatCount() == 10 && (code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            tvHideListNow();
            tvQualityDialog();
            return true;
        }
        if (tvListShown()) return super.dispatchKeyEvent(e);
        if(tvControlsOpen && tvBanner!=null && (code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT || code==KeyEvent.KEYCODE_DPAD_UP || code==KeyEvent.KEYCODE_DPAD_DOWN || code==KeyEvent.KEYCODE_DPAD_CENTER || code==KeyEvent.KEYCODE_ENTER || code==KeyEvent.KEYCODE_NUMPAD_ENTER))return super.dispatchKeyEvent(e);
        // Звукът се оправя от системата.
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (!down) return true;
        int rep = e.getRepeatCount();
        boolean okKey = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_PAGE_UP:
                if (rep == 0 || rep % 5 == 0) tvZap(1);
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                if (rep == 0 || rep % 5 == 0) tvZap(-1);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (rep == 0) tvSwitchStream(1);
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (rep == 0) tvSwitchSource(1);
                break;
            case KeyEvent.KEYCODE_MENU:
                if (rep == 0) tvShowList();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (rep == 0) tvTogglePause();
                break;
            default:
                if (okKey && rep == 0) {
                    if (!tvPlaying && tvWait != null && tvWait.getVisibility() == View.VISIBLE) {
                        // Чака се дълго: OK отваря самия сайт, там каналът може да се пусне на ръка.
                        if (tvCapturing && tvCapSrc >= 0) tvFirstSrc = tvCapSrc;
                        tvGiveUp();
                    } else {
                        tvShowControls();
                    }
                }
                break;
        }
        return true;
    }


    // ====================== НАШИ ФИЛМИ ПРЕЗ СКРИТАТА СТРАНИЦА ======================
    // Филмът или епизодът се търси на заден план в адресите от „Източници“ (същата скрита страница
    // като при каналите), хваща се истинският поток и тръгва в родния плеър - без реклами.
    // Ако един източник не даде поток, се пробва следващият; чак накрая се отваря самият сайт.

    private static final long VOD_MIN_MS = 8 * 60 * 1000L; // по-кратко от това е реклама или клип

    private boolean tvVod;                  // гледа се филм (а не канал)
    private CTitle vodT;
    private int vodSeason, vodEpisode;
    private ArrayList<String> vodUrls = new ArrayList<>();
    private Runnable vodBack;               // връща към каталога
    private long vodResume;                 // откъде да продължи след скъсване или смяна на източника
    private String vodKey = "";             // ключ за запомненото място
    private long vodBackAt;
    private boolean vodSizeRetry;
    private long vodSaveAt;
    private long vodPending = -1;           // къде е насочено превъртането (докато не се изпълни)
    private boolean vodOkWasPlaying;
    private boolean vodDone;                // филмът е изгледан до края
    private String vodStatus;

    private FrameLayout vodBar;
    private TextView vodBarTitle, vodBarStatus, vodBarTime, vodBarClock;
    private VodProgress vodProg;
    private View vodChipSubs;

    // Лента за напредъка: пътека, заредено и изиграно.
    private static class VodProgress extends View {
        private float frac, buf;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        VodProgress(Context c) {
            super(c);
        }

        void set(float f, float b) {
            frac = Math.max(0f, Math.min(1f, f));
            buf = Math.max(frac, Math.min(1f, b));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float th = h * 0.36f;
            float top = (h - th) / 2f;
            float r = th / 2f;
            p.setColor(0x33FFFFFF);
            c.drawRoundRect(0, top, w, top + th, r, r, p);
            p.setColor(0x66FFFFFF);
            c.drawRoundRect(0, top, Math.max(th, w * buf), top + th, r, r, p);
            p.setColor(T_SKY);
            c.drawRoundRect(0, top, Math.max(th, w * frac), top + th, r, r, p);
            p.setColor(Color.WHITE);
            c.drawCircle(Math.max(h * 0.3f, Math.min(w - h * 0.3f, w * frac)), h / 2f, h * 0.3f, p);
        }
    }

    // Пуска филма или епизода. false = не може (няма адреси): тогава се продължава постарому.
    private void vodOrderSources(ArrayList<String> urls,boolean series){
        final String good=prefs.getString("vodGood:"+(series?"t":"m"),"");
        final long now=System.currentTimeMillis();
        Collections.sort(urls,(a,b)->Integer.compare(vodSourceRank(a,good,now),vodSourceRank(b,good,now)));
    }
    private int vodSourceRank(String url,String good,long now){
        String host=hostOf(url);long failed=prefs.getLong("vodBad:"+host,0);
        return (failed>0 && now>=failed && now-failed<5*60*1000L?100:0)+(host.equals(good)?-10:0);
    }

    private boolean vodStart(CTitle t, int season, int episode, Runnable back) {
        if (isFinishing()) return false;
        ArrayList<String> urls = sourceUrls(t, season, episode);
        if (urls.isEmpty()) return false;
        vodOrderSources(urls,t.tv);
        TvChan c = new TvChan();
        c.id = "vod";
        c.name = t.name;
        for (String u : urls) {
            TvSrc s = new TvSrc();
            s.site = hostOf(u);
            s.page = u;
            c.srcs.add(s);
        }
        vodT = t;
        vodSeason = season;
        vodEpisode = episode;
        vodUrls = urls;
        vodBack = back;
        vodKey = "vp:" + (t.tv ? "t" : "m") + t.id + (t.tv ? ":" + season + ":" + episode : "");
        vodResume = prefs.getLong(vodKey, 0);
        vodBackAt = 0;
        vodDone = false;
        vodPending = -1;
        vodSizeRetry = false;
        vodStatus = null;
        hideCatalog();
        tvVod = true;
        subReset();
        tvTuneUser(c, 0);
        return true;
    }

    private String vodTitleLine() {
        if (vodT == null) return "";
        return vodT.tv ? vodT.name + "  ·  С" + vodSeason + " Е" + vodEpisode : vodT.name;
    }

    // Приключва гледането на филма: чисти всичко (вика се от tvLeave, преди плеърът да се затвори).
    private void vodCleanup() {
        if (!tvVod) return;
        if (tvPlaying && player != null) vodSavePos(player.getCurrentPosition());
        tvVod = false;
        ui.removeCallbacks(vodTickRun);
        ui.removeCallbacks(vodSeekGo);
        ui.removeCallbacks(vodBarHideRun);
        vodPending = -1;
        vodT = null;
        vodUrls = new ArrayList<>();
        vodBack = null;
        vodStatus = null;
        vodHideBarNow();
        subCleanup();
    }

    // Нищо не тръгна: чак сега се отваря самият сайт на избрания източник (както преди).
    private void vodGiveUp(int idx) {
        final ArrayList<String> urls = vodUrls;
        final Runnable back = vodBack;
        final String label = vodT != null && vodT.tv
                ? vodT.name + " (С" + vodSeason + " Е" + vodEpisode + ")"
                : (vodT != null ? vodT.name : "Филм");
        tvLeave(false);
        if (urls == null || urls.isEmpty()) {
            if (back != null) back.run();
            else backToMenu();
            return;
        }
        if (idx < 0 || idx >= urls.size()) idx = 0;
        logEvent("Филм: не успях да го намеря сам, отварям сайта " + hostOf(urls.get(idx)), true);
        Toast.makeText(this, "„" + label + "“ не тръгна сам. Отварям сайта.", Toast.LENGTH_LONG).show();
        openSite(new String[]{label, urls.get(idx), "", "Наши филми"});
        srcQueue = urls;
        srcIdx = idx;
        catReturn = back;
        srcArm();
    }

    // Излиза от филма и се връща в каталога.
    private void vodExit() {
        Runnable b = vodBack;
        tvLeave(false);
        if (b != null) b.run();
        else backToMenu();
    }

    private void vodBackPressed() {
        if (!tvPlaying || vodDone) {
            vodExit();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - vodBackAt < 3000) {
            vodExit();
            return;
        }
        vodBackAt = now;
        vodShowBar(3000, "Натисни „Назад“ още веднъж за изход");
    }

    // Изречение върху екрана: върху лентата, докато върви филмът, иначе върху екрана за чакане.
    private void vodSay(String msg) {
        if (tvPlaying) vodShowBar(3000, msg);
        else tvWaitText(msg);
    }

    // ---- проверка на намерения поток, продължаване, край ----

    // Дали намереният поток прилича на цял филм: не е на живо, има дължина и картина.
    private boolean vodAccept(final String url, final HashMap<String, String> props, final String ua,
                              final int si, final boolean viaCapture) {
        if (player == null) return false;
        long d = player.getDuration();
        if (player.isCurrentMediaItemLive() || player.isCurrentMediaItemDynamic()) {
            tvTryFailed("на живо, а не филм");
            return false;
        }
        if (d == C.TIME_UNSET || d < VOD_MIN_MS) {
            tvTryFailed(d == C.TIME_UNSET ? "без дължина" : "кратко видео (" + (d / 1000) + " сек.)");
            return false;
        }
        VideoSize vs = player.getVideoSize();
        if (vs == null || vs.width <= 0) {
            if (!vodSizeRetry) {
                // Размерът на картината може да се получи малко след готовността.
                vodSizeRetry = true;
                final int my = tvTryGen;
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (my != tvTryGen || !tvVod || tvPlaying) return;
                        tvReady(url, props, ua, si, viaCapture);
                    }
                }, 1200);
                return false;
            }
            tvTryFailed("няма картина");
            return false;
        }
        return true;
    }

    // Картината на филма тръгна.
    private void vodOnPlaying() {
        String note = null;
        long dur = player != null ? player.getDuration() : C.TIME_UNSET;
        if (player != null && vodResume > 20000
                && (dur == C.TIME_UNSET || vodResume < dur - 90000)) {
            player.seekTo(vodResume);
            note = "Продължавам от " + vodFmt(vodResume) + "  ·  меню → „От началото“";
        }
        vodResume = 0;
        vodSizeRetry = false;
        ui.removeCallbacks(vodTickRun);
        vodSaveAt = SystemClock.uptimeMillis();
        ui.postDelayed(vodTickRun, 250);
        subOnPlaying();
        vodShowBar(note != null ? 6000 : 5000, note);
    }

    // Потокът свърши. Ако е близо до края - филмът е гледан; иначе е скъсване.
    private void vodEnded() {
        long dur = player != null ? player.getDuration() : C.TIME_UNSET;
        long pos = player != null ? player.getCurrentPosition() : 0;
        if (dur != C.TIME_UNSET && dur - pos > 90000) {
            tvDropped("потокът прекъсна");
            return;
        }
        if (!vodKey.isEmpty()) prefs.edit().remove(vodKey).apply();
        ui.removeCallbacks(vodTickRun);
        vodDone = true;
        subHide();
        int[] nx = vodNextEp();
        if (nx != null) {
            vodNextDialog(nx);
        } else {
            vodShowBar(0, "Край  ·  Назад — към каталога");
        }
    }

    // Кой е следващият епизод (в същия сезон или първият на следващия), или null.
    private int[] vodNextEp() {
        if (vodT == null || !vodT.tv) return null;
        for (int[] se : vodT.seasons) {
            if (se[0] == vodSeason && vodEpisode < se[1]) return new int[]{vodSeason, vodEpisode + 1};
        }
        int best = -1;
        for (int[] se : vodT.seasons) {
            if (se[0] > vodSeason && se[1] > 0 && (best < 0 || se[0] < best)) best = se[0];
        }
        return best > 0 ? new int[]{best, 1} : null;
    }

    private void vodNextDialog(final int[] nx) {
        LinearLayout card = tvCard("Край на епизода",
                "Следва сезон " + nx[0] + ", епизод " + nx[1] + ".");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout first = tvChoice(null, "Пусни следващия", "С" + nx[0] + " Е" + nx[1], 1, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                vodPlayNext(nx);
            }
        });
        list.addView(first, lpTopW(MP, u(62), 0));
        list.addView(tvChoice(null, "Към каталога", null, 0, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                vodExit();
            }
        }), lpTopW(MP, u(62), u(10)));
        card.addView(list, lpTopW(MP, WC, u(24)));
        tvDialogShow(card, 700, first);
    }

    private void vodPlayNext(int[] nx) {
        final CTitle t = vodT;
        final Runnable back = vodBack;
        tvLeave(false);
        if (t == null) return;
        prefs.edit().putString("tvlast:" + t.id, nx[0] + ":" + nx[1]).apply();
        if (!vodStart(t, nx[0], nx[1], back)) {
            if (back != null) back.run();
            else backToMenu();
        }
    }

    private void vodSavePos(long pos) {
        if (vodKey.isEmpty() || player == null) return;
        long dur = player.getDuration();
        SharedPreferences.Editor ed = prefs.edit();
        if (dur != C.TIME_UNSET && dur > 0 && pos > dur - 180000) ed.remove(vodKey);
        else if (pos > 90000) ed.putLong(vodKey, pos);
        else return;
        ed.apply();
    }

    private final Runnable vodTickRun = new Runnable() {
        @Override
        public void run() {
            if (!tvVod || !tvPlaying || player == null) return;
            long pos = player.getCurrentPosition();
            subTick(pos);
            if (vodBar != null && vodBar.getVisibility() == View.VISIBLE) vodUpdateBar(vodPending >= 0 ? vodPending : pos);
            long now = SystemClock.uptimeMillis();
            if (now - vodSaveAt > 20000) {
                vodSaveAt = now;
                vodSavePos(pos);
            }
            ui.postDelayed(this, 250);
        }
    };

    // ---- превъртане, пауза ----

    private void vodSeekBy(long delta) {
        if (player == null || !tvPlaying) return;
        long dur = player.getDuration();
        long base = vodPending >= 0 ? vodPending : player.getCurrentPosition();
        long t = base + delta;
        if (t < 0) t = 0;
        if (dur != C.TIME_UNSET && dur > 0 && t > dur - 2000) t = Math.max(0, dur - 2000);
        vodPending = t;
        vodShowBar(4000, null);
        vodUpdateBar(t);
        ui.removeCallbacks(vodSeekGo);
        ui.postDelayed(vodSeekGo, 450);
    }

    private final Runnable vodSeekGo = new Runnable() {
        @Override
        public void run() {
            long t = vodPending;
            vodPending = -1;
            if (t >= 0 && player != null && tvVod && tvPlaying) {
                player.seekTo(t);
                subSeeked();
                if (vodDone) {
                    // Превъртяно е след края: следенето на времето започва наново.
                    vodDone = false;
                    ui.removeCallbacks(vodTickRun);
                    ui.postDelayed(vodTickRun, 250);
                }
            }
        }
    };

    private void vodTogglePause() {
        if (player == null || !tvPlaying) return;
        boolean play = !player.getPlayWhenReady();
        player.setPlayWhenReady(play);
        vodShowBar(play ? 2500 : 0, play ? null : "❚❚  Пауза");
    }

    // Следващ (1) или предишен (-1) източник. Мястото във филма се запазва.
    private void vodSkipSource(int dir) {
        TvChan c = tvCur;
        if (c == null) return;
        int n = c.srcs.size();
        if (n < 2) {
            vodSay("Има само един източник");
            return;
        }
        if (tvPlaying && player != null) {
            long p = player.getCurrentPosition();
            if (p > 0) vodResume = p;
        }
        int next = ((tvSrcNowIdx() + dir) % n + n) % n;
        tvTuneUser(c, next);
    }

    // Клавиши, докато върви или се чака филм.
    private boolean vodKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.dispatchKeyEvent(e);
        }
        if (!down) return true;
        int rep = e.getRepeatCount();
        boolean okKey = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
        if (!tvPlaying || player == null) {
            // Чака се: OK отваря самия сайт, ◀ ▶ сменят източника.
            if (rep != 0) return true;
            if (okKey) {
                if (tvCapturing && tvCapSrc >= 0) tvFirstSrc = tvCapSrc;
                tvGiveUp();
            } else if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                vodSkipSource(-1);
            } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                vodSkipSource(1);
            }
            return true;
        }
        if (okKey) {
            if (rep == 0) {
                vodOkWasPlaying = player.getPlayWhenReady();
                vodTogglePause();
            } else if (rep == 10) {
                // Задържане на OK: меню (паузата от първото натискане се връща).
                player.setPlayWhenReady(vodOkWasPlaying);
                vodMenu();
            }
            return true;
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                if (rep != 0 && rep % 6 != 0) break;
                long step = rep < 12 ? 10000 : rep < 36 ? 30000 : 60000;
                vodSeekBy(code == KeyEvent.KEYCODE_DPAD_LEFT ? -step : step);
                break;
            }
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                vodSeekBy(-30000);
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                vodSeekBy(30000);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (rep == 0) subDialog();
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MENU:
                if (rep == 0) vodMenu();
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (rep == 0) vodTogglePause();
                break;
            default:
                break;
        }
        return true;
    }

    // ---- меню по време на филма ----

    private void vodMenu() {
        if (!tvVod || player == null || vodT == null) return;
        vodHideBarNow();
        LinearLayout card = tvCard(vodT.name, vodT.tv ? "Сезон " + vodSeason + "  ·  епизод " + vodEpisode : null);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout first = tvChoice(null, "Субтитри", subShort(), 1, new Runnable() {
            @Override
            public void run() {
                subDialog();
            }
        });
        list.addView(first, lpTopW(MP, u(62), 0));
        list.addView(tvChoice(null, "Синхрон на субтитрите", subShiftText(), 0, new Runnable() {
            @Override
            public void run() {
                subSyncDialog();
            }
        }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Размер на субтитрите", subSizeName(), 0, new Runnable() {
            @Override
            public void run() {
                subCycleSize();
                vodMenu();
            }
        }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Качество на видеото", qualityName(videoQuality()), 0, new Runnable() {
            @Override
            public void run() {
                tvQualityDialog();
            }
        }), lpTopW(MP, u(62), u(10)));
        int n = tvCur != null ? tvCur.srcs.size() : 0;
        if (n > 1) {
            list.addView(tvChoice(null, "Друг източник", (tvSrcNowIdx() + 1) + " от " + n, 0, new Runnable() {
                @Override
                public void run() {
                    tvDialogClose();
                    vodSkipSource(1);
                }
            }), lpTopW(MP, u(62), u(10)));
        }
        list.addView(tvChoice(null, "От началото", null, 0, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                if (player != null) {
                    player.seekTo(0);
                    subSeeked();
                }
                if (!vodKey.isEmpty()) prefs.edit().remove(vodKey).apply();
            }
        }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Затвори", null, 0, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
            }
        }), lpTopW(MP, u(62), u(10)));
        card.addView(list, lpTopW(MP, WC, u(24)));
        tvDialogShow(card, 760, first);
    }

    // ---- лентата долу: заглавие, време, бутони ----

    private static String vodFmt(long ms) {
        if (ms < 0 || ms == C.TIME_UNSET) return "--:--";
        long s = ms / 1000;
        long h = s / 3600;
        long m = s / 60 % 60;
        long ss = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, ss)
                : String.format(Locale.US, "%02d:%02d", m, ss);
    }

    private void vodBuildBar() {
        if (vodBar != null || nativeLayer == null) return;
        vodBar = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(u(56), u(36), u(56), u(28));
        col.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x000D1119,0xC90D1119,0xF50D1119}));

        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        vodBarTitle = text("", 38, T_TEXT, tfCondBold, true);
        r1.addView(vodBarTitle, new LinearLayout.LayoutParams(0, WC, 1f));
        r1.addView(tvDashboardPill(IC_BARS,"Субтитри",()->subDialog()),lp(WC,u(48)));
        vodBarStatus = text("", 19, T_SKY2, tfMedium, true);
        LinearLayout.LayoutParams stl = lp(WC, WC);
        stl.leftMargin = u(20);
        r1.addView(vodBarStatus, stl);
        vodBarClock = text("", 30, T_TEXT, tfCondBold, true);
        vodBarClock.setIncludeFontPadding(false);
        LinearLayout.LayoutParams ckl = lp(WC, WC);
        ckl.leftMargin = u(26);
        r1.addView(vodBarClock, ckl);
        col.addView(r1, lp(MP, WC));

        vodProg = new VodProgress(this);
        col.addView(vodProg, lpTopW(MP, u(30), u(10)));

        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        r3.setGravity(Gravity.CENTER_VERTICAL);
        vodBarTime = text("", 24, T_TEXT, tfCondBold, true);
        r3.addView(vodBarTime, new LinearLayout.LayoutParams(0, WC, 1f));
        String[][] ks = {{"OK", "пауза"}, {"◀ ▶", "превърти"}, {"▲", "субтитри"}, {"▼", "меню"}};
        for (int i = 0; i < ks.length; i++) {
            LinearLayout.LayoutParams kl = lp(WC, u(38));
            kl.leftMargin = u(10);
            View chip = tvKeyChip(ks[i][0], ks[i][1]);
            if (i == 2) vodChipSubs = chip;
            r3.addView(chip, kl);
        }
        col.addView(r3, lpTopW(MP, WC, u(8)));
        LinearLayout transport=new LinearLayout(this);transport.setGravity(Gravity.CENTER);
        transport.addView(playerControl("−10","Назад 10 секунди",()->vodSeekBy(-10000),false));
        vodPauseControl=playerControl("❚❚","Пауза / Пусни",()->vodTogglePause(),true);transport.addView(vodPauseControl);
        transport.addView(playerControl("+10","Напред 10 секунди",()->vodSeekBy(10000),false));
        LinearLayout.LayoutParams sourceButton=lp(WC,u(48));sourceButton.leftMargin=u(28);transport.addView(tvDashboardPill(IC_MONITOR,"Източник",()->vodMenu()),sourceButton);
        LinearLayout.LayoutParams qualityButton=lp(WC,u(48));qualityButton.leftMargin=u(14);transport.addView(tvDashboardPill(IC_SLIDERS,"Качество",()->tvQualityDialog()),qualityButton);
        col.addView(transport,lpTopW(MP,u(74),u(10)));


        vodBar.addView(col, new FrameLayout.LayoutParams(MP, WC));
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM);
        bl.leftMargin = 0;
        bl.rightMargin = 0;
        bl.bottomMargin = 0;
        vodBar.setVisibility(View.GONE);
        nativeLayer.addView(vodBar, bl);
    }

    private void vodFillBar() {
        if (vodBar == null) return;
        if(vodPauseControl!=null)vodPauseControl.setText(player!=null && player.getPlayWhenReady()?"❚❚":"▶");
        vodBarTitle.setText(vodTitleLine());
        boolean paused = player != null && !player.getPlayWhenReady();
        String st = vodStatus;
        if (st == null || st.isEmpty()) st = paused ? "❚❚  Пауза" : subStatus();
        vodBarStatus.setText(st);
        if (player != null) vodUpdateBar(vodPending >= 0 ? vodPending : player.getCurrentPosition());
    }

    private void vodUpdateBar(long pos) {
        if (vodBar == null || player == null) return;
        long dur = player.getDuration();
        float f = 0f;
        float b = 0f;
        if (dur != C.TIME_UNSET && dur > 0) {
            f = pos / (float) dur;
            b = player.getBufferedPosition() / (float) dur;
        }
        vodProg.set(f, b);
        vodBarTime.setText(vodFmt(pos) + "  /  " + vodFmt(dur));
    }

    private final Runnable vodBarHideRun = new Runnable() {
        @Override
        public void run() {
            if (player != null && !player.getPlayWhenReady() && tvPlaying) return; // на пауза лентата стои
            vodHideBar();
        }
    };

    // Показва лентата. hideMs = 0: стои, докато не бъде скрита (пауза, зареждане).
    private void vodShowBar(long hideMs, String status) {
        if (!tvVod || isFinishing()) return;
        vodBuildBar();
        if (vodBar == null) return;
        vodStatus = status;
        vodFillBar();
        tvFadeIn(vodBar);
        subLift(true);
        ui.removeCallbacks(vodBarHideRun);
        if (hideMs > 0) ui.postDelayed(vodBarHideRun, hideMs);
    }

    private void vodHideBar() {
        ui.removeCallbacks(vodBarHideRun);
        vodStatus = null;
        if (vodBar != null) tvFadeOut(vodBar);
        subLift(false);
    }

    private void vodHideBarNow() {
        ui.removeCallbacks(vodBarHideRun);
        if (vodBar != null) tvHideNow(vodBar);
        subLift(false);
    }

    // Екранът за чакане на филма: един етикет за източника, който се пробва, и броячът.
    private void vodRefreshWait() {
        if (tvWaitChips == null || tvCur == null) return;
        tvWaitChips.removeAllViews();
        int n = tvCur.srcs.size();
        int cur = tvStep >= 0 && tvStep < tvPlan.size() ? tvPlan.get(tvStep)[0] : 0;
        if (cur < 0 || cur >= n) cur = 0;
        String[] words = {tvCur.srcs.get(cur).site + "  ·  търся потока",
                "източник " + (cur + 1) + " от " + n};
        for (int i = 0; i < words.length; i++) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(u(20), 0, u(22), 0);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(u(22));
            if (i == 0) {
                g.setColor(0x1F38BDF8);
                g.setStroke(Math.max(1, u(1.5f)), T_SKY);
            } else {
                g.setColor(0x12FFFFFF);
                g.setStroke(Math.max(1, u(1.5f)), 0x1FFFFFFF);
            }
            chip.setBackground(g);
            chip.addView(text(words[i], 18, i == 0 ? T_TEXT : T_TEXT3, tfMedium, true));
            LinearLayout.LayoutParams cl = lp(WC, u(44));
            if (i > 0) cl.leftMargin = u(12);
            tvWaitChips.addView(chip, cl);
        }
    }

    // ====================== СУБТИТРИ ======================
    // Четири източника (OpenSubtitles, Subdl, SubSource, Subs.sab.bz) се питат едновременно. Субтитрите
    // се свалят, превръщат се в текст (кирилицата често е в windows-1251), разчитат се (SRT, VTT, ASS) и
    // се рисуват върху видеото. Синхронът (изместването във времето) се променя по всяко време.

    private static final ExecutorService SUBNET = newSubPool();

    private static ExecutorService newSubPool() {
        ThreadPoolExecutor p = new ThreadPoolExecutor(4, 4, 20, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), lowPriority("tvs-sub"));
        p.allowCoreThreadTimeOut(true);
        return p;
    }

    private static class SubQuery {
        boolean tv;
        String title = "", orig = "", year = "", imdb = "";
        int tmdb, season, episode;
        final ArrayList<String> langs = new ArrayList<>();

        // Заглавие за търсене по име: оригиналното, ако е с латински букви.
        String latin() {
            if (!orig.isEmpty()) {
                int ok = 0;
                for (int i = 0; i < orig.length(); i++) {
                    if (orig.charAt(i) < 0x250) ok++;
                }
                if (ok * 10 >= orig.length() * 8) return orig;
            }
            return title;
        }

        String key() {
            StringBuilder sb = new StringBuilder();
            sb.append(tv ? "t" : "m").append(tmdb).append('|').append(season).append('|').append(episode);
            for (String l : langs) sb.append('|').append(l);
            return sb.toString();
        }
    }

    private static class SubItem {
        String provider = "", providerName = "", lang = "", release = "", ref = "";
        int downloads;
        boolean trusted, hearing;

        String uid() {
            return provider + "|" + ref;
        }
    }

    private static class SubFile {
        final String name;
        final byte[] bytes;

        SubFile(String n, byte[] b) {
            name = n;
            bytes = b;
        }
    }

    private static class SubCues {
        long[] from = new long[0], to = new long[0];
        String[] txt = new String[0];
        long end;
    }

    private static class SubHttpErr extends IOException {
        final int code;

        SubHttpErr(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    // ---- настройки ----

    private String subCfg(String prefKey, String codeValue) {
        String v = prefs.getString(prefKey, "").trim();
        return v.isEmpty() ? codeValue.trim() : v;
    }

    private String subOsKey() {
        return subCfg("osKey", SUB_OS_KEY);
    }

    private String subOsUser() {
        return subCfg("osUser", SUB_OS_USER);
    }

    private String subOsPass() {
        return subCfg("osPass", SUB_OS_PASS);
    }

    private String subSubdlKey() {
        return subCfg("subdlKey", SUB_SUBDL_KEY);
    }

    private String subSsKey() {
        return subCfg("ssKey", SUB_SUBSOURCE_KEY);
    }

    private static final String[] SUB_IDS = {"sabbz", "opensubtitles", "subdl", "subsource"};

    private static String subProviderName(String id) {
        if (id.equals("opensubtitles")) return "OpenSubtitles";
        if (id.equals("subdl")) return "Subdl";
        if (id.equals("subsource")) return "SubSource";
        return "Subs.sab.bz";
    }

    private boolean subConfigured(String id, SubQuery q) {
        if (id.equals("opensubtitles")) {
            return !subOsKey().isEmpty() && !subOsUser().isEmpty() && !subOsPass().isEmpty();
        }
        if (id.equals("subdl")) return !subSubdlKey().isEmpty();
        if (id.equals("subsource")) return !subSsKey().isEmpty();
        return q == null || q.langs.contains("bg");
    }

    private static final String[][] SUB_LANG_SETS = {
            {"bg"}, {"bg", "en"}, {"en"}, {"en", "bg"}};
    private static final String[] SUB_LANG_NAMES = {"Български", "Български, после English",
            "English", "English, после български"};
    private static final String[] SUB_SIZE_NAMES = {"малък", "среден", "голям"};
    private static final float[] SUB_SIZE_UNITS = {30f, 38f, 48f};

    private int subLangSet() {
        int i = prefs.getInt("subLang", 1);
        return i < 0 || i >= SUB_LANG_SETS.length ? 1 : i;
    }

    private int subSizeIdx() {
        int i = prefs.getInt("subSize", 1);
        return i < 0 || i >= SUB_SIZE_UNITS.length ? 1 : i;
    }

    private String subSizeName() {
        return SUB_SIZE_NAMES[subSizeIdx()];
    }

    private void subCycleSize() {
        prefs.edit().putInt("subSize", (subSizeIdx() + 1) % SUB_SIZE_UNITS.length).apply();
        subApplyStyle();
    }

    // ---- състояние ----

    private SubQuery subQ;
    private final ArrayList<SubItem> subResults = new ArrayList<>();
    private final ArrayList<String> subNotes = new ArrayList<>();
    private int subPending;
    private volatile int subGen;
    private boolean subSearched;
    private boolean subAutoDone;
    private boolean subWantAuto;
    private boolean subLoading;
    private boolean subSearching;
    private String subSavedUid = "";
    private long subSavedShift;
    private boolean subOff;
    private long[] subFrom = new long[0];
    private long[] subTo = new long[0];
    private String[] subTxt = new String[0];
    private long subShift;
    private SubItem subCur;
    private String subShown = "";
    private TextView subView;
    private Dialog subDlg;

    private final ThreadLocal<Integer> subRequestGeneration=new ThreadLocal<>();
    private final java.util.Set<HttpURLConnection> subConnections=java.util.Collections.synchronizedSet(new HashSet<>());
    private void subExecute(int generation,Runnable action){
        SUBNET.execute(()->{if(generation!=subGen)return;subRequestGeneration.set(generation);
            try{action.run();}finally{subRequestGeneration.remove();}});
    }
    private void subCancelRequests(){
        ArrayList<HttpURLConnection> active;
        synchronized(subConnections){active=new ArrayList<>(subConnections);subConnections.clear();}
        for(HttpURLConnection connection:active)connection.disconnect();
    }

    // Нов филм: всичко от предишния се чисти.
    private void subReset() {
        subGen++;
        subCancelRequests();
        ui.removeCallbacks(subWatchRun);
        subResults.clear();
        subNotes.clear();
        subPending = 0;
        subSearched = false;
        subAutoDone = false;
        subWantAuto = false;
        subLoading = false;
        subSearching = false;
        subSavedUid = "";
        subSavedShift = 0;
        subOff = false;
        subFrom = new long[0];
        subTo = new long[0];
        subTxt = new String[0];
        subShift = 0;
        subCur = null;
        subShown = "";
        subQ = null;
        if (subView != null) subView.setVisibility(View.GONE);
    }

    private void subCleanup() {
        subReset();
        subDlg = null;
        if (subView != null && nativeLayer != null) {
            try {
                nativeLayer.removeView(subView);
            } catch (Exception ignored) {
            }
        }
        subView = null;
    }

    private SubQuery subBuildQuery() {
        SubQuery q = new SubQuery();
        q.tv = vodT.tv;
        q.tmdb = vodT.id;
        q.title = vodT.name;
        q.orig = vodT.orig;
        q.year = vodT.year;
        q.imdb = vodT.imdb;
        q.season = vodSeason;
        q.episode = vodEpisode;
        for (String l : SUB_LANG_SETS[subLangSet()]) q.langs.add(l);
        return q;
    }

    // ---- мрежа ----

    private static final String SUB_UA = "tvsites v1.0";

    private byte[] subHttp(String url, String method, Map<String, String> headers, String body)
            throws IOException {
        Integer expected=subRequestGeneration.get();
        final int generation=expected==null?subGen:expected;
        if(generation!=subGen)throw new IOException("Търсенето е прекратено");
        String cur = url;
        String m = method;
        String b = body;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
            synchronized(subConnections){
                if(generation!=subGen){c.disconnect();throw new IOException("Търсенето е прекратено");}
                subConnections.add(c);
            }
            try {
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(false);
                c.setRequestMethod(m);
                c.setRequestProperty("User-Agent", SUB_UA);
                if (headers != null) {
                    for (Map.Entry<String, String> en : headers.entrySet()) {
                        c.setRequestProperty(en.getKey(), en.getValue());
                    }
                }
                if (b != null) {
                    byte[] bb = b.getBytes("UTF-8");
                    c.setDoOutput(true);
                    c.setFixedLengthStreamingMode(bb.length);
                    OutputStream os = c.getOutputStream();
                    os.write(bb);
                    os.close();
                }
                int rc = c.getResponseCode();
                if (rc >= 300 && rc < 400) {
                    String loc = c.getHeaderField("Location");
                    if (loc == null || loc.isEmpty()) throw new IOException("пренасочване без адрес");
                    cur = new URL(new URL(cur), loc).toString();
                    if (rc != 307 && rc != 308) {
                        m = "GET";
                        b = null;
                    }
                    continue;
                }
                InputStream in = rc >= 400 ? c.getErrorStream() : c.getInputStream();
                byte[] data = subReadAll(in, 12 * 1024 * 1024);
                if (rc >= 400) {
                    String msg = "HTTP " + rc;
                    try {
                        String t = new String(data, "UTF-8").trim();
                        if (t.startsWith("{")) {
                            JSONObject o = new JSONObject(t);
                            String mm = jstr(o, "message");
                            if (mm.isEmpty()) mm = jstr(o, "error");
                            if (!mm.isEmpty()) msg += ": " + mm;
                        }
                    } catch (Exception ignored) {
                        // тялото не е JSON
                    }
                    throw new SubHttpErr(rc, msg);
                }
                return data;
            } finally {
                subConnections.remove(c);
                c.disconnect();
            }
        }
        throw new IOException("твърде много пренасочвания");
    }

    private static byte[] subReadAll(InputStream in, int max) throws IOException {
        if (in == null) return new byte[0];
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) throw new IOException("файлът е твърде голям");
            }
            return out.toByteArray();
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String subStr(byte[] b) throws IOException {
        return new String(b, "UTF-8");
    }

    private static String subEnc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static String subLangNorm(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        if (s.equals("bulgarian")) return "bg";
        if (s.equals("english")) return "en";
        if (s.equals("russian")) return "ru";
        if (s.equals("turkish")) return "tr";
        if (s.equals("greek")) return "el";
        if (s.equals("romanian")) return "ro";
        if (s.equals("serbian")) return "sr";
        if (s.equals("german")) return "de";
        int k = s.indexOf('-');
        if (k > 0) s = s.substring(0, k);
        k = s.indexOf('_');
        if (k > 0) s = s.substring(0, k);
        return s.length() > 2 ? s.substring(0, 2) : s;
    }

    private static String subJoin(List<String> l, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(s);
        }
        return sb.toString();
    }

    // ---- доставчици: търсене ----

    private List<SubItem> subSearchOne(String id, SubQuery q) throws Exception {
        if (id.equals("opensubtitles")) return subOsSearch(q);
        if (id.equals("subdl")) return subSubdlSearch(q);
        if (id.equals("subsource")) return subSsSearch(q);
        return subSabSearch(q);
    }

    private String osHost = "api.opensubtitles.com";
    private String osToken;
    private long osTokenAt;

    private HashMap<String, String> osHeaders() {
        HashMap<String, String> h = new HashMap<>();
        h.put("Api-Key", subOsKey());
        h.put("User-Agent", SUB_UA);
        h.put("Accept", "application/json");
        h.put("Content-Type", "application/json");
        return h;
    }

    private synchronized String osLogin(boolean force) throws Exception {
        String user = subOsUser();
        String pass = subOsPass();
        if (user.isEmpty() || pass.isEmpty()) return null;
        if (!force && osToken != null && System.currentTimeMillis() - osTokenAt < 20L * 3600 * 1000) {
            return osToken;
        }
        JSONObject body = new JSONObject().put("username", user).put("password", pass);
        JSONObject resp = new JSONObject(subStr(subHttp("https://api.opensubtitles.com/api/v1/login",
                "POST", osHeaders(), body.toString())));
        String bu = jstr(resp, "base_url");
        if (!bu.isEmpty()) {
            bu = bu.replace("https://", "").replace("http://", "");
            while (bu.endsWith("/")) bu = bu.substring(0, bu.length() - 1);
            if (!bu.isEmpty()) osHost = bu;
        }
        osToken = jstr(resp, "token");
        osTokenAt = System.currentTimeMillis();
        return osToken.isEmpty() ? null : osToken;
    }

    private List<SubItem> subOsSearch(SubQuery q) throws Exception {
        String imdb = q.imdb.startsWith("tt") ? q.imdb.substring(2) : q.imdb;
        java.util.TreeMap<String, String> p = new java.util.TreeMap<>();
        p.put("languages", subJoin(q.langs, ","));
        p.put("order_by", "download_count");
        p.put("order_direction", "desc");
        if (q.tv) {
            p.put("type", "episode");
            if (!imdb.isEmpty()) p.put("parent_imdb_id", imdb);
            else p.put("query", q.latin());
            p.put("season_number", String.valueOf(q.season));
            p.put("episode_number", String.valueOf(q.episode));
        } else {
            p.put("type", "movie");
            if (!imdb.isEmpty()) {
                p.put("imdb_id", imdb);
            } else {
                p.put("query", q.latin());
                if (!q.year.isEmpty()) p.put("year", q.year);
            }
        }
        StringBuilder qs = new StringBuilder();
        for (Map.Entry<String, String> en : p.entrySet()) {
            if (qs.length() > 0) qs.append('&');
            qs.append(en.getKey()).append('=').append(subEnc(en.getValue()));
        }
        String url = "https://" + osHost + "/api/v1/subtitles?" + qs;
        JSONObject j = new JSONObject(subStr(subHttp(url, "GET", osHeaders(), null)));
        JSONArray data = j.optJSONArray("data");
        ArrayList<SubItem> out = new ArrayList<>();
        if (data == null) return out;
        for (int i = 0; i < data.length(); i++) {
            JSONObject o = data.optJSONObject(i);
            if (o == null) continue;
            JSONObject a = o.optJSONObject("attributes");
            if (a == null) continue;
            JSONArray files = a.optJSONArray("files");
            if (files == null || files.length() == 0) continue;
            JSONObject f0 = files.optJSONObject(0);
            if (f0 == null) continue;
            long fileId = f0.optLong("file_id");
            if (fileId <= 0) continue;
            SubItem it = new SubItem();
            it.provider = "opensubtitles";
            it.providerName = "OpenSubtitles";
            it.lang = subLangNorm(jstr(a, "language"));
            it.release = jstr(a, "release");
            if (it.release.isEmpty()) it.release = jstr(f0, "file_name");
            it.ref = String.valueOf(fileId);
            it.downloads = a.optInt("download_count");
            it.trusted = a.optBoolean("from_trusted", false);
            it.hearing = a.optBoolean("hearing_impaired", false);
            out.add(it);
        }
        return out;
    }

    private List<SubFile> subOsDownload(SubItem r) throws Exception {
        String tk = osLogin(false);
        if (tk == null) throw new IOException("въведи потребител и парола за OpenSubtitles");
        String body = new JSONObject().put("file_id", Long.parseLong(r.ref)).put("sub_format", "srt").toString();
        String resp;
        try {
            resp = subOsCall(body, tk);
        } catch (SubHttpErr e) {
            if (e.code != 401) throw e;
            tk = osLogin(true);
            if (tk == null) throw e;
            resp = subOsCall(body, tk);
        }
        JSONObject j = new JSONObject(resp);
        String link = jstr(j, "link");
        if (link.isEmpty()) {
            String m = jstr(j, "message");
            throw new IOException(m.isEmpty() ? "няма адрес за сваляне" : m);
        }
        ArrayList<SubFile> out = new ArrayList<>();
        String fn = jstr(j, "file_name");
        out.add(new SubFile(fn.isEmpty() ? "sub.srt" : fn, subHttp(link, "GET", null, null)));
        return out;
    }

    private String subOsCall(String body, String token) throws Exception {
        HashMap<String, String> h = osHeaders();
        h.put("Authorization", "Bearer " + token);
        return subStr(subHttp("https://" + osHost + "/api/v1/download", "POST", h, body));
    }

    private List<SubItem> subSubdlSearch(SubQuery q) throws Exception {
        StringBuilder sb = new StringBuilder("https://api.subdl.com/api/v1/subtitles?api_key=");
        sb.append(subEnc(subSubdlKey())).append("&subs_per_page=30&languages=");
        StringBuilder ls = new StringBuilder();
        for (String l : q.langs) {
            if (ls.length() > 0) ls.append(',');
            ls.append(l.toUpperCase(Locale.US));
        }
        sb.append(ls);
        if (!q.imdb.isEmpty()) sb.append("&imdb_id=").append(subEnc(q.imdb));
        else sb.append("&film_name=").append(subEnc(q.latin()));
        sb.append("&type=").append(q.tv ? "tv" : "movie");
        if (q.tv) sb.append("&season_number=").append(q.season).append("&episode_number=").append(q.episode);
        JSONObject j = new JSONObject(subStr(subHttp(sb.toString(), "GET", null, null)));
        if (!j.optBoolean("status", true)) {
            String e = jstr(j, "error");
            throw new IOException(e.isEmpty() ? "грешка" : e);
        }
        JSONArray arr = j.optJSONArray("subtitles");
        ArrayList<SubItem> out = new ArrayList<>();
        if (arr == null) return out;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String url = jstr(o, "url");
            if (url.isEmpty()) continue;
            SubItem it = new SubItem();
            it.provider = "subdl";
            it.providerName = "Subdl";
            String lg = jstr(o, "language");
            if (lg.isEmpty()) lg = jstr(o, "lang");
            it.lang = subLangNorm(lg);
            it.release = jstr(o, "release_name");
            if (it.release.isEmpty()) it.release = jstr(o, "name");
            it.ref = url;
            it.hearing = o.optBoolean("hi", false);
            out.add(it);
        }
        return out;
    }

    private List<SubFile> subSubdlDownload(SubItem r) throws Exception {
        String url = r.ref.startsWith("http") ? r.ref : "https://dl.subdl.com" + r.ref;
        return subExtract(subHttp(url, "GET", null, null));
    }

    // SubSource: схемата на отговорите не е напълно известна - работи „най-добро усилие“.
    private static final String SS_BASE = "https://api.subsource.net/api/v1";

    private HashMap<String, String> ssHeaders() {
        HashMap<String, String> h = new HashMap<>();
        h.put("X-API-Key", subSsKey());
        h.put("Accept", "application/json");
        return h;
    }

    private static JSONArray subListOf(JSONObject j) {
        JSONArray a = j.optJSONArray("data");
        if (a == null) a = j.optJSONArray("results");
        if (a == null) a = j.optJSONArray("subtitles");
        if (a == null) a = j.optJSONArray("movies");
        return a == null ? new JSONArray() : a;
    }

    private static String subFlat(JSONObject o, String name) {
        Object v = o.opt(name);
        if (v == null || v == JSONObject.NULL) return "";
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            return a.length() > 0 ? a.optString(0, "") : "";
        }
        return v.toString();
    }

    private List<SubItem> subSsSearch(SubQuery q) throws Exception {
        String murl = !q.imdb.isEmpty()
                ? SS_BASE + "/movies/search?searchType=imdb&imdb=" + subEnc(q.imdb)
                : SS_BASE + "/movies/search?searchType=text&q=" + subEnc(q.latin());
        JSONArray found = subListOf(new JSONObject(subStr(subHttp(murl, "GET", ssHeaders(), null))));
        ArrayList<SubItem> out = new ArrayList<>();
        if (found.length() == 0) return out;
        JSONObject first = found.optJSONObject(0);
        if (first == null) return out;
        String movieId = jstr(first, "movieId");
        if (movieId.isEmpty()) movieId = jstr(first, "id");
        if (movieId.isEmpty()) return out;
        String[][] names = {{"bg", "bulgarian"}, {"en", "english"}, {"ru", "russian"}, {"tr", "turkish"},
                {"el", "greek"}, {"ro", "romanian"}, {"sr", "serbian"}, {"de", "german"}};
        for (String lang : q.langs) {
            String full = null;
            for (String[] n : names) {
                if (n[0].equals(lang)) full = n[1];
            }
            if (full == null) continue;
            JSONArray arr = subListOf(new JSONObject(subStr(subHttp(
                    SS_BASE + "/subtitles?movieId=" + subEnc(movieId) + "&language=" + subEnc(full),
                    "GET", ssHeaders(), null))));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String sid = jstr(o, "subtitleId");
                if (sid.isEmpty()) sid = jstr(o, "id");
                if (sid.isEmpty()) continue;
                String release = subFlat(o, "releaseInfo");
                if (release.isEmpty()) release = subFlat(o, "release_info");
                if (release.isEmpty()) release = subFlat(o, "releases");
                if (release.isEmpty()) release = jstr(o, "name");
                if (q.tv) {
                    java.util.regex.Pattern ep = java.util.regex.Pattern.compile("(?i)s0*" + q.season
                            + " ?e0*" + q.episode + "(?!\\d)|\\b" + q.season + "x0*" + q.episode + "(?!\\d)");
                    java.util.regex.Pattern pack = java.util.regex.Pattern.compile("(?i)\\bs0*" + q.season
                            + "\\b(?! ?e\\d)|season ?0*" + q.season + "\\b");
                    if (!ep.matcher(release).find() && !pack.matcher(release).find()) continue;
                }
                SubItem it = new SubItem();
                it.provider = "subsource";
                it.providerName = "SubSource";
                it.lang = lang;
                it.release = release;
                it.ref = sid;
                it.downloads = o.optInt("downloads");
                it.hearing = o.optBoolean("hearingImpaired", false);
                out.add(it);
            }
        }
        return out;
    }

    private List<SubFile> subSsDownload(SubItem r) throws Exception {
        return subExtract(subHttp(SS_BASE + "/subtitles/" + r.ref + "/download", "GET", ssHeaders(), null));
    }

    // Subs.sab.bz: български субтитри, няма API - чете се страницата с резултатите (windows-1251).
    private static final String SAB = "http://subs.sab.bz";

    private List<SubItem> subSabSearch(SubQuery q) throws Exception {
        if (!q.langs.contains("bg")) return new ArrayList<>();
        String name = q.latin();
        if (q.tv) name += String.format(Locale.US, " S%02dE%02d", q.season, q.episode);
        String url = SAB + "/index.php?act=search&movie=" + subEnc(name) + "&select-language=2&upl=&yr="
                + subEnc(q.year);
        HashMap<String, String> h = new HashMap<>();
        h.put("Referer", SAB + "/");
        String html = new String(subHttp(url, "GET", h, null), java.nio.charset.Charset.forName("windows-1251"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "<a\\b[^>]*?href\\s*=\\s*[\"']([^\"']*attach_id=(\\d+)[^\"']*)[\"'][^>]*>(.*?)</a>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(html);
        LinkedHashMap<String, String> labels = new LinkedHashMap<>();
        while (m.find() && labels.size() < 30) {
            String id = m.group(2);
            String label = subCleanHtml(m.group(3));
            if (label.isEmpty()) {
                // Няма текст в връзката: взема се редът от таблицата.
                int a = html.lastIndexOf("<tr", m.start());
                int b = html.indexOf("</tr>", m.end());
                if (a >= 0 && b > a && b - a < 3000) label = subCleanHtml(html.substring(a, b));
            }
            String old = labels.get(id);
            if (old == null || (label.length() > old.length() && label.length() < 160)) {
                labels.put(id, label.length() > 140 ? label.substring(0, 140) : label);
            }
        }
        ArrayList<SubItem> out = new ArrayList<>();
        for (Map.Entry<String, String> en : labels.entrySet()) {
            if (out.size() >= 15) break;
            SubItem it = new SubItem();
            it.provider = "sabbz";
            it.providerName = "Subs.sab.bz";
            it.lang = "bg";
            it.release = en.getValue().isEmpty() ? "Субтитри " + en.getKey() : en.getValue();
            it.ref = en.getKey();
            out.add(it);
        }
        return out;
    }

    private List<SubFile> subSabDownload(SubItem r) throws Exception {
        HashMap<String, String> h = new HashMap<>();
        h.put("Referer", SAB + "/");
        return subExtract(subHttp(SAB + "/index.php?act=download&attach_id=" + r.ref, "GET", h, null));
    }

    private static String subCleanHtml(String s) {
        String t = s.replaceAll("(?s)<[^>]*>", " ");
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'");
        return t.replaceAll("\\s+", " ").trim();
    }

    private List<SubFile> subDownloadOne(SubItem r) throws Exception {
        if (r.provider.equals("opensubtitles")) return subOsDownload(r);
        if (r.provider.equals("subdl")) return subSubdlDownload(r);
        if (r.provider.equals("subsource")) return subSsDownload(r);
        return subSabDownload(r);
    }

    // ---- архиви, текст, разчитане ----

    private static boolean subIsSubExt(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".srt") || n.endsWith(".ass") || n.endsWith(".ssa") || n.endsWith(".vtt");
    }

    private List<SubFile> subExtract(byte[] b) throws Exception {
        ArrayList<SubFile> out = new ArrayList<>();
        if (b.length > 4 && b[0] == 'P' && b[1] == 'K') {
            java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(b));
            try {
                java.util.zip.ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    if (e.isDirectory() || !subIsSubExt(e.getName())) continue;
                    byte[] d = subReadZipEntry(z);
                    String nm = e.getName();
                    int k = nm.lastIndexOf('/');
                    if (d.length > 0) out.add(new SubFile(k >= 0 ? nm.substring(k + 1) : nm, d));
                }
            } finally {
                z.close();
            }
            return out;
        }
        if (b.length > 7 && b[0] == 'R' && b[1] == 'a' && b[2] == 'r') return subUnrar(b);
        out.add(new SubFile("sub.srt", b));
        return out;
    }

    private static byte[] subReadZipEntry(java.util.zip.ZipInputStream z) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = z.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > 6 * 1024 * 1024) break;
        }
        return out.toByteArray();
    }

    // RAR се разархивира с библиотеката junrar, ако е добавена към приложението (иначе се съобщава).
    private List<SubFile> subUnrar(byte[] b) throws Exception {
        Class<?> c;
        try {
            c = Class.forName("com.github.junrar.Junrar");
        } catch (ClassNotFoundException e) {
            throw new IOException("архивът е RAR, а тази версия не го чете (избери друг субтитър)");
        }
        File dir = new File(getCacheDir(), "sub" + System.nanoTime());
        File rar = new File(dir, "a.rar");
        File dest = new File(dir, "out");
        ArrayList<SubFile> out = new ArrayList<>();
        try {
            dest.mkdirs();
            java.io.FileOutputStream fo = new java.io.FileOutputStream(rar);
            fo.write(b);
            fo.close();
            c.getMethod("extract", File.class, File.class).invoke(null, rar, dest);
            subCollect(dest, out);
        } finally {
            subDelete(dir);
        }
        return out;
    }

    private static void subCollect(File d, ArrayList<SubFile> out) throws IOException {
        File[] fs = d.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                subCollect(f, out);
            } else if (subIsSubExt(f.getName()) && f.length() > 0 && f.length() < 6L * 1024 * 1024) {
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                out.add(new SubFile(f.getName(), subReadAll(in, 7 * 1024 * 1024)));
            }
        }
    }

    private static void subDelete(File f) {
        File[] fs = f.listFiles();
        if (fs != null) {
            for (File x : fs) subDelete(x);
        }
        f.delete();
    }

    // Explicit episode markers take priority over file size. Manual selection can override a mismatch.
    private static SubFile subPick(List<SubFile> files, boolean tv, int season, int episode, boolean auto) {
        String[] names=new String[files.size()];int[] sizes=new int[files.size()];
        for(int i=0;i<files.size();i++){names[i]=files.get(i).name;sizes[i]=files.get(i).bytes.length;}
        int index=SubtitleMatch.pick(names,sizes,tv,season,episode,auto);
        return index<0?null:files.get(index);
    }

    // Субтитрите често са в windows-1251: всичко се превръща в обикновен текст.
    private static String subDecode(byte[] b, String lang) {
        try {
            if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
                return new String(b, 3, b.length - 3, "UTF-8");
            }
            if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
                return new String(b, 2, b.length - 2, "UTF-16LE");
            }
            if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
                return new String(b, 2, b.length - 2, "UTF-16BE");
            }
            try {
                java.nio.charset.CharsetDecoder dec = java.nio.charset.Charset.forName("UTF-8").newDecoder()
                        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
                return dec.decode(java.nio.ByteBuffer.wrap(b)).toString();
            } catch (java.nio.charset.CharacterCodingException e) {
                // не е UTF-8: следва кодовата таблица според езика
            }
            String cs;
            if (lang.equals("bg") || lang.equals("ru") || lang.equals("sr") || lang.equals("mk")
                    || lang.equals("uk")) cs = "windows-1251";
            else if (lang.equals("el")) cs = "windows-1253";
            else if (lang.equals("tr")) cs = "windows-1254";
            else if (lang.equals("ro") || lang.equals("pl") || lang.equals("cs") || lang.equals("hu")) cs = "windows-1250";
            else cs = "windows-1252";
            return new String(b, cs);
        } catch (Exception e) {
            return new String(b);
        }
    }

    private static long subStamp(String s) {
        // 00:01:02,345  |  01:02.345  |  1:02:03.45
        String[] p = s.trim().split("[:.,]");
        try {
            long h = 0, m, sec, ms;
            if (p.length >= 4) {
                h = Long.parseLong(p[0]);
                m = Long.parseLong(p[1]);
                sec = Long.parseLong(p[2]);
                ms = subFrac(p[3]);
            } else if (p.length == 3) {
                m = Long.parseLong(p[0]);
                sec = Long.parseLong(p[1]);
                ms = subFrac(p[2]);
            } else {
                return -1;
            }
            return ((h * 60 + m) * 60 + sec) * 1000 + ms;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long subFrac(String f) {
        String t = f.length() > 3 ? f.substring(0, 3) : f;
        while (t.length() < 3) t += "0";
        return Long.parseLong(t);
    }

    private static String subCleanText(String s) {
        String t = s.replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ");
        t = t.replaceAll("(?s)\\{[^}]*\\}", "").replaceAll("(?s)<[^>]*>", "");
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'");
        StringBuilder sb = new StringBuilder();
        int lines = 0;
        for (String ln : t.split("\n")) {
            String x = ln.replace('\r', ' ').trim();
            if (x.isEmpty()) continue;
            if (lines >= 4) break;
            if (sb.length() > 0) sb.append('\n');
            sb.append(x);
            lines++;
        }
        return sb.toString();
    }

    private static class SubCue {
        long a, b;
        String t;
    }

    private static SubCues subParse(String text) {
        ArrayList<SubCue> list = new ArrayList<>();
        String norm = text.replace("\r\n", "\n").replace('\r', '\n');
        if (norm.contains("[Script Info]") || norm.contains("\nDialogue:") || norm.startsWith("Dialogue:")) {
            subParseAss(norm, list);
        } else {
            subParseSrt(norm, list);
        }
        java.util.Collections.sort(list, new java.util.Comparator<SubCue>() {
            @Override
            public int compare(SubCue x, SubCue y) {
                return x.a < y.a ? -1 : x.a > y.a ? 1 : 0;
            }
        });
        SubCues c = new SubCues();
        int n = list.size();
        c.from = new long[n];
        c.to = new long[n];
        c.txt = new String[n];
        for (int i = 0; i < n; i++) {
            SubCue x = list.get(i);
            c.from[i] = x.a;
            c.to[i] = x.b;
            c.txt[i] = x.t;
            if (x.b > c.end) c.end = x.b;
        }
        return c;
    }

    private static void subParseSrt(String norm, ArrayList<SubCue> list) {
        java.util.regex.Pattern tp = java.util.regex.Pattern.compile(
                "((?:\\d{1,3}:)?\\d{1,2}:\\d{2}[.,:]\\d{1,3})\\s*-->\\s*((?:\\d{1,3}:)?\\d{1,2}:\\d{2}[.,:]\\d{1,3})");
        String[] lines = norm.split("\n");
        int i = 0;
        while (i < lines.length && list.size() < 12000) {
            java.util.regex.Matcher m = tp.matcher(lines[i]);
            if (!m.find()) {
                i++;
                continue;
            }
            long a = subStamp(m.group(1));
            long b = subStamp(m.group(2));
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < lines.length && !lines[i].trim().isEmpty() && !tp.matcher(lines[i]).find()) {
                sb.append(lines[i]).append('\n');
                i++;
            }
            String t = subCleanText(sb.toString());
            if (a >= 0 && b > a && !t.isEmpty()) {
                SubCue c = new SubCue();
                c.a = a;
                c.b = b;
                c.t = t;
                list.add(c);
            }
        }
    }

    private static void subParseAss(String norm, ArrayList<SubCue> list) {
        int iStart = 1, iEnd = 2, iText = 9, nCols = 10;
        for (String ln : norm.split("\n")) {
            String s = ln.trim();
            if (s.startsWith("Format:") && s.contains("Start") && s.contains("Text")) {
                String[] cols = s.substring(7).split(",");
                nCols = cols.length;
                for (int k = 0; k < cols.length; k++) {
                    String c = cols[k].trim();
                    if (c.equalsIgnoreCase("Start")) iStart = k;
                    else if (c.equalsIgnoreCase("End")) iEnd = k;
                    else if (c.equalsIgnoreCase("Text")) iText = k;
                }
            } else if (s.startsWith("Dialogue:") && list.size() < 12000) {
                String[] p = s.substring(9).trim().split(",", nCols);
                if (p.length <= Math.max(iText, Math.max(iStart, iEnd))) continue;
                long a = subStamp(p[iStart]);
                long b = subStamp(p[iEnd]);
                String t = subCleanText(p[iText]);
                if (a >= 0 && b > a && !t.isEmpty()) {
                    SubCue c = new SubCue();
                    c.a = a;
                    c.b = b;
                    c.t = t;
                    list.add(c);
                }
            }
        }
    }

    // ---- търсене: ход ----

    private final Runnable subWatchRun = new Runnable() {
        @Override
        public void run() {
            if (!tvVod || !subSearching) return;
            subPending = 0;
            subSearchFinished();
        }
    };

    private static final HashMap<String, Object[]> SUB_CACHE = new HashMap<>();

    private void subStartSearch(boolean auto) {
        if (!tvVod || vodT == null) return;
        final SubQuery q = subBuildQuery();
        subQ = q;
        subGen++;
        subCancelRequests();
        final int gen = subGen;
        subResults.clear();
        subNotes.clear();
        subSearched = true;
        subWantAuto = auto;
        // Същото търсене до 10 минути пази отговора (за да не се хабят заявките на доставчиците).
        Object[] hit;
        synchronized (SUB_CACHE) {
            hit = SUB_CACHE.get(q.key());
        }
        if (hit != null && System.currentTimeMillis() - (Long) hit[0] < 10 * 60 * 1000L) {
            @SuppressWarnings("unchecked")
            ArrayList<SubItem> cached = (ArrayList<SubItem>) hit[1];
            subResults.addAll(cached);
            subNotes.add("от паметта");
            subPending = 0;
            subSearching = true;
            subSearchFinished();
            return;
        }
        ArrayList<String> ids = new ArrayList<>();
        for (String id : SUB_IDS) {
            if (subConfigured(id, q)) ids.add(id);
        }
        subPending = ids.size();
        subSearching = subPending > 0;
        if (subPending == 0) {
            subSearchFinished();
            return;
        }
        ui.removeCallbacks(subWatchRun);
        ui.postDelayed(subWatchRun, 24000);
        for (final String id : ids) {
            subExecute(gen, new Runnable() {
                @Override
                public void run() {
                    List<SubItem> res = null;
                    String err = null;
                    try {
                        res = subSearchOne(id, q);
                    } catch (Exception e) {
                        err = subErrText(e);
                    }
                    final List<SubItem> fres = res;
                    final String ferr = err;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            subGot(gen, id, fres, ferr);
                        }
                    });
                }
            });
        }
    }

    private static String subErrText(Exception e) {
        String m = e.getMessage();
        if (m == null || m.isEmpty()) {
            m = e instanceof java.net.UnknownHostException ? "няма връзка" : e.getClass().getSimpleName();
        }
        if (e instanceof java.net.SocketTimeoutException) m = "времето изтече";
        if (e instanceof SubHttpErr && ((SubHttpErr) e).code == 401) m = "ключът не е приет (401)";
        return m.length() > 70 ? m.substring(0, 70) : m;
    }

    private void subGot(int gen, String id, List<SubItem> res, String err) {
        if (gen != subGen || !tvVod) return;
        subPending--;
        String name = subProviderName(id);
        if (err != null) {
            subNotes.add(name + ": " + err);
        } else if (res != null) {
            int add = 0;
            for (SubItem it : res) {
                if (add >= 25) break;
                subResults.add(it);
                add++;
            }
            subNotes.add(name + ": " + add);
        }
        subSort();
        if (subPending <= 0) subSearchFinished();
        else subRefreshDialog();
    }

    private int subLangRank(String lang) {
        String[] set = SUB_LANG_SETS[subLangSet()];
        for (int i = 0; i < set.length; i++) {
            if (set[i].equals(lang)) return i;
        }
        return 99;
    }

    private int subScore(SubItem r) {
        int sc = Math.min(r.downloads, 5000) / 100 + (r.trusted ? 20 : 0) - (r.hearing ? 10 : 0);
        if(subQ!=null)sc+=SubtitleMatch.rank(r.release,subQ.title,subQ.orig,subQ.year,subQ.tv,subQ.season,subQ.episode,subVideoName());
        if (r.provider.equals("sabbz")) sc += 25;
        return sc;
    }

    private void subSort() {
        java.util.Collections.sort(subResults, new java.util.Comparator<SubItem>() {
            @Override
            public int compare(SubItem x, SubItem y) {
                int a = subLangRank(x.lang);
                int b = subLangRank(y.lang);
                if (a != b) return a < b ? -1 : 1;
                return subScore(y) - subScore(x);
            }
        });
    }

    private void subSearchFinished() {
        ui.removeCallbacks(subWatchRun);
        boolean wasSearching = subSearching;
        subSearching = false;
        if (wasSearching && subQ != null) {
            boolean errors = false;
            for (String n : subNotes) {
                if (n.contains(": ") && !n.matches(".*: \\d+")) errors = true;
            }
            if (!errors && !subResults.isEmpty() && !subNotes.contains("от паметта")) {
                synchronized (SUB_CACHE) {
                    if (SUB_CACHE.size() > 12) SUB_CACHE.clear();
                    SUB_CACHE.put(subQ.key(), new Object[]{System.currentTimeMillis(),
                            new ArrayList<SubItem>(subResults)});
                }
            }
        }
        subSort();
        subRefreshDialog();
        if (subWantAuto) {
            subWantAuto = false;
            subAutoPick();
        } else {
            subStatusChanged();
        }
    }

    // Автоматичният избор: най-добрите от най-предпочитания наличен език; запомненият избор е пръв.
    private void subAutoPick() {
        if (!tvVod) return;
        if (subResults.isEmpty()) {
            subStatusChanged();
            vodShowBar(5000, "Не намерих субтитри  ·  ▲ за ръчно търсене");
            return;
        }
        ArrayList<SubItem> eligible=new ArrayList<>();
        for(SubItem it:subResults)if(subQ==null || !subQ.tv || SubtitleMatch.episode(it.release,subQ.season,subQ.episode)>=0)eligible.add(it);
        if(eligible.isEmpty()){subStatusChanged();vodShowBar(6000,"Няма подходящ епизод · ▲ за ръчен избор");return;}
        ArrayList<SubItem> cands = new ArrayList<>();
        if(!subSavedUid.isEmpty())for(SubItem it:eligible)if(it.uid().equals(subSavedUid))cands.add(it);
        int bestRank=subLangRank(eligible.get(0).lang);
        if(bestRank>=99){subStatusChanged();return;}
        for(SubItem it:eligible){if(cands.size()>=5)break;if(subLangRank(it.lang)==bestRank && !cands.contains(it))cands.add(it);}
        subLoad(cands, true);
    }

    // Сваля и разчита субтитрите. При автоматичен избор се пробват до пет, докато една
    // не пасне по дължина на филма.
    private void subLoad(final ArrayList<SubItem> cands, final boolean auto) {
        if (cands.isEmpty() || !tvVod) return;
        final int gen = subGen;
        final boolean tv = vodT.tv;
        final int season = vodSeason;
        final int episode = vodEpisode;
        final long videoMs = player != null ? player.getDuration() : C.TIME_UNSET;
        subLoading = true;
        subStatusChanged();
        subExecute(gen, new Runnable() {
            @Override
            public void run() {
                SubCues got = null;
                SubItem gotItem = null;
                String warn = null;
                String err = null;
                for (SubItem it : cands) {
                    if(gen!=subGen)return;
                    try {
                        List<SubFile> files = subDownloadOne(it);
                        if(gen!=subGen)return;
                        SubFile f = subPick(files, tv, season, episode, auto);
                        if (f == null) {
                            err = "Няма файл за избрания епизод в архива";
                            continue;
                        }
                        SubCues c = subParse(subDecode(f.bytes, it.lang));
                        if (c.txt.length < 10) {
                            err = "файлът не е разчетен";
                            continue;
                        }
                        String w = subFitText(c.end, videoMs);
                        if(tv && (SubtitleMatch.episode(f.name,season,episode)<0 || SubtitleMatch.episode(it.release,season,episode)<0))
                            w="Файлът е означен за друг епизод · избран е ръчно";
                        if (auto && w != null) {
                            err = w;
                            continue;
                        }
                        got = c;
                        gotItem = it;
                        warn = w;
                        break;
                    } catch (Exception e) {
                        err = subErrText(e);
                    }
                }
                final SubCues fc = got;
                final SubItem fi = gotItem;
                final String fw = warn;
                final String fe = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != subGen || !tvVod) return;
                        subLoading = false;
                        if (fc != null) {
                            subSet(fc, fi, auto);
                            if (fw != null) vodShowBar(7000, fw);
                        } else {
                            subStatusChanged();
                            if (!auto) {
                                vodShowBar(5000, "Субтитрите не се свалиха: " + (fe == null ? "грешка" : fe));
                            } else if (fe != null) {
                                vodShowBar(6000, "Не намерих субтитри, които пасват  ·  ▲ за избор");
                            }
                        }
                    }
                });
            }
        });
    }

    // Duration is a sanity check; streams usually expose no original-file hash for an exact match.
    private static String subFitText(long subEndMs, long videoMs) {
        return SubtitleMatch.durationWarning(subEndMs,videoMs);
    }

    private String subVideoName() {
        String path=Uri.parse(tvStableUrl==null?"":tvStableUrl).getLastPathSegment();
        return path==null?"":path;
    }

    private String subMatchDescription(SubItem item) {
        String note=item.providerName;
        if(subQ!=null && subQ.tv){int ep=SubtitleMatch.episode(item.release,subQ.season,subQ.episode);
            note+=ep>0?" · точен епизод":ep<0?" · друг епизод, ръчен избор":" · епизодът не е указан";}
        String source=SubtitleMatch.source(item.release);
        if(!source.isEmpty())note+=" · "+source;
        return note;
    }

    private void subSet(SubCues c, SubItem it, boolean auto) {
        subFrom = c.from;
        subTo = c.to;
        subTxt = c.txt;
        subCur = it;
        subOff = false;
        subShown = "";
        if (auto && it.uid().equals(subSavedUid)) subShift = subSavedShift;
        else if (!auto) subShift = 0;
        subSaveChoice();
        subEnsureView();
        subStatusChanged();
        vodShowBar(5000, null);
        subSeeked();
    }

    private void subClear(boolean userOff) {
        subFrom = new long[0];
        subTo = new long[0];
        subTxt = new String[0];
        subCur = null;
        subShown = "";
        subOff = userOff;
        if (subView != null) subView.setVisibility(View.GONE);
        subSaveChoice();
        subStatusChanged();
    }

    private void subSaveChoice() {
        if (vodKey.isEmpty()) return;
        String v;
        if (subOff) v = "off";
        else if (subCur != null) v = subCur.uid() + "\t" + subShift;
        else return;
        prefs.edit().putString("vs:" + vodKey, v).apply();
    }

    // Запомненият избор от предишното гледане.
    private void subReadSaved() {
        String v = prefs.getString("vs:" + vodKey, "");
        subSavedUid = "";
        subSavedShift = 0;
        if (v.equals("off")) {
            subOff = true;
        } else if (!v.isEmpty()) {
            int k = v.indexOf('\t');
            if (k > 0) {
                subSavedUid = v.substring(0, k);
                try {
                    subSavedShift = Long.parseLong(v.substring(k + 1));
                } catch (NumberFormatException ignored) {
                    subSavedShift = 0;
                }
            }
        }
    }

    // Започна ли филмът: автоматичното търсене на субтитри.
    private void subOnPlaying() {
        if (subAutoDone) {
            subEnsureView();
            return;
        }
        subAutoDone = true;
        subReadSaved();
        if (subOff) {
            subStatusChanged();
            return;
        }
        if (!prefs.getBoolean("subAuto", true)) return;
        subStartSearch(true);
    }

    // ---- рисуване ----

    private void subEnsureView() {
        if (subView != null || nativeLayer == null) return;
        subView = new TextView(this);
        subView.setGravity(Gravity.CENTER);
        subView.setTextColor(Color.WHITE);
        subView.setTypeface(Typeface.DEFAULT_BOLD);
        subView.setShadowLayer(Math.max(2f, u(5)), 0f, Math.max(1f, u(1.5f)), 0xFF000000);
        subView.setPadding(u(20), u(6), u(20), u(8));
        subApplyStyle();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WC, WC,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = u(60);
        lp.leftMargin = u(120);
        lp.rightMargin = u(120);
        subView.setVisibility(View.GONE);
        nativeLayer.addView(subView, lp);
    }

    private void subApplyStyle() {
        if (subView == null) return;
        ts(subView, SUB_SIZE_UNITS[subSizeIdx()]);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x73000000);
        g.setCornerRadius(u(12));
        subView.setBackground(g);
    }

    private void subLift(boolean up) {
        if (subView != null) subView.setTranslationY(up ? -u(185) : 0);
    }

    private void subHide() {
        if (subView != null) subView.setVisibility(View.GONE);
        subShown = "";
    }

    // Принуждава обновяване на надписа (след превъртане или промяна на синхрона).
    private void subSeeked() {
        subShown = "\u0000";
        if (player != null) subTick(player.getCurrentPosition());
    }

    private void subTick(long pos) {
        int n = subTxt.length;
        if (n == 0 || subView == null) return;
        long t = pos - subShift;
        int lo = 0, hi = n - 1, ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (subFrom[mid] <= t) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        String show = "";
        if (ans >= 0) {
            String cur = subTo[ans] > t ? subTxt[ans] : "";
            // Два реда, които се застъпват (двама говорещи): показват се заедно.
            String prev = "";
            if (ans > 0 && subTo[ans - 1] > t && subFrom[ans - 1] <= t) prev = subTxt[ans - 1];
            if (!prev.isEmpty() && !cur.isEmpty()) show = prev + "\n" + cur;
            else show = !cur.isEmpty() ? cur : prev;
        }
        if (show.equals(subShown)) return;
        subShown = show;
        if (show.isEmpty()) {
            subView.setVisibility(View.GONE);
        } else {
            subView.setText(show);
            subView.setVisibility(View.VISIBLE);
        }
    }

    // ---- надписи ----

    private String subShiftText() {
        float s = subShift / 1000f;
        return String.format(Locale.US, "%s%.2f s", s > 0 ? "+" : "", s);
    }

    private String subShort() {
        if (subLoading) return "свалям…";
        if (subCur != null) return subCur.lang.toUpperCase(Locale.ROOT) + " · " + subCur.providerName;
        if (subOff) return "изключени";
        if (subSearching) return "търся…";
        return subSearched && subResults.isEmpty() ? "няма" : "избери";
    }

    // Състоянието на субтитрите за лентата.
    private String subStatus() {
        if (subLoading) return "Свалям субтитри…";
        if (subCur != null) {
            return "Субтитри: " + subCur.lang.toUpperCase(Locale.ROOT) + " · " + subCur.providerName
                    + (subShift != 0 ? "  (" + subShiftText() + ")" : "");
        }
        if (subOff) return "Без субтитри";
        if (subSearching) return "Търся субтитри…";
        if (subSearched) return "Няма субтитри  ·  ▲ за избор";
        return "";
    }

    private void subStatusChanged() {
        if (!tvVod || vodBar == null || vodBar.getVisibility() != View.VISIBLE) return;
        if (vodStatus == null && vodBarStatus != null) vodBarStatus.setText(subStatus());
    }

    // ---- прозорци ----

    private boolean subDlgShowing() {
        return subDlg != null && tvDialog == subDlg && subDlg.isShowing();
    }

    private void subRefreshDialog() {
        if (!subDlgShowing()) return;
        String keep = null;
        View f = subDlg.getCurrentFocus();
        if (f != null && f.getTag() instanceof String) keep = (String) f.getTag();
        subBuildDialog(keep);
    }

    private void subDialog() {
        if (!tvVod || vodT == null) return;
        vodHideBarNow();
        if (!subSearched) {
            if (!subSavedUid.isEmpty() || subOff) {
                // нищо: използва се обичайното търсене
            }
            subStartSearch(false);
        }
        subBuildDialog(null);
    }

    private void subBuildDialog(String focusTag) {
        StringBuilder sub = new StringBuilder();
        if (subSearching) sub.append("Търся… ");
        else if (subResults.isEmpty()) sub.append("Няма намерени субтитри. ");
        else sub.append("Намерени: ").append(subResults.size()).append(". ");
        if (!subNotes.isEmpty()) sub.append(subJoin(subNotes, "  ·  "));
        ArrayList<String> off = new ArrayList<>();
        for (String id : SUB_IDS) {
            if (!subConfigured(id, null) && !id.equals("sabbz")) {
                off.add(id.equals("opensubtitles") ? "OpenSubtitles (ключ, потребител, парола)"
                        : subProviderName(id));
            }
        }
        if (!off.isEmpty()) {
            sub.append("\nБез ключ (от бутона Източници): ").append(subJoin(off, ", "));
        }
        LinearLayout card = tvCard("Субтитри", sub.toString());
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View focus = null;
        View firstRow = null;

        LinearLayout none = tvChoice(null, "Без субтитри", subCur == null ? "● избрано" : null,
                subCur == null ? 1 : 0, new Runnable() {
                    @Override
                    public void run() {
                        tvDialogClose();
                        subClear(true);
                    }
                });
        none.setTag("none");
        list.addView(none, lpTopW(MP, u(60), 0));
        firstRow = none;
        View curRow = null;

        LinearLayout sync = tvChoice(null, "Синхрон", subCur != null ? subShiftText() : "първо избери субтитри", 0,
                new Runnable() {
                    @Override
                    public void run() {
                        if (subCur == null) return;
                        subSyncDialog();
                    }
                });
        sync.setTag("sync");
        list.addView(sync, lpTopW(MP, u(60), u(8)));
        LinearLayout set = tvChoice(null, "Настройки на субтитрите", null, 0, new Runnable() {
            @Override
            public void run() {
                subSettingsDialog();
            }
        });
        set.setTag("set");
        list.addView(set, lpTopW(MP, u(60), u(8)));

        int shown = 0;
        for (final SubItem it : subResults) {
            if (shown >= 40) break;
            String rel = it.release.trim();
            if (rel.length() > 42) rel = rel.substring(0, 42) + "…";
            boolean cur = subCur != null && subCur.uid().equals(it.uid());
            String label = it.lang.toUpperCase(Locale.ROOT) + " · " + (rel.isEmpty() ? "субтитри" : rel);
            LinearLayout row = tvChoice(null, label, cur ? "● избрано" : subMatchDescription(it), cur ? 1 : 0,
                    new Runnable() {
                        @Override
                        public void run() {
                            tvDialogClose();
                            ArrayList<SubItem> one = new ArrayList<>();
                            one.add(it);
                            subLoad(one, false);
                        }
                    });
            row.setTag(it.uid());
            list.addView(row, lpTopW(MP, u(60), u(8)));
            if (cur) curRow = row;
            shown++;
        }
        if (focusTag != null) {
            for (int i = 0; i < list.getChildCount(); i++) {
                if (focusTag.equals(list.getChildAt(i).getTag())) focus = list.getChildAt(i);
            }
        }
        if (focus == null) focus = curRow != null ? curRow : firstRow;
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new ViewGroup.LayoutParams(MP, WC));
        card.addView(sv, lpTopW(MP, u(420), u(20)));
        tvDialogShow(card, 880, focus);
        subDlg = tvDialog;
    }

    private void subSettingsDialog() {
        LinearLayout card = tvCard("Настройки на субтитрите", null);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout first = tvChoice(null, "Езици", SUB_LANG_NAMES[subLangSet()], 1, new Runnable() {
            @Override
            public void run() {
                prefs.edit().putInt("subLang", (subLangSet() + 1) % SUB_LANG_SETS.length).apply();
                subSettingsDialog();
            }
        });
        list.addView(first, lpTopW(MP, u(62), 0));
        list.addView(tvChoice(null, "Автоматично търсене", prefs.getBoolean("subAuto", true) ? "включено" : "изключено",
                0, new Runnable() {
                    @Override
                    public void run() {
                        prefs.edit().putBoolean("subAuto", !prefs.getBoolean("subAuto", true)).apply();
                        subSettingsDialog();
                    }
                }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Размер на текста", subSizeName(), 0, new Runnable() {
            @Override
            public void run() {
                subCycleSize();
                subSettingsDialog();
            }
        }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Търси отново", null, 0, new Runnable() {
            @Override
            public void run() {
                subStartSearch(false);
                subBuildDialog(null);
            }
        }), lpTopW(MP, u(62), u(10)));
        list.addView(tvChoice(null, "Назад", null, 0, new Runnable() {
            @Override
            public void run() {
                subBuildDialog(null);
            }
        }), lpTopW(MP, u(62), u(10)));
        card.addView(list, lpTopW(MP, WC, u(24)));
        tvDialogShow(card, 760, first);
        subDlg = null;
    }

    // Синхрон: ◀ ▶ местят субтитрите по-рано или по-късно (задържане = по-бързо), OK затваря.
    private void subSyncDialog() {
        if (subCur == null) return;
        LinearLayout card = tvCard("Синхрон на субтитрите",
                "◀ по-рано   ▶ по-късно.  Ако надписът идва след репликата, натискай ◀.");
        final LinearLayout row = tvChoice(null, subShiftText(), "OK — готово", 1, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                subSaveChoice();
            }
        });
        final TextView val = (TextView) row.getChildAt(0);
        ts(val, 54);
        row.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent ev) {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                long step = ev.getRepeatCount() >= 8 ? 1000 : 250;
                subShift += keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -step : step;
                if (subShift < -120000) subShift = -120000;
                if (subShift > 120000) subShift = 120000;
                val.setText(subShiftText());
                subSeeked();
                return true;
            }
        });
        card.addView(row, lpTopW(MP, u(110), u(22)));
        LinearLayout reset = tvChoice(null, "Нулирай", null, 0, new Runnable() {
            @Override
            public void run() {
                subShift = 0;
                val.setText(subShiftText());
                subSeeked();
            }
        });
        card.addView(reset, lpTopW(MP, u(60), u(12)));
        tvDialogShow(card, 760, row);
        subDlg = null;
        final Dialog dd = tvDialog;
        if (dd != null) {
            dd.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface d) {
                    if (tvDialog == dd) tvDialog = null;
                    subSaveChoice();
                    hideSystemBars();
                }
            });
        }
    }

    // ---- готов списък канали, проверка на линковете, неработещи ----

    // Име = линк1, линк2 ... (gledaitv.fan, gledaitv.live, iptv-bg.com, seirsanduk, bgnow.tv)
    private static final String TV_LIST = ""
        + "БНТ 1 = https://www.gledaitv.fan/bnt-1-live-tv.html, https://www.gledaitv.live/watch-tv/64/bnt-1-online, https://iptv-bg.com/bnt-1-online/, https://www.seirsanduk.online/hd-bnt-1-hd-online, https://www.bgnow.tv/en/watch/bnt-1/\n"
        + "БНТ 2 = https://www.gledaitv.fan/bnt-2-live-tv.html, https://www.gledaitv.live/watch-tv/63/bnt-2-online, https://iptv-bg.com/bnt-2-online/, https://www.seirsanduk.online/bnt-2-online, https://www.bgnow.tv/en/watch/bnt-2/\n"
        + "БНТ 3 = https://www.gledaitv.fan/bnt-3-live-tv.html, https://www.gledaitv.live/watch-tv/62/bnt-3-online, https://iptv-bg.com/bnt-3-online/, https://www.seirsanduk.online/hd-bnt-3-hd-online, https://www.bgnow.tv/en/watch/bnt-3/\n"
        + "БНТ 4 = https://www.gledaitv.fan/bnt-4-live-tv.html, https://www.gledaitv.live/watch-tv/42/bnt-4-online, https://iptv-bg.com/bnt-4-online/, https://www.seirsanduk.online/bnt-4-online, https://www.bgnow.tv/en/watch/bnt-4/\n"
        + "bTV = https://www.gledaitv.fan/btv-live-tv.html, https://www.gledaitv.live/watch-tv/61/btv-online, https://iptv-bg.com/btv/, https://www.seirsanduk.online/hd-btv-hd-online, https://www.bgnow.tv/en/watch/btv/\n"
        + "Nova = https://www.gledaitv.fan/nova-live-tv.html, https://www.gledaitv.live/watch-tv/60/nova-tv-online, https://iptv-bg.com/nova-tv-online/, https://www.seirsanduk.online/hd-nova-tv-hd-online, https://www.bgnow.tv/en/watch/nova/\n"
        + "Diema = https://www.gledaitv.fan/diema-live-tv.html, https://www.gledaitv.live/watch-tv/52/diema-online, https://iptv-bg.com/diema-online/, https://www.seirsanduk.online/hd-diema-hd-online, https://www.bgnow.tv/en/watch/diema/\n"
        + "Diema Family = https://www.gledaitv.fan/diema-family-live-tv.html, https://www.gledaitv.live/watch-tv/51/diema-family-online, https://iptv-bg.com/diema-family-online/, https://www.seirsanduk.online/hd-diema-family-hd-online, https://www.bgnow.tv/en/watch/diema-family/\n"
        + "Kino Nova = https://www.gledaitv.fan/kino-nova-live-tv.html, https://www.gledaitv.live/watch-tv/44/kino-nova-online, https://iptv-bg.com/kino-nova-online/, https://www.seirsanduk.online/hd-kino-nova-hd-online, https://www.bgnow.tv/en/watch/kino-nova/\n"
        + "City TV = https://www.gledaitv.fan/city-tv-live-tv.html, https://www.gledaitv.live/watch-tv/41/city-tv-online, https://iptv-bg.com/city-tv-online/, https://www.seirsanduk.online/city-tv-online, https://www.bgnow.tv/en/watch/city-tv/\n"
        + "Bulgaria On Air = https://www.gledaitv.fan/bulgaria-on-air-live-tv.html, https://www.gledaitv.live/watch-tv/79/bulgaria-on-air, https://iptv-bg.com/bg-on-air-online/, https://www.seirsanduk.online/bulgaria-on-air-online, https://www.bgnow.tv/en/watch/bulgaria-on-air/\n"
        + "Bulgaria On Air TV = https://www.gledaitv.fan/bulgaria-on-air-tv-live-tv.html\n"
        + "Kanal 3 = https://www.gledaitv.fan/kanal-3-live-tv.html, https://iptv-bg.com/kanal-3-online/, https://www.seirsanduk.online/kanal-3-online, https://www.bgnow.tv/en/watch/kanal-3/\n"
        + "Evrokom = https://www.gledaitv.fan/evrokom-live-tv.html, https://www.gledaitv.live/watch-tv/80/eurocom-online, https://iptv-bg.com/evrokom-online/, https://www.seirsanduk.online/evrokom-online\n"
        + "Balkanika TV = https://www.gledaitv.fan/balkanika-tv-live-tv.html, https://www.gledaitv.live/watch-tv/73/balkanika-tv-online, https://iptv-bg.com/balkanika-online/, https://www.bgnow.tv/en/watch/balkanika-tv/\n"
        + "FEN TV = https://www.gledaitv.fan/fen-tv-live-tv.html, https://www.gledaitv.live/watch-tv/74/fen-tv-online, https://iptv-bg.com/fen-tv-online/, https://www.bgnow.tv/en/watch/fen-tv/\n"
        + "Kanal 0 = https://www.gledaitv.fan/kanal-0-live-tv.html\n"
        + "Agro = https://www.gledaitv.fan/agro-live-tv.html\n"
        + "TVN = https://www.gledaitv.fan/tvn-live-tv.html\n"
        + "Skat TV = https://iptv-bg.com/skat-tv-online/, https://www.seirsanduk.online/skat-online\n"
        + "TV Evropa = https://iptv-bg.com/tv-evropa-online/\n"
        + "BIT = https://iptv-bg.com/bit-online/\n"
        + "Alfa TV = https://iptv-bg.com/alfa-tv-online/\n"
        + "Bulgaria 24 = https://www.gledaitv.fan/bulgaria-24-live-tv.html\n"
        + "BFM = https://www.gledaitv.fan/bfm-live-tv.html\n"
        + "France 24 = https://www.gledaitv.fan/france-24-live-tv.html\n"
        + "Deutsche Welle = https://www.gledaitv.fan/deutsche-welle-live-tv.html\n"
        + "Bloomberg = https://iptv-bg.com/bloomberg-online/, https://www.seirsanduk.online/bloomberg-tv-online\n"
        + "Russia Today = https://iptv-bg.com/russia-today-online/\n"
        + "Euronews Bulgaria = https://www.seirsanduk.online/hd-euronews-bulgaria-hd-online\n"
        + "Nova News = https://www.seirsanduk.online/hd-nova-news-hd-online, https://www.bgnow.tv/en/watch/nova-news/\n"
        + "TRT Haber = https://www.bgnow.tv/en/watch/trt-haber/\n"
        + "Habertürk TV = https://www.bgnow.tv/en/watch/haberturk-tv/\n"
        + "CNN Türk = https://www.bgnow.tv/en/watch/cnn-turk/\n"
        + "NTV = https://www.bgnow.tv/en/watch/ntv/\n"
        + "Diema Sport = https://www.gledaitv.fan/diema-sport-live-tv.html, https://www.gledaitv.live/watch-tv/13/diema-sport-online, https://iptv-bg.com/diema-sport-online/, https://www.seirsanduk.online/hd-diema-sport-hd-online\n"
        + "Diema Sport 2 = https://www.gledaitv.fan/diema-sport-2-live-tv.html, https://www.gledaitv.live/watch-tv/12/diema-sport-2-online, https://iptv-bg.com/diema-sport-2-online/, https://www.seirsanduk.online/hd-diema-sport-2-hd-online\n"
        + "Diema Sport 3 = https://www.gledaitv.fan/diema-sport-3-live-tv.html, https://www.gledaitv.live/watch-tv/38/diema-sport-3-online, https://iptv-bg.com/diema-sport-3-online/, https://www.seirsanduk.online/hd-diema-sport-3-hd-online\n"
        + "Nova Sport = https://www.gledaitv.fan/nova-sport-live-tv.html, https://www.gledaitv.live/watch-tv/32/nova-sport-online, https://iptv-bg.com/nova-sport-online/, https://www.seirsanduk.online/hd-nova-sport-hd-online, https://www.bgnow.tv/en/watch/nova-sport/\n"
        + "Max Sport 1 = https://www.gledaitv.fan/max-sport-1-live-tv.html, https://www.gledaitv.live/watch-tv/9/max-sport-bg-1-online, https://iptv-bg.com/max-sport-1-online/, https://www.seirsanduk.online/hd-max-sport-1-hd-online\n"
        + "Max Sport 2 = https://www.gledaitv.fan/max-sport-2-live-tv.html, https://www.gledaitv.live/watch-tv/10/max-sport-2-bg-online, https://iptv-bg.com/max-sport-2-online/, https://www.seirsanduk.online/hd-max-sport-2-hd-online\n"
        + "Max Sport 3 = https://www.gledaitv.fan/max-sport-3-live-tv.html, https://www.gledaitv.live/watch-tv/11/max-sport-3-bg-online, https://iptv-bg.com/max-sport-3-online/, https://www.seirsanduk.online/hd-max-sport-3-hd-online\n"
        + "Max Sport 4 = https://www.gledaitv.fan/max-sport-4-live-tv.html, https://www.gledaitv.live/watch-tv/65/max-sport-4-bg-online, https://iptv-bg.com/max-sport-4-online/, https://www.seirsanduk.online/hd-max-sport-4-hd-online\n"
        + "Eurosport 1 BG = https://www.gledaitv.fan/eurosport-1-bg-live-tv.html, https://www.gledaitv.live/watch-tv/33/eurosport-1-online, https://iptv-bg.com/eurosport-online/, https://www.seirsanduk.online/hd-eurosport-1-hd-online, https://www.bgnow.tv/en/watch/eurosport-1/\n"
        + "Eurosport 2 BG = https://www.gledaitv.fan/eurosport-2-bg-live-tv.html, https://www.gledaitv.live/watch-tv/34/eurosport-2-online, https://iptv-bg.com/eurosport-2-online/, https://www.seirsanduk.online/hd-eurosport-2-hd-online, https://www.bgnow.tv/en/watch/eurosport-2/\n"
        + "Match Futbol 1 = https://www.gledaitv.fan/match-futbol-1-live-tv.html\n"
        + "Match Futbol 2 = https://www.gledaitv.fan/match-futbol-2-live-tv.html\n"
        + "Match Futbol 3 = https://www.gledaitv.fan/match-futbol-3-live-tv.html\n"
        + "Ring BG = https://www.gledaitv.fan/ring-bg-live-tv.html, https://www.gledaitv.live/watch-tv/31/ring-bg-online, https://iptv-bg.com/ring-bg-online/, https://www.seirsanduk.online/hd-ring-bg-hd-online, https://www.bgnow.tv/en/watch/ring/\n"
        + "Sila = https://www.gledaitv.fan/sila-live-tv.html\n"
        + "FITE = https://www.gledaitv.fan/fite-live-tv.html\n"
        + "BEK Sports = https://www.gledaitv.fan/bek-sports-live-tv.html\n"
        + "beIN Sports = https://www.gledaitv.fan/bein-sports-live-tv.html\n"
        + "Sports TV = https://www.gledaitv.fan/sports-tv-live-tv.html\n"
        + "TJK TV = https://www.gledaitv.fan/tjk-tv-live-tv.html\n"
        + "TRT Spor = https://www.gledaitv.fan/trt-spor-live-tv.html, https://www.bgnow.tv/en/watch/trt-spor/\n"
        + "Arena Sport 1 = https://iptv-bg.com/arena-sport-1-online/\n"
        + "Arena Sport 2 = https://iptv-bg.com/arena-sport-2-online/\n"
        + "Arena Sport 3 = https://iptv-bg.com/arena-sport-3-online/\n"
        + "Arena Sport 4 = https://iptv-bg.com/arena-sport-4-online/\n"
        + "bTV Action = https://www.gledaitv.fan/btv-action-live-tv.html, https://www.gledaitv.live/watch-tv/56/btv-action-online, https://iptv-bg.com/btv-action/, https://www.seirsanduk.online/hd-btv-action-hd-online, https://www.bgnow.tv/en/watch/btv-action/\n"
        + "bTV Cinema = https://www.gledaitv.fan/btv-cinema-live-tv.html, https://www.gledaitv.live/watch-tv/55/btv-cinema-online, https://iptv-bg.com/btv-cinema-online/, https://www.seirsanduk.online/btv-cinema-online, https://www.bgnow.tv/en/watch/btv-cinema/\n"
        + "bTV Comedy = https://www.gledaitv.fan/btv-comedy-live-tv.html, https://www.gledaitv.live/watch-tv/54/btv-comedy-online, https://iptv-bg.com/btv-comedy-online/, https://www.seirsanduk.online/hd-btv-comedy-hd-online, https://www.bgnow.tv/en/watch/btv-comedy/\n"
        + "bTV Lady = https://www.gledaitv.fan/btv-lady-live-tv.html, https://www.gledaitv.live/watch-tv/53/btv-lady-online, https://iptv-bg.com/btv-lady-online/\n"
        + "Star Channel = https://www.gledaitv.fan/star-channel-live-tv.html, https://www.seirsanduk.online/hd-star-channel-hd-online, https://www.bgnow.tv/en/watch/star-channel/\n"
        + "Star Life = https://www.gledaitv.fan/star-life-live-tv.html, https://www.seirsanduk.online/hd-star-life-hd-online, https://www.bgnow.tv/en/watch/star-life/\n"
        + "Star Crime = https://www.gledaitv.fan/star-crime-live-tv.html, https://www.seirsanduk.online/hd-star-crime-hd-online, https://www.bgnow.tv/en/watch/star-crime/\n"
        + "AXN = https://www.gledaitv.fan/axn-live-tv.html, https://www.gledaitv.live/watch-tv/59/axn-online, https://www.seirsanduk.online/axn-online, https://www.bgnow.tv/en/watch/axn/\n"
        + "AMC = https://www.gledaitv.fan/amc-live-tv.html, https://www.gledaitv.live/watch-tv/66/amc-online, https://iptv-bg.com/amc-online/\n"
        + "Epic Drama = https://www.gledaitv.fan/epic-drama-live-tv.html, https://www.gledaitv.live/watch-tv/75/epic-drama-online, https://www.seirsanduk.online/hd-epic-drama-hd-online\n"
        + "Filmbox Stars = https://www.gledaitv.fan/filmbox-stars-live-tv.html\n"
        + "Filmbox Extra = https://www.gledaitv.fan/filmbox-extra-live-tv.html, https://www.gledaitv.live/watch-tv/49/film-box-extra-online\n"
        + "Movie Star = https://www.gledaitv.fan/movie-star-live-tv.html, https://www.gledaitv.live/watch-tv/43/movie-star-online, https://iptv-bg.com/movie-star-online/\n"
        + "Scream = https://www.gledaitv.fan/scream-live-tv.html\n"
        + "FOX TV = https://www.gledaitv.fan/fox-tv-live-tv.html, https://www.gledaitv.live/watch-tv/47/fox-online, https://iptv-bg.com/fox-online/\n"
        + "AXN Black = https://www.gledaitv.live/watch-tv/58/axn-black-online, https://iptv-bg.com/axn-black-online/, https://www.seirsanduk.online/axn-black-online\n"
        + "AXN White = https://www.gledaitv.live/watch-tv/57/axn-white-online, https://iptv-bg.com/axn-white-online/, https://www.seirsanduk.online/axn-white-online\n"
        + "FilmBox = https://www.gledaitv.live/watch-tv/50/film-box-online\n"
        + "FilmBox Plus = https://www.gledaitv.live/watch-tv/48/film-box-plus-online\n"
        + "Fox Crime = https://www.gledaitv.live/watch-tv/45/fox-crime-online, https://iptv-bg.com/fox-crime-online/\n"
        + "Fox Life = https://www.gledaitv.live/watch-tv/46/fox-life-online, https://iptv-bg.com/fox-life-online/\n"
        + "CBS Reality = https://iptv-bg.com/csb-reality-online/\n"
        + "Comedy Central = https://iptv-bg.com/comedy-central-online/\n"
        + "TV1000 = https://iptv-bg.com/tv1000-online/\n"
        + "Cinemax 2 = https://iptv-bg.com/cinemax-2-online/\n"
        + "Cinemax = https://iptv-bg.com/cinemax-online/\n"
        + "HBO 3 = https://iptv-bg.com/hbo-3-online/\n"
        + "HBO 2 = https://iptv-bg.com/hbo-2-online/\n"
        + "HBO = https://iptv-bg.com/hbo-online/\n"
        + "Btv Story = https://www.seirsanduk.online/btv-story-online, https://www.bgnow.tv/en/watch/btv-story/\n"
        + "National Geographic = https://www.gledaitv.fan/national-geographic-live-tv.html, https://www.gledaitv.live/watch-tv/23/national-geographic-channel, https://iptv-bg.com/nat-geo-online/, https://www.seirsanduk.online/hd-nat-geo-hd-online, https://www.bgnow.tv/en/watch/national-geographic-channel/\n"
        + "Nat Geo Wild = https://www.gledaitv.fan/nat-geo-wild-live-tv.html, https://www.gledaitv.live/watch-tv/24/nat-geo-wild-online, https://iptv-bg.com/nat-geo-wild-online/, https://www.seirsanduk.online/hd-nat-geo-wild-hd-online, https://www.bgnow.tv/en/watch/nat-geo-wild/\n"
        + "Discovery Channel = https://www.gledaitv.fan/discovery-channel-live-tv.html, https://www.gledaitv.live/watch-tv/18/discovery-channel-online, https://iptv-bg.com/discovery-channel-online/, https://www.seirsanduk.online/hd-discovery-channel-hd-online, https://www.bgnow.tv/en/watch/discovery-channel/\n"
        + "Investigation Discovery = https://www.gledaitv.fan/investigation-discovery-live-tv.html, https://www.gledaitv.live/watch-tv/22/investigation-discovery-online, https://www.bgnow.tv/en/watch/investigation-discovery/\n"
        + "Animal Planet = https://www.gledaitv.fan/animal-planet-live-tv.html, https://www.gledaitv.live/watch-tv/17/animal-planet-online, https://iptv-bg.com/animal-planet-online/, https://www.bgnow.tv/en/watch/animal-planet/\n"
        + "Viasat Nature = https://www.gledaitv.fan/viasat-nature-live-tv.html, https://www.gledaitv.live/watch-tv/28/viasat-nature-online, https://iptv-bg.com/viasat-nature-online/, https://www.bgnow.tv/en/watch/viasat-nature/\n"
        + "Viasat Explore = https://www.gledaitv.fan/viasat-explore-live-tv.html, https://www.gledaitv.live/watch-tv/26/viasat-explore-online, https://iptv-bg.com/viasat-explorer-online/, https://www.seirsanduk.online/hd-viasat-explore-hd-online\n"
        + "Travel Channel = https://www.gledaitv.fan/travel-channel-live-tv.html, https://www.gledaitv.live/watch-tv/82/travel-channel-online, https://iptv-bg.com/travel-channel-online/, https://www.seirsanduk.online/hd-travel-channel-hd-online, https://www.seirsanduk.online/travel-tv-online, https://www.bgnow.tv/en/watch/travel-channel/\n"
        + "TLC BG = https://www.gledaitv.fan/tlc-bg-live-tv.html, https://www.gledaitv.live/watch-tv/36/tlc-online, https://iptv-bg.com/tlc-online/, https://www.seirsanduk.online/tlc-online, https://www.bgnow.tv/en/watch/tlc/\n"
        + "TLC = https://www.gledaitv.fan/tlc-live-tv.html\n"
        + "DMAX = https://www.gledaitv.fan/dmax-live-tv.html\n"
        + "Docubox = https://www.gledaitv.fan/docubox-live-tv.html, https://www.gledaitv.live/watch-tv/19/docubox-online\n"
        + "24 Kitchen = https://www.gledaitv.fan/24-kitchen-live-tv.html, https://www.gledaitv.live/watch-tv/35/24-kitchen-online, https://iptv-bg.com/24kitchen-online/, https://www.seirsanduk.online/hd-24-kitchen-hd-online, https://www.bgnow.tv/en/watch/24-kitchen/\n"
        + "TRT Belgesel = https://www.gledaitv.fan/trt-belgesel-live-tv.html\n"
        + "Code Fashion = https://www.gledaitv.fan/code-fashion-live-tv.html, https://www.gledaitv.live/watch-tv/78/code-fashion-tv-online, https://www.seirsanduk.online/hd-code-fashion-tv-hd-online, https://www.bgnow.tv/en/watch/code-fashion/\n"
        + "Code Health = https://www.gledaitv.fan/code-health-live-tv.html, https://www.bgnow.tv/en/watch/code-health/\n"
        + "VTK = https://www.gledaitv.fan/vtk-live-tv.html, https://www.seirsanduk.online/vtk-online\n"
        + "Cartoon Network = https://www.gledaitv.fan/cartoon-network-live-tv.html, https://www.gledaitv.live/watch-tv/16/cartoon-network-online, https://iptv-bg.com/cartoon-network-online/, https://www.seirsanduk.online/cartoon-network-online, https://www.bgnow.tv/en/watch/cartoon-network/\n"
        + "Cartoonito = https://www.gledaitv.fan/cartoonito-live-tv.html, https://www.bgnow.tv/en/watch/cartoonito/\n"
        + "Disney Channel = https://www.gledaitv.fan/disney-channel-live-tv.html, https://www.gledaitv.live/watch-tv/15/disney-channel-online, https://iptv-bg.com/disney-channel-online/, https://www.seirsanduk.online/disney-channel-online, https://www.bgnow.tv/en/watch/disney-channel/\n"
        + "Nick Jr = https://www.gledaitv.fan/nick-jr-live-tv.html, https://www.gledaitv.live/watch-tv/29/nick-jr-online, https://www.seirsanduk.online/nick-jr-online\n"
        + "Nickelodeon = https://www.gledaitv.fan/nickelodeon-live-tv.html, https://www.gledaitv.live/watch-tv/14/nickelodeon-online, https://iptv-bg.com/nickelodeon-online/, https://www.seirsanduk.online/nickelodeon-online, https://www.bgnow.tv/en/watch/nickelodeon/\n"
        + "Nick Toons = https://www.gledaitv.fan/nick-toons-live-tv.html, https://www.gledaitv.live/watch-tv/30/nicktoons-online, https://www.seirsanduk.online/nicktoons-online, https://www.bgnow.tv/en/watch/nicktoons/\n"
        + "Nick Jr RU = https://www.gledaitv.fan/nick-jr-ru-live-tv.html\n"
        + "Nickelodeon RU = https://www.gledaitv.fan/nickelodeon-ru-live-tv.html\n"
        + "Carousel = https://www.gledaitv.fan/carousel-live-tv.html\n"
        + "TRT Cocuk = https://www.gledaitv.fan/trt-cocuk-live-tv.html, https://www.bgnow.tv/en/watch/trt-cocuk/\n"
        + "Baby TV = https://iptv-bg.com/baby-tv-online/\n"
        + "Duck TV = https://iptv-bg.com/duck-tv-online/\n"
        + "Disney Junior = https://iptv-bg.com/disney-junior-online/\n"
        + "E Kids = https://iptv-bg.com/ekids-online/, https://www.seirsanduk.online/e-kids-online\n"
        + "Boomerang = https://iptv-bg.com/boomerang-tv-online/\n"
        + "MTV 00s = https://www.gledaitv.fan/mtv-00s-live-tv.html\n"
        + "MTV Hits = https://www.gledaitv.fan/mtv-hits-live-tv.html, https://iptv-bg.com/mtv-hits-online/\n"
        + "The Voice = https://www.gledaitv.fan/the-voice-live-tv.html, https://www.gledaitv.live/watch-tv/4/the-voice-online, https://iptv-bg.com/the-voice-online/, https://www.seirsanduk.online/the-voice-online, https://www.bgnow.tv/en/watch/the-voice/\n"
        + "Planeta HD BG = https://www.gledaitv.fan/planeta-hd-bg-live-tv.html, https://www.gledaitv.live/watch-tv/40/planeta-hd-online, https://iptv-bg.com/planeta-hd-online/, https://iptv-bg.com/planeta-tv-online/, https://www.seirsanduk.online/hd-planeta-hd-online, https://www.bgnow.tv/en/watch/planeta-hd/\n"
        + "Planeta Folk = https://www.gledaitv.fan/planeta-folk-live-tv.html, https://www.gledaitv.live/watch-tv/39/planeta-folk-online, https://iptv-bg.com/planeta-folk-online/, https://www.seirsanduk.online/planeta-folk-online\n"
        + "Power Turk = https://www.gledaitv.fan/power-turk-live-tv.html, https://www.bgnow.tv/en/watch/power-turk-tv/\n"
        + "Kral Pop TV = https://www.gledaitv.fan/kral-pop-tv-live-tv.html, https://www.bgnow.tv/en/watch/kral-pop-tv/\n"
        + "TRT Muzik = https://www.gledaitv.fan/trt-muzik-live-tv.html\n"
        + "Tatlises TV = https://www.gledaitv.fan/tatlises-tv-live-tv.html\n"
        + "Byeaz TV = https://www.gledaitv.fan/byeaz-tv-live-tv.html\n"
        + "Pervyy Kanal = https://www.gledaitv.fan/pervyy-kanal-live-tv.html\n"
        + "Russia 1 = https://www.gledaitv.fan/russia-1-live-tv.html\n"
        + "RU TV = https://www.gledaitv.fan/ru-tv-live-tv.html\n"
        + "1 HD TV = https://www.gledaitv.fan/1-hd-tv-live-tv.html\n"
        + "TRT 1 = https://www.gledaitv.fan/trt-1-live-tv.html, https://www.gledaitv.live/watch-tv/71/trt-1-online, https://www.bgnow.tv/en/watch/trt-1/\n"
        + "ATV = https://www.gledaitv.fan/atv-live-tv.html, https://www.gledaitv.live/watch-tv/68/atv-online, https://www.bgnow.tv/en/watch/atv/\n"
        + "Kanal D = https://www.gledaitv.fan/kanal-d-live-tv.html, https://www.gledaitv.live/watch-tv/67/kanal-d-online, https://www.bgnow.tv/en/watch/kanal-d/\n"
        + "Show TV = https://www.gledaitv.fan/show-tv-live-tv.html, https://www.gledaitv.live/watch-tv/69/show-tv-online, https://www.bgnow.tv/en/watch/show-tv/\n"
        + "Star TV = https://www.gledaitv.fan/star-tv-live-tv.html, https://www.gledaitv.live/watch-tv/70/star-tv-online, https://www.bgnow.tv/en/watch/star-tv/\n"
        + "Yaban TV = https://www.gledaitv.fan/yaban-tv-live-tv.html\n"
        + "ARD = https://www.gledaitv.fan/ard-live-tv.html\n"
        + "ZDF = https://www.gledaitv.fan/zdf-live-tv.html\n"
        + "RTL = https://www.gledaitv.fan/rtl-live-tv.html\n"
        + "PRO 7 = https://www.gledaitv.fan/pro-7-live-tv.html\n"
        + "WDR = https://www.gledaitv.fan/wdr-live-tv.html\n"
        + "Bayerischer Rundfunk = https://www.gledaitv.fan/bayerischer-rundfunk-live-tv.html\n"
        + "TV5 Monde = https://www.gledaitv.fan/tv5-monde-live-tv.html\n"
        + "C8 FR = https://www.gledaitv.fan/c8-fr-live-tv.html\n"
        + "Euronews = https://www.gledaitv.live/watch-tv/81/euronews-online, https://www.bgnow.tv/en/watch/euronews/\n"
        + "7/8 TV = https://iptv-bg.com/7-8-tv-online/, https://www.seirsanduk.online/hd-78-tv-hd-online\n"
        + "Fashion TV = https://iptv-bg.com/fashion-tv-online/\n"
        + "Max One = https://www.seirsanduk.online/hd-max-one-hd-online\n"
        + "Now TV = https://www.bgnow.tv/en/watch/now-tv/\n"
        + "TV1 = https://www.bgnow.tv/en/watch/tv1/\n"
        + "Minika GO = https://www.bgnow.tv/en/watch/minika-go/\n"
        + "Discovery Science = https://www.gledaitv.live/watch-tv/20/discovery-science-online\n"
        + "History Channel = https://www.gledaitv.live/watch-tv/21/history-channel-online, https://iptv-bg.com/history-tv-online/, https://www.bgnow.tv/en/watch/history-channel/\n"
        + "Viasat History = https://www.gledaitv.live/watch-tv/27/viasat-history-online, https://iptv-bg.com/viasat-history-online/, https://www.bgnow.tv/en/watch/viasat-history/\n"
        + "Food Network = https://www.seirsanduk.online/hd-food-network-hd-online, https://www.bgnow.tv/en/watch/food-network/\n"
        + "Id Xtra = https://www.seirsanduk.online/hd-id-xtra-hd-online\n"
        + "A Spor = https://www.gledaitv.live/watch-tv/76/a-spor-online, https://www.bgnow.tv/en/watch/a-spor/\n"
        + "BEIN SPORTS HABER = https://www.gledaitv.live/watch-tv/77/bein-sports-haber-online, https://www.bgnow.tv/en/watch/bein-sports-haber/\n"
        + "FOX TV (TR) = https://www.gledaitv.live/watch-tv/72/fox-tv-tr-online\n"
        + "BG Music Channel = https://iptv-bg.com/bg-music-channel-online/\n"
        + "Box TV = https://iptv-bg.com/box-tv-online/\n"
        + "Magic TV = https://iptv-bg.com/magic-tv-online/\n"
        + "DSTV = https://iptv-bg.com/dstv-online/, https://www.seirsanduk.online/dstv-online\n"
        + "Tiankov Folk = https://iptv-bg.com/tiankov-folk-online/\n"
        + "Fen Folk = https://iptv-bg.com/fen-folk-online/\n"
        + "Sunrise Music = https://iptv-bg.com/sunrise-music-online/\n"
        + "Mezzo = https://iptv-bg.com/mezzo-online/\n"
        + "VH1 = https://iptv-bg.com/vh1-online/\n"
        + "MTV Rocks = https://iptv-bg.com/mtv-rocks-online/\n"
        + "MTV = https://iptv-bg.com/mtv-online/\n"
        + "Folklor Tv = https://www.seirsanduk.online/folklor-tv-online\n"
        + "Rodina Tv = https://www.seirsanduk.online/rodina-tv-online\n"
        + "Tiankov Tv = https://www.seirsanduk.online/tiankov-tv-online\n";

    private static final String TV_CHK_UA = "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";
    private static final long TV_CHK_GAP = 6L * 3600 * 1000;

    private static String tvKeyName(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private TvChan tvFindLoose(String name) {
        TvChan c = tvFindByName(name);
        if (c != null) return c;
        String k = tvKeyName(name);
        for (TvChan x : tvChans) {
            if (tvKeyName(x.name).equals(k)) return x;
        }
        return null;
    }

    // Добавя само липсващото: нови канали и нови сайтове към вече съществуващи канали.
    private void tvImportList() {
        int newCh = 0, newLinks = 0;
        for (String line : TV_LIST.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf(" = ");
            if (eq <= 0) continue;
            String name = line.substring(0, eq).trim();
            TvChan c = tvFindLoose(name);
            boolean created = false;
            if (c == null) {
                c = new TvChan();
                c.id = Long.toString(System.currentTimeMillis(), 36) + tvChans.size();
                c.name = name;
                created = true;
            }
            int added = 0;
            for (String u : line.substring(eq + 3).split(",\\s*")) {
                u = u.trim();
                if (!u.startsWith("http")) continue;
                String host = hostOf(u);
                boolean have = false;
                for (TvSrc x : c.srcs) {
                    if (samePage(x.page, u) || x.site.equalsIgnoreCase(host)
                            || (x.page != null && x.page.startsWith("http")
                            && hostOf(x.page).equalsIgnoreCase(host))) {
                        have = true;
                        break;
                    }
                }
                if (have) continue;
                TvSrc s = new TvSrc();
                s.site = host;
                s.page = u;
                c.srcs.add(s);
                added++;
            }
            if (added == 0) continue;
            newLinks += added;
            if (created) {
                tvChans.add(c);
                newCh++;
            }
        }
        tvSave();
        String msg = newCh + " нови канала, " + newLinks + " нови линка";
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        logEvent("ТВ: списък - " + msg);
        if (tvPanelShown()) tvFill(null);
    }

    private void tvImportAsk() {
        LinearLayout card = tvCard("Добави готов списък канали",
                "Добавят се само липсващите канали и линкове. Твоите канали и потоци не се пипат.");
        LinearLayout no = tvChoice(null, "Назад", null, 0, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
            }
        });
        LinearLayout yes = tvChoice(null, "Добави", null, 1, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                tvImportList();
            }
        });
        card.addView(yes, lpTopW(MP, u(62), u(24)));
        card.addView(no, lpTopW(MP, u(62), u(10)));
        tvDialogShow(card, 760, yes);
    }

    // ---- "не работи" ----

    private static boolean tvDead(TvChan c) {
        if (c.srcs.isEmpty()) return false;
        for (TvSrc s : c.srcs) {
            if (s.bad < 3) return false;
        }
        return true;
    }

    // Не е отговорил при последната проверка (всички източници).
    private static boolean tvFailing(TvChan c) {
        if (c.srcs.isEmpty()) return false;
        for (TvSrc s : c.srcs) {
            if (s.bad < 1) return false;
        }
        return true;
    }

    private static int tvMinBad(TvChan c) {
        int m = 99;
        for (TvSrc s : c.srcs) m = Math.min(m, s.bad);
        return m == 99 ? 0 : m;
    }

    private static class ChkJob {
        TvChan c;
        TvSrc s;
        volatile boolean good;
    }

    private volatile int chkGen;
    private int chkDone;
    private TextView chkTv;

    // Проверява дали записаният поток или страницата отговарят. Не пуска канал.
    private static boolean tvProbe(String url, String ref, String org, String ua, boolean stream) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", ua == null || ua.isEmpty() ? TV_CHK_UA : ua);
            if (ref != null && !ref.isEmpty()) c.setRequestProperty("Referer", ref);
            if (org != null && !org.isEmpty()) c.setRequestProperty("Origin", org);
            int rc = c.getResponseCode();
            if (rc >= 400) return false;
            InputStream in = c.getInputStream();
            byte[] buf = new byte[4096];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            String ct = c.getContentType();
            String head = new String(buf, 0, n, "UTF-8");
            if (stream) {
                if (head.startsWith("#EXTM3U") || head.contains("#EXTINF")) return true;
                if (ct != null && ct.toLowerCase(Locale.ROOT).contains("text/html")) return false;
                return n > 0;
            }
            return n >= 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static boolean tvProbeSrc(TvSrc s) {
        if (s.stream != null && !s.stream.isEmpty()
                && tvProbe(s.stream, s.ref, s.org, s.ua, true)) return true;
        if (s.page != null && s.page.startsWith("http")) return tvProbe(s.page, "", "", "", false);
        return false;
    }

    private void tvCheckAll() {
        final ArrayList<ChkJob> jobs = new ArrayList<>();
        for (TvChan c : tvChans) {
            for (TvSrc s : c.srcs) {
                ChkJob j = new ChkJob();
                j.c = c;
                j.s = s;
                jobs.add(j);
            }
        }
        if (jobs.isEmpty()) {
            Toast.makeText(this, "Още няма канали за проверка", Toast.LENGTH_LONG).show();
            return;
        }
        final int gen = ++chkGen;
        chkDone = 0;
        final int total = jobs.size();
        LinearLayout card = tvCard("Проверявам каналите",
                "Проверява се само дали линковете отговарят. Каналите не се пускат.");
        chkTv = text("0 от " + total, 24, T_TEXT, tfMedium, true);
        card.addView(chkTv, lpTopW(MP, WC, u(20)));
        LinearLayout stop = tvChoice(null, "Спри", null, 2, new Runnable() {
            @Override
            public void run() {
                chkGen++;
                tvDialogClose();
            }
        });
        card.addView(stop, lpTopW(MP, u(62), u(24)));
        tvDialogShow(card, 760, stop);
        for (final ChkJob j : jobs) {
            SUBNET.execute(new Runnable() {
                @Override
                public void run() {
                    if (gen != chkGen) return;
                    final boolean ok = tvProbeSrc(j.s);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (gen != chkGen) return;
                            j.good = ok;
                            chkDone++;
                            if (chkTv != null) chkTv.setText(chkDone + " от " + total);
                            if (chkDone >= total) tvCheckFinish(jobs);
                        }
                    });
                }
            });
        }
    }

    private void tvCheckFinish(ArrayList<ChkJob> jobs) {
        int good = 0;
        for (ChkJob j : jobs) {
            if (j.good) good++;
        }
        tvDialogClose();
        if (good == 0 && jobs.size() >= 5) {
            // Нищо не отговаря: най-вероятно няма интернет. Нищо не се отбелязва като счупено.
            LinearLayout card = tvCard("Нищо не отговаря",
                    "Провери интернета на кутията и опитай пак. Каналите не са отбелязани като неработещи.");
            LinearLayout ok = tvChoice(null, "Добре", null, 1, new Runnable() {
                @Override
                public void run() {
                    tvDialogClose();
                }
            });
            card.addView(ok, lpTopW(MP, u(62), u(24)));
            tvDialogShow(card, 760, ok);
            return;
        }
        long now = System.currentTimeMillis();
        for (ChkJob j : jobs) {
            if (j.good) {
                j.s.bad = 0;
                j.s.chk = now;
            } else if (j.s.chk == 0 || now - j.s.chk > TV_CHK_GAP) {
                j.s.bad++;
                j.s.chk = now;
            }
        }
        tvSave();
        int failing = 0, dead = 0;
        for (TvChan c : tvChans) {
            if (tvFailing(c)) failing++;
            if (tvDead(c)) dead++;
        }
        String sub = "Отговарят " + good + " от " + jobs.size() + " източника.\n"
                + (failing == 0 ? "Всички канали имат поне един работещ източник."
                : failing + " канала нямат работещ източник"
                + (dead > 0 ? " (" + dead + " са се провалили 3 пъти)." : ".")
                + "\nКаналът се маха от списъка само когато ти го решиш.");
        LinearLayout card = tvCard("Проверката свърши", sub);
        View first;
        if (failing > 0) {
            first = tvChoice(null, "Неработещи", String.valueOf(failing), 1, new Runnable() {
                @Override
                public void run() {
                    tvDeadDialog();
                }
            });
            card.addView(first, lpTopW(MP, u(62), u(24)));
        } else {
            first = null;
        }
        LinearLayout done = tvChoice(null, "Готово", null, first == null ? 1 : 0, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
                if (tvPanelShown()) tvFill(null);
            }
        });
        card.addView(done, lpTopW(MP, u(62), first == null ? u(24) : u(10)));
        tvDialogShow(card, 760, first != null ? first : done);
        if (tvPanelShown()) tvFill(null);
    }

    private void tvDeadDialog() {
        final ArrayList<TvChan> bad = new ArrayList<>();
        for (TvChan c : tvChans) {
            if (tvFailing(c)) bad.add(c);
        }
        LinearLayout card = tvCard("Неработещи канали",
                bad.isEmpty() ? "Няма неработещи канали."
                        : "Няма работещ източник при последната проверка. 3 неуспеха = „не работи“.");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = null;
        for (final TvChan c : bad) {
            LinearLayout r = tvChoice(null, c.name, "проверки: " + Math.min(3, tvMinBad(c)) + "/3",
                    tvDead(c) ? 2 : 0, new Runnable() {
                        @Override
                        public void run() {
                            tvChannelMenu(c);
                        }
                    });
            list.addView(r, lpTopW(MP, u(62), first == null ? 0 : u(10)));
            if (first == null) first = r;
        }
        int dead = 0;
        for (TvChan c : bad) {
            if (tvDead(c)) dead++;
        }
        if (dead > 0) {
            LinearLayout del = tvChoice(null, "Изтрий " + dead + " с 3 неуспеха", null, 2,
                    new Runnable() {
                        @Override
                        public void run() {
                            ArrayList<TvChan> rm = new ArrayList<>();
                            for (TvChan c : tvChans) {
                                if (tvDead(c)) rm.add(c);
                            }
                            tvChans.removeAll(rm);
                            tvSave();
                            tvDialogClose();
                            if (tvPanelShown()) tvFill(null);
                        }
                    });
            list.addView(del, lpTopW(MP, u(62), first == null ? 0 : u(18)));
            if (first == null) first = del;
        }
        LinearLayout reset = tvChoice(null, "Занули проверките", "всички пак са „работят“", 0,
                new Runnable() {
                    @Override
                    public void run() {
                        for (TvChan c : tvChans) {
                            for (TvSrc s : c.srcs) {
                                s.bad = 0;
                                s.chk = 0;
                            }
                        }
                        tvSave();
                        tvDialogClose();
                        if (tvPanelShown()) tvFill(null);
                    }
                });
        list.addView(reset, lpTopW(MP, u(62), first == null ? 0 : u(10)));
        if (first == null) first = reset;
        LinearLayout back = tvChoice(null, "Затвори", null, 0, new Runnable() {
            @Override
            public void run() {
                tvDialogClose();
            }
        });
        list.addView(back, lpTopW(MP, u(62), u(10)));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(list, new ViewGroup.LayoutParams(MP, WC));
        card.addView(sv, lpTopW(MP, Math.min(u(420), u(72) * (bad.size() + 3)), u(24)));
        tvDialogShow(card, 760, first);
    }

    // Редовете за списък и проверка най-горе в "Добави". Връща първия за фокус.
    private View tvManageRows(LinearLayout list) {
        LinearLayout a = tvChoice(null, "Добави готов списък", "само липсващите", 1, new Runnable() {
            @Override
            public void run() {
                tvImportAsk();
            }
        });
        list.addView(a, lp(MP, u(62)));
        LinearLayout b = tvChoice(null, "Провери всички канали", "работят ли линковете", 0,
                new Runnable() {
                    @Override
                    public void run() {
                        tvCheckAll();
                    }
                });
        list.addView(b, lpTopW(MP, u(62), u(10)));
        int f = 0;
        for (TvChan c : tvChans) {
            if (tvFailing(c)) f++;
        }
        if (f > 0) {
            list.addView(tvChoice(null, "Неработещи", String.valueOf(f), 0, new Runnable() {
                @Override
                public void run() {
                    tvDeadDialog();
                }
            }), lpTopW(MP, u(62), u(10)));
        }
        return a;
    }

    // ====================== ОЩЕ ПОТОЦИ ПРИ НУЖДА ======================
    // Само докато каналът се гледа и само когато си поискал: ◀ без друг поток на сайта, или
    // „Обнови потоците“ в менюто на канала. Скритата страница на сайта се отваря за кратко, събират
    // се плейлистите, които тя поиска, и всеки се проверява с една малка заявка: истински ли е,
    // на живо ли е, и не е ли просто вариант (качество) на поток, който вече имаме. Новите
    // стават резервни потоци на сайта (най-много 4 резервни).

    private boolean tvSearching;
    private boolean tvSearchChecking;
    private volatile WebView tvSearchWeb;
    private ExoPlayer tvSearchPlayer;
    private boolean tvSearchResumePlayback;
    private float tvSearchVolume;
    private Future<?> tvSearchTask;
    private static final ThreadPoolExecutor TV_SEARCH_NET = new ThreadPoolExecutor(0,1,15,TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),lowPriority("tvs-stream"));
    private int tvSearchSi = -1;
    private boolean tvSearchTune;                // след търсенето да се мине на първия нов поток
    private int tvSearchGen;
    private TvChan tvMoreChan;                   // канал, за който от менюто е поискано търсене след пускане
    private final HashSet<Integer> tvSearched = new HashSet<>(); // сайтове, търсени в това гледане
    private static final long TV_SEARCH_MS = 18000;
    private static final long TV_SEARCH_PROBE_MS = 8000;
    private long tvSearchBusyUntil;              // до кога скритата страница може да натоварва кутията

    private static boolean tvSearchMedia(Uri uri){
        if(mediaKind(uri.toString())!=0)return true;
        String path=nz(uri.getPath()).toLowerCase(Locale.ROOT);
        return path.matches(".*\\.(ts|m4s|aac|mp3|webm|ogg|m4a)$");
    }

    private void tvSearchRestorePlayback(){
        ExoPlayer previous=tvSearchPlayer;tvSearchPlayer=null;
        if(previous!=null && previous==player && tvMode && !tvVod && tvPlaying){
            previous.setVolume(tvSearchVolume);previous.setPlayWhenReady(tvSearchResumePlayback);
        }
    }

    // Допълнително заглушаване на страницата; родният плеър е спрян до унищожаването ѝ.
    private static final String TV_MUTE_JS =
            "(function(){try{function m(){var a=document.querySelectorAll('video,audio');"
                    + "for(var i=0;i<a.length;i++){try{a[i].muted=true;a[i].volume=0;}catch(e){}}}"
                    + "m();if(!window.__tvm){window.__tvm=1;"
                    + "document.addEventListener('play',function(e){try{e.target.muted=true;"
                    + "e.target.volume=0;}catch(x){}},true);"
                    + "var t=0;new MutationObserver(function(){if(t)return;"
                    + "t=setTimeout(function(){t=0;m();},500);}).observe(document.documentElement,"
                    + "{childList:true,subtree:true});}}catch(e){}})()";

    // Има ли смисъл да се търси: текущият сайт има страница, която сочи точно към канала.
    private boolean tvSearchable() {
        TvChan c = tvCur;
        if (c == null || tvVod) return false;
        int i = tvSrcNowIdx();
        return i >= 0 && i < c.srcs.size() && tvCapturable(c.srcs.get(i));
    }

    private void tvMoreStart(boolean thenTune) {
        TvChan c = tvCur;
        if (!tvMode || tvVod || c == null) return;
        if (tvSearching) {
            tvSearchTune = tvSearchTune || thenTune;
            tvShowBanner(0, "Още търся потоци…");
            return;
        }
        int si = tvSrcNowIdx();
        if (si < 0 || si >= c.srcs.size()) return;
        TvSrc s = c.srcs.get(si);
        if (!tvPlaying || tvCapturing || !tvCapturable(s) || tvSearched.contains(si)) {
            tvShowBanner(2500, "Няма друг поток на този сайт · → за друг сайт");
            return;
        }
        tvSearched.add(si);
        tvSearching = true;
        tvSearchChecking = false;
        tvSearchBusyUntil = SystemClock.uptimeMillis() + TV_SEARCH_MS + 12000;
        tvSearchSi = si;
        tvSearchTune = thenTune;
        ++tvSearchGen;
        tvCapturing = true;
        tvReloaded = true; // страницата не се презарежда: плеърът свири и търсенето е кратко
        tvCapSrc = si;
        tvTrying = false;
        tvSearchPlayer=player;
        if(player!=null){
            tvSearchResumePlayback=player.getPlayWhenReady();tvSearchVolume=player.getVolume();
            player.setVolume(0f);player.pause();
        }
        ui.removeCallbacks(tvStallRun);
        tvPokeN = 0;
        logEvent("ТВ: търся още потоци в " + s.site, true);
        tvShowBanner(0, "Търся още потоци…");
        try {
            tvOpenHidden(s, true);
        } catch (Exception e) {
            logEvent("ТВ: търсенето не започна (" + e.getClass().getSimpleName() + ")");
            tvSearchEnd();
            return;
        }
        ui.removeCallbacks(tvSearchEndRun);
        ui.postDelayed(tvSearchEndRun, TV_SEARCH_MS);
    }

    private final Runnable tvSearchEndRun = new Runnable() {
        @Override
        public void run() {
            tvSearchEnd();
        }
    };

    // Времето за търсене изтече: страницата се затваря, а намерените плейлисти се проверяват.
    private void tvSearchEnd() {
        ui.removeCallbacks(tvSearchEndRun);
        if (!tvSearching || tvSearchChecking) return;
        final TvChan c = tvCur;
        final int si = tvSearchSi;
        final boolean thenTune = tvSearchTune;
        final int gen = tvSearchGen;
        tvSearchChecking = true;
        final ArrayList<String> urls = new ArrayList<>();
        final HashMap<String, HashMap<String, String>> hs = new HashMap<>();
        synchronized (mediaPlaylists) {
            for (Map.Entry<String, HashMap<String, String>> en : mediaPlaylists.entrySet()) {
                urls.add(en.getKey());
                hs.put(en.getKey(), en.getValue());
            }
        }
        tvCapturing = false;
        ui.removeCallbacks(tvPokeRun);
        ui.removeCallbacks(tvPokeFallback);
        if(web==tvSearchWeb)tvDropWeb();
        tvSearchWeb=null;
        tvSearchRestorePlayback();
        if (!tvMode || tvVod || c == null || si < 0 || si >= c.srcs.size()) return;
        TvSrc s = c.srcs.get(si);
        // Вече известните потоци на сайта и сайтовете (CDN), от които е позволено да идват новите.
        final HashSet<String> known = new HashSet<>();
        final HashSet<String> fams = new HashSet<>();
        if (!s.stream.isEmpty()) {
            known.add(tvStreamKey(s.stream));
            fams.add(siteBase(hostOf(s.stream)));
        }
        for (String[] al : s.alts) {
            known.add(tvStreamKey(al[0]));
            fams.add(siteBase(hostOf(al[0])));
        }
        if (!s.page.isEmpty()) fams.add(siteBase(hostOf(s.page)));
        final String ua = tvUaOf(s);
        final String site = s.site;
        if (urls.isEmpty()) {
            tvSearchDone(c, si, new ArrayList<String[]>(), 0, thenTune, gen);
            return;
        }
        tvShowBanner(0, "Проверявам намерените потоци…");
        final int cand = urls.size();
        ui.postDelayed(tvSearchProbeTimeout,TV_SEARCH_PROBE_MS+500);
        tvSearchTask=TV_SEARCH_NET.submit(new Runnable() {
            @Override
            public void run() {
                final ArrayList<String[]> found = tvSearchProbe(urls, hs, known, fams, ua);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        tvSearchDone(c, si, found, cand, thenTune, gen);
                    }
                });
            }
        });
    }

    private final Runnable tvSearchProbeTimeout=()->{
        if(!tvSearching || !tvSearchChecking)return;
        tvSearchDone(tvCur,tvSearchSi,new ArrayList<String[]>(),0,tvSearchTune,tvSearchGen);
    };

    // Проверява плейлистите един по един. Връща {адрес, Referer, Origin, User-Agent} на всеки нов,
    // истински и отделен поток. Работи в отделна нишка.
    private ArrayList<String[]> tvSearchProbe(ArrayList<String> urls,
                                              HashMap<String, HashMap<String, String>> hs,
                                              HashSet<String> known, HashSet<String> fams,
                                              String defUa) {
        ArrayList<String[]> out = new ArrayList<>();
        HashSet<String> covered = new HashSet<>();
        long deadline=SystemClock.uptimeMillis()+TV_SEARCH_PROBE_MS;
        int tried = 0;
        for (String u : urls) {
            if (Thread.currentThread().isInterrupted() || SystemClock.uptimeMillis()>=deadline
                    || out.size() >= TV_MAX_ALTS - 1 || tried >= 10) break;
            String key = tvStreamKey(u);
            boolean isKnown = known.contains(key);
            // Вариант (качество) на плейлист, който вече сме видели: не е отделен поток.
            if (!isKnown && covered.contains(key)) continue;
            String host = hostOf(u);
            if (isBlocked(host)) continue;
            if (!isKnown && !fams.contains(siteBase(host))) continue; // чужд домейн: може да е друг канал
            tried++;
            HashMap<String, String> h = hs.get(u);
            String ua = h != null && h.get("User-Agent") != null ? h.get("User-Agent") : defUa;
            String[] r = tvFetchPlaylist(u, h, ua,deadline);
            if (r == null) continue;
            String t = r[1].trim();
            if (t.startsWith("﻿")) t = t.substring(1);
            boolean valid = false;
            if (t.startsWith("#EXTM3U")) {
                if (t.contains("#EXT-X-STREAM-INF")) {
                    // Главен плейлист: качествата му не са отделни потоци.
                    tvCoverVariants(t, r[0], covered);
                    covered.add(key);
                    valid = true;
                } else if (t.contains("#EXTINF") && !t.contains("#EXT-X-ENDLIST")) {
                    valid = true; // плейлист на живо, без главен (или главният не е хванат)
                }
            } else if (t.contains("<MPD") && t.contains("type=\"dynamic\"")) {
                valid = true;
            }
            if (!valid || isKnown) continue;
            String ref = h != null ? nz(h.get("Referer")) : "";
            String org = h != null ? nz(h.get("Origin")) : "";
            out.add(new String[]{u, ref, org, ua});
            covered.add(key);
        }
        return out;
    }

    // Адресите на качествата и записите в главния плейлист се отбелязват като „вече покрити“.
    private static void tvCoverVariants(String text, String baseUrl, HashSet<String> covered) {
        java.net.URL base;
        try {
            base = new java.net.URL(baseUrl);
        } catch (Exception e) {
            return;
        }
        boolean next = false;
        for (String raw : text.split("\n")) {
            String ln = raw.trim();
            if (ln.isEmpty()) continue;
            if (ln.startsWith("#")) {
                if (ln.startsWith("#EXT-X-STREAM-INF")) next = true;
                int q = ln.indexOf("URI=\"");
                if (q >= 0) {
                    int e = ln.indexOf('"', q + 5);
                    if (e > q) tvCoverOne(base, ln.substring(q + 5, e), covered);
                }
                continue;
            }
            if (next) {
                tvCoverOne(base, ln, covered);
                next = false;
            }
        }
    }

    private static void tvCoverOne(java.net.URL base, String ref, HashSet<String> covered) {
        try {
            covered.add(tvStreamKey(new java.net.URL(base, ref).toString()));
        } catch (Exception ignored) {
            // неразбираем адрес: не се отбелязва
        }
    }

    // Ключ на потока без променливите части (параметри и дълги знакови низове в пътя):
    // същият поток с нов знак (token) е същият поток.
    private static String tvStreamKey(String url) {
        if (url == null) return "";
        try {
            Uri u = Uri.parse(url);
            StringBuilder sb = new StringBuilder(nz(u.getHost()).toLowerCase(Locale.ROOT));
            for (String seg : nz(u.getPath()).split("/")) {
                if (seg.isEmpty()) continue;
                sb.append('/').append(tvTokenSeg(seg) ? "*" : seg.toLowerCase(Locale.ROOT));
            }
            ArrayList<String> parameters=new ArrayList<>(u.getQueryParameterNames());Collections.sort(parameters);
            for(String parameter:parameters){String name=parameter.toLowerCase(Locale.ROOT);
                if(name.equals("id") || name.equals("ch") || name.equals("channel") || name.equals("stream") || name.equals("source") || name.equals("server") || name.equals("player") || name.equals("cid") || name.equals("sid") || name.equals("video") || name.equals("playlist"))sb.append('&').append(name).append('=').append(u.getQueryParameter(parameter));
            }
            return sb.toString();
        } catch (Exception e) {
            return url;
        }
    }

    private static boolean tvTokenSeg(String seg) {
        if (seg.length() < 14) return false;
        boolean digit = false;
        for (int i = 0; i < seg.length(); i++) {
            char ch = seg.charAt(i);
            if (ch >= '0' && ch <= '9') digit = true;
            else if (!((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || ch == '-' || ch == '_'
                    || ch == '=')) {
                return false; // точка или друг знак: това е име на файл
            }
        }
        return digit;
    }

    // Малка заявка за плейлиста: {краен адрес, текст} или null.
    private static String[] tvFetchPlaylist(String url, HashMap<String, String> h, String ua,long deadline) {
        java.net.HttpURLConnection con = null;
        try {
            con = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            int timeout=(int)Math.max(1,Math.min(2000,deadline-SystemClock.uptimeMillis()));
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", ua);
            HashMap<String, String> p = new HashMap<>();
            if (h != null) {
                if (h.get("Referer") != null) p.put("Referer", h.get("Referer"));
                if (h.get("Origin") != null) p.put("Origin", h.get("Origin"));
            }
            tvCommonHeaders(p, url);
            for (Map.Entry<String, String> en : p.entrySet()) {
                con.setRequestProperty(en.getKey(), en.getValue());
            }
            con.setRequestProperty("Accept-Encoding", "identity");
            int code = con.getResponseCode();
            if (code < 200 || code >= 300) return null;
            InputStream in = con.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (bo.size()<200000 && !Thread.currentThread().isInterrupted()
                    && SystemClock.uptimeMillis()<deadline && (n=in.read(buf))>0)bo.write(buf,0,n);
            in.close();
            if(Thread.currentThread().isInterrupted() || SystemClock.uptimeMillis()>=deadline)return null;
            return new String[]{con.getURL().toString(), bo.toString("UTF-8")};
        } catch (Exception e) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    // Резултатът от търсенето: новите потоци стават резервни; ако е натиснато ◀ - първият се пуска.
    private void tvSearchDone(TvChan c, int si, ArrayList<String[]> found, int candidates,
                              boolean thenTune, int gen) {
        if (gen != tvSearchGen || !tvSearching || !tvMode || tvVod || tvCur != c || si < 0 || si >= c.srcs.size()) return;
        thenTune=thenTune || tvSearchTune;
        tvSearching=false;tvSearchChecking=false;tvSearchTune=false;
        ui.removeCallbacks(tvSearchProbeTimeout);
        if(tvSearchTask!=null){tvSearchTask.cancel(true);tvSearchTask=null;}
        TvSrc s = c.srcs.get(si);
        int added = 0;
        for (String[] f : found) {
            boolean have = f[0].equals(s.stream);
            for (String[] al : s.alts) {
                if (al[0].equals(f[0])) have = true;
            }
            if (have) continue;
            // Няма място: отпада най-неуспешният от старите резервни (не и от току-що добавените).
            while (s.alts.size() >= TV_MAX_ALTS) {
                int limit = s.alts.size() - added;
                if (limit <= 0) break;
                int worst = 0;
                for (int k = 1; k < limit; k++) {
                    if (tvAltFails(s.alts.get(k)) > tvAltFails(s.alts.get(worst))) worst = k;
                }
                s.alts.remove(worst);
            }
            if (s.alts.size() >= TV_MAX_ALTS) break;
            s.alts.add(new String[]{f[0], f[1], f[2], f[3], "0"});
            added++;
        }
        if (added > 0) tvSave();
        logEvent("ТВ: още потоци в " + s.site + ": страницата поиска " + candidates
                + " плейлиста, нови потоци " + added);
        if (added == 0) {
            tvShowBanner(3500, "Няма други потоци на този сайт · → за друг сайт");
            return;
        }
        if (thenTune) {
            tvTuneStream(c, si, s.alts.size() - added);
        } else {
            tvShowBanner(4000, "Добавих " + added + (added == 1 ? " нов поток" : " нови потока")
                    + " към " + s.site);
        }
    }
    // ====================== НОВИЯТ ВИД: лога, категории, плочки ======================
    // Логата се теглят само докато е отворен екранът „Моята телевизия“, по веднъж за всяко лого,
    // и остават на кутията. Докато се гледа канал, нищо не се тегли и екранът не държи памет.

    private static final int TV_HOME = -1, TV_ALL = -2;

    // ---- таблици: категория и лого по име на канал ----

    private HashMap<String, Integer> tvCatMap;
    private HashMap<String, String[]> tvLogoMap;

    private void tvTablesInit() {
        if (tvCatMap != null) return;
        HashMap<String, Integer> cm = new HashMap<>();
        for (int i = 0; i < TV_CAT_DATA.length; i++) {
            for (String n : TV_CAT_DATA[i].split("\\|")) cm.put(tvKeyName(n), i);
        }
        HashMap<String, String[]> lm = new HashMap<>();
        for (String line : TV_LOGO_DATA.split("\n")) {
            int a = line.indexOf('=');
            if (a <= 0) continue;
            String path = line.substring(a + 1);
            String flag = "";
            int b = path.indexOf('=');
            if (b > 0) {
                flag = path.substring(b + 1);
                path = path.substring(0, b);
            }
            lm.put(tvKeyName(line.substring(0, a)), new String[]{path, flag});
        }
        tvLogoMap = lm;
        tvCatMap = cm;
    }

    // Категория на канал по името му; всичко непознато (и твоите канали) е „Други“.
    private int tvCatOfName(String name) {
        tvTablesInit();
        Integer i = tvCatMap.get(tvKeyName(name));
        return i == null ? TV_CAT_NAMES.length - 1 : i;
    }

    // Какво се рисува върху плочката на един канал.
    private static class TvPic {
        String name = "";
        int cat;                   // категория (цветът на плочката с име)
        String path;               // път до логото или null, ако няма
        boolean light;             // светла плочка (за тъмни лога)
        StaticLayout lay;          // оформеното име (за плочка без лого)
        TextPaint tp;
        int layW, layH;            // за какъв размер е оформено
    }

    private TvPic tvPicFor(String name) {
        tvTablesInit();
        String key = tvKeyName(name);
        TvPic p = new TvPic();
        p.name = name;
        Integer ci = tvCatMap.get(key);
        p.cat = ci == null ? TV_CAT_NAMES.length - 1 : ci;
        String[] lg = tvLogoMap.get(key);
        if (lg != null) {
            p.path = lg[0];
            p.light = lg[1].equals("L");
        }
        return p;
    }

    // ---- логата: теглят се веднъж, пазят се във файл, а в паметта стоят само видимите ----

    private LruCache<String, Bitmap> tvLogoCache;
    private ThreadPoolExecutor tvLogoPool;
    private final HashMap<String, ArrayList<View>> tvLogoWait = new HashMap<>();
    private final HashSet<String> tvLogoFailed = new HashSet<>();   // не се изтегли (до рестарт)
    private final HashSet<String> tvLogoMissing = new HashSet<>();  // няма на кутията (проверено без мрежа)
    private boolean tvLogoLogged;
    private volatile int tvLogoErrRun;          // неуспешни теглия поред (мрежата не отговаря)
    private volatile int tvLogoGeneration;
    private volatile boolean tvLogoOffline;     // няма връзка: логата не се теглят до следващото отваряне

    // Логото от паметта. Ако го няма, зареждането почва (веднъж) и view се прерисува, когато е готово.
    // net: позволено ли е теглене от интернет (на екрана със списъка - да; докато се гледа - не).
    private Bitmap tvLogoGet(String path, View waiter, boolean net) {
        if (tvLogoCache == null) {
            tvLogoCache = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
                @Override
                protected int sizeOf(String k, Bitmap b) {
                    return b.getByteCount();
                }
            };
        }
        Bitmap b = tvLogoCache.get(path);
        if (b != null) return b;
        if (tvLogoGone(path, net)) return null;
        ArrayList<View> w = tvLogoWait.get(path);
        if (w == null) {
            w = new ArrayList<>();
            tvLogoWait.put(path, w);
            tvLogoJob(path, net);
        }
        if (waiter != null && !w.contains(waiter)) w.add(waiter);
        return null;
    }

    private boolean tvLogoGone(String path, boolean net) {
        return tvLogoFailed.contains(path) || (!net && tvLogoMissing.contains(path));
    }

    private void tvLogoJob(final String path, final boolean net) {
        if (tvLogoPool == null) {
            ThreadPoolExecutor ex = new ThreadPoolExecutor(3, 3, 4, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                @Override
                public Thread newThread(final Runnable r) {
                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            android.os.Process.setThreadPriority(
                                    android.os.Process.THREAD_PRIORITY_BACKGROUND);
                            r.run();
                        }
                    }, "tv-logo");
                    t.setDaemon(true);
                    return t;
                }
            });
            ex.allowCoreThreadTimeOut(true);
            tvLogoPool = ex;
        }
        final int generation = tvLogoGeneration;
        final File dir = new File(getFilesDir(), "tvlogos");
        final int maxW = u(172);
        final int maxH = u(96);
        tvLogoPool.execute(new Runnable() {
            @Override
            public void run() {
                if (generation != tvLogoGeneration) return;
                Bitmap b = null;
                int st = 0; // 0 е готово, 1 няма на кутията, 2 не се изтегли
                try {
                    File f = new File(dir, path.replace('/', '_'));
                    if (!f.exists() || f.length() < 40) {
                        if (!net) st = 1;
                        else if (tvLogoOffline) st = 2;
                        else if (!(dir.isDirectory() || dir.mkdirs()) || !tvLogoDownload(path, f)) st = 2;
                    }
                    if (st == 0) {
                        b = tvLogoDecode(f, maxW, maxH);
                        if (b == null) {
                            f.delete();
                            st = net ? 2 : 1;
                        }
                    }
                } catch (Throwable t) {
                    st = 2;
                }
                final Bitmap fb = b;
                final int fst = st;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (generation == tvLogoGeneration && !isFinishing()) tvLogoDone(path, fb, fst);
                    }
                });
            }
        });
    }

    private void tvLogoDone(String path, Bitmap b, int st) {
        ArrayList<View> w = tvLogoWait.remove(path);
        if (b != null) {
            // Ако никой вече не чака (екранът е затворен), логото не се държи в паметта.
            if (w != null && !w.isEmpty() && tvLogoCache != null) tvLogoCache.put(path, b);
        } else if (st == 2) {
            tvLogoFailed.add(path);
            if (!tvLogoLogged) {
                tvLogoLogged = true;
                logEvent("Лого: не се изтегли " + path);
            }
        } else {
            tvLogoMissing.add(path);
        }
        if (w != null) {
            for (View v : w) {
                if (v.isAttachedToWindow()) v.invalidate();
            }
        }
    }

    // Освобождава паметта на логата (екранът се затваря или започва гледане).
    private void tvLogoTrim() {
        tvLogoGeneration++;
        tvLogoWait.clear();
        if (tvLogoPool != null) tvLogoPool.getQueue().clear();
        if (tvLogoCache != null) tvLogoCache.evictAll();
    }

    // Тегли логото (първо от GitHub, после от огледало) и го записва стегнато до 360 px.
    private boolean tvLogoDownload(String path, File out) {
        String[] urls = {
                "https://raw.githubusercontent.com/tv-logo/tv-logos/" + TV_LOGO_SHA + "/" + path,
                "https://cdn.jsdelivr.net/gh/tv-logo/tv-logos@" + TV_LOGO_SHA + "/" + path};
        for (String us : urls) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(us).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(8000);
                c.setRequestProperty("User-Agent", TV_CHK_UA);
                if (c.getResponseCode() != 200) continue;
                InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bo.write(buf, 0, n);
                    if (bo.size() > 3000000) break;
                }
                in.close();
                if (bo.size() > 3000000 || bo.size() < 60) continue;
                if (tvLogoSave(bo.toByteArray(), out)) {
                    tvLogoErrRun = 0;
                    return true;
                }
            } catch (java.net.UnknownHostException | java.net.SocketTimeoutException
                    | java.net.ConnectException t) {
                // Няма връзка: втори адрес не се пробва, а след три такива поред - и следващите логата.
                if (++tvLogoErrRun >= 3) tvLogoOffline = true;
                return false;
            } catch (Throwable t) {
                // следва другият адрес
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return false;
    }

    // Реже прозрачните краища, смалява до 360 px и записва като PNG.
    private boolean tvLogoSave(byte[] data, File out) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return false;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 360) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (b == null) return false;
        int w = b.getWidth();
        int h = b.getHeight();
        int[] row = new int[w];
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                if ((row[x] >>> 24) > 8) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    maxY = y;
                }
            }
        }
        if (maxX < 0) return false; // празна картинка
        Bitmap c = b;
        if (minX > 0 || minY > 0 || maxX < w - 1 || maxY < h - 1) {
            c = Bitmap.createBitmap(b, minX, minY, maxX - minX + 1, maxY - minY + 1);
        }
        float s = 360f / Math.max(c.getWidth(), c.getHeight());
        if (s < 1f) {
            c = Bitmap.createScaledBitmap(c, Math.max(1, Math.round(c.getWidth() * s)),
                    Math.max(1, Math.round(c.getHeight() * s)), true);
        }
        File tmp = new File(out.getParentFile(), out.getName() + "." + Thread.currentThread().getId() + ".tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        try {
            c.compress(Bitmap.CompressFormat.PNG, 100, fo);
        } finally {
            fo.close();
        }
        return tmp.renameTo(out);
    }

    // Чете записаното лого, смалено до размера, с който се показва.
    private Bitmap tvLogoDecode(File f, int maxW, int maxH) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= maxW && o.outHeight / (sample * 2) >= maxH) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b == null) return null;
        float s = Math.min(1f, Math.min(maxW / (float) b.getWidth(), maxH / (float) b.getHeight()));
        if (s < 1f) {
            b = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * s)),
                    Math.max(1, Math.round(b.getHeight() * s)), true);
        }
        return b;
    }

    // ---- рисуване на плочките (общи за всички; само от главната нишка) ----

    private final android.util.SparseArray<GradientDrawable> tvPlateBgs = new android.util.SparseArray<>();
    private Paint tvPaintObj;
    private final RectF tvRf = new RectF();

    private Paint tvPaint() {
        if (tvPaintObj == null) tvPaintObj = new Paint(Paint.ANTI_ALIAS_FLAG);
        return tvPaintObj;
    }

    private static int tvMix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    // kind: 0 тъмна плочка с лого, 1 светла (за тъмни лога), 2 плочка с името (цвят по категория).
    private GradientDrawable tvPlateBg(int kind, int cat, boolean focus) {
        int key = (kind * 16 + cat) * 2 + (focus ? 1 : 0);
        GradientDrawable g = tvPlateBgs.get(key);
        if (g != null) return g;
        int a, b, stroke;
        if (kind == 1) {
            a = focus ? 0xFFFAFCFF : 0xFFEDF3FC;
            b = focus ? 0xFFE6EEFA : 0xFFD5E0F3;
            stroke = 0x8CFFFFFF;
        } else if (kind == 2) {
            b = focus ? T_SURF2 : T_SURF;
            a = tvMix(b, TV_CAT_COLORS[cat], focus ? 0.42f : 0.26f);
            stroke = 0x248FDBFF;
        } else {
            a = focus ? 0xFF2A4380 : 0xFF18244C;
            b = focus ? T_SURF2 : T_SURF;
            stroke = 0x248FDBFF;
        }
        g = new GradientDrawable(kind == 2 ? GradientDrawable.Orientation.TL_BR
                : GradientDrawable.Orientation.TOP_BOTTOM, new int[]{a, b});
        g.setCornerRadius(u(20));
        g.setStroke(Math.max(1, Math.round(U * 1.5f)), stroke);
        tvPlateBgs.put(key, g);
        return g;
    }

    // Плочка: фон, а върху него логото или (когато няма лого) името на канала.
    // pending: логото се чака - показва се празна тъмна плочка, без име.
    private void tvDrawPlate(Canvas cv, TvPic pic, Bitmap bmp, boolean pending, int l, int t,
                             int w, int h, boolean focus, boolean dead, int padX, int padY) {
        Paint p = tvPaint();
        int kind = bmp != null ? (pic.light ? 1 : 0) : pending ? 0 : 2;
        float rad = u(20);
        if (focus) {
            float hw = u(8);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(hw);
            p.setColor(0x2938BDF8);
            tvRf.set(l - hw / 2f, t - hw / 2f, l + w + hw / 2f, t + h + hw / 2f);
            cv.drawRoundRect(tvRf, rad + hw / 2f, rad + hw / 2f, p);
            float rw = u(3);
            p.setStrokeWidth(rw);
            p.setColor(T_SKY);
            tvRf.set(l - rw / 2f, t - rw / 2f, l + w + rw / 2f, t + h + rw / 2f);
            cv.drawRoundRect(tvRf, rad + rw / 2f, rad + rw / 2f, p);
        }
        GradientDrawable g = tvPlateBg(kind, pic.cat, focus);
        g.setAlpha(dead ? 140 : 255);
        g.setBounds(l, t, l + w, t + h);
        g.draw(cv);
        if (bmp != null) {
            float bw = bmp.getWidth();
            float bh = bmp.getHeight();
            float asp = bw / bh;
            float aw = w - padX;
            float ah = h - padY;
            if (asp < 1.35f) ah = h - Math.round(padY * 0.7f);
            if (asp > 4.5f) aw = w - Math.round(padX * 0.55f);
            float s = Math.min(aw / bw, ah / bh);
            float dw = bw * s;
            float dh = bh * s;
            tvRf.set(l + (w - dw) / 2f, t + (h - dh) / 2f, l + (w + dw) / 2f, t + (h + dh) / 2f);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setAlpha(dead ? 140 : 255);
            p.setFilterBitmap(true);
            cv.drawBitmap(bmp, null, tvRf, p);
            p.setAlpha(255);
        } else if (kind == 2) {
            tvDrawName(cv, pic, l, t, w, h, dead);
        }
    }

    // Името на канала върху плочка без лого: до два реда, шрифтът се смалява, докато се събере.
    private void tvDrawName(Canvas cv, TvPic pic, int l, int t, int w, int h, boolean dead) {
        int aw = w - u(28);
        if (pic.lay == null || pic.layW != aw || pic.layH != h) {
            if (pic.tp == null) {
                pic.tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                pic.tp.setTypeface(tfCondBold);
            }
            float size = u(32);
            float min = u(16);
            StaticLayout sl;
            while (true) {
                pic.tp.setTextSize(size);
                sl = new StaticLayout(pic.name, pic.tp, aw, Layout.Alignment.ALIGN_CENTER, 1.02f, 0f, false);
                if ((sl.getLineCount() <= 2 && sl.getHeight() <= h - u(18)) || size <= min) break;
                size -= u(2);
            }
            pic.lay = sl;
            pic.layW = aw;
            pic.layH = h;
        }
        pic.tp.setColor(dead ? 0x8CEEF4FF : T_TEXT);
        cv.save();
        cv.translate(l + (w - aw) / 2f, t + (h - pic.lay.getHeight()) / 2f);
        pic.lay.draw(cv);
        cv.restore();
    }

    // Само плочката (за голямата карта и за лентата върху картината).
    private class TvPlate extends View {
        boolean showNameWhileLoading;
        private TvPic pic;
        private final int padX, padY;
        private final boolean net;

        TvPlate(Context ctx, float padXU, float padYU, boolean net) {
            super(ctx);
            this.padX = u(padXU);
            this.padY = u(padYU);
            this.net = net;
        }

        void setChan(String name) {
            pic = tvPicFor(name);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas cv) {
            if (pic == null) return;
            Bitmap bmp = null;
            boolean pending = false;
            if (pic.path != null) {
                bmp = tvLogoGet(pic.path, this, net);
                pending = bmp == null && !tvLogoGone(pic.path, net);
            }
            tvDrawPlate(cv, pic, bmp, pending && !showNameWhileLoading, 0, 0, getWidth(), getHeight(), false, false, padX, padY);
        }
    }

    // Канал в мрежата и в редовете: плочка с логото и отдолу номер и име. Всичко се рисува в един view.
    private class TvCard extends View {
        final TvChan chan;
        final TvPic pic;
        final boolean dead;
        boolean edge;   // първа в реда: ◀ води към лентата с категории
        final int wPx, plateH, hPx, padX, padY;
        private final String capNum;
        private final String capName;
        private final float capNumW, badgeW;

        TvCard(Context ctx, TvChan c, TvPic pic, int num, float wU, float plateU) {
            super(ctx);
            this.chan = c;
            this.pic = pic;
            this.dead = tvDead(c);
            this.wPx = u(wU);
            this.plateH = u(plateU);
            this.hPx = plateH + u(42);
            this.padX = u(44);
            this.padY = u(40);
            this.capNum = String.valueOf(num);
            Paint p = tvPaint();
            p.setTypeface(tfCondBold);
            p.setTextSize(u(22));
            this.capNumW = p.measureText(capNum);
            p.setTypeface(tfMedium);
            p.setTextSize(u(17));
            this.capName = TextUtils.ellipsize(c.name, new TextPaint(p),
                    wPx - u(8) - capNumW - u(10), TextUtils.TruncateAt.END).toString();
            p.setTextSize(u(14));
            this.badgeW = dead ? p.measureText("не работи") + u(20) : 0f;
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
            setTag("ch:" + c.id);
            final int ww = wPx;
            final int ph = plateH;
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, ww, ph, u(20));
                }
            });
        }

        @Override
        protected void onMeasure(int wms, int hms) {
            setMeasuredDimension(wPx, hPx);
        }

        @Override
        protected void onDraw(Canvas cv) {
            Bitmap bmp = null;
            boolean pending = false;
            if (pic.path != null) {
                bmp = tvLogoGet(pic.path, this, true);
                pending = bmp == null && !tvLogoGone(pic.path, true);
            }
            boolean f = isFocused();
            tvDrawPlate(cv, pic, bmp, pending, 0, 0, wPx, plateH, f, dead, padX, padY);
            Paint p = tvPaint();
            p.setStyle(Paint.Style.FILL);
            p.setTypeface(tfCondBold);
            p.setTextSize(u(22));
            p.setColor(dead ? T_TEXT3 : T_SKY2);
            float base = plateH + u(11) + u(19);
            cv.drawText(capNum, u(4), base, p);
            p.setTypeface(tfMedium);
            p.setTextSize(u(17));
            p.setColor(f ? T_TEXT : dead ? T_TEXT3 : T_TEXT2);
            cv.drawText(capName, u(4) + capNumW + u(10), base, p);
            if (dead) {
                float bh = u(24);
                tvRf.set(u(8), u(8), u(8) + badgeW, u(8) + bh);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xE03C101C);
                cv.drawRoundRect(tvRf, bh / 2f, bh / 2f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1, u(1)));
                p.setColor(0x80FDA4AF);
                cv.drawRoundRect(tvRf, bh / 2f, bh / 2f, p);
                p.setStyle(Paint.Style.FILL);
                p.setTypeface(tfMedium);
                p.setTextSize(u(14));
                p.setColor(0xFFFDA4AF);
                p.setTextAlign(Paint.Align.CENTER);
                cv.drawText("не работи", u(8) + badgeW / 2f, u(8) + bh / 2f + u(5), p);
                p.setTextAlign(Paint.Align.LEFT);
            }
        }
    }

    // Елемент от лентата с категории вляво: икона или цветна точка, име и брой.
    private class TvRailItem extends FrameLayout {
        final int sel;
        private final TextView nm;
        private final TextView cn;
        private final View bar;

        TvRailItem(Context ctx, int sel, String label, int icon, int count, int color) {
            super(ctx);
            this.sel = sel;
            setFocusable(true);
            setClickable(true);
            GradientDrawable fo = new GradientDrawable();
            fo.setColor(T_SURF2);
            fo.setCornerRadius(u(16));
            fo.setStroke(u(2), TvTheme.FOCUS);
            GradientDrawable on = new GradientDrawable();
            on.setColor(T_SURF2);
            on.setCornerRadius(u(16));
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, fo);
            bg.addState(new int[]{android.R.attr.state_selected}, on);
            bg.addState(new int[]{}, new ColorDrawable(0));
            setBackground(bg);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(u(16), 0, u(18), 0);
            FrameLayout ic = new FrameLayout(ctx);
            if (icon >= 0) {
                ic.addView(new IconView(ctx, icon, T_SKY2),
                        new FrameLayout.LayoutParams(u(22), u(22), Gravity.CENTER));
            } else {
                View dot = new View(ctx);
                GradientDrawable dg = new GradientDrawable();
                dg.setColor(TvTheme.MUTED);
                dg.setCornerRadius(u(4));
                dot.setBackground(dg);
                ic.addView(dot, new FrameLayout.LayoutParams(u(12), u(12), Gravity.CENTER));
            }
            LinearLayout.LayoutParams ilp = lp(u(22), u(22));
            ilp.rightMargin = u(12);
            row.addView(ic, ilp);
            nm = text(label, 18, T_TEXT2, tfCondBold, true);
            row.addView(nm, new LinearLayout.LayoutParams(0, WC, 1f));
            if (count > 0) {
                cn = text(String.valueOf(count), 17, T_TEXT3, tfMedium, true);
                row.addView(cn, lp(WC, WC));
            } else {
                cn = null;
            }
            addView(row, new FrameLayout.LayoutParams(MP, MP));
            bar = new View(ctx);
            GradientDrawable bgd = new GradientDrawable();
            bgd.setColor(TvTheme.FOCUS);
            bgd.setCornerRadius(u(3));
            bar.setBackground(bgd);
            bar.setVisibility(View.INVISIBLE);
            addView(bar, new FrameLayout.LayoutParams(u(5), u(34), Gravity.START | Gravity.CENTER_VERTICAL));
        }

        void mark(boolean on) {
            setSelected(on);
            nm.setTextColor(on ? T_TEXT : T_TEXT2);
            if (cn != null) cn.setTextColor(on ? T_TEXT2 : T_TEXT3);
            bar.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // ---- таблици за новия вид (направени автоматично от gen_tables.py) ----
    private static final String TV_LOGO_SHA = "d32e347bb7c4c640dceec23957802ad9182f58a6";
    private static final String[] TV_CAT_NAMES = {"Български", "Новини", "Спорт", "Филми и сериали", "Документални", "Детски", "Музика", "Чужди", "Други"};
    private static final int[] TV_CAT_COLORS = {0xFF4C8DFF, 0xFFF87171, 0xFF34D399, 0xFFA78BFA, 0xFFFBBF24, 0xFFFB923C, 0xFFF472B6, 0xFF94A3B8, 0xFF2DD4BF};
    // Имената на готовите канали по категория (последната категория „Други“ е за всичко останало).
    private static final String[] TV_CAT_DATA = {
        "БНТ 1|БНТ 2|БНТ 3|БНТ 4|bTV|Nova|Diema|Diema Family|City TV|Kanal 3|Kanal 0|Agro|TVN|Skat TV|TV Evropa|BIT|Alfa TV",
        "Bulgaria On Air|Bulgaria On Air TV|Bulgaria 24|BFM|France 24|Deutsche Welle|Bloomberg|Russia Today|Euronews Bulgaria|Nova News|TRT Haber|Habertürk TV|CNN Türk|NTV|Euronews",
        "Diema Sport|Diema Sport 2|Diema Sport 3|Nova Sport|Max Sport 1|Max Sport 2|Max Sport 3|Max Sport 4|Eurosport 1 BG|Eurosport 2 BG|Match Futbol 1|Match Futbol 2|Match Futbol 3|Ring BG|Sila|FITE|BEK Sports|beIN Sports|Sports TV|TJK TV|TRT Spor|Arena Sport 1|Arena Sport 2|Arena Sport 3|Arena Sport 4|A Spor|BEIN SPORTS HABER",
        "Kino Nova|bTV Action|bTV Cinema|bTV Comedy|bTV Lady|Star Channel|Star Life|Star Crime|AXN|AMC|Epic Drama|Filmbox Stars|Filmbox Extra|Movie Star|Scream|FOX TV|AXN Black|AXN White|FilmBox|FilmBox Plus|Fox Crime|Fox Life|CBS Reality|Comedy Central|TV1000|Cinemax 2|Cinemax|HBO 3|HBO 2|HBO|Btv Story",
        "National Geographic|Nat Geo Wild|Discovery Channel|Investigation Discovery|Animal Planet|Viasat Nature|Viasat Explore|Travel Channel|TLC BG|TLC|DMAX|Docubox|24 Kitchen|TRT Belgesel|Code Fashion|Code Health|VTK|Discovery Science|History Channel|Viasat History|Food Network|Id Xtra",
        "Cartoon Network|Cartoonito|Disney Channel|Nick Jr|Nickelodeon|Nick Toons|Nick Jr RU|Nickelodeon RU|Carousel|TRT Cocuk|Baby TV|Duck TV|Disney Junior|E Kids|Boomerang|Minika GO",
        "Evrokom|Balkanika TV|FEN TV|MTV 00s|MTV Hits|The Voice|Planeta HD BG|Planeta Folk|Power Turk|Kral Pop TV|TRT Muzik|Tatlises TV|Byeaz TV|BG Music Channel|Box TV|Magic TV|DSTV|Tiankov Folk|Fen Folk|Sunrise Music|Mezzo|VH1|MTV Rocks|MTV|Folklor Tv|Rodina Tv|Tiankov Tv",
        "Pervyy Kanal|Russia 1|RU TV|1 HD TV|TRT 1|ATV|Kanal D|Show TV|Star TV|Yaban TV|ARD|ZDF|RTL|PRO 7|WDR|Bayerischer Rundfunk|TV5 Monde|C8 FR|7/8 TV|Fashion TV|Max One|Now TV|TV1|FOX TV (TR)",
    };
    // Име=път на логото в хранилището tv-logo/tv-logos; „=L“ значи светла плочка (тъмно лого).
    private static final String TV_LOGO_DATA = ""
        + "БНТ 1=countries/bulgaria/bnt-1-bg.png\n"
        + "БНТ 2=countries/bulgaria/bnt-2-bg.png\n"
        + "БНТ 3=countries/bulgaria/bnt-3-bg.png\n"
        + "БНТ 4=countries/bulgaria/bnt-4-bg.png\n"
        + "bTV=countries/bulgaria/btv-bg.png\n"
        + "Nova=countries/bulgaria/nova-bg.png\n"
        + "Diema=countries/bulgaria/diema-bg.png\n"
        + "Diema Family=countries/bulgaria/diema-family-bg.png\n"
        + "Kino Nova=countries/bulgaria/kino-nova-bg.png\n"
        + "City TV=countries/bulgaria/city-tv-bg.png\n"
        + "Bulgaria On Air=countries/bulgaria/bulgaria-on-air-bg.png\n"
        + "Bulgaria On Air TV=countries/bulgaria/bulgaria-on-air-bg.png\n"
        + "Evrokom=countries/bulgaria/evrokom-bg.png\n"
        + "Agro=countries/bulgaria/agro-tv-bg.png\n"
        + "Skat TV=countries/bulgaria/skat-bg.png=L\n"
        + "Bulgaria 24=countries/bulgaria/bulgaria-24-bg.png\n"
        + "France 24=countries/international/france-24-int.png\n"
        + "Bloomberg=countries/bulgaria/bloomberg-tv-bulgaria-bg.png\n"
        + "Russia Today=countries/international/russia-today-int.png\n"
        + "Euronews Bulgaria=countries/international/euro-news-int.png\n"
        + "Nova News=countries/bulgaria/nova-news-bg.png\n"
        + "TRT Haber=countries/turkey/trt-haber-hd-tr.png\n"
        + "NTV=countries/turkey/ntv-tr.png\n"
        + "Diema Sport=countries/bulgaria/diema-sport-hd-bg.png\n"
        + "Diema Sport 2=countries/bulgaria/diema-sport2-hd-bg.png\n"
        + "Diema Sport 3=countries/bulgaria/diema-sport3-hd-bg.png\n"
        + "Nova Sport=countries/bulgaria/nova-sport-bg.png\n"
        + "Max Sport 1=countries/bulgaria/max-sport-1-bg.png\n"
        + "Max Sport 2=countries/bulgaria/max-sport-2-bg.png\n"
        + "Max Sport 3=countries/bulgaria/max-sport-3-bg.png\n"
        + "Max Sport 4=countries/bulgaria/max-sport-4-bg.png\n"
        + "Eurosport 1 BG=countries/germany/eurosport-1-de.png\n"
        + "Eurosport 2 BG=countries/germany/eurosport-2-de.png\n"
        + "Match Futbol 1=countries/russia/match-futbol-1-ru.png\n"
        + "Match Futbol 2=countries/russia/match-futbol-2-ru.png\n"
        + "Match Futbol 3=countries/russia/match-futbol-3-ru.png\n"
        + "Ring BG=countries/bulgaria/ring-bg.png\n"
        + "FITE=countries/international/fite-tv-int.png\n"
        + "beIN Sports=countries/international/beinsports/old/stacked/bein-sports-4k-int.png\n"
        + "TJK TV=countries/turkey/tjk-tv-tr.png\n"
        + "TRT Spor=countries/turkey/trt-spor-hd-tr.png\n"
        + "Arena Sport 1=countries/world-europe/arena-sport/arena-sport-1-eu.png\n"
        + "Arena Sport 2=countries/world-europe/arena-sport/arena-sport-2-eu.png\n"
        + "Arena Sport 3=countries/world-europe/arena-sport/arena-sport-3-eu.png\n"
        + "Arena Sport 4=countries/world-europe/arena-sport/arena-sport-4-eu.png\n"
        + "bTV Action=countries/bulgaria/btv-action-bg.png\n"
        + "bTV Cinema=countries/bulgaria/btv-cinema-bg.png\n"
        + "bTV Comedy=countries/bulgaria/btv-comedy-bg.png\n"
        + "bTV Lady=countries/bulgaria/btv-lady-bg.png=L\n"
        + "Star Channel=countries/bulgaria/star-channel-bg.png\n"
        + "Star Life=countries/bulgaria/star-life-bg.png\n"
        + "Star Crime=countries/bulgaria/star-crime-bg.png\n"
        + "AXN=countries/romania/axn-ro.png\n"
        + "AMC=countries/romania/amc-ro.png\n"
        + "Epic Drama=countries/romania/epic-drama-ro.png\n"
        + "Filmbox Stars=countries/international/filmbox-stars-int.png\n"
        + "Filmbox Extra=countries/international/filmbox-extra-hd-int.png\n"
        + "Movie Star=countries/bulgaria/movie-star-bg.png=L\n"
        + "AXN Black=countries/germany/axn-black-de.png\n"
        + "AXN White=countries/germany/axn-white-de.png\n"
        + "FilmBox=countries/international/filmbox-hd-int.png\n"
        + "FilmBox Plus=countries/international/filmbox-plus-int.png\n"
        + "Fox Crime=countries/turkey/fox-crime-tr.png\n"
        + "Fox Life=countries/international/fox-life-int.png\n"
        + "CBS Reality=countries/romania/cbs-reality-ro.png\n"
        + "Comedy Central=countries/germany/comedy-central-de.png\n"
        + "TV1000=countries/romania/tv1000-ro.png\n"
        + "Cinemax 2=countries/romania/cinemax2-ro.png\n"
        + "Cinemax=countries/romania/cinemax-ro.png\n"
        + "HBO 3=countries/romania/hbo3-ro.png\n"
        + "HBO 2=countries/romania/hbo2-ro.png\n"
        + "HBO=countries/romania/hbo-ro.png\n"
        + "National Geographic=countries/germany/hd/national-geographic-hd-de.png\n"
        + "Nat Geo Wild=countries/serbia/national-geographic-wild-rs.png\n"
        + "Discovery Channel=countries/germany/discovery-channel-de.png\n"
        + "Investigation Discovery=countries/international/investigation-discovery-int.png\n"
        + "Animal Planet=countries/international/animal-planet-int.png\n"
        + "Viasat Nature=countries/romania/viasat-nature-ro.png\n"
        + "Viasat Explore=countries/romania/viasat-explore-ro.png=L\n"
        + "TLC BG=countries/international/tlc-int.png\n"
        + "TLC=countries/international/tlc-int.png\n"
        + "DMAX=countries/germany/dmax-de.png\n"
        + "Docubox=countries/international/docubox-hd-int.png\n"
        + "24 Kitchen=countries/bulgaria/24-kitchen-bg.png\n"
        + "TRT Belgesel=countries/turkey/trt-belgesel-tr.png\n"
        + "Code Fashion=countries/bulgaria/code-fashion-bg.png\n"
        + "Code Health=countries/bulgaria/code-health-bg.png\n"
        + "Cartoon Network=countries/international/cartoon-network-hd-int.png\n"
        + "Cartoonito=countries/united-kingdom/cartoonito-uk.png\n"
        + "Disney Channel=countries/international/disney-channel-int.png\n"
        + "Nick Jr=countries/germany/nick-jr-de.png\n"
        + "Nickelodeon=countries/germany/nickelodeon-de.png\n"
        + "Nick Toons=countries/germany/nick-toons-de.png\n"
        + "TRT Cocuk=countries/turkey/trt-cocuk-tr.png\n"
        + "Baby TV=countries/serbia/baby-tv-rs.png\n"
        + "Duck TV=countries/world-europe/duck-tv-eu.png\n"
        + "E Kids=countries/bulgaria/ekids-bg.png\n"
        + "Boomerang=countries/germany/boomerang-de.png\n"
        + "MTV 00s=countries/world-europe/mtv-00s-eu.png\n"
        + "MTV Hits=countries/serbia/mtv-hits-rs.png\n"
        + "The Voice=countries/bulgaria/the-voice-bg.png\n"
        + "Planeta HD BG=countries/bulgaria/planeta-bg.png=L\n"
        + "Planeta Folk=countries/bulgaria/planeta-folk-bg.png\n"
        + "Power Turk=countries/turkey/powerturk-tr.png\n"
        + "Kral Pop TV=countries/turkey/kral-pop-tr.png\n"
        + "TRT Muzik=countries/turkey/trt-muzik-tr.png\n"
        + "Tatlises TV=countries/turkey/tatlises-tv-tr.png\n"
        + "TRT 1=countries/turkey/trt-1-hd-tr.png\n"
        + "ATV=countries/turkey/atv-tr.png\n"
        + "Kanal D=countries/turkey/kanal-d-tr.png\n"
        + "Show TV=countries/turkey/show-tr.png\n"
        + "Yaban TV=countries/turkey/yaban-tr.png\n"
        + "ARD=countries/germany/ard-de.png\n"
        + "ZDF=countries/germany/hd/zdf-hd-de.png\n"
        + "RTL=countries/germany/hd/rtl-hd-de.png\n"
        + "WDR=countries/germany/hd/wdr-hd-de.png\n"
        + "TV5 Monde=countries/international/tv5-monde-int.png\n"
        + "Euronews=countries/international/euro-news-int.png\n"
        + "7/8 TV=countries/bulgaria/7-8-tv-bg.png\n"
        + "Fashion TV=countries/international/fashion-tv-int.png\n"
        + "Now TV=countries/turkey/now-tr.png\n"
        + "TV1=countries/turkey/tv1-tr.png\n"
        + "Minika GO=countries/turkey/minika-go-tr.png\n"
        + "Discovery Science=countries/serbia/discovery-science-rs.png\n"
        + "History Channel=countries/germany/hd/history-hd-de.png\n"
        + "Viasat History=countries/romania/viasat-history-ro.png=L\n"
        + "Food Network=countries/serbia/food-network-rs.png=L\n"
        + "A Spor=countries/turkey/a-spor-tr.png\n"
        + "BEIN SPORTS HABER=countries/international/beinsports/old/stacked/bein-sports-haber-int.png\n"
        + "Magic TV=countries/bulgaria/magic-tv-bg.png\n"
        + "DSTV=countries/bulgaria/dstv-bg.png\n"
        + "Fen Folk=countries/bulgaria/fen-folk-tv-bg.png\n"
        + "Mezzo=countries/france/mezzo-fr.png\n"
        + "VH1=countries/united-kingdom/vh1-uk.png=L\n"
        + "MTV Rocks=countries/united-kingdom/mtv-rocks-uk.png\n"
        + "MTV=countries/germany/hd/mtv-hd-de.png\n"
        + "Folklor Tv=countries/bulgaria/folklor-tv-bg.png\n"
        + "Rodina Tv=countries/bulgaria/tv-rodina-bg.png\n"
        + "BG Music Channel=countries/bulgaria/bg-muic-channel-bg.png\n"
        + "Balkanika TV=countries/bulgaria/balkanika-music-television-bg.png\n"
        + "Carousel=countries/bulgaria/karusel-bg.png\n"
        ;
    // ---- плочките в менюто ----

    // Нарисувана картинка (без файлове): прожектор, голям "play", филмова лента
    // и червена лента "В РАЗРАБОТКА" в ъгъла. Картината е в средата: текстът е вдясно.
    private static class CatalogArt extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rf = new RectF();

        CatalogArt(Context c, Typeface tf) {
            super(c);
            tp.setTypeface(tf);
            tp.setColor(Color.WHITE);
            tp.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            p.setStyle(Paint.Style.FILL);
            float cx = w * 0.36f;
            float cy = h * 0.46f;
            float r = h * 0.25f;
            // мек прожектор около "play"
            p.setColor(0x22FFFFFF);
            c.drawCircle(cx, cy, h * 0.98f, p);
            p.setColor(0x18FFFFFF);
            c.drawCircle(cx, cy, h * 0.64f, p);
            // филмова лента отдолу
            float stripH = h * 0.22f;
            p.setColor(0x55000000);
            c.drawRect(0, h - stripH, w, h, p);
            p.setColor(0xCCFFFFFF);
            float hole = stripH * 0.46f;
            float gap = hole * 0.9f;
            for (float x = gap * 0.7f; x < w; x += hole + gap) {
                float top = h - stripH + (stripH - hole) / 2f;
                rf.set(x, top, x + hole, top + hole);
                c.drawRoundRect(rf, hole * 0.25f, hole * 0.25f, p);
            }
            // голям "play"
            p.setColor(0x33FFFFFF);
            c.drawCircle(cx, cy, r * 1.4f, p);
            p.setColor(0xF2FFFFFF);
            c.drawCircle(cx, cy, r, p);
            p.setColor(0xFFC2410C);
            path.reset();
            path.moveTo(cx - r * 0.28f, cy - r * 0.46f);
            path.lineTo(cx - r * 0.28f, cy + r * 0.46f);
            path.lineTo(cx + r * 0.50f, cy);
            path.close();
            c.drawPath(path, p);
            // червена лента в ъгъла: показва, че разделът още се доработва
            drawDevRibbon(c, p, tp, path, h);
        }
    }

    // Ред "Мои раздели" в менюто: надпис вляво и две широки плочки - филми и телевизия.
    private View makeCatalogLane() {
        LinearLayout lane = new LinearLayout(this);
        lane.setOrientation(LinearLayout.HORIZONTAL);
        lane.setClipChildren(false);

        LinearLayout lab = new LinearLayout(this);
        lab.setOrientation(LinearLayout.HORIZONTAL);
        lab.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ilp = lp(u(56), u(56));
        ilp.rightMargin = u(14);
        lab.addView(iconCircle(IC_GRID, 0xFF16284F, TvTheme.TEXT, 56), ilp);
        LinearLayout nt = new LinearLayout(this);
        nt.setOrientation(LinearLayout.VERTICAL);
        nt.addView(text("Мои раздели", 27, Color.WHITE, tfCondBold, true));
        nt.addView(text("в разработка", 15, C_MUTED, tfMedium, true));
        lab.addView(nt);
        lane.addView(lab, lp(u(220), u(112)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);

        FrameLayout films = sectionTile(new CatalogArt(this, tfCondBold), "Филми и сериали",
                "търси и предложения", new int[]{0xFF1B7A8C, 0xFF0F3541, 0xFFC2570C},
                new Runnable() {
                    @Override
                    public void run() {
                        catFocusKey = null;
                        showCatalog(null);
                    }
                });
        catTile = films;
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, u(112), 2f);
        flp.leftMargin = u(16);
        row.addView(films, flp);

        tvLoad();
        FrameLayout tv = sectionTile(new TvArt(this, tfCondBold), "Моята телевизия",
                tvTileSubText(), new int[]{0xFF2B5FE8, 0xFF1A2F7A, 0xFF0B1230},
                new Runnable() {
                    @Override
                    public void run() {
                        tvShowPanel();
                    }
                });
        tvTile = tv;
        tvTileSub = sectionSub;
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, u(112), 2f);
        tlp.leftMargin = u(16);
        row.addView(tv, tlp);

        row.addView(new View(this), new LinearLayout.LayoutParams(0, u(112), 2f));
        lane.addView(row, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return lane;
    }

    // Малък кръг, който служи за курсор.
    private static class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrows = new Path();
        private boolean scroll;

        // В режим превъртане курсорът е оранжев със стрелки нагоре и надолу.
        void setScroll(boolean on) {
            scroll = on;
            fill.setColor(on ? 0xFFF59E0B : 0xCCFFFFFF);
            invalidate();
        }

        CursorView(Context c) {
            super(c);
            fill.setColor(0xCCFFFFFF);
            stroke.setColor(Color.BLACK);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(4);
            dark.setColor(0xFF1F1300);
            dark.setStyle(Paint.Style.FILL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float r = getWidth() / 2f;
            canvas.drawCircle(r, r, r - 4, fill);
            canvas.drawCircle(r, r, r - 4, stroke);
            if (scroll) {
                float k = r / 20f;
                arrows.reset();
                arrows.moveTo(r, r - 13 * k);
                arrows.lineTo(r - 7 * k, r - 3.5f * k);
                arrows.lineTo(r + 7 * k, r - 3.5f * k);
                arrows.close();
                arrows.moveTo(r, r + 13 * k);
                arrows.lineTo(r - 7 * k, r + 3.5f * k);
                arrows.lineTo(r + 7 * k, r + 3.5f * k);
                arrows.close();
                canvas.drawPath(arrows, dark);
            }
        }
    }
}
