package com.springchat.springchat_chat.modules.users.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidProfileUpdateValidator.class)
public @interface ValidProfileUpdate {
    String message() default "Periksa kembali isian Anda.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
