// DoD-1 — the full account journey: sign up, obtain the verification link from Mailpit, verify, and
// log in. This spec exercises login from scratch, so it opts out of the shared stored session.

import { test, expect } from '@playwright/test';
import { registerVerifyLogin } from './helpers';

test.use({ storageState: { cookies: [], origins: [] } });

test('sign up → verify via Mailpit → log in (DoD-1)', async ({ page, request }) => {
  const email = await registerVerifyLogin(page, request);

  // Logged in: the board is shown and the header carries the account email.
  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
  await expect(page.getByText(email)).toBeVisible();
});
