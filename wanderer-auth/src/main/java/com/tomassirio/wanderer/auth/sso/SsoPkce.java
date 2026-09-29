package com.tomassirio.wanderer.auth.sso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * RFC 7636 (S256 only) binding between the client that starts an SSO login and the one that
 * exchanges the one-time code. Stops another app that claimed {@code wanderer://} from redeeming an
 * intercepted code, and stops an attacker from injecting their own code into a victim's client.
 */
public final class SsoPkce {

    public static final String S256 = "S256";
    public static final String VERIFIER_REGEX = "[A-Za-z0-9\\-._~]{43,128}";

    private static final Pattern CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern VERIFIER = Pattern.compile(VERIFIER_REGEX);

    private SsoPkce() {}

    /** A base64url (no padding) SHA-256 digest; the method, if sent, must be S256. */
    public static boolean isValidChallenge(String challenge, String method) {
        return challenge != null
                && CHALLENGE.matcher(challenge).matches()
                && (method == null || S256.equals(method));
    }

    public static boolean isValidVerifier(String verifier) {
        return verifier != null && VERIFIER.matcher(verifier).matches();
    }

    /** Constant-time check that BASE64URL-NOPAD(SHA-256(ASCII(verifier))) equals the challenge. */
    public static boolean matches(String challenge, String verifier) {
        if (!isValidChallenge(challenge, null) || !isValidVerifier(verifier)) {
            return false;
        }
        return MessageDigest.isEqual(
                challenge.getBytes(StandardCharsets.US_ASCII),
                s256(verifier).getBytes(StandardCharsets.US_ASCII));
    }

    private static String s256(String verifier) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
