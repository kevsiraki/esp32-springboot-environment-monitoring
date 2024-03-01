package temperature.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;
import temperature.repository.UserRepository;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ApiKeyValidationFilter extends GenericFilterBean {

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public ApiKeyValidationFilter(ObjectMapper objectMapper, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();

        // Rate limiting logic
        String ipAddress = getClientIpAddress(httpRequest);
        int requests = requestCounts.compute(ipAddress, (key, value) -> value == null ? 1 : value + 1);
        if (requests > MAX_REQUESTS_PER_MINUTE) {
            httpResponse.setStatus(429);
            httpResponse.getWriter().write("Too many requests");
            return;
        }

        // Schedule removal of IP address entry after a minute
        scheduleReset(ipAddress);

        // Extract the request URI
        if (requestUri.startsWith("/temperatures") || requestUri.startsWith("/devices")) {
            // Extract API key from request headers
            String apiKey = httpRequest.getHeader("X-API-Key");

            // If API key is null or empty, reject the request
            if (apiKey == null || apiKey.isEmpty()) {
                writeJsonResponse(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "API key is missing");
                return;
            }

            // Set API key in authentication object
            Authentication authentication = new ApiKeyAuthentication(apiKey, userRepository);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        chain.doFilter(request, response);
    }

    private void scheduleReset(String ipAddress) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.schedule(() -> requestCounts.remove(ipAddress), 1, TimeUnit.MINUTES);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader != null) {
            return xForwardedForHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    private void writeJsonResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");

        // Create JSON response body with HATEOAS links
        Map<String, Object> responseBody = new ConcurrentHashMap<>();
        responseBody.put("status", status);
        responseBody.put("message", message);
        responseBody.put("_links", getErrorLinks());

        objectMapper.writeValue(response.getWriter(), responseBody);
    }

    private Map<String, Link> getErrorLinks() {
        Map<String, Link> links = new ConcurrentHashMap<>();
        links.put("self", Link.of(WebMvcLinkBuilder.linkTo(ApiKeyValidationFilter.class).withSelfRel().getHref()));
        return links;
    }
}
