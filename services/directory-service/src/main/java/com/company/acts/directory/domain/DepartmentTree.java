package com.company.acts.directory.domain;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Операции над деревом подразделений одной организации. */
public final class DepartmentTree {

    private final List<Department> departments;

    public DepartmentTree(Collection<Department> departments) {
        this.departments = List.copyOf(departments);
    }

    /** Подразделение и все вложенные в него. */
    public Set<UUID> subtree(UUID rootId) {
        Set<UUID> result = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (result.add(current)) {
                departments.stream()
                        .filter(d -> current.equals(d.parentId()))
                        .forEach(d -> queue.add(d.id()));
            }
        }
        return result;
    }

    /** Станет ли {@code newParentId} потомком {@code departmentId} — то есть возникнет цикл. */
    public boolean wouldCreateCycle(UUID departmentId, UUID newParentId) {
        return newParentId != null && subtree(departmentId).contains(newParentId);
    }

    public boolean hasChildren(UUID departmentId) {
        return departments.stream().anyMatch(d -> departmentId.equals(d.parentId()));
    }
}
