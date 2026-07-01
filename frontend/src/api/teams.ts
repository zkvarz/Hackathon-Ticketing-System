// Team API calls (architecture.md §8). Typed wrappers over the shared client.

import { apiClient } from './client';
import type { components } from './schema';

// Derived from the OpenAPI spec (HTS-050) — mirrors the backend TeamResponse exactly.
export type Team = components['schemas']['TeamResponse'];

export function listTeams() {
  return apiClient.get<Team[]>('/teams');
}

export function createTeam(name: string) {
  return apiClient.post<Team>('/teams', { name });
}

export function renameTeam(id: string, name: string) {
  return apiClient.put<Team>(`/teams/${id}`, { name });
}

export function deleteTeam(id: string) {
  return apiClient.delete<void>(`/teams/${id}`);
}

/** True when the team still has epics or tickets and so cannot be deleted (FR-T5). */
export function isReferenced(team: Team): boolean {
  return team.epicCount + team.ticketCount > 0;
}
