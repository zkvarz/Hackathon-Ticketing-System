// App shell: header with nav + backend status widget, and an <Outlet/> for routed pages
// (architecture.md §11). Feature screens render inside this layout in later epics.

import { NavLink, Outlet } from 'react-router-dom';
import { BackendStatus } from '../components/BackendStatus';

const NAV = [
  { to: '/board', label: 'Board' },
  { to: '/teams', label: 'Teams' },
  { to: '/epics', label: 'Epics' },
];

export function AppLayout() {
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
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
