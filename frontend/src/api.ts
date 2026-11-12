export async function getCsrfToken(): Promise<string> {
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include' });
  if (!response.ok) throw new Error('Failed to fetch CSRF token');
  const data = await response.json();
  // Store token in a cookie header is also set by server, but we need the raw token value
  return data.token;
}

export async function register(email: string, password: string): Promise<void> {
  const csrf = await getCsrfToken();
  const response = await fetch('/api/v1/auth/register', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    const err = await response.json();
    throw new Error(err.message ?? 'Registration failed');
  }
}

export async function login(email: string, password: string): Promise<void> {
  const csrf = await getCsrfToken();
  const response = await fetch('/api/v1/auth/login', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': csrf,
    },
    body: JSON.stringify({ email, password }),
  });
  if (!response.ok) {
    const err = await response.json();
    throw new Error(err.message ?? 'Login failed');
  }
}

export async function fetchCurrentUser(): Promise<{ email: string }> {
  const response = await fetch('/api/v1/auth/me', { credentials: 'include' });
  if (!response.ok) throw new Error('Not authenticated');
  const data = await response.json();
  return { email: data.email };
}

export async function createLink(destinationUrl: string, csrfToken?: string): Promise<{ shortUrl: string }> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (csrfToken) headers['X-XSRF-TOKEN'] = csrfToken;
  const response = await fetch('/api/v1/links', {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ destination_url: destinationUrl }),
  });
  if (!response.ok) {
    const err = await response.json();
    throw new Error(err.message ?? 'Link creation failed');
  }
  const data = await response.json();
  return { shortUrl: data.short_url };
}

export async function getCurrentUser(): Promise<{ email: string } | null> {
  try {
    return await fetchCurrentUser();
  } catch {
    return null;
  }
}

export type LinkFilter = 'all' | 'active' | 'inactive' | 'expired';

export interface DashboardLink {
  id: number;
  title: string | null;
  destination_url: string;
  short_url: string;
  active: boolean;
  expires_at: string | null;
}

interface LinkPage {
  content: DashboardLink[];
  total_pages: number;
}

export async function fetchLinks(
  search: string,
  status: LinkFilter,
  page: number,
  signal: AbortSignal,
): Promise<LinkPage> {
  const query = new URLSearchParams({ search, status, page: String(page), size: '10' });
  const response = await fetch(`/api/v1/links?${query}`, { credentials: 'include', signal });
  if (response.status === 401) throw new Error('Your session has expired. Sign in again to see your links.');
  if (!response.ok) throw new Error('Could not load your links. Try again.');
  return response.json();
}

type LinkUpdate = Partial<Pick<DashboardLink, 'title' | 'expires_at' | 'active'>>;

async function writeLink(id: number, method: 'PATCH' | 'DELETE', update?: LinkUpdate): Promise<void> {
  const token = await getCsrfToken();
  const headers: Record<string, string> = { 'X-XSRF-TOKEN': token };
  if (update) headers['Content-Type'] = 'application/json';
  const response = await fetch(`/api/v1/links/${id}`, {
    method,
    credentials: 'include',
    headers,
    body: update ? JSON.stringify(update) : undefined,
  });
  if (response.ok) return;

  if (response.status === 401) throw new Error('Your session has expired. Sign in again.');
  const error = await response.json().catch(() => null);
  const fallback = method === 'DELETE' ? 'Could not delete the link.' : 'Could not update the link.';
  throw new Error(error?.message || fallback);
}

export function updateLink(id: number, update: LinkUpdate): Promise<void> {
  return writeLink(id, 'PATCH', update);
}

export function deleteLink(id: number): Promise<void> {
  return writeLink(id, 'DELETE');
}
