import React, { useEffect, useState } from 'react';
import { Routes, Route, Link, Navigate, useNavigate } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import { getCsrfToken, getCurrentUser } from './api';

function App() {
  const [user, setUser] = useState<{ email: string } | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);
  const [logoutError, setLogoutError] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    // Attempt session restoration on mount
    getCurrentUser()
      .then((u) => setUser(u))
      .finally(() => setCheckingSession(false));
  }, []);

  const handleLogout = async () => {
    setLogoutError(false);
    try {
      const token = await getCsrfToken();
      const response = await fetch('/api/v1/auth/logout', {
        method: 'POST',
        credentials: 'include',
        headers: { 'X-XSRF-TOKEN': token },
      });
      if (!response.ok) {
        setLogoutError(true);
        return;
      }
      setUser(null);
      navigate('/');
    } catch {
      setLogoutError(true);
    }
  };

  return (
    <div className="flex flex-col min-h-screen">
      <header className="bg-deep-olive text-paper p-4 flex justify-between items-center">
        <Link to="/" className="text-2xl font-bold">LinkScale</Link>
        <nav className="flex flex-wrap items-center justify-end gap-x-4 gap-y-2">
          {checkingSession ? null : user ? (
            <>
              <Link to="/dashboard" className="font-semibold text-darkNavy hover:underline">
                Dashboard
              </Link>
              <span className="hidden sm:inline">{user.email}</span>
              <button onClick={handleLogout} className="underline">
                Logout
              </button>
            </>
          ) : (
            <>
              <a href="/login" className="underline">
                Login
              </a>
              <a href="/register" className="underline">
                Register
              </a>
            </>
          )}
        </nav>
      </header>

      <main className="flex-1">
        {logoutError && <p role="alert" className="mx-auto max-w-5xl px-4 pt-4 text-red-700">Could not sign out. Try again.</p>}
        <Routes>
          <Route path="/" element={<LandingPage user={user} />} />
          <Route
            path="/register"
            element={user ? <Navigate to="/" /> : <RegisterPage setUser={setUser} />}
          />
          <Route path="/login" element={user ? <Navigate to="/" /> : <LoginPage setUser={setUser} />} />
          <Route
            path="/dashboard"
            element={
              checkingSession ? <p role="status" className="p-4">Checking your session…</p>
              : user ? <DashboardPage /> : <Navigate to="/login" replace />
            }
          />
        </Routes>
      </main>

      <footer className="bg-paper text-near-black p-4 text-center">
        © 2026 LinkScale. All rights reserved.
      </footer>
    </div>
  );
}

export default App;

