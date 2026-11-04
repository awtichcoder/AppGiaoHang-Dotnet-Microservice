// API Gateway client. In development the base URL is empty: requests go to the Vite server,
// which proxies /api and /health to the Gateway (see vite.config.ts), so no CORS is needed.
const BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? '';
const SESSION_KEY = 'delivery_admin_session';

export type Session = { accessToken: string; refreshToken: string };
export type User = { userId: string; fullName: string; email: string; phone: string; role: string };
export type Paged<T> = { page: number; pageSize: number; totalItems: number; items: T[] };
export type Order = {
  orderId: string; customerId: string; pickupAddress: string; dropoffAddress: string; receiverPhone: string;
  distanceKm: number; baseFee: number; surcharge: number; platformFee: number;
  discountPromotion: number; discountPoints: number; totalFee: number;
  status: string; matchingStatus: string; version: number; createdAt: string; updatedAt?: string | null; refundStatus: string;
};
export type Delivery = {
  deliveryId: string; orderId: string; customerId: string; driverId?: string | null;
  pickupAddress: string; dropoffAddress: string; receiverPhone?: string | null; status: string;
  currentRadiusKm: number; searchStartedAt: string; searchEndedAt?: string | null; version: number; totalFee: number;
  assignedAt?: string | null; pickedUpAt?: string | null; deliveringAt?: string | null; completedAt?: string | null; cancelledAt?: string | null;
};
export type MatchingStatus = {
  orderId: string; status: string; searchStatus: string; currentRadiusKm: number; invitedDrivers: number;
  attemptCount: number; candidateCount: number; remainingCandidateCount: number; searchStartedAt: string; searchEndedAt?: string | null;
};
export type DriverLocation = { deliveryId: string; driverId: string; latitude: number; longitude: number; recordedAt: string; accuracyM?: number | null };
export type NewAccount = { email: string; password: string; fullName: string; phone: string; role: 'DRIVER' | 'ADMIN' };

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}

export const session = {
  get(): Session | null {
    try { return JSON.parse(sessionStorage.getItem(SESSION_KEY) ?? 'null'); } catch { return null; }
  },
  set(value: Session | null) {
    if (value) sessionStorage.setItem(SESSION_KEY, JSON.stringify(value)); else sessionStorage.removeItem(SESSION_KEY);
  },
};

async function send<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  let response: Response;
  try {
    response = await fetch(BASE + path, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers },
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Không kết nối được API Gateway. Hãy kiểm tra backend đang chạy ở cổng 6200.');
  }
  if (!response.ok) {
    const text = await response.text();
    let body: { code?: string; message?: string } = {};
    try { body = JSON.parse(text); } catch { /* not JSON */ }
    const fallback = response.status === 502 || response.status === 504
      ? 'API Gateway không phản hồi. Hãy kiểm tra backend.'
      : `Máy chủ trả lỗi ${response.status}.`;
    throw new ApiError(response.status, body.code ?? `HTTP_${response.status}`, body.message ?? fallback);
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

let refreshing: Promise<Session | null> | null = null;
async function refresh(): Promise<Session | null> {
  const current = session.get();
  if (!current) return null;
  refreshing ??= send<Session>('/api/identity/refresh', { method: 'POST', body: JSON.stringify({ refreshToken: current.refreshToken }) })
    .then(next => { session.set(next); return next; })
    .catch(() => { session.set(null); return null; })
    .finally(() => { refreshing = null; });
  return refreshing;
}

/** Authenticated call; refreshes the access token once on 401. */
async function authed<T>(path: string, init: RequestInit = {}): Promise<T> {
  const current = session.get();
  if (!current) throw new ApiError(401, 'SESSION_EXPIRED', 'Phiên đăng nhập đã hết hạn.');
  try {
    return await send<T>(path, init, current.accessToken);
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    const next = await refresh();
    if (!next) throw new ApiError(401, 'SESSION_EXPIRED', 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
    return send<T>(path, init, next.accessToken);
  }
}

export const api = {
  async login(email: string, password: string): Promise<User> {
    const tokens = await send<Session & { expiresAt: string }>('/api/identity/login', { method: 'POST', body: JSON.stringify({ email, password }) });
    session.set({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
    const me = await api.me();
    if (me.role !== 'ADMIN') {
      await api.logout();
      throw new ApiError(403, 'ADMIN_REQUIRED', 'Tài khoản này không có quyền quản trị (ADMIN).');
    }
    return me;
  },
  me: () => authed<User>('/api/identity/me'),
  async logout() {
    const current = session.get();
    session.set(null);
    if (current) await send('/api/identity/logout', { method: 'POST', body: JSON.stringify({ refreshToken: current.refreshToken }) }).catch(() => undefined);
  },
  orders: (page: number, pageSize: number, status?: string) =>
    authed<Paged<Order>>(`/api/order?page=${page}&pageSize=${pageSize}${status ? `&status=${status}` : ''}`),
  order: (orderId: string) => authed<Order>(`/api/order/${orderId}`),
  delivery: (orderId: string) => authed<Delivery>(`/api/delivery/order/${orderId}`),
  matching: (orderId: string) => authed<MatchingStatus>(`/api/delivery/order/${orderId}/matching-status`),
  driverLocation: (deliveryId: string) => authed<DriverLocation>(`/api/delivery/${deliveryId}/location`),
  createAccount: (account: NewAccount) => authed<User>('/api/identity/admin/users', { method: 'POST', body: JSON.stringify(account) }),
  health: (path: string) => send<{ status: string }>(path),
};
