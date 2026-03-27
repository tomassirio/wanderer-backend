package com.tomassirio.wanderer.auth.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordComplexityValidatorTest {

    private PasswordComplexityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordComplexityValidator();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "SecurePass1!",
                "MyTrip2026@",
                "Wanderer#123",
                "Test1234$",
                "ComplexP@ss1",
                "Abcd1234!",
                "MyP@ssw0rd"
            })
    void isValid_whenPasswordMeetsAllRequirements_shouldReturnTrue(String password) {
        assertTrue(validator.isValid(password, null));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "password123", // No uppercase, no special
                "PASSWORD123!", // No lowercase
                "MyPassword", // No number, no special
                "Pass1!", // Too short (< 8 chars)
                "mypassword1!", // No uppercase
                "MYPASSWORD1!", // No lowercase
                "MyPassword!", // No number
                "MyPassword1", // No special character
                "Short1!", // Only 7 characters
                "" // Empty (handled by @NotBlank but tested here)
            })
    void isValid_whenPasswordDoesNotMeetRequirements_shouldReturnFalse(String password) {
        assertFalse(validator.isValid(password, null));
    }

    @Test
    void isValid_whenPasswordIsNull_shouldReturnTrue() {
        // Null is allowed here because @NotBlank handles it
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void isValid_whenPasswordIsBlank_shouldReturnFalse() {
        // Blank should be rejected by this validator
        assertFalse(validator.isValid("   ", null));
    }

    @Test
    void isValid_whenPasswordContainsInvalidSpecialCharacters_shouldReturnFalse() {
        // Special chars allowed: @$!%*?&#
        assertFalse(validator.isValid("Password1^", null)); // ^ not allowed
        assertFalse(validator.isValid("Password1()", null)); // () not allowed
        assertFalse(validator.isValid("Password1~", null)); // ~ not allowed
    }

    @Test
    void isValid_whenPasswordContainsSpaces_shouldReturnFalse() {
        assertFalse(validator.isValid("My Pass1!", null));
        assertFalse(validator.isValid(" MyPass1!", null));
        assertFalse(validator.isValid("MyPass1! ", null));
    }

    @Test
    void isValid_whenPasswordIsExactly8Characters_shouldReturnTrue() {
        assertTrue(validator.isValid("MyPass1!", null));
    }

    @Test
    void isValid_whenPasswordIsVeryLong_shouldReturnTrue() {
        String longPassword = "VeryLongSecureP@ssw0rd" + "a".repeat(100);
        assertTrue(validator.isValid(longPassword, null));
    }
}
