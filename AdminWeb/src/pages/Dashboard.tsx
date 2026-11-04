import { useCallback, useEffect, useState } from 'react';
import { api, type Order } from '../api';
import { Alert, Button, Card, icons, ORDER_STATUSES, Status, statusName, vnd } from '../ui';
import { OrderCard } from './Orders';

type Summary = { total: number; byStatus: Record<string, number>; revenue: number; revenueOrders: number; revenueCapped: boolean; recent: Order[] };
const REVENUE_PAGES = 10; // up to 1 000 completed orders (the API allows 100 per page)

export function Dashboard({ onOpenOrder, onOpenOrders }: { onOpenOrder: (id: string) => void; onOpenOrders: (status?: string) => void }) {
  const [data, setData] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      // Exact counts come from the backend's status filter (totalItems), not from a loaded page.
      const [all, ...counts] = await Promise.all([
        api.orders(1, 5),
        ...ORDER_STATUSES.map(status => api.orders(1, 1, status).then(page => page.totalItems)),
      ]);
      let revenue = 0, revenueOrders = 0, page = 1, capped = false;
      while (true) {
        const completed = await api.orders(page, 100, 'COMPLETED');
        completed.items.forEach(order => { revenue += order.totalFee; revenueOrders++; });
        if (page * 100 >= completed.totalItems) break;
        if (page >= REVENUE_PAGES) { capped = true; break; }
        page++;
      }
      setData({
        total: all.totalItems,
        byStatus: Object.fromEntries(ORDER_STATUSES.map((status, i) => [status, counts[i]])),
        revenue, revenueOrders, revenueCapped: capped, recent: all.items,
      });
    } catch (e) { setError(e instanceof Error ? e.message : 'Không tải được dữ liệu.'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { void load(); }, [load]);

  const active = data ? (data.byStatus.ASSIGNED ?? 0) + (data.byStatus.PICKED_UP ?? 0) + (data.byStatus.DELIVERING ?? 0) : 0;
  return <>
    <div className="page-head">
      <h1>Tổng quan</h1>
      <Button variant="outline" onClick={() => void load()} busy={loading}>{icons.refresh} Làm mới</Button>
    </div>
    {error && <Alert onRetry={() => void load()}>{error}</Alert>}
    <div className="stats">
      <Stat label="Tổng đơn hàng" value={data?.total} onClick={() => onOpenOrders()} />
      <Stat label="Đang chờ tài xế" value={data?.byStatus.PENDING} onClick={() => onOpenOrders('PENDING')} />
      <Stat label="Đang giao" value={data ? active : undefined} hint="Đã nhận · đã lấy hàng · đang giao" />
      <Stat label="Doanh thu (phí đơn hoàn tất)" value={data ? vnd(data.revenue) : undefined}
        hint={data ? `${data.revenueOrders} đơn hoàn tất${data.revenueCapped ? ' (1 000 đơn mới nhất)' : ''}` : undefined} money />
    </div>
    <div className="grid-2">
      <Card title="Đơn hàng theo trạng thái">
        <div className="status-list">
          {ORDER_STATUSES.map(status => {
            const count = data?.byStatus[status] ?? 0;
            const share = data?.total ? count / data.total : 0;
            return <button key={status} className="status-line" onClick={() => onOpenOrders(status)}>
              <Status value={status} />
              <span className="bar"><b style={{ width: `${Math.max(share * 100, count ? 3 : 0)}%` }} /></span>
              <strong>{data ? count : '—'}</strong>
            </button>;
          })}
        </div>
      </Card>
      <Card title="Đơn mới nhất" action={<button className="link" onClick={() => onOpenOrders()}>Xem tất cả</button>}>
        <div className="stack">
          {data?.recent.length ? data.recent.map(order => <OrderCard key={order.orderId} order={order} compact onOpen={onOpenOrder} />)
            : <p className="muted">{loading ? 'Đang tải…' : 'Chưa có đơn hàng.'}</p>}
        </div>
      </Card>
    </div>
    <p className="note">Doanh thu là tổng phí của các đơn {statusName.COMPLETED.toLowerCase()}. Backend chưa có API báo cáo theo ngày/tháng.</p>
  </>;
}

function Stat({ label, value, hint, onClick, money }: { label: string; value?: string | number; hint?: string; onClick?: () => void; money?: boolean }) {
  const content = <><span>{label}</span><strong className={money ? 'money' : ''}>{value ?? '—'}</strong>{hint && <small>{hint}</small>}</>;
  return onClick ? <button className="card stat clickable" onClick={onClick}>{content}</button> : <div className="card stat">{content}</div>;
}
