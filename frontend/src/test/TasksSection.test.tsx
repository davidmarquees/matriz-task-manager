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

describe('TasksSection create form (US3, Q-5)', () => {
  it('flags the title field as invalid and links it to the message when the title is only spaces', async () => {
    const api = mockApi({
      'GET /auth/me': { status: 200, body: demoUser },
      'GET /projects/7/members': { status: 200, body: [] },
      'GET /projects/7/tasks': { status: 200, body: emptyPage },
    });
    renderWithApp(<TasksSection projectId={7} onTasksChanged={() => undefined} />);

    fireEvent.click(await screen.findByTestId('task-create-open'));
    const input = await screen.findByTestId('task-title-input');
    expect(input).not.toHaveAttribute('aria-invalid');
    fireEvent.change(input, { target: { value: '   ' } });
    fireEvent.click(screen.getByTestId('task-create-submit'));

    expect(await screen.findByTestId('task-create-error')).toHaveTextContent('O título é obrigatório');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription(/O título é obrigatório/);
    expect(api.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false);
  });

  it('counts an emoji as one character, so a 150-character title with an emoji is kept whole (Q-3)', async () => {
    mockApi({
      'GET /auth/me': { status: 200, body: demoUser },
      'GET /projects/7/members': { status: 200, body: [] },
      'GET /projects/7/tasks': { status: 200, body: emptyPage },
    });
    renderWithApp(<TasksSection projectId={7} onTasksChanged={() => undefined} />);

    fireEvent.click(await screen.findByTestId('task-create-open'));
    const input = await screen.findByTestId('task-title-input');
    const longest = 'x'.repeat(149) + '\u{1F600}'; // 150 characters, 151 UTF-16 units
    fireEvent.change(input, { target: { value: longest } });
    expect(input).toHaveValue(longest);
    expect(input).not.toHaveAttribute('maxlength');

    fireEvent.change(input, { target: { value: longest + 'y' } }); // 151 characters: cut to 150
    expect(input).toHaveValue(longest);
  });

  it('refuses a title of two characters even when one is an emoji (Q-3)', async () => {
    const api = mockApi({
      'GET /auth/me': { status: 200, body: demoUser },
      'GET /projects/7/members': { status: 200, body: [] },
      'GET /projects/7/tasks': { status: 200, body: emptyPage },
    });
    renderWithApp(<TasksSection projectId={7} onTasksChanged={() => undefined} />);

    fireEvent.click(await screen.findByTestId('task-create-open'));
    fireEvent.change(await screen.findByTestId('task-title-input'), { target: { value: 'a\u{1F600}' } });
    fireEvent.click(screen.getByTestId('task-create-submit'));

    expect(await screen.findByTestId('task-create-error')).toHaveTextContent('3');
    expect(api.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false);
  });

  it('shows the invalid-character message when the API refuses a control character (D49)', async () => {
    mockApi({
      'GET /auth/me': { status: 200, body: demoUser },
      'GET /projects/7/members': { status: 200, body: [] },
      'GET /projects/7/tasks': { status: 200, body: emptyPage },
      'POST /projects/7/tasks': {
        status: 422,
        body: { status: 422, title: 'Unprocessable Entity', errors: [{ field: 'title', code: 'INVALID_CHARACTER' }] },
      },
    });
    renderWithApp(<TasksSection projectId={7} onTasksChanged={() => undefined} />);

    fireEvent.click(await screen.findByTestId('task-create-open'));
    const input = await screen.findByTestId('task-title-input');
    fireEvent.change(input, { target: { value: 'ab\u0007cd' } });
    fireEvent.click(screen.getByTestId('task-create-submit'));

    expect(await screen.findByTestId('task-create-error')).toHaveTextContent('O texto contém caracteres não permitidos.');
    expect(input).toHaveAttribute('aria-invalid', 'true');
  });
});
