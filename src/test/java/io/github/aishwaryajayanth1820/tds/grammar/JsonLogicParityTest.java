package io.github.aishwaryajayanth1820.tds.grammar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Shared vectors with ui/src/grammar/jsonlogic.test.ts (ADR-0003). */
class JsonLogicParityTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static Stream<Arguments> vectors() throws Exception {
        JsonNode all = MAPPER.readTree(Files.readString(Path.of("testdata/parity/jsonlogic.json")));
        return all.values().stream().map(v -> Arguments.of(v.get("name").asString(), v));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vectors")
    @SuppressWarnings("unchecked")
    void matchesExpected(String name, JsonNode v) {
        Map<String, Object> data = (Map<String, Object>) JsonLogic.literal(v.get("data"));
        Object actual = JsonLogic.apply(v.get("rule"), data);
        Object expected = JsonLogic.literal(v.get("expected"));
        if (expected instanceof Number e && actual instanceof Number a) {
            assertThat(a.doubleValue()).isEqualTo(e.doubleValue());
        } else if (expected instanceof List<?> && actual instanceof List<?>) {
            assertThat(actual).isEqualTo(expected);
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }
}
