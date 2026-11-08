package com.springchat.springchat_chat.modules.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.springchat.springchat_chat.modules.chat.enums.ConversationType;
import com.springchat.springchat_chat.modules.chat.validation.ValidConversationRequest;
import com.springchat.springchat_chat.utils.Sanitizer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

@ValidConversationRequest
@Data
@NoArgsConstructor
public class CreateConversationRequest {
    @JsonProperty("type")
    @NotNull(message = "Pilih jenis percakapan.")
    private ConversationType type;

    @JsonProperty("name")
    @Size(max = 100, message = "Nama grup maksimal 100 karakter.")
    private String name;

    @JsonProperty("memberIds")
    @NotEmpty(message = "Pilih pengguna yang ingin diajak mengobrol.")
    private List<@NotNull(message = "Ada anggota yang belum dipilih. Pilih kembali anggota percakapan.") UUID> memberIds;

    public CreateConversationRequest(ConversationType type, String name, List<UUID> memberIds) {
        setType(type);
        setName(name);
        setMemberIds(memberIds);
    }

    public void setName(String name) {
        this.name = Sanitizer.trim(Sanitizer.sanitize(name));
    }

}
