# ShipperApp — kiến trúc Kotlin Native

Project Android Native nằm trực tiếp tại gốc repository, gồm `app/`, `gradle/`, `settings.gradle.kts` và `build.gradle.kts`. Android Studio mở `D:\App_giao_hang\FE-GiaoHang`. Module duy nhất là `:app`; applicationId/namespace là `com.deliveryapp.shipper`. Mã cũ đã được lưu ngoài workspace; chi tiết nằm trong [CHUAN_HOA_NATIVE.md](CHUAN_HOA_NATIVE.md).

## Customer đã phân tích

Nguồn: CustomerApp--DeliveryApp commit aeb2cd255849138d6a3eba58571ccfb165f387fc. Đã đọc AppContainer, AuthViewModel, TrackingViewModel/Screen, TokenManager, AuthInterceptor, TokenRefreshAuthenticator, NetworkFactory, ApiCall, AppNavHost, theme và UI components.

- Sao chép có điều chỉnh package bộ Color/Type/Theme để đồng nhất nhận diện: #16A36A, #0D6F49, #EAF8F2, #F6F8F7; sans 30/24/20/16/14 sp.
- Dùng cùng mô hình Application → AppContainer → repository, inject qua ViewModel.Factory, không thêm DI framework không cần thiết.
- Customer dùng mutableStateOf cho Auth và StateFlow cho tracking. Shipper thống nhất StateFlow + collectAsStateWithLifecycle.
- Tái sử dụng nguyên tắc NavHost, clear auth stack khi đăng nhập/đăng xuất; không sao chép route đăng ký/rewards của CUSTOMER.
- Retrofit + Gson, OkHttp bearer interceptor và refresh authenticator tách client để không lặp 401.
- Customer TokenManager lưu token bằng Preferences DataStore (không tự mã hóa). Shipper chuẩn bị TokenManager lưu ciphertext AES/GCM bằng Android Keystore và tách namespace shipper_session. Mock không sinh hoặc lưu JWT giả.
- MapLibre AndroidView và quản lý lifecycle tương tự Customer; bản đồ thật dùng OpenFreeMap, tọa độ chuyến/driver mẫu được gắn nhãn. GPS thiết bị chỉ đọc khi người dùng chọn và cấp quyền. Không gửi vị trí lên backend trong mock.

## Cấu trúc

app/src/main/java/com/deliveryapp/shipper/
- core/network, storage, location: hạ tầng không chứa composable
- data/model, api, repository: hợp đồng DTO backend và repository mô phỏng
- feature/auth: đăng nhập, session, Splash
- feature/home, offer, delivery, history, profile: UI theo nghiệp vụ
- feature/work: ViewModel StateFlow dùng chung cho chuyến đang làm
- navigation: routes, NavHost và back stack
- ui/theme, components: design system dùng lại

## Luồng

Splash → Login → Home ↔ History/Profile.
Home → Offer (30 giây theo expiresAt) → Detail → Tracking.
ASSIGNED → PICKED_UP → DELIVERING → COMPLETED → Home.
History → HistoryDetail (chỉ đọc). Logout → Login, xóa back stack.

## Phạm vi và giai đoạn

1. Nền project Android độc lập + theme/DTO/repository.
2. Splash/login, tab navigation; chỉ DRIVER, không đăng ký.
3. Availability/offer/transition/confirmation, loading/error/empty/success.
4. MapLibre, GPS permission, history/profile/settings.
5. Unit tests, Compose instrumented tests, assembleDebug, emulator QA và tài liệu.

Mock riêng, không sửa Customer/Backend. Lịch sử và thu nhập mô phỏng ghi rõ. Phí giao hàng không đồng nghĩa tiền tài xế nhận. Không gọi internal API, không có chức năng ví/rút tiền/đánh giá. Live repository chưa bật; backend mapping nằm trong docs/API.md. Figma chưa truy cập được nên chưa khẳng định đối chiếu pixel.
