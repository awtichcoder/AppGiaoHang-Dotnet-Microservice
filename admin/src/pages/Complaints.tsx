import { useCallback, useEffect, useState } from 'react';
import { api, type Complaint, type Paged } from '../api';
import { Alert, Button, COMPLAINT_STATUSES, complaintCategoryName, dateTime, icons, orderCode, reporterName, Status, statusName } from '../ui';

const PAGE_SIZE = 20;

export function Complaints({ initialStatus, onOpen }: { initialStatus?: string; onOpen: (id: string) => void }) {
  const [status, setStatus] = useState(initialStatus ?? '');
  const [page, setPage] = useState(1);
  const [data, setData] = useState<Paged<Complaint> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => { setStatus(initialStatus ?? ''); setPage(1); }, [initialStatus]);

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setData(await api.complaints(page, PAGE_SIZE, status || undefined)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Không tải được khiếu nại.'); }
    finally { setLoading(false); }
  }, [page, status]);
  useEffect(() => { void load(); }, [load]);

  const pages = data ? Math.max(1, Math.ceil(data.totalItems / PAGE_SIZE)) : 1;
  const items = data?.items ?? [];

  return <>
    <div className="page-head">
      <h1>Khiếu nại</h1>
      <Button variant="outline" onClick={() => void load()} busy={loading}>{icons.refresh} Làm mới</Button>
    </div>
    <div className="chips">
      {['', ...COMPLAINT_STATUSES].map(value => <button key={value || 'all'} className={`chip${status === value ? ' active' : ''}`}
        onClick={() => { setStatus(value); setPage(1); }}>{value ? statusName[value] : 'Tất cả'}</button>)}
    </div>
    {error && <Alert onRetry={() => void load()}>{error}</Alert>}
    {items.length > 0 && <div className="table-wrap">
      <table className="data-table">
        <thead><tr><th>Đơn</th><th>Người gửi</th><th>Loại vấn đề</th><th>Mô tả</th><th>Ảnh</th><th>Trạng thái</th><th>Gửi lúc</th></tr></thead>
        <tbody>
          {items.map(item => <tr key={item.id} tabIndex={0} onClick={() => onOpen(item.id)}
            onKeyDown={e => { if (e.key === 'Enter') onOpen(item.id); }}>
            <td>{orderCode(item.orderId)}</td>
            <td>{reporterName[item.reporterRole] ?? item.reporterRole}</td>
            <td>{complaintCategoryName[item.category] ?? item.category}</td>
            <td className="clip" title={item.description}>{item.description}</td>
            <td>{item.attachments.length}</td>
            <td><Status value={item.status} /></td>
            <td>{dateTime(item.createdAt)}</td>
          </tr>)}
        </tbody>
      </table>
    </div>}
    {!loading && data && items.length === 0 && <p className="empty">Không có khiếu nại phù hợp.</p>}
    <div className="pager">
      <span>{data ? `${data.totalItems} khiếu nại · trang ${page}/${pages}` : '—'}</span>
      <div>
        <Button variant="soft" disabled={page <= 1 || loading} onClick={() => setPage(p => p - 1)}>‹ Trước</Button>
        <Button variant="soft" disabled={page >= pages || loading} onClick={() => setPage(p => p + 1)}>Sau ›</Button>
      </div>
    </div>
  </>;
}
