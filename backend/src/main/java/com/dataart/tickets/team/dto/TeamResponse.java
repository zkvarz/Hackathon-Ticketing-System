package com.dataart.tickets.team.dto;

import com.dataart.tickets.team.Team;

import java.time.Instant;
import java.util.UUID;

/**
 * Team view including reference counts so the UI can show usage and disable delete when the
 * team has children (FR-T5; HTS-016). Counts are 0 until the epic/ticket modules register their
 * counters (HTS-017/HTS-019).
 */
public record TeamResponse(
        UUID id,
        String name,
        long epicCount,
        long ticketCount,
        Instant createdAt,
        Instant modifiedAt
) {
    public static TeamResponse from(Team team, long epicCount, long ticketCount) {
        return new TeamResponse(team.getId(), team.getName(), epicCount, ticketCount,
                team.getCreatedAt(), team.getModifiedAt());
    }
}
