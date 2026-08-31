package com.project.backend.infrastructure.persistence.repository;

import com.project.backend.domain.identity.Person;
import com.project.backend.domain.identity.PersonStatus;
import com.project.backend.domain.repository.PersonRepository;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.project.backend.infrastructure.persistence.JdbcTime.timestamp;
import static com.project.backend.infrastructure.persistence.JdbcTime.instant;

@Repository
public final class JdbcPersonRepository implements PersonRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPersonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Person> findById(PersonId id) {
        return queryPerson("""
                SELECT person.*, email.normalized_email FROM loans.people person
                JOIN loans.person_emails email ON email.person_id = person.person_id AND email.is_primary
                WHERE person.person_id = ?
                """, id.value());
    }

    @Override
    public Optional<Person> findByEmail(EmailAddress email) {
        return queryPerson("""
                SELECT person.*, email.normalized_email FROM loans.people person
                JOIN loans.person_emails email ON email.person_id = person.person_id AND email.is_primary
                WHERE email.normalized_email = ?
                """, email.value());
    }

    @Override
    public Person save(Person person) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update("""
                UPDATE loans.people
                SET first_name = ?, last_name = ?, status = ?, updated_at = ?, version = version + 1
                WHERE person_id = ?
                """, person.firstName(), person.lastName(), person.status().name(), timestamp(now), person.id().value());
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO loans.people
                    (person_id, first_name, last_name, status, created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, 0)
                    """, person.id().value(), person.firstName(), person.lastName(), person.status().name(),
                    timestamp(person.createdAt()), timestamp(now));
            jdbcTemplate.update("""
                    INSERT INTO loans.person_emails
                    (person_email_id, person_id, original_email, normalized_email, is_primary, created_at)
                    VALUES (?, ?, ?, ?, true, ?)
                    """, UUID.randomUUID(), person.id().value(), person.primaryEmail().value(),
                    person.primaryEmail().value(), timestamp(person.createdAt()));
        }
        return person;
    }

    @Override
    public void delete(PersonId id) {
        jdbcTemplate.update("DELETE FROM loans.person_emails WHERE person_id = ?", id.value());
        jdbcTemplate.update("DELETE FROM loans.people WHERE person_id = ?", id.value());
    }

    @Override
    public boolean exists(PersonId id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM loans.people WHERE person_id = ?)", Boolean.class, id.value()));
    }

    private Optional<Person> queryPerson(String sql, Object argument) {
        List<Person> people = jdbcTemplate.query(sql, (resultSet, rowNumber) -> Person.rehydrate(
                new PersonId(resultSet.getObject("person_id", UUID.class)),
                new EmailAddress(resultSet.getString("normalized_email")),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                PersonStatus.valueOf(resultSet.getString("status")),
                instant(resultSet, "created_at")), argument);
        return people.stream().findFirst();
    }
}
