package com.gregomebije.ledger.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Collections;

import com.gregomebije.ledger.utility.TenantContext;

// NOTE: no @Component here on purpose. This filter is constructed and
// registered exactly once, as a @Bean inside SecurityConfig, and wired
// into the chain via addFilterBefore(...). Annotating it with @Component
// as well would cause Spring Boot to ALSO auto-register it as a generic
// container-wide servlet filter, running its logic twice per request.
@Component
public class CustomJwtFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    public CustomJwtFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtProvider.validateToken(token)) {
                String tenantId = jwtProvider.extractTenantId(token);
                String email = jwtProvider.extractEmail(token);

                // 1. Anchor context firmly within ThreadLocal space for this request thread duration
                TenantContext.setTenantId(tenantId);

                // 2. Authorize standard Spring Security lifecycle tracking mechanics
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        email, null, Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL SPREAD: Always clean out states on exit boundaries to prevent cross-request contamination
            TenantContext.clear();
        }
    }
}