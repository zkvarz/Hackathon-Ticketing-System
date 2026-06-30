// Route table (architecture.md §11). Auth screens render standalone; app routes render
// inside AppLayout. Route guards (redirect unauthed → /login) arrive in HTS-014.

import { createBrowserRouter, Navigate } from 'react-router-dom';
import { AppLayout } from './layout/AppLayout';
import { SignupPage } from './features/auth/SignupPage';
import {
  BoardPage,
  EpicsPage,
  LoginPage,
  NotFoundPage,
  TeamsPage,
  TicketDetailsPage,
  VerifyPage,
} from './pages/placeholders';

export const routes = [
  { path: '/', element: <Navigate to="/board" replace /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  { path: '/verify', element: <VerifyPage /> },
  {
    element: <AppLayout />,
    children: [
      { path: '/board', element: <BoardPage /> },
      { path: '/teams', element: <TeamsPage /> },
      { path: '/epics', element: <EpicsPage /> },
      { path: '/tickets/:id', element: <TicketDetailsPage /> },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
];

export const router = createBrowserRouter(routes);
