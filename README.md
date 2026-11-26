# DeliveryApp — Hệ thống giao hàng theo kiến trúc Microservices

Thư mục `C:\CNPM\COM` gồm **4 thành phần** của hệ thống DeliveryApp. Mỗi thành phần là một bản clone riêng (một branch riêng) của cùng một repo Git:

| Thư mục | Branch | Thành phần | Công nghệ | Quy mô |
|---|---|---|---|---|
| `BE-được-tách-ra-từ-cái-trước/` | `feature/complaints` (tách từ `BE-được-tách-ra-từ-cái-trước`) | **Backend**: API Gateway + 8 microservice + SQL Server | .NET 9, ASP.NET Core, EF Core 9, YARP, Docker | ~8.100 dòng C#, 70 unit test |
| `admin/` | `admin` | **Web Quản trị** | React 19, Vite 8, TypeScript | ~950 dòng TS/TSX |
| `customer/` | `customer` | **App Khách hàng** (Android) | Kotlin 2.2, Jetpack Compose, Material 3 | ~6.000 dòng Kotlin, 42 test |
| `shipper/` | `shipper` | **App Tài xế** (Android) | Kotlin 2.2, Jetpack Compose, Material 3 | ~4.200 dòng Kotlin, 54 test |

Remote: `https://github.com/KhoaNguyenx-110/ki-t-l-c-x-m-l--s--t-n-token.git`

Mọi client chỉ gọi **API Gateway** ở cổng `6200`. Các service phía sau gọi nhau qua API nội bộ, xác thực bằng `X-Internal-Key`.

---

## Mục lục

1. [Kiến trúc tổng thể](#1-kiến-trúc-tổng-thể)
2. [Cấu trúc thư mục](#2-cấu-trúc-thư-mục)
3. [Chạy toàn bộ hệ thống](#3-chạy-toàn-bộ-hệ-thống)
4. [Luồng nghiệp vụ chính](#4-luồng-nghiệp-vụ-chính)
5. [Backend: các microservice](#5-backend-các-microservice)
6. [API public qua Gateway](#6-api-public-qua-gateway)
7. [Web Quản trị (admin)](#7-web-quản-trị-admin)
8. [App Khách hàng (customer)](#8-app-khách-hàng-customer)
9. [App Tài xế (shipper)](#9-app-tài-xế-shipper)
10. [Bảo mật](#10-bảo-mật)
11. [Kiểm thử và CI](#11-kiểm-thử-và-ci)
12. [Demo trên điện thoại thật](#12-demo-trên-điện-thoại-thật)
13. [Lỗi thường gặp](#13-lỗi-thường-gặp)
14. [Giới hạn hiện tại](#14-giới-hạn-hiện-tại)
15. [Tài liệu chi tiết](#15-tài-liệu-chi-tiết)

---

## 1. Kiến trúc tổng thể

```text
 ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
 │ App Khách hàng│  │  App Tài xế   │  │ Web Quản trị  │
 │ Android/Compose│ │ Android/Compose│ │ React + Vite  │
 └───────┬───────┘  └───────┬───────┘  └───────┬───────┘
         │   HTTP + JWT (Bearer)                │ (proxy Vite :5173)
         └──────────────────┼───────────────────┘
                            ▼
                ┌───────────────────────┐
                │  API Gateway (YARP)   │  :6200   /health/live, /health/ready
                └───────────┬───────────┘
   ┌──────────┬──────────┬──┴───────┬──────────┬──────────┬──────────┬──────────┐
   ▼          ▼          ▼          ▼          ▼          ▼          ▼          ▼
Identity   Driver     Order     Delivery   Promotion  Loyalty     Map     Complaint
 :6203     :6202      :6206      :6201      :6207      :6204     :6205      :6208
   │          │          │          │          │          │                   │
   └──────────┴──────────┴──── SQL Server 2022 (:1433), mỗi service 1 database ┘

Gọi nội bộ (X-Internal-Key, Gateway không route):
  Order    ─► Map (khoảng cách), Delivery (/quote, /assign), Promotion, Loyalty (giữ/commit/hoàn trả)
  Delivery ─► Driver (tài xế gần, busy/available), Order (đồng bộ trạng thái), Loyalty (cộng điểm)
  Complaint─► Delivery (complaint-context)
```

Các kỹ thuật chính:

- **Database-per-service**: `DeliveryApp_Identity`, `_Driver`, `_Order`, `_Delivery`, `_Promotion`, `_Loyalty`, `_Complaint`. Map service không có DB (tính Haversine).
- **Transactional Outbox** ở Order và Delivery: gọi service khác qua bảng `OutboxMessages` + worker nền, retry khi lỗi mạng/5xx, dừng (`PARKED:`) sau 5 lần lỗi nghiệp vụ 4xx.
- **Reserve → Commit → Release** cho mã khuyến mãi và điểm thưởng (giữ khi đặt đơn, trừ khi tài xế lấy hàng, hoàn trả khi hủy/không có tài xế).
- **Idempotency**: `clientRequestId` + hash nội dung chống tạo trùng đơn.
- **Optimistic concurrency** (`Version`) và transaction `Serializable` khi tài xế nhận lời mời.
- **Correlation ID**: Gateway gắn header `X-Correlation-ID` cho mọi request.
- Migration EF Core tự chạy khi container khởi động (`APPLY_MIGRATIONS=true`), tài khoản mẫu được seed tự động.

---

## 2. Cấu trúc thư mục

```text
C:\CNPM\COM
├── BE-được-tách-ra-từ-cái-trước\     Backend .NET 9
│   ├── ApiGateway\                 YARP reverse proxy, CORS cho admin, health check tổng
│   ├── IdentityService\            đăng ký, đăng nhập, JWT, refresh token, seed tài khoản
│   ├── DriverService\              trạng thái OFFLINE/AVAILABLE/BUSY + GPS tài xế
│   ├── MapService\                 tính khoảng cách Haversine
│   ├── OrderService\               đơn hàng, giá, máy trạng thái, outbox
│   ├── DeliveryService\            báo giá, ghép tài xế, lời mời, chuyến giao, outbox
│   ├── PromotionService\           mã ưu đãi, giữ/commit/hoàn trả
│   ├── LoyaltyService\             điểm thưởng, hạng thành viên
│   ├── ComplaintService\           khiếu nại + ảnh đính kèm (lưu tạm)
│   ├── *.Tests\                    xUnit: Order, Delivery, Loyalty, Promotion, Complaint
│   ├── postman\                    2 collection Postman
│   ├── scripts\                    New-DevJwt.ps1, Test-CustomerIntegration.ps1, Test-ComplaintIntegration.ps1
│   ├── docker-compose.yml, Dockerfile, .env.example, DeliveryApp.sln
│   └── .github\workflows\ci.yml
├── admin\                          Web quản trị
│   ├── src\api.ts                  gọi Gateway, lưu phiên, refresh token
│   ├── src\ui.tsx, main.tsx        component chung, header/tab, router theo hash
│   ├── src\pages\                  Login, Dashboard, Orders, OrderDetail, Complaints, ComplaintDetail, Accounts, Profile
│   └── vite.config.ts              proxy /api, /health → Gateway
├── customer\                       App Khách hàng (module :app, com.deliveryapp.customer)
│   └── app\src\main\java\com\deliveryapp\customer\
│       ├── core\                   network (Retrofit/OkHttp, AuthInterceptor, TokenRefreshAuthenticator), storage, location
│       ├── data\                   api, dto, repository
│       ├── feature\                auth, home, order (tạo/danh sách/theo dõi), rewards, profile, complaint
│       ├── navigation\             AppRoutes, AppNavHost
│       └── ui\                     components (bản đồ MapLibre, bottom bar), theme
└── shipper\                        App Tài xế (module :shipper, com.deliveryapp.shipper)
    └── shipper\
        ├── src\main\java\com\deliveryapp\shipper\
        │   ├── core\               network, storage (token mã hóa, cài đặt), location
        │   ├── data\               api, model, repository (Live + Mock)
        │   ├── feature\            auth, home, work, delivery, history, profile, promotion, complaint
        │   └── navigation\, ui\
        └── docs\                   KIEN_TRUC, API, KIEM_THU, RA_SOAT, CHUAN_HOA_NATIVE, screenshots
```

---

## 3. Chạy toàn bộ hệ thống

### 3.1. Cài đặt cần có

| Phần | Cần cài |
|---|---|
| Backend | Docker Desktop (bật WSL2). .NET 9 SDK chỉ cần khi chạy test hoặc chạy không dùng Docker |
| Web Admin | Node.js (CI dùng Node 24) + npm |
| App Android | Android Studio (JDK 17), Android SDK 36, Emulator API 26+ |

### 3.2. Backend

> ⚠️ Thư mục `BE-được-tách-ra-từ-cái-trước` có **dấu tiếng Việt** trong đường dẫn, Docker có thể báo lỗi `x-docker-expose-session-sharedkey ... non-printable ASCII`. Nên sao chép/clone sang thư mục không dấu, ví dụ `C:\src\deliveryapp-be`, rồi chạy ở đó.

```powershell
cd C:\src\deliveryapp-be                  # bản sao không dấu của BE-được-tách-ra-từ-cái-trước
Copy-Item .env.example .env
docker compose up --build -d              # lần đầu 5–10 phút
docker compose ps                         # chờ tất cả "healthy"
Invoke-RestMethod http://localhost:6200/health/ready   # status = Ready
```

Lệnh khác: `docker compose logs -f orderservice deliveryservice` (xem log), `docker compose down` (dừng), `docker compose down -v` (xóa sạch dữ liệu và ảnh khiếu nại). VS Code có sẵn task trong `.vscode/tasks.json`.

### 3.3. Web Admin

```powershell
cd C:\CNPM\COM\admin
npm install
npm run dev          # mở http://127.0.0.1:5173
```

### 3.4. App Khách hàng và App Tài xế

Mở `customer\` hoặc `shipper\` bằng Android Studio, chờ Gradle Sync, chọn Emulator rồi Run. Hoặc dùng dòng lệnh:

```powershell
cd C:\CNPM\COM\customer
.\gradlew.bat :app:installDebug

cd C:\CNPM\COM\shipper
.\gradlew.bat :shipper:installDebug
.\gradlew.bat :shipper:installDebug -PuseMock=true      # giao diện demo, không cần Backend
```

Emulator gọi Gateway qua `http://10.0.2.2:6200/` (mặc định). Điện thoại thật: thêm `-PapiBaseUrl=http://<IP máy tính>:6200/`.

### 3.5. Dữ liệu mẫu

| Vai trò | Email | Mật khẩu | Dùng ở |
|---|---|---|---|
| Khách hàng | `dev@deliveryapp.local` | `Dev123!` | App Khách hàng |
| Tài xế | `driver@deliveryapp.local` | `Driver123!` | App Tài xế |
| Quản trị | `admin@deliveryapp.local` | `Admin123!` | Web Admin |

Khách hàng tự đăng ký trong app. Tài khoản Tài xế/Quản trị chỉ Admin tạo được (Web Admin → **Tạo tài khoản**).

| Mã ưu đãi | Nội dung | Điều kiện |
|---|---|---|
| `FREESHIP` | Giảm 10.000đ | Đơn từ 30.000đ, tổng 1.000 lượt, 3 lượt/khách |
| `GIAM10` | Giảm 10%, tối đa 20.000đ | 1 lượt/khách |

---

## 4. Luồng nghiệp vụ chính

### 4.1. Một đơn hàng từ đầu đến cuối

```text
[Khách] Chọn điểm A/B trên bản đồ, nhập SĐT người nhận, chọn mã, nhập điểm
   │  POST /api/order/preview  (xem phí trước)
   ▼
[Order] Tính khoảng cách (Map) → báo giá (Delivery) → GIỮ mã (Promotion) + GIỮ điểm (Loyalty)
   │  Lưu đơn PENDING + outbox "assign"
   ▼
[Delivery] Lấy tài xế AVAILABLE có GPS ≤ 60 giây, bán kính 3 km → xáo thứ tự → mời từng người (30 giây/lời mời)
   │  Hết ứng viên 3 km → mở rộng >3–5 km → vẫn không có → NO_DRIVER_FOUND (hoàn trả mã/điểm)
   ▼
[Tài xế] Nhận lời mời (người đầu tiên thắng) → ASSIGNED, tài xế BUSY
   │  Đến điểm lấy (GPS ≤ 200 m) → PICKED_UP → COMMIT mã/điểm
   ▼
   Bắt đầu giao → DELIVERING → Xác nhận đã giao → COMPLETED
   │  Loyalty cộng điểm, tài xế về AVAILABLE
   ▼
[Khách/Tài xế] Trong 24 giờ có thể gửi khiếu nại → Admin xử lý thủ công
```

### 4.2. Trạng thái đơn hàng

```text
PENDING ──► ASSIGNED ──► PICKED_UP ──► DELIVERING ──► COMPLETED
   │            │
   │            └──► CANCELLED   (khách hủy khi PENDING/ASSIGNED)
   ├──► CANCELLED
   └──► NO_DRIVER_FOUND
```

`COMPLETED`, `CANCELLED`, `NO_DRIVER_FOUND` là trạng thái kết thúc (`OrderStateMachine`). Phía Delivery dùng `SEARCHING` thay cho `PENDING`.

### 4.3. Giá, điểm thưởng, hạng

| Quy tắc | Giá trị |
|---|---|
| Phí cơ bản | `khoảng cách (km) × 10.000đ` |
| Phụ phí | 20% phí cơ bản |
| Phí nền tảng | 3.000đ |
| Dùng điểm | 1 điểm = 1.000đ, không vượt quá số điểm khả dụng và giá trị đơn |
| Tích điểm | 1 điểm / 10.000đ khi đơn hoàn tất |
| Hạng | BRONZE < 100 ≤ SILVER < 500 ≤ GOLD (giảm 5%/10% chỉ để hiển thị, chưa trừ vào đơn) |
| Lượt giữ mã/điểm | TTL 120 phút; tạo đơn giới hạn 90 giây từ lúc giữ |

Giá luôn do server tính, client chỉ hiển thị.

### 4.4. Khiếu nại

- Chỉ cho đơn `COMPLETED`, trong **24 giờ** kể từ `CompletedAt`.
- Mỗi đơn tối đa 1 khiếu nại từ khách và 1 từ tài xế.
- Loại: `LATE_DELIVERY`, `WRONG_ITEM`, `DAMAGED_ITEM`, `DRIVER_BEHAVIOR`, `OTHER`; mô tả tối đa 1.000 ký tự; 0–3 ảnh JPEG/PNG, mỗi ảnh ≤ 5 MB.
- Trạng thái: `OPEN → IN_REVIEW → RESOLVED | REJECTED` (bắt buộc ghi chú khi từ chối). Hệ thống **không** tự đền bù tiền/điểm.
- Ảnh lưu **tạm** trên volume Docker; worker xóa ảnh của khiếu nại đã đóng quá 30 ngày.

---

## 5. Backend: các microservice

| Service | Cổng | Database | Trách nhiệm chính |
|---|---|---|---|
| ApiGateway | 6200 | — | Reverse proxy YARP, chỉ public route cho client; CORS cho `localhost:5173`; `/health/ready` kiểm tra mọi service |
| DeliveryService | 6201 | `DeliveryApp_Delivery` | Báo giá; ghép tài xế (`DeliveryMatchingWorker`); lời mời 30 giây; pickup/start/complete; vị trí tài xế; lịch sử chuyến; outbox |
| DriverService | 6202 | `DeliveryApp_Driver` | Bật/tắt nhận đơn, cập nhật GPS, tìm tài xế gần, đánh dấu busy/available |
| IdentityService | 6203 | `DeliveryApp_Identity` | Đăng ký (luôn CUSTOMER), đăng nhập, refresh/logout, admin tạo DRIVER/ADMIN, seed tài khoản mẫu |
| LoyaltyService | 6204 | `DeliveryApp_Loyalty` | Tài khoản điểm, preview, giữ/commit/hoàn trả, cộng điểm khi hoàn tất |
| MapService | 6205 | — | Tính khoảng cách đường chim bay (Haversine) |
| OrderService | 6206 | `DeliveryApp_Order` | Tạo/xem/hủy đơn, preview phí, máy trạng thái, hoàn trả khi tạo đơn lỗi giữa chừng, outbox |
| PromotionService | 6207 | `DeliveryApp_Promotion` | Danh sách mã, kiểm tra, giữ/commit/hoàn trả, giới hạn tổng và theo khách |
| ComplaintService | 6208 | `DeliveryApp_Complaint` | Gửi/xem khiếu nại, ảnh đính kèm, admin đổi trạng thái, dọn ảnh định kỳ |
| SQL Server 2022 | 1433 | — | Chạy trong Docker, volume `deliveryapp_sql_data` |

Mỗi service theo cùng cấu trúc: `Controllers/`, `Services/`, `Entities/`, `Contracts/` (DTO), `Data/` (DbContext + Factory), `Migrations/`, `Common/` (ApiException, middleware lỗi), `Security/` (xác thực `X-Internal-Key`).

Cấu hình ghép tài xế (trong `docker-compose.yml`): `Matching__RadiusStepsKm = [3, 5]`, `Matching__OfferTimeoutSeconds = 30`, `Matching__MaxLocationAgeSeconds = 60`.

---

## 6. API public qua Gateway

| Nhóm | Endpoint | Quyền |
|---|---|---|
| Identity | `POST /api/identity/register`, `/login`, `/refresh`, `/logout`; `GET /api/identity/me` | Công khai / đã đăng nhập |
| | `POST /api/identity/admin/users` | ADMIN |
| Driver | `PATCH /api/driver/me/availability`, `PUT /api/driver/me/location` | DRIVER |
| Order | `POST /api/order/preview`, `POST /api/order`, `POST /api/order/{id}/cancel` | CUSTOMER |
| | `GET /api/order?page=&pageSize=&status=`, `GET /api/order/{id}` | CUSTOMER, DRIVER, ADMIN |
| Promotion | `GET /api/promotion` | Công khai |
| Loyalty | `GET /api/loyalty/me`, `POST /api/loyalty/preview` | CUSTOMER |
| Delivery | `GET /api/delivery/offers/me`, `GET /api/delivery/history/me`, `POST /api/delivery/offers/{id}/accept|reject`, `POST /api/delivery/{id}/pickup|start|complete` | DRIVER |
| | `GET /api/delivery/order/{orderId}`, `GET .../matching-status`, `GET /api/delivery/{id}/location` | Chủ đơn / ADMIN |
| Complaint | `POST /api/complaint`, `GET /api/complaint/me`, `GET /api/complaint/{id}`, `GET /api/complaint/{id}/attachments/{aid}` | CUSTOMER, DRIVER (chủ khiếu nại), ADMIN |
| | `GET /api/complaint/admin`, `PATCH /api/complaint/admin/{id}/status` | ADMIN |
| Health | `GET /health/live`, `GET /health/ready`, `GET /health/{identity|driver|delivery|order|complaint}/ready` | Công khai |

Lỗi luôn trả JSON thống nhất:

```json
{ "code": "ORDER_NOT_FOUND", "message": "Không tìm thấy đơn hàng.", "details": null }
```

Một số mã lỗi đáng chú ý: `PICKUP_GPS_REQUIRED` (400), `PICKUP_TOO_FAR` (409), `DRIVER_LOCATION_STALE` (404), `COMPLAINT_WINDOW_CLOSED` (409), `COMPLAINT_ALREADY_EXISTS` (409).

Danh sách đầy đủ, API nội bộ và ví dụ gọi bằng PowerShell: xem `BE-được-tách-ra-từ-cái-trước/README.md`. Postman: import `postman/DeliveryApp-Group2.postman_collection.json`.

---

## 7. Web Quản trị (admin)

- Đăng nhập chỉ cho vai trò ADMIN; phiên lưu trong `sessionStorage`, tự refresh access token.
- Trình duyệt gọi `/api` và `/health` trên cổng 5173, Vite chuyển tiếp sang Gateway (`VITE_PROXY_TARGET`, mặc định `http://localhost:6200`), nên không vướng CORS khi chạy local.
- Không dùng thư viện router hay UI ngoài: điều hướng theo hash (`#/orders/<id>`), component tự viết trong `ui.tsx`.

| Màn hình | Chức năng |
|---|---|
| Tổng quan | Số đơn theo trạng thái, doanh thu (tổng phí đơn hoàn tất), 5 đơn mới nhất |
| Đơn hàng / Chi tiết | Lọc, phân trang; lộ trình, chi phí, mốc thời gian, tiến trình tìm tài xế, vị trí tài xế |
| Khiếu nại / Chi tiết | Lọc theo trạng thái, xem ảnh, đổi trạng thái kèm ghi chú |
| Tạo tài khoản | Tạo tài khoản Tài xế hoặc Quản trị viên |
| Tài khoản | Hồ sơ, đăng xuất, kiểm tra tình trạng Gateway và từng service |

Lệnh: `npm run dev`, `npm run build` (`tsc -b && vite build`), `npm run preview` (cổng 4173).

---

## 8. App Khách hàng (customer)

- Android API 26+, target SDK 36, MVVM + Jetpack Compose, Navigation Compose, Retrofit 3 + OkHttp, DataStore, Coroutines, MapLibre 11.
- Màn hình: Đăng nhập/Đăng ký, Trang chủ, Tạo đơn, Danh sách đơn, Theo dõi đơn, Điểm thưởng, Tài khoản, Khiếu nại của tôi, Chi tiết khiếu nại.
- **Tạo đơn**: chọn điểm trên bản đồ hoặc "Dùng vị trí hiện tại"; địa chỉ tự gợi ý bằng OpenStreetMap Nominatim; phí xem trước tự động; bấm lại sau lỗi mạng dùng cùng `clientRequestId` nên không tạo trùng.
- **Theo dõi đơn**: làm mới mỗi 4 giây (trạng thái, tiến trình tìm tài xế, vị trí tài xế trên bản đồ); hủy khi còn `PENDING`/`ASSIGNED`; "Đặt lại" khi bị hủy hoặc không có tài xế.
- **Danh sách đơn**: tự làm mới mỗi 10 giây.
- **Phiên đăng nhập**: `AuthInterceptor` gắn Bearer token, `TokenRefreshAuthenticator` tự refresh khi gặp 401; token không được Android Backup sao lưu; HTTP thường chỉ cho phép ở bản debug.
- Bản đồ nền OpenFreeMap (không cần API key).

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
# APK: app\build\outputs\apk\debug\app-debug.apk
```

---

## 9. App Tài xế (shipper)

- Cùng nền tảng với app Khách hàng; module `:shipper`.
- Mặc định gọi Backend thật (`LiveShipperRepository`); build `-PuseMock=true` để dùng `MockShipperRepository` (demo giao diện, có menu thử tình huống: lời mời mới, danh sách trống, mất mạng, khách hủy, offer đã xử lý).
- Bật nhận đơn → xin quyền vị trí → gửi GPS mỗi 15 giây, kiểm tra lời mời mỗi 3 giây.
- Lời mời có đếm ngược 30 giây tính theo `expiresAt`; chấp nhận/từ chối, lấy hàng (gửi GPS mới ≤ 10 giây, phải cách điểm lấy ≤ 200 m), bắt đầu giao, hoàn thành.
- Không cho tắt nhận đơn hay đăng xuất khi đang có chuyến. Không có đăng ký tài khoản tài xế.
- Lịch sử: 100 chuyến hoàn thành/đã hủy gần nhất từ Backend. Báo cáo sự cố trong 24 giờ từ màn chi tiết chuyến.
- Token lưu mã hóa AES/GCM bằng Android Keystore; cỡ chữ lưu bằng DataStore.
- Nút "Mở Google Maps" để chỉ đường ngoài ứng dụng.

```powershell
.\gradlew.bat :shipper:assembleDebug :shipper:testDebugUnitTest :shipper:lintDebug
.\gradlew.bat :shipper:connectedDebugAndroidTest      # cần Emulator/điện thoại
# APK: shipper\build\outputs\apk\debug\shipper-debug.apk
```

Ảnh chụp màn hình: `shipper/shipper/docs/screenshots/`.

---

## 10. Bảo mật

- **JWT HS256**, issuer `deliveryapp-identity`, claim `sub`, `email`, `role`, `exp`. Access token 60 phút, refresh token 7 ngày (lưu dạng hash, xoay vòng khi refresh).
- Mật khẩu băm bằng `PasswordHasher` của ASP.NET Core Identity.
- 3 vai trò: `CUSTOMER`, `DRIVER`, `ADMIN`. Đăng ký công khai luôn là `CUSTOMER`.
- API nội bộ yêu cầu `X-Internal-Key` và không được Gateway route (gọi qua 6200 trả `404`).
- Số điện thoại người nhận không lộ trong lời mời trước khi tài xế nhận đơn, không có trong lịch sử tài xế.
- Ảnh khiếu nại: kiểm tra Content-Type và magic bytes, tên file do server sinh (GUID), tải qua API có xác thực.
- `.env.example` chỉ chứa giá trị phát triển. Khi deploy thật phải đổi toàn bộ mật khẩu/khóa, không commit `.env`, đặt HTTPS ở reverse proxy trước Gateway.

---

## 11. Kiểm thử và CI

| Phần | Lệnh | Nội dung |
|---|---|---|
| Backend unit test | `dotnet test DeliveryApp.sln` | 70 test xUnit: máy trạng thái, outbox retry, hoàn trả khi tạo đơn lỗi, matching, pickup, báo giá, giới hạn mã, commit điểm, khiếu nại |
| Backend tích hợp | `./scripts/Test-CustomerIntegration.ps1`, `./scripts/Test-ComplaintIntegration.ps1` | Cần PowerShell 7 + Docker đang chạy; tạo dữ liệu thử riêng, không chạy trên production |
| Web Admin | `npm run build` | Kiểm tra kiểu TypeScript + build |
| App Khách hàng | `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug` | 42 test: AuthViewModel, TokenManager, CreateOrder, OrderTracking, Complaint |
| App Tài xế | `.\gradlew.bat :shipper:testDebugUnitTest :shipper:connectedDebugAndroidTest` | 54 test: API contract, Live/Mock repository, ViewModel, polling, khôi phục trạng thái, luồng Compose |

Mỗi thành phần có GitHub Actions riêng trong `.github/workflows/` (`ci.yml`, `admin-ci.yml`, `android-ci.yml`).

---

## 12. Demo trên điện thoại thật

1. Máy tính chạy Backend; máy tính và hai điện thoại dùng **chung một Wi-Fi** (Wi-Fi trường/công ty thường chặn, khi đó dùng hotspot điện thoại).
2. Lấy IPv4 của máy tính bằng `ipconfig` (mục Wi-Fi), ví dụ `192.168.1.10`.
3. Mở cổng 6200 (PowerShell Administrator, làm một lần):
   ```powershell
   New-NetFirewallRule -DisplayName "DeliveryApp Gateway" -Direction Inbound -Protocol TCP -LocalPort 6200 -Action Allow
   ```
4. Mở `http://192.168.1.10:6200/health/live` trên trình duyệt điện thoại, phải thấy `Healthy`.
5. Build và cài app:
   ```powershell
   .\gradlew.bat :app:installDebug -PapiBaseUrl=http://192.168.1.10:6200/        # trong customer\
   .\gradlew.bat :shipper:installDebug -PapiBaseUrl=http://192.168.1.10:6200/    # trong shipper\
   ```
6. Khi tạo đơn, chọn điểm A bằng **Dùng vị trí hiện tại** để tài xế đứng đúng điểm lấy hàng.

Trên Emulator, đặt vị trí bằng `adb emu geo fix <kinh độ> <vĩ độ>`, ví dụ `adb emu geo fix 106.6996 10.7755`. Nên dùng **hai Emulator** riêng cho hai app, vì app Tài xế ngừng gửi GPS khi bị đưa xuống nền.

---

## 13. Lỗi thường gặp

| Hiện tượng | Nguyên nhân / cách xử lý |
|---|---|
| `docker compose` không kết nối được Docker | Chưa mở Docker Desktop |
| Lỗi `non-printable ASCII` khi build Docker | Đường dẫn có dấu tiếng Việt → sao chép Backend sang thư mục không dấu |
| Gateway `/health/ready` trả 503 | SQL Server khởi động chậm lần đầu, đợi thêm; xem `docker compose logs <service>` |
| App báo "Không thể kết nối máy chủ" | Backend chưa chạy; điện thoại thật sai IP, khác Wi-Fi hoặc chưa mở tường lửa |
| "Không tìm thấy tài xế" | App Tài xế chưa bật nhận đơn, đang chạy nền, chưa có GPS hoặc ở xa > 5 km. Mở lại app Tài xế, đợi ~15 giây rồi **Đặt lại** |
| "Bạn đang ở xa điểm lấy hàng" | Tài xế cách điểm lấy > 200 m → `adb emu geo fix` tới điểm lấy |
| Mã ưu đãi "đang được giữ cho một đơn khác" | Đúng thiết kế: mã giới hạn 1 lần/khách đang giữ cho đơn chưa xong |
| Outbox có tác vụ `PARKED:` | Lỗi nghiệp vụ không tự hết; xử lý nguyên nhân rồi đặt lại `NextAttemptAt` (xem README Backend) |
| Web Admin không đăng nhập được | Dùng tài khoản ADMIN; Gateway phải chạy ở `VITE_PROXY_TARGET` |

---

## 14. Giới hạn hiện tại

- Khoảng cách tính theo đường chim bay (Haversine), không theo đường đi thực tế; chưa có ETA.
- App Tài xế chỉ gửi GPS khi đang mở trên màn hình (chưa có dịch vụ chạy nền), và chưa khôi phục được chuyến đang giao nếu app bị đóng hẳn.
- Theo dõi đơn dùng polling, chưa có push notification hay WebSocket/SignalR.
- Giảm giá theo hạng thành viên chỉ hiển thị, chưa trừ vào đơn.
- Chưa có thanh toán trực tuyến.
- Ảnh khiếu nại lưu tạm trên đĩa cục bộ, không sao lưu.
- Web Admin chưa có: danh sách/duyệt tài xế, danh sách khách hàng, quản lý khuyến mãi, báo cáo doanh thu theo kỳ, khóa tài khoản hay hủy đơn (Backend chưa có API).

---

## 15. Tài liệu chi tiết

| Tài liệu | Nội dung |
|---|---|
| `BE-được-tách-ra-từ-cái-trước/README.md` | Backend: chạy local, API đầy đủ, API nội bộ, outbox, khiếu nại, cập nhật từ repo của Kha |
| `admin/README.md` | Web Admin: chức năng, API dùng, CORS |
| `customer/README.md` | App Khách hàng: môi trường, phân công, quy trình Git, tiêu chí nghiệm thu |
| `shipper/README.md`, `shipper/shipper/README.md` | App Tài xế: chạy thử, bản đồ, mock |
| `shipper/shipper/docs/` | `KIEN_TRUC.md`, `API.md`, `KIEM_THU.md`, `RA_SOAT.md`, `CHUAN_HOA_NATIVE.md` |

### Ghi chú về các README con

Các README con đã được cập nhật cho khớp mã nguồn ngày 26/11/2026: branch Backend, số test, thư mục `feature/` của app Khách hàng, chế độ Live/Mock và cấu trúc source của app Tài xế.
