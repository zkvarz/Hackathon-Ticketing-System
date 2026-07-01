package com.dataart.tickets.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    // Board/list order is most-recently-modified first (FR-B7). The creator is fetched in the same
    // query (open-in-view is off) so the response can expose its email (AMB-8) after the tx closes.
    @EntityGraph(attributePaths = "createdBy")
    List<Ticket> findByTeam_IdOrderByModifiedAtDesc(UUID teamId);

    // Override the standard lookup to eagerly load the creator so its email is available after the
    // transaction closes (open-in-view is off).
    @EntityGraph(attributePaths = "createdBy")
    @Override
    Optional<Ticket> findById(UUID id);

    long countByTeam_Id(UUID teamId);

    long countByEpic_Id(UUID epicId);
}
