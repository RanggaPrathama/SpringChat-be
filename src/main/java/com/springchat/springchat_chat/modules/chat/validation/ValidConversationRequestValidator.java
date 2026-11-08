package com.springchat.springchat_chat.modules.chat.validation;

import com.springchat.springchat_chat.modules.chat.dto.request.CreateConversationRequest;
import com.springchat.springchat_chat.modules.chat.enums.ConversationType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
public class ValidConversationRequestValidator implements ConstraintValidator<ValidConversationRequest, CreateConversationRequest> {
    @Override
    public boolean isValid(CreateConversationRequest value, ConstraintValidatorContext context) {
        if (value == null) return true;
        if (value.getType() == null || value.getMemberIds() == null) return true;
        var ids = value.getMemberIds();
        if (new java.util.HashSet<>(ids).size() != ids.size()) return error(context, "memberIds", "Pengguna yang sama tidak boleh dipilih lebih dari sekali.");
        if (value.getType() == ConversationType.DIRECT) {
            if (ids.size() != 1) return error(context, "memberIds", "Pilih satu orang untuk memulai percakapan pribadi.");
            if (value.getName() != null) return error(context, "name", "Nama percakapan hanya digunakan untuk grup.");
        } else {
            if (ids.size() < 2) return error(context, "memberIds", "Pilih setidaknya dua orang untuk membuat grup.");
            if (value.getName() == null || value.getName().isBlank()) return error(context, "name", "Masukkan nama grup.");
        }
        return true;
    }
    private boolean error(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
        return false;
    }
}
