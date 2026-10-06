package io.github.aishwaryajayanth1820.tds.web;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the bundled React app (ADR-0009). Existing files are served as-is; any other
 * non-API path without a file extension falls back to {@code index.html} so client-side
 * routes survive a reload. API paths never fall back, so unknown endpoints stay 404.
 */
@Configuration(proxyBeanMethods = false)
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) {
                            return file;
                        }
                        return isClientRoute(path) ? indexHtml() : null;
                    }
                });
    }

    static boolean isClientRoute(String path) {
        if (path.equals("api") || path.startsWith("api/")) {
            return false;
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !last.contains(".");
    }

    private static Resource indexHtml() {
        Resource index = new ClassPathResource("static/index.html");
        return index.exists() ? index : null;
    }
}
