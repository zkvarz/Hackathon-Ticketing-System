// Setup project (HTS-036): register + verify + log in one shared user and persist the session, so
// the ticket/board specs reuse it instead of re-running the (slow) email verification each time.

import { test as setup } from '@playwright/test';
import { registerVerifyLogin } from './helpers';
import { STORAGE_STATE } from '../playwright.config';

setup('authenticate a shared user', async ({ page, request }) => {
  await registerVerifyLogin(page, request);
  await page.context().storageState({ path: STORAGE_STATE });
});
