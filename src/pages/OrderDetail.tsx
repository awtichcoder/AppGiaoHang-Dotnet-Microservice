import { useCallback, useEffect, useState } from 'react';
import { api, ApiError, type Delivery, type DriverLocation, type MatchingStatus, type Order } from '../api';
import { Alert, Button, Card, dateTime, icons, km, orderCode, Row, shortGuid, Status, vnd } from '../ui';

const refundName: Record<string, string> = { NOT_APPLICABLE: "Không áp dụng" };
const TRACKABLE = ['ASSIGNED', 'PICKED_UP', 'DELIVERING'];

export function OrderDetail({ orderId, onBack }: { orderId: string; onBack: () => void }) {
  const [order, setOrder] = useState<Order | null>(null);
  const [delivery, setDelivery] = useState<Delivery | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [matching, setMatching] = useState<MatchingStatus | null>(null);
  const [location, setLocation] = useState<DriverLocation | null>(null);
  const [panelBusy, setPanelBusy] = useState<'matching' | 'location' | null>(null);
  const [panelError, setPanelError] = useState('');
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const [o, d] = await Promise.all([
        api.order(orderId),
        // The delivery exists only after the order was handed to Delivery Service.
        api.delivery(orderId).catch(e => { if (e instanceof ApiError && e.status === 404) return null; throw e; }),
      ]);
      setOrder(o); setDelivery(d);
    } catch (e) { setError(e instanceof Error ? e.message : 'Không tải được đơn hàng.'); }
    finally { setLoading(false); }
  }, [orderId]);
  useEffect(() => { void load(); }, [load]);

  const showMatching = async () => {
    setPanelBusy('matching'); setPanelError('');
    try { setMatching(await api.matching(orderId)); }
    catch (e) { setPanelError(e instanceof Error ? e.message : 'Không tải được tiến trình tìm tài xế.'); }
    finally { setPanelBusy(null); }
  };
  const showLocation = async () => {
    if (!delivery) return;
    setPanelBusy('location'); setPanelError('');
    try { setLocation(await api.driverLocation(delivery.deliveryId)); }
    catch (e) { setLocation(null); setPanelError(e instanceof Error ? e.message : 'Không tải được vị trí tài xế.'); }
    finally { setPanelBusy(null); }
  };
  const copyId = async () => {
    await navigator.clipboard?.writeText(orderId).catch(() => undefined);
    setCopied(true); setTimeout(() => setCopied(false), 1500);
  };

  const trackable = !!delivery?.driverId && TRACKABLE.includes(delivery.status);
  return <>
    <div className="page-head">
      <div className="head-title">
        <button className="link back" onClick={onBack}>‹ Đơn hàng</button>
        <h1>{orderCode(orderId)}</h1>
        {order && <Status value={order.status} />}
      </div>
      <div className="actions">
        <Button variant="soft" onClick={() => void copyId()}>{copied ? 'Đã sao chép' : 'Sao chép mã đơn'}</Button>
        <Button variant="outline" onClick={() => void load()} busy={loading}>{icons.refresh} Làm mới</Button>
      </div>
    </div>
    {error && <Alert onRetry={() => void load()}>{error}</Alert>}
    {order && <div className="grid-2">
      <div className="stack">
        <Card title="Lộ trình">
          <div className="route">
            <div><b>A</b><span><small>Điểm lấy hàng</small>{order.pickupAddress}</span></div>
            <div><b className="b">B</b><span><small>Điểm giao hàng</small>{order.dropoffAddress}</span></div>
          </div>
          <Row label="Khoảng cách" tone="green">{km(order.distanceKm)}</Row>
          <Row label="SĐT người nhận">{order.receiverPhone || '—'}</Row>
          <Row label="Mã khách hàng">{shortGuid(order.customerId)}</Row>
          <Row label="Tạo lúc">{dateTime(order.createdAt)}</Row>
          <Row label="Cập nhật">{dateTime(order.updatedAt)}</Row>
        </Card>
        <Card title="Chi phí">
          <Row label="Phí cơ bản">{vnd(order.baseFee)}</Row>
          <Row label="Phụ phí">{vnd(order.surcharge)}</Row>
          <Row label="Phí nền tảng">{vnd(order.platformFee)}</Row>
          <Row label="Giảm giá khuyến mãi" tone="red">−{vnd(order.discountPromotion)}</Row>
          <Row label="Giảm bằng điểm" tone="red">−{vnd(order.discountPoints)}</Row>
          <div className="total"><span>Tổng phí đơn</span><strong>{vnd(order.totalFee)}</strong></div>
          <Row label="Hoàn tiền" tone="muted">{refundName[order.refundStatus] ?? order.refundStatus}</Row>
        </Card>
      </div>
      <div className="stack">
        <Card title="Chuyến giao">
          {delivery ? <>
            <Row label="Trạng thái"><Status value={delivery.status} /></Row>
            <Row label="Tài xế">{delivery.driverId ? shortGuid(delivery.driverId) : 'Chưa có'}</Row>
            <ol className="timeline">
              {[
                ['Bắt đầu tìm tài xế', delivery.searchStartedAt],
                ['Tài xế nhận đơn', delivery.assignedAt],
                ['Đã lấy hàng', delivery.pickedUpAt],
                ['Bắt đầu giao', delivery.deliveringAt],
                ['Hoàn tất', delivery.completedAt],
                ...(delivery.cancelledAt ? [['Đã hủy', delivery.cancelledAt]] : []),
              ].map(([label, at]) => <li key={label} className={at ? 'done' : ''}><span>{label}</span><time>{dateTime(at)}</time></li>)}
            </ol>
          </> : <p className="muted">Đơn chưa được chuyển sang Delivery Service (đang chờ hệ thống tạo chuyến giao).</p>}
          <div className="actions">
            <Button variant="soft" onClick={() => void showMatching()} busy={panelBusy === 'matching'} disabled={!delivery}>Tiến trình tìm tài xế</Button>
            <Button onClick={() => void showLocation()} busy={panelBusy === 'location'} disabled={!trackable}
              title={trackable ? undefined : 'Chỉ có khi tài xế đã nhận và chưa giao xong'}>Vị trí tài xế</Button>
          </div>
          {panelError && <Alert>{panelError}</Alert>}
        </Card>
        {matching && <Card title="Tiến trình tìm tài xế" action={<button className="link" onClick={() => setMatching(null)}>Đóng</button>}>
          <Row label="Trạng thái"><Status value={matching.searchStatus} /></Row>
          <Row label="Bán kính hiện tại" tone="green">{km(matching.currentRadiusKm)}</Row>
          <Row label="Tài xế phù hợp">{matching.candidateCount}</Row>
          <Row label="Đã mời">{matching.invitedDrivers}</Row>
          <Row label="Còn chờ mời">{matching.remainingCandidateCount}</Row>
          <Row label="Bắt đầu">{dateTime(matching.searchStartedAt)}</Row>
          <Row label="Kết thúc">{dateTime(matching.searchEndedAt)}</Row>
        </Card>}
        {location && <Card title="Vị trí tài xế" action={<button className="link" onClick={() => setLocation(null)}>Đóng</button>}>
          <Row label="Tọa độ">{location.latitude.toFixed(5)}, {location.longitude.toFixed(5)}</Row>
          <Row label="Sai số">{location.accuracyM != null ? `${Math.round(location.accuracyM)} m` : '—'}</Row>
          <Row label="Ghi nhận lúc">{dateTime(location.recordedAt)}</Row>
          <a className="btn primary" target="_blank" rel="noreferrer"
            href={`https://www.openstreetmap.org/?mlat=${location.latitude}&mlon=${location.longitude}#map=17/${location.latitude}/${location.longitude}`}>
            Mở bản đồ
          </a>
        </Card>}
      </div>
    </div>}
  </>;
}
