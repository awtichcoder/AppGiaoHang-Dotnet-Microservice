import type { ReactNode } from 'react';

// Icons are the driver app's Figma assets (BottomNavBar), plus two simple strokes for actions.
export const icons = {
  home: <svg width="16" height="22" viewBox="0 0 16 22" aria-hidden><path fill="currentColor" d="M2 16H5V10H11V16H14V7L8 2.5L2 7V16M0 18V6L8 0L16 6V18H9V12H7V18H0Z" /></svg>,
  orders: <svg width="18" height="24" viewBox="0 0 18 24" aria-hidden><path fill="currentColor" d="M8 17.425V10.575L2 7.1V13.95L8 17.425M10 17.425L16 13.95V7.1L10 10.575V17.425M8 19.725L1 15.7C.683 15.517.438 15.275.263 14.975.088 14.675 0 14.342 0 13.975V6.025C0 5.658.088 5.325.263 5.025.438 4.725.683 4.483 1 4.3L8 .275C8.317.092 8.65 0 9 0S9.683.092 10 .275L17 4.3C17.317 4.483 17.563 4.725 17.738 5.025 17.913 5.325 18 5.658 18 6.025V13.975C18 14.342 17.913 14.675 17.738 14.975 17.563 15.275 17.317 15.517 17 15.7L10 19.725C9.683 19.908 9.35 20 9 20S8.317 19.908 8 19.725M13 6.525L14.925 5.425 9 2 7.05 3.125 13 6.525M9 8.85L10.95 7.725 5.025 4.3 3.075 5.425 9 8.85Z" /></svg>,
  account: <svg width="16" height="20" viewBox="0 0 16 20" aria-hidden><path fill="currentColor" d="M8 8C6.9 8 5.958 7.608 5.175 6.825 4.392 6.042 4 5.1 4 4S4.392 1.958 5.175 1.175C5.958.392 6.9 0 8 0S10.042.392 10.825 1.175C11.608 1.958 12 2.9 12 4S11.608 6.042 10.825 6.825C10.042 7.608 9.1 8 8 8M0 16V13.2C0 12.633.146 12.113.438 11.638.729 11.163 1.117 10.8 1.6 10.55 2.633 10.033 3.683 9.646 4.75 9.388 5.817 9.129 6.9 9 8 9S10.183 9.129 11.25 9.388C12.317 9.646 13.367 10.033 14.4 10.55 14.883 10.8 15.271 11.163 15.563 11.638 15.854 12.113 16 12.633 16 13.2V16H0M2 14H14V13.2C14 13.017 13.954 12.85 13.863 12.7 13.771 12.55 13.65 12.433 13.5 12.35 12.6 11.9 11.692 11.563 10.775 11.338 9.858 11.113 8.933 11 8 11S6.142 11.113 5.225 11.338C4.308 11.563 3.4 11.9 2.5 12.35 2.35 12.433 2.229 12.55 2.138 12.7 2.046 12.85 2 13.017 2 13.2V14M8 6C8.55 6 9.021 5.804 9.413 5.413 9.804 5.021 10 4.55 10 4S9.804 2.979 9.413 2.588C9.021 2.196 8.55 2 8 2S6.979 2.196 6.588 2.588C6.196 2.979 6 3.45 6 4S6.196 5.021 6.588 5.413C6.979 5.804 7.45 6 8 6Z" /></svg>,
  people: <svg width="22" height="18" viewBox="0 0 22 18" aria-hidden><path fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" d="M8 8a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7ZM1 17v-1.5C1 13 3.5 11 8 11s7 2 7 4.5V17M15 1.5a3.5 3.5 0 0 1 0 6.5M18 11.5c2 .7 3 2 3 4V17" /></svg>,
  refresh: <svg width="14" height="14" viewBox="0 0 24 24" aria-hidden><path fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" d="M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7" /></svg>,
};

export function Card({ title, action, children, className = '' }: { title?: string; action?: ReactNode; children: ReactNode; className?: string }) {
  return <section className={`card ${className}`}>
    {(title || action) && <div className="card-head">{title && <h2>{title}</h2>}{action}</div>}
    {children}
  </section>;
}

type ButtonProps = { children: ReactNode; onClick?: () => void; disabled?: boolean; type?: 'button' | 'submit'; variant?: 'primary' | 'soft' | 'outline' | 'danger'; busy?: boolean; title?: string };
export function Button({ children, onClick, disabled, type = 'button', variant = 'primary', busy, title }: ButtonProps) {
  return <button type={type} className={`btn ${variant}`} onClick={onClick} disabled={disabled || busy} title={title}>
    {busy ? <span className="spinner" /> : children}
  </button>;
}

export const statusName: Record<string, string> = {
  PENDING: 'Chờ tài xế', SEARCHING: 'Đang tìm tài xế', ASSIGNED: 'Tài xế đã nhận', PICKED_UP: 'Đã lấy hàng',
  DELIVERING: 'Đang giao', COMPLETED: 'Hoàn tất', CANCELLED: 'Đã hủy', NO_DRIVER_FOUND: 'Không có tài xế',
  NOT_STARTED: 'Chưa bắt đầu', ACTIVE: 'Đang chạy',
};
export const ORDER_STATUSES = ['PENDING', 'ASSIGNED', 'PICKED_UP', 'DELIVERING', 'COMPLETED', 'CANCELLED', 'NO_DRIVER_FOUND'];

export function Status({ value }: { value?: string | null }) {
  if (!value) return <span className="status">—</span>;
  return <span className={`status s-${value.toLowerCase().replaceAll('_', '-')}`}>{statusName[value] ?? value}</span>;
}

export function Row({ label, children, tone }: { label: string; children: ReactNode; tone?: 'green' | 'red' | 'muted' }) {
  return <div className="row"><span>{label}</span><strong className={tone ? `t-${tone}` : ''}>{children}</strong></div>;
}

export function Alert({ children, onRetry }: { children: ReactNode; onRetry?: () => void }) {
  return <div className="alert">{children}{onRetry && <button className="link" onClick={onRetry}>Thử lại</button>}</div>;
}

export const vnd = (value: number) => new Intl.NumberFormat('vi-VN').format(Math.round(value)) + 'đ';
export const km = (value: number) => new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value) + ' km';
export const orderCode = (id: string) => '#' + id.slice(0, 8).toUpperCase();
export const shortGuid = (id?: string | null) => (id ? id.slice(0, 8).toUpperCase() : '—');
/** Backend timestamps are UTC; values read from SQL Server come without an offset. */
export function parseUtc(value?: string | null) {
  if (!value) return null;
  return new Date(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : value + 'Z');
}
export const dateTime = (value?: string | null) => parseUtc(value)?.toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' }) ?? '—';
