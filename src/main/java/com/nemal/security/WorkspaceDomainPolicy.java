package com.nemal.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Restricts sign-up and Google sign-in to the company Google Workspace domain.
 * When google.workspace.domain is blank (local/dev), every domain is allowed.
 */
@Component
public class WorkspaceDomainPolicy {

    private final String workspaceDomain;

    public WorkspaceDomainPolicy(@Value("${google.workspace.domain:}") String workspaceDomain) {
        String normalized = workspaceDomain == null ? "" : workspaceDomain.trim().toLowerCase(Locale.ROOT);
        this.workspaceDomain = normalized.startsWith("@") ? normalized.substring(1) : normalized;
    }

    public boolean isEnforced() {
        return !workspaceDomain.isEmpty();
    }

    public boolean isAllowedEmail(String email) {
        if (!isEnforced()) {
            return true;
        }
        return email != null && email.trim().toLowerCase(Locale.ROOT).endsWith("@" + workspaceDomain);
    }

    /** Google ID tokens carry the Workspace domain in the "hd" claim; personal accounts have none. */
    public boolean isAllowedGoogleAccount(String email, String hostedDomain) {
        if (!isEnforced()) {
            return true;
        }
        return hostedDomain != null
                && workspaceDomain.equals(hostedDomain.trim().toLowerCase(Locale.ROOT))
                && isAllowedEmail(email);
    }

    public String domain() {
        return workspaceDomain;
    }
}
