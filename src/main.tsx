import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { api, session, type User } from './api';
import { icons } from './ui';
import { Login } from './pages/Login';
import { Dashboard } from './pages/Dashboard';
import { Orders } from './pages/Orders';
import { OrderDetail } from './pages/OrderDetail';
import { Accounts } from './pages/Accounts';
import { Profile } from './pages/Profile';
import './styles.css';

type Route = { page: 'dashboard' } | { page: 'orders'; status?: string } | { page: 'order'; id: string } | { page: 'accounts' } | { page: 'profile' };

function parseHash(): Route {
  const [path, search = ''] = window.location.hash.replace(/^#\/?/, '').split('?');
  const [page, arg] = path.split('/');
  if (page === 'orders') return arg ? { page: 'order', id: arg } : { page: 'orders', status: new URLSearchParams(search).get('status') ?? undefined };
  if (page === 'accounts' || page === 'profile') return { page };
  return { page: 'dashboard' };
}
const go = (hash: string) => { window.location.hash = hash; };

const TABS = [
  { key: 'dashboard', hash: '#/', label: 'Tổng quan', icon: icons.home },
  { key: 'orders', hash: '#/orders', label: 'Đơn hàng', icon: icons.orders },
  { key: 'accounts', hash: '#/accounts', label: 'Tạo tài khoản', icon: icons.people },
  { key: 'profile', hash: '#/profile', label: 'Tài khoản', icon: icons.account },
];
const initial = (name: string) => name.trim().split(/\s+/).pop()?.[0]?.toUpperCase() ?? 'A';

function App() {
  const [admin, setAdmin] = useState<User | null>(null);
  const [restoring, setRestoring] = useState(() => session.get() != null);
  const [notice, setNotice] = useState('');
  const [route, setRoute] = useState<Route>(parseHash);

  useEffect(() => {
    const onHash = () => setRoute(parseHash());
    window.addEventListener('hashchange', onHash);
    return () => window.removeEventListener('hashchange', onHash);
  }, []);

  // Restore the session kept in sessionStorage (the API layer refreshes an expired access token).
  useEffect(() => {
    if (!restoring) return;
    api.me()
      .then(me => { if (me.role === 'ADMIN') setAdmin(me); else { session.set(null); setNotice('Tài khoản này không có quyền quản trị (ADMIN).'); } })
      .catch(e => { session.set(null); setNotice(e instanceof Error ? e.message : ''); })
      .finally(() => setRestoring(false));
  }, [restoring]);

  // When the refresh token is no longer valid the API layer clears the session; go back to login.
  useEffect(() => {
    const timer = setInterval(() => { if (admin && !session.get()) { setAdmin(null); setNotice('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'); } }, 1000);
    return () => clearInterval(timer);
  }, [admin]);

  if (restoring) return <div className="splash"><div className="brand-mark">D</div></div>;
  if (!admin) return <Login notice={notice} onLogin={me => { setNotice(''); setAdmin(me); go('#/'); }} />;

  const activeTab = route.page === 'order' ? 'orders' : route.page;
  return <div className="shell">
    <header className="header">
      <span className="avatar light">{initial(admin.fullName)}</span>
      <div className="who"><strong>{admin.fullName}</strong><small>Quản trị viên</small></div>
      <span className="brand">DeliveryApp · Admin</span>
    </header>
    <nav className="tabs">
      {TABS.map(tab => <a key={tab.key} href={tab.hash} className={activeTab === tab.key ? 'active' : ''}>
        <span className="tab-icon">{tab.icon}</span>{tab.label}
      </a>)}
    </nav>
    <main className="content">
      {route.page === 'dashboard' && <Dashboard onOpenOrder={id => go(`#/orders/${id}`)} onOpenOrders={status => go(status ? `#/orders?status=${status}` : '#/orders')} />}
      {route.page === 'orders' && <Orders initialStatus={route.status} onOpenOrder={id => go(`#/orders/${id}`)} />}
      {route.page === 'order' && <OrderDetail key={route.id} orderId={route.id} onBack={() => go('#/orders')} />}
      {route.page === 'accounts' && <Accounts />}
      {route.page === 'profile' && <Profile admin={admin} onLogout={() => { setAdmin(null); setNotice(''); go('#/'); }} />}
    </main>
  </div>;
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
