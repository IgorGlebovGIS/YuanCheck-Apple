package kz.chinacalc;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() { finish(); }

    private void send(final String json) {
        runOnUiThread(() -> web.evaluateJavascript("window.onRates(" + json + ")", null));
    }

    class Bridge {
        @JavascriptInterface
        public String version() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (Exception e) { return "1.6"; }
        }

        @JavascriptInterface
        public void refresh() {
            new Thread(() -> {
                try { send(load()); } catch (Exception e) { send("{\"ok\":false}"); }
            }).start();
        }
    }

    /** Официальные курсы НБ РК (RSS). Если на сегодня нет — берём предыдущие дни. */
    private String load() throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Almaty"));
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Almaty"));
        for (int i = 0; i < 5; i++) {
            String date = f.format(c.getTime());
            String xml = get("https://nationalbank.kz/rss/get_rates.cfm?fdate=" + date);
            double cny = rate(xml, "CNY"), usd = rate(xml, "USD"), eur = rate(xml, "EUR");
            if (cny > 0 && usd > 0)
                return "{\"ok\":true,\"cny\":" + cny + ",\"usd\":" + usd + ",\"eur\":" + eur + ",\"date\":\"" + date + "\"}";
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        return "{\"ok\":false}";
    }

    private String get(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(10000);
        h.setReadTimeout(10000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
        } finally { h.disconnect(); }
        return sb.toString();
    }

    private double rate(String xml, String code) {
        for (String item : xml.split("<item>")) {
            if (!code.equals(tag(item, "title"))) continue;
            try {
                double v = Double.parseDouble(tag(item, "description").replace(',', '.'));
                String q = tag(item, "quant");
                double quant = q.isEmpty() ? 1 : Double.parseDouble(q);
                return Math.round(v / quant * 100.0) / 100.0;
            } catch (Exception e) { return 0; }
        }
        return 0;
    }

    private String tag(String s, String t) {
        Matcher m = Pattern.compile("<" + t + ">\\s*(.*?)\\s*</" + t + ">", Pattern.DOTALL).matcher(s);
        return m.find() ? m.group(1).trim() : "";
    }
}
