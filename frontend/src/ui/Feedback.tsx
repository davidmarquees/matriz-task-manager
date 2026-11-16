import { CircleCheck } from 'lucide-react';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import { useTranslation } from 'react-i18next';

interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  danger?: boolean;
}

interface FeedbackValue {
  /** Accessible confirmation dialog for destructive actions (NFR6); resolves true when confirmed. */
  confirm(options: ConfirmOptions): Promise<boolean>;
  /** Short success message, announced to screen readers. */
  toast(message: string): void;
}

const FeedbackContext = createContext<FeedbackValue | null>(null);

interface PendingConfirm extends ConfirmOptions {
  resolve(value: boolean): void;
}

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<PendingConfirm | null>(null);
  const [toasts, setToasts] = useState<{ id: number; message: string }[]>([]);
  const nextId = useRef(1);

  const confirm = useCallback(
    (options: ConfirmOptions) => new Promise<boolean>((resolve) => setPending({ ...options, resolve })),
    [],
  );

  const toast = useCallback((message: string) => {
    const id = nextId.current++;
    setToasts((list) => [...list, { id, message }]);
    setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), 3500);
  }, []);

  const value = useMemo(() => ({ confirm, toast }), [confirm, toast]);

  return (
    <FeedbackContext.Provider value={value}>
      {children}
      {pending && (
        <ConfirmDialog
          {...pending}
          onClose={(result) => {
            pending.resolve(result);
            setPending(null);
          }}
        />
      )}
      <div className="toasts" role="status" aria-live="polite" data-testid="toasts">
        {toasts.map((t) => (
          <div key={t.id} className="toast" data-testid="toast">
            <CircleCheck size={18} aria-hidden="true" />
            {t.message}
          </div>
        ))}
      </div>
    </FeedbackContext.Provider>
  );
}

function ConfirmDialog({
  title,
  message,
  confirmLabel,
  danger,
  onClose,
}: ConfirmOptions & { onClose(result: boolean): void }) {
  const { t } = useTranslation();
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    cancelRef.current?.focus(); // the safe choice has the focus
    return () => previous?.focus?.();
  }, []);

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose(false);
      return;
    }
    if (event.key === 'Tab' && dialogRef.current) {
      // keep the focus inside the dialog
      const items = dialogRef.current.querySelectorAll<HTMLElement>('button');
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
  }

  return (
    <div className="dialog-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose(false)}>
      <div
        ref={dialogRef}
        className="dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-message"
        onKeyDown={onKeyDown}
        data-testid="confirm-dialog"
      >
        <h2 id="confirm-title">{title}</h2>
        <p id="confirm-message" className="muted">
          {message}
        </p>
        <div className="dialog-actions">
          <button ref={cancelRef} type="button" className="btn" onClick={() => onClose(false)} data-testid="confirm-cancel">
            {t('common.cancel')}
          </button>
          <button
            type="button"
            className={danger ? 'btn btn-primary' : 'btn btn-primary'}
            onClick={() => onClose(true)}
            data-testid="confirm-ok"
          >
            {confirmLabel ?? t('confirm.confirm')}
          </button>
        </div>
      </div>
    </div>
  );
}

export function useFeedback(): FeedbackValue {
  const value = useContext(FeedbackContext);
  if (!value) throw new Error('useFeedback must be used inside FeedbackProvider');
  return value;
}
