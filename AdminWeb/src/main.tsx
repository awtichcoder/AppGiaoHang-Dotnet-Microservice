import { StrictMode, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

type Page = 'dashboard' | 'orders' | 'order-detail' | 'drivers' | 'customers' | 'promotions' | 'revenue' | 'applications' | 'staff' | 'settings';
type Order = {
  orderId: string; customerId: string; pickupAddress: string; dropoffAddress: string;
  totalFee: number; status: string; matchingStatus?: string; createdAt: string;
};
type Promotion = { code: string; description: string; discountAmount?: number; discountPercent?: number; maxDiscount?: number; minOrderAmount: number; endAt: string };
type User = { userId: string; fullName: string; email: string; phone: string; role: string };

const API = import.meta.env.VITE_API_URL ?? 'http://localhost:6200';
const money = (value: number) => new Intl.NumberFormat('vi-VN').format(value) + ' ₫';
const shortId = (id: string) => id ? `DH-${id.replaceAll('-', '').slice(0, 4).toUpperCase()}` : '—';
const statusName: Record<string, string> = { PENDING: 'Chờ tài xế', ASSIGNED: 'Đã nhận', PICKED_UP: 'Đã lấy hàng', DELIVERING: 'Đang giao', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy', NO_DRIVER_FOUND: 'Không có tài xế' };

async function api<T>(path: string, token?: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(init?.headers ?? {}) },
  });
  if (!response.ok) throw new Error((await response.text()) || `API ${response.status}`);
  return response.status === 204 ? (undefined as T) : response.json();
}

function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('delivery_admin_token') ?? '');
  const [admin, setAdmin] = useState<User | null>(null);
  const [page, setPage] = useState<Page>('dashboard');
  const [orders, setOrders] = useState<Order[]>([]);
  const [totalOrders, setTotalOrders] = useState(0);
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const loadData = async () => {
    if (!token) return;
    setLoading(true); setError('');
    try {
      const [orderPage, activePromotions, me] = await Promise.all([
        api<{ items: Order[]; totalItems: number }>('/api/order?page=1&pageSize=50', token),
        api<Promotion[]>('/api/promotion'),
        api<User>('/api/identity/me', token),
      ]);
      setOrders(orderPage.items ?? []); setTotalOrders(orderPage.totalItems ?? orderPage.items?.length ?? 0); setPromotions(activePromotions ?? []); setAdmin(me);
      if (me.role !== 'ADMIN') throw new Error('Tài khoản hiện tại không có quyền ADMIN.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Không thể tải dữ liệu từ API Gateway.'); }
    finally { setLoading(false); }
  };

  useEffect(() => { void loadData(); }, [token]);

  if (!token || (error && !admin)) return <Login onLogin={(next) => { sessionStorage.setItem('delivery_admin_token', next); setError(''); setToken(next); }} error={error} />;

  return <div className="app-shell">
    <Sidebar active={page === 'order-detail' ? 'orders' : page} onNavigate={setPage} />
    <section className="main-shell">
      <header className="topbar"><span className="page-title">{pageTitle(page)}</span><div className="admin-chip"><span>Quản trị viên</span><b>{admin?.fullName?.split(' ').map(x => x[0]).join('').slice(-2) || 'AK'}</b></div></header>
      <main className="content">
        {error && <div className="alert error">{error}<button onClick={() => void loadData()}>Thử lại</button></div>}
        {loading && <div className="loading-line">Đang đồng bộ với API Gateway…</div>}
        {page === 'dashboard' && <Dashboard orders={orders} totalOrders={totalOrders} promotions={promotions} onNavigate={setPage} />}
        {page === 'orders' && <Orders orders={orders} totalOrders={totalOrders} onRefresh={loadData} onOpen={(id) => { sessionStorage.setItem('selected_order', id); setPage('order-detail'); }} />}
        {page === 'order-detail' && <OrderDetail order={orders.find(order => order.orderId === sessionStorage.getItem('selected_order'))} onBack={() => setPage('orders')} />}
        {page === 'drivers' && <Drivers />}
        {page === 'customers' && <Customers />}
        {page === 'promotions' && <Promotions promotions={promotions} />}
        {page === 'revenue' && <Revenue orders={orders} />}
        {page === 'applications' && <Capability title="Đăng ký làm tài xế" description="Backend hiện chưa có API nhận và duyệt hồ sơ tài xế. Giao diện đã chừa module để nối vào Identity/Driver Service khi hợp đồng API được bổ sung." />}
        {page === 'staff' && <Staff token={token} />}
        {page === 'settings' && <Settings api={API} admin={admin} onLogout={() => { sessionStorage.clear(); setToken(''); setAdmin(null); }} />}
      </main>
    </section>
  </div>;
}

function Login({ onLogin, error }: { onLogin: (token: string) => void; error: string }) {
  const [email, setEmail] = useState('admin@deliveryapp.local');
  const [password, setPassword] = useState('Admin123!');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState(error);
  const submit = async (event: React.FormEvent) => { event.preventDefault(); setBusy(true); setMessage(''); try { const result = await api<{ accessToken: string }>('/api/identity/login', undefined, { method: 'POST', body: JSON.stringify({ email, password }) }); onLogin(result.accessToken); } catch (e) { setMessage(e instanceof Error ? e.message : 'Đăng nhập thất bại.'); } finally { setBusy(false); } };
  return <div className="login-page"><div className="brand-panel"><div className="brand-mark">D</div><div className="brand-name">DELIVERY</div><div className="brand-subtitle">ADMIN CENTER</div><p>Giao nhanh, an tâm từng chặng</p></div><form className="login-card" onSubmit={submit}><div className="eyebrow">ADMIN WEB</div><h1>Chào mừng trở lại</h1><p className="muted">Đăng nhập để quản lý và theo dõi vận hành</p><label>Email<input value={email} onChange={e => setEmail(e.target.value)} type="email" /></label><label>Mật khẩu<input value={password} onChange={e => setPassword(e.target.value)} type="password" /></label>{message && <div className="alert error">{message}</div>}<button className="primary wide" disabled={busy}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button><small>Dev account: admin@deliveryapp.local / Admin123!</small></form></div>;
}

function Sidebar({ active, onNavigate }: { active: Page; onNavigate: (page: Page) => void }) {
  const items: [Page, string, string][] = [['dashboard', '⌂', 'Tổng quan'], ['orders', '▤', 'Đơn hàng'], ['drivers', '♙', 'Tài xế'], ['applications', '＋', 'Đăng ký tài xế'], ['customers', '♧', 'Khách hàng'], ['revenue', '↗', 'Doanh thu'], ['promotions', '◇', 'Khuyến mãi'], ['staff', '♟', 'Nhân sự'], ['settings', '⚙', 'Cấu hình']];
  return <aside className="sidebar"><div className="brand"><div className="brand-mark small">D</div><div><strong>DELIVERY</strong><span>ADMIN CENTER</span></div></div><nav>{items.map(([key, icon, label]) => <button key={key} className={active === key ? 'nav-item active' : 'nav-item'} onClick={() => onNavigate(key)}><i>{icon}</i>{label}</button>)}</nav><div className="gateway-status"><span className="online-dot" />API Gateway • Online</div></aside>;
}

function pageTitle(page: Page) { return ({ dashboard: 'Tổng quan vận hành', orders: 'Quản lý đơn hàng', 'order-detail': 'Chi tiết đơn hàng', drivers: 'Quản lý tài xế', customers: 'Quản lý khách hàng', promotions: 'Quản lý khuyến mãi', revenue: 'Thống kê doanh thu', applications: 'Đăng ký làm tài xế', staff: 'Quản lý nhân sự', settings: 'Cấu hình hệ thống' } as Record<Page, string>)[page]; }

function Dashboard({ orders, totalOrders, promotions, onNavigate }: { orders: Order[]; totalOrders: number; promotions: Promotion[]; onNavigate: (page: Page) => void }) {
  const delivering = orders.filter(o => ['ASSIGNED', 'PICKED_UP', 'DELIVERING'].includes(o.status)).length;
  const completed = orders.filter(o => o.status === 'COMPLETED').length;
  const revenue = orders.reduce((sum, o) => sum + Number(o.totalFee || 0), 0);
  const counts = ['PENDING', 'ASSIGNED', 'PICKED_UP', 'DELIVERING', 'COMPLETED'].map(status => ({ status, count: orders.filter(o => o.status === status).length }));
  return <><div className="section-heading"><div><div className="eyebrow">HÔM NAY</div><h1>Tổng quan vận hành</h1></div><button className="secondary" onClick={() => onNavigate('orders')}>Xem đơn hàng</button></div><div className="stat-grid"><Stat label="Đơn mới" value={orders.filter(o => o.status === 'PENDING').length || '—'} hint={`${totalOrders || 0} đơn trong hệ thống`} tone="green" /><Stat label="Đang giao" value={delivering || '—'} hint="Từ dữ liệu Order Service" tone="blue" /><Stat label="Hoàn tất" value={completed || '—'} hint="Trạng thái COMPLETED" tone="purple" /><Stat label="Doanh thu đã tải" value={revenue ? compactMoney(revenue) : '—'} hint="Tổng phí của 50 đơn gần nhất" tone="orange" /></div><div className="dashboard-grid"><Card title="Luồng đơn hàng" subtitle="Cập nhật theo trạng thái từ Order Service"><div className="flow-list">{counts.map(({ status, count }) => <div className="flow-row" key={status}><span>{statusName[status]}</span><div className="bar"><b style={{ width: `${Math.min(100, count * 12 + (count ? 8 : 0))}%` }} /></div><strong>{count}</strong></div>)}</div></Card><Card title="Cảnh báo hệ thống"><div className="warning-badge">Cần xử lý</div><ul className="warning-list"><li>{orders.filter(o => o.status === 'PENDING').length} đơn chưa có tài xế</li><li>{promotions.length} promotion đang hoạt động</li><li>Doanh thu phụ thuộc dữ liệu API hiện có</li></ul><button className="secondary" onClick={() => onNavigate('settings')}>Xem cấu hình API</button></Card></div></>;
}

function Orders({ orders, totalOrders, onRefresh, onOpen }: { orders: Order[]; totalOrders: number; onRefresh: () => Promise<void>; onOpen: (id: string) => void }) {
  const [query, setQuery] = useState('');
  const filtered = orders.filter(o => `${shortId(o.orderId)} ${o.pickupAddress} ${o.dropoffAddress} ${o.customerId}`.toLowerCase().includes(query.toLowerCase()));
  return <><div className="section-heading"><div><div className="eyebrow">ORDER SERVICE</div><h1>Quản lý đơn hàng</h1></div><button className="secondary" onClick={() => void onRefresh()}>↻ Làm mới</button></div><Card><div className="toolbar"><input placeholder="Tìm mã đơn, khách hàng, địa chỉ…" value={query} onChange={e => setQuery(e.target.value)} /></div><DataTable headers={['MÃ ĐƠN', 'KHÁCH HÀNG', 'TUYẾN GIAO', 'PHÍ', 'TRẠNG THÁI']} rows={filtered.length ? filtered.map(order => <tr key={order.orderId} onClick={() => onOpen(order.orderId)}><td><strong className="order-id">{shortId(order.orderId)}</strong></td><td>{order.customerId.slice(0, 8)}…</td><td>{order.pickupAddress} <span className="arrow">→</span> {order.dropoffAddress}</td><td><strong>{money(order.totalFee)}</strong></td><td><Status value={order.status} /></td></tr>) : null} empty="Chưa có đơn hàng hoặc backend chưa sẵn sàng." /></Card><div className="table-footer">Hiển thị {filtered.length} / {totalOrders || orders.length} đơn <span>‹ 1 2 3 ›</span></div></>;
}

function OrderDetail({ order, onBack }: { order?: Order; onBack: () => void }) {
  if (!order) return <><div className="section-heading"><div><div className="eyebrow">ORDER SERVICE</div><h1>Chi tiết đơn hàng</h1></div><button className="secondary" onClick={onBack}>← Quay lại</button></div><Capability title="Không tìm thấy đơn hàng" description="Hãy quay lại danh sách và chọn một đơn hàng khác." /></>;
  return <><div className="section-heading"><div><div className="eyebrow">ORDER SERVICE</div><h1>Chi tiết đơn {shortId(order.orderId)}</h1></div><button className="secondary" onClick={onBack}>← Quay lại danh sách</button></div><div className="detail-grid"><Card><div className="detail-header"><strong>{shortId(order.orderId)}</strong><Status value={order.status} /></div><div className="detail-block"><span>KHÁCH HÀNG</span><strong>{order.customerId}</strong></div><div className="route-block"><div><b>LẤY HÀNG</b><p>{order.pickupAddress}</p></div><div className="route-line" /><div><b>GIAO HÀNG</b><p>{order.dropoffAddress}</p></div></div><div className="detail-block"><span>PHÍ VẬN CHUYỂN</span><strong>{money(order.totalFee)}</strong></div><div className="detail-block"><span>TRẠNG THÁI GHÉP TÀI XẾ</span><strong>{order.matchingStatus || '—'}</strong></div></Card><Card title="Tóm tắt"><div className="settings-grid"><span>Mã đơn</span><strong>{shortId(order.orderId)}</strong><span>Khoảng cách</span><strong>—</strong><span>Ngày tạo</span><strong>{new Date(order.createdAt).toLocaleString('vi-VN')}</strong><span>Version</span><strong>API response</strong></div></Card></div></>;
}

function Drivers() { return <><div className="section-heading"><div><div className="eyebrow">DRIVER SERVICE</div><h1>Quản lý tài xế</h1></div><button className="primary" disabled>+ Tạo tài khoản DRIVER</button></div><div className="stat-grid"><Stat label="Tổng tài xế" value="—" hint="Chưa có API danh sách tài xế" tone="green" /><Stat label="Đang sẵn sàng" value="—" hint="Chưa có API quản trị" tone="blue" /><Stat label="Đang giao" value="—" hint="Theo dõi qua Delivery Service" tone="purple" /><Stat label="Ngoại tuyến" value="—" hint="Chưa có API quản trị" tone="orange" /></div><Capability title="API còn thiếu cho module tài xế" description="Driver Service hiện chỉ cho tài xế tự cập nhật availability/location và cho service nội bộ truy vấn nearby. Để trang này vận hành đầy đủ cần thêm endpoint ADMIN list/detail/approve/suspend driver." /></> }

function Customers() { return <><div className="section-heading"><div><div className="eyebrow">IDENTITY + LOYALTY</div><h1>Quản lý khách hàng</h1></div></div><div className="stat-grid"><Stat label="Khách hàng" value="—" hint="Identity chưa có API list user" tone="green" /><Stat label="Điểm Loyalty đã phát" value="—" hint="Loyalty hiện chỉ có /me" tone="blue" /><Stat label="Hạng GOLD" value="—" hint="Chưa có API tổng hợp" tone="purple" /></div><Capability title="Cần bổ sung API quản trị khách hàng" description="Backend hiện hỗ trợ tạo tài khoản, đăng nhập, xem hồ sơ cá nhân và loyalty của chính người dùng. Chưa có endpoint ADMIN để liệt kê, tìm kiếm hoặc khóa khách hàng." /></> }

function Promotions({ promotions }: { promotions: Promotion[] }) { return <><div className="section-heading"><div><div className="eyebrow">PROMOTION SERVICE</div><h1>Quản lý khuyến mãi</h1></div><button className="primary" disabled>+ Tạo promotion</button></div><Card><div className="promotion-header"><span>Mã giảm giá cho Customer App</span><span>{promotions.length} mã đang hoạt động</span></div>{promotions.length ? promotions.map(p => <div className="promotion-row" key={p.code}><div><strong>{p.code}</strong><p>{p.description || formatDiscount(p)} • Đơn từ {money(p.minOrderAmount)}</p></div><span>Đến {new Date(p.endAt).toLocaleDateString('vi-VN')}</span><Status value="ACTIVE" /></div>) : <Empty text="Chưa có promotion hoặc backend chưa sẵn sàng." />}</Card><p className="api-note">API hiện có: GET /api/promotion • Tạo/sửa promotion chưa được mở qua Gateway.</p></> }

function Revenue({ orders }: { orders: Order[] }) { const completed = orders.filter(o => o.status === 'COMPLETED'); const total = completed.reduce((sum, order) => sum + Number(order.totalFee || 0), 0); return <><div className="section-heading"><div><div className="eyebrow">ORDER SERVICE</div><h1>Thống kê doanh thu</h1></div><span className="api-pill">Derived from loaded orders</span></div><div className="stat-grid"><Stat label="Doanh thu hoàn tất" value={total ? compactMoney(total) : '—'} hint={`${completed.length} đơn COMPLETED trong dữ liệu đã tải`} tone="green" /><Stat label="Phí vận chuyển bình quân" value={completed.length ? compactMoney(total / completed.length) : '—'} hint="Tính từ TotalFee" tone="blue" /><Stat label="Đơn đã tải" value={orders.length} hint="Tối đa 50 đơn mỗi lần tải" tone="purple" /></div><Card title="Doanh thu theo trạng thái"><div className="revenue-bars">{['PENDING', 'ASSIGNED', 'DELIVERING', 'COMPLETED', 'CANCELLED'].map(s => { const n = orders.filter(o => o.status === s).reduce((sum, o) => sum + Number(o.totalFee || 0), 0); return <div className="revenue-bar" key={s}><span>{statusName[s]}</span><div><b style={{ width: `${total ? Math.max(4, n / total * 100) : 4}%` }} /></div><strong>{money(n)}</strong></div>; })}</div></Card><p className="api-note">Để có báo cáo theo ngày/tháng, cần thêm endpoint aggregation trong Order/Reporting Service.</p></> }

function Staff({ token }: { token: string }) { const [form, setForm] = useState({ email: '', password: '', fullName: '', phone: '', role: 'DRIVER' }); const [message, setMessage] = useState(''); const submit = async (e: React.FormEvent) => { e.preventDefault(); try { const user = await api<User>('/api/identity/admin/users', token, { method: 'POST', body: JSON.stringify(form) }); setMessage(`Đã tạo ${user.role}: ${user.fullName}`); setForm({ email: '', password: '', fullName: '', phone: '', role: 'DRIVER' }); } catch (err) { setMessage(err instanceof Error ? err.message : 'Không thể tạo nhân sự.'); } }; return <><div className="section-heading"><div><div className="eyebrow">IDENTITY SERVICE</div><h1>Quản lý nhân sự</h1></div></div><Card title="Tạo tài khoản nội bộ"><form className="staff-form" onSubmit={submit}><label>Họ tên<input required value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} /></label><label>Email<input required type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} /></label><label>Số điện thoại<input required value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} /></label><label>Mật khẩu<input required type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} /></label><label>Vai trò<select value={form.role} onChange={e => setForm({ ...form, role: e.target.value })}><option value="DRIVER">DRIVER</option><option value="ADMIN">ADMIN</option></select></label><button className="primary" type="submit">Tạo tài khoản</button></form>{message && <div className="notice">{message}</div>}</Card><Capability title="Giới hạn backend hiện tại" description="Có API tạo DRIVER/ADMIN nhưng chưa có API list, cập nhật vai trò hoặc khóa tài khoản. Module này hiện tập trung vào tạo tài khoản nội bộ." /></> }

function Settings({ api: apiUrl, admin, onLogout }: { api: string; admin: User | null; onLogout: () => void }) { return <><div className="section-heading"><div><div className="eyebrow">SYSTEM</div><h1>Cấu hình hệ thống</h1></div><button className="secondary danger" onClick={onLogout}>Đăng xuất</button></div><Card title="Phiên quản trị"><div className="settings-grid"><span>Tài khoản</span><strong>{admin?.email}</strong><span>Role</span><strong>{admin?.role}</strong><span>API Gateway</span><strong>{apiUrl}</strong><span>Trạng thái</span><strong className="text-green">Sẵn sàng kiểm tra</strong></div></Card><Capability title="Health check" description="Gateway cung cấp /health/live và /health/ready. Docker Desktop hiện đang lỗi khởi động Ingest server trên máy này, nên API chưa thể kiểm chứng end-to-end." /></> }

function Stat({ label, value, hint, tone }: { label: string; value: string | number; hint: string; tone: string }) { return <div className={`stat-card ${tone}`}><span>{label}</span><strong>{value}</strong><small>{hint}</small></div> }
function Card({ title, subtitle, children }: { title?: string; subtitle?: string; children: React.ReactNode }) { return <section className="card">{title && <div className="card-heading"><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div></div>}{children}</section> }
function DataTable({ headers, rows, empty }: { headers: string[]; rows: React.ReactNode; empty: string }) { return <div className="table-wrap"><table><thead><tr>{headers.map(header => <th key={header}>{header}</th>)}</tr></thead><tbody>{rows || <tr><td colSpan={headers.length}><Empty text={empty} /></td></tr>}</tbody></table></div> }
function Empty({ text }: { text: string }) { return <div className="empty">{text}</div> }
function Status({ value }: { value: string }) { return <span className={`status ${value.toLowerCase().replaceAll('_', '-')}`}>{statusName[value] || value}</span> }
function Capability({ title, description }: { title: string; description: string }) { return <div className="capability"><div className="capability-icon">i</div><div><strong>{title}</strong><p>{description}</p></div></div> }
function compactMoney(value: number) { return value >= 1_000_000 ? `${(value / 1_000_000).toFixed(1)} tr ₫` : money(value); }
function formatDiscount(p: Promotion) { return p.discountAmount ? `Giảm ${money(p.discountAmount)}` : `Giảm ${p.discountPercent ?? 0}%`; }

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
