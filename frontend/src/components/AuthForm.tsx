import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { fetchCurrentUser, login, register } from '../api';

interface AuthFormProps {
  mode: 'login' | 'register';
  setUser: (user: { email: string } | null) => void;
}

export default function AuthForm({ mode, setUser }: AuthFormProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const isRegister = mode === 'register';
  const heading = isRegister ? 'Create your account' : 'Sign in to LinkScale';
  const pendingUrl = (location.state as { pendingUrl?: unknown } | null)?.pendingUrl;
  const draftState = typeof pendingUrl === 'string' && pendingUrl ? { pendingUrl } : undefined;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;

    setSubmitting(true);
    setError('');
    try {
      if (isRegister) await register(email, password);
      await login(email, password);
      setUser(await fetchCurrentUser());
      navigate('/', { state: draftState });
    } catch (err: unknown) {
      const fallback = isRegister ? 'Could not create your account.' : 'Could not sign in.';
      setError(err instanceof Error && err.message ? err.message : fallback);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="bg-pageBg px-4 py-10 text-darkNavy sm:py-16">
      <div className="mx-auto max-w-md">
        <Link to="/" className="mb-5 inline-flex items-center gap-2 text-sm font-medium hover:underline">
          <span aria-hidden="true">←</span> Back to LinkScale
        </Link>

        <div className="rounded-xl border border-slate-200 bg-surface p-6 shadow-sm sm:p-8">
          <h1 className="text-3xl font-bold tracking-tight">{heading}</h1>

          <form onSubmit={handleSubmit} className="mt-7 space-y-5" aria-busy={submitting}>
            {error && (
              <p role="alert" className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
                {error}
              </p>
            )}

            <div>
              <label htmlFor="auth-email" className="block text-sm font-semibold">Email address</label>
              <input
                id="auth-email"
                name="email"
                type="email"
                autoComplete="username"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className="mt-2 block w-full rounded-md border border-slate-300 bg-white px-3 py-2.5 focus:border-darkNavy focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label htmlFor="auth-password" className="block text-sm font-semibold">Password</label>
              <input
                id="auth-password"
                name="password"
                type="password"
                autoComplete={isRegister ? 'new-password' : 'current-password'}
                minLength={isRegister ? 8 : undefined}
                maxLength={isRegister ? 128 : undefined}
                aria-describedby={isRegister ? 'auth-password-hint' : undefined}
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className="mt-2 block w-full rounded-md border border-slate-300 bg-white px-3 py-2.5 focus:border-darkNavy focus:outline-none focus:ring-2 focus:ring-primary"
              />
              {isRegister && <p id="auth-password-hint" className="mt-2 text-sm text-slate-600">Use at least 8 characters.</p>}
            </div>

            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-md bg-primary px-4 py-3 font-semibold text-darkNavy transition-colors hover:bg-primary/85 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-darkNavy disabled:opacity-60"
            >
              {submitting
                ? (isRegister ? 'Creating account…' : 'Signing in…')
                : (isRegister ? 'Create account' : 'Sign in')}
            </button>
          </form>

          <p className="mt-6 border-t border-slate-200 pt-5 text-sm text-slate-600">
            {isRegister ? 'Already have an account? ' : 'New to LinkScale? '}
            <Link
              to={isRegister ? '/login' : '/register'}
              state={draftState}
              className="font-semibold text-darkNavy underline underline-offset-4"
            >
              {isRegister ? 'Sign in' : 'Create an account'}
            </Link>
          </p>
        </div>
      </div>
    </section>
  );
}
