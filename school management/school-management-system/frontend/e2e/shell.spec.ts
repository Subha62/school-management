import { expect, test, type Page } from '@playwright/test';
import { expectNoHorizontalOverflow, mockBackend, problem } from './support';

const MOBILE_BREAKPOINT = 900;

async function signIn(page: Page, password = 'Correct-Pass-1!') {
  await page.getByLabel('Email or ID').fill('tara@school.test');
  await page.getByLabel('Password', { exact: false }).first().fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test('anonymous visitors land on a login page that fits the viewport', async ({ page }) => {
  await mockBackend(page);
  await page.goto('/');

  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await expect(page.getByLabel('Email or ID')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('empty login form shows field-level validation without calling the backend', async ({
  page,
}) => {
  await mockBackend(page);
  let loginCalls = 0;
  await page.route('**/api/v1/auth/login', (route) => {
    loginCalls += 1;
    return route.fulfill(problem(401, 'Unauthorized', 'Invalid credentials.'));
  });
  await page.goto('/login');

  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page.getByText('Email or ID is required')).toBeVisible();
  await expect(page.getByText('Password is required')).toBeVisible();
  expect(loginCalls).toBe(0);
  await expectNoHorizontalOverflow(page);
});

test('wrong credentials show one generic message', async ({ page }) => {
  await mockBackend(page);
  await page.goto('/login');

  await signIn(page, 'Wrong-Pass-1!');

  await expect(page.getByRole('alert')).toContainText('email/ID or password is incorrect');
  await expectNoHorizontalOverflow(page);
});

test('signing in opens the role workspace and protected pages need a session', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_ADMIN' });
  await page.goto('/admin/profile');
  await expect(page).toHaveURL(/\/login$/);

  await signIn(page);

  await expect(page).toHaveURL(/\/admin\/profile$/);
  await expect(page.getByRole('heading', { name: 'My profile' })).toBeVisible();
  await expect(page.getByText('tara@school.test')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('workspace navigation adapts to the viewport', async ({ page }, testInfo) => {
  await mockBackend(page, { role: 'ROLE_ADMIN', signedIn: true });
  await page.goto('/admin');
  const isMobile = (testInfo.project.use.viewport?.width ?? 0) < MOBILE_BREAKPOINT;
  const menuButton = page.getByRole('button', { name: 'Open navigation menu' });

  if (isMobile) {
    await expect(menuButton).toBeVisible();
    await expect(page.getByRole('navigation')).toHaveCount(0);
    const box = await menuButton.boundingBox();
    expect(box?.width).toBeGreaterThanOrEqual(44);
    expect(box?.height).toBeGreaterThanOrEqual(44);

    await menuButton.click();
    await expect(
      page.getByRole('navigation', { name: /administration navigation/i }),
    ).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('navigation')).toHaveCount(0);
  } else {
    await expect(menuButton).toHaveCount(0);
    await expect(
      page.getByRole('navigation', { name: /administration navigation/i }),
    ).toBeVisible();
  }

  await expect(page.getByRole('heading', { name: 'Admin dashboard' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('a student cannot open the admin workspace', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await page.goto('/admin');

  await expect(page.getByRole('heading', { name: 'Access denied' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Admin dashboard' })).toHaveCount(0);
  await expectNoHorizontalOverflow(page);
});

test('account menu signs the user out and returns to the login page', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_TEACHER', signedIn: true });
  await page.goto('/teacher');

  const accountButton = page.getByRole('button', { name: 'Account menu' });
  const box = await accountButton.boundingBox();
  expect(box?.width).toBeGreaterThanOrEqual(44);
  await accountButton.click();
  await expect(page.getByText('tara@school.test')).toBeVisible();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();

  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/teacher');
  await expect(page).toHaveURL(/\/login$/);
});

test('change-password form shows validation and fits small screens', async ({ page }) => {
  await mockBackend(page, { role: 'ROLE_STUDENT', signedIn: true });
  await page.goto('/student/change-password');

  await page.getByLabel('Current password').fill('Old-Pass-1!');
  await page.getByLabel(/^new password/i).fill('weak');
  await page.getByLabel('Confirm new password').fill('other');
  await page.getByRole('button', { name: 'Change password' }).click();

  await expect(page.getByText(/at least 10 characters/i)).toBeVisible();
  await expect(page.getByText('Passwords do not match')).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('forgot-password shows the neutral confirmation', async ({ page }) => {
  await mockBackend(page);
  await page.route('**/api/v1/auth/forgot-password', (route) =>
    route.fulfill({ status: 202, json: { message: 'If an account exists...' } }),
  );
  await page.goto('/forgot-password');

  await page.getByLabel('Email').fill('anyone@school.test');
  await page.getByRole('button', { name: 'Send reset link' }).click();

  await expect(page.getByText(/if an account exists for that email address/i)).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('unknown routes show the not found page', async ({ page }) => {
  await mockBackend(page);
  await page.goto('/does-not-exist');

  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});

test('backend outage is shown as a safe error with retry, not as a signed-out state', async ({
  page,
}) => {
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({
      status: 500,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'https://school.example/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: 'NullPointerException in com.schoolmanagement',
        traceId: 'e2e-trace',
      }),
    }),
  );
  await page.goto('/admin');

  const alert = page.getByRole('alert');
  await expect(alert).toContainText('Something went wrong on our side', { timeout: 15_000 });
  await expect(alert).not.toContainText('NullPointerException');
  await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible();
  await expectNoHorizontalOverflow(page);
});
