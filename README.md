# DeliveryApp — Ứng dụng Khách hàng (Customer)

Nhánh `customer` chứa ứng dụng Android dành cho **Khách hàng** (`:app`, `com.deliveryapp.customer`). Ứng dụng gửi request qua API Gateway của Backend (`http://localhost:6200`).

Các ứng dụng khác nằm ở nhánh riêng: `shipper` (app Tài xế), `admin` (web Quản trị). Backend nằm ở nhánh `main`.

Lệnh thường dùng:

```powershell
.\gradlew.bat :app:installDebug                                   # cài app Khách hàng
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug           # test + lint + build
.\gradlew.bat :app:installDebug -PapiBaseUrl=http://192.168.1.10:6200/   # điện thoại thật
```


## Trạng thái hiện tại

Đã hoàn thành phần nền tảng dùng chung:

- Project Android API 26 trở lên, Kotlin và Jetpack Compose.
- Điều hướng cho 9 màn hình theo luồng Customer Figma v3.
- Retrofit, OkHttp và các API interface cho Identity, Order, Delivery, Loyalty.
- DTO request và response khớp với Backend hiện tại.
- `AppContainer` cung cấp các API dùng chung cho toàn ứng dụng.
- DataStore lưu access token và refresh token.
- `AuthInterceptor` tự gắn `Authorization: Bearer <accessToken>`.
- Token không được Android Backup hoặc device transfer sao lưu.
- HTTP chỉ được cho phép ở bản debug để kết nối Backend local.
- Có GitHub Actions kiểm tra unit test, lint và debug build.

Đã nối Backend thật: đăng nhập/đăng ký, trang chủ, hồ sơ, điểm thưởng, và luồng đơn hàng:

- **Tạo đơn** (`CreateOrderScreen`): chọn điểm lấy/giao bằng bản đồ (kéo bản đồ dưới ghim, hoặc "Dùng vị trí hiện tại"), địa chỉ tự gợi ý từ OpenStreetMap Nominatim khi ô còn trống, số điện thoại người nhận, chọn/nhập mã ưu đãi (`GET /api/promotion`), dùng điểm (`availablePoints`). Phí được xem trước tự động (`POST /api/order/preview` + `POST /api/loyalty/preview`). Bấm lại sau lỗi mạng dùng cùng `clientRequestId` nên không tạo trùng đơn.
- **Theo dõi đơn** (`OrderTrackingScreen`): trạng thái, tiến trình tìm tài xế (bán kính, số tài xế đã mời), bản đồ A/B và vị trí tài xế (cập nhật mỗi 4 giây khi màn hình đang mở, dừng khi đơn kết thúc), mốc thời gian, chi phí; hủy đơn khi còn `PENDING`/`ASSIGNED`; "Đặt lại đơn này" khi đơn bị hủy hoặc không tìm thấy tài xế (mở form đã điền sẵn).
- **Danh sách đơn** (`OrdersScreen`): tự làm mới mỗi 10 giây, chạm để mở theo dõi.

Bản đồ dùng MapLibre + nền OpenFreeMap như app Tài xế. Trên Emulator, đặt vị trí bằng `adb emu geo fix <kinh độ> <vĩ độ>` trước khi bấm "Dùng vị trí hiện tại".

## Yêu cầu môi trường

- Android Studio có JDK 17 đi kèm.
- Android SDK 36.
- Máy ảo Android API 26 trở lên.
- Docker Desktop để chạy Backend DeliveryApp.
- Git.

## Clone và mở dự án

```bash
git clone -b customer https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice.git CustomerApp
cd CustomerApp
```

Mở thư mục vừa clone bằng Android Studio, chọn **Trust Project** nếu được hỏi, sau đó chờ **Gradle Sync** hoàn tất. Không sao chép file `local.properties` từ máy khác; Android Studio sẽ tự tạo file này theo vị trí Android SDK của từng máy.

## Chạy Backend

Mở terminal tại repository Backend DeliveryApp.

Nếu dùng Git Bash:

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
curl http://localhost:6200/health/live
```

Nếu dùng PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
curl http://localhost:6200/health/live
```

Gateway phải trả về trạng thái `Healthy` trước khi chạy ứng dụng Android.

## Kết nối API Gateway

Địa chỉ mặc định cho Android Emulator:

```text
http://10.0.2.2:6200/
```

`10.0.2.2` là địa chỉ để máy ảo Android truy cập `localhost` của máy tính. Cấu hình nằm trong `app/build.gradle.kts` dưới tên `BuildConfig.API_BASE_URL`.

Nếu chạy bằng điện thoại thật, thay `10.0.2.2` bằng IPv4 của máy tính trong cùng mạng Wi-Fi, ví dụ `http://192.168.1.10:6200/`. Firewall phải cho phép cổng 6200.

## Chạy ứng dụng

1. Trong Android Studio, chọn **Tools > Device Manager**.
2. Tạo hoặc mở máy ảo API 26 trở lên.
3. Chọn thiết bị ở thanh công cụ.
4. Bấm **Run app**.

Có thể kiểm tra bằng PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK debug được tạo tại `app/build/outputs/apk/debug/app-debug.apk`.

## Cấu trúc code

```text
com.deliveryapp.customer
├── AppContainer.kt
├── DeliveryCustomerApplication.kt
├── MainActivity.kt
├── navigation
│   ├── AppRoutes.kt
│   └── AppNavHost.kt
├── core
│   ├── network
│   │   ├── ApiResult.kt
│   │   ├── AuthInterceptor.kt
│   │   └── NetworkFactory.kt
│   └── storage
│       └── TokenManager.kt
├── data
│   ├── api
│   └── dto
├── feature
│   ├── auth
│   ├── home
│   ├── order
│   ├── tracking
│   ├── rewards
│   └── profile
└── ui
    ├── components
    └── theme
```

Không tạo Retrofit riêng trong từng màn hình. ViewModel hoặc Repository lấy API từ `DeliveryCustomerApplication.container` để cùng dùng một cấu hình Gateway và Bearer token.

## Phân công hai phần

### Phần 1 Tài khoản và trải nghiệm khách hàng

Branch: `feature/customer-account`

Phạm vi chính:

- `feature/auth`
- `feature/home`
- `feature/profile`
- `feature/rewards`
- `core/storage`
- `IdentityApi.kt` và `LoyaltyApi.kt`

Đầu việc:

- Đăng ký, đăng nhập, refresh token và đăng xuất.
- Trang chủ, hồ sơ người dùng và điểm thưởng.
- Validation, loading, lỗi mạng và session expired.
- Unit test cho AuthViewModel và TokenManager.

### Phần 2 Đơn hàng và theo dõi giao hàng

Branch: `feature/customer-order`

Phạm vi chính:

- `feature/order`
- `feature/tracking`
- `OrderApi.kt` và `DeliveryApi.kt`

Đầu việc:

- Tạo đơn, danh sách đơn, chi tiết và hủy đơn.
- Polling trạng thái tìm tài xế và vị trí tài xế.
- Tích hợp bản đồ sau khi có Google Maps API key.
- Unit test cho OrderViewModel và logic polling.

## Quy trình Git

Mỗi phần tạo branch từ cùng phiên bản `main`:

```bash
git switch main
git pull
git switch -c feature/customer-account
```

Phần 2 dùng tên branch `feature/customer-order`. Không làm trực tiếp trên `main`. Trước khi mở Pull Request phải chạy:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Không commit `local.properties`, thư mục build, file APK, access token, mật khẩu hoặc API key.

## Tiêu chí nghiệm thu chung

- Project clone về Gradle Sync và build được mà không cần sao chép file máy cá nhân.
- Đăng ký, đăng nhập, refresh token và đăng xuất hoạt động qua Gateway.
- Tạo đơn, xem danh sách, xem chi tiết và hủy đơn hoạt động.
- Trạng thái tìm tài xế và vị trí tài xế được cập nhật đúng.
- Điểm thưởng hiển thị và dùng được khi tạo đơn.
- Giao diện bám Figma Customer v3.
- Có trạng thái loading, empty, validation, lỗi mạng và session expired.
- Không chứa secret trong Git.
