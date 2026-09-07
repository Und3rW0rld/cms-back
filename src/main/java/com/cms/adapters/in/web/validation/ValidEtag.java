package com.cms.adapters.in.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EtagValidator.class)
public @interface ValidEtag {
    String message() default "Invalid ETag format.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
