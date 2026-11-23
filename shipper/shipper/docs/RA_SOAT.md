# Rà soát mã Kotlin ShipperApp

Ngày: 31/10/2026. Phạm vi: source Android Native, dependency, resources, navigation, ViewModel, repository, mạng/JWT, GPS/bản đồ, kiểm thử và file phát sinh trong workspace.

## Những gì đã sửa

| Vấn đề tìm thấy qua đọc source | Cách xử lý | Kiểm chứng |
|---|---|---|
| Lỗi ghi DataStore trong coroutine cài đặt chưa được bắt, có thể làm ứng dụng dừng | Hiển thị lỗi lưu cỡ chữ; cho phép thử lại; không nuốt CancellationException | StateRecoveryTest.settingsWriteFailureIsVisibleAndCanBeRetried |
| LocationViewModel sống theo Activity, giữ GPS qua logout; tác vụ đọc có thể còn chạy | Hủy tác vụ, xóa vị trí và trạng thái quyền khi logout; xóa vị trí cũ khi bị từ chối quyền | Hai test reset/denied GPS trong StateRecoveryTest; gọi reset tại navigation |
| finally của tác vụ bị hủy có thể đặt loading=false cho phiên mới | Dùng thế hệ phiên để chỉ tác vụ thuộc phiên hiện tại được nhả khóa | StateRecoveryTest.cancelledSessionCannotUnlockNewAction |
| Gson có thể đưa null vào trường Kotlin không nullable; refresh thiếu token có thể ghi đè phiên | Kiểm tra token trước khi lưu, xử lý JSON lỗi và giữ phiên khi refresh lỗi tạm thời | SessionNetworkTest: 7 tests với MockWebServer |
| Callback style/camera có thể đến sau khi MapView bị dispose | Chặn callback muộn, tạo view theo LifecycleOwner, dùng LocalDensity cho padding | Test UI mở/đóng bản đồ hai lần và recreate Activity tại bản đồ |
| Lịch sử dùng Column, dựng mọi chuyến ngay cả ngoài màn hình | LazyColumn với key theo deliveryId; tách AppFrame dùng chung cho màn thường và màn danh sách | Kiểm tra luồng lịch sử và chi tiết trên Emulator |
| Theme có darkTheme/dynamicColor nhưng không sử dụng | Bỏ hai tham số gây hiểu nhầm; vẫn dùng nhận diện sáng của Customer App | Build Kotlin |
| Dependency preview và import không dùng | Bỏ khai báo trực tiếp ui-tooling-preview cùng 5 import dư | Build, lint |
| Chưa khai báo quy tắc backup/transfer rõ ràng theo phiên bản Android | Thêm data_extraction_rules.xml, giữ allowBackup=false và fullBackupContent=false | Android resource build/lint |
| Đường dẫn mở project sau chuẩn hóa | README hướng dẫn mở thư mục gốc chứa settings.gradle.kts | Gradle build và báo cáo chuẩn hóa |

Lớp mạng vẫn là phần chuẩn bị cho live; các test mới dùng máy chủ giả cục bộ, không gọi Backend thật.

## Trạng thái project hiện tại

Project đã được đưa lên thư mục gốc repository. Chỉ còn module Android Native `app`; mã nguồn, test và tài nguyên đang dùng được giữ nguyên khi chuyển đường dẫn. Danh sách file cũ đã loại khỏi workspace, file Native được giữ và điểm khôi phục nằm trong [CHUAN_HOA_NATIVE.md](CHUAN_HOA_NATIVE.md).

- Giữ MVVM/StateFlow, Material 3, Navigation Compose, mock repository và các lớp Retrofit/JWT chuẩn bị kết nối backend.
- Giữ Gradle wrapper, icon chữ D, resources theo phiên bản API, cấu hình backup và ảnh chụp Native.
- Cache hiện tại `.gradle/`, `.kotlin/`, `app/build/`, `build/` được tạo lại khi build Android và đã gitignore.
- APK debug universal chứa MapLibre cho nhiều ABI để hỗ trợ Emulator và điện thoại. Không xóa thủ công thư viện native; khi phát hành có thể đánh giá App Bundle/R8 và kiểm thử riêng.
- Không thay đổi Backend hoặc Customer App. Chưa bật repository live hay phát hành bản release có ký số.

Kết quả build và kiểm thử mới tại thư mục gốc được ghi trong [KIEM_THU.md](KIEM_THU.md).