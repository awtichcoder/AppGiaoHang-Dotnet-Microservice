# DeliveryApp - .NET 9 Microservices

Backend ứng dụng giao hàng gồm API Gateway, 7 microservice và SQL Server. Phần **Nhóm 2** (`OrderService` + `DeliveryService`) đã có luồng đặt đơn, báo giá, matching tài xế, offer, giao hàng, idempotency, JWT/RBAC, outbox retry và migration.

## Chạy nhanh bằng Docker

Yêu cầu: Git và Docker Desktop.

```powershell
git clone https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice.git
cd AppGiaoHang-Dotnet-Microservice
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

Lần đầu Docker cần tải image .NET 9, SQL Server và NuGet package nên sẽ lâu hơn. Khi tất cả container báo `healthy`, kiểm tra:

```powershell
Invoke-RestMethod http://localhost:6200/health/live
Invoke-RestMethod http://localhost:6200/health/order/ready
Invoke-RestMethod http://localhost:6200/health/delivery/ready
```

Trong VS Code có thể nhấn `Ctrl+Shift+P` → **Tasks: Run Task** → **DeliveryApp: Docker up**. Workspace cũng gợi ý cài C# Dev Kit và Docker extension.

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

DeliveryService ── snapshot 3 km ──> offer tuần tự 30 giây
                └─ hết ứng viên ───> snapshot vùng >3–5 km
                └─ accept ─────────> đồng bộ trạng thái Order qua Outbox
```

- Mỗi service sở hữu database riêng: `DeliveryApp_Order`, `DeliveryApp_Delivery` và `DeliveryApp_Promotion`.
- `clientRequestId` + hash nội dung chống tạo trùng. Cùng ID và cùng payload trả đơn cũ; cùng ID nhưng payload khác trả `409`.
- Giá do server tính: `baseFee = distanceKm × 10.000`, phụ phí `20%`, phí nền tảng `3.000` VNĐ.
- Matching lưu snapshot ứng viên theo bán kính `[3, 5]`, xáo thứ tự rồi mời từng người. Không đặt số lần thử cố định.
- Accept offer dùng transaction `Serializable`; chỉ người đầu tiên thành công.
- Trạng thái: `PENDING → ASSIGNED → PICKED_UP → DELIVERING → COMPLETED`; có nhánh `CANCELLED` và `NO_DRIVER_FOUND`.
- Outbox retry với exponential backoff giúp không mất đồng bộ khi service đích tạm lỗi.
- Số điện thoại người nhận không xuất hiện trong offer trước khi tài xế accept.

`OrderService` gọi **MapService**, **PromotionService** và **DeliveryService** thật qua HTTP nội bộ. Map dùng công thức Haversine; Promotion lưu reservation vào database riêng và hỗ trợ reserve/commit/release. Loyalty và danh sách tài xế hiện vẫn dùng adapter mock cho đến khi nhóm phụ trách cung cấp API tương ứng.

## JWT và phân quyền

JWT dùng HS256, issuer `deliveryapp-identity`, claim `sub`, `email`, `role`, `exp`. Role hợp lệ: `CUSTOMER`, `DRIVER`, `ADMIN`.

IdentityService hiện thuộc nhóm khác và chưa phát token. Để kiểm thử local, tạo token phát triển bằng script kèm theo:

```powershell
$customerId = [Guid]::NewGuid()
$token = .\scripts\New-DevJwt.ps1 -Role CUSTOMER -SubjectId $customerId
$headers = @{ Authorization = "Bearer $token" }
```

Tài xế mock mặc định có các ID sau; tạo JWT `DRIVER` bằng một trong các ID này:

```text
10000000-0000-0000-0000-000000000001
10000000-0000-0000-0000-000000000002
10000000-0000-0000-0000-000000000003
```

## API Nhóm 2

Order (public qua Gateway):

- `POST /api/order`
- `GET /api/order?page=1&pageSize=20`
- `GET /api/order/{orderId}`
- `POST /api/order/{orderId}/cancel`

Delivery (public qua Gateway):

- `GET /api/delivery/offers/me`
- `POST /api/delivery/offers/{offerId}/accept`
- `POST /api/delivery/offers/{offerId}/reject`
- `POST /api/delivery/{deliveryId}/pickup`
- `POST /api/delivery/{deliveryId}/start`
- `POST /api/delivery/{deliveryId}/complete`
- `GET /api/delivery/order/{orderId}`
- `GET /api/delivery/order/{orderId}/matching-status`
- `GET /api/delivery/{deliveryId}/location`

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
  promotionCode = 'WELCOME'
  pointsToUse = 1000
  clientRequestId = [Guid]::NewGuid()
} | ConvertTo-Json -Depth 4

$order = Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:6200/api/order `
  -Headers $headers `
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
dotnet run --project MapService
dotnet run --project PromotionService
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
├── MapService/
├── OrderService/
├── OrderService.Tests/
├── PromotionService/
├── scripts/New-DevJwt.ps1
├── Dockerfile
├── docker-compose.yml
└── .env.example
```
