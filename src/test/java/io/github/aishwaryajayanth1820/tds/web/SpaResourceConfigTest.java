package io.github.aishwaryajayanth1820.tds.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpaResourceConfigTest {

    @Test
    void clientRoutesFallBackToIndex() {
        assertThat(SpaResourceConfig.isClientRoute("")).isTrue();
        assertThat(SpaResourceConfig.isClientRoute("t/alerts")).isTrue();
        assertThat(SpaResourceConfig.isClientRoute("admin/studio/alerts")).isTrue();
    }

    @Test
    void filesAndApiPathsDoNot() {
        assertThat(SpaResourceConfig.isClientRoute("assets/index-abc.js")).isFalse();
        assertThat(SpaResourceConfig.isClientRoute("favicon.svg")).isFalse();
        assertThat(SpaResourceConfig.isClientRoute("api")).isFalse();
        assertThat(SpaResourceConfig.isClientRoute("api/unknown")).isFalse();
    }
}
