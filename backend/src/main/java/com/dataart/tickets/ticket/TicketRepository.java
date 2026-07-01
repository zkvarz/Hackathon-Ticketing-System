package com.dataart.tickets.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    // Board/list order is most-recently-modified first, with a UUIDv7 id tie-break so the order is
    // stable when two tickets share a modified_at (FR-B7). creator + epic are fetched in the same
    // query (open-in-view is off) so the response can expose the creator email and epic title
    // after the tx closes (AMB-8, board card needs the epic). Backed by ix_tickets_team_modified
    // for the 100+ ticket bar (FR-B10).
    @EntityGraph(attributePaths = {"createdBy", "epic"})
    List<Ticket> findByTeam_IdOrderByModifiedAtDescIdDesc(UUID teamId);

    // Override the standard lookup to eagerly load creator + epic so their fields are available
    // after the transaction closes (open-in-view is off).
    @EntityGraph(attributePaths = {"createdBy", "epic"})
    @Override
    Optional<Ticket> findById(UUID id);

    long countByTeam_Id(UUID teamId);

    long countByEpic_Id(UUID epicId);
}
