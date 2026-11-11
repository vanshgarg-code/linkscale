import React from 'react';
import ShortenForm from '../components/ShortenForm';

function LandingPage({ user }: { user: { email: string } | null }) {
  return (
    <div className="bg-pageBg text-darkNavy py-12">
      <div className="max-w-[1120px] mx-auto px-4 grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Left column – headline */}
        <section className="flex flex-col justify-center">
          <h1 className="text-4xl md:text-5xl font-bold text-primary mb-4">LinkScale</h1>
          <p className="text-xl md:text-2xl font-medium mb-2">Short links you can manage.</p>
          <p className="text-base md:text-lg text-darkNavy">
            Create short links instantly. Share them and view basic click stats.
          </p>
        </section>
        {/* Right column – shortener card */}
        <section className="bg-surface rounded border border-gray-200 p-6 shadow-sm">
          <h2 className="text-lg font-medium text-darkNavy mb-4">Create a short link</h2>
          <ShortenForm user={user} />
        </section>
      </div>
    </div>
  );
}

export default LandingPage;


