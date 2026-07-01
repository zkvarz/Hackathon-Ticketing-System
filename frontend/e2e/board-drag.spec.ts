// DoD-6 — drag a card to another column and confirm the move persists across a page reload.
// Self-contained: creates its own team and ticket using the shared authenticated session.

import { test, expect } from '@playwright/test';
import { createTeam, createTicket, dragCardToColumn, uniqueName } from './helpers';

test('drag a ticket to another column and it persists after refresh (DoD-6)', async ({ page }) => {
  const team = await createTeam(page);
  const title = uniqueName('Drag');
  await createTicket(page, { team, title }); // defaults to state "new"

  // Open the board for this team.
  await page.goto('/board');
  await page.getByLabel('Team').selectOption({ label: team });

  const newColumn = page.locator('.board__column[aria-label="New"]');
  const inProgress = page.locator('.board__column[aria-label="In progress"]');
  await expect(newColumn.locator('.ticket-card', { hasText: title })).toBeVisible();

  // Drag New → In progress.
  await dragCardToColumn(page, title, 'In progress');
  await expect(inProgress.locator('.ticket-card', { hasText: title })).toBeVisible();
  await expect(newColumn.locator('.ticket-card', { hasText: title })).toHaveCount(0);

  // Reload: the move was persisted server-side, so the card stays in In progress.
  await page.reload();
  await page.getByLabel('Team').selectOption({ label: team });
  await expect(inProgress.locator('.ticket-card', { hasText: title })).toBeVisible();
  await expect(newColumn.locator('.ticket-card', { hasText: title })).toHaveCount(0);
});
