package com.springchat.springchat_chat.modules.chat.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidConversationRequestValidator.class)
public @interface ValidConversationRequest {
    String message() default "Periksa kembali isian Anda.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
