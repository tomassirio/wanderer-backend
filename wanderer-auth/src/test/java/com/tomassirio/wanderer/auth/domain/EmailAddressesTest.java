package com.tomassirio.wanderer.auth.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EmailAddressesTest {

    @Test
    void normalize_trimsAndLowercases() {
        assertEquals("ana@gmail.com", EmailAddresses.normalize(" Ana@Gmail.COM "));
    }

    @Test
    void normalize_null_returnsNull() {
        assertNull(EmailAddresses.normalize(null));
    }
}
