// Ticket API calls (architecture.md §8). Typed wrappers over the shared client, plus the
// canonical enum values and their human-readable labels (FR §6): the UI shows labels but always
// sends/receives the canonical wire values.

import { apiClient } from './client';
import type { components } from './schema';

// Response shape + the enum unions are derived from the OpenAPI spec (HTS-050) — the enum values
// come straight from the backend's @JsonValue wire strings, so they can't drift.
export type Ticket = components['schemas']['TicketResponse'];
export type TicketType = Ticket['type'];
export type TicketState = Ticket['state'];

// The request body stays hand-written: the client decides how to send an absent epic (null),
// which differs from the server's optional-field shape.
export interface TicketInput {
  teamId: string;
  epicId?: string | null;
  type: TicketType;
  state: TicketState;
  title: string;
  body: string;
}

export interface TicketFilters {
  type?: TicketType;
  epicId?: string;
  q?: string;
}

// Canonical order of the five workflow states (FR-B2) and their labels (FR §6).
export const TICKET_STATES: TicketState[] = [
  'new',
  'ready_for_implementation',
  'in_progress',
  'ready_for_acceptance',
  'done',
];

export const TICKET_STATE_LABELS: Record<TicketState, string> = {
  new: 'New',
  ready_for_implementation: 'Ready for implementation',
  in_progress: 'In progress',
  ready_for_acceptance: 'Ready for acceptance',
  done: 'Done',
};

export const TICKET_TYPES: TicketType[] = ['bug', 'feature', 'fix'];

export const TICKET_TYPE_LABELS: Record<TicketType, string> = {
  bug: 'Bug',
  feature: 'Feature',
  fix: 'Fix',
};

export function listTickets(teamId: string, filters: TicketFilters = {}) {
  const params = new URLSearchParams({ teamId });
  if (filters.type) params.set('type', filters.type);
  if (filters.epicId) params.set('epicId', filters.epicId);
  if (filters.q) params.set('q', filters.q);
  return apiClient.get<Ticket[]>(`/tickets?${params.toString()}`);
}

export function getTicket(id: string) {
  return apiClient.get<Ticket>(`/tickets/${id}`);
}

export function createTicket(input: TicketInput) {
  return apiClient.post<Ticket>('/tickets', input);
}

export function updateTicket(id: string, input: TicketInput) {
  return apiClient.put<Ticket>(`/tickets/${id}`, input);
}

export function changeTicketState(id: string, state: TicketState) {
  return apiClient.patch<Ticket>(`/tickets/${id}/state`, { state });
}

export function deleteTicket(id: string) {
  return apiClient.delete<void>(`/tickets/${id}`);
}
