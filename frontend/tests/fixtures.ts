import { test as base, expect } from '@playwright/test';

export const test = base.extend({
  page: async ({ page }, use) => {
    await page.addInitScript(() => {
      Element.prototype.requestPointerLock = () =>
        Promise.reject(new Error('Native pointer lock disabled during verification'));
      Element.prototype.setPointerCapture = () => {};
      Element.prototype.releasePointerCapture = () => {};
      Document.prototype.exitPointerLock = () => {};
    });
    await use(page);
  },
});
export { expect };
