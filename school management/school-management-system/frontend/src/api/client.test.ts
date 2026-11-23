import {
  AxiosError,
  type AxiosAdapter,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { apiClient, setSessionExpiredHandler } from './client';

interface Call {
  method: string | undefined;
  url: string | undefined;
}

function response(
  config: InternalAxiosRequestConfig,
  status: number,
  data: unknown,
): AxiosResponse {
  return { status, data, statusText: '', headers: {}, config };
}

function unauthorized(config: InternalAxiosRequestConfig): AxiosError {
  return new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, response(config, 401, {}));
}

/** Replaces the network with a scripted backend and records every call that reaches it. */
function scriptBackend(
  handler: (config: InternalAxiosRequestConfig, calls: Call[]) => Promise<AxiosResponse>,
) {
  const calls: Call[] = [];
  const adapter: AxiosAdapter = (config) => {
    calls.push({ method: config.method, url: config.url });
    return handler(config, calls);
  };
  apiClient.defaults.adapter = adapter;
  return calls;
}

describe('apiClient session handling', () => {
  const onExpired = vi.fn();

  beforeEach(() => {
    onExpired.mockReset();
    setSessionExpiredHandler(onExpired);
  });

  afterEach(() => setSessionExpiredHandler(null));

  it('refreshes once after a 401 and retries the original request transparently', async () => {
    let refreshed = false;
    const calls = scriptBackend((config) => {
      if (config.url === '/auth/refresh') {
        refreshed = true;
        return Promise.resolve(response(config, 200, {}));
      }
      return refreshed
        ? Promise.resolve(response(config, 200, { ok: true }))
        : Promise.reject(unauthorized(config));
    });

    const result = await apiClient.get('/students');

    expect(result.data).toEqual({ ok: true });
    expect(calls.map((call) => call.url)).toEqual(['/students', '/auth/refresh', '/students']);
    expect(onExpired).not.toHaveBeenCalled();
  });

  it('shares one refresh between concurrent requests that expire together', async () => {
    let refreshed = false;
    const calls = scriptBackend(async (config) => {
      if (config.url === '/auth/refresh') {
        await Promise.resolve();
        refreshed = true;
        return response(config, 200, {});
      }
      if (!refreshed) {
        throw unauthorized(config);
      }
      return response(config, 200, { url: config.url });
    });

    const results = await Promise.all([
      apiClient.get('/a'),
      apiClient.get('/b'),
      apiClient.get('/c'),
    ]);

    expect(results.map((r) => r.data.url)).toEqual(['/a', '/b', '/c']);
    expect(calls.filter((call) => call.url === '/auth/refresh')).toHaveLength(1);
  });

  it('gives up when the refresh fails, reports the expired session and never loops', async () => {
    const calls = scriptBackend((config) => Promise.reject(unauthorized(config)));

    await expect(apiClient.get('/students')).rejects.toMatchObject({
      name: 'ApiError',
      status: 401,
    });

    expect(calls.map((call) => call.url)).toEqual(['/students', '/auth/refresh']);
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it('does not retry forever when the retried request is still unauthorized', async () => {
    const calls = scriptBackend((config) =>
      config.url === '/auth/refresh'
        ? Promise.resolve(response(config, 200, {}))
        : Promise.reject(unauthorized(config)),
    );

    await expect(apiClient.get('/students')).rejects.toMatchObject({ status: 401 });

    expect(calls.map((call) => call.url)).toEqual(['/students', '/auth/refresh', '/students']);
    expect(onExpired).toHaveBeenCalledTimes(1);
  });

  it.each([
    '/auth/login',
    '/auth/refresh',
    '/auth/logout',
    '/auth/forgot-password',
    '/auth/reset-password',
  ])('never tries to refresh when %s itself answers 401', async (url) => {
    const calls = scriptBackend((config) => Promise.reject(unauthorized(config)));

    await expect(apiClient.post(url)).rejects.toMatchObject({ status: 401 });

    expect(calls).toHaveLength(1);
    expect(onExpired).not.toHaveBeenCalled();
  });

  it('does not refresh for errors other than 401', async () => {
    const calls = scriptBackend((config) =>
      Promise.reject(
        new AxiosError('Forbidden', 'ERR_BAD_REQUEST', config, null, response(config, 403, {})),
      ),
    );

    await expect(apiClient.get('/admin/x')).rejects.toMatchObject({ status: 403 });

    expect(calls).toHaveLength(1);
  });
});
