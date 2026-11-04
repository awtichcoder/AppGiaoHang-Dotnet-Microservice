# Đối chiếu API và chuẩn bị tích hợp

Nguồn backend đã đọc: [AppGiaoHang-Dotnet-Microservice](https://github.com/awtichcoder/AppGiaoHang-Dotnet-Microservice), commit `2a65ff066f53d32dd49429652246ab7656d021df`; `DeliveryWorkflowContracts.cs`, `DeliveryController.cs`, Driver contracts/controller, Identity contracts. Đã đọc tab API của [Google Sheet](https://docs.google.com/spreadsheets/d/1XrBIZQ8c4lD_7dpJaxRse24ont4GWrBGJfxCkY5Uaws/edit).

Mọi endpoint dùng Gateway. Không có API internal trong interface Retrofit.

| UI | Endpoint | Request/response cần chú ý |
|---|---|---|
| Đăng nhập | POST /api/identity/login | email/password → accessToken/refreshToken/expiresAt |
| Hồ sơ và role | GET /api/identity/me | userId/fullName/email/phone/role; chỉ role DRIVER được vào |
| Refresh | POST /api/identity/refresh | refreshToken; client riêng không interceptor/authenticator |
| Đăng xuất | POST /api/identity/logout | refreshToken; sau đó xóa phiên và back stack |
| Bật/tắt | PATCH /api/driver/me/availability | availabilityStatus = OFFLINE hoặc AVAILABLE; BUSY do server |
| GPS | PUT /api/driver/me/location | latitude/longitude/accuracyM/recordedAt UTC |
| Lời mời | GET /api/delivery/offers/me?page=1&pageSize=20 | PagedResponse; status, incomeEstimate, distanceToPickupKm, sentAt, expiresAt |
| Nhận | POST /api/delivery/offers/{offerId}/accept | Không body; receipt chỉ offerId/deliveryId/status |
| Từ chối | POST /api/delivery/offers/{offerId}/reject | reasonCode bắt buộc |
| Chi tiết sau nhận | GET /api/delivery/order/{orderId} | DeliveryResponse, địa chỉ/tọa độ, receiverPhone, totalFee, timestamp |
| Lấy hàng | POST /api/delivery/{id}/pickup | gpsLatitude/gpsLongitude nullable trong DTO thực |
| Bắt đầu giao | POST /api/delivery/{id}/start | Chỉ từ PICKED_UP; DeliveryResponse |
| Hoàn thành | POST /api/delivery/{id}/complete | Chỉ từ DELIVERING; DeliveryResponse |

Sheet dùng estimatedIncome/offerStatus trong ví dụ, code backend dùng incomeEstimate/status. Kotlin DTO theo code backend. Không lấy tổng phí làm tiền tài xế thực nhận. Offer model không có số điện thoại người nhận.

## Các điểm đã chuẩn bị

- `data/api/ShipperApi.kt`: endpoint và DTO tách khỏi UI; status lạ thành UNKNOWN/PROCESSED để khóa hành động.
- `core/network/NetworkFactory.kt`: Retrofit/Gson + OkHttp, bearer, timeout, error code, log BASIC không body.
- Refresh 401 tối đa một lần; đồng bộ refresh; không xóa phiên chỉ vì lỗi mạng/5xx.
- TokenPair được thay thế có điều kiện: refresh chậm không ghi token trở lại sau logout.
- `TokenManager`: DataStore lưu cặp token mã hóa AES/GCM bằng Android Keystore, không chứa internal key.
- `WorkRepository`/`AuthRepository`: giao diện để thay mock bằng adapter thật.
- `AppContainer`: điểm duy nhất lựa chọn repository.

## Phần cần làm trước khi bật live

1. Viết NetworkAuthRepository: login → lưu token → GET me → kiểm tra DRIVER; role sai phải xóa token. Thêm restore session và thông báo session expired.
2. Viết NetworkWorkRepository. Accept phải lưu orderId ngay khi receipt thành công, rồi GET detail. Nếu GET detail mất mạng, retry detail thay vì gửi accept lần nữa.
3. Khôi phục active trip sau process death: backend hiện chưa có endpoint DRIVER lấy chuyến đang làm theo token. Thống nhất API với nhóm backend hoặc lưu orderId và xác minh lại quyền/trạng thái khi mở app.
4. Chưa thấy API lịch sử riêng DRIVER. Giữ mock lịch sử có nhãn cho đến khi có hợp đồng thật. Không dùng danh sách đơn dành cho CUSTOMER.
5. Poll/push offer khi AVAILABLE và refresh chuyến khi foreground; 409 phải đọc lại trạng thái server. UI không phải nguồn quyết định cuối cùng.
6. GPS foreground: xin quyền, xác định tần suất và độ chính xác, gửi recordedAt UTC. Chưa triển khai background location hay foreground service.
7. Chạy contract/integration tests với Gateway đang hoạt động; không gọi service-to-service.
8. Release HTTPS, cấu hình signing riêng. Bản debug cho HTTP localhost qua manifest debug; không đưa internal secret vào build config.

Có thể đổi URL cấu hình bằng `-PapiBaseUrl=https://gateway.example/` khi build. Cấu hình này chỉ phục vụ lớp mạng đã chuẩn bị, **không tự chuyển ứng dụng từ mock sang live**.
