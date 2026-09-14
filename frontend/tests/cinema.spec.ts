import { test, expect } from './fixtures';
import { randomBytes } from 'node:crypto';
import { setDefaultResultOrder } from 'node:dns';
setDefaultResultOrder('ipv4first');

test('cinema entrance unfolds, stays readable, and opens real film destinations', async ({
  page,
}, info) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('/');
  await expect(page).toHaveURL(/\/experience\/$/);
  await expect(page.locator('html')).toHaveClass(/motion-ready/);
  await expect(page.getByRole('heading', { name: 'Every film. A new world.' })).toBeVisible();
  await expect(page.locator('.reel-world')).toHaveAttribute('data-reel-ready', 'true');
  await page.waitForTimeout(1600);
  await page.screenshot({ path: info.outputPath('entrance.png') });
  if (info.project.name === 'desktop') {
    await page.mouse.move(30, 30);
    await page.waitForTimeout(350);
    const before = await page
      .locator('.reel-stage')
      .screenshot({ path: info.outputPath('pointer-before.png') });
    const box = await page.locator('.reel-stage').boundingBox();
    await page.mouse.move(box!.x + box!.width * 0.8, box!.y + box!.height * 0.25);
    await page.waitForTimeout(500);
    const after = await page
      .locator('.reel-stage')
      .screenshot({ path: info.outputPath('pointer-after.png') });
    expect(before.equals(after)).toBe(false);
  }
  const scene = page.locator('.reel-world');
  const journey = await scene.evaluate((el) => ({
    top: el.getBoundingClientRect().top + scrollY,
    travel: el.clientHeight - el.querySelector('.reel-stage')!.clientHeight,
  }));
  const states: number[][] = [];
  for (const p of [0, 0.25, 0.5, 0.7, 0.85, 0.99]) {
    await page.evaluate(
      ({ journey, p }) => scrollTo({ top: journey.top + journey.travel * p, behavior: 'instant' }),
      { journey, p },
    );
    await page.waitForTimeout(700);
    states.push((await scene.getAttribute('data-sc-verify-state'))!.split(',').map(Number));
    await page.screenshot({ path: info.outputPath('reel-' + p + '.png') });
  }
  expect(Math.abs(states[2][0] - states[0][0])).toBeGreaterThan(5);
  expect(states[0][1] - states[5][1]).toBeGreaterThan(7);
  await expect(page.locator('.reel-copy-second')).toHaveCSS('opacity', '1');
  const collection = page.locator('#collection');
  const desktop = info.project.name === 'desktop';
  const geometry = await collection.evaluate((el) => ({
    top: el.getBoundingClientRect().top + scrollY,
    travel: el.getBoundingClientRect().height - innerHeight,
  }));
  for (const progress of [0, 0.2, 0.4, 0.65, 1]) {
    await page.evaluate(
      ({ top, travel, p }) =>
        window.scrollTo({ top: top + Math.max(travel, 0) * p, behavior: 'instant' }),
      { ...geometry, p: progress },
    );
    await page.waitForTimeout(120);
    await page.screenshot({ path: info.outputPath(`collection-${progress}.png`) });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2)).toBe(
      true,
    );
  }
  if (desktop) {
    await page.evaluate(
      ({ top, travel }) => window.scrollTo({ top: top + travel * 0.65, behavior: 'instant' }),
      geometry,
    );
    await page.waitForTimeout(120);
    const boxes = await page.locator('.print-art').evaluateAll((els) =>
      els.map((el) => {
        const r = el.getBoundingClientRect();
        return { left: r.left, right: r.right, top: r.top, bottom: r.bottom };
      }),
    );
    expect(boxes[0].right).toBeLessThan(boxes[1].left);
    expect(boxes[1].right).toBeLessThan(boxes[2].left);
    expect(
      boxes.every((b) => b.left >= 0 && b.right <= 1440 && b.top >= 0 && b.bottom <= 1000),
    ).toBe(true);
    // Focus from the folded state must settle the collection before use.
    await page.evaluate((top) => window.scrollTo({ top, behavior: 'instant' }), geometry.top);
    await page.locator('.print-right .print-art').focus();
    await expect(page.locator('.print-right .print-art')).toBeInViewport({ ratio: 1 });
  }
  await page.getByRole('radio', { name: 'Animation', exact: true }).check();
  await expect(page.locator('#genre-image')).toHaveAttribute('src', '/art/spirited-away.svg');
  await expect(page.locator('#genre-caption')).toContainText('Spirited Away');
  await page.screenshot({ path: info.outputPath('genre.png') });
  await page.locator('#enter').scrollIntoViewIfNeeded();
  await page.screenshot({ path: info.outputPath('entrance-close.png') });
  expect(
    await page
      .locator('img')
      .evaluateAll((imgs) =>
        imgs.every(
          (img) => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0,
        ),
      ),
  ).toBe(true);
  await page.evaluate(
    ({ journey }) => scrollTo({ top: journey.top + journey.travel * 0.995, behavior: 'instant' }),
    { journey },
  );
  await page.waitForTimeout(750);
  await page.locator('#interstellar .text-action').click();
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fmovies%2Finterstellar/);
  await expect(page.getByRole('heading', { name: 'Take your seat.' })).toBeVisible();
  expect(errors).toEqual([]);
});

test('reel stays animated with reduced motion and compact layouts keep films accessible', async ({
  page,
}, info) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/experience/');
  await expect(page.locator('html')).toHaveClass(/static-motion/);
  const scene = page.locator('.reel-world');
  await expect(scene).toHaveAttribute('data-motion', 'running');
  await expect(scene).toHaveAttribute('data-reel-ready', 'true');
  const filmOffset = Number((await scene.getAttribute('data-sc-verify-state'))!.split(',')[2]);
  await expect
    .poll(async () => Number((await scene.getAttribute('data-sc-verify-state'))!.split(',')[2]))
    .toBeGreaterThan(filmOffset);
  await expect(page.locator('.collection [data-sc-stage]')).toHaveCSS('position', 'static');
  for (const print of await page.locator('.print-art').all()) {
    await print.focus();
    await expect(print).toBeInViewport();
  }
  await page.screenshot({ path: info.outputPath('reduced.png'), fullPage: true });
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  if (info.project.name === 'desktop') {
    await expect(page.locator('.collection [data-sc-stage]')).toHaveCSS('position', 'sticky');
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect(page.locator('.collection [data-sc-stage]')).toHaveCSS('position', 'static');
  }
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.setViewportSize({ width: 360, height: 640 });
  await page.goto('/experience/');
  await expect(page.locator('.reel-world')).toHaveAttribute('data-motion', 'running');
  await page.waitForTimeout(1500);
  await expect(page.getByRole('heading', { name: 'Every film. A new world.' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 2)).toBe(
    true,
  );
  await page.screenshot({ path: info.outputPath('compact.png') });
});

test('genre selection survives registration and opens the filtered live catalogue', async ({
  page,
}, info) => {
  const email = 'cinema-' + randomBytes(6).toString('hex') + '@example.test';
  const password = 'CinemaTest!' + randomBytes(8).toString('hex');
  let registered = false;
  try {
    await page.goto('/experience/');
    await page.getByRole('radio', { name: 'Animation', exact: true }).check();
    await page.getByRole('link', { name: 'Browse Animation' }).click();
    await expect(page.getByRole('heading', { name: 'Take your seat.' })).toBeVisible();
    await page.getByRole('link', { name: 'Create an account', exact: true }).click();
    await page.getByLabel('Your name').fill('Cinema Guest');
    await page.getByLabel('Email address').fill(email);
    await page.getByLabel('Password', { exact: true }).fill(password);
    const creation = page.waitForResponse(
      (response) =>
        response.url().endsWith('/api/auth/register') && response.request().method() === 'POST',
    );
    await page.getByRole('button', { name: 'Create account' }).click();
    registered = (await creation).ok();
    await expect(page.getByRole('heading', { name: 'Something worth watching.' })).toBeVisible();
    await expect(page.locator('.genre-list button.selected')).toHaveText('Animation');
    await expect(page.getByRole('link', { name: 'View Spirited Away', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'View Interstellar', exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'All films', exact: true }).click();
    await expect(page.locator('movie-card')).toHaveCount(18);
    await page.getByRole('link', { name: 'Cinema entrance', exact: true }).click();
    await expect(page).toHaveURL(/\/experience\/$/);
    await page.getByRole('banner').getByRole('link', { name: 'Enter Neo4flix' }).click();
    await expect(page.getByRole('heading', { name: 'Something worth watching.' })).toBeVisible();
    await expect(page.locator('cinema-feature .reel-world')).toHaveAttribute(
      'data-reel-ready',
      'true',
    );
    await page.waitForTimeout(1500);
    await page.screenshot({ path: info.outputPath('discover-reel.png') });
    await expect(page.getByRole('button', { name: /(?:Pause|Play) motion/ })).toHaveCount(0);
    const reelTravel = await page.locator('cinema-feature .reel-world').evaluate((el) => ({
      top: el.getBoundingClientRect().top + scrollY,
      distance: el.clientHeight - el.querySelector('.reel-stage')!.clientHeight,
    }));
    for (const progress of [0.15, 0.5, 0.85]) {
      await page.evaluate(
        ({ top, distance, progress }) =>
          scrollTo({ top: top + distance * progress, behavior: 'instant' }),
        { ...reelTravel, progress },
      );
      await page.waitForTimeout(400);
      const coverage = await page.locator('cinema-feature .reel-stage').evaluate((el) => {
        const box = el.getBoundingClientRect();
        return { top: box.top, bottom: box.bottom, viewport: innerHeight };
      });
      await page.screenshot({ path: info.outputPath(`discover-scroll-${progress}.png`) });
      expect(Math.abs(coverage.top)).toBeLessThanOrEqual(2);
      expect(coverage.bottom).toBeGreaterThanOrEqual(coverage.viewport - 2);
    }
    // Changing the viewport must not reintroduce the empty strip under the pinned scene.
    const originalViewport = page.viewportSize()!;
    await page.setViewportSize({ width: originalViewport.width, height: 600 });
    await expect
      .poll(async () =>
        page
          .locator('cinema-feature .reel-stage')
          .evaluate((el) => Math.abs(el.getBoundingClientRect().height - innerHeight)),
      )
      .toBeLessThanOrEqual(2);
    await page.setViewportSize(originalViewport);
    await page.evaluate(() => {
      (window as any).__oldReel = document.querySelector('cinema-feature .reel-world');
    });
    await page.getByRole('link', { name: 'Browse films', exact: false }).click();
    await expect(page.getByRole('textbox', { name: 'Search movies' })).toBeInViewport();
    await page.getByRole('button', { name: 'Animation', exact: true }).click();
    await expect(page.locator('cinema-feature')).toHaveCount(0);
    expect(await page.evaluate(() => (window as any).__oldReel.dataset.motion)).toBe('disposed');
    const frames = await page.evaluate(() => (window as any).__oldReel.dataset.reelFrames);
    await page.waitForTimeout(200);
    expect(await page.evaluate(() => (window as any).__oldReel.dataset.reelFrames)).toBe(frames);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.getByRole('button', { name: 'All films', exact: true }).click();
    await page.evaluate(() => scrollTo({ top: 0, behavior: 'instant' }));
    await expect(page.locator('cinema-feature .reel-world')).toHaveAttribute(
      'data-motion',
      'running',
    );
    await expect(page.locator('cinema-feature .reel-world')).toHaveAttribute(
      'data-reel-ready',
      'true',
    );
  } finally {
    // Isolated test account; never modifies existing user accounts.
    if (registered) {
      // Use the browser's same-origin connection so the local TLS hostname and
      // loopback networking match the application, including on Windows.
      const statuses = await page.evaluate(
        async ({ email, password }) => {
          const login = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password, code: '' }),
          });
          if (!login.ok) return [login.status];
          const session = await login.json();
          const deleted = await fetch('/api/users/me', {
            method: 'DELETE',
            headers: {
              'Content-Type': 'application/json',
              Authorization: 'Bearer ' + session.accessToken,
            },
            body: JSON.stringify({ password, code: '' }),
          });
          return [login.status, deleted.status];
        },
        { email, password },
      );
      expect(statuses).toEqual([200, 204]);
    }
  }
});

test('entrance remains readable with JavaScript disabled', async ({ browser }, info) => {
  const context = await browser.newContext({
    javaScriptEnabled: false,
    ignoreHTTPSErrors: false,
    viewport:
      info.project.name === 'desktop' ? { width: 1440, height: 1000 } : { width: 390, height: 844 },
  });
  try {
    const page = await context.newPage();
    await page.goto(
      (process.env['NEO4FLIX_TEST_URL'] || 'https://localhost:8443') + '/experience/',
    );
    await expect(page.getByRole('heading', { name: 'Every film. A new world.' })).toBeVisible();
    await expect(page.locator('.collection [data-sc-stage]')).toHaveCSS('position', 'static');
    await page.getByRole('link', { name: 'Create your account' }).scrollIntoViewIfNeeded();
    await expect(page.getByRole('link', { name: 'Create your account' })).toBeVisible();
    await page.screenshot({ path: info.outputPath('no-javascript.png'), fullPage: true });
  } finally {
    await context.close();
  }
});

test('reel keeps moving across preference changes and reloads, with skip and graphics fallback', async ({
  page,
}, info) => {
  await page.goto('/experience/');
  const scene = page.locator('.reel-world');
  await expect(scene).toHaveAttribute('data-reel-ready', 'true');
  await expect(page.getByRole('button', { name: /(?:Pause|Play) motion/ })).toHaveCount(0);
  for (const reducedMotion of ['reduce', 'no-preference'] as const) {
    await page.emulateMedia({ reducedMotion });
    await expect(scene).toHaveAttribute('data-motion', 'running');
    const frames = Number(await scene.getAttribute('data-reel-frames'));
    await expect
      .poll(async () => Number(await scene.getAttribute('data-reel-frames')))
      .toBeGreaterThan(frames + 2);
  }
  await page.reload();
  await expect(scene).toHaveAttribute('data-motion', 'running');
  await page.getByRole('link', { name: 'Browse the collection', exact: false }).click();
  await expect(page.locator('#collection')).toBeFocused();
  await page.goto('/experience/');
  await expect(scene).toHaveAttribute('data-reel-ready', 'true');
  await page.locator('.reel-surface canvas').evaluate((el: HTMLCanvasElement) => {
    el.getContext('webgl2')!.getExtension('WEBGL_lose_context')!.loseContext();
  });
  await expect(scene).toHaveAttribute('data-motion', 'fallback');
  await expect(page.locator('.reel-poster')).toHaveCSS('opacity', '1');
  await expect(page.getByRole('heading', { name: 'Every film. A new world.' })).toBeVisible();
  await page.screenshot({ path: info.outputPath('graphics-fallback.png') });
});
