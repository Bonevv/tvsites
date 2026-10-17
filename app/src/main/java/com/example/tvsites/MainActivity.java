package com.example.tvsites;

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
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.BaseAdapter;
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
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    "https://multiembed.mov/?video_id={tmdb}&tmdb=1",
    "https://vidlink.pro/movie/{tmdb}",
    "https://vidspark.to/movie/{tmdb}"
    };
    private static final String[] TV_SOURCES = {
    "https://vidsrc.sh/embed/tv/{tmdb}/{season}/{episode}",
    "https://vidcore.org/embed/tv/{tmdb}/{season}/{episode}",
    "https://multiembed.mov/?video_id={tmdb}&tmdb=1&s={season}&e={episode}",
    "https://vidlink.pro/tv/{tmdb}/{season}/{episode}",
    "https://vidspark.to/tv/{tmdb}/{season}/{episode}"
    };
    // Ключ за TMDB: по-добре го въведи на кутията (репото е публично).
    private static final String TMDB_KEY = "d2c1e02ec9baf771e1ce6b4511a5aea8";

    // Сайтове по подразбиране: {етикет, адрес, режим, група}. Режим: "" | "mobile" | "desktop"
    private static final String[][] DEFAULT_SITES = {
            {"GledaiTV", "https://www.gledaitv.fan/", "", "Телевизия"},
            {"GledaiTV Live", "https://www.gledaitv.live/", "", "Телевизия"},
            {"IPTV-BG", "https://iptv-bg.com/", "", "Телевизия"},
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
    // Спокойна тъмна основа с един акцент (кехлибарен); фокусът е бяла рамка.
    private static final int C_BG = 0xFF0C0D10;
    private static final int C_ACCENT = 0xFFF5A524;
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
    private static final HashMap<String, Bitmap> ICONS = new HashMap<>();

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
        // Мерна единица на менюто (макет 1600x900) и шрифтове на системата.
        android.util.DisplayMetrics dm0 = getResources().getDisplayMetrics();
        U = Math.min(dm0.heightPixels, dm0.widthPixels * 9f / 16f) / 900f;
        tfCondBold = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        tfCond = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
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

    private static final int C_BG_TOP = 0xFF0E1426;
    private static final int C_BG_BOTTOM = 0xFF080B16;
    private static final int C_PANEL = 0xFF141B30;
    private static final int C_PANEL_LINE = 0xFF243053;
    private static final int C_PANEL_FOCUS = 0xFF1F2A4A;
    private static final int C_ICON_BG = 0xFF1F2B4D;
    private static final int C_ICON_FG = 0xFF7DD3FC;
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
            IC_CURSOR = 16, IC_BARS = 17, IC_SHIELD = 18, IC_DOWN = 19, IC_SLIDERS = 20;

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
        focused.setStroke(u(3), strokeColor);
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
        if (menu == null || menu.getChildCount() == 0) return;
        menu.removeViewAt(0);
        menu.addView(buildHeader(), 0, lp(ViewGroup.LayoutParams.MATCH_PARENT, u(80)));
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

    private void fillMenu() {
        menu.removeAllViews();
        capCache = null;
        heroView = null;
        firstTile = null;
        catTile = null;
        menu.addView(buildHeader(), lp(ViewGroup.LayoutParams.MATCH_PARENT, u(80)));

        // Продължи + последни страници.
        ArrayList<String[]> recent = loadRecent();
        LinearLayout feat = new LinearLayout(this);
        feat.setOrientation(LinearLayout.HORIZONTAL);
        feat.setClipChildren(false);
        String lastUrl = prefs.getString("lastUrl", null);
        String lastName = prefs.getString("lastName", null);
        String[] heroSite = null;
        String heroLabel = "Продължи гледане";
        if (lastUrl != null && lastName != null) {
            heroSite = findSite(lastUrl, lastName);
        } else if (!sites.isEmpty()) {
            heroSite = sites.get(0);
            heroLabel = "Започни от тук";
        }
        if (heroSite != null) {
            String sub = "Последно отворен сайт · " + hostOf(heroSite[1]);
            for (String[] rec : recent) {
                if (rec[2].equals(heroSite[0])) {
                    String ago = timeAgo(parseLong(rec[3]));
                    sub = ago.isEmpty() ? rec[0] : rec[0] + " · " + ago;
                    break;
                }
            }
            if (lastUrl == null) sub = hostOf(heroSite[1]);
            heroView = makeHero(heroSite, heroLabel, sub);
            feat.addView(heroView, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.MATCH_PARENT, 1.55f));
        }
        LinearLayout.LayoutParams rplp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        if (heroSite != null) rplp.leftMargin = u(24);
        feat.addView(makeRecentPanel(recent), rplp);
        LinearLayout.LayoutParams flp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.topMargin = u(24);
        menu.addView(feat, flp);

        // Групи от сайтове.
        LinkedHashMap<String, ArrayList<String[]>> groups = new LinkedHashMap<>();
        for (String[] s : sites) {
            String g = groupOf(s);
            ArrayList<String[]> l = groups.get(g);
            if (l == null) {
                l = new ArrayList<>();
                groups.put(g, l);
            }
            l.add(s);
        }
        boolean first = true;
        boolean catAdded = false;
        for (Map.Entry<String, ArrayList<String[]>> en : groups.entrySet()) {
            LinearLayout.LayoutParams llp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = u(first ? 26 : 14);
            first = false;
            menu.addView(makeLane(en.getKey(), en.getValue()), llp);
            if (!catAdded && family(en.getKey()) == 1) {
                catAdded = true;
                LinearLayout.LayoutParams clp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.topMargin = u(14);
                menu.addView(makeCatalogLane(), clp);
            }
        }
        if (!catAdded) {
            LinearLayout.LayoutParams clp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.topMargin = u(14);
            menu.addView(makeCatalogLane(), clp);
        }

        // Бутони най-долу.
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setClipChildren(false);
        LinearLayout b1 = dockButton(IC_REFRESH, false, "Провери за версия",
                "Свали нова, ако има", new Runnable() {
                    @Override
                    public void run() {
                        checkUpdate();
                    }
                });
        LinearLayout b2 = dockButton(IC_TRASH, false, "Изчисти кеша",
                cacheSizeText != null ? "Заема " + cacheSizeText : "Измерва се...", new Runnable() {
                    @Override
                    public void run() {
                        clearCacheNow();
                    }
                });
        cacheSub = (TextView) b2.getTag();
        LinearLayout b3 = dockButton(IC_SPARK, false, "Авто чистене", autoSubText(), new Runnable() {
            @Override
            public void run() {
                toggleAutoCache();
            }
        });
        autoSub = (TextView) b3.getTag();
        LinearLayout b4 = dockButton(IC_PULSE, false, "Диагностика", "Ако нещо спре", new Runnable() {
            @Override
            public void run() {
                showDiagnostics();
            }
        });
        LinearLayout b5 = dockButton(IC_POWER, true, "Изход", "Затваря приложението", new Runnable() {
            @Override
            public void run() {
                exitApp();
            }
        });
        LinearLayout[] bs = {b1, b2, b3, b4, b5};
        for (int i = 0; i < bs.length; i++) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, u(72), 1f);
            if (i > 0) blp.leftMargin = u(14);
            dock.addView(bs[i], blp);
        }
        LinearLayout.LayoutParams dlp = lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = u(26);
        menu.addView(dock, dlp);

        menu.post(new Runnable() {
            @Override
            public void run() {
                if (menuHolder.getVisibility() != View.VISIBLE) return;
                View f = heroView != null ? heroView : firstTile;
                if (f != null) f.requestFocus();
                else menu.requestFocus();
            }
        });
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
                // След връщане към менюто чистим историята, за да не се връщаме в празна страница.
                if (clearHistoryPending && "about:blank".equals(u)) {
                    v.clearHistory();
                    clearHistoryPending = false;
                }
                if (u != null && u.startsWith("http")) hideSplash();
                if (u != null && u.startsWith("http")) srcDone();
            }

            // Страница с код 404 или 5xx (няма такъв филм там): минава на следващия източник.
            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r,
                                            WebResourceResponse resp) {
                if (r == null || resp == null || !r.isForMainFrame()) return;
                if (resp.getStatusCode() >= 400 && srcQueue != null) tryNextSource();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError err) {
                if (r == null || !r.isForMainFrame()) return;
                String u = r.getUrl().toString();
                if (!u.startsWith("http")) return;
                String desc = err == null ? "" : String.valueOf(err.getDescription());
                if (desc.contains("ERR_ABORTED")) return;
                logEvent("Грешка при зареждане: " + r.getUrl().getHost() + " " + desc);
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
                        recoverFromRendererLoss(crashed);
                    }
                });
                return true;
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
        if (cursor == null) {
            cursor = new CursorView(this);
            root.addView(cursor, new FrameLayout.LayoutParams(CURSOR_SIZE, CURSOR_SIZE));

            buildLoadingUi();
            buildLogPanel();
            buildNativeLayer();
            buildStatsView();
        }
    }

    // Страницата е спряна от системата: връщаме се в менюто със съобщение вместо да се затваря всичко.
    private void recoverFromRendererLoss(boolean crashed) {
        if (web == null) return; // страницата вече е затворена
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
    private void destroyWeb() {
        if (web == null) return;
        try {
            CookieManager.getInstance().flush();
        } catch (Exception ignored) {
        }
        try {
            web.stopLoading();
            root.removeView(web);
            web.destroy();
        } catch (Exception ignored) {
        }
        web = null;
    }

    // Спира всичко, което върви в сайта, и изхвърля страницата напълно:
    // паметта се освобождава и всеки сайт започва на чисто.
    private void teardownSite() {
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
        logPanel.setVisibility(View.VISIBLE);
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
            p.addView(mediaRow(pl.get(i), "Пусни в родния плейър"));
        }
        for (int i = fl.size() - 1; i >= 0 && shown < 3; i--, shown++) {
            p.addView(mediaRow(fl.get(i), "Пусни файла в родния плейър"));
        }
        if (shown == 0) {
            p.addView(text("Още няма намерено видео. Пусни канала или филма и отвори Опции пак.",
                    17, C_MUTED, tfMedium, false));
        }
        return p;
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
        r.setBackground(stateBg(0xFF1A2342, C_PANEL_FOCUS, Color.WHITE, u(18), 0));
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
        } else if (key.equals("#domains")) {
            showDomains();
        } else if (key.startsWith("#media:")) {
            hideLog();
            playNative(key.substring(7));
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
        player = new ExoPlayer.Builder(this)
                .setLoadControl(load)
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
        if (backToPage && wasActive) resumePage();
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
        if (scrollMode) {
            setScrollMode(false);
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
        // В менюто Назад затваря приложението напълно.
        exitApp();
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
        ui.removeCallbacksAndMessages(null);
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
        String name = "", poster = "", year = "", overview = "", imdb = "", genres = "";
        double rating;
        int runtime;
        final ArrayList<int[]> seasons = new ArrayList<>(); // {номер, брой епизоди}
    }

    private static class CEpisode {
        int num;
        String name = "", date = "";
        int runtime;
    }

    // Малка мрежова група за каталога: до 3 нишки с нисък приоритет, които се затварят,
    // когато няма работа (не държат памет, докато не гледаш каталога).
    private static final ExecutorService NET = newNetPool();

    private static ExecutorService newNetPool() {
        ThreadPoolExecutor p = new ThreadPoolExecutor(3, 3, 20, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), lowPriority("tvs-net"));
        p.allowCoreThreadTimeOut(true);
        return p;
    }

    private FrameLayout catPanel;
    private ScrollView catScroll;
    private LinearLayout catBox;
    private Runnable catUp;      // какво прави "Назад" на текущия екран
    private Runnable catReturn;  // къде се връщаме, когато излезем от сайта
    private View catTile;        // плочката в менюто (за фокус при връщане)
    private View catMain;        // главният бутон на екрана
    private String catFocusKey;  // коя корица да е на фокус при връщане към списъка
    private volatile int catGen;  // номер на екрана: закъснели отговори се отхвърлят
    private int catRowsLeft;
    private ArrayList<String> srcQueue; // адреси на източниците за отворения сайт
    private int srcIdx;
    private Dialog srcDialog;

    // Отговорите на TMDB се пазят 30 минути, за да не се тегли едно и също.
    private static final HashMap<String, String> TMDB_JSON = new HashMap<>();
    private static final HashMap<String, Long> TMDB_AT = new HashMap<>();
    // Кориците се пазят само в паметта (най-много 10 МБ) и се чистят при излизане от каталога.
    private static final LruCache<String, Bitmap> POSTERS = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
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

    private static final int SRC_SLOTS = 5; // основен + 4 резервни

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

    private String srcPrefText(boolean tv) {
        int p = srcPref(tv);
        String tpl = sourceTpl(tv, p).replace("{", "").replace("}", "");
        String host = hostOf(tpl);
        return (p == 0 ? "основен" : "резервен " + p) + (host == null || host.isEmpty() ? "" : ": " + host);
    }

    // Бутон за избор кой източник да се ползва първи (при повече от един).
    private View srcPickButton(final boolean tv) {
        final LinearLayout b = dockButton(IC_SLIDERS, false, "Източник", srcPrefText(tv),
                new Runnable() {
                    @Override
                    public void run() {
                        // кликът се слага по-долу, за да обновява и надписа
                    }
                });
        final TextView sub = (TextView) b.getTag();
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int p = srcPref(tv);
                for (int k = 1; k <= SRC_SLOTS; k++) {
                    int n = (p + k) % SRC_SLOTS;
                    if (!sourceTpl(tv, n).isEmpty()) {
                        p = n;
                        break;
                    }
                }
                prefs.edit().putInt(tv ? "tvSrcPref" : "mvSrcPref", p).apply();
                sub.setText(srcPrefText(tv));
            }
        });
        return b;
    }

    private int sourceCount(boolean tv) {
        int n = 0;
        for (int i = 0; i < SRC_SLOTS; i++) {
            if (!sourceTpl(tv, i).isEmpty()) n++;
        }
        return n;
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
        final String hit = tmdbCached(path);
        if (hit != null) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    done.run(hit, null);
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
                    c = (HttpURLConnection) new URL(url).openConnection();
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
                        if (isFinishing()) return;
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
                t.poster = jstr(o, "poster_path");
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
            String p = jstr(o, "poster_path");
            if (!p.isEmpty()) t.poster = p;
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
                if (e.num > 0) out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---- корици ----

    private static void roundClip(View v, final int radiusPx) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radiusPx);
            }
        });
        v.setClipToOutline(true);
    }

    private void loadPoster(final String path, final String size, final ImageView iv,
                            final View placeholder, final int gen) {
        if (path == null || path.isEmpty()) return;
        final String key = size + path;
        Bitmap hit = POSTERS.get(key);
        if (hit != null) {
            iv.setImageBitmap(hit);
            iv.setVisibility(View.VISIBLE);
            placeholder.setVisibility(View.GONE);
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
                    c = (HttpURLConnection) new URL("https://image.tmdb.org/t/p/" + size + path)
                            .openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(10000);
                    if (c.getResponseCode() == 200) {
                        // Целият отговор се чете и потокът се затваря: връзката се използва
                        // повторно за следващата корица (без ново договаряне на защитата).
                        byte[] data = readBytes(c.getInputStream(), 600000);
                        reuse = true;
                        BitmapFactory.Options o = new BitmapFactory.Options();
                        o.inPreferredConfig = Bitmap.Config.RGB_565; // наполовина памет
                        b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (c != null && !reuse) c.disconnect();
                }
                if (b == null) return;
                POSTERS.put(key, b);
                final Bitmap fb = b;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != catGen || isFinishing()) return;
                        iv.setImageBitmap(fb);
                        iv.setVisibility(View.VISIBLE);
                        placeholder.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    // Корица със заоблени ъгли; докато се зареди, е цветна плочка с буква.
    private FrameLayout posterArt(CTitle t, String size, int radiusPx) {
        FrameLayout inner = new FrameLayout(this);
        int[][] pal = PALS[1];
        int[] col = pal[Math.abs(t.id) % pal.length];
        inner.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{col[0], col[1]}));
        roundClip(inner, radiusPx);
        TextView letter = text(initial(t.name), 64, 0x66FFFFFF, tfCondBold, false);
        letter.setGravity(Gravity.CENTER);
        letter.setIncludeFontPadding(false);
        inner.addView(letter, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setVisibility(View.GONE);
        inner.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (!t.poster.isEmpty()) loadPoster(t.poster, size, iv, letter, catGen);
        return inner;
    }

    // Клетка в ред: корица (на нея е фокусът), име и година.
    private View posterCell(final CTitle t, final Runnable onClick) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);

        final FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag("t" + t.id + (t.tv ? "t" : "m"));
        card.setPadding(u(4), u(4), u(4), u(4));
        card.setBackground(stateBg(C_PANEL, C_PANEL_FOCUS, Color.WHITE, u(16), C_PANEL_LINE));
        FrameLayout inner = posterArt(t, "w185", u(12));
        if (t.rating > 0) {
            TextView r = text(String.format(Locale.US, "%.1f", t.rating), 13, 0xFF241800,
                    Typeface.DEFAULT_BOLD, true);
            r.setPadding(u(8), u(2), u(8), u(2));
            GradientDrawable rb = new GradientDrawable();
            rb.setColor(C_AMBER_FG);
            rb.setCornerRadius(u(10));
            r.setBackground(rb);
            FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            rlp.setMargins(0, u(8), u(8), 0);
            inner.addView(r, rlp);
        }
        card.addView(inner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cell.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, u(236)));

        TextView nm = text(t.name, 16, Color.WHITE, tfMedium, false);
        nm.setMaxLines(2);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        nm.setPadding(u(4), u(10), u(4), 0);
        cell.addView(nm);
        if (!t.year.isEmpty()) {
            TextView y = text(t.year, 14, C_MUTED, tfMedium, true);
            y.setPadding(u(4), u(2), u(4), 0);
            cell.addView(y);
        }
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                catFocusKey = (String) card.getTag();
                onClick.run();
            }
        });
        card.setTag(R_SCALE, 1.08f);
        card.setOnFocusChangeListener(tileFocus);
        return cell;
    }

    // Ред от списък (епизод): заглавие, пояснение и малка иконка "пусни".
    private View catRow(String title, String sub, final Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(u(22), u(10), u(22), u(10));
        r.setMinimumHeight(u(66));
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackground(stateBg(C_PANEL, C_PANEL_FOCUS, Color.WHITE, u(20), C_PANEL_LINE));
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 20, Color.WHITE, tfMedium, true));
        if (!sub.isEmpty()) tx.addView(text(sub, 15, C_MUTED, tfMedium, true));
        r.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(new IconView(this, IC_PLAY, C_ICON_FG), lp(u(26), u(26)));
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        r.setTag(R_SCALE, 1.02f);
        r.setOnFocusChangeListener(tileFocus);
        return r;
    }

    private LinearLayout.LayoutParams lpTopW(int w, int h, int topPx) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(w, h);
        l.topMargin = topPx;
        return l;
    }

    private LinearLayout.LayoutParams lpTop(int topPx) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = topPx;
        return l;
    }

    // ---- екрани ----

    private boolean catShown() {
        return catPanel != null && catPanel.getVisibility() == View.VISIBLE;
    }

    private void buildCatalogUi() {
        catPanel = new FrameLayout(this);
        catPanel.setBackground(pageBg());
        catScroll = new ScrollView(this);
        catScroll.setFillViewport(true);
        catScroll.setVerticalScrollBarEnabled(false);
        catScroll.setClipChildren(false);
        catScroll.setClipToPadding(false);
        catBox = new LinearLayout(this);
        catBox.setOrientation(LinearLayout.VERTICAL);
        catBox.setClipChildren(false);
        catBox.setPadding(u(64), u(36), u(64), u(30));
        catScroll.addView(catBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        catPanel.addView(catScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        catPanel.setVisibility(View.GONE);
        root.addView(catPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // Показва каталога. first е екранът, с който да започне (по подразбиране началото).
    private void showCatalog(Runnable first) {
        if (isFinishing()) return;
        if (catPanel == null) buildCatalogUi();
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
        catUp = null;
        if (catPanel != null) {
            catPanel.setVisibility(View.GONE);
            catBox.removeAllViews();
        }
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        POSTERS.evictAll();
        View f = catTile != null ? catTile : (heroView != null ? heroView : firstTile);
        if (f != null) f.requestFocus();
        updateState();
    }

    private View catHeader(String title, String sub, boolean withSettings) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ilp = lp(u(60), u(60));
        ilp.rightMargin = u(16);
        h.addView(iconCircle(IC_FILM, C_RED_BG, C_RED_FG, 60), ilp);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 44, Color.WHITE, tfCondBold, true));
        tx.addView(text(sub, 17, C_MUTED, tfMedium, true));
        h.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (withSettings) {
            LinearLayout b = dockButton(IC_SLIDERS, false, "Източници", "Ключ и адреси",
                    new Runnable() {
                        @Override
                        public void run() {
                            showSourcesDialog();
                        }
                    });
            catMain = b;
            h.addView(b, lp(u(300), u(72)));
        }
        return h;
    }

    private View catNoKey() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(u(28), u(22), u(28), u(24));
        p.setBackground(panelBg(26));
        p.addView(text("Още няма ключ за TMDB", 26, Color.WHITE, tfCondBold, true));
        TextView d = text("Безплатният ключ дава корици, оценки и описания. Вземи го от "
                + "themoviedb.org (Профил, Настройки, API) и го въведи с бутона \"Източници\" "
                + "горе вдясно. Дотогава виждаш само примерни карти.", 17, C_MUTED, tfMedium, false);
        d.setPadding(0, u(8), 0, 0);
        p.addView(d);
        return p;
    }

    // Нов ред със заглавие; връща лентата, в която се слагат корици.
    private LinearLayout addRowShell(String title) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setClipChildren(false);
        if (!title.isEmpty()) {
            TextView h = text(title, 24, Color.WHITE, tfCondBold, true);
            h.setPadding(u(6), 0, 0, u(8));
            block.addView(h);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setPadding(u(8), u(10), u(8), u(10)); // място за уголемяването на фокуса
        hs.addView(strip, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        block.addView(hs);
        catBox.addView(block, lpTop(u(20)));
        return strip;
    }

    private LinearLayout.LayoutParams cellLp2() {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                u(158), ViewGroup.LayoutParams.WRAP_CONTENT);
        l.rightMargin = u(14);
        return l;
    }

    // Когато фокусът е близо до края на вече заредените корици, се зарежда следващата страница.
    private void watchEnd(View cell, CTitle t, final int idx, final int[] count, final int ahead,
                          final Runnable more) {
        View card = cell.findViewWithTag("t" + t.id + (t.tv ? "t" : "m"));
        if (card == null) return;
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                tileFocus.onFocusChange(v, has);
                if (has && idx >= count[0] - ahead) more.run();
            }
        });
    }

    // Ред от началния екран: като се стигне до края, се дозареждат още (до 10 страници по 20).
    private void addCatRow(String title, final String path, final boolean tv, final int gen) {
        final LinearLayout strip = addRowShell(title);
        final TextView wait = text("Зареждане...", 17, C_MUTED, tfMedium, false);
        wait.setPadding(u(8), u(90), 0, u(90));
        strip.addView(wait);
        final int[] page = {0};
        final int[] count = {0};
        final boolean[] busy = {false};
        final boolean[] done = {false};
        final Runnable[] more = new Runnable[1];
        more[0] = new Runnable() {
            @Override
            public void run() {
                if (busy[0] || done[0] || gen != catGen) return;
                busy[0] = true;
                final int p = ++page[0];
                tmdbFetch(path + "?language=bg-BG&page=" + p, new Done() {
                    @Override
                    public void run(String json, String err) {
                        busy[0] = false;
                        if (gen != catGen) return;
                        ArrayList<CTitle> list = json == null ? null : parseList(json, tv, 20);
                        if (p == 1) strip.removeAllViews();
                        if (list == null || list.isEmpty()) {
                            if (p == 1 || err == null) done[0] = true;
                            else page[0]--;
                            if (p == 1) {
                                TextView e = text(err != null ? err : "Няма резултати.", 17,
                                        C_RED_FG, tfMedium, false);
                                e.setPadding(u(8), u(80), 0, u(80));
                                strip.addView(e);
                            }
                        } else {
                            if (p >= 5) done[0] = true; // най-много 100 заглавия на ред: пази паметта
                            int base = count[0];
                            for (int i = 0; i < list.size(); i++) {
                                final CTitle t = list.get(i);
                                View cell = posterCell(t, new Runnable() {
                                    @Override
                                    public void run() {
                                        catDetail(t);
                                    }
                                });
                                strip.addView(cell, cellLp2());
                                watchEnd(cell, t, base + i, count, 6, more[0]);
                            }
                            count[0] += list.size();
                            if (p == 1 && catFocusKey != null) {
                                View want = findByTag(strip, catFocusKey);
                                if (want != null) {
                                    want.requestFocus();
                                    catFocusKey = null;
                                }
                            }
                        }
                        if (p == 1 && --catRowsLeft <= 0 && catScroll.findFocus() == null) {
                            catFocusKey = null;
                            ArrayList<View> fs = new ArrayList<>();
                            catBox.addFocusables(fs, View.FOCUS_DOWN);
                            for (View fv : fs) {
                                if (fv != null) {
                                    fv.requestFocus();
                                    break;
                                }
                            }
                        }
                    }
                });
            }
        };
        more[0].run();
    }

    // Начало: редове с корици (или примерни карти, докато няма ключ).
    private void catHome() {
        final int gen = ++catGen;
        catUp = new Runnable() {
            @Override
            public void run() {
                hideCatalog();
            }
        };
        catParent = null;
        catBox.removeAllViews();
        catMain = null;
        catBox.addView(catHeader("Наши филми", "Филми и сериали", true));
        if (tmdbKey().isEmpty()) {
            catBox.addView(catNoKey(), lpTop(u(24)));
            LinearLayout strip = addRowShell("Пример (отворени филми на Blender)");
            String[] names = {"Big Buck Bunny", "Sintel", "Tears of Steel", "Elephants Dream",
                    "Cosmos Laundromat", "Spring", "Agent 327"};
            String[] years = {"2008", "2010", "2012", "2006", "2015", "2019", "2017"};
            for (int i = 0; i < names.length; i++) {
                CTitle t = new CTitle();
                t.id = -(i + 1);
                t.name = names[i];
                t.year = years[i];
                strip.addView(posterCell(t, new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                "Примерна карта. Въведи ключ за TMDB от бутона Източници.",
                                Toast.LENGTH_LONG).show();
                    }
                }), cellLp2());
            }
        } else {
            catRowsLeft = 4;
            catActions(null);
            addCatRow("Популярни филми", "/movie/popular", false, gen);
            addCatRow("Най-добре оценени филми", "/movie/top_rated", false, gen);
            addCatRow("Популярни сериали", "/tv/popular", true, gen);
            addCatRow("Най-добре оценени сериали", "/tv/top_rated", true, gen);
            if (catFocusKey == null && catMain != null) catMain.requestFocus();
        }
        if (catFocusKey == null) catScroll.scrollTo(0, 0);
        catBox.post(new Runnable() {
            @Override
            public void run() {
                if (gen == catGen && tmdbKey().isEmpty() && catMain != null) catMain.requestFocus();
            }
        });
    }

    private String srcNote(CTitle t) {
        int n = sourceCount(t.tv);
        return n == 0 ? "Няма настроен източник" : "Източници: " + n;
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
        catBox.addView(catHeader(t.name, t.tv ? "Сериал" : "Филм", false));
        final TextView wait = text("Зареждане...", 18, C_MUTED, tfMedium, false);
        wait.setPadding(u(6), u(40), 0, 0);
        catBox.addView(wait);
        catScroll.scrollTo(0, 0);
        String path = (t.tv ? "/tv/" : "/movie/") + t.id + "?language=bg-BG"
                + (t.tv ? "&append_to_response=external_ids" : "");
        tmdbFetch(path, new Done() {
            @Override
            public void run(String json, String err) {
                if (gen != catGen) return;
                if (json == null) {
                    wait.setText(err == null ? "Грешка при зареждане" : err);
                    wait.setTextColor(C_RED_FG);
                    return;
                }
                parseDetail(json, t);
                catBox.removeView(wait);
                catBox.addView(detailBody(t), lpTop(u(26)));
                if (catMain != null) catMain.requestFocus();
            }
        });
    }

    private Runnable backTo(final Runnable screen) {
        return new Runnable() {
            @Override
            public void run() {
                showCatalog(screen);
            }
        };
    }

    private View detailBody(final CTitle t) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setClipChildren(false);

        FrameLayout pf = new FrameLayout(this);
        pf.setPadding(u(4), u(4), u(4), u(4));
        pf.setBackground(panelBg(22));
        pf.addView(posterArt(t, "w342", u(18)), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(pf, lp(u(264), u(396)));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setClipChildren(false);
        StringBuilder meta = new StringBuilder();
        if (!t.year.isEmpty()) meta.append(t.year);
        if (t.rating > 0) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append("оценка ").append(String.format(Locale.US, "%.1f", t.rating));
        }
        if (t.runtime > 0) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(t.runtime).append(" мин");
        }
        if (meta.length() > 0) right.addView(text(meta.toString(), 21, 0xFFE6F2FB, tfMedium, true));
        if (!t.genres.isEmpty()) {
            TextView g = text(t.genres, 17, C_MUTED, tfMedium, true);
            g.setPadding(0, u(4), 0, 0);
            right.addView(g);
        }
        TextView ov = text(t.overview.isEmpty() ? "Няма описание на български." : t.overview, 18,
                0xFFD5DCF0, tfMedium, false);
        ov.setMaxLines(6);
        ov.setEllipsize(TextUtils.TruncateAt.END);
        ov.setLineSpacing(0, 1.15f);
        ov.setPadding(0, u(14), 0, u(16));
        right.addView(ov);

        if (!t.tv) {
            LinearLayout play = dockButton(IC_PLAY, false, "Пусни", srcNote(t), new Runnable() {
                @Override
                public void run() {
                    catPlay(t, 1, 1, backTo(new Runnable() {
                        @Override
                        public void run() {
                            catDetail(t);
                        }
                    }));
                }
            });
            catMain = play;
            right.addView(play, lp(u(340), u(72)));
        } else {
            String last = prefs.getString("tvlast:" + t.id, "");
            int ci = last.indexOf(':');
            if (ci > 0) {
                final int ls = (int) parseLong(last.substring(0, ci));
                final int le = (int) parseLong(last.substring(ci + 1));
                if (ls > 0 && le > 0) {
                    LinearLayout cont = dockButton(IC_PLAY, false, "Продължи: сезон " + ls
                            + ", епизод " + le, srcNote(t), new Runnable() {
                        @Override
                        public void run() {
                            catPlay(t, ls, le, backTo(new Runnable() {
                                @Override
                                public void run() {
                                    catEpisodes(t, ls);
                                }
                            }));
                        }
                    });
                    catMain = cont;
                    right.addView(cont, lp(u(420), u(72)));
                }
            }
            if (t.seasons.isEmpty()) {
                TextView none = text("Няма данни за сезоните.", 17, C_MUTED, tfMedium, false);
                none.setPadding(0, u(10), 0, 0);
                right.addView(none);
            }
            LinearLayout row = null;
            for (int i = 0; i < t.seasons.size(); i++) {
                final int sn = t.seasons.get(i)[0];
                int ec = t.seasons.get(i)[1];
                if (i % 2 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setClipChildren(false);
                    right.addView(row, lpTop(u(i == 0 && catMain == null ? 0 : 12)));
                }
                LinearLayout sb = dockButton(IC_FILM, false, "Сезон " + sn, ec + " епизода",
                        new Runnable() {
                            @Override
                            public void run() {
                                catEpisodes(t, sn);
                            }
                        });
                if (catMain == null) catMain = sb;
                LinearLayout.LayoutParams slp = lp(u(300), u(72));
                if (i % 2 == 1) slp.leftMargin = u(14);
                row.addView(sb, slp);
            }
        }
        if (sourceCount(t.tv) > 1) {
            right.addView(srcPickButton(t.tv), lpTopW(u(340), u(72), u(12)));
        }
        TextView note = text(srcNote(t), 15, sourceCount(t.tv) == 0 ? C_AMBER_FG : C_MUTED,
                tfMedium, true);
        note.setPadding(u(4), u(14), 0, 0);
        right.addView(note);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rlp.leftMargin = u(40);
        body.addView(right, rlp);
        return body;
    }

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
        catBox.addView(catHeader(t.name, "Сезон " + season, false));
        if (sourceCount(true) > 1) {
            catBox.addView(srcPickButton(true), lpTopW(u(340), u(72), u(16)));
        }
        final TextView wait = text("Зареждане...", 18, C_MUTED, tfMedium, false);
        wait.setPadding(u(6), u(40), 0, 0);
        catBox.addView(wait);
        catScroll.scrollTo(0, 0);
        tmdbFetch("/tv/" + t.id + "/season/" + season + "?language=bg-BG", new Done() {
            @Override
            public void run(String json, String err) {
                if (gen != catGen) return;
                catBox.removeView(wait);
                ArrayList<CEpisode> list = json == null ? null : parseEpisodes(json);
                if (list == null || list.isEmpty()) {
                    TextView e = text(err != null ? err : "Няма епизоди.", 18, C_RED_FG,
                            tfMedium, false);
                    e.setPadding(u(6), u(40), 0, 0);
                    catBox.addView(e);
                    return;
                }
                String lastKey = prefs.getString("tvlast:" + t.id, "");
                View focus = null;
                for (final CEpisode ep : list) {
                    String title = ep.num + ". " + (ep.name.isEmpty() ? "Епизод " + ep.num : ep.name);
                    String sub = ep.date + (ep.runtime > 0 ? (ep.date.isEmpty() ? "" : "  ·  ")
                            + ep.runtime + " мин" : "");
                    View r = catRow(title, sub, new Runnable() {
                        @Override
                        public void run() {
                            catPlay(t, season, ep.num, backTo(new Runnable() {
                                @Override
                                public void run() {
                                    catEpisodes(t, season);
                                }
                            }));
                        }
                    });
                    catBox.addView(r, lpTop(u(10)));
                    if (focus == null) focus = r;
                    if (lastKey.equals(season + ":" + ep.num)) focus = r;
                }
                if (focus != null) focus.requestFocus();
            }
        });
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

    // ---- категории и търсене ----

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

    private Runnable catParent; // екранът, от който е отворена страницата на заглавие

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
                t.poster = jstr(o, "poster_path");
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

    private void catActions(final Runnable here) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout s = dockButton(IC_SEARCH, false, "Търсене", "Име на филм или сериал",
                new Runnable() {
                    @Override
                    public void run() {
                        showSearchDialog();
                    }
                });
        LinearLayout c = dockButton(IC_SLIDERS, false, "Категории", "Жанрове",
                new Runnable() {
                    @Override
                    public void run() {
                        catGenres();
                    }
                });
        LinearLayout d = dockButton(IC_SPARK, false, "Предложи ми", "Хвърли зара", new Runnable() {
            @Override
            public void run() {
                catGenres(true);
            }
        });
        row.addView(s, lp(u(330), u(72)));
        LinearLayout.LayoutParams clp = lp(u(330), u(72));
        clp.leftMargin = u(14);
        row.addView(c, clp);
        LinearLayout.LayoutParams dlp = lp(u(330), u(72));
        dlp.leftMargin = u(14);
        row.addView(d, dlp);
        catBox.addView(row, lpTop(u(22)));
        catMain = s;
    }

    private void catGenres() {
        catGenres(false);
    }

    private void catGenres(final boolean suggest) {
        final int gen = ++catGen;
        catUp = new Runnable() {
            @Override
            public void run() {
                catHome();
            }
        };
        catParent = null;
        catBox.removeAllViews();
        catMain = null;
        catBox.addView(catHeader(suggest ? "Предложи ми" : "Категории",
                suggest ? "Избери жанр и ще хвърля зара" : "Избери жанр", false));
        View first = null;
        for (int kind = 0; kind < 2; kind++) {
            final boolean tv = kind == 1;
            int[] ids = tv ? TV_GENRE_ID : MOVIE_GENRE_ID;
            String[] names = tv ? TV_GENRE_NAME : MOVIE_GENRE_NAME;
            if (suggest) {
                int[] ids2 = new int[ids.length + 1];
                String[] names2 = new String[names.length + 1];
                names2[0] = "Всякакви";
                System.arraycopy(ids, 0, ids2, 1, ids.length);
                System.arraycopy(names, 0, names2, 1, names.length);
                ids = ids2;
                names = names2;
            }
            TextView h = text(tv ? "Сериали" : "Филми", 24, Color.WHITE, tfCondBold, true);
            h.setPadding(u(6), 0, 0, u(4));
            catBox.addView(h, lpTop(u(24)));
            LinearLayout row = null;
            for (int i = 0; i < ids.length; i++) {
                if (i % 5 == 0) {
                    row = new LinearLayout(this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setClipChildren(false);
                    catBox.addView(row, lpTop(u(10)));
                }
                final int gid = ids[i];
                final String gname = names[i];
                LinearLayout b = dockButton(tv ? IC_TV : IC_FILM, false, gname, "", new Runnable() {
                    @Override
                    public void run() {
                        catFocusKey = null;
                        if (suggest) catSuggest(tv, gid, gname, true);
                        else catGenre(tv, gid, gname);
                    }
                });
                if (first == null) first = b;
                LinearLayout.LayoutParams blp = lp(u(214), u(66));
                blp.rightMargin = u(10);
                row.addView(b, blp);
            }
        }
        catScroll.scrollTo(0, 0);
        if (first != null) first.requestFocus();
    }

    private ArrayList<CTitle> catPicks;

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
        catBox.addView(catHeader("Предложи ми", (tv ? "Сериали" : "Филми") + ": " + gname, false));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        LinearLayout dice = dockButton(IC_SPARK, false, "Хвърли пак", "Други 5 предложения",
                new Runnable() {
                    @Override
                    public void run() {
                        catFocusKey = null;
                        catSuggest(tv, gid, gname, true);
                    }
                });
        LinearLayout other = dockButton(IC_SLIDERS, false, "Друг жанр", "Смени избора",
                new Runnable() {
                    @Override
                    public void run() {
                        catGenres(true);
                    }
                });
        row.addView(dice, lp(u(330), u(72)));
        LinearLayout.LayoutParams olp = lp(u(330), u(72));
        olp.leftMargin = u(14);
        row.addView(other, olp);
        catBox.addView(row, lpTop(u(22)));
        catMain = dice;
        final LinearLayout strip = addRowShell("");
        final TextView wait = text("Зареждане...", 17, C_MUTED, tfMedium, false);
        wait.setPadding(u(8), u(90), 0, u(90));
        strip.addView(wait);
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
                    TextView e = text(err != null ? err : "Няма предложения.", 17, C_RED_FG,
                            tfMedium, false);
                    e.setPadding(u(8), u(80), 0, u(80));
                    strip.addView(e);
                    return;
                }
                Collections.shuffle(list);
                catPicks = new ArrayList<>(list.subList(0, Math.min(5, list.size())));
                showPicks(strip, catPicks);
                if (catMain != null) catMain.requestFocus();
            }
        });
    }

    private void showPicks(LinearLayout strip, ArrayList<CTitle> picks) {
        strip.removeAllViews();
        for (final CTitle t : picks) {
            strip.addView(posterCell(t, new Runnable() {
                @Override
                public void run() {
                    catDetail(t);
                }
            }), cellLp2());
        }
    }

    private void catGenre(final boolean tv, int genreId, final String genreName) {
        String path = "/discover/" + (tv ? "tv" : "movie") + "?language=bg-BG&sort_by=popularity.desc"
                + "&vote_count.gte=50&with_genres=" + genreId;
        catGrid(genreName, tv ? "Сериали" : "Филми", path, tv, new Runnable() {
            @Override
            public void run() {
                catGenres();
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
        catBox.addView(catHeader(title, sub, false));
        final TextView wait = text("Зареждане...", 18, C_MUTED, tfMedium, false);
        wait.setPadding(u(6), u(40), 0, 0);
        catBox.addView(wait);
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
                        if (p == 1) catBox.removeView(wait);
                        ArrayList<CTitle> list = json == null ? null
                                : (path.startsWith("/search") ? parseMixed(json)
                                : parseList(json, tv, 20));
                        if (list == null || list.isEmpty()) {
                            if (p == 1 || err == null) done[0] = true;
                            else page[0]--;
                            if (p == 1) {
                                TextView e = text(err != null ? err : "Няма резултати.", 18,
                                        err != null ? C_RED_FG : C_MUTED, tfMedium, false);
                                e.setPadding(u(6), u(40), 0, 0);
                                catBox.addView(e);
                            }
                            return;
                        }
                        if (p >= 8) done[0] = true; // най-много около 160 заглавия
                        View focus = null;
                        int base = count[0];
                        for (int i = 0; i < list.size(); i++) {
                            if ((base + i) % 7 == 0) strip[0] = addRowShell("");
                            final CTitle t = list.get(i);
                            View cell = posterCell(t, new Runnable() {
                                @Override
                                public void run() {
                                    catDetail(t);
                                }
                            });
                            strip[0].addView(cell, cellLp2());
                            watchEnd(cell, t, base + i, count, 14, more[0]);
                            String key = "t" + t.id + (t.tv ? "t" : "m");
                            if (p == 1) {
                                if (focus == null) focus = cell.findViewWithTag(key);
                                if (key.equals(catFocusKey)) focus = cell.findViewWithTag(key);
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

    private void showSearchDialog() {
        if (isFinishing() || (srcDialog != null && srcDialog.isShowing())) return;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(22), dp(28), dp(22));
        GradientDrawable pbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2A1B6B, 0xFF171C3F});
        pbg.setCornerRadius(dp(24));
        pbg.setStroke(dp(1), 0x44FFFFFF);
        panel.setBackground(pbg);
        TextView title = new TextView(this);
        title.setText("Търсене");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
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
        btns.addView(makeButton("Търси", false, go), l1);
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
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(0xFF7B86A8);
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
        l.setTextColor(C_MUTED);
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
                new int[]{0xFF2A1B6B, 0xFF171C3F});
        pbg.setCornerRadius(dp(24));
        pbg.setStroke(dp(1), 0x44FFFFFF);
        panel.setBackground(pbg);

        TextView title = new TextView(this);
        title.setText("Източници и ключ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Ключ за TMDB и до три адреса (основен и резервни). В адреса се слагат: "
                + "{tmdb} {imdb} {season} {episode}. Празно поле = не се ползва. "
                + "Пример за вида: https://example.com/embed/movie/{tmdb}");
        sub.setTextColor(C_MUTED);
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
        String[] names = {"основен", "резервен 1", "резервен 2", "резервен 3", "резервен 4"};
        for (int i = 0; i < SRC_SLOTS; i++) {
            ff[i] = srcField("https://.../movie/{tmdb}", prefs.getString("fsrc" + i, ""), true);
            addLabeled(col, "Филми: " + names[i], ff[i]);
        }
        for (int i = 0; i < SRC_SLOTS; i++) {
            tf[i] = srcField("https://.../tv/{tmdb}/{season}/{episode}",
                    prefs.getString("tsrc" + i, ""), true);
            addLabeled(col, "Сериали: " + names[i], tf[i]);
        }
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
        btns.addView(makeButton("Запази", false, save), l1);
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

    // ---- плочката в менюто ----

    // Нарисувана картинка (без файлове): прожектор, голям "play", филмова лента
    // и червена лента "В РАЗРАБОТКА" в ъгъла.
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
            // мек прожектор
            p.setColor(0x22FFFFFF);
            c.drawCircle(w * 0.80f, h * 0.95f, h * 1.10f, p);
            p.setColor(0x18FFFFFF);
            c.drawCircle(w * 0.80f, h * 0.95f, h * 0.74f, p);
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
            // голям "play" вдясно
            float cx = w * 0.80f;
            float cy = h * 0.42f;
            float r = h * 0.27f;
            p.setColor(0x33FFFFFF);
            c.drawCircle(cx, cy, r * 1.4f, p);
            p.setColor(0xF2FFFFFF);
            c.drawCircle(cx, cy, r, p);
            p.setColor(0xFFBE185D);
            path.reset();
            path.moveTo(cx - r * 0.28f, cy - r * 0.46f);
            path.lineTo(cx - r * 0.28f, cy + r * 0.46f);
            path.lineTo(cx + r * 0.50f, cy);
            path.close();
            c.drawPath(path, p);
            // червена лента в ъгъла: показва, че разделът още се доработва
            float aIn = h * 0.46f;
            float aOut = h * 0.98f;
            path.reset();
            path.moveTo(aIn, 0);
            path.lineTo(aOut, 0);
            path.lineTo(0, aOut);
            path.lineTo(0, aIn);
            path.close();
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
    }

    // Ред "Наши филми" в менюто: надпис вляво и широка плочка с картинка.
    private View makeCatalogLane() {
        LinearLayout lane = new LinearLayout(this);
        lane.setOrientation(LinearLayout.HORIZONTAL);
        lane.setClipChildren(false);

        LinearLayout lab = new LinearLayout(this);
        lab.setOrientation(LinearLayout.HORIZONTAL);
        lab.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ilp = lp(u(56), u(56));
        ilp.rightMargin = u(14);
        lab.addView(iconCircle(IC_SPARK, 0xFF2A2250, 0xFFA78BFA, 56), ilp);
        LinearLayout nt = new LinearLayout(this);
        nt.setOrientation(LinearLayout.VERTICAL);
        nt.addView(text("Наши филми", 27, Color.WHITE, tfCondBold, true));
        nt.addView(text("каталог", 15, C_MUTED, tfMedium, true));
        lab.addView(nt);
        lane.addView(lab, lp(u(220), u(112)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackground(gradBg(new int[]{0xFF7C3AED, 0xFFBE185D, 0xFF3B0F6B}, 22, 4));
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        alp.setMargins(u(4), u(4), u(4), u(4));
        CatalogArt art = new CatalogArt(this, tfCondBold);
        roundClip(art, u(18));
        tile.addView(art, alp);

        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text("Филми и сериали", 20, Color.WHITE, Typeface.DEFAULT_BOLD, true));
        tx.addView(text("търси и предложения", 14, 0xDDFFFFFF, tfMedium, true));
        FrameLayout.LayoutParams tlp2 = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        tlp2.rightMargin = u(18);
        tlp2.bottomMargin = u(30);
        tile.addView(tx, tlp2);

        tile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                catFocusKey = null;
                showCatalog(null);
            }
        });
        tile.setOnFocusChangeListener(tileFocus);
        catTile = tile;
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, u(112), 2f);
        tlp.leftMargin = u(16);
        row.addView(tile, tlp);
        row.addView(new View(this), new LinearLayout.LayoutParams(0, u(112), 4f));
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
