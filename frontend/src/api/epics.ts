// Epic API calls (architecture.md §8). Typed wrappers over the shared client.

import { apiClient } from './client';
import type { components } from './schema';

// Response derived from the OpenAPI spec (HTS-050); the request body stays hand-written.
export type Epic = components['schemas']['EpicResponse'];

export interface EpicInput {
  title: string;
  description?: string;
}

export function listEpics(teamId: string) {
  return apiClient.get<Epic[]>(`/epics?teamId=${encodeURIComponent(teamId)}`);
}

export function createEpic(teamId: string, input: EpicInput) {
  return apiClient.post<Epic>(`/epics?teamId=${encodeURIComponent(teamId)}`, input);
}

export function updateEpic(id: string, input: EpicInput) {
  return apiClient.put<Epic>(`/epics/${id}`, input);
}

export function deleteEpic(id: string) {
  return apiClient.delete<void>(`/epics/${id}`);
}
