package com.springchat.springchat_chat.common.response;

import java.util.List;

/** Internal paginated data; the controller supplies the public API envelope. */
public record PaginatedResult<T>(List<T> data, PaginationMeta pagination) {}
