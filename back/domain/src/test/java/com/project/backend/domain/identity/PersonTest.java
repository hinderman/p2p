package com.project.backend.domain.identity;

import com.project.backend.domain.exception.DomainRuleViolation;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final EmailAddress EMAIL = new EmailAddress("carolina@example.com");

    @Test
    void a_registered_person_keeps_a_trimmed_name_and_stays_pending_until_verified() {
        Person person = Person.register(newId(), EMAIL, "  Carolina  ", "  Restrepo  ", NOW);

        assertEquals("Carolina", person.firstName());
        assertEquals("Restrepo", person.lastName());
        assertEquals(PersonStatus.PENDING, person.status());
    }

    /** An invited counterparty is known only by the address the inviter typed. */
    @Test
    void an_invited_person_has_no_name_yet() {
        Person person = Person.createPending(newId(), EMAIL, NOW);

        assertNull(person.firstName());
        assertNull(person.lastName());
    }

    @Test
    void naming_an_invited_person_fills_in_what_registration_now_knows() {
        Person person = Person.createPending(newId(), EMAIL, NOW);

        person.identify("Carolina", "Restrepo");

        assertEquals("Carolina", person.firstName());
        assertEquals("Restrepo", person.lastName());
    }

    @Test
    void rejects_a_blank_name() {
        assertThrows(DomainRuleViolation.class, () -> Person.register(newId(), EMAIL, "   ", "Restrepo", NOW));
    }

    @Test
    void rejects_a_name_beyond_the_column_width() {
        String tooLong = "N".repeat(121);

        assertThrows(DomainRuleViolation.class, () -> Person.register(newId(), EMAIL, tooLong, "Restrepo", NOW));
    }

    private static PersonId newId() {
        return new PersonId(UUID.randomUUID());
    }
}
