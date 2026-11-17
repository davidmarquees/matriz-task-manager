import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TasksSection } from '../components/TasksSection';
import { demoUser, mockApi, renderWithApp } from './render';

const emptyPage = { items: [], page: 0, size: 10, totalItems: 0 };

describe('TasksSection filters (US6)', () => {
  it('filters by assignee "me" through the API and shows the empty state (E3)', async () => {
    const api = mockApi({
      'GET /auth/me': { status: 200, body: demoUser },
      'GET /projects/7/members': {
        status: 200,
        body: [{ user: { id: 1, name: 'Gestor Demo', active: true }, projectRole: 'MANAGER', addedAt: '2026-11-16T10:00:00Z' }],
      },
      'GET /projects/7/tasks': { status: 200, body: emptyPage },
    });
    renderWithApp(<TasksSection projectId={7} onTasksChanged={() => undefined} />);

    const select = await screen.findByTestId('task-filter-assignee');
    await waitFor(() => expect(screen.getByRole('option', { name: 'Eu (Gestor Demo)' })).toBeInTheDocument());
    fireEvent.change(select, { target: { value: '1' } });

    await waitFor(() =>
      expect(api.mock.calls.some(([url]) => String(url).includes('/projects/7/tasks') && String(url).includes('assigneeId=1'))).toBe(true),
    );
    expect(await screen.findByTestId('tasks-empty')).toHaveTextContent('Nenhuma tarefa encontrada.');
  });

  it('starts filtered by the status that came from the dashboard', async () => {
    const api = mockApi({
      'GET /auth/me': { status: 200, body: demoUser },
      'GET /projects/7/members': { status: 200, body: [] },
      'GET /projects/7/tasks': { status: 200, body: emptyPage },
    });
    renderWithApp(<TasksSection projectId={7} initialStatus="IN_PROGRESS" onTasksChanged={() => undefined} />);

    expect(await screen.findByTestId('task-filter-status')).toHaveValue('IN_PROGRESS');
    await waitFor(() => expect(api.mock.calls.some(([url]) => String(url).includes('status=IN_PROGRESS'))).toBe(true));
  });
});
