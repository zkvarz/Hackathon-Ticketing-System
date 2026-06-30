package com.dataart.tickets.team;

import java.util.UUID;

/**
 * Counts entities that reference a team, so a team cannot be deleted while it has children
 * (FR-T5, no cascade). Each child type contributes a counter bean: epics (HTS-017) and tickets
 * (HTS-019) implement this. No implementations exist yet, so a team is currently always
 * deletable — the rule activates automatically as child modules are added.
 */
public interface TeamReferenceCounter {

    /** Stable label for this reference type, e.g. "epics" or "tickets". */
    String label();

    long countByTeam(UUID teamId);
}
