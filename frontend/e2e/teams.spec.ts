// Optional — team creation and the delete-when-referenced guard (FR-T5). A team with a ticket has
// its Delete disabled in the UI; an empty team deletes cleanly. Self-contained via the shared
// session.

import { test, expect } from '@playwright/test';
import { createTeam, createTicket, uniqueName } from './helpers';

test('a referenced team cannot be deleted, an empty one can (optional)', async ({ page }) => {
  // A team with a ticket is referenced → Delete is disabled.
  const referenced = await createTeam(page);
  await createTicket(page, { team: referenced, title: uniqueName('Ref') });
  await page.goto('/teams');
  const referencedRow = page.getByRole('row', { name: new RegExp(referenced) });
  await expect(referencedRow.getByRole('button', { name: 'Delete' })).toBeDisabled();

  // A fresh, empty team can be deleted.
  const empty = await createTeam(page);
  const emptyRow = page.getByRole('row', { name: new RegExp(empty) });
  await emptyRow.getByRole('button', { name: 'Delete' }).click();
  await emptyRow.getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByRole('cell', { name: empty, exact: true })).toHaveCount(0);
});
