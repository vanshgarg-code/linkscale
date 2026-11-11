import React, { useEffect, useState } from 'react';
import { Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import LandingPage from './pages/LandingPage';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import { getCurrentUser } from './api';

function App() {
  const [user, setUser] = useState<{ email: string } | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    // Attempt session restoration on mount
    getCurrentUser()
      .then((u) => setUser(u))
      .catch(() => setUser(null));
  }, []);

  const handleLogout = async () => {
    await fetch('/api/v1/auth/logout', { method: 'POST', credentials: 'include' });
    setUser(null);
    navigate('/');
  };

  return (
    <div className="flex flex-col min-h-screen">
      <header className="bg-deep-olive text-paper p-4 flex justify-between items-center">
        <h1 className="text-2xl font-bold">LinkScale</h1>
        <nav className="space-x-4">
          {user ? (
            <>
              <span>{user.email}</span>
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
        <Routes>
          <Route path="/" element={<LandingPage user={user} />} />
          <Route
            path="/register"
            element={user ? <Navigate to="/" /> : <RegisterPage setUser={setUser} />}
          />
          <Route path="/login" element={user ? <Navigate to="/" /> : <LoginPage setUser={setUser} />} />
          <Route
            path="/dashboard"
            element={user ? <DashboardPage user={user} /> : <Navigate to="/login" />}
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

