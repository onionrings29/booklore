package com.adityachandel.booklore.filter;

import com.adityachandel.booklore.service.ephemera.EphemeraProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

        // Don't intercept requests that are already going through the Ephemera proxy
        if (requestUri.startsWith("/api/v1/ephemera")) {
            return false;
        }

        // Don't intercept booklore's own API endpoints
        if (requestUri.startsWith("/api/v1/")) {
            return false;
        }

        // Don't intercept if referer is null
        if (referer == null || referer.isEmpty()) {
            return false;
        }

        // CRITICAL: Only intercept if the referer is specifically from the Ephemera iframe
        // The referer must contain /api/v1/ephemera/ which is the unique ephemera proxy path
        // This ensures we ONLY intercept requests originating from ephemera's iframe,
        // not from any other booklore pages
        boolean isFromEphemera = referer.contains("/api/v1/ephemera/");

        if (isFromEphemera) {
            log.debug("Detected API request from Ephemera iframe: {} with referer: {}", requestUri, referer);
        }

        return isFromEphemera;
    }

    /**
     * Proxies the request to the Ephemera backend
     */
    private void proxyToEphemera(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // Build target URI for Ephemera backend
            String baseUrl = properties.getEffectiveBaseUrl();
            String path = request.getRequestURI();
            StringBuilder uriBuilder = new StringBuilder();
            uriBuilder.append(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
            uriBuilder.append(path);
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                uriBuilder.append("?").append(request.getQueryString());
            }
            URI targetUri = new URI(uriBuilder.toString());

            // Read request body
            byte[] body = request.getInputStream().readAllBytes();

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

        } catch (Exception e) {
            log.error("Failed to proxy Ephemera API call: {}", request.getRequestURI(), e);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Failed to reach Ephemera service\"}");
        }
    }
}
