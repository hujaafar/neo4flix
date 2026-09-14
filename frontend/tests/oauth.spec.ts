import { test, expect } from './fixtures';

// These verify UI contracts only. Signed OIDC/code/state validation is covered by the Java provider tests.
test.use({ trace: 'off', screenshot: 'off' });

// UI-contract cases must not consume the live credential throttle during repeated page reloads.
test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/refresh', (route) => route.fulfill({ status: 401, json: {} }));
  await page.route('**/api/auth/oauth2/providers', (route) =>
    route.fulfill({
      json: [
        { id: 'google', name: 'Google', enabled: false },
        { id: 'github', name: 'GitHub', enabled: false },
      ],
    }),
  );
});

for (const provider of ['google', 'github']) {
  test(`${provider} button starts the matching provider and preserves the return route`, async ({
    page,
  }) => {
    await page.route('**/api/auth/oauth2/providers', (route) =>
      route.fulfill({
        json: [
          { id: 'google', name: 'Google', enabled: true },
          { id: 'github', name: 'GitHub', enabled: true },
        ],
      }),
    );
    await page.route('**/api/auth/oauth2/authorize/**', (route) =>
      route.fulfill({
        contentType: 'text/html',
        body: '<h1>Test provider redirect</h1>',
      }),
    );
    await page.goto('/login?returnUrl=%2Fwatchlist');
    await page
      .getByRole('button', {
        name: provider === 'google' ? 'Continue with Google' : 'Continue with GitHub',
      })
      .click();
    await expect(page.getByRole('heading', { name: 'Test provider redirect' })).toBeVisible();
    const destination = new URL(page.url());
    expect(destination.pathname).toBe('/api/auth/oauth2/authorize/' + provider);
    expect(destination.searchParams.get('returnUrl')).toBe('/watchlist');
  });
}

test('GitHub disconnect requires password and 2FA and leaves Google controls separate', async ({
  page,
}) => {
  let disconnected = false;
  await page.route('**/api/auth/refresh', (route) =>
    route.fulfill(
      disconnected
        ? { status: 401, json: {} }
        : {
            json: {
              accessToken: 'ui-test-token',
              user: {
                id: 'oauth-ui',
                name: 'OAuth Viewer',
                email: 'oauth-ui@example.test',
                role: 'USER',
                twoFactorEnabled: true,
                googleLinked: true,
                githubLinked: true,
              },
            },
          },
    ),
  );
  await page.route('**/api/users/me/oauth2/github', (route) => {
    expect(route.request().method()).toBe('DELETE');
    expect(route.request().postDataJSON()).toEqual({
      password: 'DemoOnlyPassword!123',
      code: '123456',
    });
    disconnected = true;
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/auth/logout', (route) => route.fulfill({ status: 204 }));
  await page.goto('/account');
  const github = page
    .locator('section.panel')
    .filter({ has: page.getByRole('heading', { name: 'GitHub sign-in', exact: true }) });
  await expect(github.getByRole('button', { name: 'Disconnect GitHub' })).toBeDisabled();
  await github.getByLabel('Current password').fill('DemoOnlyPassword!123');
  await expect(github.getByRole('button', { name: 'Disconnect GitHub' })).toBeDisabled();
  await github.getByLabel('Authenticator code').fill('123456');
  await expect(page.getByRole('button', { name: 'Disconnect Google' })).toBeDisabled();
  await github.getByRole('button', { name: 'Disconnect GitHub' }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(disconnected).toBe(true);
});

for (const provider of [
  { id: 'google', name: 'Google' },
  { id: 'github', name: 'GitHub' },
]) {
  test(`${provider.name} availability, callback failure and expired proof give clear choices`, async ({
    page,
  }, testInfo) => {
    await page.route('**/api/auth/oauth2/providers', (route) =>
      route.fulfill({ json: [{ ...provider, enabled: false }] }),
    );
    await page.goto('/login');
    await expect(
      page.getByRole('button', { name: `Continue with ${provider.name}` }),
    ).toBeDisabled();
    await expect(page.getByText(`${provider.name} sign-in is awaiting setup.`)).toBeVisible();
    await page.goto(`/login?oauthError=${provider.id}`);
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
    await page.screenshot({
      path: testInfo.outputPath(`${provider.id}-login.png`),
      fullPage: true,
    });
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(
      false,
    );
  });

  test(`${provider.name} registration explains password and existing account linking requires password plus 2FA`, async ({
    page,
  }, testInfo) => {
    let mode = 'register';
    let completed = 0;
    await page.route('**/api/auth/oauth2/pending', (route) =>
      route.fulfill({
        json: {
          provider: provider.id,
          providerName: provider.name,
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
    await expect(
      page.getByRole('button', { name: `Connect ${provider.name} and sign in` }),
    ).toBeDisabled();
    await page.getByLabel('Neo4flix password', { exact: true }).fill('DemoOnlyPassword!123');
    await page.getByLabel('Authenticator code').fill('123456');
    await page.getByRole('button', { name: `Connect ${provider.name} and sign in` }).click();
    await expect(page.getByRole('alert')).toContainText('incorrect');
    expect(completed).toBe(1);
    await page.getByLabel('Neo4flix password', { exact: true }).clear();
    await page.getByLabel('Authenticator code').clear();
    await page.screenshot({
      path: testInfo.outputPath(`${provider.id}-linking.png`),
      fullPage: true,
    });
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(
      false,
    );
  });

  test(`connected ${provider.name} login pauses for authenticator and cancellation returns to login`, async ({
    page,
  }) => {
    let completed = 0;
    await page.route('**/api/auth/oauth2/pending', (route) =>
      route.fulfill({
        json: {
          provider: provider.id,
          providerName: provider.name,
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
    await page.getByRole('button', { name: `Cancel ${provider.name} sign-in` }).click();
    await expect(page).toHaveURL(/\/login$/);
  });
}
