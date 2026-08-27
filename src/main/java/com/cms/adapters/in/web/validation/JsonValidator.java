package com.cms.adapters.in.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class JsonValidator implements ConstraintValidator<ValidJson, String> {

    private final ObjectMapper mapper;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            return mapper.readTree(value).isObject();
        } catch (JacksonException e) {
            return false;
        }
    }
}
