package com.bits.itsm.user;

import java.util.Objects;

/**
 * Factory pattern: the only place that decides which {@link User} subclass to instantiate for a role.
 * Used by administrator account creation and by student self-registration, so the rules for each
 * subtype (student number for students, a primary skill for technicians) live in one place.
 */
public final class UserFactory {
    private UserFactory() { }

    public static User create(Role role, String name, String normalizedEmail, String passwordHash, String studentNumber, TechnicianSkill skill) {
        String cleanName = Objects.requireNonNull(name, "Name is required.").trim();
        return switch (Objects.requireNonNull(role, "Role is required.")) {
            case STUDENT -> {
                if (studentNumber == null || studentNumber.isBlank()) throw new IllegalArgumentException("Student ID is required.");
                yield new Student(cleanName, normalizedEmail, passwordHash, studentNumber.trim());
            }
            case TECHNICIAN -> new Technician(cleanName, normalizedEmail, passwordHash, Objects.requireNonNull(skill, "Select a technician skill.").name());
            case ADMIN -> new Administrator(cleanName, normalizedEmail, passwordHash, "Campus IT administration");
        };
    }
}
