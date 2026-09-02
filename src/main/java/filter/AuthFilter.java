package filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

/**
 * DESIGN PATTERN: Intercepting Filter (Chain of Responsibility).
 *
 * Every /api/* request passes through here first. Endpoints that must work
 * before sign-in are listed explicitly; everything else needs a session, so no
 * servlet can accidentally be left unprotected.
 */
@WebFilter("/api/*")
public class AuthFilter implements Filter {

    private static final String SESSION_USER = "authenticatedUser";

    /** Exact paths that stay open. */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/health",
            "/api/settings/public"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI().substring(req.getContextPath().length());

        if (isPublic(path) || "OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER) == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"success\":false,\"message\":\"Your session has ended. Please sign in again.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isPublic(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        // The waiting-room screen shows queue numbers only, so it needs no login.
        if (path.startsWith("/api/sessions/") && path.endsWith("/queue")) {
            return true;
        }
        // The public portal: clinic details, open sessions, self-registration
        // and booking. PosService decides what those two commands may touch.
        return path.equals("/api/pos/public") || path.startsWith("/api/pos/public/");
    }
}
