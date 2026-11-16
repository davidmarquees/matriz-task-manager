import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { AppShell } from './components/AppShell';
import { ChangePasswordPage } from './pages/ChangePasswordPage';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';
import { ProjectDetailPage } from './pages/ProjectDetailPage';
import { ProjectsPage } from './pages/ProjectsPage';
import { TaskDetailPage } from './pages/TaskDetailPage';
import { PreferencesProvider } from './prefs/PreferencesContext';
import { FeedbackProvider } from './ui/Feedback';

function Protected({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <AppShell>{children}</AppShell>
    </RequireAuth>
  );
}

export function App() {
  return (
    <AuthProvider>
      <PreferencesProvider>
        <FeedbackProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/" element={<Protected><DashboardPage /></Protected>} />
              <Route path="/change-password" element={<Protected><ChangePasswordPage /></Protected>} />
              <Route path="/projects" element={<Protected><ProjectsPage /></Protected>} />
              <Route path="/projects/:projectId" element={<Protected><ProjectDetailPage /></Protected>} />
              <Route path="/tasks/:taskId" element={<Protected><TaskDetailPage /></Protected>} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </FeedbackProvider>
      </PreferencesProvider>
    </AuthProvider>
  );
}
