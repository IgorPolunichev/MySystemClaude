package com.company.acts.mail.application.port.out;

import java.util.Set;

public interface CurrentUserProvider {

    record CurrentUser(String username, String fullName, String email, Set<String> roles) {
        public boolean isAdmin() {
            return roles.contains("ADMIN");
        }
    }

    CurrentUser current();
}
