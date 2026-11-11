import React, { useState, useEffect } from 'react';
import { createLink, getCsrfToken } from '../api';
import { Loader2, Clipboard, ClipboardCheck } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';

function ShortenForm({ user }: { user: { email: string } | null }) {
  const navigate = useNavigate();
  const location = useLocation();
  const pendingUrl = (location.state as { pendingUrl?: unknown } | null)?.pendingUrl;
  const [url, setUrl] = useState(typeof pendingUrl === 'string' ? pendingUrl : '');
  const [shortUrl, setShortUrl] = useState<string | null>(null);
  const [status, setStatus] = useState<'idle' | 'pending' | 'success' | 'error'>('idle');
  const [error, setError] = useState<string | null>(null);
  const [csrfToken, setCsrfToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (user) {
      getCsrfToken().then(t => setCsrfToken(t)).catch(() => setCsrfToken(null));
    }
  }, [user]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user) {
      navigate('/login', { state: { pendingUrl: url } });
      return;
    }
    setError(null);
    setStatus('pending');
    try {
      const result = await createLink(url, csrfToken ?? undefined);
      setShortUrl(result.shortUrl);
      setStatus('success');
      setUrl('');
    } catch (err: unknown) {
      setError(err instanceof Error && err.message ? err.message : 'Failed to shorten');
      setStatus('error');
    }
  };

  const copyToClipboard = async () => {
    if (shortUrl) {
      await navigator.clipboard.writeText(shortUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }
  };

  const isLocal = shortUrl?.includes('localhost');

  return (
    <div className="bg-surface p-6 rounded shadow-md max-w-lg mx-auto">
      <form onSubmit={handleSubmit} className="flex flex-col space-y-4">
        <label className="block">
          <span className="text-darkNavy">Destination URL</span>
          <input
            type="url"
            required
            placeholder="https://example.com"
            value={url}
            onChange={e => setUrl(e.target.value)}
            className="mt-1 block w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </label>
        <button
          type="submit"
          disabled={status === 'pending'}
          className={`px-4 py-2 rounded text-darkNavy ${user ? 'bg-primary hover:bg-primary/80' : 'bg-primary/60'} focus:outline-none`}
        >
          {status === 'pending' ? <Loader2 className="animate-spin inline-block" /> : 'Shorten'}
        </button>
      </form>
      {error && <p className="text-red-600 mt-2">{error}</p>}
      {status === 'success' && shortUrl && (
        <div className="mt-4 border-t pt-4">
          <div className="flex items-center space-x-2">
            <a href={shortUrl} target="_blank" rel="noopener noreferrer" className="text-primary break-all" aria-label="Short link">
              {shortUrl}
            </a>
            <button onClick={copyToClipboard} className="p-1" aria-label="Copy">
              {copied ? <ClipboardCheck className="w-5 h-5 text-primary" /> : <Clipboard className="w-5 h-5" />}
            </button>
          </div>
          {isLocal && <p className="text-sm text-gray-500 mt-1">Local development link</p>}
          {copied && <p className="text-sm text-green-600 mt-1">Copied!</p>}
        </div>
      )}
    </div>
  );
}

export default ShortenForm;
