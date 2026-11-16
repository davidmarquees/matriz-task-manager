import { API_URL } from './config';

/** Problem Details body returned by the API on errors (openapi.yaml). */
export interface Problem {
  status: number;
  title: string;
  detail?: string;
  code?: string;
  errors?: { field: string; code: string }[];
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem | null,
  ) {
    super(`API error ${status}`);
  }

  /** Error code for one field of a 422 response, if any. */
  fieldCode(field: string): string | undefined {
    return this.problem?.errors?.find((e) => e.field === field)?.code;
  }
}

/** Fired when an authenticated request gets 401: the session expired or ended (US1). */
export const SESSION_EXPIRED_EVENT = 'matriz:session-expired';

function csrfToken(): string | undefined {
  const cookie = document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='));
  return cookie ? decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)) : undefined;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE';
  body?: unknown;
  /** false for requests where 401 is an expected answer (login, first getMe). */
  notifySessionExpired?: boolean;
}

/**
 * Single entry point for API calls: same-origin cookies, JSON, CSRF header on unsafe requests,
 * Problem Details on errors.
 */
export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    const token = csrfToken();
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }

  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    let problem: Problem | null = null;
    try {
      problem = (await response.json()) as Problem;
    } catch {
      problem = null;
    }
    if (response.status === 401 && options.notifySessionExpired !== false) {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    throw new ApiError(response.status, problem);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
