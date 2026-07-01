// Ticket activity-history API (architecture.md §8; HTS-042 stretch). Append-only log of a ticket's
// changes, read-only from the frontend — one entry per changed field on create/edit and per state
// transition (HTS-041). `field` is a canonical field name (or "created"); old/new values are
// human-readable strings captured at change time (null for the creation entry).

import { apiClient } from './client';
import type { components } from './schema';

// Derived from the OpenAPI spec (HTS-050) — mirrors the backend TicketActivityResponse.
export type TicketActivity = components['schemas']['TicketActivityResponse'];

export function listActivity(ticketId: string) {
  return apiClient.get<TicketActivity[]>(`/tickets/${ticketId}/activity`);
}
