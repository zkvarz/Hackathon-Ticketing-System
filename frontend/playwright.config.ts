// Playwright E2E config (HTS-036). The suite runs against the full docker-compose stack — the SPA
// on http://localhost:8081 (nginx, which reverse-proxies /api to the backend) with Mailpit on
// :8025 for reading verification emails. It does NOT start the stack; bring it up first:
//   docker compose up --build -d
//   cd frontend && npx playwright test
// Override the targets with PW_BASE_URL / MAILPIT_URL if your ports differ.
//
// Projects: a `setup` project registers + verifies + logs in one user and saves its storage state;
// the `e2e` project reuses that session (so most specs skip re-authenticating) but each spec still
// creates its own team/ticket. The full signup→verify→login journey (auth.spec) opts out of the
// stored session so it exercises login from scratch.

import { defineConfig, devices } from '@playwright/test';

export const BASE_URL = process.env.PW_BASE_URL ?? 'http://localhost:8081';
export const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://localhost:8025';
export const STORAGE_STATE = 'e2e/.auth/user.json';

export default defineConfig({
  testDir: './e2e',
  // A real browser + a live stack: give each action/test room, but never sleep blindly in specs.
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'e2e',
      testIgnore: /auth\.setup\.ts/,
      dependencies: ['setup'],
      use: { ...devices['Desktop Chrome'], storageState: STORAGE_STATE },
    },
  ],
});
