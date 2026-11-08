package com.springchat.springchat_chat.modules.users.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.springchat.springchat_chat.utils.Sanitizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateUserRequest {
    @JsonProperty("username")
    @NotBlank(message = "Masukkan nama pengguna.")
    @Size(min = 3, max = 50, message = "Nama pengguna harus terdiri dari 3 hingga 50 karakter.")
    private String username;

    @JsonProperty("email")
    @NotBlank(message = "Masukkan alamat email.")
    @Email(message = "Masukkan alamat email yang valid.")
    @Size(max = 255, message = "Alamat email maksimal 255 karakter.")
    private String email;

    @JsonProperty(value = "password", access = JsonProperty.Access.WRITE_ONLY)
    @lombok.ToString.Exclude
    @NotBlank(message = "Masukkan kata sandi.")
    @Size(min = 8, max = 72, message = "Kata sandi harus terdiri dari 8 hingga 72 karakter.")
    private String password;

    public CreateUserRequest(String username, String email, String password) {
        setUsername(username);
        setEmail(email);
        setPassword(password);
    }

    public void setUsername(String username) {
        this.username = Sanitizer.normalize(Sanitizer.sanitize(username));
    }

    public void setEmail(String email) {
        this.email = Sanitizer.normalize(email);
    }

}
