package com.springchat.springchat_chat.modules.users.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.springchat.springchat_chat.modules.users.validation.ValidProfileUpdate;
import com.springchat.springchat_chat.utils.Sanitizer;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@ValidProfileUpdate
@Data
@NoArgsConstructor
public class ProfileUpdateRequest {
    @JsonProperty("username")
    @Size(min = 3, max = 50, message = "Nama pengguna harus terdiri dari 3 hingga 50 karakter.")
    private String username;

    @JsonProperty("avatarUrl")
    @Size(max = 2048, message = "Tautan foto profil terlalu panjang.") @Pattern(regexp = "^$|https?://.+", message = "Gunakan tautan foto profil yang diawali http:// atau https://.")
    private String avatarUrl;

    public ProfileUpdateRequest(String username, String avatarUrl) {
        setUsername(username);
        setAvatarUrl(avatarUrl);
    }

    public void setUsername(String username) {
        this.username = Sanitizer.normalize(Sanitizer.sanitize(username));
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = Sanitizer.trim(avatarUrl);
    }

}

