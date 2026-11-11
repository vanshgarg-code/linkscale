import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { fetchLinks, type DashboardLink, type LinkFilter } from '../api';

function linkStatus(link: DashboardLink) {
  if (link.expires_at && Date.parse(link.expires_at) <= Date.now()) {
    return link.active ? 'Expired' : 'Inactive · Expired';
  }
  return link.active ? 'Active' : 'Inactive';
}

export default function DashboardPage() {
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<LinkFilter>('all');
  const [page, setPage] = useState(0);
  const [links, setLinks] = useState<DashboardLink[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [copiedId, setCopiedId] = useState<number | null>(null);
  const [copyError, setCopyError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      try {
        const result = await fetchLinks(search.trim(), status, page, controller.signal);
        if (controller.signal.aborted) return;
        setLinks(result.content);
        setTotalPages(result.total_pages);
      } catch (err) {
        if (controller.signal.aborted) return;
        setError(err instanceof Error ? err.message : 'Could not load your links.');
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }, 200);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [search, status, page]);

  function resetResult() {
    setLoading(true);
    setError('');
    setCopiedId(null);
    setCopyError('');
  }

  async function copyLink(link: DashboardLink) {
    setCopiedId(null);
    setCopyError('');
    try {
      await navigator.clipboard.writeText(link.short_url);
      setCopiedId(link.id);
    } catch {
      setCopyError('Copy failed. You can select and copy the short URL.');
    }
  }

  return (
    <section className="min-h-full bg-pageBg px-4 py-10 text-darkNavy sm:px-6 sm:py-14">
      <div className="mx-auto max-w-5xl">
        <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
          <h1 className="text-3xl font-bold tracking-tight">Your links</h1>
          <Link to="/" className="rounded-md bg-primary px-4 py-2 font-semibold">Create a short link</Link>
        </div>

        <div className="rounded-lg border border-slate-200 bg-white">
          <div className="flex flex-col gap-4 border-b border-slate-200 p-4 sm:flex-row">
            <label className="flex flex-1 flex-col gap-1 text-sm font-medium">
              Search
              <input
                type="search"
                placeholder="Search by destination or short code"
                value={search}
                onChange={(event) => {
                  if (event.target.value === search) return;
                  resetResult();
                  setSearch(event.target.value);
                  setPage(0);
                }}
                className="rounded-md border border-slate-300 px-3 py-2"
              />
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium">
              Status
              <select
                value={status}
                onChange={(event) => {
                  resetResult();
                  setStatus(event.target.value as LinkFilter);
                  setPage(0);
                }}
                className="rounded-md border border-slate-300 bg-white px-3 py-2 sm:min-w-36"
              >
                <option value="all">All statuses</option>
                <option value="active">Active</option>
                <option value="inactive">Inactive</option>
                <option value="expired">Expired</option>
              </select>
            </label>
          </div>

          {loading && <p role="status" className="p-6 text-slate-600">Loading your links…</p>}
          {!loading && error && <p role="alert" className="p-6 text-red-700">{error}</p>}
          {!loading && !error && links.length === 0 && (
            <p className="p-6 text-slate-600">
              {search.trim() || status !== 'all' ? 'No links match these filters.' : 'Your short links will appear here after you create one.'}
            </p>
          )}
          {!loading && !error && links.length > 0 && (
            <ul className="divide-y divide-slate-200">
              {links.map((link) => (
                <li key={link.id} className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    {link.title && <p className="mb-1 break-words font-semibold">{link.title}</p>}
                    <a href={link.short_url} target="_blank" rel="noreferrer" className="break-all font-medium underline">
                      {link.short_url}
                    </a>
                    <p className="mt-1 break-all text-sm text-slate-600">{link.destination_url}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <span className="rounded-full bg-pageBg px-2.5 py-1 text-xs font-medium">{linkStatus(link)}</span>
                    <button type="button" onClick={() => void copyLink(link)} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium">
                      {copiedId === link.id ? 'Copied' : 'Copy'}
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          {copyError && <p role="alert" className="px-4 py-2 text-sm text-red-700">{copyError}</p>}
          {!loading && !error && totalPages > 1 && (
            <nav aria-label="Link pages" className="flex items-center justify-between gap-3 border-t border-slate-200 p-4 text-sm">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => { resetResult(); setPage(page - 1); }}
                className="rounded-md border border-slate-300 px-3 py-1.5 disabled:opacity-40"
              >
                Previous
              </button>
              <span>Page {page + 1} of {totalPages}</span>
              <button
                type="button"
                disabled={page + 1 >= totalPages}
                onClick={() => { resetResult(); setPage(page + 1); }}
                className="rounded-md border border-slate-300 px-3 py-1.5 disabled:opacity-40"
              >
                Next
              </button>
            </nav>
          )}
        </div>
      </div>
    </section>
  );
}
