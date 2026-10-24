package com.company.acts.analytics.application.port.out;

import java.util.Set;

public interface CurrentUserProvider {

    record CurrentUser(String userId, String username, Set<String> roles) {
        public boolean isAdmin() {
            return roles.contains("ADMIN");
        }

        public boolean isHead() {
            return roles.contains("DEPARTMENT_HEAD");
        }
    }

    CurrentUser current();
}
