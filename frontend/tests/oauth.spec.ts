import { test, expect } from './fixtures';

// These verify UI contracts only. Signed OIDC/code/state validation is covered by the Java provider tests.
test.use({ trace: 'off', screenshot: 'off' });

test('Google availability, callback failure and expired proof give clear choices', async ({
  page,
}, testInfo) => {
  await page.route('**/api/auth/oauth2/providers', (route) =>
    route.fulfill({ json: [{ id: 'google', name: 'Google', enabled: false }] }),
  );
  await page.goto('/login');
  await expect(page.getByRole('button', { name: 'Continue with Google' })).toBeDisabled();
  await expect(page.getByText('Google sign-in is awaiting setup.')).toBeVisible();
  await page.goto('/login?oauthError=google');
  await expect(page.getByRole('alert')).toContainText('cancelled or could not be verified');
  await page.route('**/api/auth/oauth2/pending', (route) =>
    route.fulfill({
      status: 401,
      json: { message: 'Google sign-in expired. Please start again.' },
    }),
  );
  await page.goto('/oauth2/complete');
  await expect(page.getByRole('alert')).toContainText('expired');
  await page.getByRole('link', { name: 'Back to sign in' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.screenshot({ path: testInfo.outputPath('google-login.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
});

test('Google registration explains password and existing account linking requires password plus 2FA', async ({
  page,
}, testInfo) => {
  let mode = 'register';
  let completed = 0;
  await page.route('**/api/auth/oauth2/pending', (route) =>
    route.fulfill({
      json: {
        mode,
        email: 'oauth-ui@example.test',
        name: 'OAuth Viewer',
        twoFactor: mode === 'link',
        returnUrl: '/watchlist',
      },
    }),
  );
  await page.route('**/api/auth/oauth2/complete', (route) => {
    completed++;
    return route.fulfill({
      status: 401,
      json: { message: 'Password or authenticator code is incorrect' },
    });
  });
  await page.goto('/oauth2/complete');
  await expect(page.getByRole('heading', { name: 'Welcome to your cinema.' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Create account' })).toBeDisabled();
  await page.getByLabel('Create a Neo4flix password').fill('DemoOnlyPassword!123');
  await expect(page.getByRole('button', { name: 'Create account' })).toBeEnabled();
  mode = 'link';
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Connect your account.' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Connect Google and sign in' })).toBeDisabled();
  await page.getByLabel('Neo4flix password', { exact: true }).fill('DemoOnlyPassword!123');
  await page.getByLabel('Authenticator code').fill('123456');
  await page.getByRole('button', { name: 'Connect Google and sign in' }).click();
  await expect(page.getByRole('alert')).toContainText('incorrect');
  expect(completed).toBe(1);
  await page.getByLabel('Neo4flix password', { exact: true }).clear();
  await page.getByLabel('Authenticator code').clear();
  await page.screenshot({ path: testInfo.outputPath('google-linking.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
});

test('connected Google login pauses for authenticator and cancellation returns to login', async ({
  page,
}) => {
  let completed = 0;
  await page.route('**/api/auth/oauth2/pending', (route) =>
    route.fulfill({
      json: {
        mode: 'login',
        email: 'oauth-ui@example.test',
        name: 'OAuth Viewer',
        twoFactor: true,
        returnUrl: '/watchlist',
      },
    }),
  );
  await page.route('**/api/auth/oauth2/complete', (route) => {
    completed++;
    return route.fulfill({ status: 401, json: { message: 'Incorrect code' } });
  });
  await page.route('**/api/auth/oauth2/cancel', (route) => route.fulfill({ status: 204 }));
  await page.goto('/oauth2/complete');
  await expect(page.getByLabel('Authenticator code')).toBeVisible();
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeDisabled();
  expect(completed).toBe(0);
  await page.getByRole('button', { name: 'Cancel Google sign-in' }).click();
  await expect(page).toHaveURL(/\/login$/);
});
