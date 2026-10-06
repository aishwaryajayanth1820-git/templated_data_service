package io.github.aishwaryajayanth1820.tds.catalog;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Adds {@code X-TDS-Catalog-Generation} to API responses so the SPA can refetch metadata after a publish (ADR-0007). */
@Component
class CatalogGenerationFilter extends OncePerRequestFilter {

    static final String HEADER = "X-TDS-Catalog-Generation";

    private final CatalogService catalog;

    CatalogGenerationFilter(CatalogService catalog) {
        this.catalog = catalog;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, Long.toString(catalog.snapshot().generation()));
        chain.doFilter(request, response);
    }
}
