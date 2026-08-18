package com.project.backend.domain.repository;

import com.project.backend.domain.identity.Person;
import com.project.backend.domain.valueobject.EmailAddress;
import com.project.backend.domain.valueobject.PersonId;

import java.util.Optional;

public interface PersonRepository extends Repository<Person, PersonId> {

    Optional<Person> findByEmail(EmailAddress email);
}
