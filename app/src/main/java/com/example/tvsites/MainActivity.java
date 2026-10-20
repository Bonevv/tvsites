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
