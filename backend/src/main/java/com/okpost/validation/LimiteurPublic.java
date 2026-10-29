package com.okpost.validation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 30 requêtes par minute et par adresse IP sur le portail public et la connexion. */
@Component
public class LimiteurPublic extends OncePerRequestFilter {

    private static final int MAX_PAR_MINUTE = 30;

    private final Map<String, AtomicInteger> compteurs = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return !(uri.startsWith("/api/v1/public/") || uri.startsWith("/api/v1/auth/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse rep, FilterChain chaine)
            throws ServletException, IOException {
        int n = compteurs.computeIfAbsent(req.getRemoteAddr(), k -> new AtomicInteger()).incrementAndGet();
        if (n > MAX_PAR_MINUTE) {
            rep.setStatus(429);
            rep.setContentType("application/problem+json;charset=UTF-8");
            rep.getWriter().write("{\"status\":429,\"detail\":\"Trop de requêtes. Réessaie dans une minute.\"}");
            return;
        }
        chaine.doFilter(req, rep);
    }

    @Scheduled(fixedRate = 60_000)
    public void reinitialiser() {
        compteurs.clear();
    }
}
