package com.springchat.springchat_chat.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
public class ApiResponse<T> {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private T data;

    @JsonProperty("error")
    private Object error;

    @JsonProperty("pagination")
    private PaginationMeta pagination;

    public ApiResponse(boolean success, String message, T data, Object error, PaginationMeta pagination) {
        setSuccess(success);
        setMessage(message);
        setData(data);
        setError(error);
        setPagination(pagination);
    }
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
