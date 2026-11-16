import { FolderOpen, KeyRound, LayoutDashboard, LogOut, Menu, X } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { PreferenceSwitches } from './PreferenceSwitches';
import { Avatar } from '../ui/Bits';

/**
 * Layout of every page after login: solid background, translucent board, sidebar (desktop) or
 * top bar with a ☰ menu (mobile, NFR7). One navigation element, so each data-testid exists once.
 */
export function AppShell({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const { state, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [pending, setPending] = useState(false);

  useEffect(() => setMenuOpen(false), [location.pathname]); // close the mobile menu after navigating

  if (state.status !== 'authenticated') return null;
  const user = state.user;

  async function onLogout() {
    setPending(true);
    try {
      await logout();
    } finally {
      navigate('/login', { replace: true });
    }
  }

  return (
    <div className="shell">
      <div className="board">
        <div className="topbar">
          <NavLink to="/" className="brand" aria-label={t('app.name')}>
            <span className="brand-mark" aria-hidden="true">
              M
            </span>
            <span className="brand-name">{t('app.name')}</span>
          </NavLink>
          <button
            type="button"
            className="btn btn-small"
            aria-expanded={menuOpen}
            aria-controls="main-menu"
            onClick={() => setMenuOpen(!menuOpen)}
            data-testid="menu-button"
          >
            {menuOpen ? <X size={18} aria-hidden="true" /> : <Menu size={18} aria-hidden="true" />}
            <span className="visually-hidden">{menuOpen ? t('nav.closeMenu') : t('nav.openMenu')}</span>
          </button>
        </div>

        <aside id="main-menu" className={menuOpen ? 'sidebar open' : 'sidebar'} aria-label={t('nav.label')}>
          <NavLink to="/" className="brand side-brand">
            <span className="brand-mark" aria-hidden="true">
              M
            </span>
            <span className="brand-name">{t('app.name')}</span>
          </NavLink>
          <nav className="nav">
            <NavLink to="/" end data-testid="nav-home">
              <LayoutDashboard size={18} aria-hidden="true" />
              {t('nav.dashboard')}
            </NavLink>
            <NavLink to="/projects" data-testid="nav-projects">
              <FolderOpen size={18} aria-hidden="true" />
              {t('nav.projects')}
            </NavLink>
            <NavLink to="/change-password" data-testid="nav-password">
              <KeyRound size={18} aria-hidden="true" />
              {t('nav.password')}
            </NavLink>
          </nav>
          <div className="side-footer">
            <span className="eyebrow" style={{ paddingLeft: 8 }}>
              {t('nav.preferences')}
            </span>
            <PreferenceSwitches />
            <hr style={{ border: 0, borderTop: '1px solid var(--line)', width: '100%', margin: '4px 0' }} />
            <div className="user-chip">
              <Avatar name={user.name} />
              <span style={{ minWidth: 0 }}>
                <strong data-testid="current-user-name">{user.name}</strong>
                <span className="muted" style={{ fontSize: 13 }} data-testid="current-user-role">
                  {t(`roles.${user.accountRole}`)}
                </span>
              </span>
            </div>
            <button type="button" className="btn btn-small" onClick={onLogout} disabled={pending} data-testid="logout-button">
              <LogOut size={16} aria-hidden="true" />
              {t('nav.logout')}
            </button>
          </div>
        </aside>

        <div className="content">{children}</div>
      </div>
    </div>
  );
}
