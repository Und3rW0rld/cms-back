package com.cms.adapters.in.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EtagValidator implements ConstraintValidator<ValidEtag, String> {

    @Override
    public boolean isValid(String etag, ConstraintValidatorContext context) {
        return etag != null && !etag.isEmpty() && etag.matches("\"\\d+\"");
    }
}
