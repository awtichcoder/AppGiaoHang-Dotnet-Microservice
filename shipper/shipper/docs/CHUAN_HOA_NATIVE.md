# Chuẩn hóa ShipperApp thành Android Native

Ngày thực hiện: 31/10/2026.

## Kết quả và bản sao an toàn

Project Kotlin + Jetpack Compose đã được đưa từ `ShipperApp/` lên thư mục gốc `D:\App_giao_hang\FE-GiaoHang`. Mở thư mục gốc này trong Android Studio. Module ứng dụng là `app`; namespace/applicationId là `com.deliveryapp.shipper`.

Các thành phần cũ được loại khỏi workspace bằng cách chuyển vào kho lưu ngoài workspace, không xóa vĩnh viễn:

`D:\App_giao_hang\_backups\FE-GiaoHang-native-20261031-c24e4a61`

- `repository.bundle`: Git history và refs; đã chạy bundle verify.
- `workspace-sources.zip`: 258 file mã nguồn/cấu hình/tài nguyên, kiểm tra SHA-256 từng entry sau khi nén.
- `source-manifest.csv`: hash và phân loại 67 file Native, 191 file cũ.
- `git-status-before.txt`, `unstaged.patch`, `staged.patch`: trạng thái trước di chuyển.
- `legacy-workspace/`: các thư mục gốc cũ và toàn bộ project ShipperApp ban đầu, gồm cache/build cũ.
- `DE_XUAT_DON_DEP.md`, `move-plan.csv`: danh sách và lý do đã lập trước thao tác.

Không commit, push hoặc thay đổi `.git`. HEAD giữ nguyên `95c7fe7d2e2e518552db726ca4dc820f9c8c6195`. Các thay đổi trước đó đã được bảo toàn. Không có file nguồn/tài nguyên không thể phục hồi nên không cần bước xác nhận dành cho trường hợp mất dữ liệu không phục hồi. Không thay đổi repository Backend hoặc Customer App.

Muốn phục hồi, lấy file cụ thể từ `legacy-workspace/` hoặc giải nén ZIP vào một thư mục riêng rồi đối chiếu manifest trước khi chép lại. Không giải nén đè lên workspace đang làm việc. Bundle giữ lịch sử Git; ZIP và thư mục lưu giữ cả file chưa commit.

## Thành phần đã loại khỏi cây dự án đang dùng

Danh sách chính xác từng file nguồn/tài nguyên: [LEGACY_REMOVED.csv](LEGACY_REMOVED.csv), 191 dòng. Cache và sản phẩm build được ghi theo thư mục trong move-plan ở bản sao lưu.

| Thành phần | Lý do |
|---|---|
| `lib/`, `test/` | Mã nguồn và test Dart cũ; Native không tham chiếu |
| `pubspec.yaml`, `pubspec.lock`, `analysis_options.yaml`, `.metadata` | Cấu hình và dependency Flutter |
| `.dart_tool/`, `.flutter-plugins-dependencies` | Metadata/cache Flutter; không có `.flutter-plugins` trong trạng thái đầu |
| `ios/`, `web/`, `linux/`, `macos/`, `windows/` | Host nền tảng Flutter |
| `android/` cũ | Đã kiểm tra plugin Flutter, SDK Flutter và Activity kế thừa FlutterActivity; không phải module Native |
| `build/` cũ | Build/cache Flutter |
| `.vscode/` cũ | Cấu hình chạy Chrome localhost:8080 |
| `docs/`, README và .gitignore cũ | Nội dung dành cho Flutter, đã thay bằng tài liệu Native |
| `ShipperApp/` lồng trước đây | 67 file cần thiết đã chép lên root và kiểm tra hash; bản gốc được lưu ngoài workspace |

Đã kiểm tra logo cũ là Flutter và icon Native riêng `ic_shipper.xml`. Native dùng font hệ thống, không phụ thuộc font/ảnh ở các host cũ. Các ảnh chụp giao diện Native vẫn được giữ trong `docs/screenshots/`. Không duyệt hoặc xóa đích junction của plugin trong pub cache.

## Thành phần giữ lại và file chỉnh sửa

[NATIVE_FILES.csv](NATIVE_FILES.csv) ghi đủ 67 file đã chuyển cùng hash trước di chuyển. Giữ toàn bộ Kotlin, Compose, Navigation, ViewModel/StateFlow, repository, DTO, Retrofit/OkHttp, DataStore, xử lý JWT, test, manifest, theme, icon, Gradle và wrapper. 50 file mã nguồn/tài nguyên/cấu hình build Native không đổi nội dung sau chuyển. Hash trong CSV là mốc trước sửa tài liệu, không phải cam kết hash hiện tại của README và các tài liệu đã cập nhật.

Chỉnh sửa README, .gitignore, docs/KIEN_TRUC.md, docs/RA_SOAT.md và docs/KIEM_THU.md để dùng đường dẫn root mới. Thêm .editorconfig, .gitattributes, báo cáo này và hai CSV kiểm kê. Không thay đổi logic màn hình trong đợt di chuyển.

```text
FE-GiaoHang/
├── app/
│   ├── build.gradle.kts
│   └── src/ (main, debug, test, androidTest)
├── gradle/wrapper/
├── docs/ (kiến trúc, API, kiểm thử, kiểm kê, screenshots)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── .editorconfig / .gitattributes / .gitignore
└── README.md
```

`.git/` được giữ nguyên; `.gradle/`, `.kotlin/`, `build/`, `app/build/` được tạo lại bởi Native build và bị gitignore. `local.properties` chứa đường dẫn SDK cục bộ, cũng bị gitignore.

## Kiểm chứng và vấn đề còn lại

- Root Gradle `help`, `assembleDebug`, `testDebugUnitTest`, `lintDebug`: BUILD SUCCESSFUL; 23 tests, 0 failures/errors.
- `connectedDebugAndroidTest` trên Android 16 Emulator: 2 tests đạt, BUILD SUCCESSFUL. Luồng đăng nhập, nhận/từ chối, bản đồ, tái tạo Activity, lấy/giao/hoàn thành, lịch sử, hồ sơ và đăng xuất được kiểm tra.
- Lint: 0 errors, 14 cảnh báo gợi ý cập nhật dependency; không nâng thư viện ngoài phạm vi dọn dẹp.
- Không còn tham chiếu Flutter trong source Native hoặc cấu hình build. Lịch sử Git và báo cáo chuyển đổi vẫn có thể nhắc Flutter.
- Gradle được xác minh qua wrapper; chưa thao tác Gradle Sync trong giao diện Android Studio.

Log và giới hạn chi tiết: [KIEM_THU.md](KIEM_THU.md). APK: `app/build/outputs/apk/debug/app-debug.apk`.

Toàn bộ màn hình chính tiếp tục chạy với mock repository. Backend thật, release signing và thiết bị vật lý chưa được kiểm chứng. Lịch sử vẫn là dữ liệu mô phỏng; không tạo API DRIVER chưa được xác minh. Nền tảng UI Native được giữ để tiếp tục phát triển theo [KIEN_TRUC.md](KIEN_TRUC.md) và hợp đồng trong [API.md](API.md).
