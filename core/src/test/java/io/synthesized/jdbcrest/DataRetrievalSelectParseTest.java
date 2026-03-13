package io.synthesized.jdbcrest;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DataRetrievalSelectParseTest {

    @Test
    void parseSimpleColumns() {
        Map<String, String> cols = DataRetrieval.parseSelectParam("id,price");
        assertThat(cols).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "id",
                "price", "price"
        ));
    }

    @Test
    void parseColumnsWithAliases() {
        Map<String, String> cols = DataRetrieval.parseSelectParam("c1:id,c2:price");
        assertThat(cols).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "c1",
                "price", "c2"
        ));
    }

    @Test
    void parseIgnoresBlanksAndWhitespace() {
        Map<String, String> cols = DataRetrieval.parseSelectParam("  id , , price  ,  ");
        assertThat(cols).containsExactlyInAnyOrderEntriesOf(Map.of(
                "id", "id",
                "price", "price"
        ));
    }

    @Test
    void parseNullOrEmptyReturnsEmpty() {
        assertThat(DataRetrieval.parseSelectParam(null)).isEmpty();
        assertThat(DataRetrieval.parseSelectParam("")).isEmpty();
        assertThat(DataRetrieval.parseSelectParam("   ")).isEmpty();
    }

    @Test
    void parseSkipsInvalidAliasForms() {
        Map<String, String> cols = DataRetrieval.parseSelectParam("a:, :b, valid");
        assertThat(cols).containsExactlyInAnyOrderEntriesOf(Map.of(
                "valid", "valid"
        ));
    }
}
