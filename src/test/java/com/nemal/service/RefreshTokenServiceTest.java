package com.nemal.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RefreshTokenServiceTest {

    @Test
    void hashIsStableSha256Hex() {
        String first = RefreshTokenService.hash("raw-token");
        String second = RefreshTokenService.hash("raw-token");

        assertEquals(64, first.length());
        assertEquals(first, second);
        assertNotEquals(first, RefreshTokenService.hash("other-token"));
    }
}
