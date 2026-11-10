# ShipperApp — Kotlin + Jetpack Compose

Ứng dụng Android Native dành cho tài xế DeliveryApp, sử dụng Kotlin + Jetpack Compose, Material 3 và MVVM. Module Gradle là `:shipper` (thư mục `shipper/`).

## Mở trong Android Studio

1. File → Open → chọn thư mục gốc của repo (thư mục chứa `settings.gradle.kts`).
2. Dùng Gradle JDK 17 trở lên (Android Studio bundled JBR 21 đã dùng để kiểm tra).
3. Cài Android SDK Platform 36; chờ Gradle Sync.
4. Chọn cấu hình chạy `shipper`, chọn Android Emulator API 26 trở lên, bấm Run.

`local.properties` chỉ dành cho máy hiện tại và đã gitignore. Android Studio sẽ tạo file này theo SDK của máy khác.

## Chạy thử

Nhấn **Điền tài khoản dùng thử**, sau đó **Đăng nhập**:

- Email: `driver@deliveryapp.local`
- Mật khẩu: `Driver123!`

Đây là tài khoản mô phỏng công khai trong source, không phải secret của backend.

Luồng chính: bật nhận đơn → xem lời mời → chấp nhận → xem chi tiết/bản đồ → xác nhận lấy hàng → bắt đầu giao → hoàn thành → lịch sử. Tài xế trở lại AVAILABLE khi hoàn thành. Không cho tắt nhận đơn/đăng xuất khi đang có chuyến. Không có đăng ký DRIVER.

Biểu tượng ống nghiệm trên Trang chủ hoặc mục **Thử tình huống mô phỏng** trong Tài khoản cho phép thử:

- Lời mời mới.
- Danh sách trống.
- Mất mạng và phục hồi (chọn Kết nối bình thường).
- Khách hủy offer khi tài xế phản hồi.
- Offer đã được xử lý.

Để thử hết hạn, chờ 30 giây tại màn lời mời. Countdown tính từ expiresAt; đổi màn hoặc đưa app ra nền không đặt lại thời hạn.

## Bản đồ và vị trí

- MapLibre hiển thị nền OpenFreeMap và ba ghim: điểm lấy, điểm giao, vị trí tài xế.
- Ban đầu tọa độ là mô phỏng, nhãn trên bản đồ ghi rõ.
- **Lấy vị trí thiết bị** xin quyền Android và đọc một vị trí mới/cached tối đa 60 giây, có thời gian và sai số; không theo dõi nền, không gửi tọa độ lên backend.
- Emulator: Extended Controls → Location → đặt vị trí tại TP.HCM, rồi nhấn lấy vị trí.
- **Mở Google Maps** mở chỉ đường ngoài ứng dụng theo địa chỉ của bước hiện tại.
- Nền bản đồ cần Internet; khi tải lỗi/chậm có thông báo và nút tải lại. Không cần API key.
- Không tự gọi endpoint bản đồ nội bộ hay tự tạo ETA/đường đi giả.

## Source code

```text
src/main/java/com/deliveryapp/shipper/
  MainActivity.kt, ShipperApplication.kt, AppContainer.kt
  core/
    location/DeviceLocationReader.kt
    network/NetworkFactory.kt
    storage/TokenManager.kt, SettingsStore.kt
  data/
    model/Models.kt
    api/ShipperApi.kt
    repository/ShipperRepository.kt, MockShipperRepository.kt
  feature/
    auth/         # AuthViewModel, Splash/Login
    work/         # WorkViewModel: StateFlow, khóa thao tác, countdown, events
    home/         # availability, offer, active trip
    offer/        # 30 giây, accept/reject và trạng thái kết thúc
    delivery/     # detail, MapLibre, GPS, transition, completion
    history/      # lọc trạng thái, chi tiết chỉ đọc
    profile/      # hồ sơ, DataStore cỡ chữ, đăng xuất
  navigation/ShipperNavHost.kt
  ui/theme/       # bộ màu/chữ từ Customer, đổi package
  ui/components/  # card/button/badge/route/state/confirmation
src/test/          # nghiệp vụ, ViewModel, Retrofit contract
src/androidTest/   # kiểm tra luồng Compose trên thiết bị
```

Phân tích nguồn và quyết định kiến trúc: [docs/KIEN_TRUC.md](docs/KIEN_TRUC.md).
Mapping backend và việc cần làm khi tích hợp: [docs/API.md](docs/API.md).
Kết quả kiểm tra thực tế: [docs/KIEM_THU.md](docs/KIEM_THU.md).
Các sửa lỗi đã thực hiện: [docs/RA_SOAT.md](docs/RA_SOAT.md).
Báo cáo chuẩn hóa, danh sách file và điểm khôi phục: [docs/CHUAN_HOA_NATIVE.md](docs/CHUAN_HOA_NATIVE.md).

## Lệnh kiểm tra

PowerShell, đứng tại thư mục gốc của repo:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :shipper:assembleDebug :shipper:testDebugUnitTest :shipper:lintDebug
# Cần mở Emulator hoặc kết nối điện thoại trước:
.\gradlew.bat :shipper:connectedDebugAndroidTest
```

APK debug: `shipper/build/outputs/apk/debug/shipper-debug.apk`.

## Giới hạn có chủ đích

- AppContainer hiện inject MockShipperRepository. Retrofit API và JWT storage là phần chuẩn bị; không có công tắc giả cho rằng đã tích hợp backend thật.
- Lịch sử là dữ liệu mô phỏng; chưa xác minh được API lịch sử riêng cho DRIVER.
- Phiên/chuyến dùng thử giữ trong ViewModel/repository khi xoay màn hình. Nếu tiến trình bị đóng, app trở về đăng nhập; không giả vờ khôi phục chuyến thật.
- Cài đặt cỡ chữ được lưu bằng DataStore. Token live được chuẩn bị lưu mã hóa AES/GCM bằng Keystore; chế độ mock không lưu JWT.
- Phí giao hàng không đồng nghĩa tiền thực nhận. Chỉ hiển thị incomeEstimate khi offer có dữ liệu.
- Không sửa Customer App/Backend. Project chỉ chứa Android Native; mã cũ đã được đưa ra ngoài workspace và lưu trong bản backup được mô tả tại báo cáo chuẩn hóa.
