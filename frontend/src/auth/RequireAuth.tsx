import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation } from 'react-router-dom';
import { Skeleton } from '../ui/Bits';
import { useAuth } from './AuthContext';

/**
 * Protected route: without a session, go to the login page (US1); while a password change is
 * required, only the change-password page is shown (FR1e, D45).
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { state } = useAuth();
  const location = useLocation();
  const { t } = useTranslation();

  if (state.status === 'loading') {
    return (
      <div className="shell">
        <div className="board" style={{ padding: 24 }}>
          <Skeleton rows={4} label={t('common.loading')} testId="loading" />
        </div>
      </div>
    );
  }
  if (state.status === 'anonymous') {
    // after an explicit logout, the next person to log in starts on the dashboard
    return <Navigate to="/login" replace state={state.loggedOut ? undefined : { from: location.pathname }} />;
  }
  if (state.user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  return <>{children}</>;
}
