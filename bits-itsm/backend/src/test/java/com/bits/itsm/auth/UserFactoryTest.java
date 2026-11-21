package com.bits.itsm.auth;

import com.bits.itsm.user.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The UserFactory that creates the right User subtype for admin-created accounts. */
class UserFactoryTest {
    @Test void factoryCreatesTheSubtypeForEachRole() {
        String hash = "$2-test-only";
        assertInstanceOf(Student.class, UserFactory.create(Role.STUDENT, " Ananya ", "s@hyderabad.bits-pilani.ac.in", hash, "2025A7PS0001H", null));
        assertInstanceOf(Technician.class, UserFactory.create(Role.TECHNICIAN, "Tech", "t@hyderabad.bits-pilani.ac.in", hash, null, TechnicianSkill.NETWORK));
        assertInstanceOf(Administrator.class, UserFactory.create(Role.ADMIN, "Admin", "a@hyderabad.bits-pilani.ac.in", hash, null, null));
        assertEquals("Ananya", UserFactory.create(Role.STUDENT, " Ananya ", "s@hyderabad.bits-pilani.ac.in", hash, "ID01", null).getName());
        assertThrows(IllegalArgumentException.class, () -> UserFactory.create(Role.STUDENT, "Ananya", "s@hyderabad.bits-pilani.ac.in", hash, " ", null));
        assertThrows(NullPointerException.class, () -> UserFactory.create(Role.TECHNICIAN, "Tech", "t@hyderabad.bits-pilani.ac.in", hash, null, null));
    }
}
