package com.dataart.tickets.epic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EpicRepository extends JpaRepository<Epic, UUID> {

    // Nested path: Epic.team.id (the entity holds a Team association, not a teamId scalar).
    List<Epic> findByTeam_IdOrderByTitleAsc(UUID teamId);

    long countByTeam_Id(UUID teamId);
}
