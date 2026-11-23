import { expect, type Page } from '@playwright/test';

export type AuthorityName = 'ROLE_ADMIN' | 'ROLE_TEACHER' | 'ROLE_STUDENT';

export const problem = (status: number, title: string, detail: string) => ({
  status,
  contentType: 'application/problem+json',
  body: JSON.stringify({ type: 'https://school.example/errors/x', title, status, detail }),
});

export const userFor = (role: AuthorityName) => ({
  id: 'u-1',
  schoolId: 's-1',
  username: 'T-0001',
  email: 'tara@school.test',
  firstName: 'Tara',
  lastName: 'Teacher',
  roles: [role],
});

/** A scripted backend: the session state flips when the login or logout endpoints are called. */
export async function mockBackend(
  page: Page,
  options: { role?: AuthorityName; signedIn?: boolean } = {},
) {
  let signedIn = options.signedIn ?? false;
  const role = options.role ?? 'ROLE_ADMIN';

  await page.route('**/api/v1/system/info', (route) =>
    route.fulfill({ json: { name: 'school-management-system', status: 'UP', profile: 'test' } }),
  );
  await page.route('**/api/v1/auth/me', (route) =>
    signedIn
      ? route.fulfill({ json: userFor(role) })
      : route.fulfill(problem(401, 'Unauthorized', 'Authentication is required.')),
  );
  await page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill(problem(401, 'Unauthorized', 'Session expired.')),
  );
  await page.route('**/api/v1/auth/login', (route) => {
    const body = route.request().postDataJSON() as { password: string };
    if (body.password === 'Correct-Pass-1!') {
      signedIn = true;
      return route.fulfill({ json: userFor(role) });
    }
    return route.fulfill(problem(401, 'Unauthorized', 'Invalid credentials.'));
  });
  await page.route('**/api/v1/auth/logout', (route) => {
    signedIn = false;
    return route.fulfill({ status: 204 });
  });
}

export async function expectNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

export const pageOf = <T>(content: T[]) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: content.length === 0 ? 0 : 1,
  first: true,
  last: true,
});
