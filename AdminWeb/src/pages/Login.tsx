import { useState, type FormEvent } from 'react';
import { api, type User } from '../api';
import { Alert, Button } from '../ui';

export function Login({ onLogin, notice }: { onLogin: (user: User) => void; notice?: string }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(notice ?? '');

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true); setError('');
    try { onLogin(await api.login(email.trim().toLowerCase(), password)); }
    catch (e) { setError(e instanceof Error ? e.message : 'Đăng nhập thất bại.'); }
    finally { setBusy(false); }
  };

  return <div className="login">
    <div className="login-hero">
      <div className="brand-mark">D</div>
      <h1>DeliveryApp</h1>
      <p>QUẢN TRỊ VIÊN</p>
    </div>
    <form className="card login-card" onSubmit={submit}>
      <h2>Đăng nhập</h2>
      <p className="muted">Dùng tài khoản có quyền ADMIN.</p>
      <label>Email<input type="email" required autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} /></label>
      <label>Mật khẩu<input type="password" required autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
      {error && <Alert>{error}</Alert>}
      <Button type="submit" busy={busy}>Đăng nhập</Button>
      {import.meta.env.DEV && <div className="dev-account">
        <span>Tài khoản backend local: admin@deliveryapp.local / Admin123!</span>
        <button type="button" className="link" onClick={() => { setEmail('admin@deliveryapp.local'); setPassword('Admin123!'); }}>Điền tài khoản</button>
      </div>}
    </form>
  </div>;
}
