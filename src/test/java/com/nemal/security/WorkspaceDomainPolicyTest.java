package com.nemal.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceDomainPolicyTest {

    @Test
    void blankDomainAllowsEveryone() {
        WorkspaceDomainPolicy policy = new WorkspaceDomainPolicy("");

        assertTrue(policy.isAllowedEmail("someone@gmail.com"));
        assertTrue(policy.isAllowedGoogleAccount("someone@gmail.com", null));
    }

    @Test
    void enforcedDomainLimitsEmailRegistration() {
        WorkspaceDomainPolicy policy = new WorkspaceDomainPolicy("mitrai.com");

        assertTrue(policy.isAllowedEmail("Jane.Doe@MitraI.com"));
        assertFalse(policy.isAllowedEmail("jane@gmail.com"));
        assertFalse(policy.isAllowedEmail("jane@mitrai.com.evil.io"));
        assertFalse(policy.isAllowedEmail(null));
    }

    @Test
    void enforcedDomainRequiresWorkspaceHostedDomainForGoogle() {
        WorkspaceDomainPolicy policy = new WorkspaceDomainPolicy("@mitrai.com");

        assertTrue(policy.isAllowedGoogleAccount("jane@mitrai.com", "mitrai.com"));
        assertFalse(policy.isAllowedGoogleAccount("jane@gmail.com", null));
        assertFalse(policy.isAllowedGoogleAccount("jane@mitrai.com", null));
    }
}
