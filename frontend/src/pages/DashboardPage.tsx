import React from 'react';

function DashboardPage({ user }: { user: { email: string } }) {
  return (
    <div className="p-4 max-w-4xl mx-auto">
      <h2 className="text-2xl font-semibold mb-4">Dashboard (coming soon)</h2>
      <p>Welcome, {user.email}. This area will host link management, analytics, and more.</p>
    </div>
  );
}

export default DashboardPage;

