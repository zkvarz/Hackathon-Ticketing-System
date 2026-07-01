package com.dataart.tickets.ticket;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Board query with optional server-side filters (HTS-025 + HTS-029). Filters are AND-combined
     * and each is skipped when its parameter is null (FR-B9, AMB-10): {@code type}/{@code epicId}
     * match by equality, {@code q} is a case-insensitive title substring. Result is
     * most-recently-modified first with a UUIDv7 id tie-break for stable ordering (FR-B7), backed
     * by {@code ix_tickets_team_modified} for the 100+ ticket bar (FR-B10). creator + epic are
     * fetched in the same query (open-in-view is off) so the response can expose the creator email
     * and epic title after the tx closes (AMB-8).
     */
    @EntityGraph(attributePaths = {"createdBy", "epic"})
    @Query("""
            select t from Ticket t
            where t.team.id = :teamId
              and (:type is null or t.type = :type)
              and (:epicId is null or t.epic.id = :epicId)
              and (cast(:q as string) is null
                   or lower(t.title) like lower(concat('%', cast(:q as string), '%')))
            order by t.modifiedAt desc, t.id desc
            """)
    List<Ticket> search(@Param("teamId") UUID teamId, @Param("type") TicketType type,
                        @Param("epicId") UUID epicId, @Param("q") String q);

    // Override the standard lookup to eagerly load creator + epic so their fields are available
    // after the transaction closes (open-in-view is off).
    @EntityGraph(attributePaths = {"createdBy", "epic"})
    @Override
    Optional<Ticket> findById(UUID id);

    long countByTeam_Id(UUID teamId);

    long countByEpic_Id(UUID epicId);
}
