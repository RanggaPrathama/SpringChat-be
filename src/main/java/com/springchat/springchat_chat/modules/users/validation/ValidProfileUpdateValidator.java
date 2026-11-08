package com.springchat.springchat_chat.modules.users.validation;

import com.springchat.springchat_chat.modules.users.dto.request.ProfileUpdateRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
public class ValidProfileUpdateValidator implements ConstraintValidator<ValidProfileUpdate, ProfileUpdateRequest> {
    @Override
    public boolean isValid(ProfileUpdateRequest value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return value.getUsername() != null || value.getAvatarUrl() != null
                || error(context, "username", "Isi nama pengguna atau foto profil yang ingin diubah.");
    }
    private boolean error(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
        return false;
    }
}
