import { useTranslation } from 'react-i18next';

/** Dark left half of the login and password reset pages (same look as the rest of the app). */
export function AuthHero() {
  const { t } = useTranslation();
  return (
    <section className="auth-hero" aria-hidden="false">
      <div className="brand" style={{ color: 'inherit' }}>
        <span className="brand-mark" aria-hidden="true">
          M
        </span>
        <span className="brand-name">{t('app.name')}</span>
      </div>
      <div className="stack" style={{ gap: 18 }}>
        <h1>
          {t('login.heroLine1')}
          <br />
          {t('login.heroLine2')}
          <br />
          <span className="accent">{t('login.heroLine3')}</span>
        </h1>
        <p style={{ maxWidth: 420, color: 'var(--on-dark-2)' }}>{t('app.tagline')}</p>
      </div>
      <div className="auth-bars" aria-hidden="true">
        <span style={{ height: '45%', border: '1.5px solid var(--on-dark)', backgroundImage: 'repeating-linear-gradient(135deg, var(--on-dark) 0 1.5px, transparent 1.5px 7px)' }} />
        <span style={{ height: '70%', background: '#e3b867' }} />
        <span style={{ height: '100%', background: '#7db39a' }} />
        <span style={{ height: '60%', background: '#d2694f' }} />
      </div>
    </section>
  );
}
