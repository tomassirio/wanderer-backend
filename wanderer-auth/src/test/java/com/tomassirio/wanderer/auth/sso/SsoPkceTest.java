package com.tomassirio.wanderer.auth.sso;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SsoPkceTest {

    // RFC 7636 Appendix B
    static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @Test
    void isValidChallenge_acceptsS256OrOmittedMethod() {
        assertTrue(SsoPkce.isValidChallenge(CHALLENGE, null));
        assertTrue(SsoPkce.isValidChallenge(CHALLENGE, "S256"));
    }

    @Test
    void isValidChallenge_rejectsOtherMethodsAndMalformedValues() {
        assertFalse(SsoPkce.isValidChallenge(CHALLENGE, "plain"));
        assertFalse(SsoPkce.isValidChallenge(CHALLENGE, "s256"));
        assertFalse(SsoPkce.isValidChallenge(null, null));
        assertFalse(SsoPkce.isValidChallenge(CHALLENGE.substring(1), null));
        assertFalse(SsoPkce.isValidChallenge(CHALLENGE + "A", null));
        assertFalse(SsoPkce.isValidChallenge(CHALLENGE.substring(1) + "=", null));
        assertFalse(SsoPkce.isValidChallenge(CHALLENGE.substring(1) + "+", null));
    }

    @Test
    void isValidVerifier_followsRfc7636Charset() {
        assertTrue(SsoPkce.isValidVerifier(VERIFIER));
        assertTrue(SsoPkce.isValidVerifier("a".repeat(43)));
        assertTrue(SsoPkce.isValidVerifier("a.b_c~d-".repeat(16)));
        assertFalse(SsoPkce.isValidVerifier("a".repeat(42)));
        assertFalse(SsoPkce.isValidVerifier("a".repeat(129)));
        assertFalse(SsoPkce.isValidVerifier("a".repeat(42) + "+"));
        assertFalse(SsoPkce.isValidVerifier(null));
    }

    @Test
    void matches_rfcVector() {
        assertTrue(SsoPkce.matches(CHALLENGE, VERIFIER));
    }

    @Test
    void matches_rejectsWrongOrInvalidVerifier() {
        assertFalse(SsoPkce.matches(CHALLENGE, "x".repeat(43)));
        assertFalse(SsoPkce.matches(CHALLENGE, CHALLENGE));
        assertFalse(SsoPkce.matches(CHALLENGE, "short"));
        assertFalse(SsoPkce.matches(null, VERIFIER));
    }
}
