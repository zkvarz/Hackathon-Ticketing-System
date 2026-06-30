// App shell: header with nav, backend status, and a user menu (HTS-012) with logout.
// Feature screens render inside this layout via <Outlet/> (architecture.md §11).

import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { BackendStatus } from '../components/BackendStatus';
import { useAuth } from '../auth/AuthContext';

const NAV = [
  { to: '/board', label: 'Board' },
  { to: '/teams', label: 'Teams' },
  { to: '/epics', label: 'Epics' },
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login');
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <span className="app-header__brand">Ticketing System</span>
        <nav className="app-header__nav">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <BackendStatus />
        {user && (
          <div className="app-header__user">
            <span className="app-header__email">{user.email}</span>
            <button type="button" onClick={handleLogout}>
              Log out
            </button>
          </div>
        )}
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
