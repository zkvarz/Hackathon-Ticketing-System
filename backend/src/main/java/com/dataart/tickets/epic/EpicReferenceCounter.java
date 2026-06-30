package com.dataart.tickets.epic;

import java.util.UUID;

/**
 * Counts entities referencing an epic, so an epic cannot be deleted while tickets reference it
 * (FR-E8). Tickets (HTS-019) implement this; until then an epic is always deletable.
 */
public interface EpicReferenceCounter {

    String label();

    long countByEpic(UUID epicId);
}
