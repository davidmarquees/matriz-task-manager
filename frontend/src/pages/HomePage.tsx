import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

/** Temporary home page; the dashboard of the wireframe replaces it in Ex. 7. */
export function HomePage() {
  const { state } = useAuth();
  if (state.status !== 'authenticated') {
    return null;
  }
  return (
    <main className="page" data-testid="home-page">
      <h1>Painel</h1>
      <p>Sessão iniciada como {state.user.name}.</p>
      <p>
        <Link to="/change-password" data-testid="change-password-link">
          Alterar palavra-passe
        </Link>
      </p>
    </main>
  );
}
