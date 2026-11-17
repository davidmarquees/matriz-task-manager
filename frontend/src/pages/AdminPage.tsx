import { History, Search, UserPlus, Users } from 'lucide-react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';
import {
  anonymiseAccount,
  createAccount,
  deactivateAccount,
  listAccounts,
  listAudit,
  reactivateAccount,
  type AccountStatus,
  type AdminAccount,
  type AuditEntry,
} from '../api/admin';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { formatDate, formatTime, userLabel } from '../labels';
import { Alert, Avatar, EmptyState, Skeleton } from '../ui/Bits';
import { useFeedback } from '../ui/Feedback';

const MIN_PASSWORD = 8; // A21
const MAX_PASSWORD = 72;
const NAME_MAX = 100;

/** Problem code of a refused account action → message key (D1, D3, D4, D5, D44). */
function adminMessageKey(e: unknown): string | null {
  if (!(e instanceof ApiError)) return 'common.genericError';
  if (e.status === 401) return null; // the app goes to the login page
  switch (e.problem?.code) {
    case 'EMAIL_IN_USE':
      return 'admin.emailInUse';
    case 'LAST_ADMIN':
      return 'admin.lastAdmin';
    case 'ONLY_MANAGER':
      return 'admin.onlyManager';
    case 'NOT_ACTIVE':
      return 'admin.notActive';
    case 'ALREADY_ACTIVE':
      return 'admin.alreadyActive';
    case 'ANONYMISED':
      return 'admin.isAnonymised';
    case 'NOT_DEACTIVATED':
      return 'admin.notDeactivated';
  }
  if (e.fieldCode('initialPassword') === 'TOO_SHORT') return 'password.tooShort';
  if (e.fieldCode('initialPassword') === 'TOO_LONG') return 'password.tooLong';
  if (e.fieldCode('name') === 'INVALID_CHARACTER') return 'common.invalidCharacters'; // D49
  if (e.fieldCode('email')) return 'admin.invalidEmail';
  if (e.fieldCode('name') === 'TOO_LONG') return 'admin.nameTooLong';
  if (e.status === 422) return 'admin.fillAll';
  return 'common.genericError';
}

/** Account administration (FR1a, FR1b, FR1d, US9, US10, US12): Admin account only. */
export function AdminPage() {
  const { t } = useTranslation();
  const { state } = useAuth();
  const [auditUser, setAuditUser] = useState<AdminAccount | null>(null);
  const [auditKey, setAuditKey] = useState(0);

  if (state.status !== 'authenticated') return null;
  if (state.user.accountRole !== 'ADMIN') return <Navigate to="/" replace />;

  return (
    <main className="stack" data-testid="admin-page">
      <div className="page-head">
        <div className="page-head-text">
          <span className="eyebrow">{t('admin.subtitle')}</span>
          <h1 className="page-title">{t('admin.title')}</h1>
        </div>
      </div>
      <div className="grid-main">
        <AccountsSection
          currentUserId={state.user.id}
          onChanged={() => setAuditKey((k) => k + 1)}
          onShowAudit={(account) => {
            setAuditUser(account);
            document.getElementById('audit-title')?.scrollIntoView({ behavior: 'smooth' });
          }}
        />
        <CreateAccountForm onCreated={() => setAuditKey((k) => k + 1)} />
      </div>
      <AuditSection user={auditUser} onShowAll={() => setAuditUser(null)} refreshKey={auditKey} />
    </main>
  );
}

function CreateAccountForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useTranslation();
  const { toast } = useFeedback();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<'MANAGER' | 'MEMBER'>('MEMBER');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!name.trim() || !email.trim() || !password) {
      setError(t('admin.fillAll'));
      return;
    }
    if (password.length < MIN_PASSWORD) {
      setError(t('password.tooShort', { min: MIN_PASSWORD }));
      return;
    }
    setPending(true);
    setError(null);
    try {
      await createAccount({ name: name.trim(), email: email.trim(), accountRole: role, initialPassword: password });
      toast(t('admin.created'));
      setName('');
      setEmail('');
      setPassword('');
      setRole('MEMBER');
      onCreated();
      window.dispatchEvent(new Event('matriz:accounts-changed'));
    } catch (e) {
      const key = adminMessageKey(e);
      if (key) setError(t(key, { min: MIN_PASSWORD, max: MAX_PASSWORD }));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="tile" aria-labelledby="create-account-title" style={{ alignSelf: 'start' }}>
      <h2 id="create-account-title">{t('admin.createTitle')}</h2>
      <form className="form" onSubmit={onSubmit} noValidate data-testid="account-create-form">
        <label className="field">
          {t('admin.name')}
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={NAME_MAX} autoComplete="off" data-testid="account-name" />
        </label>
        <label className="field">
          {t('admin.email')}
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" data-testid="account-email" />
        </label>
        <label className="field">
          {t('admin.role')}
          <select value={role} onChange={(e) => setRole(e.target.value as 'MANAGER' | 'MEMBER')} data-testid="account-role">
            <option value="MEMBER">{t('roles.MEMBER')}</option>
            <option value="MANAGER">{t('roles.MANAGER')}</option>
          </select>
        </label>
        <label className="field">
          {t('admin.initialPassword')}
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            maxLength={MAX_PASSWORD}
            autoComplete="new-password"
            aria-describedby="initial-password-hint"
            data-testid="account-password"
          />
          <span id="initial-password-hint" className="hint">
            {t('admin.initialHint', { min: MIN_PASSWORD })}
          </span>
        </label>
        {error && (
          <Alert kind="error" testId="account-create-error">
            {error}
          </Alert>
        )}
        <button type="submit" className="btn btn-primary" disabled={pending} data-testid="account-create-submit">
          <UserPlus size={16} aria-hidden="true" />
          {pending ? t('common.saving') : t('admin.create')}
        </button>
      </form>
    </section>
  );
}

function AccountsSection({
  currentUserId,
  onChanged,
  onShowAudit,
}: {
  currentUserId: number;
  onChanged: () => void;
  onShowAudit: (account: AdminAccount) => void;
}) {
  const { t, i18n } = useTranslation();
  const { confirm, toast } = useFeedback();
  const [status, setStatus] = useState<AccountStatus | ''>('');
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [accounts, setAccounts] = useState<AdminAccount[]>([]);
  const [load, setLoad] = useState<'loading' | 'ready' | 'error'>('loading');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [reactivating, setReactivating] = useState<number | null>(null);
  const [newPassword, setNewPassword] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => setQuery(search), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const reload = useCallback(async () => {
    try {
      setAccounts(await listAccounts(status, query));
      setLoad('ready');
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) setLoad('error');
    }
  }, [status, query]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    const listener = () => void reload();
    window.addEventListener('matriz:accounts-changed', listener);
    return () => window.removeEventListener('matriz:accounts-changed', listener);
  }, [reload]);

  async function run(action: () => Promise<unknown>, success: string) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await action();
      toast(success);
      setReactivating(null);
      setNewPassword('');
      onChanged();
      await reload();
    } catch (e) {
      const key = adminMessageKey(e);
      if (key) setError(t(key, { min: MIN_PASSWORD, max: MAX_PASSWORD }));
    } finally {
      setPending(false);
    }
  }

  async function onDeactivate(account: AdminAccount) {
    const ok = await confirm({
      title: t('admin.deactivateTitle'),
      message: t('admin.deactivateText', { name: account.name ?? account.email }),
      confirmLabel: t('admin.deactivate'),
      danger: true,
    });
    if (ok) void run(() => deactivateAccount(account.id), t('admin.deactivated'));
  }

  async function onAnonymise(account: AdminAccount) {
    const ok = await confirm({
      title: t('admin.anonymiseTitle'),
      message: t('admin.anonymiseText', { name: account.name ?? account.email }),
      confirmLabel: t('admin.anonymise'),
      danger: true,
    });
    if (ok) void run(() => anonymiseAccount(account.id), t('admin.anonymised'));
  }

  function onReactivate(event: FormEvent, account: AdminAccount) {
    event.preventDefault();
    if (newPassword.length < MIN_PASSWORD) {
      setError(t('password.tooShort', { min: MIN_PASSWORD }));
      return;
    }
    void run(() => reactivateAccount(account.id, newPassword), t('admin.reactivated'));
  }

  return (
    <section className="tile" aria-labelledby="accounts-title" data-testid="accounts-section">
      <h2 id="accounts-title">
        {t('admin.title')} {load === 'ready' && <span className="eyebrow">{accounts.length}</span>}
      </h2>
      <div className="field-row">
        <label className="field">
          {t('admin.search')}
          <span style={{ position: 'relative', display: 'block' }}>
            <Search size={18} aria-hidden="true" className="search-icon" />
            <input type="search" value={search} onChange={(e) => setSearch(e.target.value)} maxLength={100} data-testid="account-search" />
          </span>
        </label>
        <label className="field">
          {t('admin.statusFilter')}
          <select value={status} onChange={(e) => setStatus(e.target.value as AccountStatus | '')} data-testid="account-status-filter">
            <option value="">{t('admin.allStatuses')}</option>
            <option value="ACTIVE">{t('admin.statuses.ACTIVE')}</option>
            <option value="DEACTIVATED">{t('admin.statuses.DEACTIVATED')}</option>
            <option value="ANONYMISED">{t('admin.statuses.ANONYMISED')}</option>
          </select>
        </label>
      </div>
      {error && (
        <Alert kind="error" testId="account-action-error">
          {error}
        </Alert>
      )}
      {load === 'loading' && <Skeleton rows={4} label={t('admin.loading')} testId="accounts-loading" />}
      {load === 'error' && (
        <Alert kind="error" testId="accounts-error">
          {t('admin.loadError')}{' '}
          <button type="button" className="link-button" onClick={() => void reload()}>
            {t('common.retry')}
          </button>
        </Alert>
      )}
      {load === 'ready' && accounts.length === 0 && <EmptyState icon={Users} title={t('admin.empty')} testId="accounts-empty" />}
      {load === 'ready' && accounts.length > 0 && (
        <ul className="list" data-testid="account-list">
          {accounts.map((account) => {
            const label = account.name ?? t('common.removedUser');
            const self = account.id === currentUserId;
            return (
              <li key={account.id} className="stack" style={{ gap: 8 }} data-testid="account-item" data-account-email={account.email}>
                <div className="row-between">
                  <span className="user-chip" style={{ padding: 0, minWidth: 0 }}>
                    <Avatar name={account.name} small tone={account.status === 'ACTIVE' ? 'pine' : undefined} />
                    <span style={{ minWidth: 0 }}>
                      <strong data-testid="account-item-name">{label}</strong>
                      {self && <span className="muted"> ({t('admin.you')})</span>}
                      <span className="muted" style={{ display: 'block', fontSize: 13, overflowWrap: 'anywhere' }}>
                        {account.email}
                      </span>
                    </span>
                  </span>
                  <span className="row" style={{ gap: 6, justifyContent: 'flex-end' }}>
                    <span className="pill pill-role">{t(`roles.${account.accountRole}`)}</span>
                    <span className={account.status === 'ACTIVE' ? 'pill pill-done' : 'pill pill-todo'} data-testid="account-item-status">
                      {t(`admin.statuses.${account.status}`)}
                    </span>
                  </span>
                </div>
                <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
                  {account.mustChangePassword && account.status === 'ACTIVE' && (
                    <span className="eyebrow" style={{ fontSize: 11 }}>
                      {t('admin.mustChange')}
                    </span>
                  )}
                  {account.deactivatedAt && (
                    <span className="eyebrow" style={{ fontSize: 11 }}>
                      {formatDate(account.deactivatedAt, i18n.language)}
                    </span>
                  )}
                  <span style={{ flex: 1 }} />
                  <button type="button" className="btn btn-small btn-ghost" onClick={() => onShowAudit(account)} data-testid="account-audit-button">
                    <History size={15} aria-hidden="true" />
                    {t('admin.auditShow')}
                  </button>
                  {account.status === 'ACTIVE' && !self && (
                    <button type="button" className="btn btn-small" onClick={() => void onDeactivate(account)} disabled={pending} data-testid="account-deactivate-button">
                      {t('admin.deactivate')}
                    </button>
                  )}
                  {account.status === 'DEACTIVATED' && (
                    <>
                      <button
                        type="button"
                        className="btn btn-small"
                        onClick={() => {
                          setReactivating(reactivating === account.id ? null : account.id);
                          setNewPassword('');
                          setError(null);
                        }}
                        aria-expanded={reactivating === account.id}
                        disabled={pending}
                        data-testid="account-reactivate-button"
                      >
                        {t('admin.reactivate')}
                      </button>
                      <button type="button" className="btn btn-small btn-danger" onClick={() => void onAnonymise(account)} disabled={pending} data-testid="account-anonymise-button">
                        {t('admin.anonymise')}
                      </button>
                    </>
                  )}
                </div>
                {reactivating === account.id && (
                  <form className="row" style={{ alignItems: 'flex-end' }} onSubmit={(e) => onReactivate(e, account)} noValidate data-testid="account-reactivate-form">
                    <label className="field" style={{ flex: 1 }}>
                      {t('admin.initialPassword')}
                      <input
                        type="password"
                        value={newPassword}
                        onChange={(e) => setNewPassword(e.target.value)}
                        maxLength={MAX_PASSWORD}
                        autoComplete="new-password"
                        data-testid="account-reactivate-password"
                      />
                    </label>
                    <button type="submit" className="btn btn-primary btn-small" disabled={pending} data-testid="account-reactivate-submit">
                      {t('admin.reactivateConfirm')}
                    </button>
                  </form>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}

function AuditSection({ user, onShowAll, refreshKey }: { user: AdminAccount | null; onShowAll: () => void; refreshKey: number }) {
  const { t, i18n } = useTranslation();
  const [items, setItems] = useState<AuditEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const userId = user?.id;

  const load = useCallback(
    async (nextPage: number) => {
      try {
        const result = await listAudit(nextPage, userId);
        setItems((current) => (nextPage === 0 ? result.items : [...current, ...result.items]));
        setTotal(result.totalItems);
        setPage(nextPage);
        setStatus('ready');
      } catch (e) {
        if (!(e instanceof ApiError && e.status === 401)) setStatus('error');
      }
    },
    [userId],
  );

  useEffect(() => {
    void load(0);
  }, [load, refreshKey]);

  return (
    <section className="tile" aria-labelledby="audit-title" data-testid="audit-section">
      <div className="row-between">
        <div className="stack" style={{ gap: 4 }}>
          <h2 id="audit-title">{user ? t('admin.auditOf', { name: user.name ?? t('common.removedUser') }) : t('admin.auditTitle')}</h2>
          <p className="muted" style={{ fontSize: 13 }}>
            {t('admin.auditSubtitle')}
          </p>
        </div>
        {user && (
          <button type="button" className="btn btn-small" onClick={onShowAll} data-testid="audit-show-all">
            {t('admin.auditAll')}
          </button>
        )}
      </div>
      {status === 'loading' && <Skeleton rows={3} label={t('common.loading')} testId="audit-loading" />}
      {status === 'error' && (
        <Alert kind="error" testId="audit-error">
          {t('admin.auditLoadError')}{' '}
          <button type="button" className="link-button" onClick={() => void load(0)}>
            {t('common.retry')}
          </button>
        </Alert>
      )}
      {status === 'ready' && items.length === 0 && <EmptyState icon={History} title={t('admin.auditEmpty')} testId="audit-empty" />}
      {status === 'ready' && items.length > 0 && (
        <ol className="list" data-testid="audit-list">
          {items.map((entry) => (
            <li key={entry.id} className="comment" data-testid="audit-item">
              <Avatar name={entry.actor?.name ?? null} small />
              <div className="stack" style={{ gap: 2, minWidth: 0 }}>
                <span>
                  <strong>{entry.actor ? userLabel(t, entry.actor) : t('admin.system')}</strong>{' '}
                  {t(`admin.actions.${entry.action}`, {
                    defaultValue: entry.action,
                    target: userLabel(t, entry.targetUser),
                    project: entry.projectName ?? '—',
                  })}
                </span>
                <span className="eyebrow" style={{ fontSize: 11 }}>
                  {formatDate(entry.createdAt, i18n.language)} {formatTime(entry.createdAt, i18n.language)}
                </span>
              </div>
            </li>
          ))}
        </ol>
      )}
      {status === 'ready' && items.length < total && (
        <button type="button" className="btn btn-small" onClick={() => void load(page + 1)} data-testid="audit-more" style={{ alignSelf: 'flex-start' }}>
          {t('admin.auditMore')}
        </button>
      )}
    </section>
  );
}
