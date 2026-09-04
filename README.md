# DeliveryApp - .NET Microservices

Backend cho ứng dụng giao hàng, xây dựng bằng ASP.NET Core Web API theo kiến trúc microservices.

## Công nghệ

- .NET 9
- ASP.NET Core Web API
- YARP Reverse Proxy 2.3.0
- JWT Bearer Authentication (HS256, sẽ cấu hình ở bước tiếp theo)
- Docker/Docker Compose (sẽ bổ sung sau)

## Cấu trúc solution

```text
DeliveryApp
├── ApiGateway
├── DeliveryService
├── DriverService
├── IdentityService
├── LoyaltyService
├── MapService
├── OrderService
├── PromotionService
└── DeliveryApp.sln
```

Trong Visual Studio, các project được sắp xếp bằng Solution Folder:

```text
DeliveryApp
├── BuildingBlocks
├── Gateway
│   └── ApiGateway
└── Services
    ├── DeliveryService
    ├── DriverService
    ├── IdentityService
    ├── LoyaltyService
    ├── MapService
    ├── OrderService
    └── PromotionService
```

## Danh sách cổng local

| Project | Giao thức | Cổng | Vai trò |
|---|---|---:|---|
| ApiGateway | HTTPS | 6200 | Điểm truy cập của client |
| DeliveryService | HTTP | 6201 | Quản lý giao hàng và phân công tài xế |
| DriverService | HTTP | 6202 | Quản lý tài xế và vị trí |
| IdentityService | HTTP | 6203 | Tài khoản, đăng nhập và JWT |
| LoyaltyService | HTTP | 6204 | Điểm và hạng thành viên |
| MapService | HTTP | 6205 | Khoảng cách và thời gian dự kiến |
| OrderService | HTTP | 6206 | Tạo và quản lý đơn hàng |
| PromotionService | HTTP | 6207 | Mã khuyến mại |

> Không dùng cổng 6000 trên trình duyệt Chrome vì cổng này bị chặn với lỗi `ERR_UNSAFE_PORT`.

## Kiến trúc giao tiếp

Client chỉ gọi API Gateway qua HTTPS. Gateway sử dụng YARP để chuyển request tới các microservice qua HTTP trong môi trường local.

```text
Client
  → HTTPS ApiGateway
  → HTTP Microservice
```

Các microservice không được truy cập trực tiếp database của nhau. Mỗi service sở hữu database riêng và giao tiếp qua API.

## Yêu cầu trên máy lập trình

Cài đặt:

- Git
- Visual Studio 2022
- Workload **ASP.NET and web development**
- .NET 9 SDK
- Docker Desktop (chưa bắt buộc ở giai đoạn hiện tại)

Kiểm tra SDK:

```powershell
dotnet --list-sdks
```

Kết quả phải có phiên bản `9.0.x`.

## Clone và chạy project

### 1. Clone repository

```powershell
git clone https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice.git
cd AppGiaoHang-Dotnet-Microservice
```

### 2. Khôi phục NuGet package

```powershell
dotnet restore DeliveryApp.sln
```

YARP và các package khác được khai báo trong file `.csproj`, vì vậy không cần cài thủ công lại từng package.

### 3. Build toàn bộ solution

```powershell
dotnet build DeliveryApp.sln
```

Build phải hoàn thành mà không có lỗi trước khi chạy.

### 4. Tin cậy chứng chỉ HTTPS local

Mỗi máy Windows chạy một lần:

```powershell
dotnet dev-certs https --trust
```

Chọn **Yes** khi Windows yêu cầu xác nhận.

### 5. Mở solution

Mở file:

```text
DeliveryApp.sln
```

Trong Visual Studio, nhấp chuột phải vào solution và chọn:

```text
Configure Startup Projects
→ Multiple startup projects
```

Thiết lập:

| Project | Action |
|---|---|
| ApiGateway | Start |
| IdentityService | Start |
| Các project còn lại | None |

Sau đó chạy bằng `Ctrl + F5`.

## Database cho PromotionService (SQL Server qua Docker)

PromotionService dùng SQL Server, chạy qua Docker container thay vì cài SQL Server
trực tiếp trên máy, để mọi thành viên có cấu hình giống nhau. File `docker-compose.yml`
ở gốc repo định nghĩa container này (map ra cổng `14330` trên máy host để tránh đụng
SQL Server có sẵn ở cổng 1433 mặc định).

Lần đầu clone repo, mỗi người tự làm các bước sau trên máy mình:

1. Cài Docker Desktop nếu chưa có, mở lên và đợi báo đang chạy.
2. Copy `.env.example` thành `.env`, tự điền một mật khẩu bất kỳ đủ mạnh cho
   `SQLSERVER_SA_PASSWORD` (không cần trùng với người khác, chỉ cần trùng với
   connection string bạn để trong `PromotionService/appsettings.Development.json`
   trên máy mình).
3. Dựng container SQL Server:
   ```powershell
   docker compose up -d sqlserver
   ```
4. Cài công cụ EF Core CLI nếu chưa có (`dotnet tool install --global dotnet-ef`),
   nên dùng bản khớp với version EF Core của project (hiện là 9.x) để tránh cảnh báo
   lệch version.
5. Áp migration để tạo bảng và dữ liệu mẫu (2 mã khuyến mại `FREESHIP`, `GIAM10`
   được seed sẵn trong migration, không cần tự thêm tay):
   ```powershell
   dotnet ef database update --project PromotionService
   ```

Mỗi người có một database vật lý riêng trên máy mình — không ai cần kết nối vào
Docker của người khác. Nếu cổng `1433` mặc định bị SQL Server khác trên máy chiếm
dụng (kiểm tra bằng `netstat -ano | findstr :1433`), đổi số cổng ở `ports:` trong
`docker-compose.yml` và ở connection string cho khớp nhau, không cần đụng tới SQL
Server có sẵn.

## Kiểm tra hệ thống

Kiểm tra trực tiếp Gateway:

```text
https://localhost:6200/health/live
```

Kết quả dự kiến:

```json
{
  "status": "Healthy",
  "service": "ApiGateway"
}
```

Kiểm tra Gateway chuyển tiếp đến IdentityService:

```text
https://localhost:6200/health/identity/ready
```

Kết quả dự kiến:

```json
{
  "status": "Ready",
  "dependencies": []
}
```

Luồng request:

```text
https://localhost:6200/health/identity/ready
→ ApiGateway
→ http://localhost:6203/health/ready
→ IdentityService
```

## Quy ước API

- REST API và dữ liệu JSON.
- Tên trường dùng `camelCase`.
- ID dùng `GUID`, không dùng số nguyên tự tăng khi tích hợp chéo.
- Ngày giờ dùng ISO 8601 UTC.
- Tiền dùng số nguyên, đơn vị VNĐ.
- Phân trang: `?page=1&pageSize=20`.

Định dạng lỗi chuẩn:

```json
{
  "code": "ORDER_NOT_FOUND",
  "message": "Không tìm thấy đơn hàng",
  "details": null
}
```

## Quy ước JWT

Thiết kế đã thống nhất:

- Thuật toán: HS256.
- Access token dự kiến hết hạn sau 60 phút.
- Claim bắt buộc: `sub`, `email`, `role`, `iss`, `exp`.
- Role: `CUSTOMER`, `DRIVER`, `ADMIN`.
- JWT secret phải giống nhau giữa các service xác thực token.
- Không viết JWT secret trực tiếp trong code hoặc commit lên GitHub.

JWT hiện chưa được triển khai hoàn chỉnh. IdentityService sẽ chịu trách nhiệm đăng nhập và phát token; các service còn lại dùng token để xác thực request.

## Biến môi trường và dữ liệu bí mật

Không commit các file sau:

```text
.env
.env.*
appsettings.Local.json
```

Khi repository có file `.env.example`, tạo file local bằng:

```powershell
Copy-Item .env.example .env
```

Sau đó tự điền JWT secret và chuỗi kết nối database. Không gửi secret qua GitHub.

## Cấu trúc cơ bản trong mỗi service

```text
ServiceName
├── Common
├── Contracts
├── Controllers
├── Data
├── Entities
├── Services
└── Properties
```

- `Common`: lỗi chuẩn và thành phần dùng trong service.
- `Contracts`: request/response DTO.
- `Controllers`: REST endpoint.
- `Data`: DbContext hoặc cấu hình truy cập dữ liệu.
- `Entities`: mô hình dữ liệu riêng của service.
- `Services`: xử lý nghiệp vụ.
- `Security`: JWT và mật khẩu, dùng rõ nhất trong IdentityService.

Các folder chưa có code sử dụng `.gitkeep` để GitHub không loại bỏ folder rỗng.

## Git workflow cơ bản

Trước khi bắt đầu code:

```powershell
git pull origin main
git switch -c feature/ten-chuc-nang
```

Sau khi hoàn thành:

```powershell
git status
git add .
git commit -m "Mô tả thay đổi"
git push -u origin feature/ten-chuc-nang
```

Không push trực tiếp lên `main` nếu nhóm sử dụng pull request.

## Việc cần làm tiếp theo

- Chốt database cho từng service: SQL Server, MySQL, MongoDB hoặc không dùng database.
- Xóa WeatherForecast mẫu và thêm health check cho các service còn lại.
- Triển khai đăng ký, đăng nhập và JWT trong IdentityService.
- Cấu hình JWT validation cho các service.
- Bổ sung route và cluster YARP cho toàn bộ service.
- Thêm Dockerfile và `docker-compose.yml`.
- Thêm `.env.example` để các thành viên cấu hình môi trường.

