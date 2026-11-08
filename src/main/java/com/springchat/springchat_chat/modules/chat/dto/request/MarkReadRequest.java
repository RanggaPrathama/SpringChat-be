package com.springchat.springchat_chat.modules.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MarkReadRequest {
    @JsonProperty("messageId")
    @NotNull(message = "Pilih pesan yang ingin ditandai sudah dibaca.")
    private UUID messageId;

    public MarkReadRequest(UUID messageId) {
        setMessageId(messageId);
    }

}
