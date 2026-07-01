// Comment API calls (architecture.md §8). Comments are nested under a ticket and immutable
// (FR-C6): only list + add. The author's email is shown as identity (AMB-8).

import { apiClient } from './client';

export interface Comment {
  id: string;
  ticketId: string;
  authorId: string;
  authorEmail: string;
  body: string;
  createdAt: string;
}

export function listComments(ticketId: string) {
  return apiClient.get<Comment[]>(`/tickets/${ticketId}/comments`);
}

export function addComment(ticketId: string, body: string) {
  return apiClient.post<Comment>(`/tickets/${ticketId}/comments`, { body });
}
