package com.springchat.springchat_chat.modules.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.springchat.springchat_chat.utils.Sanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SendMessageRequest {
    @JsonProperty("content")
    @NotBlank(message = "Tulis pesan yang ingin dikirim.")
    @Size(max = 5000, message = "Pesan maksimal 5000 karakter.")
    private String content;

    @JsonProperty("idempotencyKey")
    @NotNull(message = "Pesan belum dapat dikirim. Silakan coba kirim kembali.")
    @JsonAlias("clientMessageId")
    private UUID idempotencyKey;

    public SendMessageRequest(String content, UUID idempotencyKey) {
        setContent(content);
        setIdempotencyKey(idempotencyKey);
    }

    public void setContent(String content) {
        this.content = Sanitizer.sanitize(content);
    }

}
