package com.nemal.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenEncryptionServiceTest {

    private final TokenEncryptionService encryptionService =
            new TokenEncryptionService("test-encryption-key-for-unit-tests");

    @Test
    void encryptAndDecrypt_roundTripsPlaintext() {
        String plaintext = "ya29.a0AfH6SMC-example-access-token";
        String encrypted = encryptionService.encrypt(plaintext);

        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, encryptionService.decrypt(encrypted));
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTime() {
        String plaintext = "refresh-token-value";
        String first = encryptionService.encrypt(plaintext);
        String second = encryptionService.encrypt(plaintext);

        assertNotEquals(first, second);
        assertEquals(plaintext, encryptionService.decrypt(first));
        assertEquals(plaintext, encryptionService.decrypt(second));
    }

    @Test
    void decrypt_nullReturnsNull() {
        assertNull(encryptionService.decrypt(null));
    }
}
