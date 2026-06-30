package com.dataart.tickets.epic.dto;

import com.dataart.tickets.epic.Epic;

import java.time.Instant;
import java.util.UUID;

/**
 * Epic view including a ticket count so the UI can show usage and disable delete when the epic
 * is referenced (FR-E8; HTS-018). Count is 0 until the ticket module registers its counter
 * (HTS-019).
 */
public record EpicResponse(
        UUID id,
        UUID teamId,
        String title,
        String description,
        long ticketCount,
        Instant createdAt,
        Instant modifiedAt
) {
    public static EpicResponse from(Epic epic, long ticketCount) {
        return new EpicResponse(epic.getId(), epic.getTeamId(), epic.getTitle(),
                epic.getDescription(), ticketCount, epic.getCreatedAt(), epic.getModifiedAt());
    }
}
