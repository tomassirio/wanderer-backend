package com.tomassirio.wanderer.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/** Validator for password complexity. Enforces strong password requirements to enhance security. */
public class PasswordComplexityValidator implements ConstraintValidator<ValidPassword, String> {

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile(
                    "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,}$");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true; // @NotBlank handles null validation
        }

        if (password.isBlank()) {
            return false; // Reject blank/whitespace-only passwords
        }

        return PASSWORD_PATTERN.matcher(password).matches();
    }
}
