package com.fongmi.android.tv.playback.vod;

import android.util.Log;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.net.OkHttp;
import com.google.common.net.HttpHeaders;

import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * 二次开发：线路测速。
 * 对每条线路取同一集的 m3u8，读分辨率、下载一个切片，得到「码率（≈画质）」和「下载速度（≈流畅度）」。
 * 只测剧集地址本身就是 http(s) m3u8 的线路（采集站基本都是），不调用站源接口，避免打断正在进行的播放解析。
 */
public final class LineProbe {

    /** 整体测速预算：超过这个时间还没测完的线路按「超时」处理 */
    public static final long BUDGET_MS = 6000;
    private static final String TAG = "LineProbe";
    private static final long PLAYLIST_TIMEOUT_MS = 2500;
    private static final long SEGMENT_MAX_BYTES = 3 * 1024 * 1024;
    private static final long SEGMENT_WINDOW_MS = 3000;
    private static final int MAX_TARGETS = 16;
    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANDWIDTH = Pattern.compile("[^-]BANDWIDTH=(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTINF = Pattern.compile("#EXTINF:\\s*([\\d.]+)");

    private LineProbe() {
    }

    /** 一条要测的线路：线路名 + 用来测的那一集地址 */
    public record Target(String flag, String url) {
    }

    /** 一次测速请求；token 用来丢弃过期结果（换源、退出后回来的旧结果） */
    public record Request(int token, String key, List<Target> targets) {
    }

    public record Report(int token, Map<String, Result> results) {
    }

    /**
     * 测速结果。
     * height：分辨率高度（0 = m3u8 里没写）；bitrateKbps：切片码率（0 = 算不出）；speedKbps：下载速度。
     */
    public record Result(String flag, boolean ok, int height, long bitrateKbps, long speedKbps) {

        static Result fail(String flag) {
            return new Result(flag, false, 0, 0, 0);
        }
    }

    public static List<Target> targets(List<Flag> flags, History history) {
        List<Target> items = new ArrayList<>();
        String remarks = history == null ? "" : history.getVodRemarks();
        for (Flag flag : flags) {
            Episode episode = flag.find(remarks, false);
            String url = episode == null ? "" : cleanUrl(episode.getUrl());
            if (isProbeable(url)) items.add(new Target(flag.getFlag(), url));
        }
        return items;
    }

    /**
     * 站源常在地址后面附加说明，如 `index.m3u8|||国语中字`、`index.m3u8$tpm3u8`，
     * 播放时由站源自己去掉；测速不经过站源，要自己截掉，否则全部 404。
     */
    static String cleanUrl(String url) {
        int end = url.length();
        int bars = url.indexOf("|||");
        int dollar = url.indexOf('$');
        if (bars >= 0) end = Math.min(end, bars);
        if (dollar >= 0) end = Math.min(end, dollar);
        return url.substring(0, end).trim();
    }

    static boolean isProbeable(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://")) && lower.contains(".m3u8");
    }

    /** 并发测所有线路，阻塞直到全部完成或超出预算。 */
    public static Map<String, Result> probeAll(List<Target> targets, Map<String, String> headers) {
        Map<String, Result> results = new HashMap<>();
        if (targets.isEmpty()) return results;
        headers = withUserAgent(headers);
        if (targets.size() > MAX_TARGETS) targets = targets.subList(0, MAX_TARGETS);
        long deadline = System.currentTimeMillis() + BUDGET_MS;
        OkHttpClient client = OkHttp.client(PLAYLIST_TIMEOUT_MS);
        // 线程数 = 线路数（最多 MAX_TARGETS），保证所有线路同时开测，不排队吃掉预算
        ExecutorService executor = Executors.newFixedThreadPool(targets.size());
        try {
            List<Future<Result>> futures = new ArrayList<>();
            Map<String, String> finalHeaders = headers;
            for (Target target : targets) futures.add(executor.submit(() -> probe(client, target, finalHeaders, deadline)));
            for (int i = 0; i < futures.size(); i++) {
                String flag = targets.get(i).flag();
                long wait = Math.max(0, deadline - System.currentTimeMillis()) + 500;
                try {
                    Result result = futures.get(i).get(wait, TimeUnit.MILLISECONDS);
                    if (result != null) results.put(flag, result);
                } catch (Exception e) {
                    // 没测完 ≠ 失败：不放进结果，排序时算「未知」，不打标签
                    Log.w(TAG, flag + " 测速未完成：" + e);
                }
            }
        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    /** 返回 null 表示没测完（超时），和「明确失败」（404、列表解析不出）区分开 */
    private static Result probe(OkHttpClient client, Target target, Map<String, String> headers, long deadline) {
        try {
            String url = target.url();
            String playlist = fetchText(client, url, headers, deadline);
            int height = 0;
            long bandwidth = 0;
            // 主播放列表（多码率）：选码率最高的那一档，记下分辨率
            if (playlist.contains("#EXT-X-STREAM-INF")) {
                Variant variant = bestVariant(playlist);
                if (variant == null) return Result.fail(target.flag());
                height = variant.height;
                bandwidth = variant.bandwidth;
                url = resolve(url, variant.uri);
                playlist = fetchText(client, url, headers, deadline);
            }
            Segment segment = pickSegment(playlist);
            if (segment == null) return Result.fail(target.flag());
            return measure(client, target.flag(), resolve(url, segment.uri), segment.duration, height, bandwidth, headers, deadline);
        } catch (InterruptedIOException e) {
            Log.w(TAG, target.flag() + " 测速超时：" + target.url());
            return null;
        } catch (Exception e) {
            Log.w(TAG, target.flag() + " 测速失败：" + target.url() + " → " + e);
            return Result.fail(target.flag());
        }
    }

    /** 和播放器保持一致：站源没给 UA 时用播放器的默认 UA（很多 CDN 会拦 okhttp 默认 UA） */
    private static Map<String, String> withUserAgent(Map<String, String> headers) {
        Map<String, String> items = new HashMap<>(headers);
        if (items.keySet().stream().noneMatch(HttpHeaders.USER_AGENT::equalsIgnoreCase)) {
            items.put(HttpHeaders.USER_AGENT, Setting.getUa().isEmpty() ? MediaItemFactory.getDefaultUserAgent() : Setting.getUa());
        }
        return items;
    }

    /**
     * 下载一个切片测速：最多下 3MB 或 3 秒（不超过整体预算），到点就按已下载的量算速度。
     * 这样慢线路也能得出「慢」的结论，而不是因为没下完被当成失败。
     */
    private static Result measure(OkHttpClient client, String flag, String url, double duration, int height, long bandwidth, Map<String, String> headers, long deadline) throws Exception {
        long start = System.currentTimeMillis();
        long stop = Math.min(deadline, start + SEGMENT_WINDOW_MS);
        if (stop - start < 500) throw new InterruptedIOException("预算已用完");
        Call call = client.newCall(request(url, headers));
        call.timeout().timeout(stop - start + 300, TimeUnit.MILLISECONDS);
        try (Response res = call.execute()) {
            if (!res.isSuccessful()) throw new IllegalStateException("切片 HTTP " + res.code() + " " + url);
            long length = res.body().contentLength();
            long bytes = 0;
            boolean complete = false;
            byte[] buffer = new byte[16 * 1024];
            try (InputStream is = res.body().byteStream()) {
                int read;
                while (true) {
                    read = is.read(buffer);
                    if (read == -1) {
                        complete = true;
                        break;
                    }
                    bytes += read;
                    if (bytes >= SEGMENT_MAX_BYTES || System.currentTimeMillis() >= stop) break;
                }
            } catch (InterruptedIOException e) {
                if (bytes == 0) throw e; // 一个字节都没下到，算没测完
            }
            long elapsed = Math.max(1, System.currentTimeMillis() - start);
            long speed = bytes * 8 / elapsed; // bit/ms == kbit/s
            // 切片大小：优先用响应头里的长度；没有的话只有完整下完才知道
            long segmentBytes = length > 0 ? length : (complete ? bytes : 0);
            long bitrate = duration > 0 && segmentBytes > 0 ? (long) (segmentBytes * 8 / duration / 1000) : bandwidth / 1000;
            return new Result(flag, bytes > 0, height, bitrate, speed);
        }
    }

    private static String fetchText(OkHttpClient client, String url, Map<String, String> headers, long deadline) throws Exception {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) throw new InterruptedIOException("预算已用完");
        Call call = client.newCall(request(url, headers));
        call.timeout().timeout(remaining, TimeUnit.MILLISECONDS);
        try (Response res = call.execute()) {
            if (!res.isSuccessful()) throw new IllegalStateException("HTTP " + res.code() + " " + url);
            return res.body().string();
        }
    }

    private static okhttp3.Request request(String url, Map<String, String> headers) {
        return new okhttp3.Request.Builder().url(url).headers(Headers.of(headers)).build();
    }

    private record Variant(String uri, int height, long bandwidth) {
    }

    private record Segment(String uri, double duration) {
    }

    static Variant bestVariant(String playlist) {
        Variant best = null;
        String[] lines = playlist.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue;
            String uri = nextUri(lines, i + 1);
            if (uri == null) continue;
            Matcher r = RESOLUTION.matcher(line);
            Matcher b = BANDWIDTH.matcher(" " + line);
            int height = r.find() ? Integer.parseInt(r.group(2)) : 0;
            long bandwidth = b.find() ? Long.parseLong(b.group(1)) : 0;
            Variant item = new Variant(uri, height, bandwidth);
            if (best == null || height > best.height || (height == best.height && bandwidth > best.bandwidth)) best = item;
        }
        return best;
    }

    /** 选一个时长 ≥3 秒的切片（开头的切片常常很短，测出来不准）；前 5 个都短就用第一个。 */
    static Segment pickSegment(String playlist) {
        Segment first = null;
        String[] lines = playlist.split("\\r?\\n");
        int checked = 0;
        for (int i = 0; i < lines.length && checked < 5; i++) {
            Matcher m = EXTINF.matcher(lines[i].trim());
            if (!m.find()) continue;
            String uri = nextUri(lines, i + 1);
            if (uri == null) continue;
            Segment segment = new Segment(uri, Double.parseDouble(m.group(1)));
            if (first == null) first = segment;
            if (segment.duration >= 3) return segment;
            checked++;
        }
        return first;
    }

    private static String nextUri(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            return line;
        }
        return null;
    }

    private static String resolve(String base, String uri) {
        HttpUrl url = HttpUrl.parse(base);
        HttpUrl resolved = url == null ? null : url.resolve(uri);
        return resolved == null ? uri : resolved.toString();
    }
}
