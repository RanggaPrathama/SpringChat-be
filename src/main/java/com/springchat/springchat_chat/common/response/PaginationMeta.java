package com.springchat.springchat_chat.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
public class PaginationMeta {
    @JsonProperty("page")
    private Integer page;

    @JsonProperty("size")
    private int size;

    @JsonProperty("totalElements")
    private Long totalElements;

    @JsonProperty("totalPages")
    private Integer totalPages;

    @JsonProperty("hasNext")
    private boolean hasNext;

    @JsonProperty("hasPrevious")
    private Boolean hasPrevious;

    @JsonProperty("nextCursor")
    private String nextCursor;

    public PaginationMeta(int page, int size, long totalElements, int totalPages, boolean hasNext, boolean hasPrevious) {
        setPage(page);
        setSize(size);
        setTotalElements(totalElements);
        setTotalPages(totalPages);
        setHasNext(hasNext);
        setHasPrevious(hasPrevious);
    }
public static PaginationMeta from(Page<?> result) {
        return new PaginationMeta(
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious());
    }

    public static PaginationMeta cursor(int size, boolean hasNext, String nextCursor) {
        PaginationMeta meta = new PaginationMeta();
        meta.setSize(size);
        meta.setHasNext(hasNext);
        meta.setNextCursor(hasNext ? nextCursor : null);
        return meta;
    }
}
