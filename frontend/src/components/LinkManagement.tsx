import { useState, type FormEvent } from 'react';
import { deleteLink, updateLink, type DashboardLink } from '../api';

export type LinkAction = 'edit' | 'toggle' | 'delete';

interface LinkManagementProps {
  link: DashboardLink;
  action: LinkAction;
  onClose: () => void;
  onSaved: (message: string) => void;
}

function localExpiry(value: string | null) {
  if (!value) return '';
  const date = new Date(value);
  const localTime = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return localTime.toISOString().slice(0, 16);
}

export default function LinkManagement({ link, action, onClose, onSaved }: LinkManagementProps) {
  const [title, setTitle] = useState(link.title ?? '');
  const originalExpiry = localExpiry(link.expires_at);
  const [expiry, setExpiry] = useState(originalExpiry);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const nextStatus = link.active ? 'Deactivate' : 'Reactivate';
  const titleChanged = title !== (link.title ?? '');
  const expiryChanged = expiry !== originalExpiry;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setError('');

    try {
      if (action === 'delete') {
        await deleteLink(link.id);
        onSaved('Link deleted.');
      } else if (action === 'toggle') {
        await updateLink(link.id, { active: !link.active });
        onSaved(link.active ? 'Link deactivated.' : 'Link reactivated.');
      } else {
        await updateLink(link.id, {
          ...(titleChanged && { title: title.trim() || null }),
          ...(expiryChanged && { expires_at: expiry ? new Date(expiry).toISOString() : null }),
        });
        onSaved('Link updated.');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not save this change.');
      setSaving(false);
    }
  }

  const heading = action === 'edit' ? 'Edit link' : action === 'delete' ? 'Delete link?' : `${nextStatus} link?`;
  const buttonText = action === 'edit' ? 'Save changes' : action === 'delete' ? 'Delete link' : nextStatus;

  return (
    <form onSubmit={handleSubmit} aria-busy={saving} className="space-y-4 rounded-md border border-slate-200 bg-pageBg p-4">
      <h2 className="font-semibold">{heading}</h2>
      {action === 'edit' && (
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="flex flex-col gap-1 text-sm font-medium">
            Title
            <input
              type="text"
              value={title}
              maxLength={255}
              disabled={saving}
              onChange={(event) => setTitle(event.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2"
            />
          </label>
          <label className="flex min-w-0 flex-col gap-1 text-sm font-medium">
            Expires (local time)
            <input
              type="datetime-local"
              value={expiry}
              disabled={saving}
              onChange={(event) => setExpiry(event.target.value)}
              className="w-full rounded-md border border-slate-300 bg-white px-3 py-2"
            />
            <span className="text-xs font-normal text-slate-600">Leave blank for no expiration.</span>
          </label>
        </div>
      )}
      {action === 'delete' && <p className="text-sm">The short URL will stop redirecting and this link will disappear from your dashboard.</p>}
      {action === 'toggle' && (
        <p className="text-sm">
          {link.active
            ? 'The short URL will stop redirecting until you reactivate it.'
            : 'The short URL can redirect again once the link is active and its expiry date is in the future or cleared.'}
        </p>
      )}
      {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
      <div className="flex flex-wrap gap-2">
        <button
          type="submit"
          disabled={saving || (action === 'edit' && !titleChanged && !expiryChanged)}
          className={`rounded-md px-3 py-2 text-sm font-semibold disabled:opacity-50 ${action === 'delete' ? 'bg-red-700 text-white' : 'bg-primary'}`}
        >
          {saving ? 'Saving…' : buttonText}
        </button>
        <button type="button" disabled={saving} onClick={onClose} className="rounded-md border border-slate-300 bg-white px-3 py-2 text-sm">
          Cancel
        </button>
      </div>
    </form>
  );
}
