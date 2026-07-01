// DoD-2 (epics) — create, edit, and delete an epic through the Epics screen, plus the
// delete-when-referenced guard (FR-E8). Complements HTS-036, which only covered epics indirectly.
// Self-contained: creates its own team, epics, and (for the guard) a ticket that references an epic,
// all via the shared authenticated session.

import { test, expect } from '@playwright/test';
import { createEpic, createTeam, createTicket, uniqueName } from './helpers';

test('create, edit, and delete an epic; a referenced epic cannot be deleted (DoD-2)', async ({ page }) => {
  const team = await createTeam(page);

  // Create → the epic lists in the team's table.
  const title = uniqueName('Epic');
  await createEpic(page, { team, title });

  // Edit the title and save → the new title shows, the old one is gone.
  const edited = `${title}-edited`;
  await page.getByRole('row', { name: new RegExp(title) }).getByRole('button', { name: 'Edit' }).click();
  await page.getByLabel(`Edit title of ${title}`).fill(edited);
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByRole('cell', { name: edited, exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: title, exact: true })).toHaveCount(0);

  // Delete an empty epic → its row disappears.
  const disposable = uniqueName('Epic');
  await createEpic(page, { team, title: disposable });
  const disposableRow = page.getByRole('row', { name: new RegExp(disposable) });
  await disposableRow.getByRole('button', { name: 'Delete' }).click();
  await disposableRow.getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByRole('cell', { name: disposable, exact: true })).toHaveCount(0);

  // Referenced guard: an epic with a ticket has its Delete disabled (FR-E8 / DoD-2).
  const referenced = uniqueName('Epic');
  await createEpic(page, { team, title: referenced });
  await createTicket(page, { team, title: uniqueName('Ref'), epic: referenced });
  await page.goto('/epics');
  await page.getByLabel('Team').selectOption({ label: team });
  const referencedRow = page.getByRole('row', { name: new RegExp(referenced) });
  await expect(referencedRow.getByRole('button', { name: 'Delete' })).toBeDisabled();
});
