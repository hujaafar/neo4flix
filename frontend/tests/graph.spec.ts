import { test, expect } from './fixtures';
import { randomBytes } from 'node:crypto';
import { readFileSync } from 'node:fs';

// This scenario signs in as the configured local administrator. Keep credential-bearing traffic out of traces.
test.use({ trace: 'off' });

test('administrator inspects real mapped ratings without private profile data', async ({
  page,
}, info) => {
  const env = Object.fromEntries(
    readFileSync('../.env', 'utf8')
      .split(/\r?\n/)
      .filter((line) => line.includes('='))
      .map((line) => {
        const index = line.indexOf('=');
        return [line.slice(0, index), line.slice(index + 1)];
      }),
  );
  const email = 'graph-test-' + randomBytes(8).toString('hex') + '@example.test';
  const password = 'GraphTest!7' + randomBytes(8).toString('hex');
  let created = false;
  await page.goto('/login');
  try {
    const fixture = await page.evaluate(
      async ({ email, password }) => {
        const registration = await fetch('/api/auth/register', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: 'Graph Test', email, password }),
        });
        if (registration.status !== 201) return { status: registration.status, id: '', rated: 0 };
        const data = await registration.json();
        const rating = await fetch('/api/ratings/me/inception', {
          method: 'PUT',
          headers: {
            'Content-Type': 'application/json',
            Authorization: 'Bearer ' + data.accessToken,
          },
          body: JSON.stringify({ score: 4, review: 'Private graph-test review' }),
        });
        await fetch('/api/auth/logout', { method: 'POST' });
        return { status: registration.status, id: data.user.id, rated: rating.status };
      },
      { email, password },
    );
    created = fixture.status === 201;
    expect(fixture.status).toBe(201);
    expect(fixture.rated).toBe(200);
    const loginStatus = await page.evaluate(
      async (credentials) => {
        const response = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(credentials),
        });
        return response.status;
      },
      { email: env['ADMIN_EMAIL'], password: env['ADMIN_PASSWORD'], code: '' },
    );
    expect(loginStatus).toBe(200);
    await page.goto('/graph');
    await expect(page.getByRole('heading', { name: 'The movie graph.' })).toBeVisible();
    await expect(page.getByText(/Live Neo4j data · GDS 2\.13\./)).toBeVisible();
    await page
      .getByRole('button', { name: 'User: Viewer ' + fixture.id.slice(0, 6), exact: true })
      .click();
    await expect(page.getByRole('cell', { name: 'RATED', exact: true })).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Inception', exact: true })).toBeVisible();
    await expect(page.getByRole('cell', { name: '4', exact: true })).toBeVisible();
    await expect(page.locator('main')).not.toContainText('Private graph-test review');
    await expect(page.locator('main')).not.toContainText(email);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2)).toBe(
      true,
    );
    await page.screenshot({ path: info.outputPath('graph.png'), fullPage: true });
  } finally {
    if (created) {
      const deleted = await page.evaluate(
        async (credentials) => {
          await fetch('/api/auth/logout', { method: 'POST' });
          const login = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ...credentials, code: '' }),
          });
          if (!login.ok) return login.status;
          const data = await login.json();
          const response = await fetch('/api/users/me', {
            method: 'DELETE',
            headers: {
              'Content-Type': 'application/json',
              Authorization: 'Bearer ' + data.accessToken,
            },
            body: JSON.stringify({ password: credentials.password, code: '' }),
          });
          return response.status;
        },
        { email, password },
      );
      expect(deleted).toBe(204);
    }
  }
});
