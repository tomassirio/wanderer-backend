package com.tomassirio.wanderer.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for validating password complexity requirements. Ensures passwords meet security
 * standards with uppercase, lowercase, numbers, and special characters.
 */
@Constraint(validatedBy = PasswordComplexityValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default
            "Password must be at least 8 characters and contain: uppercase, lowercase, number, and special character (@$!%*?&#)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
