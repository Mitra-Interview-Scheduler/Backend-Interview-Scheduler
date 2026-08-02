package com.nemal.util;

import com.nemal.enums.Role;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RoleUtils {

    private static final List<Role> ROLE_ORDER = List.of(Role.ADMIN, Role.HR, Role.INTERVIEWER);

    private RoleUtils() {
    }

    public static Set<Role> sortRoles(Collection<Role> roles) {
        LinkedHashSet<Role> sorted = new LinkedHashSet<>();
        if (roles == null || roles.isEmpty()) {
            return sorted;
        }

        for (Role role : ROLE_ORDER) {
            if (roles.contains(role)) {
                sorted.add(role);
            }
        }

        for (Role role : roles) {
            sorted.add(role);
        }

        return sorted;
    }

    public static List<String> toSortedRoleNames(Collection<Role> roles) {
        List<String> names = new ArrayList<>();
        for (Role role : sortRoles(roles)) {
            names.add(role.name());
        }
        return names;
    }
}
