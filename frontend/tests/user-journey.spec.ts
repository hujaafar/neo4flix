import { test, expect } from './fixtures';
import { randomBytes } from 'node:crypto';
import { setDefaultResultOrder } from 'node:dns';
setDefaultResultOrder('ipv4first');

let cleanupAccount: { email: string; password: string } | null = null;
test.afterEach(async ({ request }) => {
  if (!cleanupAccount) return;
  const login = await request.post('/api/auth/login', {
    headers: { Origin: process.env['NEO4FLIX_TEST_URL'] || 'https://localhost:8443' },
    data: { ...cleanupAccount, code: '' },
  });
  if (login.ok()) {
    const session = await login.json();
    await request.delete('/api/users/me', {
      headers: { Authorization: 'Bearer ' + session.accessToken },
      data: { password: cleanupAccount.password, code: '' },
    });
  }
  cleanupAccount = null;
});

test('register, discover, rate, watchlist, share, refresh, and delete account', async ({
  page,
}, testInfo) => {
  const failures: string[] = [];
  page.on('pageerror', (e) => failures.push(e.message));
  const email = 'browser-' + randomBytes(6).toString('hex') + '@example.test';
  const password = 'BrowserTest!' + randomBytes(8).toString('hex');
  cleanupAccount = { email, password };
  await page.goto('/register');
  await page.getByLabel('Your name').fill('Cinema Explorer');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Something worth watching.' })).toBeVisible();
  await expect(page.locator('movie-card')).toHaveCount(18);
  await expect(page.locator('.movie-grid')).toHaveCSS('display', 'grid');
  await expect(page.locator('body')).not.toHaveClass(/error/);
  // Read through the collection before photographing its one-shot entrances.
  const pageHeight = await page.evaluate(() => document.body.scrollHeight);
  for (let y = 0; y < pageHeight; y += 700) {
    await page.evaluate((top) => window.scrollTo(0, top), y);
    await page.waitForTimeout(40);
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(550);
  await page.screenshot({ path: testInfo.outputPath('discovery.png'), fullPage: true });
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth > window.innerWidth + 2,
  );
  expect(overflow).toBe(false);
  await page.getByRole('textbox', { name: 'Search movies' }).fill('Arrival');
  await expect(page.locator('movie-card')).toHaveCount(1);
  await page.getByRole('link', { name: 'View Arrival', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Arrival', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '+ Add to watchlist', exact: true }).click();
  await expect(
    page.getByRole('button', { name: '✓ In your watchlist', exact: true }),
  ).toBeVisible();
  await page.getByRole('radio', { name: '5 out of 5 stars' }).check();
  await page.getByLabel(/Your notes/).fill('A thoughtful film with a beautiful sense of time.');
  await page.getByRole('button', { name: 'Save rating', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Update rating', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Share this film' }).click();
  await page.getByRole('button', { name: 'Create a share link' }).click();
  await expect(page.getByLabel('Share link', { exact: true })).toHaveValue(/\/share\//);
  const shareUrl = await page.getByLabel('Share link', { exact: true }).inputValue();
  await page.goto(shareUrl);
  await expect(page.getByRole('heading', { name: 'A film for your evening.' })).toBeVisible();
  await page.getByRole('link', { name: 'Watchlist', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'For another evening.' })).toBeVisible();
  await expect(page.locator('movie-card')).toHaveCount(1);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'For another evening.' })).toBeVisible();
  await expect(page.locator('movie-card')).toHaveCount(1);
  await page.getByRole('link', { name: 'My ratings', exact: true }).click();
  await expect(page.getByText('A thoughtful film with a beautiful sense of time.')).toBeVisible();
  await page.getByRole('link', { name: 'For you', exact: true }).click();
  await expect(
    page.getByRole('heading', { name: 'Made for your kind of movie night.' }),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: 'View Arrival', exact: true })).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('recommendations.png'), fullPage: true });
  await page.goto('/account');
  await expect(page.getByRole('heading', { name: 'Behind the profile.' })).toBeVisible();
  await page.getByRole('main').getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Take your seat.' })).toBeVisible();
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Something worth watching.' })).toBeVisible();
  await page.goto('/account');
  const deletePanel = page
    .locator('section')
    .filter({ has: page.getByRole('heading', { name: 'Delete account', exact: true }) });
  await deletePanel.getByLabel('Current password', { exact: true }).fill(password);
  page.once('dialog', (dialog) => dialog.accept());
  await deletePanel.getByRole('button', { name: 'Delete my account' }).click();
  await expect(page.getByRole('heading', { name: 'Take your seat.' })).toBeVisible();
  cleanupAccount = null;
  expect(failures).toEqual([]);
});
