package com.springchat.springchat_chat.common.response;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Object error,
        PaginationMeta pagination) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, null);
    }

    public static <T> ApiResponse<T> error(String message, Object error) {
        return new ApiResponse<>(false, message, null, error, null);
    }

    public static <T> ApiResponse<T> paginated(String message, T data, PaginationMeta pagination) {
        return new ApiResponse<>(true, message, data, null, pagination);
    }

}
