// Shared E2E helpers (HTS-036): unique test data, reading the verification link out of Mailpit's
// HTTP API, the signup→verify→login journey, and creating a team through the UI. Helpers use
// role/text selectors and Playwright's built-in auto-waiting/expect polling — no arbitrary sleeps.

import { expect, type Page, type APIRequestContext } from '@playwright/test';
import { MAILPIT_URL } from '../playwright.config';

/** A unique, valid email per run so specs never collide on the CI-unique email rule (FR-A2). */
export function uniqueEmail(prefix = 'e2e'): string {
  const rand = Math.random().toString(36).slice(2, 8);
  return `${prefix}-${Date.now()}-${rand}@example.com`;
}

/** A unique team name so specs don't clash on the CI-unique team name (FR-T4). */
export function uniqueName(prefix = 'Team'): string {
  const rand = Math.random().toString(36).slice(2, 6);
  return `${prefix}-${Date.now()}-${rand}`;
}

interface MailpitMessage {
  ID: string;
}

/**
 * Poll Mailpit for the newest message addressed to `email` and return the verification link it
 * contains. Uses Mailpit's search + message APIs (architecture.md §10). `expect.poll` retries until
 * the async email arrives (or the timeout trips), so there are no fixed sleeps.
 */
export async function getVerificationLink(request: APIRequestContext, email: string): Promise<string> {
  const messageId = await pollForMessageId(request, email);
  const res = await request.get(`${MAILPIT_URL}/api/v1/message/${messageId}`);
  expect(res.ok(), `Mailpit message ${messageId} should be readable`).toBeTruthy();
  const body = await res.json();
  const text: string = `${body.Text ?? ''}\n${body.HTML ?? ''}`;
  const match = text.match(/https?:\/\/[^\s"'<>]*\/verify\?token=[^\s"'<>]+/);
  expect(match, 'verification email should contain a /verify?token= link').not.toBeNull();
  return match![0];
}

async function pollForMessageId(request: APIRequestContext, email: string): Promise<string> {
  let id = '';
  await expect
    .poll(
      async () => {
        const res = await request.get(
          `${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`,
        );
        if (!res.ok()) return 0;
        const data = await res.json();
        const messages: MailpitMessage[] = data.messages ?? [];
        if (messages.length > 0) id = messages[0].ID;
        return messages.length;
      },
      { message: `waiting for a verification email to ${email}`, timeout: 20_000 },
    )
    .toBeGreaterThan(0);
  return id;
}

const PASSWORD = 'Sup3rSecret!';
export { PASSWORD as TEST_PASSWORD };

/**
 * Full account bootstrap: sign up, pull the verification link from Mailpit, verify, then log in.
 * Leaves the page on the board, authenticated. Returns the account's email.
 */
export async function registerVerifyLogin(page: Page, request: APIRequestContext): Promise<string> {
  const email = uniqueEmail();
  await signup(page, email);
  const link = await getVerificationLink(request, email);
  await page.goto(link);
  await expect(page.getByRole('heading', { name: /email verified/i })).toBeVisible();
  await login(page, email);
  return email;
}

export async function signup(page: Page, email: string): Promise<void> {
  await page.goto('/signup');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByLabel('Confirm password').fill(PASSWORD);
  await page.getByRole('button', { name: /create account/i }).click();
  await expect(page.getByRole('heading', { name: /check your email/i })).toBeVisible();
}

export async function login(page: Page, email: string): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: /^log in$/i }).click();
  // Landing on the board confirms the session was established.
  await expect(page.getByRole('heading', { name: 'Board' })).toBeVisible();
}

/** Create a team via the Teams screen and return its name. */
export async function createTeam(page: Page, name = uniqueName()): Promise<string> {
  await page.goto('/teams');
  await page.getByLabel('New team').fill(name);
  await page.getByRole('button', { name: 'Add team' }).click();
  await expect(page.getByRole('cell', { name, exact: true })).toBeVisible();
  return name;
}

/**
 * Create a ticket through the ticket form (for the given team), landing on its detail view. Returns
 * the ticket's detail URL. Type/state keep their defaults (bug / new), which suits the board specs.
 */
export async function createTicket(
  page: Page,
  opts: { team: string; title: string; body?: string },
): Promise<string> {
  await page.goto('/tickets/new');
  await page.getByLabel('Team').selectOption({ label: opts.team });
  await page.getByLabel('Title').fill(opts.title);
  await page.getByLabel('Description').fill(opts.body ?? 'Created by the E2E suite.');
  await page.getByRole('button', { name: 'Create ticket' }).click();
  await expect(page.getByRole('heading', { name: 'Ticket', exact: true })).toBeVisible();
  return page.url();
}

/**
 * Drag a card (found by its title) onto the column with the given state label, satisfying dnd-kit's
 * pointer activation distance with an initial small move before travelling to the target.
 */
export async function dragCardToColumn(page: Page, title: string, columnLabel: string): Promise<void> {
  const card = page.locator('.ticket-card', { hasText: title }).first();
  const column = page.locator(`.board__column[aria-label="${columnLabel}"]`);
  const from = await card.boundingBox();
  const to = await column.boundingBox();
  if (!from || !to) throw new Error('card or target column not found for drag');

  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
  await page.mouse.down();
  // Exceed the 5px activation distance so the drag actually starts.
  await page.mouse.move(from.x + from.width / 2 + 10, from.y + from.height / 2 + 10, { steps: 5 });
  // Travel to the target column (near its top) in steps so collision detection tracks it.
  await page.mouse.move(to.x + to.width / 2, to.y + 60, { steps: 12 });
  await page.mouse.move(to.x + to.width / 2, to.y + 60, { steps: 3 });
  await page.mouse.up();
}
