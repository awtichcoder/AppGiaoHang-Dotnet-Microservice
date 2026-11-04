import { useCallback, useEffect, useState } from 'react';
import { api, type User } from '../api';
import { Button, Card, icons, Row } from '../ui';

const CHECKS: [string, string][] = [
  ['API Gateway', '/health/live'],
  ['Identity Service', '/health/identity/ready'],
  ['Order Service', '/health/order/ready'],
  ['Delivery Service', '/health/delivery/ready'],
  ['Driver Service', '/health/driver/ready'],
];
type Result = { ok: boolean; text: string };

export function Profile({ admin, onLogout }: { admin: User; onLogout: () => void }) {
  const [results, setResults] = useState<Record<string, Result>>({});
  const [checking, setChecking] = useState(false);
  const [leaving, setLeaving] = useState(false);

  const check = useCallback(async () => {
    setChecking(true);
    const entries = await Promise.all(CHECKS.map(async ([name, path]) => {
      try { const r = await api.health(path); return [name, { ok: /healthy|ready/i.test(r.status), text: r.status }] as const; }
      catch (e) { return [name, { ok: false, text: e instanceof Error ? e.message : 'Lỗi' }] as const; }
    }));
    setResults(Object.fromEntries(entries)); setChecking(false);
  }, []);
  useEffect(() => { void check(); }, [check]);

  return <>
    <div className="page-head"><h1>Tài khoản</h1></div>
    <div className="grid-2">
      <Card title="Quản trị viên">
        <div className="person big">
          <span className="avatar">{admin.fullName.trim().split(/\s+/).pop()?.[0]?.toUpperCase()}</span>
          <div><strong>{admin.fullName}</strong><small>{admin.email}</small></div>
        </div>
        <Row label="Số điện thoại">{admin.phone || '—'}</Row>
        <Row label="Vai trò" tone="green">{admin.role}</Row>
        <Row label="Mã người dùng">{admin.userId.slice(0, 8).toUpperCase()}</Row>
        <Button variant="danger" busy={leaving} onClick={() => { setLeaving(true); void api.logout().finally(onLogout); }}>Đăng xuất</Button>
      </Card>
      <Card title="Tình trạng hệ thống" action={<Button variant="outline" onClick={() => void check()} busy={checking}>{icons.refresh} Kiểm tra</Button>}>
        {CHECKS.map(([name]) => {
          const r = results[name];
          return <div key={name} className="health">
            <span className={`dot ${r ? (r.ok ? 'ok' : 'bad') : ''}`} />
            <span>{name}</span>
            <small>{r ? (r.ok ? 'Hoạt động' : r.text) : 'Đang kiểm tra…'}</small>
          </div>;
        })}
      </Card>
    </div>
  </>;
}
