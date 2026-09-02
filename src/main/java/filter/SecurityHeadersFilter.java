package filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Sets UTF-8 on every request and adds the browser hardening headers the
 * ETHICAL criterion of the brief asks for (clickjacking, MIME sniffing and
 * referrer leakage).
 */
@WebFilter("/*")
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");

        HttpServletResponse resp = (HttpServletResponse) response;
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("X-Frame-Options", "SAMEORIGIN");
        resp.setHeader("Referrer-Policy", "same-origin");
        resp.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

        HttpServletRequest req = (HttpServletRequest) request;
        if (req.getRequestURI().contains("/api/")) {
            resp.setHeader("Cache-Control", "no-store");
        }

        chain.doFilter(request, response);
    }
}
