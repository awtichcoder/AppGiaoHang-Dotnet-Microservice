# MobileApp — App Khách hàng và App Tài xế (Android)

Một project Gradle gồm hai module, cài độc lập trên cùng thiết bị. Cả hai gọi Backend qua API Gateway.

| Module | Ứng dụng | Package | Tài liệu |
|---|---|---|---|
| `:app` | Khách hàng | `com.deliveryapp.customer` | README này |
| `:shipper` | Tài xế | `com.deliveryapp.shipper` | [shipper/README.md](shipper/README.md), [shipper/docs](shipper/docs) |

Cách chạy Backend, Web Admin và luồng thử nghiệm đầy đủ: xem [README ở thư mục gốc](../README.md).

## Yêu cầu

- JDK 17 và Android SDK 36 (Android Studio có sẵn cả hai).
- Máy ảo hoặc điện thoại Android 8.0 (API 26) trở lên.
- Backend đang chạy (`docker compose up -d` ở thư mục gốc repo).

## Chạy

**Android Studio:** File → Open → chọn thư mục `MobileApp` → Trust Project → chờ Gradle Sync → chọn cấu hình `app` hoặc `shipper` → Run. Không copy `local.properties` từ máy khác.

**Dòng lệnh** (trong thư mục `MobileApp`):

```powershell
.\gradlew.bat :app:installDebug          # cài app Khách hàng lên thiết bị đang kết nối
.\gradlew.bat :shipper:installDebug      # cài app Tài xế
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug   # test + lint + build cả hai
```

Nếu Gradle báo không tìm thấy SDK: đặt biến `ANDROID_HOME`, hoặc tạo `local.properties` chứa `sdk.dir=<đường dẫn Android SDK>`.

### Địa chỉ Backend

| Thiết bị | Địa chỉ | Cách build |
|---|---|---|
| Emulator (mặc định) | `http://10.0.2.2:6200/` (= `localhost` của máy tính) | không cần tham số |
| Điện thoại thật | `http://<IPv4 Wi-Fi của máy tính>:6200/` | `-PapiBaseUrl=http://192.168.1.10:6200/` |

```powershell
.\gradlew.bat :app:assembleDebug :shipper:assembleDebug -PapiBaseUrl=http://192.168.1.10:6200/
```

Điện thoại và máy tính phải cùng mạng, tường lửa máy tính mở cổng 6200 (hướng dẫn ở README gốc, mục "Demo trên hai điện thoại thật"). Bản debug cho phép HTTP; bản release thì không.

Tùy chọn riêng app Tài xế: `-PuseMock=true` chạy giao diện với dữ liệu giả, không cần Backend.

### GPS trên Emulator

```powershell
adb emu geo fix 106.7019 10.7769     # kinh độ trước, vĩ độ sau
```

Hoặc Extended Controls → Location.

## App Khách hàng (`:app`)

| Màn hình | Chức năng | API |
|---|---|---|
| Đăng nhập / Đăng ký | Lưu token bằng DataStore, tự refresh, báo hết phiên | `/api/identity/*` |
| Trang chủ, Hồ sơ | Thông tin tài khoản, đăng xuất | `/api/identity/me` |
| Điểm thưởng | Điểm, hạng, lịch sử giao dịch | `GET /api/loyalty/me` |
| **Tạo đơn** (`CreateOrderScreen`) | Chọn điểm A/B trên bản đồ (kéo bản đồ dưới ghim hoặc "Dùng vị trí hiện tại"), địa chỉ tự gợi ý khi ô trống, SĐT người nhận, mã ưu đãi, dùng điểm, phí tính trước tự động. Bấm lại sau lỗi mạng dùng cùng `clientRequestId` nên không tạo trùng đơn | `GET /api/promotion`, `POST /api/order/preview`, `POST /api/loyalty/preview`, `POST /api/order` |
| **Theo dõi đơn** (`OrderTrackingScreen`) | Trạng thái, tiến trình tìm tài xế, bản đồ A/B + vị trí tài xế, mốc thời gian, chi phí. Cập nhật mỗi 4 giây khi đang mở, dừng khi đơn kết thúc. Hủy đơn khi `PENDING`/`ASSIGNED`; "Đặt lại" khi bị hủy hoặc không tìm thấy tài xế | `GET /api/order/{id}`, `GET /api/delivery/order/{id}`, `.../matching-status`, `GET /api/delivery/{deliveryId}/location`, `POST /api/order/{id}/cancel` |
| **Danh sách đơn** (`OrdersScreen`) | Tự làm mới mỗi 10 giây, chạm để mở theo dõi | `GET /api/order` |

- Bản đồ: MapLibre + nền OpenFreeMap (giống app Tài xế), không cần API key.
- Gợi ý địa chỉ: OpenStreetMap Nominatim, một yêu cầu mỗi lần xác nhận điểm; lỗi thì người dùng tự nhập.
- Quyền vị trí chỉ xin khi bấm "Dùng vị trí hiện tại".

### Cấu trúc code

```text
com.deliveryapp.customer
├── AppContainer.kt              Retrofit + repository dùng chung (không tạo Retrofit riêng trong màn hình)
├── core/network                 ApiResult, safeApiCall, AuthInterceptor, TokenRefreshAuthenticator
├── core/storage                 TokenManager (DataStore)
├── core/location                vị trí hiện tại, ReverseGeocoder (Nominatim)
├── data/api, data/dto           Retrofit interface + DTO khớp Backend
├── data/repository              AuthRepository, OrderRepository (OrderGateway), ...
├── feature/auth|home|profile|rewards
├── feature/order                CreateOrder*, OrderTracking*, OrdersScreen, OrderFormat, OrderUi
├── navigation                   AppRoutes, AppNavHost (route "order/{orderId}")
└── ui/components, ui/theme      DeliveryCard, PrimaryAction, StatusBanner, DeliveryMap, màu
```

Unit test: `app/src/test` (AuthViewModel, TokenManager, CreateOrderViewModel, OrderTrackingViewModel với `FakeOrderGateway`).

## App Tài xế (`:shipper`)

- Đăng nhập tài khoản DRIVER (`driver@deliveryapp.local / Driver123!`; tài xế mới do Admin tạo).
- Bật nhận đơn: xin quyền vị trí, gửi GPS mỗi 15 giây, kiểm tra lời mời mỗi 3 giây. Backend chỉ ghép tài xế có GPS trong 60 giây gần nhất, cách điểm lấy 3–5 km.
- Nhận/từ chối lời mời → lấy hàng → bắt đầu giao → hoàn thành đều gọi API thật.
- Lấy hàng gửi GPS mới (≤ 10 giây); Backend chỉ chấp nhận trong 200 m quanh điểm lấy, ở xa app báo "Bạn đang ở xa điểm lấy hàng".

Giới hạn hiện tại:

- GPS chỉ gửi khi app đang mở trên màn hình (chưa có foreground service) → giữ app mở khi chờ đơn và khi giao.
- Chuyến đang giao không được khôi phục nếu app bị đóng; tab Lịch sử chỉ hiện chuyến trong phiên đăng nhập.

## Quy ước

- Không commit `local.properties`, thư mục `build`, file APK, token, mật khẩu hoặc API key.
- Trước khi mở Pull Request: `.\gradlew.bat testDebugUnitTest lintDebug assembleDebug`.
