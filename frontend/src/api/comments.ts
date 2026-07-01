// Comment API calls (architecture.md §8). Comments are nested under a ticket. List + add are the
// base feature (HTS-024); the EP-09 stretch (HTS-040) adds author-only edit/delete. The author's
// email is shown as identity (AMB-8); editedAt is null until the author edits (HTS-039).

import { apiClient } from './client';
import type { components } from './schema';

// Derived from the OpenAPI spec (HTS-050) — mirrors the backend CommentResponse (editedAt nullable).
export type Comment = components['schemas']['CommentResponse'];

export function listComments(ticketId: string) {
  return apiClient.get<Comment[]>(`/tickets/${ticketId}/comments`);
}

export function addComment(ticketId: string, body: string) {
  return apiClient.post<Comment>(`/tickets/${ticketId}/comments`, { body });
}

/** Edit the author's own comment (HTS-040). 403 if not the author (shouldn't normally occur). */
export function editComment(ticketId: string, commentId: string, body: string) {
  return apiClient.put<Comment>(`/tickets/${ticketId}/comments/${commentId}`, { body });
}

/** Delete the author's own comment (HTS-040). */
export function deleteComment(ticketId: string, commentId: string) {
  return apiClient.delete<void>(`/tickets/${ticketId}/comments/${commentId}`);
}
