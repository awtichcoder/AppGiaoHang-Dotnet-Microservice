# DeliveryApp — Ứng dụng Tài xế (Shipper)

Nhánh `shipper` chứa ứng dụng Android dành cho **Tài xế** (module `:shipper`, `com.deliveryapp.shipper`). Ứng dụng gửi request qua API Gateway của Backend (`http://localhost:6200`, Emulator dùng `http://10.0.2.2:6200/`).

Các ứng dụng khác nằm ở nhánh riêng: `customer` (app Khách hàng), `admin` (web Quản trị). Backend nằm ở nhánh `main`.

## Clone và mở dự án

```bash
git clone -b shipper https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice.git ShipperApp
cd ShipperApp
```

Mở thư mục vừa clone bằng Android Studio (JDK 17, Android SDK 36), chờ Gradle Sync, chọn cấu hình chạy `shipper` rồi bấm Run. Không sao chép `local.properties` từ máy khác.

Lệnh thường dùng:

```powershell
.\gradlew.bat :shipper:installDebug                                        # cài app Tài xế
.\gradlew.bat :shipper:testDebugUnitTest :shipper:lintDebug :shipper:assembleDebug
.\gradlew.bat :shipper:installDebug -PuseMock=true                         # giao diện demo, không cần Backend
.\gradlew.bat :shipper:installDebug -PapiBaseUrl=http://192.168.1.10:6200/ # điện thoại thật
```

## App Tài xế kết nối Backend

Mặc định app Tài xế gọi Backend thật qua Gateway (`LiveShipperRepository`):

- Đăng nhập bằng tài khoản DRIVER. Backend local tạo sẵn `driver@deliveryapp.local / Driver123!`.
- Khi bật nhận đơn, app xin quyền vị trí, gửi GPS mỗi 15 giây (Backend chỉ ghép tài xế có vị trí trong 60 giây gần nhất, bán kính 3–5 km quanh điểm lấy hàng) và tự kiểm tra lời mời mỗi 3 giây.
- Nhận/từ chối lời mời, lấy hàng → bắt đầu giao → hoàn thành đều gọi API Delivery thật.
- Khi bấm lấy hàng, app gửi GPS hiện tại (không cũ quá 10 giây). Backend chỉ cho lấy hàng trong phạm vi 200 m quanh điểm lấy hàng; ở xa hơn app báo "Bạn đang ở xa điểm lấy hàng". Trên emulator, đặt vị trí đúng tại điểm lấy hàng trước khi bấm.
- Backend chưa có API lịch sử/chuyến hiện tại cho tài xế, nên tab Lịch sử chỉ hiện chuyến hoàn thành trong phiên đăng nhập và chuyến đang giao không được khôi phục khi app bị đóng.

Emulator: đặt vị trí gần điểm lấy hàng, ví dụ `adb emu geo fix 106.6996 10.7755` (kinh độ trước, vĩ độ sau) hoặc Extended Controls → Location.

Tài liệu chi tiết của app: [shipper/README.md](shipper/README.md) và thư mục [shipper/docs](shipper/docs).
