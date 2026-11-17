import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { CountsBar } from '../components/CountsBar';
import i18n from '../i18n';
import { en } from '../i18n/en';
import { ptPT } from '../i18n/pt-PT';
import { FeedbackProvider, useFeedback } from '../ui/Feedback';
import { StatusPill } from '../ui/Bits';

function keys(value: object, prefix = ''): string[] {
  return Object.entries(value).flatMap(([k, v]) =>
    typeof v === 'object' && v !== null ? keys(v, `${prefix}${k}.`) : [`${prefix}${k}`],
  );
}

describe('translations (NFR1)', () => {
  it('have the same keys in Portuguese and English', () => {
    expect(keys(en).sort()).toEqual(keys(ptPT).sort());
  });

  it('show status as words in each language, never as API codes', async () => {
    const { rerender } = render(<StatusPill status="IN_PROGRESS" />);
    expect(screen.getByText('Em curso')).toBeInTheDocument();
    await i18n.changeLanguage('en');
    rerender(<StatusPill status="IN_PROGRESS" />);
    expect(screen.getByText('In progress')).toBeInTheDocument();
    expect(screen.queryByText('IN_PROGRESS')).not.toBeInTheDocument();
  });
});

describe('CountsBar (NFR8)', () => {
  it('gives every count a text label, not only a colour', () => {
    render(<CountsBar counts={{ TODO: 3, IN_PROGRESS: 2, DONE: 1 }} testId="counts" />);
    expect(screen.getByTestId('counts-TODO')).toHaveTextContent('3 a fazer');
    expect(screen.getByTestId('counts-IN_PROGRESS')).toHaveTextContent('2 em curso');
    expect(screen.getByTestId('counts-DONE')).toHaveTextContent('1 concluídas');
    expect(screen.getByRole('img')).toHaveAccessibleName('1 de 6 tarefas concluídas');
  });

  it('shows 0 / 0 / 0 for a project without tasks (E2)', () => {
    render(<CountsBar counts={{ TODO: 0, IN_PROGRESS: 0, DONE: 0 }} testId="counts" />);
    expect(screen.getByTestId('counts-TODO')).toHaveTextContent('0 a fazer');
    expect(screen.getByRole('img')).toHaveAccessibleName('Ainda sem tarefas');
  });
});

function DeleteButton() {
  const { confirm } = useFeedback();
  const [result, setResult] = useState('none');
  return (
    <>
      <button type="button" onClick={async () => setResult(String(await confirm({ title: 'Eliminar?', message: 'Não pode ser desfeito.' })))}>
        delete
      </button>
      <output data-testid="result">{result}</output>
    </>
  );
}

describe('confirmation dialog (NFR6)', () => {
  it('starts on the safe choice and cancels with Escape', async () => {
    render(
      <FeedbackProvider>
        <DeleteButton />
      </FeedbackProvider>,
    );
    fireEvent.click(screen.getByText('delete'));

    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveAccessibleName('Eliminar?');
    expect(screen.getByTestId('confirm-cancel')).toHaveFocus();

    fireEvent.keyDown(dialog, { key: 'Escape' });
    expect(await screen.findByTestId('result')).toHaveTextContent('false');
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('resolves true when confirmed', async () => {
    render(
      <FeedbackProvider>
        <DeleteButton />
      </FeedbackProvider>,
    );
    fireEvent.click(screen.getByText('delete'));
    fireEvent.click(await screen.findByTestId('confirm-ok'));
    expect(await screen.findByTestId('result')).toHaveTextContent('true');
  });
});
