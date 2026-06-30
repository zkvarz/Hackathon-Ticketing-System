package com.dataart.tickets.team;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    boolean existsByNameIgnoreCase(String name);

    /** Uniqueness check for rename: another team (not this id) already uses the name. */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    default List<Team> findAllOrdered() {
        return findAll(Sort.by(Sort.Direction.ASC, "name"));
    }
}
