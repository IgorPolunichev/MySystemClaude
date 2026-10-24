package com.company.acts.analytics.application;

import com.company.acts.analytics.application.port.out.CurrentUserProvider;
import com.company.acts.analytics.application.port.out.DirectoryClient;
import com.company.acts.analytics.application.view.DashboardView;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Кого видит пользователь (раздел 9 SPEC.md): инженер — только себя, руководитель — свой отдел,
 * администратор — всех.
 */
@Component
class AccessScope {

    record Visible(DashboardView.Scope scope, List<DirectoryClient.EngineerRef> engineers, UUID departmentId) {

        boolean contains(UUID engineerId) {
            return engineers.stream().anyMatch(e -> e.id().equals(engineerId));
        }

        boolean canEditDepartment(UUID department) {
            return scope == DashboardView.Scope.ALL
                    || scope == DashboardView.Scope.DEPARTMENT && department != null && department.equals(departmentId);
        }
    }

    private final DirectoryClient directory;
    private final CurrentUserProvider users;

    AccessScope(DirectoryClient directory, CurrentUserProvider users) {
        this.directory = directory;
        this.users = users;
    }

    Visible resolve() {
        CurrentUserProvider.CurrentUser user = users.current();
        List<DirectoryClient.EngineerRef> all = directory.engineers();
        if (user.isAdmin()) {
            return new Visible(DashboardView.Scope.ALL, all, null);
        }
        DirectoryClient.EngineerRef me = directory.me();
        if (me == null) {
            throw new AccessDeniedException("Для вашей учётной записи нет карточки инженера");
        }
        if (user.isHead() && me.departmentId() != null) {
            return new Visible(DashboardView.Scope.DEPARTMENT,
                    all.stream().filter(e -> me.departmentId().equals(e.departmentId())).toList(), me.departmentId());
        }
        return new Visible(DashboardView.Scope.SELF, List.of(me), me.departmentId());
    }
}
