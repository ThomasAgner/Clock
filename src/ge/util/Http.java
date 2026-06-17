package ge.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Thin HTTP GET helper with an on-disk response cache.
 *
 * <p>The RuneScape Wiki real-time API <em>requires</em> a descriptive
 * {@code User-Agent} that identifies the application, so every request carries
 * one. Responses are cached under {@code .ge-cache/} with a configurable TTL so
 * repeated scans are fast and polite to the upstream services.
 */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Identifies the tool to upstream APIs (RuneScape Wiki etiquette). */
    private static final String USER_AGENT =
            "GE-Analyzer/1.0 (Grand Exchange technical-analysis tool; contact: tombaum42@gmail.com)";

    private static final Path CACHE_DIR = Path.of(".ge-cache");

    private static boolean cacheEnabled = true;
    private static Duration cacheTtl = Duration.ofMinutes(30);

    private Http() {
    }

    public static void setCacheEnabled(boolean enabled) {
        cacheEnabled = enabled;
    }

    public static void setCacheTtl(Duration ttl) {
        cacheTtl = ttl;
    }

    /** Fetch a URL as a String, using the disk cache when a fresh copy exists. */
    public static String get(String url) throws IOException, InterruptedException {
        Path cacheFile = cacheEnabled ? cachePath(url) : null;
        if (cacheFile != null && isFresh(cacheFile)) {
            return Files.readString(cacheFile, StandardCharsets.UTF_8);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        String body = response.body();

        if (cacheFile != null) {
            try {
                Files.createDirectories(CACHE_DIR);
                Files.writeString(cacheFile, body, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // Caching is best-effort; a failure here must not break the fetch.
            }
        }
        return body;
    }

    private static boolean isFresh(Path file) {
        try {
            if (!Files.exists(file)) return false;
            long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
            return age <= cacheTtl.toMillis();
        } catch (IOException e) {
            return false;
        }
    }

    private static Path cachePath(String url) {
        return CACHE_DIR.resolve(sha1(url) + ".json");
    }

    private static String sha1(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
