package com.springchat.springchat_chat.common.response;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

class PaginationMetaTest {
    @Test
    void numberedPaginationKeepsTotalsAndOmitsCursor() {
        var page = new PageImpl<>(List.of("item"), PageRequest.of(0, 1), 3);
        var json = JsonMapper.builder().build().valueToTree(PaginationMeta.from(page));
        assertThat(json.get("page").asInt()).isEqualTo(1);
        assertThat(json.get("totalElements").asLong()).isEqualTo(3);
        assertThat(json.get("totalPages").asInt()).isEqualTo(3);
        assertThat(json.get("hasPrevious").asBoolean()).isFalse();
        assertThat(json.has("nextCursor")).isFalse();
    }
}
