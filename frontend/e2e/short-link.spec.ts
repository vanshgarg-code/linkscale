import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

test('a new user can shorten a URL and see a public visit in analytics', async ({ page, browser, baseURL }) => {
  const runId = randomUUID();
  const destination = new URL('/?e2e-destination=' + runId, baseURL).href;

  await page.goto('/register');
  await page.getByLabel('Email address').fill(`playwright-${runId}@example.com`);
  await page.getByLabel('Password', { exact: true }).fill('LinkScale-test-2026!');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible();

  await page.getByLabel('Destination URL').fill(destination);
  await page.getByRole('button', { name: 'Shorten' }).click();
  const shortLink = page.getByRole('link', { name: 'Short link' });
  await expect(shortLink).toHaveAttribute('href', /^https?:\/\/[^/]+\/r\/[^/]+$/);
  const shortUrl = await shortLink.evaluate((link: HTMLAnchorElement) => link.href);

  const visitor = await browser.newPage();
  try {
    const response = await visitor.goto(shortUrl);
    expect(response?.ok()).toBe(true);
    expect(response?.request().redirectedFrom()?.url()).toBe(shortUrl);
    await expect(visitor).toHaveURL(destination);
  } finally {
    await visitor.close();
  }

  await page.getByRole('link', { name: 'Dashboard' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Your links' })).toBeVisible();
  const linkRow = page.getByRole('listitem').filter({
    has: page.getByRole('link', { name: shortUrl, exact: true }),
  });
  await linkRow.getByRole('button', { name: 'Analytics' }).click();
  const analytics = linkRow.getByRole('region', { name: 'Click analytics' });
  await expect(analytics.getByText('1 recorded clicks', { exact: true })).toBeVisible();
});
