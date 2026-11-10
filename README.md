# DeliveryApp — Web Quản trị (Admin)

Nhánh `admin` chứa web quản trị DeliveryApp. Các ứng dụng khác nằm ở nhánh riêng: `customer` (app Khách hàng), `shipper` (app Tài xế). Backend nằm ở nhánh `main`.

```bash
git clone -b admin https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice.git AdminWeb
cd AdminWeb
npm install
npm run dev
```


Web quản trị React + Vite + TypeScript, giao diện theo phong cách app Tài xế (màu, thẻ, nút, icon lấy từ thiết kế Figma của app Tài xế). Mọi request đi qua API Gateway.

## Chạy local

```powershell
npm install
npm run dev
```

Mở `http://127.0.0.1:5173`. Backend phải đang chạy (`docker compose up -d` trong thư mục backend).

Tài khoản phát triển do backend tạo sẵn: `admin@deliveryapp.local / Admin123!`

### CORS

Trình duyệt gọi `/api/...` và `/health/...` trên chính cổng 5173; Vite chuyển tiếp sang Gateway (`vite.config.ts`). Vì cùng origin nên Gateway không cần bật CORS khi chạy local.

- Đổi Gateway mà proxy trỏ tới: `VITE_PROXY_TARGET=http://<host>:6200` (xem `.env.example`).
- `npm run preview` (bản build) dùng cùng proxy ở cổng 4173.
- Khi deploy, phục vụ web và Gateway sau cùng một domain/reverse proxy, hoặc đặt `VITE_API_URL` tới một Gateway có bật CORS.

## Chức năng (theo API backend hiện có)

| Màn hình | Chức năng | API |
|---|---|---|
| Đăng nhập | Đăng nhập, chỉ cho vai trò ADMIN, tự làm mới access token, đăng xuất | `POST /api/identity/login`, `GET /api/identity/me`, `POST /api/identity/refresh`, `POST /api/identity/logout` |
| Tổng quan | Số đơn theo từng trạng thái (đếm chính xác bằng bộ lọc `status`), doanh thu = tổng phí đơn hoàn tất, 5 đơn mới nhất | `GET /api/order?status=…` |
| Đơn hàng | Lọc theo trạng thái, phân trang, tìm trong trang | `GET /api/order?page=&pageSize=&status=` |
| Chi tiết đơn | Lộ trình, chi phí, chuyến giao và mốc thời gian; nút **Tiến trình tìm tài xế**, **Vị trí tài xế** (mở bản đồ) | `GET /api/order/{id}`, `GET /api/delivery/order/{id}`, `GET /api/delivery/order/{id}/matching-status`, `GET /api/delivery/{deliveryId}/location` |
| Tạo tài khoản | Tạo tài khoản Tài xế hoặc Quản trị viên | `POST /api/identity/admin/users` |
| Tài khoản | Hồ sơ admin, đăng xuất, kiểm tra tình trạng Gateway và các service | `GET /health/live`, `GET /health/{service}/ready` |

Chưa làm vì backend chưa có API: danh sách/duyệt tài xế, danh sách khách hàng, quản lý khuyến mãi, báo cáo doanh thu theo kỳ, quản lý nhân sự, khóa/hủy tài khoản hoặc đơn.

## Cấu trúc

```text
src/
  api.ts          # gọi Gateway, lưu phiên (sessionStorage), refresh token, lỗi {code, message}
  ui.tsx          # Card, Button, Status, Row, icon, định dạng tiền/ngày
  main.tsx        # header, tab, điều hướng theo hash (#/orders/<id>)
  pages/          # Login, Dashboard, Orders, OrderDetail, Accounts, Profile
  styles.css      # token màu của app Tài xế
```
