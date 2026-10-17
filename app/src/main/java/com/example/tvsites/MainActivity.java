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

    // Сайтове по подразбиране: {етикет, адрес, режим, група}. Режим: "" | "mobile" | "desktop"
    private static final String[][] DEFAULT_SITES = {
            {"GledaiTV", "https://www.gledaitv.fan/", "", "Телевизия"},
            {"GledaiTV Live", "https://www.gledaitv.live/", "", "Телевизия"},
            {"IPTV-BG", "https://iptv-bg.com/", "", "Телевизия"},
            {"Seir Sanduk", "https://www.seirsanduk.online/", "", "Телевизия"},
            {"bTV Плюс", "https://btvplus.bg/live/", "", "Официални · телевизия"},
            {"FilmiZip", "https://filmizip.com/", "", "Филми"},
            {"Filmi9", "https://filmi9.com/", "", "Филми"},
            {"ZenoPlay", "https://zenoplay.to/movies/", "", "Филми"},
            {"Всички филми", "https://vsichkifilmi.net/", "", "Филми"},
            {"FilmiZT", "https://filmizt.com/", "", "Филми"},
            {"FilmiFen", "https://filmifen.com/", "", "Филми"},
            {"BGNow", "https://www.bgnow.tv/en/category/movie/", "", "Филми"},
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
            IC_CURSOR = 16, IC_BARS = 17, IC_SHIELD = 18, IC_DOWN = 19, IC_SLIDERS = 20,
            IC_STAR = 21, IC_GRID = 22, IC_DICE = 23, IC_PLUS = 24;

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
                if (tvCapturing && u != null && u.startsWith("http")) tvPageFinished();
            }

            // Страница с код 404 или 5xx (няма такъв филм там): минава на следващия източник.
            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r,
                                            WebResourceResponse resp) {
                if (r == null || resp == null || !r.isForMainFrame()) return;
                if (resp.getStatusCode() >= 400 && tvCapturing) {
                    tvCaptureError("код " + resp.getStatusCode());
                    return;
                }
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
                        recoverFromRendererLoss(crashed);
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
        // Скритото търсене на канал: след кратко изчакване се проверява новият поток.
        if (kind == 1 && tvCapturing) {
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
        r.setBackground(stateBg(0xFF1A2342, C_PANEL_FOCUS, Color.WHITE, u(18), 0));
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

        // Моята телевизия: стрелките сменят канала, OK отваря списъка.
        if (tvMode) {
            if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(e);
            return tvKey(e);
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
        if (scrollMode) {
            setScrollMode(false);
            return;
        }
        if (tvMode) {
            if (tvListShown()) tvHideListNow();
            else tvLeave(true);
            return;
        }
        if (tvPanelShown()) {
            tvHidePanel();
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
        String name = "", poster = "", backdrop = "", year = "", overview = "", imdb = "", genres = "";
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

    private static ExecutorService newNetPool() {
        ThreadPoolExecutor p = new ThreadPoolExecutor(3, 3, 20, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), lowPriority("tvs-net"));
        p.allowCoreThreadTimeOut(true);
        return p;
    }

    private static final int MP = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    // Цветове на каталога: тъмно синьо-зелено (като киносалон) и кехлибарено за избраното.
    private static final int K_BG = 0xFF08131A;
    private static final int K_SURF = 0xFF10222A;
    private static final int K_SURF2 = 0xFF183540;
    private static final int K_LINE = 0xFF274855;
    private static final int K_TEXT = 0xFFF6F1E7;
    private static final int K_TEXT2 = 0xFFB9C7CB;
    private static final int K_TEXT3 = 0xFF8EA3A9;
    private static final int K_AMBER = 0xFFFFB547;
    private static final int K_AMBER2 = 0xFFFFD27A;
    private static final int K_EMBER = 0xFFF2711C;
    private static final int K_INK = 0xFF1A1204;
    private static final int K_ERR = 0xFFFF9A8A;
    private static final int R_TITLE = 0x7f0b0002; // ключ за таг: заглавието на клетката

    private static final int HERO_H = 380; // височина на горната част на началото (единици)

    private Typeface tfSerif = Typeface.SERIF;

    private FrameLayout catPanel;
    private FrameLayout catHeroLayer; // горната част на началото: картина, надписи и бутони
    private ScrimView catFade;        // мек преход под нея
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
                    c = (HttpURLConnection) new URL("https://image.tmdb.org/t/p/" + size + path)
                            .openConnection();
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
                if (b == null) return;
                cache.put(key, b);
                final Bitmap fb = b;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != catGen || isFinishing()) return;
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
        private int tint = 0xFF183540;
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
            c.drawColor(0xFF0B1D25);
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
            int[][] pal = PALS[1];
            int[] col = pal[Math.abs(id) % pal.length];
            this.c0 = col[0];
            this.c1 = col[1];
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
            if (b == null) return;
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
            float s = Math.max((float) getWidth() / bmp.getWidth(),
                    (float) getHeight() / bmp.getHeight());
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
                c.drawRoundRect(rf, radius, radius, img);
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
                fill.setColor(0xA008131A);
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
                fill.setColor(0xBA060E12);
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
                if (isFocused()) {
                    float sw = u(3);
                    fill.setStyle(Paint.Style.STROKE);
                    fill.setStrokeWidth(sw);
                    fill.setColor(K_AMBER);
                    rf.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
                    c.drawRoundRect(rf, radius - sw / 2f, radius - sw / 2f, fill);
                    fill.setStyle(Paint.Style.FILL);
                } else {
                    fill.setColor(0x4008131A);
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
            fill.setColor(f ? 0xFF1C4250 : K_SURF);
            c.drawPath(shape, fill);
            line.setPathEffect(null);
            line.setStrokeWidth(f ? 3f * U : (preferred ? 2f * U : 1.5f * U));
            line.setColor(f || preferred ? K_AMBER : 0xFF2C5160);
            c.drawPath(shape, line);
            line.setStrokeWidth(2f * U);
            line.setColor(preferred ? 0xB3FFB547 : 0xB335606F);
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
            this.cols = genreCols(gid);
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
                fill.setColor(K_AMBER);
                rf.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
                c.drawRoundRect(rf, radius - sw / 2f, radius - sw / 2f, fill);
                fill.setStyle(Paint.Style.FILL);
            } else {
                fill.setColor(0x2608131A);
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
                v.setOutlineAmbientShadowColor(has ? 0x66FFB547 : Color.BLACK);
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
        GradientDrawable n = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFCB6B, 0xFFF29B2C});
        n.setCornerRadius(radiusPx);
        GradientDrawable f = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFD98A, 0xFFF5A53C});
        f.setCornerRadius(radiusPx);
        f.setStroke(u(3), Color.WHITE);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, f);
        sl.addState(new int[]{}, n);
        return sl;
    }

    // Бутон-хапче: икона и надпис; на фокус е с кехлибарена рамка.
    private LinearLayout kPill(int icon, String label, final Runnable action) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(u(icon >= 0 ? 20 : 24), 0, u(24), 0);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(stateBg(0x14FFFFFF, K_SURF2, K_AMBER, u(26), 0x29FFFFFF));
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
        fo.setStroke(u(3), K_AMBER);
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
        h.setPadding(u(64), u(40), u(64), 0);
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

    private BackdropView heroBd;
    private LinearLayout heroInfo;
    private LinearLayout heroFacts;
    private TextView heroTitle;
    private TextView heroOv;
    private FrameLayout heroCta;
    private CTitle heroWant;
    private boolean heroActive;

    private void buildCatalogUi() {
        tfSerif = Typeface.create("serif", Typeface.BOLD);
        catPanel = new FrameLayout(this);
        catPanel.setBackgroundColor(K_BG);
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
        buildHero();
        catFade = new ScrimView(this, true, new int[]{K_BG, 0x0008131A}, new float[]{0f, 1f});
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(MP, u(30));
        flp.topMargin = u(HERO_H);
        catPanel.addView(catFade, flp);
        catPanel.setVisibility(View.GONE);
        root.addView(catPanel, new FrameLayout.LayoutParams(MP, MP));
    }

    // Горната част на началото: голяма картина, име на каталога, бутони и данни за избраното.
    private void buildHero() {
        catHeroLayer = new FrameLayout(this);
        heroBd = new BackdropView(this);
        catHeroLayer.addView(heroBd, new FrameLayout.LayoutParams(u(1200), MP,
                Gravity.TOP | Gravity.END));
        catHeroLayer.addView(new ScrimView(this, false,
                new int[]{0xFF08131A, 0xFF08131A, 0x9E08131A, 0x0008131A},
                new float[]{0f, 0.33f, 0.66f, 1f}), new FrameLayout.LayoutParams(MP, MP));
        catHeroLayer.addView(new ScrimView(this, true,
                new int[]{0x0008131A, 0x0008131A, 0xEB08131A, 0xFF08131A},
                new float[]{0f, 0.50f, 0.88f, 1f}), new FrameLayout.LayoutParams(MP, MP));
        catHeroLayer.addView(new ScrimView(this, true,
                new int[]{0xBF060E12, 0x00060E12, 0x00060E12},
                new float[]{0f, 0.28f, 1f}), new FrameLayout.LayoutParams(MP, MP));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setPadding(u(64), 0, u(64), 0);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mlp = lp(u(40), u(40));
        mlp.rightMargin = u(14);
        brand.addView(brandMark(40), mlp);
        brand.addView(serifText("Наши филми", 32, K_TEXT, true));
        bar.addView(brand, new LinearLayout.LayoutParams(0, WC, 1f));
        String[] labels = {"Търсене", "Категории", "Предложи ми", "Източници"};
        int[] icons = {IC_SEARCH, IC_GRID, IC_DICE, IC_SLIDERS};
        Runnable[] acts = {
                new Runnable() {
                    @Override
                    public void run() {
                        showSearchDialog();
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        catGenres(false);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        catGenres(true);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        showSourcesDialog();
                    }
                }};
        for (int i = 0; i < labels.length; i++) {
            LinearLayout.LayoutParams l = lp(WC, u(52));
            l.leftMargin = u(12);
            bar.addView(kPill(icons[i], labels[i], acts[i]), l);
        }
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(MP, u(60), Gravity.TOP);
        blp.topMargin = u(24);
        catHeroLayer.addView(bar, blp);

        heroInfo = new LinearLayout(this);
        heroInfo.setOrientation(LinearLayout.VERTICAL);
        heroInfo.setClipChildren(false);
        heroFacts = new LinearLayout(this);
        heroFacts.setOrientation(LinearLayout.HORIZONTAL);
        heroFacts.setGravity(Gravity.CENTER_VERTICAL);
        heroInfo.addView(heroFacts, lp(WC, u(30)));
        heroTitle = serifText("", 50, K_TEXT, true);
        heroInfo.addView(heroTitle, lpTop(u(10)));
        heroOv = text("", 19, K_TEXT2, tfMedium, false);
        heroOv.setMaxLines(3);
        heroOv.setEllipsize(TextUtils.TruncateAt.END);
        heroOv.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(u(760), WC);
        olp.topMargin = u(8);
        heroInfo.addView(heroOv, olp);
        heroCta = new FrameLayout(this);
        heroCta.setClipChildren(false);
        heroCta.setVisibility(View.GONE);
        heroInfo.addView(heroCta, lpTop(u(18)));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(u(900), WC,
                Gravity.BOTTOM | Gravity.START);
        ilp.leftMargin = u(64);
        ilp.bottomMargin = u(24);
        catHeroLayer.addView(heroInfo, ilp);
        catPanel.addView(catHeroLayer, new FrameLayout.LayoutParams(MP, u(HERO_H)));
    }

    // Начало (true): горната картина отгоре и редовете под нея. Другите екрани са на цяла височина.
    private void catMode(boolean home) {
        FrameLayout.LayoutParams l = (FrameLayout.LayoutParams) catScroll.getLayoutParams();
        int top = home ? u(HERO_H) : 0;
        if (l.topMargin != top) {
            l.topMargin = top;
            catScroll.setLayoutParams(l);
        }
        catHeroLayer.setVisibility(home ? View.VISIBLE : View.GONE);
        catFade.setVisibility(home ? View.VISIBLE : View.GONE);
        heroActive = home;
        catBox.setPadding(0, home ? u(30) : 0, 0, u(70));
        ui.removeCallbacks(focusTick);
        focusAct = null;
        focusTitle = null;
    }

    // Показва данните за заглавието в горната картина.
    private void heroShow(final CTitle t) {
        if (!heroActive) return;
        heroWant = t;
        heroInfo.setVisibility(View.VISIBLE);
        heroCta.setVisibility(View.GONE);
        heroCta.removeAllViews();
        heroTitle.setText(t.name);
        ts(heroTitle, t.name.length() > 34 ? 38 : (t.name.length() > 24 ? 44 : 50));
        heroOv.setText(t.overview);
        fillFacts(heroFacts, t);
        heroBd.setTint(PALS[1][Math.abs(t.id) % PALS[1].length][0]);
        if (t.backdrop.isEmpty()) {
            heroBd.setImage(null, true);
        } else {
            loadImage(backdropSize(), t.backdrop, catGen, true, new ImageSink() {
                @Override
                public void set(Bitmap b, boolean instant) {
                    if (heroWant == t && heroActive) heroBd.setImage(b, !instant);
                }
            });
        }
    }

    // Показва, докато няма ключ за TMDB: какво да се направи.
    private void heroKeyHelp() {
        heroWant = null;
        heroInfo.setVisibility(View.VISIBLE);
        heroFacts.removeAllViews();
        heroTitle.setText("Добави ключ за TMDB");
        ts(heroTitle, 44);
        heroOv.setText("Безплатният ключ дава корици, оценки и описания. Вземи го от themoviedb.org "
                + "(Профил, Настройки, API) и го въведи от бутона Източници.");
        heroCta.removeAllViews();
        LinearLayout b = kPrimary(IC_SLIDERS, "Въведи ключ", new Runnable() {
            @Override
            public void run() {
                showSourcesDialog();
            }
        });
        heroCta.addView(b, new FrameLayout.LayoutParams(WC, u(72)));
        heroCta.setVisibility(View.VISIBLE);
        catMain = b;
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
        ui.removeCallbacks(focusTick);
        focusAct = null;
        focusTitle = null;
        heroActive = false;
        heroWant = null;
        if (catPanel != null) {
            catPanel.setVisibility(View.GONE);
            catBox.removeAllViews();
            heroBd.setImage(null, false);
        }
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
            h.setPadding(u(64), 0, u(64), u(2));
            block.addView(h);
        }
        HorizontalScrollView hs = new RowScroll(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setClipChildren(false);
        strip.setPadding(u(64), u(14), u(64), u(20)); // място за уголемяването на фокуса
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

    private int catRowsLeft;

    // Ред от началния екран: като се стигне до края, се дозареждат още (до 5 страници по 20).
    private void addCatRow(String title, final String path, final boolean tv, final int gen,
                           final boolean first) {
        final LinearLayout strip = addRowShell(title);
        fillSkeleton(strip, 8, 168, 252, 16);
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
                                strip.addView(errorBlock(err != null ? err : "Няма резултати.",
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                if (gen != catGen) return;
                                                strip.removeAllViews();
                                                fillSkeleton(strip, 8, 168, 252, 16);
                                                page[0] = 0;
                                                count[0] = 0;
                                                done[0] = false;
                                                more[0].run();
                                            }
                                        }));
                            }
                        } else {
                            if (p >= 5) done[0] = true; // най-много 100 заглавия на ред: пази паметта
                            int base = count[0];
                            for (int i = 0; i < list.size(); i++) {
                                final CTitle t = list.get(i);
                                ArtView cell = posterCard(t, rowSize(), new Runnable() {
                                    @Override
                                    public void run() {
                                        catDetail(t);
                                    }
                                });
                                strip.addView(cell, posterLp(168, 252, 16));
                                watchEnd(cell, base + i, count, 6, more[0]);
                            }
                            count[0] += list.size();
                            if (p == 1) {
                                if (first && heroWant == null) heroShow(list.get(0));
                                if (catFocusKey != null) {
                                    View want = findByTag(strip, catFocusKey);
                                    if (want != null) {
                                        want.requestFocus();
                                        catFocusKey = null;
                                    }
                                } else if (first && catPanel.findFocus() == null) {
                                    View f0 = strip.getChildAt(0);
                                    if (f0 != null) f0.requestFocus();
                                }
                            }
                        }
                        if (p == 1 && --catRowsLeft <= 0 && catPanel.findFocus() == null) {
                            catFocusKey = null;
                            View f1 = firstFocusable();
                            if (f1 != null) f1.requestFocus();
                        }
                    }
                });
            }
        };
        more[0].run();
    }

    // Начало: горна картина и редове с корици (или примерни карти, докато няма ключ).
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
        catMode(true);
        heroWant = null;
        heroInfo.setVisibility(View.INVISIBLE);
        heroBd.setImage(null, false);
        focusAct = new Runnable() {
            @Override
            public void run() {
                if (focusTitle != null) heroShow(focusTitle);
            }
        };
        if (tmdbKey().isEmpty()) {
            heroKeyHelp();
            LinearLayout strip = addRowShell("Пример (отворени филми на Blender)");
            String[] names = {"Big Buck Bunny", "Sintel", "Tears of Steel", "Elephants Dream",
                    "Cosmos Laundromat", "Spring", "Agent 327"};
            String[] years = {"2008", "2010", "2012", "2006", "2015", "2019", "2017"};
            for (int i = 0; i < names.length; i++) {
                CTitle t = new CTitle();
                t.id = -(i + 1);
                t.name = names[i];
                t.year = years[i];
                strip.addView(posterCard(t, rowSize(), new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                "Примерна карта. Въведи ключ за TMDB от бутона Източници.",
                                Toast.LENGTH_LONG).show();
                    }
                }), posterLp(168, 252, 16));
            }
            if (catMain != null) catMain.requestFocus();
        } else {
            catRowsLeft = 4;
            addCatRow("Популярни филми", "/movie/popular", false, gen, true);
            addCatRow("Най-добре оценени филми", "/movie/top_rated", false, gen, false);
            addCatRow("Популярни сериали", "/tv/popular", true, gen, false);
            addCatRow("Най-добре оценени сериали", "/tv/top_rated", true, gen, false);
        }
        if (catFocusKey == null) catScroll.scrollTo(0, 0);
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
        bd.setTint(PALS[1][Math.abs(t.id) % PALS[1].length][0]);
        page.addView(bd, new FrameLayout.LayoutParams(u(1320), u(660), Gravity.TOP | Gravity.END));
        page.addView(new ScrimView(this, false,
                new int[]{0xFF08131A, 0xFF08131A, 0xC708131A, 0x4008131A, 0x1A08131A},
                new float[]{0f, 0.26f, 0.52f, 0.82f, 1f}), new FrameLayout.LayoutParams(MP, u(660)));
        page.addView(new ScrimView(this, true,
                new int[]{0x0008131A, 0x0008131A, 0xE608131A, 0xFF08131A},
                new float[]{0f, 0.38f, 0.82f, 1f}), new FrameLayout.LayoutParams(MP, u(660)));
        page.addView(new ScrimView(this, true,
                new int[]{0x99060E12, 0x00060E12, 0x00060E12},
                new float[]{0f, 0.22f, 1f}), new FrameLayout.LayoutParams(MP, u(660)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setPadding(u(64), u(88), u(64), u(56));
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
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(u(900), WC);
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
        row.setPadding(u(padLeftUnits), u(8), u(64), u(10));
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
            line.setPadding(u(64), 0, u(64), 0);
            TextView lab = text("Източник", 18, K_TEXT2, tfMedium, true);
            line.addView(lab, lp(u(104), WC));
            line.addView(tickScroll(ticketRow(t, 196, 64, 12, false)),
                    new LinearLayout.LayoutParams(0, WC, 1f));
            catBox.addView(line, lpTop(u(2)));
        }
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setPadding(u(64), u(14), u(64), 0);
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
        grid.setPadding(u(64), u(10), u(64), 0);
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
        sgInfo.setPadding(u(64), u(6), u(64), 0);
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
        skel.setPadding(u(64), u(24), u(64), 0);
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
                                hold.setPadding(u(64), u(20), u(64), 0);
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
                new int[]{0xFF14313C, 0xFF0A1A21});
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
                new int[]{0xFF14313C, 0xFF0A1A21});
        pbg.setCornerRadius(dp(24));
        pbg.setStroke(dp(1), 0x44FFFFFF);
        panel.setBackground(pbg);

        TextView title = new TextView(this);
        title.setText("Източници и ключ");
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

    private static final int T_BG0 = 0xFF0B1330;
    private static final int T_BG1 = 0xFF050912;
    private static final int T_SURF = 0xFF101A36;
    private static final int T_SURF2 = 0xFF1A2B55;
    private static final int T_LINE = 0xFF2A4170;
    private static final int T_TEXT = 0xFFEEF4FF;
    private static final int T_TEXT2 = 0xFFAEBEDD;
    private static final int T_TEXT3 = 0xFF7788AE;
    private static final int T_SKY = 0xFF38BDF8;
    private static final int T_SKY2 = 0xFF8FDBFF;
    private static final int T_INK = 0xFF04121F;
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
        int fails;   // колко пъти поред запазеният поток не е тръгнал
        // Резервни потоци на същия сайт: {адрес, Referer, Origin, User-Agent, неуспехи поред}.
        final ArrayList<String[]> alts = new ArrayList<>();
    }

    private static final int TV_MAX_ALTS = 3;

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
            glow1 = new RadialGradient(w * 0.88f, -h * 0.06f, r * 0.56f, 0x3338BDF8, 0x0038BDF8,
                    Shader.TileMode.CLAMP);
            glow2 = new RadialGradient(0, h * 1.05f, r * 0.44f, 0x2E4F46E5, 0x004F46E5,
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
        GradientDrawable n = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{T_SKY2, T_SKY});
        n.setCornerRadius(radiusPx);
        GradientDrawable f = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{T_SKY2, T_SKY});
        f.setCornerRadius(radiusPx);
        f.setStroke(u(3), Color.WHITE);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, f);
        sl.addState(new int[]{}, n);
        return sl;
    }

    private final View.OnFocusChangeListener tvFocus = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean has) {
            float k = v.getTag(R_SCALE) instanceof Float ? (Float) v.getTag(R_SCALE) : 1.04f;
            v.animate().scaleX(has ? k : 1f).scaleY(has ? k : 1f).setDuration(130).start();
            v.setTranslationZ(has ? 14 * U : 0f);
            if (Build.VERSION.SDK_INT >= 28) {
                v.setOutlineSpotShadowColor(has ? T_SKY : Color.BLACK);
                v.setOutlineAmbientShadowColor(has ? 0x6638BDF8 : Color.BLACK);
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
        b.setBackground(stateBg(0x12FFFFFF, T_SURF2, T_SKY, u(26), 0x26FFFFFF));
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

    // Голямата сянка с номера на канала зад картата.
    private static class GhostNum extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String s = "";

        GhostNum(Context c, Typeface tf, String s) {
            super(c);
            this.s = s;
            p.setTypeface(tf);
            p.setColor(0x14FFFFFF);
            p.setTextAlign(Paint.Align.RIGHT);
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            p.setTextSize(h * 1.9f);
            c.drawText(s, getWidth() + h * 0.10f, h * 1.12f, p);
        }
    }

    // Точки: по една за всеки източник на канала.
    private static class DotsView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int n, dot, gap;

        DotsView(Context c, int n, int dot, int gap, int color) {
            super(c);
            this.n = Math.min(Math.max(n, 1), 6);
            this.dot = dot;
            this.gap = gap;
            p.setColor(color);
        }

        @Override
        protected void onMeasure(int wms, int hms) {
            setMeasuredDimension(n * dot + (n - 1) * gap, View.MeasureSpec.getSize(hms));
        }

        @Override
        protected void onDraw(Canvas c) {
            float cy = getHeight() / 2f;
            for (int i = 0; i < n; i++) {
                c.drawCircle(dot / 2f + i * (dot + gap), cy, dot / 2f, p);
            }
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

    // ---- екранът със списъка на каналите ----

    private FrameLayout tvPanel;
    private LinearLayout tvHead;
    private ScrollView tvScroll;
    private LinearLayout tvBox;
    private TextView tvHint;
    private View tvTile;
    private View tvMain;

    private boolean tvPanelShown() {
        return tvPanel != null && tvPanel.getVisibility() == View.VISIBLE;
    }

    private void tvBuildPanel() {
        tvPanel = new FrameLayout(this);
        tvPanel.addView(new TvBgView(this), new FrameLayout.LayoutParams(MP, MP));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        tvHead = new LinearLayout(this);
        tvHead.setOrientation(LinearLayout.HORIZONTAL);
        tvHead.setGravity(Gravity.CENTER_VERTICAL);
        tvHead.setClipChildren(false);
        col.addView(tvHead, new LinearLayout.LayoutParams(MP, u(110)));
        tvScroll = new CatScroll(this);
        tvScroll.setFillViewport(true);
        tvScroll.setVerticalScrollBarEnabled(false);
        tvScroll.setClipChildren(false);
        tvScroll.setClipToPadding(false);
        tvBox = new LinearLayout(this);
        tvBox.setOrientation(LinearLayout.VERTICAL);
        tvBox.setClipChildren(false);
        tvBox.setPadding(u(64), u(6), u(64), u(20));
        tvScroll.addView(tvBox, new ViewGroup.LayoutParams(MP, WC));
        col.addView(tvScroll, new LinearLayout.LayoutParams(MP, 0, 1f));
        tvHint = text("", 16, T_TEXT3, tfMedium, true);
        tvHint.setGravity(Gravity.CENTER);
        col.addView(tvHint, new LinearLayout.LayoutParams(MP, u(54)));
        tvPanel.addView(col, new FrameLayout.LayoutParams(MP, MP));
        tvPanel.setVisibility(View.GONE);
        root.addView(tvPanel, new FrameLayout.LayoutParams(MP, MP));
    }

    private void tvShowPanel() {
        if (isFinishing()) return;
        tvLoad();
        if (tvPanel == null) tvBuildPanel();
        // Докато екранът е отгоре, менюто отдолу не взима фокус.
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        tvPanel.setVisibility(View.VISIBLE);
        logEvent("Отворена Моята телевизия");
        tvFill(null);
        updateState();
    }

    // Скрива екрана, без да връща фокуса в менюто (когато се отваря друг екран).
    private void tvHidePanelQuiet() {
        if (tvPanel != null) {
            tvPanel.setVisibility(View.GONE);
            tvBox.removeAllViews();
            tvHead.removeAllViews();
        }
        tvMain = null;
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // Броят на каналите в плочката на менюто следва промените.
        if (tvTileSub != null) tvTileSub.setText(tvTileSubText());
    }

    private void tvHidePanel() {
        tvHidePanelQuiet();
        View f = tvTile != null ? tvTile : (heroView != null ? heroView : firstTile);
        if (f != null) f.requestFocus();
        updateState();
    }

    private void tvFillHead(String sub, boolean addBtn) {
        tvHead.removeAllViews();
        tvHead.setPadding(u(64), u(26), u(64), 0);
        FrameLayout mark = new FrameLayout(this);
        GradientDrawable mg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6FD3FF, 0xFF4F46E5});
        mg.setCornerRadius(u(16));
        mark.setBackground(mg);
        mark.addView(new IconView(this, IC_TV, T_INK),
                new FrameLayout.LayoutParams(u(30), u(30), Gravity.CENTER));
        LinearLayout.LayoutParams mlp = lp(u(56), u(56));
        mlp.rightMargin = u(16);
        tvHead.addView(mark, mlp);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text("Моята телевизия", 46, T_TEXT, tfCondBold, true));
        TextView st = text(sub, 18, T_TEXT2, tfMedium, true);
        st.setPadding(0, u(2), 0, 0);
        tx.addView(st);
        tvHead.addView(tx, new LinearLayout.LayoutParams(0, WC, 1f));
        if (addBtn) {
            tvHead.addView(tvPill(IC_PLUS, "Добави от сайт", new Runnable() {
                @Override
                public void run() {
                    tvAddFromSite();
                }
            }), lp(WC, u(52)));
        }
    }

    // Пълни екрана: празно състояние или списък. focusId е каналът, който да остане на фокус.
    private void tvFill(final String focusId) {
        tvHead.removeAllViews();
        tvBox.removeAllViews();
        tvMain = null;
        if (tvChans.isEmpty()) {
            tvFillHead("Още няма канали", false);
            tvFillEmpty();
            tvHint.setText("");
        } else {
            tvFillHead(plural(tvChans.size(), "канал", "канала") + "  ·  "
                    + plural(tvSourceTotal(), "източник", "източника"), true);
            tvFillList();
            tvHint.setText("Задръж OK върху канал, за да го промениш");
        }
        tvPanel.post(new Runnable() {
            @Override
            public void run() {
                if (!tvPanelShown()) return;
                View f = focusId == null ? null : findByTag(tvBox, "ch:" + focusId);
                if (f == null) f = tvMain;
                if (f != null) f.requestFocus();
                if (focusId == null) tvScroll.scrollTo(0, 0);
            }
        });
    }

    private String tvSrcNames(TvChan c) {
        StringBuilder sb = new StringBuilder();
        for (TvSrc s : c.srcs) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(s.site);
        }
        return sb.toString();
    }

    // Голямата карта "Последно гледан".
    private View tvFeatured(final TvChan c, int num) {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1E5AA8, 0xFF143A7A, 0xFF0B1D45});
        bg.setCornerRadius(u(30));
        bg.setStroke(Math.max(1, u(1)), 0x408FDBFF);
        card.setBackground(bg);
        roundClip(card, u(30));
        card.addView(new GhostNum(this, tfCondBold, String.valueOf(num)),
                new FrameLayout.LayoutParams(MP, MP));

        LinearLayout lab = new LinearLayout(this);
        lab.setOrientation(LinearLayout.HORIZONTAL);
        lab.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dlp = lp(u(18), u(18));
        dlp.rightMargin = u(10);
        lab.addView(tvLiveDot(18), dlp);
        lab.addView(text("Последно гледан", 17, T_TEXT2, tfMedium, true));
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(WC, WC,
                Gravity.TOP | Gravity.START);
        llp.leftMargin = u(40);
        llp.topMargin = u(24);
        card.addView(lab, llp);

        TextView nm = text(c.name, c.name.length() > 18 ? 56 : 72, T_TEXT, tfCondBold, true);
        nm.setIncludeFontPadding(false);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(MP, WC,
                Gravity.TOP | Gravity.START);
        nlp.leftMargin = u(40);
        nlp.rightMargin = u(280);
        nlp.topMargin = u(54);
        card.addView(nm, nlp);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        int shown = 0;
        for (TvSrc s : c.srcs) {
            if (shown++ >= 4) break;
            TextView chip = text(s.site, 16, T_TEXT, tfMedium, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(u(14), 0, u(14), 0);
            GradientDrawable cg = new GradientDrawable();
            cg.setColor(0x1FFFFFFF);
            cg.setCornerRadius(u(16));
            cg.setStroke(Math.max(1, u(1)), 0x2EFFFFFF);
            chip.setBackground(cg);
            LinearLayout.LayoutParams clp = lp(WC, u(32));
            if (shown > 1) clp.leftMargin = u(10);
            chips.addView(chip, clp);
        }
        FrameLayout.LayoutParams chlp = new FrameLayout.LayoutParams(WC, WC,
                Gravity.BOTTOM | Gravity.START);
        chlp.leftMargin = u(40);
        chlp.bottomMargin = u(22);
        card.addView(chips, chlp);

        LinearLayout go = tvPrimary(IC_PLAY, "Гледай", 72, 24, new Runnable() {
            @Override
            public void run() {
                tvTuneUser(c, -1);
            }
        });
        tvMain = go;
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(WC, u(72),
                Gravity.END | Gravity.CENTER_VERTICAL);
        glp.rightMargin = u(48);
        card.addView(go, glp);
        return card;
    }

    // Канал в мрежата: цветна черта, голям номер, име и сайтове.
    private View tvCell(final TvChan c, int num) {
        FrameLayout cell = new FrameLayout(this);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setLongClickable(true);
        cell.setTag("ch:" + c.id);
        cell.setBackground(stateBg(T_SURF, T_SURF2, T_SKY, u(20), T_LINE));

        int col = tvColor(c);
        View bar = new View(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{col, (col & 0x00FFFFFF) | 0x55000000});
        float r = u(18);
        bg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        bar.setBackground(bg);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(u(7), MP);
        blp.setMargins(u(2), u(2), 0, u(2));
        cell.addView(bar, blp);

        TextView no = text(String.valueOf(num), 46, T_SKY2, tfCondBold, true);
        no.setIncludeFontPadding(false);
        no.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(u(74), MP);
        nlp.leftMargin = u(24);
        cell.addView(no, nlp);

        View sep = new View(this);
        sep.setBackgroundColor(T_LINE);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Math.max(1, u(1.5f)), MP);
        slp.leftMargin = u(104);
        slp.topMargin = u(20);
        slp.bottomMargin = u(20);
        cell.addView(sep, slp);

        TextView nm = text(c.name, 32, T_TEXT, tfCondBold, true);
        nm.setIncludeFontPadding(false);
        FrameLayout.LayoutParams nmlp = new FrameLayout.LayoutParams(MP, WC);
        nmlp.leftMargin = u(124);
        nmlp.rightMargin = u(96);
        nmlp.topMargin = u(14);
        cell.addView(nm, nmlp);

        TextView sr = text(tvSrcNames(c), 15, T_TEXT3, tfMedium, true);
        FrameLayout.LayoutParams srlp = new FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM);
        srlp.leftMargin = u(124);
        srlp.rightMargin = u(96);
        srlp.bottomMargin = u(12);
        cell.addView(sr, srlp);

        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(WC, MP,
                Gravity.END | Gravity.CENTER_VERTICAL);
        dlp.rightMargin = u(22);
        cell.addView(new DotsView(this, c.srcs.size(), u(9), u(6), T_SKY), dlp);

        cell.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvTuneUser(c, -1);
            }
        });
        cell.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                tvChannelMenu(c);
                return true;
            }
        });
        cell.setTag(R_SCALE, 1.03f);
        cell.setOnFocusChangeListener(tvFocus);
        return cell;
    }

    private void tvFillList() {
        TvChan last = tvLastChan();
        tvBox.addView(tvFeatured(last, tvChans.indexOf(last) + 1),
                new LinearLayout.LayoutParams(MP, u(192)));

        LinearLayout sec = new LinearLayout(this);
        sec.setOrientation(LinearLayout.HORIZONTAL);
        sec.addView(text("Всички канали", 30, T_TEXT, tfCondBold, true));
        TextView cnt = text(String.valueOf(tvChans.size()), 18, T_TEXT3, tfMedium, true);
        cnt.setPadding(u(14), u(12), 0, 0);
        sec.addView(cnt);
        tvBox.addView(sec, lpTop(u(26)));

        int n = tvChans.size();
        for (int i = 0; i < n; i += 3) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            for (int k = 0; k < 3; k++) {
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, u(86), 1f);
                if (k > 0) clp.leftMargin = u(20);
                if (i + k < n) {
                    row.addView(tvCell(tvChans.get(i + k), i + k + 1), clp);
                } else {
                    row.addView(new View(this), clp);
                }
            }
            tvBox.addView(row, lpTop(u(i == 0 ? 16 : 14)));
        }
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
        b.addView(new IconView(this, IC_TV, 0xFF7DD3FC), ilp);
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
        LinearLayout card = tvCard("Добави от сайт",
                "Отвори сайта, пусни канал, задръж OK и в „Видео на страницата“ избери „В ТВ“.");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        View first = null;
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
        card.addView(sv, lpTopW(MP, Math.min(u(340), u(72) * Math.max(1, tvSites().size())), u(24)));
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

    // ▲▼: следващ или предишен поток на същия сайт.
    private void tvSwitchStream(int dir) {
        TvChan c = tvCur;
        int m = tvStreamCount();
        if (c == null || m < 2) {
            tvShowBanner(2500, "Няма друг поток на този сайт");
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
        tvTune(c, firstSrc);
    }

    // Пуска канала. firstSrc е източникът, с който да започне (-1 = основният).
    private void tvTune(TvChan c, int firstSrc) {
        if (c == null || c.srcs.isEmpty() || isFinishing()) return;
        ensureLayers();
        tvBuildOsd();
        if (!tvMode && player != null) closeNative(false); // чужд плеър (не е на телевизията)
        ui.removeCallbacks(tvZapGo);
        tvZapIdx = -1;
        tvStopAttempt(false);
        tvStepGen++;
        tvMode = true;
        tvCur = c;
        tvPlaying = false;
        int n = c.srcs.size();
        tvFirstSrc = firstSrc >= 0 && firstSrc < n ? firstSrc : 0;
        // План: за всеки източник първо запазеният поток, после търсене през страницата му.
        tvPlan.clear();
        ArrayList<Integer> order = new ArrayList<>();
        order.add(tvFirstSrc);
        for (int i = 0; i < n; i++) {
            if (i != tvFirstSrc) order.add(i);
        }
        for (int i : order) {
            TvSrc s = c.srcs.get(i);
            boolean cap = tvCapturable(s);
            // Поток, който няколко пъти поред не е тръгнал, вероятно е изтекъл: веднага към търсене.
            if (!s.stream.isEmpty() && (s.fails < 3 || !cap)) tvPlan.add(new int[]{i, TV_DIRECT, -1});
            // Резервните потоци на същата страница се пробват, преди да се търси или смени сайтът.
            if (!s.stream.isEmpty()) {
                for (int k = 0; k < s.alts.size(); k++) tvPlan.add(new int[]{i, TV_DIRECT, k});
            }
            if (cap) tvPlan.add(new int[]{i, TV_CAPTURE});
        }
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
        if (tvPanel != null) {
            // Докато се гледа, екранът със списъка не държи памет.
            tvPanel.setVisibility(View.GONE);
            tvBox.removeAllViews();
            tvHead.removeAllViews();
        }
        menuHolder.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        nativeLayer.setVisibility(View.VISIBLE);
        tvHideListNow();
        tvHideBannerNow();
        tvWaitShow(c);
        updateState();
        logEvent("ТВ: пускам " + c.name, true);
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
            tvWaitText(s.stream.isEmpty() ? "Търся канала в " + s.site + "…"
                    : "Запазеният поток не тръгна. Търся нов в " + s.site + "…");
            tvStartCapture(s, st[0]);
        }
    }

    private HashMap<String, String> tvHeadersOf(TvSrc s) {
        HashMap<String, String> p = new HashMap<>();
        if (!s.ref.isEmpty()) p.put("Referer", s.ref);
        if (!s.org.isEmpty()) p.put("Origin", s.org);
        try {
            String ck = CookieManager.getInstance().getCookie(s.stream);
            if (ck != null && !ck.isEmpty()) p.put("Cookie", ck);
        } catch (Exception ignored) {
        }
        return p;
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
        final HashMap<String, String> fprops = props;
        final String fua = ua;
        try {
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(12000)
                    .setReadTimeoutMs(12000)
                    .setUserAgent(ua);
            http.setDefaultRequestProperties(props);
            if (player == null) {
                // По-малък буфер: по-малко памет, а за телевизия няколко секунди стигат.
                DefaultLoadControl load = new DefaultLoadControl.Builder()
                        .setBufferDurationsMs(15000, 30000, 1500, 3000)
                        .build();
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
                        tvTryFailed("потокът свърши");
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
            ui.postDelayed(tvTryTimeout, 15000);
        } catch (Exception e) {
            tvTryFailed("грешка " + e.getClass().getSimpleName());
        }
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
                tvCur.srcs.get(st[0]).fails++;
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
            tvShowBanner(1800, null);
            return;
        }
        if (tvCur == null || si < 0 || si >= tvCur.srcs.size()) return;
        if (viaCapture && player != null) {
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
        if (player != null) player.setVolume(1f);
        TvSrc s = tvCur.srcs.get(si);
        long now = System.currentTimeMillis();
        boolean fresh = false;
        if (!viaCapture && !tvPinned && !s.stream.equals(url)) {
            // Тръгна резервен поток: става основен, старият основен отива в резервните.
            for (int k = 0; k < s.alts.size(); k++) {
                String[] al = s.alts.get(k);
                if (al[0].equals(url)) {
                    String[] oldMain = {s.stream, s.ref, s.org, s.ua, String.valueOf(s.fails)};
                    s.alts.set(k, oldMain);
                    s.stream = al[0];
                    s.ref = al[1];
                    s.org = al[2];
                    s.ua = al[3];
                    break;
                }
            }
        }
        if (viaCapture) {
            // Новият поток се запомня: следващия път тръгва направо.
            fresh = !s.stream.isEmpty() && !s.stream.equals(url);
            for (int k = s.alts.size() - 1; k >= 0; k--) {
                if (s.alts.get(k)[0].equals(url)) s.alts.remove(k);
            }
            // Старият основен поток отива в резервните, освен ако е счупен (3 пъти поред).
            if (fresh && s.fails < 3) {
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
            tvCapturing = false;
            tvDropWeb();
        }
        s.ok = now;
        s.fails = 0;
        tvCur.last = now;
        tvSave();
        prefs.edit().putString("tvlast", tvCur.id).apply();
        tvPlayedAt = SystemClock.uptimeMillis();
        logEvent("ТВ: тръгна " + tvCur.name + " от " + s.site + (viaCapture ? " (нов поток)" : ""), true);
        tvWaitHide();
        tvShowBanner(5000, fresh ? "Запазеният поток не тръгна. Намерих нов и го записах." : null);
        updateState();
    }

    private final Runnable tvStallRun = new Runnable() {
        @Override
        public void run() {
            if (!tvMode || !tvPlaying) return;
            tvDropped("потокът заседна");
        }
    };

    private void tvBuffering() {
        tvShowBanner(0, "Зареждане…");
        ui.removeCallbacks(tvStallRun);
        ui.postDelayed(tvStallRun, 25000);
    }

    // Връзката се скъса, докато гледаме: пробва се наново; ако не помага - друг източник.
    private void tvDropped(String why) {
        long now = SystemClock.uptimeMillis();
        if (now - tvPlayedAt > 120000) {
            tvRestarts = 0;
            tvSwitches = 0;
        }
        tvRestarts++;
        TvChan c = tvCur;
        if (c == null) return;
        int cur = tvSrcNowIdx();
        logEvent("ТВ: връзката се скъса (" + why + "), опит " + tvRestarts);
        if (tvRestarts <= 3) {
            tvTune(c, cur);
            return;
        }
        tvRestarts = 0;
        if (c.srcs.size() > 1 && ++tvSwitches <= 2) tvTune(c, (cur + 1) % c.srcs.size());
        else tvGiveUp();
    }

    // Нищо не тръгна: чак сега се отваря самият сайт на основния източник.
    private void tvGiveUp() {
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
        boolean was = tvMode;
        tvMode = false;
        ui.removeCallbacks(tvZapGo);
        ui.removeCallbacks(tvBannerHideRun);
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
        web.setVisibility(View.VISIBLE);
        web.loadUrl(url);
        // Ако страницата не съобщи, че е готова (реклами, които висят), "буткането" започва пак.
        ui.removeCallbacks(tvPokeFallback);
        ui.postDelayed(tvPokeFallback, 8000);
        tvArmStep(25000);
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
            if (!tvMode || !tvCapturing || web == null || tvPlaying) return;
            int n = tvPokeN++;
            if (!tvTrying) {
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
                if (!tvBad.contains(u)) return true;
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
                if (tvBad.contains(u)) continue;
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
        try {
            String ck = CookieManager.getInstance().getCookie(cand);
            if (ck != null && !ck.isEmpty()) props.put("Cookie", ck);
        } catch (Exception ignored) {
        }
        tvWaitText("Намерих поток, пускам…");
        tvTryUrl(cand, props, ua, tvCapSrc, true);
    }

    // ---- екраните върху видеото: чакане, табела с канала, списък ----

    private FrameLayout tvWait;
    private TextView tvWaitNo, tvWaitName, tvWaitMsg;
    private LinearLayout tvWaitChips;
    private LinearLayout tvBanner, tvKeys;
    private View tvChipZap, tvChipSrc, tvChipStream;
    private TextView tvBnNo, tvBnName, tvBnSub;
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
        g.setColor(0xB3050A18);
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

        // Табела с канала долу вляво.
        tvBanner = new LinearLayout(this);
        tvBanner.setOrientation(LinearLayout.HORIZONTAL);
        tvBanner.setGravity(Gravity.CENTER_VERTICAL);
        tvBanner.setPadding(u(30), 0, u(34), 0);
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(0xD6050A18);
        bb.setCornerRadius(u(26));
        bb.setStroke(Math.max(1, u(1)), 0x24FFFFFF);
        tvBanner.setBackground(bb);
        tvBnNo = text("", 76, T_SKY2, tfCondBold, true);
        tvBnNo.setIncludeFontPadding(false);
        tvBnNo.setGravity(Gravity.CENTER);
        tvBnNo.setMinWidth(u(86));
        tvBanner.addView(tvBnNo, lp(WC, WC));
        View sep = new View(this);
        sep.setBackgroundColor(T_LINE);
        LinearLayout.LayoutParams sl = lp(Math.max(1, u(1.5f)), u(68));
        sl.leftMargin = u(24);
        sl.rightMargin = u(24);
        tvBanner.addView(sep, sl);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tvBnName = text("", 48, T_TEXT, tfCondBold, true);
        tvBnName.setIncludeFontPadding(false);
        tx.addView(tvBnName);
        tvBnSub = text("", 19, T_TEXT2, tfMedium, true);
        tvBnSub.setPadding(0, u(8), 0, 0);
        tx.addView(tvBnSub);
        tvBanner.addView(tx, new LinearLayout.LayoutParams(0, WC, 1f));
        LinearLayout live = new LinearLayout(this);
        live.setOrientation(LinearLayout.HORIZONTAL);
        live.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dl = lp(u(18), u(18));
        dl.rightMargin = u(10);
        live.addView(tvLiveDot(18), dl);
        live.addView(text("На живо", 18, T_TEXT, Typeface.DEFAULT_BOLD, true));
        LinearLayout.LayoutParams ll = lp(WC, WC);
        ll.leftMargin = u(20);
        tvBanner.addView(live, ll);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(u(900), u(124),
                Gravity.BOTTOM | Gravity.START);
        bl.leftMargin = u(56);
        bl.bottomMargin = u(56);
        tvBanner.setVisibility(View.GONE);
        nativeLayer.addView(tvBanner, bl);

        // Подсказка за бутоните над табелата.
        tvKeys = new LinearLayout(this);
        tvKeys.setOrientation(LinearLayout.HORIZONTAL);
        String[][] ks = {{"▲▼", "канал"}, {"OK", "списък"}, {"◀", "друг поток"},
                {"▶", "друг сайт"}, {"Назад", "към каналите"}};
        for (int i = 0; i < ks.length; i++) {
            LinearLayout.LayoutParams kl = lp(WC, u(38));
            kl.rightMargin = u(10);
            View chip = tvKeyChip(ks[i][0], ks[i][1]);
            if (i == 0) tvChipZap = chip;
            if (i == 2) tvChipStream = chip;
            if (i == 3) tvChipSrc = chip;
            tvKeys.addView(chip, kl);
        }
        FrameLayout.LayoutParams kl2 = new FrameLayout.LayoutParams(WC, WC,
                Gravity.BOTTOM | Gravity.START);
        kl2.leftMargin = u(56);
        kl2.bottomMargin = u(200);
        tvKeys.setVisibility(View.GONE);
        nativeLayer.addView(tvKeys, kl2);

        // Списък на каналите вляво.
        tvListBox = new FrameLayout(this);
        GradientDrawable lg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF7040812, 0xEE040812, 0x00040812});
        tvListBox.setBackground(lg);
        LinearLayout lc = new LinearLayout(this);
        lc.setOrientation(LinearLayout.VERTICAL);
        lc.setPadding(u(48), u(40), 0, u(30));
        lc.addView(text("Канали", 40, T_TEXT, tfCondBold, true));
        TextView ls = text("OK — превключи     Назад — затвори", 17, T_TEXT3, tfMedium, true);
        ls.setPadding(0, u(4), 0, u(18));
        lc.addView(ls);
        tvListScroll = new CatScroll(this);
        tvListScroll.setVerticalScrollBarEnabled(false);
        tvListScroll.setClipChildren(false);
        tvListScroll.setClipToPadding(false);
        tvListCol = new LinearLayout(this);
        tvListCol.setOrientation(LinearLayout.VERTICAL);
        tvListCol.setClipChildren(false);
        tvListScroll.addView(tvListCol, new ViewGroup.LayoutParams(u(460), WC));
        lc.addView(tvListScroll, new LinearLayout.LayoutParams(u(472), 0, 1f));
        tvListBox.addView(lc, new FrameLayout.LayoutParams(MP, MP));
        tvListBox.setVisibility(View.GONE);
        nativeLayer.addView(tvListBox, new FrameLayout.LayoutParams(u(560), MP,
                Gravity.START));
    }

    private void tvFadeIn(View v) {
        if (v == null) return;
        v.animate().cancel();
        if (v.getVisibility() != View.VISIBLE) {
            v.setAlpha(0f);
            v.setVisibility(View.VISIBLE);
        }
        v.animate().alpha(1f).setDuration(180).start();
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
        ui.removeCallbacks(tvBannerHideRun);
        tvHideNow(tvBanner);
        tvHideNow(tvKeys);
    }

    private void tvHideListNow() {
        tvHideNow(tvListBox);
    }

    private boolean tvListShown() {
        return tvListBox != null && tvListBox.getVisibility() == View.VISIBLE;
    }

    // Екранът за чакане: показва се докато каналът не тръгне.
    private void tvWaitShow(TvChan c) {
        tvWait.animate().cancel();
        tvWait.setAlpha(1f);
        tvWait.setVisibility(View.VISIBLE);
        tvWaitNo.setText("Канал " + (tvChans.indexOf(c) + 1));
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
        if (tvBanner == null || tvCur == null) return;
        tvFillBanner(tvCur, status != null ? status : tvLine());
        // Подсказки само за това, което има смисъл: един канал не се сменя, един източник също.
        tvChipZap.setVisibility(tvChans.size() > 1 ? View.VISIBLE : View.GONE);
        tvChipStream.setVisibility(tvStreamCount() > 1 ? View.VISIBLE : View.GONE);
        tvChipSrc.setVisibility(tvCur.srcs.size() > 1 ? View.VISIBLE : View.GONE);
        tvFadeIn(tvBanner);
        tvFadeIn(tvKeys);
        ui.removeCallbacks(tvBannerHideRun);
        if (hideMs > 0) ui.postDelayed(tvBannerHideRun, hideMs);
    }

    private void tvFillBanner(TvChan c, String line) {
        tvBnNo.setText(String.valueOf(tvChans.indexOf(c) + 1));
        tvBnName.setText(c.name);
        ts(tvBnName, c.name.length() > 22 ? 36 : 48);
        tvBnSub.setText(line);
    }

    private void tvHideBanner() {
        ui.removeCallbacks(tvBannerHideRun);
        tvFadeOut(tvBanner);
        tvFadeOut(tvKeys);
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
        tvFillBanner(t, t.srcs.get(0).site + "  ·  източник 1 от " + t.srcs.size());
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
        boolean play = !player.getPlayWhenReady();
        player.setPlayWhenReady(play);
        tvShowBanner(2500, play ? "▶  Пуснато" : "❚❚  Пауза");
    }

    // Списъкът на каналите върху видеото.
    private void tvShowList() {
        if (tvChans.isEmpty() || tvListBox == null) return;
        tvHideBannerNow();
        ui.removeCallbacks(tvZapGo);
        tvZapIdx = -1;
        tvListCol.removeAllViews();
        int cur = tvChans.indexOf(tvCur);
        View focus = null;
        for (int i = 0; i < tvChans.size(); i++) {
            final TvChan c = tvChans.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(u(24), 0, u(20), 0);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackground(stateBg(0x0DFFFFFF, T_SURF2, T_SKY, u(18), 0x14FFFFFF));
            row.addView(text(String.valueOf(i + 1), 34, T_SKY2, tfCondBold, true),
                    lp(u(62), WC));
            row.addView(text(c.name, 29, T_TEXT, tfCondBold, true),
                    new LinearLayout.LayoutParams(0, WC, 1f));
            if (i == cur) {
                LinearLayout now = new LinearLayout(this);
                now.setOrientation(LinearLayout.HORIZONTAL);
                now.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams dl = lp(u(16), u(16));
                dl.rightMargin = u(8);
                now.addView(tvLiveDot(16), dl);
                now.addView(text("гледаш", 15, T_TEXT2, Typeface.DEFAULT_BOLD, true));
                row.addView(now, lp(WC, WC));
                focus = row;
            }
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tvHideListNow();
                    if (c != tvCur) tvTuneUser(c, -1);
                }
            });
            tvListCol.addView(row, lpTopW(MP, u(66), i == 0 ? 0 : u(10)));
        }
        LinearLayout qrow = new LinearLayout(this);
        qrow.setOrientation(LinearLayout.HORIZONTAL);
        qrow.setGravity(Gravity.CENTER_VERTICAL);
        qrow.setPadding(u(24), 0, u(20), 0);
        qrow.setFocusable(true);
        qrow.setClickable(true);
        qrow.setBackground(stateBg(0x0DFFFFFF, T_SURF2, T_SKY, u(18), 0x14FFFFFF));
        qrow.addView(text("Качество", 24, T_TEXT2, tfCondBold, true),
                new LinearLayout.LayoutParams(0, WC, 1f));
        qrow.addView(text(qualityName(videoQuality()), 18, T_SKY2, Typeface.DEFAULT_BOLD, true));
        qrow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvQualityDialog();
            }
        });
        tvListCol.addView(qrow, lpTopW(MP, u(58), u(18)));
        if (focus == null) focus = tvListCol.getChildAt(0);
        tvListBox.setAlpha(1f);
        tvListBox.setVisibility(View.VISIBLE);
        final View f = focus;
        tvListBox.post(new Runnable() {
            @Override
            public void run() {
                if (f != null && tvListShown()) f.requestFocus();
            }
        });
    }

    // Клавиши, докато се гледа канал (списъкът върху видеото сам си взима стрелките и OK).
    private boolean tvKey(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        // Задържане на OK: избор на качество (първото натискане е отворило списъка с канали).
        if (down && e.getRepeatCount() == 10 && (code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            tvHideListNow();
            tvQualityDialog();
            return true;
        }
        if (tvListShown()) return super.dispatchKeyEvent(e);
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
                        tvShowList();
                    }
                }
                break;
        }
        return true;
    }

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
        lab.addView(iconCircle(IC_GRID, 0xFF16284F, 0xFF7DD3FC, 56), ilp);
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
