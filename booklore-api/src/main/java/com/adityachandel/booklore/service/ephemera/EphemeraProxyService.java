package com.adityachandel.booklore.service.ephemera;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.util.RequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class EphemeraProxyService {

    private static final Set<String> FORWARDED_REQUEST_HEADERS = Set.of(
            HttpHeaders.ACCEPT.toLowerCase(Locale.ROOT),
            HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.USER_AGENT.toLowerCase(Locale.ROOT),
            HttpHeaders.COOKIE.toLowerCase(Locale.ROOT),
            "x-requested-with"
    );

    private static final Set<String> RESPONSE_HEADER_WHITELIST = Set.of(
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_DISPOSITION.toLowerCase(Locale.ROOT),
            HttpHeaders.CACHE_CONTROL.toLowerCase(Locale.ROOT),
            HttpHeaders.PRAGMA.toLowerCase(Locale.ROOT),
            HttpHeaders.EXPIRES.toLowerCase(Locale.ROOT)
    );

    private final EphemeraProperties properties;
    private final HttpClient httpClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public EphemeraProxyService(EphemeraProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    public ResponseEntity<byte[]> forward(BookLoreUser user) {
        HttpServletRequest request = RequestUtils.getCurrentRequest();
        byte[] body = readBody(request);
        validateRequest(request);

        URI targetUri = buildTargetUri(request);
        HttpRequest outboundRequest = buildOutboundRequest(request, body, user, targetUri);

        try {
            HttpResponse<byte[]> response = httpClient.send(outboundRequest, HttpResponse.BodyHandlers.ofByteArray());
            HttpHeaders headers = extractResponseHeaders(response);
            byte[] responseBody = response.body();

            // Rewrite URLs in responses to work correctly when ephemera is served via proxy
            String contentType = response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("");
            if (responseBody != null && responseBody.length > 0) {
                if (contentType.contains("text/html")) {
                    responseBody = injectBaseTag(responseBody);
                } else if (contentType.contains("javascript")) {
                    responseBody = rewriteJavaScript(responseBody);
                }
            }

            return ResponseEntity.status(response.statusCode()).headers(headers).body(responseBody);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error("Ephemera proxy interrupted", ie);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Ephemera proxy interrupted", ie);
        } catch (IOException e) {
            log.error("Failed to proxy Ephemera request", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach Ephemera service", e);
        }
    }

    /**
     * Rewrites JavaScript to use relative paths for API calls when served via proxy
     */
    private byte[] rewriteJavaScript(byte[] jsBytes) {
        String js = new String(jsBytes, StandardCharsets.UTF_8);
        String original = js;
        int replacements = 0;

        // Pattern 1: baseUrl: "/api" (with or without spaces) - MOST COMMON
        String pattern1Before = "baseUrl\\s*:\\s*\"/api\"";
        js = js.replaceAll(pattern1Before, "baseUrl:\"./api\"");
        if (!js.equals(original)) {
            replacements++;
            log.info("Matched pattern: baseUrl: \"/api\"");
        }

        // Pattern 2: baseUrl: '/api' (single quotes)
        js = js.replaceAll("baseUrl\\s*:\\s*'/api'", "baseUrl:'./api'");

        // Pattern 3: {baseUrl:"/api"} (object literal, minified)
        String beforeObj = js;
        js = js.replaceAll("\\{baseUrl:\"/api\"", "{baseUrl:\"./api\"");
        if (!js.equals(beforeObj)) {
            replacements++;
            log.info("Matched pattern: {{baseUrl:\"/api\"");
        }

        // Pattern 4: "baseUrl":"/api" (JSON-style with quotes on key)
        js = js.replaceAll("\"baseUrl\"\\s*:\\s*\"/api\"", "\"baseUrl\":\"./api\"");

        // Pattern 5: Catch variations with , or } after
        js = js.replaceAll("baseUrl:\"/api\",", "baseUrl:\"./api\",");
        js = js.replaceAll("baseUrl:\"/api\"}", "baseUrl:\"./api\"}");
        js = js.replaceAll("baseUrl:'/api',", "baseUrl:'./api',");
        js = js.replaceAll("baseUrl:'/api'}", "baseUrl:'./api'}");

        // CRITICAL: Also rewrite the usage of baseUrl in the client
        // The apiFetch function concatenates baseUrl with the path
        // Pattern: clientConfig.baseUrl + path or baseUrl + path
        js = js.replaceAll("clientConfig\\.baseUrl\\}\\$\\{", "clientConfig.baseUrl}${");

        // Rewrite EventSource and WebSocket paths to be relative
        String beforeESE = js;
        js = js.replaceAll("new EventSource\\(\\s*\"/api/", "new EventSource(\"./api/");
        if (!js.equals(beforeESE)) {
            replacements++;
            log.info("Matched pattern: new EventSource(\"/api/");
        }
        js = js.replaceAll("new EventSource\\(\\s*'/api/", "new EventSource('./api/");
        js = js.replaceAll("new WebSocket\\(\\s*\"/api/", "new WebSocket(\"./api/");
        js = js.replaceAll("new WebSocket\\(\\s*'/api/", "new WebSocket('./api/");

        // Rewrite fetch calls with absolute /api/ paths
        js = js.replaceAll("fetch\\(\\s*\"/api/", "fetch(\"./api/");
        js = js.replaceAll("fetch\\(\\s*'/api/", "fetch('./api/");

        // Rewrite URL construction patterns
        js = js.replaceAll("\\+\\s*\"/api/", "+\"./api/");
        js = js.replaceAll("\\+\\s*'/api/", "+'./api/");
        js = js.replaceAll("`/api/", "`./api/");

        // AGGRESSIVE: Rewrite ALL standalone "/api" strings (not followed by /)
        // This catches the baseUrl: "/api" configuration regardless of pattern
        js = js.replaceAll(":\"/api\"", ":\"./api\"");
        js = js.replaceAll(":'/api'", ":'./api'");

        boolean modified = !js.equals(original);
        if (modified) {
            log.warn("Rewrote JavaScript API paths to relative paths ({} specific patterns matched)", replacements);
            // Log a snippet to verify rewriting
            if (js.contains("baseUrl:\"./api\"") || js.contains("baseUrl:'./api'")) {
                log.warn("SUCCESS: Verified baseUrl was rewritten to use relative path");
            } else {
                log.error("WARNING: JavaScript was modified but baseUrl pattern not found in output!");
            }
        } else {
            log.warn("WARNING: No JavaScript patterns matched for rewriting - this file may not be the bundle we're looking for");
        }

        return js.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Injects a base tag and rewrites absolute paths to work correctly when ephemera is served via proxy
     */
    private byte[] injectBaseTag(byte[] htmlBytes) {
        String html = new String(htmlBytes, StandardCharsets.UTF_8);

        // Rewrite absolute paths to relative paths so base tag works correctly
        // Paths starting with / are absolute from domain root and ignore base tag
        html = html.replaceAll("(src|href)=\"/([^/])", "$1=\"./$2");

        // Rewrite API calls to use relative paths that go through the proxy
        // This handles cases where Ephemera makes direct API calls to absolute URLs
        html = html.replaceAll("\"/api/", "\"./api/");
        html = html.replaceAll("'/api/", "'./api/");

        // Also rewrite any JavaScript fetch or XMLHttpRequest calls
        html = html.replaceAll("fetch\\(\"/api/", "fetch(\"./api/");
        html = html.replaceAll("fetch\\(\\s*'/api/", "fetch('./api/");
        html = html.replaceAll("\\.get\\(\"/api/", ".get(\"./api/");
        html = html.replaceAll("\\.post\\(\"/api/", ".post(\"./api/");
        html = html.replaceAll("\\.put\\(\"/api/", ".put(\"./api/");
        html = html.replaceAll("\\.delete\\(\"/api/", ".delete(\"./api/");
        html = html.replaceAll("\\.patch\\(\"/api/", ".patch(\"./api/");
        html = html.replaceAll("\\.head\\(\"/api/", ".head(\"./api/");
        html = html.replaceAll("\\.options\\(\"/api/", ".options(\"./api/");

        // Rewrite XMLHttpRequest open calls
        html = html.replaceAll("\\.open\\(\\s*[\"'](GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)[\"']\\s*,\\s*[\"']/api/", ".open(\"$1\", \"./api/");

        // Rewrite any absolute URLs that might be constructed in JavaScript
        html = html.replaceAll("window\\.location\\.origin\\s*\\+\\s*[\"']/api/", "\"./api/");
        html = html.replaceAll("location\\.origin\\s*\\+\\s*[\"']/api/", "\"./api/");

        // Rewrite any hardcoded absolute URLs to library.saulutions.ca
        html = html.replaceAll("https?://library\\.saulutions\\.ca/api/", "./api/");

        // Only inject base tag if it doesn't already exist
        if (html.toLowerCase(Locale.ROOT).contains("<base ")) {
            log.debug("Base tag already exists, skipping injection");
            return html.getBytes(StandardCharsets.UTF_8);
        }

        // Find the <head> tag (case-insensitive)
        String lowerHtml = html.toLowerCase(Locale.ROOT);
        int headIndex = lowerHtml.indexOf("<head");

        if (headIndex == -1) {
            log.warn("No <head> tag found in HTML, cannot inject base tag");
            return html.getBytes(StandardCharsets.UTF_8);
        }

        // Find the closing > of the head tag
        int closeIndex = html.indexOf(">", headIndex);
        if (closeIndex == -1) {
            log.warn("Malformed <head> tag, cannot inject base tag");
            return html.getBytes(StandardCharsets.UTF_8);
        }

        // Inject base tag immediately after <head>
        String baseTag = "<base href=\"/api/v1/ephemera/\">";
        String modifiedHtml = html.substring(0, closeIndex + 1) + baseTag + html.substring(closeIndex + 1);

        log.debug("Successfully injected base tag and rewrote asset paths");
        return modifiedHtml.getBytes(StandardCharsets.UTF_8);
    }

    private void validateRequest(HttpServletRequest request) {
        String method = request.getMethod();
        List<String> allowedMethods = properties.getAllowedMethods();
        if (allowedMethods.stream().noneMatch(m -> m.equalsIgnoreCase(method))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HTTP method not allowed for Ephemera");
        }

        String relativePath = resolveRelativePath(request);
        boolean allowedPath = properties.getAllowedPaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, relativePath));

        if (!allowedPath) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ephemera path is not whitelisted");
        }
    }

    private HttpRequest buildOutboundRequest(HttpServletRequest request, byte[] body, BookLoreUser user, URI targetUri) {
        HttpRequest.BodyPublisher publisher = createBodyPublisher(request.getMethod(), body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .method(request.getMethod(), publisher);

        Collections.list(request.getHeaderNames()).forEach(header -> {
            if (shouldForwardHeader(header)) {
                Collections.list(request.getHeaders(header)).forEach(value -> builder.header(header, value));
            }
        });

        if (properties.isInjectUserHeaders() && user != null) {
            builder.header("X-Booklore-User", user.getUsername());
            if (user.getEmail() != null) {
                builder.header("X-Booklore-User-Email", user.getEmail());
            }
        }

        return builder.build();
    }

    private HttpHeaders extractResponseHeaders(HttpResponse<byte[]> response) {
        HttpHeaders headers = new HttpHeaders();
        response.headers().map().forEach((name, values) -> {
            if (name != null && RESPONSE_HEADER_WHITELIST.contains(name.toLowerCase(Locale.ROOT))) {
                headers.put(name, values);
            }
        });
        return headers;
    }

    private HttpRequest.BodyPublisher createBodyPublisher(String method, byte[] body) {
        if (body == null || body.length == 0 || "GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(body);
    }

    private boolean shouldForwardHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        String lower = headerName.toLowerCase(Locale.ROOT);
        return FORWARDED_REQUEST_HEADERS.contains(lower);
    }

    private URI buildTargetUri(HttpServletRequest request) {
        try {
            String baseUrl = properties.getEffectiveBaseUrl();
            String relativePath = resolveRelativePath(request);
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
            uriBuilder.append(relativePath.startsWith("/") ? relativePath : "/" + relativePath);
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                uriBuilder.append("?").append(request.getQueryString());
            }
            return new URI(uriBuilder.toString());
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Ephemera target URI", e);
        }
    }

    private String resolveRelativePath(HttpServletRequest request) {
        String path = request.getRequestURI().replaceFirst("^/api/v1/ephemera", "");
        if (path.isEmpty()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private byte[] readBody(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            return bytes.length == 0 ? null : bytes;
        } catch (IOException e) {
            log.warn("Unable to read Ephemera request body, sending empty body", e);
            return null;
        }
    }
}

