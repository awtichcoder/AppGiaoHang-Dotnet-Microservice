# DeliveryApp — Backend .NET 9 Microservices + App Khách hàng, App Tài xế, Web Admin

Hệ thống giao hàng gồm:

| Thư mục | Thành phần | Công nghệ |
|---|---|---|
| `ApiGateway/`, `*Service/` | Backend: API Gateway (YARP) + 7 microservice + SQL Server | .NET 9, EF Core, Docker |
| `MobileApp/app` | App **Khách hàng**: đặt đơn, theo dõi đơn, điểm thưởng | Android, Kotlin, Jetpack Compose |
| `MobileApp/shipper` | App **Tài xế**: nhận lời mời, lấy hàng, giao hàng | Android, Kotlin, Jetpack Compose |
| `AdminWeb/` | Web **Quản trị**: tổng quan, đơn hàng, tạo tài khoản tài xế | React 19, Vite, TypeScript |

Mọi client chỉ gọi **API Gateway** `http://localhost:6200`.

> Branch `Khoa-Deptrai` = backend của Kha (`kha-admin`) + các bản sửa lỗi logic + FE đã nối backend thật. Xem mục [Cập nhật BE theo repo của Kha](#cập-nhật-be-theo-repo-của-kha).

## Mục lục

1. [Chạy local từ đầu](#chạy-local-từ-đầu)
2. [Luồng cơ bản để thử](#luồng-cơ-bản-để-thử)
3. [Demo trên hai điện thoại thật](#demo-trên-hai-điện-thoại-thật)
4. [Cập nhật BE theo repo của Kha](#cập-nhật-be-theo-repo-của-kha)
5. [Lỗi thường gặp](#lỗi-thường-gặp)
6. [Tham chiếu Backend](#tham-chiếu-backend) (kiến trúc, API, outbox)

---

## Chạy local từ đầu

### 0. Cài đặt cần có

| Phần | Cần cài |
|---|---|
| Backend | Git, **Docker Desktop** (bật WSL2). Không bắt buộc .NET SDK nếu chỉ chạy bằng Docker |
| Web Admin | **Node.js 20+** (khuyên dùng 24) |
| App Android | **JDK 17** + **Android SDK 36**. Dễ nhất là cài Android Studio (có sẵn cả hai). Không có Android Studio vẫn được: cài [command-line tools](https://developer.android.com/studio#command-line-tools-only), JDK 17 (Temurin/Corretto) và tạo máy ảo bằng `avdmanager` |
| Chạy unit test BE (tùy chọn) | .NET 9 SDK |

### 1. Clone đúng branch

```powershell
git clone -b Khoa-Deptrai https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice.git
cd AppGiaoHang-Dotnet-Microservice
```

### 2. Chạy Backend bằng Docker

```powershell
Copy-Item .env.example .env        # Git Bash: cp .env.example .env
docker compose up --build -d
docker compose ps                   # chờ tất cả "healthy" (lần đầu 5–10 phút)
Invoke-RestMethod http://localhost:6200/health/ready   # status = Ready, đủ 7 dependencies
```

- Lần đầu Docker tải image .NET 9 và SQL Server nên lâu. Database và tài khoản mẫu được tạo tự động (migration chạy khi container khởi động).
- Sau khi khởi động lại máy: mở Docker Desktop rồi chạy lại `docker compose up -d`.
- Xem log: `docker compose logs -f orderservice deliveryservice`. Dừng: `docker compose down`. Xóa sạch dữ liệu: `docker compose down -v`.

Tài khoản tạo sẵn:

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Khách hàng | `dev@deliveryapp.local` | `Dev123!` |
| Tài xế | `driver@deliveryapp.local` | `Driver123!` |
| Quản trị | `admin@deliveryapp.local` | `Admin123!` |

Khách hàng mới tự đăng ký trong app. Tài khoản tài xế chỉ Admin tạo được (Web Admin → **Tạo tài khoản**).

### 3. Chạy Web Admin

```powershell
cd AdminWeb
npm install
npm run dev            # mở http://127.0.0.1:5173
```

Vite chuyển tiếp `/api` và `/health` sang Gateway nên không gặp lỗi CORS. Đổi Gateway khác bằng biến `VITE_PROXY_TARGET` (xem `AdminWeb/.env.example`). Chi tiết: [AdminWeb/README.md](AdminWeb/README.md).

### 4. Chạy hai app Android trên máy ảo (Emulator)

Hai app là hai module của cùng project Gradle trong `MobileApp/`. Mặc định chúng gọi `http://10.0.2.2:6200/` — địa chỉ máy ảo dùng để gọi `localhost` của máy tính, nên **không cần sửa gì** khi chạy trên Emulator.

**Cách 1 – Android Studio:** File → Open → chọn thư mục `MobileApp` → chờ Gradle Sync → chọn cấu hình `app` (Khách hàng) hoặc `shipper` (Tài xế) → Run. Không copy `local.properties` từ máy khác; Android Studio tự tạo.

**Cách 2 – dòng lệnh** (máy ảo đang chạy, `JAVA_HOME` trỏ tới JDK 17, `ANDROID_HOME` trỏ tới Android SDK hoặc có file `MobileApp/local.properties` chứa `sdk.dir=...`):

```powershell
cd MobileApp
.\gradlew.bat :app:installDebug          # app Khách hàng
.\gradlew.bat :shipper:installDebug      # app Tài xế
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug   # test + lint + build cả hai
```

Đặt vị trí GPS cho máy ảo (kinh độ trước, vĩ độ sau), ví dụ Quận 1:

```powershell
adb emu geo fix 106.7019 10.7769
```

Chi tiết từng app: [MobileApp/README.md](MobileApp/README.md), [MobileApp/shipper/README.md](MobileApp/shipper/README.md).

### 5. (Tùy chọn) Unit test Backend

```powershell
dotnet test DeliveryApp.sln
```

---

## Luồng cơ bản để thử

```text
Khách tạo đơn ─► Order giữ mã/điểm ─► Delivery tìm tài xế (3 km → 5 km, GPS ≤ 60 giây)
   ─► Tài xế nhận lời mời (30 giây) ─► Lấy hàng (GPS ≤ 200 m điểm lấy) ─► Giao ─► Hoàn tất
   ─► Order trừ mã/điểm đã giữ, Loyalty cộng điểm thưởng
```

1. **App Tài xế**: đăng nhập `driver@deliveryapp.local` → bật công tắc **nhận đơn**. App gửi GPS mỗi 15 giây và kiểm tra lời mời mỗi 3 giây. **Giữ app luôn mở** (xem lưu ý bên dưới).
2. **App Khách hàng**: đăng nhập `dev@deliveryapp.local` → **Tạo đơn giao hàng**:
   - Điểm A/B: bấm **Chọn trên bản đồ**, kéo bản đồ cho ghim vào vị trí (hoặc **Dùng vị trí hiện tại**) → **Xác nhận**. Ô địa chỉ tự gợi ý nếu đang trống.
   - Nhập số điện thoại người nhận, chọn mã ưu đãi, nhập số điểm muốn dùng. Phí được tính trước tự động.
   - **Đặt đơn** → chuyển sang màn **Theo dõi đơn hàng**.
3. **App Tài xế**: lời mời hiện ra → **Nhận đơn** → đến điểm lấy → **Đến Nhận hàng** → **Bắt đầu giao hàng** → **Xác nhận đã giao**.
4. **App Khách hàng**: màn theo dõi tự cập nhật (mỗi 4 giây): trạng thái, vị trí tài xế trên bản đồ, tiến trình. Có **Hủy đơn** khi chưa lấy hàng và **Đặt lại** khi không tìm thấy tài xế.
5. **Web Admin**: xem đơn, tiến trình tìm tài xế, vị trí tài xế.

Lưu ý quan trọng:

- Tài xế chỉ được ghép khi vị trí gửi lên **trong 60 giây gần nhất** và cách điểm lấy **≤ 5 km**. App Tài xế chỉ gửi GPS khi đang mở trên màn hình (chưa có dịch vụ chạy nền). Nếu khách thấy "Không tìm thấy tài xế", mở lại app Tài xế, đợi ~15 giây rồi **Đặt lại**.
- Lấy hàng bị từ chối nếu tài xế ở xa điểm lấy quá **200 m**. Trên Emulator: `adb emu geo fix <kinh độ điểm lấy> <vĩ độ điểm lấy>` trước khi bấm.
- Không đóng hẳn app Tài xế giữa chuyến: app chưa khôi phục được chuyến đang chạy, tài xế sẽ kẹt trạng thái bận.
- Chạy cả hai app trên **một** Emulator thì app còn lại bị đưa xuống nền và ngừng gửi GPS → nên dùng hai máy ảo hoặc hai điện thoại.

---

## Demo trên hai điện thoại thật

1. Máy tính chạy Backend (mục 2). Hai điện thoại và máy tính dùng **chung một Wi-Fi**. Wi-Fi trường/công ty thường chặn thiết bị gọi nhau → khi đó bật hotspot trên một điện thoại và cho máy tính + điện thoại còn lại vào hotspot.
2. Lấy IPv4 của máy tính: `ipconfig` → mục **Wi-Fi** (bỏ qua các card ảo VirtualBox/VMware/WSL), ví dụ `192.168.1.10`.
3. Mở cổng 6200 (PowerShell chạy **Administrator**, làm một lần):

   ```powershell
   New-NetFirewallRule -DisplayName "DeliveryApp Gateway" -Direction Inbound -Protocol TCP -LocalPort 6200 -Action Allow
   ```

4. Kiểm tra từ trình duyệt điện thoại: `http://192.168.1.10:6200/health/live` phải hiện `Healthy`.
5. Build APK trỏ vào IP đó và cài lên điện thoại (gửi file qua Zalo/Drive hoặc `adb install`):

   ```powershell
   cd MobileApp
   .\gradlew.bat :app:assembleDebug :shipper:assembleDebug -PapiBaseUrl=http://192.168.1.10:6200/
   # app/build/outputs/apk/debug/app-debug.apk         → điện thoại Khách hàng
   # shipper/build/outputs/apk/debug/shipper-debug.apk → điện thoại Tài xế
   ```

6. Để hai điện thoại cạnh nhau; khi tạo đơn chọn điểm A bằng **Dùng vị trí hiện tại** để tài xế đang đứng đúng điểm lấy hàng. Bật GPS, cho phép quyền vị trí. Nếu IP máy tính đổi, build lại bước 5.

---

## Cập nhật BE theo repo của Kha

Backend của branch này lấy từ repo của Kha: **<https://github.com/HoangKha-06/AppGiaoHang-Dotnet-Microservice>** (branch `kha-admin`), rồi sửa thêm các lỗi logic tìm được khi kiểm thử với app thật.

### Bản của Kha bổ sung gì so với `main`

- `POST /api/order/preview`: xem phí và kiểm tra mã trước khi đặt; `GET /api/promotion`: danh sách mã đang hiệu lực.
- `GET /api/loyalty/me` có `availablePoints`, `tierDiscountPercent`; hạng SILVER từ 100, GOLD từ 500 điểm.
- Delivery trả tọa độ điểm lấy/giao (phục vụ bản đồ theo dõi).
- **Lấy hàng bắt buộc gửi GPS** trong 200 m quanh điểm lấy (`400 PICKUP_GPS_REQUIRED`, `409 PICKUP_TOO_FAR`).
- Tạo đơn lỗi giữa chừng tự hoàn trả mã/điểm đã giữ (`ReleaseUncreatedOrder`), lỗi 401/403 trả JSON `{code, message, details}`, Gateway `/health/ready` kiểm tra đủ 7 service, CORS cho Web Admin, thư mục `AdminWeb`.

### Branch này sửa thêm (lỗi logic phát hiện khi cập nhật)

| Lỗi | Cách sửa |
|---|---|
| Một mã giới hạn 1 lần/khách vẫn dùng được cho nhiều đơn cùng lúc (chỉ đếm lượt đã commit). Đơn thứ hai được giảm giá rồi kẹt vì commit thất bại mãi | Khi giữ mã, đếm cả **các lượt đang giữ còn hạn** vào giới hạn tổng và giới hạn mỗi khách (`PromotionManager`) |
| Lấy hàng sau 120 phút kể từ lúc đặt: commit mã/điểm báo hết hạn và retry mãi | Lượt giữ đã hết hạn nhưng chưa hoàn trả **vẫn commit được** nếu còn lượt / đủ điểm; điểm không được lấn sang phần đang giữ cho đơn khác (`PromotionManager`, `LoyaltyManager`) |
| Outbox của Order/Delivery retry vô hạn cả với lỗi nghiệp vụ không bao giờ tự hết | `OutboxRetryPolicy`: lỗi 400/404/409/422 lặp 5 lần thì dừng (`PARKED:`), lỗi mạng/5xx vẫn retry. Xem [Outbox](#outbox-retry-và-tác-vụ-bị-dừng) |
| App Tài xế gửi pickup rỗng → không lấy hàng được với BE mới | App gửi GPS mới (≤ 10 giây) khi lấy hàng, báo rõ khi ở xa |

Đi kèm: thêm project `PromotionService.Tests`, test cho Loyalty và outbox (51 test BE), app Khách hàng có màn **Tạo đơn**, **Theo dõi đơn**, **Danh sách đơn** dùng API thật.

### Cập nhật máy đang chạy bản cũ

Không cần xóa dữ liệu: bản mới **không đổi cấu trúc database**.

```powershell
git fetch origin
git switch Khoa-Deptrai           # hoặc: git switch -c Khoa-Deptrai origin/Khoa-Deptrai
docker compose up --build -d      # bắt buộc --build để build lại image các service
Invoke-RestMethod http://localhost:6200/health/ready
```

Muốn tự lấy bản mới nhất từ repo của Kha để so sánh/merge:

```powershell
git remote add kha https://github.com/HoangKha-06/AppGiaoHang-Dotnet-Microservice.git
git fetch kha
git log --oneline origin/main..kha/kha-admin     # các commit Kha có mà main chưa có
git merge kha/kha-admin                          # trên branch của bạn, rồi build lại Docker
```

Sau khi cập nhật BE: cài lại app Tài xế bản mới (bản cũ không gửi GPS khi lấy hàng).

---

## Lỗi thường gặp

| Hiện tượng | Nguyên nhân / cách xử lý |
|---|---|
| `docker compose` báo không kết nối được Docker | Chưa mở Docker Desktop (mở xong đợi biểu tượng báo running) |
| Container không `healthy`, Gateway `/health/ready` trả 503 | Đợi thêm (SQL Server khởi động chậm lần đầu); xem `docker compose logs <service>` |
| App báo "Không thể kết nối máy chủ" | Backend chưa chạy; điện thoại thật: sai IP, khác Wi-Fi, chưa mở tường lửa cổng 6200 |
| "Không tìm thấy tài xế" | App Tài xế chưa bật nhận đơn / đang chạy nền / chưa có GPS, hoặc tài xế cách điểm lấy > 5 km |
| "Bạn đang ở xa điểm lấy hàng" | Tài xế cách điểm lấy > 200 m. Emulator: `adb emu geo fix` tới điểm lấy |
| Mã ưu đãi báo "đang được giữ cho một đơn khác" | Đúng thiết kế: mã giới hạn 1 lần/khách đang dùng cho đơn chưa xong của bạn |
| Unit test Android lỗi `ClassNotFoundException` cho mọi class test | Đường dẫn project có dấu tiếng Việt (ví dụ `D:\test cô tú\...`). Clone repo vào thư mục không dấu như `D:\projects\deliveryapp` |
| Gradle báo không tìm thấy SDK | Đặt `ANDROID_HOME` hoặc tạo `MobileApp/local.properties` với `sdk.dir=C\:\\Users\\<bạn>\\AppData\\Local\\Android\\Sdk` |
| Web Admin lỗi đăng nhập/CORS | Mở qua `npm run dev` (cổng 5173) để dùng proxy, không mở file `dist` trực tiếp |
| Outbox có tác vụ `PARKED:` | Lỗi nghiệp vụ không tự hết; xử lý nguyên nhân rồi chạy lại theo mục [Outbox](#outbox-retry-và-tác-vụ-bị-dừng) |

---

## Tham chiếu Backend

### Cổng dịch vụ

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

> `.env.example` chỉ chứa giá trị phát triển. Khi deploy thật phải thay toàn bộ password/key, không commit file `.env`, đặt HTTPS/TLS ở reverse proxy phía trước Gateway.

### Kiến trúc

```text
Mobile/Web ──JWT──> API Gateway ──> OrderService
                                      │
                                      ├─ tính khoảng cách (MapService)
                                      ├─ DeliveryService /quote
                                      ├─ reserve promotion/points
                                      └─ Outbox ──> DeliveryService /assign

DeliveryService ── DriverService 3 km ──> offer tuần tự 30 giây
                └─ hết ứng viên ───> snapshot vùng >3–5 km
                └─ accept ─────────> đồng bộ trạng thái Order qua Outbox
```

- Mỗi service sở hữu database riêng: `DeliveryApp_Identity`, `DeliveryApp_Driver`, `DeliveryApp_Order`, `DeliveryApp_Delivery`, `DeliveryApp_Promotion`, `DeliveryApp_Loyalty`.
- `clientRequestId` + hash nội dung chống tạo trùng. Cùng ID và cùng payload trả đơn cũ; cùng ID nhưng payload khác trả `409`.
- Giá do server tính: `baseFee = distanceKm × 10.000`, phụ phí `20%`, phí nền tảng `3.000` VNĐ. 1 điểm = 1.000đ; hoàn tất đơn được 1 điểm / 10.000đ.
- Matching lưu snapshot ứng viên theo bán kính `[3, 5]`, xáo thứ tự rồi mời từng người, mỗi lời mời 30 giây.
- Accept offer dùng transaction `Serializable`; chỉ người đầu tiên thành công.
- Trạng thái: `PENDING → ASSIGNED → PICKED_UP → DELIVERING → COMPLETED`; nhánh `CANCELLED` (khách hủy khi `PENDING`/`ASSIGNED`) và `NO_DRIVER_FOUND`.
- Mã/điểm được giữ khi tạo đơn, **commit khi tài xế lấy hàng**, hoàn trả khi hủy hoặc không tìm thấy tài xế.
- Số điện thoại người nhận không xuất hiện trong offer trước khi tài xế accept.

### JWT và phân quyền

JWT HS256, issuer `deliveryapp-identity`, claim `sub`, `email`, `role`, `exp`. Role: `CUSTOMER`, `DRIVER`, `ADMIN`. Đăng ký công khai luôn là `CUSTOMER`; chỉ `ADMIN` tạo `DRIVER`/`ADMIN` qua `/api/identity/admin/users`.

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:6200/api/identity/login `
  -ContentType 'application/json' -Body '{"email":"dev@deliveryapp.local","password":"Dev123!"}'
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
```

Test tài xế bằng API: đăng nhập `driver@deliveryapp.local`, gọi `PATCH /api/driver/me/availability` với `{"availabilityStatus":"AVAILABLE"}` và `PUT /api/driver/me/location` (vị trí mới) thì tài xế mới vào matching. Postman: import `postman/DeliveryApp-Group2.postman_collection.json`.

### API public qua Gateway

- **Identity**: `POST /api/identity/register`, `/login`, `/refresh`, `/logout`; `GET /api/identity/me`; `POST /api/identity/admin/users` (ADMIN)
- **Driver** (DRIVER): `PATCH /api/driver/me/availability`, `PUT /api/driver/me/location`
- **Order**: `POST /api/order/preview`, `POST /api/order`, `GET /api/order?page=&pageSize=&status=`, `GET /api/order/{orderId}`, `POST /api/order/{orderId}/cancel`
- **Promotion**: `GET /api/promotion`
- **Loyalty** (CUSTOMER): `GET /api/loyalty/me`, `POST /api/loyalty/preview`
- **Delivery**: `GET /api/delivery/offers/me`, `POST /api/delivery/offers/{offerId}/accept|reject`, `POST /api/delivery/{deliveryId}/pickup|start|complete`, `GET /api/delivery/order/{orderId}`, `GET /api/delivery/order/{orderId}/matching-status`, `GET /api/delivery/{deliveryId}/location`

Pickup cần body `{"gpsLatitude": ..., "gpsLongitude": ...}`. Vị trí tài xế chỉ trả khi đơn `ASSIGNED`/`PICKED_UP`/`DELIVERING` và GPS ≤ 60 giây (`404 DRIVER_LOCATION_STALE` nếu cũ hơn).

Lỗi luôn theo mẫu:

```json
{ "code": "ORDER_NOT_FOUND", "message": "Không tìm thấy đơn hàng.", "details": null }
```

### API nội bộ (chỉ trong mạng Docker, cần `X-Internal-Key`)

- DeliveryService: `/api/delivery/quote`, `/api/delivery/assign`, `/api/delivery/order/{orderId}/cancel`
- OrderService: `PATCH /api/order/{orderId}/status`
- MapService: `/api/map/route/quote`
- PromotionService: `/api/promotion/validate`, `/reserve`, `/reservations/{id}/commit|release`, `/orders/{orderId}/release`
- DriverService: `/api/driver/nearby`, `/api/driver/{driverId}/location`, `/api/driver/{driverId}/busy|available`
- LoyaltyService: `/api/loyalty/reserve`, `/reservations/{id}/commit|release`, `/orders/{orderId}/release`, `/earn`

Gateway cố ý không route các API này; gọi qua cổng 6200 sẽ nhận `404`.

### Tạo đơn: hoàn trả khi lỗi giữa chừng

Trước khi giữ điểm/mã, Order ghi tác vụ `ReleaseUncreatedOrder`. Nếu tạo đơn lỗi, Order hoàn trả ngay theo `orderId` ở Promotion và Loyalty (kể cả khi response reserve bị mất); tác vụ nền kiểm tra lại sau 5 phút. Tạo đơn giới hạn 90 giây từ lúc bắt đầu reserve; lưu thành công thì kết thúc tác vụ recovery cùng transaction với đơn. Lượt giữ có TTL 120 phút.

### Outbox: retry và tác vụ bị dừng

Order và Delivery dùng outbox để gọi service khác. Lỗi mạng hoặc 5xx được retry mãi (tối đa 60 giây/lần). Lỗi nghiệp vụ (400/404/409/422) lặp lại 5 lần thì tác vụ bị dừng: `LastError` bắt đầu bằng `PARKED:`, `NextAttemptAt = 9999-12-31`, log mức Error. Sau khi xử lý nguyên nhân, cho chạy lại:

```powershell
docker compose exec sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "<SQLSERVER_SA_PASSWORD trong .env>" -C -d DeliveryApp_Order `
  -Q "UPDATE OutboxMessages SET NextAttemptAt = SYSUTCDATETIME(), RetryCount = 0 WHERE Id = '<id>'"
```

### Kiểm thử tích hợp

```powershell
./scripts/Test-CustomerIntegration.ps1     # cần PowerShell 7, Docker đang chạy
```

Script tạo tài khoản/đơn thử riêng, kiểm tra bảo mật, preview, hoàn trả, tọa độ và một chuyến giao hoàn chỉnh. Không chạy trên production.

### Chạy Backend không dùng Docker

Cần .NET 9 SDK và SQL Server. Đặt biến môi trường `JWT_SECRET`, `INTERNAL_API_KEY`, connection string của từng service (xem `docker-compose.yml`), rồi:

```powershell
dotnet build DeliveryApp.sln
dotnet run --project IdentityService   # tương tự cho DriverService, MapService, PromotionService,
                                       # LoyaltyService, DeliveryService, OrderService, ApiGateway
```

### Cấu trúc thư mục

```text
├── ApiGateway/                 YARP, route public, /health/ready
├── IdentityService/            đăng ký, đăng nhập, JWT, tài khoản mẫu
├── DriverService/              trạng thái + GPS tài xế
├── MapService/                 khoảng cách Haversine
├── PromotionService/           mã ưu đãi, giữ/commit/hoàn trả
├── LoyaltyService/             điểm thưởng
├── OrderService/               đơn hàng, giá, outbox
├── DeliveryService/            matching, chuyến giao, outbox
├── *.Tests/                    unit test (Order, Delivery, Loyalty, Promotion)
├── AdminWeb/                   web quản trị (React + Vite)
├── MobileApp/                  project Gradle: app/ (Khách hàng), shipper/ (Tài xế)
├── postman/, scripts/          collection Postman, script kiểm thử
├── docker-compose.yml, Dockerfile, .env.example
└── .github/workflows/ci.yml    CI: test BE, build Android, build Admin web
```
