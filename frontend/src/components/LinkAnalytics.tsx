import { useEffect, useId, useState } from 'react';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { fetchLinkAnalytics, type LinkAnalyticsData } from '../api';

type AnalyticsResult = { data: LinkAnalyticsData } | { error: string } | null;

function formatDate(date: string) {
  return new Date(date + 'T00:00:00Z').toLocaleDateString(undefined, {
    month: 'short',
    day: 'numeric',
    timeZone: 'UTC',
  });
}

export default function LinkAnalytics({ linkId }: { linkId: number }) {
  const [days, setDays] = useState(30);
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<AnalyticsResult>(null);
  const headingId = useId();

  useEffect(() => {
    const controller = new AbortController();

    fetchLinkAnalytics(linkId, days, controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setResult({ data });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ error: error instanceof Error ? error.message : 'Could not load analytics.' });
        }
      });

    return () => controller.abort();
  }, [linkId, days, attempt]);

  return (
    <section aria-labelledby={headingId} className="rounded-md border border-slate-200 bg-pageBg p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id={headingId} className="font-semibold">Click analytics</h2>
        <label className="flex items-center gap-2 text-sm">
          Date range
          <select
            value={days}
            onChange={(event) => {
              setResult(null);
              setDays(Number(event.target.value));
            }}
            className="rounded-md border border-slate-300 bg-white px-2 py-1.5"
          >
            <option value={7}>Last 7 days</option>
            <option value={30}>Last 30 days</option>
            <option value={90}>Last 90 days</option>
          </select>
        </label>
      </div>

      {!result && <p role="status" className="mt-4 text-sm text-slate-600">Loading click analytics…</p>}
      {result && 'error' in result && (
        <div className="mt-4">
          <p role="alert" className="text-sm text-red-700">{result.error}</p>
          <button
            type="button"
            onClick={() => {
              setResult(null);
              setAttempt((value) => value + 1);
            }}
            className="mt-2 rounded-md border border-slate-300 bg-white px-3 py-1.5 text-sm"
          >
            Retry
          </button>
        </div>
      )}
      {result && 'data' in result && (
        <div className="mt-4">
          <p className="text-sm">
            <strong className="text-xl tabular-nums">{result.data.total_clicks.toLocaleString()}</strong> recorded clicks
          </p>
          <p className="mt-1 text-xs text-slate-600">Dates use {result.data.timezone}. Repeat visits count separately.</p>
          {result.data.total_clicks === 0 ? (
            <p className="mt-4 text-sm text-slate-600">No clicks recorded in this range.</p>
          ) : (
            <div className="mt-4 min-w-0">
              <ResponsiveContainer width="100%" height={192} minWidth={0}>
                <BarChart data={result.data.daily_clicks} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                  <CartesianGrid vertical={false} stroke="#CBD5E1" />
                  <XAxis dataKey="date" tickFormatter={formatDate} minTickGap={24} tick={{ fontSize: 12 }} />
                  <YAxis allowDecimals={false} width={48} tick={{ fontSize: 12 }} />
                  <Tooltip labelFormatter={(label) => formatDate(String(label))} />
                  <Bar dataKey="clicks" name="Clicks" fill="#075985" isAnimationActive={false} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
          <details className="mt-3 text-sm">
            <summary className="cursor-pointer font-medium">Daily counts</summary>
            <div className="mt-2 max-h-48 overflow-auto">
              <table className="w-full text-left tabular-nums">
                <caption className="sr-only">Recorded clicks by date ({result.data.timezone})</caption>
                <thead><tr><th scope="col">Date</th><th scope="col" className="text-right">Clicks</th></tr></thead>
                <tbody className="divide-y divide-slate-200">
                  {result.data.daily_clicks.map((day) => (
                    <tr key={day.date}><td className="py-1">{day.date}</td><td className="text-right">{day.clicks.toLocaleString()}</td></tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </div>
      )}
    </section>
  );
}
