package com.lta.whatsapp.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

    @Value("${app.admin-key}")
    private String adminKey;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        // Only guard admin API namespace
        String uri = req.getRequestURI();
        if (!uri.startsWith("/api/whatsapp")) return true;

        String key = req.getHeader("X-ADMIN-KEY");
        if (adminKey != null && adminKey.equals(key)) return true;

        res.setStatus(403);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"Forbidden\"}");
        return false;
    }
}
