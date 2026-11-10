import { useCallback, useEffect, useState } from 'react';
import { api, type Order, type Paged } from '../api';
import { Alert, Button, dateTime, icons, km, ORDER_STATUSES, orderCode, Status, statusName, vnd } from '../ui';

const PAGE_SIZE = 20;

/** Same layout as the driver app's order card: id + distance, A/B stops, amount. */
export function OrderCard({ order, onOpen, compact }: { order: Order; onOpen: (id: string) => void; compact?: boolean }) {
  return <button className={`order-card${compact ? ' compact' : ''}`} onClick={() => onOpen(order.orderId)}>
    <div className="oc-top"><span className="oc-id">{orderCode(order.orderId)}</span><span className="oc-km">{km(order.distanceKm)}</span></div>
    <div className="oc-a">A&nbsp;&nbsp;{order.pickupAddress}</div>
    <div className="oc-b">B&nbsp;&nbsp;{order.dropoffAddress}</div>
    <div className="oc-bottom">
      <Status value={order.status} />
      <span className="oc-fee"><small>Phí đơn</small>{vnd(order.totalFee)}</span>
    </div>
    {!compact && <div className="oc-time">Tạo lúc {dateTime(order.createdAt)}</div>}
  </button>;
}

export function Orders({ initialStatus, onOpenOrder }: { initialStatus?: string; onOpenOrder: (id: string) => void }) {
  const [status, setStatus] = useState(initialStatus ?? '');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Order> | null>(null);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { setStatus(initialStatus ?? ''); setPage(1); }, [initialStatus]);

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setData(await api.orders(page, PAGE_SIZE, status || undefined)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không tải được đơn hàng.'); }
    finally { setLoading(false); }
  }, [page, status]);
  useEffect(() => { void load(); }, [load]);

  const pages = data ? Math.max(1, Math.ceil(data.totalItems / PAGE_SIZE)) : 1;
  const q = query.trim().toLowerCase();
  const items = (data?.items ?? []).filter(o => !q ||
    [o.orderId, o.customerId, o.pickupAddress, o.dropoffAddress, o.receiverPhone].some(v => v?.toLowerCase().includes(q)));

  return <>
    <div className="page-head">
      <h1>Đơn hàng</h1>
      <Button variant="outline" onClick={() => void load()} busy={loading}>{icons.refresh} Làm mới</Button>
    </div>
    <div className="chips">
      {['', ...ORDER_STATUSES].map(value => <button key={value || 'all'} className={`chip${status === value ? ' active' : ''}`}
        onClick={() => { setStatus(value); setPage(1); }}>{value ? statusName[value] : 'Tất cả'}</button>)}
    </div>
    <input className="search" placeholder="Tìm trong trang này: mã đơn, mã khách hàng, địa chỉ, số điện thoại…" value={query} onChange={e => setQuery(e.target.value)} />
    {error && <Alert onRetry={() => void load()}>{error}</Alert>}
    <div className="order-grid">
      {items.map(order => <OrderCard key={order.orderId} order={order} onOpen={onOpenOrder} />)}
    </div>
    {!loading && data && items.length === 0 && <p className="empty">Không có đơn hàng phù hợp.</p>}
    <div className="pager">
      <span>{data ? `${data.totalItems} đơn · trang ${page}/${pages}` : '—'}</span>
      <div>
        <Button variant="soft" disabled={page <= 1 || loading} onClick={() => setPage(p => p - 1)}>‹ Trước</Button>
        <Button variant="soft" disabled={page >= pages || loading} onClick={() => setPage(p => p + 1)}>Sau ›</Button>
      </div>
    </div>
  </>;
}
