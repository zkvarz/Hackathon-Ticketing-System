package com.dataart.tickets.ticket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketActivityRepository extends JpaRepository<TicketActivity, UUID> {

    // Chronological, oldest-first (AC-2). Ordered by the DB-assigned monotonic seq so rows written in
    // the same transaction (a multi-field edit shares one occurred_at) keep their insertion order —
    // a UUIDv7 id is only millisecond-ordered and cannot tie-break within a millisecond.
    List<TicketActivity> findByTicket_IdOrderBySeqAsc(UUID ticketId);
}
