package com.springchat.springchat_chat.modules.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.springchat.springchat_chat.utils.Sanitizer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class LoginRequest {
    @JsonProperty("email")
    @NotBlank(message = "Masukkan alamat email.")
    @Size(max = 255, message = "Alamat email maksimal 255 karakter.")
    private String email;

    @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY)
    @lombok.ToString.Exclude
    @NotBlank(message = "Masukkan kata sandi.")
    @Size(max = 72, message = "Kata sandi maksimal 72 karakter.")
    private String password;

    public LoginRequest(String email, String password) {
        setEmail(email);
        setPassword(password);
    }

    public void setEmail(String email) {
        this.email = Sanitizer.normalize(email);
    }

}
