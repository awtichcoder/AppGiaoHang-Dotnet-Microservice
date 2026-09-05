# DeliveryApp - .NET 9 Microservices

Backend ứng dụng giao hàng gồm API Gateway, 7 microservice và SQL Server. Tất cả service đã được nối thành một luồng chạy chung bằng Docker; Order giữ/hoàn/trừ điểm qua Loyalty thật và Delivery cộng điểm đúng một lần khi hoàn tất đơn.

## Chạy nhanh bằng Docker

Yêu cầu: Git và Docker Desktop.

```powershell
git clone https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice.git
cd AppGiaoHang-Dotnet-Microservice
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

Nếu dùng Git Bash, thay lệnh `Copy-Item` bằng:

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

Lần đầu Docker cần tải image .NET 9, SQL Server và NuGet package nên sẽ lâu hơn. Khi tất cả container báo `healthy`, kiểm tra:

```powershell
Invoke-RestMethod http://localhost:6200/health/live
Invoke-RestMethod http://localhost:6200/health/order/ready
Invoke-RestMethod http://localhost:6200/health/delivery/ready
Invoke-RestMethod http://localhost:6200/health/identity/ready
Invoke-RestMethod http://localhost:6200/health/driver/ready
Invoke-RestMethod http://localhost:6204/health/ready
```

Trong VS Code có thể nhấn `Ctrl+Shift+P` → **Tasks: Run Task** → **DeliveryApp: Docker up**. Workspace cũng gợi ý cài C# Dev Kit và Docker extension.

Để test Postman, import `postman/DeliveryApp-Group2.postman_collection.json` và chạy request theo thứ tự 1 → 13. Sau bước tạo đơn, đợi khoảng 2–3 giây trước khi lấy offer vì matching chạy nền.

Xem log hoặc dừng hệ thống:

```powershell
docker compose logs -f orderservice deliveryservice
docker compose down
```

Muốn xóa cả dữ liệu SQL local để chạy mới hoàn toàn:

```powershell
docker compose down -v
```

> `.env.example` chỉ chứa giá trị phát triển. Khi deploy thật phải thay toàn bộ password/key, không commit file `.env`, đặt HTTPS/TLS ở reverse proxy phía trước Gateway.

## Cổng dịch vụ

| Thành phần | URL local |
|---|---|
| API Gateway | `http://localhost:6200` |
| DeliveryService | `http://localhost:6201` |
| DriverService | `http://localhost:6202` |
| IdentityService | `http://localhost:6203` |
| LoyaltyService | `http://localhost:6204` |
| MapService | `http://localhost:6205` |
| OrderService | `http://localhost:6206` |
| PromotionService | `http://localhost:6207` |
| SQL Server | `localhost:1433` |

Client chỉ nên gọi Gateway. Các endpoint nội bộ dùng `X-Internal-Key` không được public qua Gateway.

## Kiến trúc Nhóm 2

```text
Mobile/Web ──JWT──> API Gateway ──> OrderService
                                      │
                                      ├─ tính khoảng cách
                                      ├─ DeliveryService /quote
                                      ├─ reserve promotion/points
                                      └─ Outbox ──> DeliveryService /assign

DeliveryService ── DriverService 3 km ──> offer tuần tự 30 giây
                └─ hết ứng viên ───> snapshot vùng >3–5 km
                └─ accept ─────────> đồng bộ trạng thái Order qua Outbox
```

- Mỗi service dữ liệu sở hữu database riêng: `DeliveryApp_Identity`, `DeliveryApp_Driver`, `DeliveryApp_Order`, `DeliveryApp_Delivery`, `DeliveryApp_Promotion` và `DeliveryApp_Loyalty`.
- `clientRequestId` + hash nội dung chống tạo trùng. Cùng ID và cùng payload trả đơn cũ; cùng ID nhưng payload khác trả `409`.
- Giá do server tính: `baseFee = distanceKm × 10.000`, phụ phí `20%`, phí nền tảng `3.000` VNĐ.
- Matching lưu snapshot ứng viên theo bán kính `[3, 5]`, xáo thứ tự rồi mời từng người. Không đặt số lần thử cố định.
- Accept offer dùng transaction `Serializable`; chỉ người đầu tiên thành công.
- Trạng thái: `PENDING → ASSIGNED → PICKED_UP → DELIVERING → COMPLETED`; có nhánh `CANCELLED` và `NO_DRIVER_FOUND`.
- Outbox retry với exponential backoff giúp không mất đồng bộ khi service đích tạm lỗi.
- Số điện thoại người nhận không xuất hiện trong offer trước khi tài xế accept.

`OrderService` gọi **MapService**, **PromotionService**, **LoyaltyService** và **DeliveryService** thật qua HTTP nội bộ. `DeliveryService` gọi **DriverService** thật để tìm tài xế GPS còn mới trong bán kính 3/5 km, dùng outbox để chuyển tài xế `BUSY/AVAILABLE`, và gọi Loyalty cộng điểm idempotent theo `orderId`. Map dùng công thức Haversine; Promotion và Loyalty lưu reservation vào database riêng, TTL 120 giây, hỗ trợ reserve/commit/release.

## JWT và phân quyền

JWT dùng HS256, issuer `deliveryapp-identity`, claim `sub`, `email`, `role`, `exp`. Role hợp lệ: `CUSTOMER`, `DRIVER`, `ADMIN`.

Docker tự tạo ba tài khoản phát triển (có thể đổi trong `.env`):

```text
admin@deliveryapp.local  / Admin123!
driver@deliveryapp.local / Driver123!
dev@deliveryapp.local    / Dev123!
```

Đăng ký khách hàng bằng `POST /api/identity/register`, sau đó đăng nhập bằng `POST /api/identity/login`. Response trả `accessToken` và `refreshToken`; dán `accessToken` vào Bearer Token của Postman. Tài khoản đăng ký công khai luôn có role `CUSTOMER`; chỉ `ADMIN` được tạo `DRIVER` hoặc `ADMIN` qua `/api/identity/admin/users`.

Ví dụ đăng nhập khách hàng trong PowerShell:

```powershell
$customerLogin = Invoke-RestMethod -Method Post -Uri http://localhost:6200/api/identity/login `
  -ContentType 'application/json' `
  -Body '{"email":"dev@deliveryapp.local","password":"Dev123!"}'
$customerHeaders = @{ Authorization = "Bearer $($customerLogin.accessToken)" }
```

Khi test tài xế, đăng nhập `driver@deliveryapp.local`, rồi gọi `PATCH /api/driver/me/availability` với `{"availabilityStatus":"AVAILABLE"}` và cập nhật vị trí mới qua `PUT /api/driver/me/location` thì tài xế mới xuất hiện trong matching.

## API Nhóm 2

Order (public qua Gateway):

- `POST /api/order`
- `GET /api/order?page=1&pageSize=20&status=PENDING`
- `GET /api/order/{orderId}`
- `POST /api/order/{orderId}/cancel`

Delivery (public qua Gateway):

- `GET /api/delivery/offers/me?page=1&pageSize=20`
- `POST /api/delivery/offers/{offerId}/accept`
- `POST /api/delivery/offers/{offerId}/reject`
- `POST /api/delivery/{deliveryId}/pickup`
- `POST /api/delivery/{deliveryId}/start`
- `POST /api/delivery/{deliveryId}/complete`
- `GET /api/delivery/order/{orderId}`
- `GET /api/delivery/order/{orderId}/matching-status`
- `GET /api/delivery/{deliveryId}/location`

Identity và Driver (public qua Gateway):

- `POST /api/identity/register`, `/login`, `/refresh`, `/logout`
- `GET /api/identity/me`
- `POST /api/identity/admin/users` (ADMIN)
- `PATCH /api/driver/me/availability` (DRIVER)
- `PUT /api/driver/me/location` (DRIVER)

Loyalty (public qua Gateway):

- `GET /api/loyalty/me` (CUSTOMER)
- `POST /api/loyalty/preview` (CUSTOMER)

Nội bộ giữa service:

- `POST DeliveryService /api/delivery/quote`
- `POST DeliveryService /api/delivery/assign`
- `POST DeliveryService /api/delivery/order/{orderId}/cancel`
- `PATCH OrderService /api/order/{orderId}/status`
- `POST MapService /api/map/route/quote`
- `POST PromotionService /api/promotion/validate`
- `POST PromotionService /api/promotion/reserve`
- `POST PromotionService /api/promotion/reservations/{id}/commit`
- `POST PromotionService /api/promotion/reservations/{id}/release`
- `GET DriverService /api/driver/nearby`
- `GET DriverService /api/driver/{driverId}/location`
- `POST DriverService /api/driver/{driverId}/busy|available`
- `POST LoyaltyService /api/loyalty/reserve`
- `POST LoyaltyService /api/loyalty/reservations/{id}/commit|release`
- `POST LoyaltyService /api/loyalty/earn`

Gateway cố ý không route các API nội bộ nêu trên. Gọi chúng qua cổng `6200` sẽ nhận `404`; chỉ service trong mạng Docker được dùng `X-Internal-Key`.

Lỗi luôn theo mẫu:

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "Không tìm thấy đơn hàng.",
  "details": null
}
```

## Ví dụ tạo đơn

```powershell
$body = @{
  pickupAddress = '1 Lê Lợi, Quận 1'
  dropoffAddress = '10 Nguyễn Huệ, Quận 1'
  pickupLocation = @{ latitude = 10.7769; longitude = 106.7009 }
  dropoffLocation = @{ latitude = 10.7731; longitude = 106.7041 }
  receiverPhone = '0900000000'
  promotionCode = 'FREESHIP'
  pointsToUse = 20
  clientRequestId = [Guid]::NewGuid()
} | ConvertTo-Json -Depth 4

$order = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:6200/api/order `
  -Headers $customerHeaders `
  -ContentType 'application/json' `
  -Body $body
$order
```

## Chạy không dùng Docker

Yêu cầu .NET 9 SDK và SQL Server. Đặt biến môi trường `JWT_SECRET`, `INTERNAL_API_KEY`, hai connection string rồi chạy:

```powershell
dotnet restore DeliveryApp.sln
dotnet build DeliveryApp.sln
dotnet test DeliveryApp.sln --no-build
dotnet run --project IdentityService
dotnet run --project DriverService
dotnet run --project MapService
dotnet run --project PromotionService
dotnet run --project LoyaltyService
dotnet run --project DeliveryService
dotnet run --project OrderService
dotnet run --project ApiGateway
```

EF Core migration nằm trong từng service và Docker tự áp dụng khi `APPLY_MIGRATIONS=true`.

## Cấu trúc

```text
DeliveryApp/
├── ApiGateway/
├── DeliveryService/
├── DeliveryService.Tests/
├── DriverService/
├── IdentityService/
├── LoyaltyService/
├── LoyaltyService.Tests/
├── MapService/
├── OrderService/
├── OrderService.Tests/
├── PromotionService/
├── scripts/New-DevJwt.ps1
├── Dockerfile
├── docker-compose.yml
└── .env.example
```
