import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { kindOf, mondayOf, WeekDue } from '../components/WeekDue';
import { DashboardPage } from '../pages/DashboardPage';
import { mockApi, renderWithApp } from './render';

const me = { id: 2, name: 'Maria Gestora', email: 'manager@example.test', accountRole: 'MANAGER', mustChangePassword: false, locale: 'pt-PT', theme: 'LIGHT' };

const dashboard = {
  totals: { projects: 2, taskCounts: { TODO: 3, IN_PROGRESS: 1, DONE: 4 }, priorityCounts: { LOW: 1, MEDIUM: 2, HIGH: 1 } },
  projects: [
    { id: 10, name: 'Alpha', description: null, myRole: 'MANAGER', memberCount: 2, taskCounts: { TODO: 1, IN_PROGRESS: 1, DONE: 2 } },
    { id: 11, name: 'Beta', description: null, myRole: 'MEMBER', memberCount: 3, taskCounts: { TODO: 2, IN_PROGRESS: 0, DONE: 2 } },
  ],
};

const today = new Date();
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

describe('DashboardPage (UI polish)', () => {
  it('draws the status and priority charts from the totals of every project', async () => {
    mockApi({ 'GET /auth/me': { status: 200, body: me }, 'GET /dashboard': { status: 200, body: dashboard }, 'GET /dashboard/due-tasks': { status: 200, body: { items: [] } } });
    renderWithApp(<DashboardPage />, '/');

    const status = await screen.findByTestId('dashboard-chart-status');
    expect(within(status).getByTestId('dashboard-chart-status-DONE')).toHaveTextContent('4 50%');
    expect(within(status).getByTestId('dashboard-chart-status-TODO')).toHaveTextContent('3 38%');
    const priority = screen.getByTestId('dashboard-chart-priority');
    expect(within(priority).getByTestId('dashboard-chart-priority-MEDIUM')).toHaveTextContent('2 50%');
    expect(screen.getByTestId('dashboard-progress')).toHaveTextContent('50%');
  });

  it('says so when there are no tasks instead of drawing empty bars', async () => {
    const empty = {
      totals: { projects: 1, taskCounts: { TODO: 0, IN_PROGRESS: 0, DONE: 0 }, priorityCounts: { LOW: 0, MEDIUM: 0, HIGH: 0 } },
      projects: [{ id: 10, name: 'Alpha', description: null, myRole: 'MANAGER', memberCount: 1, taskCounts: { TODO: 0, IN_PROGRESS: 0, DONE: 0 } }],
    };
    mockApi({ 'GET /auth/me': { status: 200, body: me }, 'GET /dashboard': { status: 200, body: empty }, 'GET /dashboard/due-tasks': { status: 200, body: { items: [] } } });
    renderWithApp(<DashboardPage />, '/');

    expect(await screen.findByTestId('dashboard-chart-status-empty')).toBeInTheDocument();
    expect(screen.getByTestId('dashboard-chart-priority-empty')).toBeInTheDocument();
    expect(await screen.findByTestId('dashboard-week-list')).toHaveTextContent('Sem prazos nesta semana.');
  });

  it('shows an error with a retry when the dashboard cannot load', async () => {
    mockApi({ 'GET /auth/me': { status: 200, body: me }, 'GET /dashboard': { status: 500, body: { status: 500, title: 'Internal Server Error' } } });
    renderWithApp(<DashboardPage />, '/');

    expect(await screen.findByTestId('dashboard-error')).toHaveTextContent('Não foi possível carregar o painel.');
  });
});

describe('WeekDue', () => {
  it('asks for the current week and lists the tasks of the day that is pointed at', async () => {
    const monday = mondayOf(today);
    const day = (n: number) => iso(new Date(monday.getFullYear(), monday.getMonth(), monday.getDate() + n));
    const api = mockApi({
      'GET /auth/me': { status: 200, body: me },
      'GET /dashboard/due-tasks': {
        status: 200,
        body: { items: [{ id: 1, projectId: 10, projectName: 'Alpha', title: 'Publish the page', status: 'TODO', priority: 'HIGH', dueDate: day(4) }] },
      },
    });
    renderWithApp(<WeekDue now={today} />, '/');

    const friday = await screen.findByTestId(`dashboard-week-day-${day(4)}`);
    const url = new URL(String(api.mock.calls.find(([u]) => String(u).includes('due-tasks'))![0]), 'http://localhost');
    expect(url.searchParams.get('from')).toBe(day(0));
    expect(url.searchParams.get('to')).toBe(day(6));

    fireEvent.mouseEnter(friday);
    expect(friday).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('dashboard-week-task')).toHaveTextContent('Publish the page');
    expect(screen.getByTestId('dashboard-week-task')).toHaveTextContent('Alpha · A fazer');
  });

  it('shows an error with a retry when the due dates cannot load', async () => {
    mockApi({ 'GET /auth/me': { status: 200, body: me }, 'GET /dashboard/due-tasks': { status: 500, body: { status: 500, title: 'Error' } } });
    renderWithApp(<WeekDue now={today} />, '/');

    expect(await screen.findByTestId('dashboard-week-error')).toHaveTextContent('Não foi possível carregar os prazos.');
  });

  it('marks a task overdue only when it is late and not done (assumption)', () => {
    const task = { id: 1, projectId: 1, projectName: 'P', title: 'T', priority: 'LOW' as const, dueDate: '2026-11-17' };
    expect(kindOf({ ...task, status: 'TODO' }, '2026-11-18')).toBe('overdue');
    expect(kindOf({ ...task, status: 'DONE' }, '2026-11-18')).toBe('done');
    expect(kindOf({ ...task, status: 'IN_PROGRESS', dueDate: '2026-11-18' }, '2026-11-18')).toBe('progress');
  });
});
