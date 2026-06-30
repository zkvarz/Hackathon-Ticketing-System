// Team API calls (architecture.md §8). Typed wrappers over the shared client.

import { apiClient } from './client';

export interface Team {
  id: string;
  name: string;
  epicCount: number;
  ticketCount: number;
  createdAt: string;
  modifiedAt: string;
}

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
