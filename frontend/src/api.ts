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

