import { Trash2, UserPlus, Users } from 'lucide-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { listMembers, type Member } from '../api/projects';
import { addAssignee, removeAssignee, type Task } from '../api/tasks';
import { userLabel } from '../labels';
import { Alert, Avatar } from '../ui/Bits';

/**
 * Assignees of a task (Ex. 6, FR8): several eligible users (active project members), each removable.
 * Eligibility and duplicates are checked again on the server (D21, D22).
 */
export function AssigneesSection({ task, onChanged }: { task: Task; onChanged: () => Promise<void> }) {
  const { t } = useTranslation();
  const [members, setMembers] = useState<Member[] | null>(null);
  const [selected, setSelected] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  useEffect(() => {
    listMembers(task.projectId)
      .then(setMembers)
      .catch(() => setMembers([]));
  }, [task.projectId]);

  const assignedIds = new Set(task.assignees.map((a) => a.id));
  const eligible = (members ?? []).filter((m) => !assignedIds.has(m.user.id));

  function messageFor(e: unknown): string | null {
    if (e instanceof ApiError) {
      if (e.status === 401) return null;
      if (e.problem?.code === 'ALREADY_ASSIGNED') return t('assignees.already');
      if (e.fieldCode('userId') === 'NOT_A_PROJECT_MEMBER') return t('assignees.notMember');
      if (e.status === 404) return t('assignees.gone');
    }
    return t('common.genericError');
  }

  async function run(action: () => Promise<unknown>) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await action();
      setSelected('');
    } catch (e) {
      setError(messageFor(e));
    } finally {
      await onChanged(); // show the state confirmed by the API
      setPending(false);
    }
  }

  return (
    <section className="tile" aria-labelledby="assignees-title" data-testid="assignees-section">
      <h2 id="assignees-title">
        {t('assignees.title')} <span className="eyebrow">{task.assignees.length}</span>
      </h2>
      {task.assignees.length === 0 ? (
        <p className="muted row" style={{ gap: 8 }} data-testid="assignees-empty">
          <Users size={16} aria-hidden="true" />
          {t('assignees.empty')}
        </p>
      ) : (
        <ul className="list" data-testid="assignee-list">
          {task.assignees.map((a) => (
            <li key={a.id} className="row-between" data-testid="assignee-item">
              <span className="user-chip" style={{ padding: 0 }}>
                <Avatar name={a.name} small />
                {userLabel(t, a)}
              </span>
              <button
                type="button"
                className="btn btn-small btn-ghost"
                disabled={pending}
                onClick={() => void run(() => removeAssignee(task.id, a.id))}
                aria-label={`${t('common.remove')}: ${userLabel(t, a)}`}
                data-testid="assignee-remove-button"
              >
                <Trash2 size={16} aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && (
        <Alert kind="error" testId="assignees-error">
          {error}
        </Alert>
      )}
      {members === null ? (
        <p className="muted">{t('assignees.loadingMembers')}</p>
      ) : eligible.length === 0 ? (
        <p className="hint" data-testid="assignees-none-eligible">
          {t('assignees.allAssigned')}
        </p>
      ) : (
        <form
          className="row"
          style={{ alignItems: 'flex-end' }}
          onSubmit={(e) => {
            e.preventDefault();
            if (selected) void run(() => addAssignee(task.id, Number(selected)));
          }}
          data-testid="assignee-add-form"
        >
          <label className="field" style={{ flex: '1 1 160px' }}>
            {t('assignees.assignTo')}
            <select value={selected} onChange={(e) => setSelected(e.target.value)} data-testid="assignee-select">
              <option value="">{t('assignees.choose')}</option>
              {eligible.map((m) => (
                <option key={m.user.id} value={m.user.id}>
                  {userLabel(t, m.user)}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="btn btn-primary" disabled={pending || !selected} data-testid="assignee-add-submit">
            <UserPlus size={16} aria-hidden="true" />
            {t('assignees.assign')}
          </button>
        </form>
      )}
    </section>
  );
}
