import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { listMembers, type Member } from '../api/projects';
import { addAssignee, removeAssignee, type Task } from '../api/tasks';
import { userLabel } from '../labels';

/**
 * Assignees of a task (Worksheet 03, Ex. 6, FR8): several eligible users (active project members),
 * each removable. Eligibility and duplicates are checked again on the server (D21, D22).
 */
export function AssigneesSection({ task, onChanged }: { task: Task; onChanged: () => Promise<void> }) {
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
      if (e.problem?.code === 'ALREADY_ASSIGNED') return 'Esse utilizador já é responsável por esta tarefa.';
      if (e.fieldCode('userId') === 'NOT_A_PROJECT_MEMBER') return 'Só podem ser atribuídos membros ativos do projeto.';
      if (e.status === 404) return 'A tarefa ou a atribuição já não existe.';
    }
    return 'Não foi possível concluir a operação. Tente novamente.';
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
      await onChanged(); // the page shows the state confirmed by the API
      setPending(false);
    }
  }

  return (
    <section className="panel" data-testid="assignees-section">
      <h2>Responsáveis ({task.assignees.length})</h2>
      {task.assignees.length === 0 ? (
        <p className="muted" data-testid="assignees-empty">
          Ainda não há responsáveis.
        </p>
      ) : (
        <ul className="members" data-testid="assignee-list">
          {task.assignees.map((a) => (
            <li key={a.id} data-testid="assignee-item">
              <span>{userLabel(a)}</span>
              <button
                type="button"
                className="secondary"
                disabled={pending}
                onClick={() => void run(() => removeAssignee(task.id, a.id))}
                data-testid="assignee-remove-button"
              >
                Remover
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p role="alert" className="error" data-testid="assignees-error">
          {error}
        </p>
      )}
      {members === null ? (
        <p className="muted">A carregar membros…</p>
      ) : eligible.length === 0 ? (
        <p className="muted" data-testid="assignees-none-eligible">
          Todos os membros do projeto já estão atribuídos.
        </p>
      ) : (
        <form
          className="inline-form"
          onSubmit={(e) => {
            e.preventDefault();
            if (selected) void run(() => addAssignee(task.id, Number(selected)));
          }}
          data-testid="assignee-add-form"
        >
          <label>
            Atribuir a
            <select value={selected} onChange={(e) => setSelected(e.target.value)} data-testid="assignee-select">
              <option value="">Escolha um membro…</option>
              {eligible.map((m) => (
                <option key={m.user.id} value={m.user.id}>
                  {userLabel(m.user)}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={pending || !selected} data-testid="assignee-add-submit">
            Atribuir
          </button>
        </form>
      )}
    </section>
  );
}
