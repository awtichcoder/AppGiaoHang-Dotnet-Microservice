import { type FormEvent, useCallback, useEffect, useState } from 'react';
import { api, ApiError, type Complaint, type ComplaintAttachment, type Delivery, type Order } from '../api';
import { Alert, Button, Card, complaintCategoryName, dateTime, icons, orderCode, reporterName, Row, shortGuid, Status, statusName, vnd } from '../ui';

const NEXT_STATUSES: Record<string, string[]> = { OPEN: ['IN_REVIEW'], IN_REVIEW: ['RESOLVED', 'REJECTED'] };
const NOTE_LIMIT = 1000;

/** Loads the image through the authenticated API; there is no static URL for attachments. */
function AttachmentImage({ complaintId, attachment }: { complaintId: string; attachment: ComplaintAttachment }) {
  const [url, setUrl] = useState('');
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    let objectUrl = '';
    setUrl(''); setFailed(false);
    api.complaintAttachment(complaintId, attachment.id)
      .then(blob => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setUrl(objectUrl);
      })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [complaintId, attachment.id]);

  if (failed) return <div className="attachment">Không tải được ảnh</div>;
  if (!url) return <div className="attachment"><span className="spinner" /></div>;
  return <a className="attachment" href={url} target="_blank" rel="noreferrer" title={attachment.fileName}>
    <img src={url} alt={`Ảnh đính kèm ${attachment.fileName}`} />
  </a>;
}

export function ComplaintDetail({ complaintId, onBack, onOpenOrder }: { complaintId: string; onBack: () => void; onOpenOrder: (id: string) => void }) {
  const [complaint, setComplaint] = useState<Complaint | null>(null);
  const [order, setOrder] = useState<Order | null>(null);
  const [delivery, setDelivery] = useState<Delivery | null>(null);
  const [orderError, setOrderError] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [target, setTarget] = useState('');
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  const load = useCallback(async () => {
    setLoading(true); setError(''); setOrderError('');
    try {
      const value = await api.complaint(complaintId);
      setComplaint(value);
      setNote(value.adminNote ?? '');
      setTarget(NEXT_STATUSES[value.status]?.[0] ?? '');
      const [o, d] = await Promise.allSettled([api.order(value.orderId), api.delivery(value.orderId)]);
      setOrder(o.status === 'fulfilled' ? o.value : null);
      setDelivery(d.status === 'fulfilled' ? d.value : null);
      if (o.status === 'rejected') setOrderError(o.reason instanceof Error ? o.reason.message : 'Không tải được đơn hàng.');
    } catch (e) { setError(e instanceof Error ? e.message : 'Không tải được khiếu nại.'); }
    finally { setLoading(false); }
  }, [complaintId]);
  useEffect(() => { void load(); }, [load]);

  const options = complaint ? NEXT_STATUSES[complaint.status] ?? [] : [];
  const noteRequired = target === 'REJECTED';

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!complaint || !target) return;
    if (noteRequired && !note.trim()) { setSaveError('Cần nhập ghi chú khi từ chối khiếu nại.'); return; }
    setSaving(true); setSaveError('');
    try {
      const updated = await api.updateComplaintStatus(complaint.id, target, note);
      setComplaint(updated);
      setNote(updated.adminNote ?? '');
      setTarget(NEXT_STATUSES[updated.status]?.[0] ?? '');
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : 'Không cập nhật được trạng thái.');
      // Another admin changed it first: show the current state.
      if (e instanceof ApiError && e.status === 409) {
        try {
          const latest = await api.complaint(complaint.id);
          setComplaint(latest);
          setTarget(NEXT_STATUSES[latest.status]?.[0] ?? '');
        } catch { /* keep the error above */ }
      }
    } finally { setSaving(false); }
  };

  return <>
    <div className="page-head">
      <div className="head-title">
        <button className="link back" onClick={onBack}>‹ Khiếu nại</button>
        <h1>{complaint ? `Khiếu nại đơn ${orderCode(complaint.orderId)}` : 'Khiếu nại'}</h1>
        {complaint && <Status value={complaint.status} />}
      </div>
      <Button variant="outline" onClick={() => void load()} busy={loading}>{icons.refresh} Làm mới</Button>
    </div>
    {error && <Alert onRetry={() => void load()}>{error}</Alert>}
    {complaint && <div className="grid-2">
      <div className="stack">
        <Card title="Nội dung khiếu nại">
          <Row label="Người gửi">{reporterName[complaint.reporterRole] ?? complaint.reporterRole} · {shortGuid(complaint.reporterId)}</Row>
          <Row label="Loại vấn đề">{complaintCategoryName[complaint.category] ?? complaint.category}</Row>
          <Row label="Gửi lúc">{dateTime(complaint.createdAt)}</Row>
          <Row label="Cập nhật">{dateTime(complaint.updatedAt)}</Row>
          <Row label="Kết thúc xử lý">{dateTime(complaint.resolvedAt)}</Row>
          <Row label="Admin xử lý">{shortGuid(complaint.handledByAdminId)}</Row>
          <p className="prose">{complaint.description}</p>
        </Card>
        <Card title={`Ảnh đính kèm (${complaint.attachments.length})`}>
          {complaint.attachments.length > 0
            ? <div className="attachments">{complaint.attachments.map(a => <AttachmentImage key={a.id} complaintId={complaint.id} attachment={a} />)}</div>
            : <p className="muted">Không có ảnh. Ảnh được tự động xóa sau 30 ngày kể từ khi khiếu nại kết thúc.</p>}
        </Card>
      </div>
      <div className="stack">
        <Card title="Xử lý">
          {options.length > 0 ? <form className="form" onSubmit={e => void submit(e)}>
            <div className="chips">
              {options.map(value => <button type="button" key={value} className={`chip${target === value ? ' active' : ''}`}
                onClick={() => { setTarget(value); setSaveError(''); }}>{statusName[value]}</button>)}
            </div>
            <label>Ghi chú của admin{noteRequired ? ' (bắt buộc)' : ''}
              <textarea value={note} maxLength={NOTE_LIMIT} onChange={e => setNote(e.target.value)} />
              <small>{note.length}/{NOTE_LIMIT}. Người gửi khiếu nại sẽ thấy ghi chú này.</small>
            </label>
            {saveError && <Alert>{saveError}</Alert>}
            <Button type="submit" busy={saving}>Chuyển sang “{statusName[target]}”</Button>
            <p className="muted">Thao tác này chỉ đổi trạng thái. Hệ thống không tự đền bù hay thay đổi tiền, điểm, đánh giá.</p>
          </form> : <>
            {saveError && <Alert>{saveError}</Alert>}
            <p className="muted">Khiếu nại đã kết thúc, không thể đổi trạng thái.</p>
            {complaint.adminNote && <p className="prose">{complaint.adminNote}</p>}
          </>}
        </Card>
        <Card title="Đơn liên kết" action={<button className="link" onClick={() => onOpenOrder(complaint.orderId)}>Mở đơn</button>}>
          {orderError && <Alert>{orderError}</Alert>}
          {order && <>
            <Row label="Trạng thái"><Status value={order.status} /></Row>
            <Row label="Điểm lấy">{order.pickupAddress}</Row>
            <Row label="Điểm giao">{order.dropoffAddress}</Row>
            <Row label="Tổng phí">{vnd(order.totalFee)}</Row>
            <Row label="Khách hàng">{shortGuid(order.customerId)}</Row>
          </>}
          {delivery && <>
            <Row label="Tài xế">{shortGuid(delivery.driverId)}</Row>
            <Row label="Hoàn tất lúc">{dateTime(delivery.completedAt)}</Row>
          </>}
        </Card>
      </div>
    </div>}
  </>;
}
