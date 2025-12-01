package com.adityachandel.booklore.filter;

import com.adityachandel.booklore.service.ephemera.EphemeraProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

/**
 * Filter that intercepts API calls from Ephemera that bypass the JavaScript rewriting
 * and redirects them to the Ephemera backend.
 * <p>
 * This filter checks if a request to /api/* (excluding /api/v1/ephemera/*) comes from
 * the Ephemera iframe (by checking the Referer header), and if so, proxies it to the
 * Ephemera backend instead of letting it hit Booklore's API.
 */
@Slf4j
@Component
@Order(1)  // Run before security filters
public class EphemeraApiInterceptorFilter extends OncePerRequestFilter {

    /**
     * List of all Booklore API path prefixes that should NEVER be intercepted.
     * This is an explicit whitelist of all known Booklore endpoints.
     */
    private static final Set<String> BOOKLORE_API_PATHS = Set.of(
            "/api/v1/auth",              // Authentication
            "/api/v1/users",             // User management
            "/api/v1/books",             // Books and metadata
            "/api/v1/authors",           // Authors
            "/api/v1/libraries",         // Libraries
            "/api/v1/shelves",           // Shelves
            "/api/v1/reviews",           // Book reviews
            "/api/v1/book-notes",        // Book notes
            "/api/v1/settings",          // App settings
            "/api/v1/public-settings",   // Public settings
            "/api/v1/ephemera-settings", // Ephemera configuration (admin)
            "/api/v1/user-ephemera-settings", // User-specific ephemera settings
            "/api/v1/tasks",             // Background tasks
            "/api/v1/background",        // Background uploads
            "/api/v1/bookdrop",          // Bookdrop file management
            "/api/v1/files",             // File operations
            "/api/v1/path",              // Path utilities
            "/api/v1/media",             // Book media (covers, etc)
            "/api/v1/pdf",               // PDF reader
            "/api/v1/cbx",               // CBX reader
            "/api/v1/opds",              // OPDS catalog
            "/api/v1/koreader-users",    // KOReader users
            "/api/v1/kobo-settings",     // Kobo settings
            "/api/v1/version",           // Version info
            "/api/v1/setup",             // Initial setup
            "/api/v1/ephemera",          // Ephemera proxy (handled separately)
            "/api/v2/opds-users",        // OPDS users v2
            "/api/v2/email",             // Email v2
            "/api/metadata/tasks",       // Metadata tasks
            "/api/magic-shelves",        // Magic shelves
            "/api/kobo/",                // Kobo integration
            "/api/koreader"              // KOReader integration
    );

    private static final Set<String> FORWARDED_HEADERS = Set.of(
            HttpHeaders.ACCEPT.toLowerCase(Locale.ROOT),
            HttpHeaders.ACCEPT_LANGUAGE.toLowerCase(Locale.ROOT),
            HttpHeaders.CONTENT_TYPE.toLowerCase(Locale.ROOT),
            HttpHeaders.USER_AGENT.toLowerCase(Locale.ROOT),
            HttpHeaders.COOKIE.toLowerCase(Locale.ROOT),
            "x-requested-with"
    );

    private final EphemeraProperties properties;
    private final HttpClient httpClient;

    public EphemeraApiInterceptorFilter(EphemeraProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String referer = request.getHeader(HttpHeaders.REFERER);

        // Check if this is an API request that should be proxied to Ephemera
        if (shouldProxyToEphemera(requestUri, referer)) {
            log.info("Intercepting Ephemera API call from iframe: {} -> Ephemera backend", requestUri);
            proxyToEphemera(request, response);
            return;
        }

        // Continue with normal filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Determines if a request should be proxied to Ephemera based on the URI and referer
     */
    private boolean shouldProxyToEphemera(String requestUri, String referer) {
        // Only intercept requests that start with /api/
        if (!requestUri.startsWith("/api/")) {
            return false;
        }

        // CRITICAL: Check if this is a known Booklore API endpoint using explicit whitelist
        // This prevents us from accidentally intercepting Booklore's own APIs
        for (String bookloreApiPath : BOOKLORE_API_PATHS) {
            if (requestUri.startsWith(bookloreApiPath)) {
                log.trace("Skipping Booklore API endpoint: {}", requestUri);
                return false;
            }
        }

        // Don't intercept if referer is null (external requests)
        if (referer == null || referer.isEmpty()) {
            log.trace("Skipping request with no referer: {}", requestUri);
            return false;
        }

        // If we get here, it's an /api/* request that's NOT in the Booklore whitelist
        // and HAS a referer (meaning it's from our domain)
        // This means it's likely an ephemera API call

        // Additional check: Verify referer is from our domain to prevent external abuse
        // The referer should be from library.saulutions.ca or contain /api/v1/ephemera/
        boolean isFromOurDomain = referer.contains("library.saulutions.ca") ||
                                   referer.contains("://localhost") ||
                                   referer.startsWith("http://10.") ||
                                   referer.startsWith("http://localhost");

        if (!isFromOurDomain) {
            log.warn("Rejecting API request with external referer: {} from {}", requestUri, referer);
            return false;
        }

        // At this point, it's an unknown /api/* endpoint from our domain
        // This is almost certainly an ephemera API call
        log.info("Intercepting unknown API call (likely ephemera): {} (referer: {})", requestUri, referer);
        return true;
    }

    /**
     * Proxies the request to the Ephemera backend
     */
    private void proxyToEphemera(HttpServletRequest request, HttpServletResponse response) throws IOException {
        URI targetUri = null;
        try {
            // CRITICAL: Fetch base URL BEFORE making HTTP call to ensure any database access completes
            // This prevents holding database connections during long-running HTTP requests
            // Use null for userSettings since filter doesn't have user context (uses global settings)
            String baseUrl = properties.getEffectiveBaseUrl((com.adityachandel.booklore.model.dto.settings.UserEphemeraSettings) null);
            
            // Validate that ephemera is properly configured
            if (baseUrl == null || baseUrl.isBlank()) {
                log.warn("Ephemera is not configured, rejecting proxy request: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Ephemera is not configured. Please configure your Ephemera server settings.\"}");
                return;
            }

            String path = request.getRequestURI();
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
            uriBuilder.append(path);
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                uriBuilder.append("?").append(request.getQueryString());
            }
            targetUri = new URI(uriBuilder.toString());

            // Read request body with size limit (10MB default)
            byte[] body = readRequestBodyWithLimit(request, 10 * 1024 * 1024);

            // Build outbound request
            HttpRequest.BodyPublisher publisher = body.length == 0
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofByteArray(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(targetUri)
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .method(request.getMethod(), publisher);

            // Forward whitelisted headers
            Collections.list(request.getHeaderNames()).forEach(header -> {
                if (FORWARDED_HEADERS.contains(header.toLowerCase(Locale.ROOT))) {
                    Collections.list(request.getHeaders(header)).forEach(value -> builder.header(header, value));
                }
            });

            // Send request to Ephemera
            HttpRequest outboundRequest = builder.build();
            HttpResponse<byte[]> ephemeraResponse = httpClient.send(outboundRequest, HttpResponse.BodyHandlers.ofByteArray());

            // Copy response back to client
            response.setStatus(ephemeraResponse.statusCode());

            // Copy response headers
            ephemeraResponse.headers().map().forEach((name, values) -> {
                if (name != null && (name.equalsIgnoreCase(HttpHeaders.CONTENT_TYPE) ||
                        name.equalsIgnoreCase(HttpHeaders.CONTENT_DISPOSITION) ||
                        name.equalsIgnoreCase(HttpHeaders.CACHE_CONTROL))) {
                    values.forEach(value -> response.addHeader(name, value));
                }
            });

            // Copy response body
            byte[] responseBody = ephemeraResponse.body();
            if (responseBody != null && responseBody.length > 0) {
                response.getOutputStream().write(responseBody);
            }

            log.info("Successfully proxied Ephemera API call: {} (status: {})", request.getRequestURI(), ephemeraResponse.statusCode());

        } catch (java.net.http.HttpTimeoutException e) {
            log.error("Ephemera request timeout: {} -> {}", request.getRequestURI(), targetUri != null ? targetUri : "unknown", e);
            response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Ephemera service request timed out\"}");
        } catch (java.net.ConnectException e) {
            log.error("Failed to connect to Ephemera service: {} -> {}", request.getRequestURI(), targetUri != null ? targetUri : "unknown", e);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Failed to connect to Ephemera service\"}");
        } catch (java.net.URISyntaxException e) {
            log.error("Invalid Ephemera target URI: {}", request.getRequestURI(), e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid Ephemera target URI\"}");
        } catch (Exception e) {
            log.error("Failed to proxy Ephemera API call: {}", request.getRequestURI(), e);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Failed to reach Ephemera service: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Reads request body with size limit to prevent memory exhaustion
     */
    private byte[] readRequestBodyWithLimit(HttpServletRequest request, int maxSize) throws IOException {
        try (java.io.InputStream inputStream = request.getInputStream()) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int bytesRead;
            int totalBytes = 0;

            while ((bytesRead = inputStream.read(data, 0, Math.min(data.length, maxSize - totalBytes))) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > maxSize) {
                    throw new IOException("Request body size exceeds maximum allowed size of " + maxSize + " bytes");
                }
                buffer.write(data, 0, bytesRead);
            }

            return buffer.toByteArray();
        }
    }
}
