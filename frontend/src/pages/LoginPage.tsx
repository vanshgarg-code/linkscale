import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { login, getCsrfToken, fetchCurrentUser } from '../api';

function LoginPage({ setUser }: { setUser: (u: { email: string } | null) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const location = useLocation();
  // location.state?.pendingUrl can hold a draft URL saved before redirect

  useEffect(() => {
    // Ensure we have a fresh CSRF token before showing login form
    getCsrfToken().catch(() => {
      // ignore; token will be fetched on submit if needed
    });
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await login(email, password);
      const user = await fetchCurrentUser();
      setUser(user);
      const pendingUrl = (location.state as { pendingUrl?: unknown } | null)?.pendingUrl;
      if (typeof pendingUrl === 'string' && pendingUrl) {
        navigate('/', { state: { pendingUrl } });
      } else {
        navigate('/');
      }
    } catch (err: unknown) {
      setError(err instanceof Error && err.message ? err.message : 'Login failed');
    }
  };

  return (
    <div className="max-w-md mx-auto p-4">
      <h2 className="text-xl font-semibold mb-4">Login</h2>
      {error && <p className="text-red-600 mb-2">{error}</p>}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block mb-1">Email</label>
          <input
            type="email"
            required
            value={email}
            onChange={e => setEmail(e.target.value)}
            className="w-full border rounded px-2 py-1"
          />
        </div>
        <div>
          <label className="block mb-1">Password</label>
            <input
            type="password"
            required
            value={password}
            onChange={e => setPassword(e.target.value)}
            className="w-full border rounded px-2 py-1"
          />
        </div>
        <button
          type="submit"
          className="bg-deep-olive text-paper px-4 py-2 rounded hover:bg-deep-olive/80"
        >
          Login
        </button>
      </form>
    </div>
  );
}

export default LoginPage;

