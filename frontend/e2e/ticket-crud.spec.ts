// DoD-3 — create, edit, and delete a ticket. Self-contained: creates its own team and ticket using
// the shared authenticated session.

import { test, expect } from '@playwright/test';
import { createTeam, createTicket, uniqueName } from './helpers';

test('create, edit, and delete a ticket (DoD-3)', async ({ page }) => {
  const team = await createTeam(page);
  const title = uniqueName('Ticket');

  // Create.
  await createTicket(page, { team, title });
  await expect(page.getByLabel('Title')).toHaveValue(title);

  // Edit the title and save.
  const edited = `${title}-edited`;
  await page.getByLabel('Title').fill(edited);
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Saved.')).toBeVisible();
  await expect(page.getByLabel('Title')).toHaveValue(edited);

  // Delete (with confirmation) → back to the board, and the ticket is gone.
  await page.getByRole('button', { name: 'Delete' }).click();
  await page.getByRole('button', { name: 'Confirm delete' }).click();
  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();

  await page.getByLabel('Team').selectOption({ label: team });
  await expect(page.getByText(edited)).toHaveCount(0);
});
