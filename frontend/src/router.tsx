// Route table (architecture.md §11). A pathless root wraps everything in AuthProvider so the
// auth context is available to every screen (and to tests that mount `routes` directly). Auth
// screens render standalone; app routes render inside AppLayout. Route guards (redirect unauthed
// → /login) arrive in HTS-014.

import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { AppLayout } from './layout/AppLayout';
import { LoginPage } from './features/auth/LoginPage';
import { SignupPage } from './features/auth/SignupPage';
import { VerifyPage } from './features/auth/VerifyPage';
import { TeamsPage } from './features/teams/TeamsPage';
import { EpicsPage } from './features/epics/EpicsPage';
import { BoardPage, NotFoundPage, TicketDetailsPage } from './pages/placeholders';

export const routes = [
  {
    element: (
      <AuthProvider>
        <Outlet />
      </AuthProvider>
    ),
    children: [
      { path: '/', element: <Navigate to="/board" replace /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      { path: '/verify', element: <VerifyPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { path: '/board', element: <BoardPage /> },
              { path: '/teams', element: <TeamsPage /> },
              { path: '/epics', element: <EpicsPage /> },
              { path: '/tickets/:id', element: <TicketDetailsPage /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export const router = createBrowserRouter(routes);
