# DeliveryApp - .NET 9 Microservices

Backend ung dung giao hang bang .NET 9, gom API Gateway, cac microservice va SQL Server. Repo nay hien co luong chinh cho Order, Delivery, Map, Promotion, Loyalty va Gateway chay duoc bang Docker.

## Trang thai hien tai

- `ApiGateway` dung YARP lam cong vao thong nhat.
- `OrderService` tao don, tinh phi, reserve promotion, reserve diem, dong bo delivery bang outbox.
- `DeliveryService` quan ly matching/tai xe nhan don/cac buoc giao hang.
- `LoyaltyService` luu diem, lich su diem, reserve/commit/release va earn diem.
- `PromotionService` ho tro validate/reserve/commit/release ma giam gia.
- `MapService` tinh khoang cach theo Haversine.
- `IdentityService` va `DriverService` hien moi co scaffold/health; API nghiep vu se do nhom phu trach tiep tuc bo sung.

## Yeu cau may chay

Can co:

- Git
- Docker Desktop dang `Engine running`
- .NET 9 SDK neu muon build/chay ngoai Docker
- O C nen con it nhat 10-15 GB trong cho lan build Docker dau tien

Lan dau `docker compose up --build` co the lau vi Docker phai tai image .NET, SQL Server va NuGet package.

## Chay nhanh bang Docker

```powershell
git clone https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice.git
cd AppGiaoHang-Dotnet-Microservice
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
```

Neu Docker command khong co trong PATH tren may Windows, mo Docker Desktop truoc roi thu lai terminal moi.

Docker se tu migrate database cho cac service co database khi `APPLY_MIGRATIONS=true`:

- `DeliveryApp_Order`
- `DeliveryApp_Delivery`
- `DeliveryApp_Promotion`
- `DeliveryApp_Loyalty`

Khi tat ca container bao `healthy`, kiem tra Gateway:

```powershell
Invoke-RestMethod http://localhost:6200/health/live
Invoke-RestMethod http://localhost:6200/health/ready
```

Kiem tra tung service qua Gateway:

```powershell
Invoke-RestMethod http://localhost:6200/health/identity/ready
Invoke-RestMethod http://localhost:6200/health/delivery/ready
Invoke-RestMethod http://localhost:6200/health/driver/ready
Invoke-RestMethod http://localhost:6200/health/loyalty/ready
Invoke-RestMethod http://localhost:6200/health/map/ready
Invoke-RestMethod http://localhost:6200/health/order/ready
Invoke-RestMethod http://localhost:6200/health/promotion/ready
```

Xem log:

```powershell
docker compose logs -f apigateway orderservice deliveryservice loyaltyservice
```

Dung container nhung giu database:

```powershell
docker compose down
```

Xoa ca database local de chay lai tu dau:

```powershell
docker compose down -v
```

> Khong commit file `.env`. `.env.example` chi dung cho local dev, khi deploy that phai thay password/key.

## Cong dich vu local

| Thanh phan | URL |
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

Client mobile/web chi nen goi `ApiGateway`. Cac API noi bo dung `X-Internal-Key` chi de service goi nhau bang HTTP noi bo Docker.

## Bien moi truong

Tao `.env` tu `.env.example`:

```env
SQLSERVER_SA_PASSWORD=DeliveryApp_Dev123!
JWT_SECRET=deliveryapp-development-jwt-secret-change-before-production
INTERNAL_API_KEY=deliveryapp-development-internal-key-change-before-production
SQLSERVER_PORT=1433
```

Y nghia don gian:

- `SQLSERVER_SA_PASSWORD`: mat khau SQL Server local.
- `JWT_SECRET`: khoa ky JWT HS256.
- `INTERNAL_API_KEY`: khoa cho API noi bo giua service.
- `SQLSERVER_PORT`: port SQL Server expose ra may host.
## Quy uoc API

- Kieu API: REST.
- Body request/response: JSON.
- Ten field: camelCase.
- ID: GUID string.
- Thoi gian: ISO UTC.
- Tien: so nguyen VND, khong dung float.
- JWT mobile: Bearer token HS256, issuer `deliveryapp-identity`.
- Claims JWT dang dung: `sub`, `email`, `role`, `iss`, `exp`.
- API noi bo: header `X-Internal-Key`.

Mau loi chung:

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "Khong tim thay don hang.",
  "details": null
}
```

## ApiGateway

Gateway route cac base path sau:

- `/api/identity`
- `/api/driver`
- `/api/order`
- `/api/delivery`
- `/api/map`
- `/api/promotion`
- `/api/loyalty`

Gateway lam 4 viec quan trong:

- Chuyen tiep `Authorization` header xuong service phia sau.
- Gan hoac giu lai `X-Correlation-ID` cho moi request.
- Xoa `X-Internal-Key` neu client ben ngoai co tinh gui qua Gateway.
- Chan API noi bo, khong proxy qua service phia sau.

Cac API noi bo bi chan qua Gateway:

- `POST /api/driver/nearby`
- `POST /api/map/route/quote`
- `POST /api/delivery/quote`
- `POST /api/delivery/assign`
- `POST /api/delivery/order/{orderId}/cancel`
- `POST /api/delivery/{id}/cancel`
- `PATCH /api/order/{orderId}/status`
- `POST /api/promotion/validate`
- `POST /api/promotion/reserve`
- `POST /api/promotion/reservations/{id}/commit`
- `POST /api/promotion/reservations/{id}/release`
- `POST /api/loyalty/reserve`
- `POST /api/loyalty/reservations/{id}/commit`
- `POST /api/loyalty/reservations/{id}/release`
- `POST /api/loyalty/earn`

Vi du test internal API bi chan:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:6200/api/loyalty/earn `
  -Headers @{ 'X-Internal-Key' = 'anything' } `
  -ContentType 'application/json' `
  -Body '{"customerId":"00000000-0000-0000-0000-000000000001","orderId":"00000000-0000-0000-0000-000000000002","orderAmount":100000}'
```

Ket qua dung la `404` voi code `INTERNAL_ENDPOINT_NOT_EXPOSED`.

## JWT dev local

IdentityService hien chua phat token that, nen khi test local co the tao JWT dev bang script:

```powershell
$customerId = [Guid]::NewGuid()
$token = .\scripts\New-DevJwt.ps1 -Role CUSTOMER -SubjectId $customerId
$headers = @{ Authorization = "Bearer $token" }
```

Tao token driver:

```powershell
$driverId = [Guid]::NewGuid()
$driverToken = .\scripts\New-DevJwt.ps1 -Role DRIVER -SubjectId $driverId
$driverHeaders = @{ Authorization = "Bearer $driverToken" }
```

Them correlation ID khi test:

```powershell
$headers = @{
  Authorization = "Bearer $token"
  'X-Correlation-ID' = 'local-test-001'
}
```

Gateway se tra lai cung `X-Correlation-ID` trong response header.

## LoyaltyService

Public qua Gateway:

- `GET /api/loyalty/me`
- `POST /api/loyalty/preview`

Noi bo, chi service khac goi truc tiep bang `X-Internal-Key`:

- `POST /api/loyalty/reserve`
- `POST /api/loyalty/reservations/{id}/commit`
- `POST /api/loyalty/reservations/{id}/release`
- `POST /api/loyalty/earn`

Quy doi diem MVP:

- Dung diem: `1 point = 1.000 VND`.
- Cong diem: `10.000 VND = 1 point`.
- Reservation diem co TTL 120 phut.
- `SILVER/GOLD` chi la thong tin hang, chua tu dong giam `totalFee`.

Vi du xem diem:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri http://localhost:6200/api/loyalty/me `
  -Headers $headers
```

Vi du preview diem:

```powershell
$body = @{ points = 5 } | ConvertTo-Json
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:6200/api/loyalty/preview `
  -Headers $headers `
  -ContentType 'application/json' `
  -Body $body
```

Seed diem de test local, goi truc tiep LoyaltyService vi day la API noi bo:

```powershell
$internalHeaders = @{ 'X-Internal-Key' = 'deliveryapp-development-internal-key-change-before-production' }
$body = @{
  customerId = $customerId
  orderId = [Guid]::NewGuid()
  orderAmount = 1000000
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:6204/api/loyalty/earn `
  -Headers $internalHeaders `
  -ContentType 'application/json' `
  -Body $body
```
## OrderService va DeliveryService

Order public qua Gateway:

- `POST /api/order`
- `GET /api/order?page=1&pageSize=20`
- `GET /api/order/{orderId}`
- `POST /api/order/{orderId}/cancel`

Delivery public qua Gateway:

- `GET /api/delivery/offers/me`
- `POST /api/delivery/offers/{offerId}/accept`
- `POST /api/delivery/offers/{offerId}/reject`
- `POST /api/delivery/{deliveryId}/pickup`
- `POST /api/delivery/{deliveryId}/start`
- `POST /api/delivery/{deliveryId}/complete`
- `GET /api/delivery/order/{orderId}`
- `GET /api/delivery/order/{orderId}/matching-status`
- `GET /api/delivery/{deliveryId}/location`

Trang thai order chinh:

```text
PENDING -> ASSIGNED -> PICKED_UP -> DELIVERING -> COMPLETED
```

Nhanh hieu:

- Tao don: `OrderService` tinh phi, reserve promotion, reserve diem, tao outbox assign delivery.
- Tai xe lay hang: order sang `PICKED_UP`, outbox commit promotion/diem.
- Huy hoac khong co tai xe: outbox release promotion/diem.
- Hoan thanh: order sang `COMPLETED`, outbox goi Loyalty earn diem theo `TotalFee`.

Vi du tao don:

```powershell
$body = @{
  pickupAddress = '1 Le Loi, Quan 1'
  dropoffAddress = '10 Nguyen Hue, Quan 1'
  pickupLocation = @{ latitude = 10.7769; longitude = 106.7009 }
  dropoffLocation = @{ latitude = 10.7731; longitude = 106.7041 }
  receiverPhone = '0900000000'
  promotionCode = $null
  pointsToUse = 0
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


## Smoke test nghiem thu

Sau khi Docker da chay va cac container bao `healthy`, chay script nay de kiem tra nhanh cac tieu chi nhom 4:

```powershell
.\scripts\Invoke-AcceptanceSmoke.ps1
```

Script se tu tao JWT dev, goi API qua Gateway, goi API noi bo truc tiep bang `X-Internal-Key`, va kiem tra:

- Gateway `/health/ready` thay du 7 service.
- `GET /api/loyalty/me` va `POST /api/loyalty/preview` chay qua Gateway voi JWT.
- Loyalty reserve/release khong lam am diem.
- Tao order co `pointsToUse` se reserve diem.
- `PICKED_UP` se commit diem da reserve.
- `COMPLETED` se earn diem va khong earn trung cung `orderId`.
- Gateway chan cac API noi bo.
## Kiem tra build local

```powershell
dotnet restore DeliveryApp.sln
dotnet build DeliveryApp.sln
```

Neu muon chay ngoai Docker, can tu chuan bi SQL Server va set bien moi truong/connection string tuong ung. Cach de nhat cho dev hien tai van la Docker, vi compose da co SQL Server va tu migrate database.

Vi du set bien moi truong PowerShell cho local run:

```powershell
$env:JWT_SECRET = 'deliveryapp-development-jwt-secret-change-before-production'
$env:INTERNAL_API_KEY = 'deliveryapp-development-internal-key-change-before-production'
$env:ConnectionStrings__OrderDatabase = 'Server=localhost,1433;Database=DeliveryApp_Order;User Id=sa;Password=DeliveryApp_Dev123!;TrustServerCertificate=True;Encrypt=False'
$env:ConnectionStrings__DeliveryDatabase = 'Server=localhost,1433;Database=DeliveryApp_Delivery;User Id=sa;Password=DeliveryApp_Dev123!;TrustServerCertificate=True;Encrypt=False'
$env:ConnectionStrings__PromotionDatabase = 'Server=localhost,1433;Database=DeliveryApp_Promotion;User Id=sa;Password=DeliveryApp_Dev123!;TrustServerCertificate=True;Encrypt=False'
$env:ConnectionStrings__LoyaltyDatabase = 'Server=localhost,1433;Database=DeliveryApp_Loyalty;User Id=sa;Password=DeliveryApp_Dev123!;TrustServerCertificate=True;Encrypt=False'
$env:APPLY_MIGRATIONS = 'true'
```

Chay tung service:

```powershell
dotnet run --project MapService
dotnet run --project PromotionService
dotnet run --project LoyaltyService
dotnet run --project DeliveryService
dotnet run --project OrderService
dotnet run --project ApiGateway
```

## Troubleshooting

Docker Desktop khoi dong lau:

- Dam bao Docker Desktop hien `Engine running`.
- O C nen con it nhat 10-15 GB trong.
- Lan dau build se lau vi tai image va package.
- Neu Docker treo lau, co the thu `wsl --shutdown`, sau do mo lai Docker Desktop.

Port bi trung:

- Sua `SQLSERVER_PORT` trong `.env` neu `1433` da bi dung.
- Cac port service dang mac dinh `6200-6207`.

NuGet/Docker restore bi loi cache:

- Dockerfile da dat cache NuGet trong `/tmp/nuget` de tranh loi cache read-only trong build stage.
- Neu da xoa cache NuGet local, lan build tiep theo se tai lai package nen cham hon.

Database loi hoac muon chay sach:

```powershell
docker compose down -v
docker compose up --build -d
```

## Cau truc repo

```text
DeliveryApp/
|-- ApiGateway/
|-- DeliveryService/
|-- DeliveryService.Tests/
|-- DriverService/
|-- IdentityService/
|-- LoyaltyService/
|-- MapService/
|-- OrderService/
|-- OrderService.Tests/
|-- PromotionService/
|-- scripts/New-DevJwt.ps1
|-- Dockerfile
|-- docker-compose.yml
|-- .env.example
`-- README.md
```