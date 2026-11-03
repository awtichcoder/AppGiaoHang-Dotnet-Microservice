# Delivery Admin Web

Admin web React/Vite bám theo màn hình `Admin Web` trong Figma và gọi API qua API Gateway.

## Chạy local

```powershell
npm install
npm run dev
```

Mặc định frontend gọi `http://localhost:6200`. Có thể đổi bằng biến môi trường `VITE_API_URL`.

Tài khoản phát triển từ backend:

```text
admin@deliveryapp.local / Admin123!
```

## Phạm vi API hiện tại

- Đăng nhập và đọc hồ sơ admin: `POST /api/identity/login`, `GET /api/identity/me`.
- Danh sách đơn và doanh thu suy ra từ các đơn đã tải: `GET /api/order`.
- Promotion đang hoạt động: `GET /api/promotion`.
- Tạo tài khoản `DRIVER`/`ADMIN`: `POST /api/identity/admin/users`.

Backend hiện chưa có API quản trị để list/duyệt tài xế, list khách hàng, CRUD promotion, báo cáo doanh thu theo kỳ hoặc quản lý nhân sự đầy đủ. Các module tương ứng được hiển thị với trạng thái chờ API thay vì dùng dữ liệu giả.
