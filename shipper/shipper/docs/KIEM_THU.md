# Kiểm thử ShipperApp Kotlin

Ngày kiểm tra: 31/10/2026. Project Android Native hiện nằm trực tiếp tại `D:\App_giao_hang\FE-GiaoHang`, module `app`, tên project `ShipperApp`.

## Môi trường

- Windows, Android Studio bundled JBR 21; Gradle wrapper 8.13.
- Kotlin 2.2.21, Android Gradle Plugin 8.13.2, compileSdk/targetSdk 36, minSdk 26.
- Emulator: Medium_Phone_API_36.1, Android 16, x86_64.
- APK debug: [app-debug.apk](../app/build/outputs/apk/debug/app-debug.apk).

## Kết quả thực tế

| Kiểm tra | Kết quả | Bằng chứng |
|---|---|---|
| help + assembleDebug | BUILD SUCCESSFUL tại thư mục gốc sau di chuyển | build/migration/build-and-tests.log, APK trong app/build/outputs/apk/debug |
| testDebugUnitTest | 23 tests, 0 failures, 0 errors | app/build/reports/tests/testDebugUnitTest/index.html |
| lintDebug (build mới tại root) | 0 errors, 14 warnings | build/migration/build-and-tests.log, app/build/reports/lint-results-debug.html |
| Compose UI trên Emulator | 2 tests đạt sau di chuyển; BUILD SUCCESSFUL trong 2m 14s | build/migration/device-tests.log, app/build/reports/androidTests/connected/debug/index.html |
| Màn hình nhỏ, chữ lớn (lần rà soát trước di chuyển) | Luồng đầy đủ: 1 test đạt, 320 dp, font_scale=1.3 | Bản lưu legacy-workspace/ShipperApp/build/review/small-screen-test.log |
| Activity recreation | Giữ chuyến tại màn chi tiết và bản đồ; mở/đóng bản đồ hai lần | completeTripHistorySettingsAndLogout |
| Kiểm tra ảnh | Đã xem ảnh thật từ Emulator | thư mục screenshots |
| MapLibre và GPS (lần xác minh giao diện trước rà soát) | Nền bản đồ đã tải; đọc vị trí Android sau khi cấp quyền | Bản lưu legacy-workspace/ShipperApp/build/review/previous-gps.log, 10-device-location.png |

Bản lưu nằm tại `D:\App_giao_hang\_backups\FE-GiaoHang-native-20261031-c24e4a61`. Các phép kiểm tra màn hình nhỏ, GPS và ảnh thuộc lần kiểm tra trước; không tính là đã chạy lại trong đợt di chuyển. Gradle đã cấu hình và giải quyết dependency qua wrapper; chưa thao tác nút Sync trong Android Studio.

Các đường dẫn build/log là kết quả trên máy này, được gitignore và được tạo lại khi chạy lệnh kiểm tra. Còn 14 cảnh báo, đều là gợi ý cập nhật dependency. Đã xử lý các cảnh báo KTX, đọc Resources qua Compose và backup. Không nâng đồng loạt thư viện chỉ để xóa cảnh báo. Không có lỗi biên dịch hoặc lỗi lint chặn build.

## Phạm vi kiểm tra

23 unit/contract tests gồm:
- 6 bài repository: tài khoản demo và quyền riêng tư người nhận, availability, đúng thứ tự trạng thái, không nhận hai lần, hết hạn khi request đang chạy, từ chối/hủy/đã xử lý, lỗi mạng và dữ liệu trống.
- 3 bài ViewModel: validation/role, trạng thái thao tác và countdown.
- 3 bài Retrofit/DTO contract bằng MockWebServer: đường dẫn, payload và field theo backend.
- 7 bài session/network: login không gắn bearer, refresh đúng một lần, không lặp 401, giữ phiên khi 503/JSON lỗi/thiếu token, xóa phiên khi refresh bị từ chối, logout trong lúc refresh không phục hồi token cũ.
- 4 bài phục hồi trạng thái: tác vụ phiên cũ không nhả khóa phiên mới; hủy GPS và xóa tọa độ khi reset/từ chối quyền; xử lý lỗi ghi cài đặt và thử lại.

Hai bài UI thao tác trên ứng dụng:
1. Đăng nhập demo → bật nhận đơn → mở lời mời → nhận → tái tạo Activity → chi tiết → mở/đóng bản đồ hai lần → tái tạo Activity tại bản đồ → xác nhận lấy hàng → bắt đầu giao → hoàn thành → lịch sử LazyColumn → chi tiết chỉ đọc → hồ sơ → đổi cỡ chữ → đăng xuất.
2. Validation email rỗng, đăng nhập demo, mở và từ chối lời mời; không còn nút chấp nhận.

Bộ test dùng Espresso 3.7 để tương thích Android 16. Test cỡ chữ chờ DataStore/Compose cập nhật trước thao tác tiếp theo.

Chạy bổ sung luồng thứ nhất với capture=true và deviceLocation=true: instrumentation cấp quyền vị trí, app đọc LocationManager và hiển thị tọa độ, thời gian, sai số. Emulator được đặt tại 10.7755, 106.6996 bằng adb emu geo fix. Đây là vị trí do Emulator cung cấp, chưa phải kiểm thử GPS ngoài thực địa; chưa tự động thao tác nút trong hộp thoại xin quyền hệ thống.

## Xem giao diện

Ảnh PNG chụp từ ứng dụng chạy trên Emulator ở lần xác minh giao diện trước đợt rà soát; không phải bản thiết kế dựng sẵn. Đợt rà soát bổ sung test hồi quy và tối ưu cách dựng danh sách, không chụp lại toàn bộ ảnh:

| Màn hình | Ảnh |
|---|---|
| Đăng nhập | [01-login.png](screenshots/01-login.png) |
| Trang chủ nghỉ nhận đơn | [02-home.png](screenshots/02-home.png) |
| Trang chủ có lời mời | [03-home-offer.png](screenshots/03-home-offer.png) |
| Chi tiết lời mời | [04-offer.png](screenshots/04-offer.png) |
| Chi tiết chuyến | [05-detail.png](screenshots/05-detail.png) |
| Bản đồ và tiến trình | [06-map.png](screenshots/06-map.png) |
| Hoàn thành | [07-completed.png](screenshots/07-completed.png) |
| Lịch sử | [08-history.png](screenshots/08-history.png) |
| Hồ sơ | [09-profile.png](screenshots/09-profile.png) |
| Đọc vị trí thiết bị | [10-device-location.png](screenshots/10-device-location.png) |

## Giới hạn của kết quả

- Kiểm thử trên Android 16 Emulator; chưa xác minh trực tiếp mọi API 26–35, tablet hay thiết bị vật lý.
- Đây là frontend với mock repository. Chưa đăng nhập/gửi vị trí/thay đổi trạng thái qua Gateway thật.
- API/DTO tests dùng MockWebServer, không chứng minh backend production đang hoạt động.
- Bản đồ cần mạng; Google Maps là ứng dụng ngoài. Chưa kiểm thử chỉ đường ngoài thực địa.
- Không có phiên/chuyến bền vững sau process death trong chế độ demo; chỉ cài đặt cỡ chữ được lưu.
- Lịch sử mô phỏng được đánh dấu; không khẳng định đã có API lịch sử DRIVER.

## Chạy lại

Từ thư mục gốc `D:\App_giao_hang\FE-GiaoHang`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```

Để giữ ảnh sau kiểm thử, có thể cài APK app và APK androidTest, chạy instrumentation trực tiếp với đối số capture=true rồi lấy thư mục /sdcard/Android/data/com.deliveryapp.shipper/files/previews bằng adb pull. Gradle có thể gỡ APK và xóa thư mục ảnh sau connectedDebugAndroidTest.
