import { useState, type FormEvent } from 'react';
import { api, type NewAccount, type User } from '../api';
import { Alert, Button, Card } from '../ui';

const EMPTY: NewAccount = { fullName: '', email: '', phone: '', password: '', role: 'DRIVER' };

/** POST /api/identity/admin/users — the only account endpoint the backend offers to ADMIN. */
export function Accounts() {
  const [form, setForm] = useState<NewAccount>(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [created, setCreated] = useState<User[]>([]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true); setError('');
    try {
      const user = await api.createAccount({ ...form, email: form.email.trim().toLowerCase(), fullName: form.fullName.trim(), phone: form.phone.trim() });
      setCreated(list => [user, ...list]);
      setForm({ ...EMPTY, role: form.role });
    } catch (e) { setError(e instanceof Error ? e.message : 'Không tạo được tài khoản.'); }
    finally { setBusy(false); }
  };
  const set = (key: keyof NewAccount) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value });

  return <>
    <div className="page-head"><h1>Tạo tài khoản</h1></div>
    <div className="grid-2">
      <Card title="Tài khoản tài xế hoặc quản trị viên">
        <form className="form" onSubmit={submit}>
          <div className="segmented">
            {(['DRIVER', 'ADMIN'] as const).map(role => <button type="button" key={role} className={form.role === role ? 'active' : ''}
              onClick={() => setForm({ ...form, role })}>{role === 'DRIVER' ? 'Tài xế' : 'Quản trị viên'}</button>)}
          </div>
          <label>Họ tên<input required maxLength={200} value={form.fullName} onChange={set('fullName')} /></label>
          <label>Email<input required type="email" maxLength={256} value={form.email} onChange={set('email')} /></label>
          <label>Số điện thoại<input required type="tel" maxLength={20} value={form.phone} onChange={set('phone')} /></label>
          <label>Mật khẩu<input required type="password" minLength={6} maxLength={100} value={form.password} onChange={set('password')} />
            <small>Tối thiểu 6 ký tự. Gửi mật khẩu cho người dùng qua kênh an toàn.</small></label>
          {error && <Alert>{error}</Alert>}
          <Button type="submit" busy={busy}>Tạo tài khoản {form.role === 'DRIVER' ? 'tài xế' : 'quản trị viên'}</Button>
        </form>
      </Card>
      <Card title="Đã tạo trong phiên này">
        {created.length ? <div className="stack">
          {created.map(user => <div key={user.userId} className="person">
            <span className="avatar">{user.fullName.trim().split(/\s+/).pop()?.[0]?.toUpperCase()}</span>
            <div><strong>{user.fullName}</strong><small>{user.email} · {user.phone}</small></div>
            <span className={`role ${user.role.toLowerCase()}`}>{user.role === 'DRIVER' ? 'Tài xế' : 'Quản trị viên'}</span>
          </div>)}
        </div> : <p className="muted">Tài khoản mới sẽ hiện ở đây. Backend chưa có API liệt kê người dùng.</p>}
      </Card>
    </div>
  </>;
}
