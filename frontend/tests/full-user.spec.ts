import { test, expect } from './fixtures';
import type { Page } from '@playwright/test';
import { createHmac, randomBytes } from 'node:crypto';
import { request as httpsRequest } from 'node:https';

test.use({ trace: 'off', screenshot: 'off' });

type Account = { email: string; password: string; secret?: string; counter?: number };
const accounts: Account[] = [];

function totp(secret: string, counter = Math.floor(Date.now() / 30000)) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  const bits = [...secret].map((c) => alphabet.indexOf(c).toString(2).padStart(5, '0')).join('');
  const key = Buffer.from(bits.match(/.{8}/g)!.map((b) => parseInt(b, 2)));
  const time = Buffer.alloc(8);
  time.writeBigUInt64BE(BigInt(counter));
  const hash = createHmac('sha1', key).update(time).digest();
  const offset = hash[hash.length - 1] & 15;
  return ((hash.readUInt32BE(offset) & 0x7fffffff) % 1000000).toString().padStart(6, '0');
}

async function nextCode(account: Account) {
  // A real authenticator changes codes every 30 seconds; consumed codes cannot be replayed.
  await expect
    .poll(() => Math.floor(Date.now() / 30000), { timeout: 35000, intervals: [500] })
    .toBeGreaterThan(account.counter ?? 0);
  account.counter = Math.floor(Date.now() / 30000);
  return totp(account.secret!, account.counter);
}

async function register(page: Page, label = 'Explorer', withValidation = false) {
  const account: Account = {
    email: 'full-ui-' + randomBytes(6).toString('hex') + '@example.test',
    password: 'UserJourney!7' + randomBytes(8).toString('hex'),
  };
  await page.goto('/register');
  await page.getByLabel('Your name').fill(label);
  await page.getByLabel('Email address').fill(account.email);
  if (withValidation) {
    await page.getByLabel('Password', { exact: true }).fill('short');
    await expect(page.getByRole('button', { name: 'Create account' })).toBeDisabled();
    await page.getByLabel('Password', { exact: true }).fill('abcdefghijklmnop');
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page.getByRole('alert')).toBeVisible();
  }
  await page.getByLabel('Password', { exact: true }).fill(account.password);
  const response = page.waitForResponse(
    (r) => r.url().endsWith('/api/auth/register') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Create account' }).click();
  expect((await response).status()).toBe(201);
  accounts.push(account);
  return account;
}

async function login(page: Page, account: Account, password = account.password) {
  await page.getByLabel('Email address').fill(account.email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
}

async function cleanup(account: Account) {
  const call = (path: string, method: string, body: unknown, token = '') =>
    new Promise<{ status: number; body: any }>((resolve, reject) => {
      const base = process.env['NEO4FLIX_TEST_URL'] || 'https://localhost:8443';
      const encoded = JSON.stringify(body);
      const request = httpsRequest(
        new URL(path, base),
        {
          family: 4,
          method,
          headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(encoded),
            Origin: base,
            ...(token ? { Authorization: 'Bearer ' + token } : {}),
          },
        },
        (response) => {
          let raw = '';
          response.on('data', (chunk) => (raw += chunk));
          response.on('end', () =>
            resolve({ status: response.statusCode!, body: raw ? JSON.parse(raw) : null }),
          );
        },
      );
      request.on('error', reject);
      request.end(encoded);
    });
  const code = account.secret ? await nextCode(account) : '';
  const loggedIn = await call('/api/auth/login', 'POST', {
    email: account.email,
    password: account.password,
    code,
  });
  expect(loggedIn.status, 'Disposable account cleanup login').toBe(200);
  const deleted = await call(
    '/api/users/me',
    'DELETE',
    { password: account.password, code: account.secret ? await nextCode(account) : '' },
    loggedIn.body.accessToken,
  );
  expect(deleted.status, 'Disposable account cleanup').toBe(204);
}

test.afterEach(async () => {
  const failures: unknown[] = [];
  for (const account of accounts.splice(0)) {
    try {
      await cleanup(account);
    } catch (error) {
      failures.push(error);
    }
  }
  if (failures.length) throw new AggregateError(failures, 'Test account cleanup failed');
});

test('share from empty collection, receive after signup, edit, copy, and revoke', async ({
  page,
  browser,
}, info) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  const owner = await register(page, 'Sharing Explorer');
  await page.getByRole('link', { name: 'Shared picks', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Found a film for a friend?' })).toBeVisible();
  await expect(page.locator('[aria-busy=true]')).toHaveCount(0);
  await page.getByRole('link', { name: 'Discover films', exact: true }).click();
  await page.getByRole('link', { name: 'Browse films', exact: true }).click();
  await page.getByRole('textbox', { name: 'Search movies' }).fill('Arrival');
  await expect(page.locator('movie-card')).toHaveCount(1);
  await page.getByRole('link', { name: 'View Arrival', exact: true }).click();
  await page.getByRole('radio', { name: '4 out of 5 stars' }).check();
  const privateNote = 'Private diary ' + randomBytes(5).toString('hex');
  await page.getByLabel(/Your notes/).fill(privateNote);
  await page.getByRole('button', { name: 'Save rating', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Update rating', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Share this film' }).click();
  const note = page.getByLabel('A note for your friend');
  await expect(note).toBeInViewport();
  await expect(note).toBeFocused();
  await note.fill('A film for our next movie night.');
  await page.getByRole('button', { name: 'Create a share link', exact: true }).click();
  await expect(page.getByLabel('Share link', { exact: true })).toHaveValue(/\/share\//);
  const url = await page.getByLabel('Share link', { exact: true }).inputValue();
  const friendContext = await browser.newContext({
    viewport: page.viewportSize()!,
    ignoreHTTPSErrors: false,
  });
  try {
    const friend = await friendContext.newPage();
    await friend.goto(url);
    await expect(friend).toHaveURL(/\/login\?returnUrl=/);
    await friend.getByRole('link', { name: 'Create an account', exact: true }).click();
    const recipient: Account = {
      email: 'friend-ui-' + randomBytes(6).toString('hex') + '@example.test',
      password: 'FriendJourney!7' + randomBytes(8).toString('hex'),
    };
    await friend.getByLabel('Your name').fill('Movie Friend');
    await friend.getByLabel('Email address').fill(recipient.email);
    await friend.getByLabel('Password', { exact: true }).fill(recipient.password);
    const created = friend.waitForResponse(
      (r) => r.url().endsWith('/api/auth/register') && r.request().method() === 'POST',
    );
    await friend.getByRole('button', { name: 'Create account' }).click();
    expect((await created).status()).toBe(201);
    accounts.push(recipient);
    await expect(friend).toHaveURL(url);
    await expect(friend.getByRole('heading', { name: 'A film for your evening.' })).toBeVisible();
    await expect(
      friend.getByText('A film for our next movie night.', { exact: true }),
    ).toBeVisible();
    await expect(friend.locator('body')).not.toContainText(privateNote);
    await expect(friend.locator('body')).not.toContainText(owner.email);
    await page.getByRole('link', { name: 'Shared picks', exact: true }).click();
    await page
      .getByLabel('Your note', { exact: true })
      .fill('Updated: Arrival is worth watching twice.');
    await page.getByRole('button', { name: 'Save note', exact: true }).click();
    await expect(page.getByRole('status')).toHaveText('Your note has been updated.');
    await page.getByRole('button', { name: 'Copy link', exact: true }).click();
    await expect(page.getByRole('status')).toHaveText(/Link copied|copy the address/);
    await friend.reload();
    await expect(
      friend.getByText('Updated: Arrival is worth watching twice.', { exact: true }),
    ).toBeVisible();
    await friend.screenshot({ path: info.outputPath('recipient.png'), fullPage: true });
    await friend.getByRole('link', { name: 'Shared picks', exact: true }).click();
    await expect(friend.getByRole('heading', { name: 'Found a film for a friend?' })).toBeVisible();
    await friend.goto(url);
    page.once('dialog', (dialog) => dialog.dismiss());
    await page.getByRole('button', { name: 'Revoke link' }).click();
    await expect(page.locator('.shared-row')).toHaveCount(1);
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: 'Revoke link' }).click();
    await expect(page.getByRole('status')).toHaveText('Share link revoked.');
    await expect(page.locator('.shared-row')).toHaveCount(0);
    await friend.reload();
    await expect(friend.getByRole('alert')).toContainText('unavailable or has been revoked');
    await expect(friend.locator('movie-card')).toHaveCount(0);
    await friend.screenshot({ path: info.outputPath('revoked.png') });
  } finally {
    await friendContext.close();
  }
  expect(errors).toEqual([]);
});

test('search dates and genres, edit ratings, remove watchlist, and hide or restore recommendations', async ({
  page,
}, info) => {
  await register(page, 'Catalogue Explorer', true);
  await page.getByRole('link', { name: 'Browse films', exact: true }).click();
  const search = page.getByRole('textbox', { name: 'Search movies' });
  await search.fill('aRrIvAl');
  await expect(page.locator('movie-card')).toHaveCount(1);
  await search.fill('Animation');
  await expect(page.locator('movie-card')).toHaveCount(3);
  await search.fill('2014');
  await expect(page.locator('movie-card')).toHaveCount(3);
  await search.fill('does-not-exist-xyz');
  await expect(page.getByRole('heading', { name: 'A different scene, perhaps?' })).toBeVisible();
  await page.getByRole('button', { name: 'Reset filters' }).click();
  await expect(page.locator('movie-card')).toHaveCount(18);
  await page.getByRole('button', { name: 'Science Fiction', exact: true }).click();
  await page.getByRole('button', { name: 'Release dates' }).click();
  await page.getByLabel('Released from').fill('2016-01-01');
  await page.getByLabel('Released through').fill('2016-12-31');
  await expect(page.locator('movie-card')).toHaveCount(1);
  await page.getByLabel('Released from').fill('2025-01-01');
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('button', { name: 'Clear dates' }).click();
  await expect(page.getByRole('alert')).toHaveCount(0);
  await search.fill('Arrival');
  await expect(page.locator('movie-card')).toHaveCount(1);
  await page.getByRole('link', { name: 'View Arrival', exact: true }).click();
  await expect(page.getByText('November 11, 2016', { exact: true })).toBeVisible();
  await expect(page.getByText('Denis Villeneuve', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save rating', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: '+ Add to watchlist', exact: true }).click();
  await expect(
    page.getByRole('button', { name: '✓ In your watchlist', exact: true }),
  ).toBeVisible();
  await page.getByRole('radio', { name: '5 out of 5 stars' }).check();
  await page.getByLabel(/Your notes/).fill('<b>A private thought</b>');
  await page.getByRole('button', { name: 'Save rating', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Update rating', exact: true })).toBeVisible();
  await page.getByRole('radio', { name: '2 out of 5 stars' }).check();
  await page.getByRole('button', { name: 'Update rating', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Rating saved');
  await page.reload();
  await expect(page.getByRole('radio', { name: '2 out of 5 stars' })).toBeChecked();
  await expect(page.getByLabel(/Your notes/)).toHaveValue('<b>A private thought</b>');
  await page.getByRole('link', { name: 'My ratings', exact: true }).click();
  await expect(page.getByText('<b>A private thought</b>', { exact: true })).toBeVisible();
  await expect(page.locator('.review-text b')).toHaveCount(0);
  await page.getByRole('link', { name: 'Watchlist', exact: true }).click();
  await expect(page.locator('movie-card')).toHaveCount(1);
  await page.getByRole('button', { name: 'Remove Arrival', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Your next movie night starts here.' }),
  ).toBeVisible();
  await page.reload();
  await expect(page.locator('movie-card')).toHaveCount(0);
  await page.getByRole('link', { name: 'For you', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Made for your kind of movie night.' }),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: 'View Arrival', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Animation', exact: true }).click();
  await expect(page.locator('movie-card')).toHaveCount(3);
  await page.getByRole('button', { name: 'Release dates' }).click();
  await page.getByLabel('Released from').fill('2020-01-01');
  await expect(page.locator('movie-card')).toHaveCount(1);
  await expect(page.getByRole('link', { name: 'View Soul', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Remove Soul', exact: true }).click();
  await expect(page.locator('movie-card')).toHaveCount(0);
  await page.getByRole('button', { name: 'Hidden picks', exact: true }).click();
  await expect(page.locator('.list-row')).toContainText('Soul');
  await page.getByRole('button', { name: 'Show again', exact: true }).click();
  await expect(page.getByRole('link', { name: 'View Soul', exact: true })).toBeVisible();
  await expect(page.getByText('No hidden films.', { exact: true })).toBeVisible();
  await page.screenshot({ path: info.outputPath('filtered-recommendations.png'), fullPage: true });
  await page.getByRole('link', { name: 'My ratings', exact: true }).click();
  await page.getByRole('link', { name: 'Edit rating' }).click();
  await page.getByRole('button', { name: 'Delete rating', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Rating removed.');
  await page.getByRole('link', { name: 'My ratings', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Every opinion opens a door.' })).toBeVisible();
});

test('profile and password changes plus complete authenticator setup and removal', async ({
  page,
}) => {
  test.setTimeout(240000);
  // Authenticator setup keys must not be stored in browser traces or screenshots.
  const account = await register(page, 'Security Explorer');
  await page.getByRole('link', { name: /Account & security/ }).click();
  await page.getByLabel('Display name').fill('');
  await expect(page.getByRole('button', { name: 'Save profile', exact: true })).toBeDisabled();
  await page.getByLabel('Display name').fill('Updated Explorer');
  await page.getByRole('button', { name: 'Save profile', exact: true }).click();
  await expect(page.getByRole('status')).toHaveText('Profile saved.');
  await page.reload();
  await expect(page.getByLabel('Display name')).toHaveValue('Updated Explorer');
  const changePanel = page
    .locator('section.panel')
    .filter({ has: page.getByRole('heading', { name: 'Change password', exact: true }) });
  await changePanel.getByLabel('Current password', { exact: true }).fill(account.password);
  await changePanel.getByLabel('New password', { exact: true }).fill('abcdefghijklmnop');
  await changePanel.getByRole('button', { name: 'Change password', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  const previousPassword = account.password;
  const newPassword = 'ChangedJourney!7' + randomBytes(8).toString('hex');
  await changePanel.getByLabel('New password', { exact: true }).fill(newPassword);
  const changed = page.waitForResponse(
    (r) => r.url().endsWith('/api/users/me/password') && r.request().method() === 'PUT',
  );
  await changePanel.getByRole('button', { name: 'Change password', exact: true }).click();
  const changedStatus = (await changed).status();
  if (changedStatus === 204) account.password = newPassword;
  expect(changedStatus).toBe(204);
  await expect(page).toHaveURL(/\/login$/);
  await login(page, account, previousPassword);
  await expect(page.getByRole('alert')).toBeVisible();
  await login(page, account);
  await page.getByRole('link', { name: /Account & security/ }).click();
  const security = page
    .locator('section.panel')
    .filter({ has: page.getByRole('heading', { name: 'Two-factor authentication', exact: true }) });
  await security.getByLabel('Current password', { exact: true }).fill(account.password);
  await security.getByRole('button', { name: 'Set up authenticator', exact: true }).click();
  await expect(page.locator('code.secret')).toBeVisible();
  const secret = (await page.locator('code.secret').textContent())!;
  const actual = totp(secret);
  const invalid = ((Number(actual) + 1) % 1000000).toString().padStart(6, '0');
  await page.getByLabel('Code from your authenticator', { exact: true }).fill(invalid);
  await page.getByRole('button', { name: 'Confirm and enable', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByLabel('Code from your authenticator', { exact: true }).fill(totp(secret));
  const confirmed = page.waitForResponse(
    (r) => r.url().endsWith('/api/users/me/2fa/confirm') && r.request().method() === 'POST',
  );
  await page.getByRole('button', { name: 'Confirm and enable', exact: true }).click();
  const confirmedStatus = (await confirmed).status();
  if (confirmedStatus === 204) {
    account.secret = secret;
    account.counter = Math.floor(Date.now() / 30000);
  }
  expect(confirmedStatus).toBe(204);
  await expect(page).toHaveURL(/\/login$/);
  await login(page, account);
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByText('Using two-factor authentication?', { exact: true }).click();
  await page.getByLabel('Authenticator code', { exact: true }).fill(await nextCode(account));
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await page.getByRole('link', { name: /Account & security/ }).click();
  await expect(security.locator('.status-pill')).toHaveText('Enabled');
  await security.getByLabel('Current password', { exact: true }).fill(account.password);
  await security.getByLabel('Authenticator code', { exact: true }).fill(await nextCode(account));
  const disabled = page.waitForResponse(
    (r) => r.url().endsWith('/api/users/me/2fa') && r.request().method() === 'DELETE',
  );
  await security.getByRole('button', { name: 'Disable 2FA', exact: true }).click();
  expect((await disabled).status()).toBe(204);
  account.secret = undefined;
  await expect(page).toHaveURL(/\/login$/);
  await login(page, account);
  await page.getByRole('link', { name: /Account & security/ }).click();
  await expect(security.locator('.status-pill')).toHaveText('Not enabled');
});

test('collection network errors offer recovery without claiming the collection is empty', async ({
  page,
}, info) => {
  await register(page, 'Recovery Explorer');
  for (const [link, path, heading] of [
    ['Shared picks', '/api/recommendations/shares', 'Found a film for a friend?'],
    ['Watchlist', '/api/users/me/watchlist', 'Your next movie night starts here.'],
    ['My ratings', '/api/users/me/ratings', 'Every opinion opens a door.'],
  ]) {
    // Only the failed response is simulated; retry loads the real Docker database.
    await page.route(
      '**' + path,
      (route) =>
        route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Temporarily unavailable. Please retry.' }),
        }),
      { times: 1 },
    );
    await page.getByRole('link', { name: link, exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('Temporarily unavailable');
    await expect(page.getByRole('heading', { name: heading, exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'Try again', exact: true }).click();
    await expect(page.getByRole('alert')).toHaveCount(0);
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
  }
  await page.goto('/movies/does-not-exist');
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByRole('link', { name: '← Back to discovery', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Something worth watching.' })).toBeVisible();
  await page.screenshot({ path: info.outputPath('recovered-discover.png') });
});
