package com.careercopilot.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a traceId (inbound X-Request-Id if it looks sane, else a fresh UUID), puts
 * it in MDC for structured logging, and echoes it back so client and server logs correlate. Ordered
 * ahead of the Spring Security filter chain so JwtAuthFilter and every downstream handler see it
 * already set.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

  public static final String TRACE_ID_HEADER = "X-Request-Id";
  public static final String MDC_KEY = "traceId";

  private static final Pattern VALID_TRACE_ID = Pattern.compile("^[a-zA-Z0-9-]{1,64}$");

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    String inbound = request.getHeader(TRACE_ID_HEADER);
    String traceId =
        (inbound != null && VALID_TRACE_ID.matcher(inbound).matches())
            ? inbound
            : UUID.randomUUID().toString();
    try {
      MDC.put(MDC_KEY, traceId);
      response.setHeader(TRACE_ID_HEADER, traceId);
      filterChain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
