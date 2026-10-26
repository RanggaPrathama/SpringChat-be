package com.springchat.springchat_chat.common.response;

import org.springframework.data.domain.Page;



public record PaginationMeta(
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
    public static PaginationMeta from(Page<?> result) {
        return new PaginationMeta(
                result.getNumber() + 1,
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.hasPrevious());
    }

    }





