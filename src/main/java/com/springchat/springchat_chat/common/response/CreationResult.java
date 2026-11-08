package com.springchat.springchat_chat.common.response;

/** Internal service result; controllers use created to choose HTTP 201 or 200. */
public record CreationResult<T>(T data, boolean created) {}
