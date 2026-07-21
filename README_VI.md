# 🤖 Trợ Lý AI XiaoZhi

**Ứng dụng Android trợ lý AI tiên tiến với tương tác bằng giọng nói, thị giác máy tính và tích hợp nhà thông minh.**

XiaoZhi là một ứng dụng Android toàn diện được hỗ trợ bởi AI kết hợp nhận dạng giọng nói, phát hiện khuôn mặt, điều khiển nhà thông minh và thực thi tác vụ thông minh. Nó có giao diện tinh vi được xây dựng bằng Jetpack Compose và tích hợp với các dịch vụ AI đám mây thông qua giao tiếp WebSocket.

## ✨ Các Tính Năng Chính

### 🎤 Giọng Nói & Âm Thanh
- **Phát Hiện Hotword**: Phát hiện từ đánh thức bằng Picovoice Porcupine
- **Phát Hiện Hoạt Động Giọng Nói (VAD)**: Xử lý âm thanh real-time với WebRTC VAD
- **Dịch Vụ Tương Tác Giọng Nói**: Tích hợp lệnh giọng nói liền mạch với hệ thống Android
- **Trực Quan Hóa Âm Thanh**: Hiển thị dạng sóng real-time trong quá trình phát nhạc
- **Hỗ Trợ Âm Thanh Đa Định Dạng**: Phát nhạc từ YouTube bằng yt-dlp

### 👁️ Thị Giác Máy Tính
- **Phát Hiện & Nhận Dạng Khuôn Mặt**: Phát hiện sự hiện diện khuôn mặt và theo dõi mắt real-time
- **Trình Xem Luồng Camera IP**: Hỗ trợ xem các camera IP (RTSP)
- **Chụp & Phân Tích Ảnh**: Chụp ảnh và phân tích với AI
- **Màn Hình Camera Lượng Tử**: Giao diện camera tiên tiến với phát hiện khuôn mặt

### 🏠 Tích Hợp Nhà Thông Minh
- **Hỗ Trợ Home Assistant**: Kết nối và điều khiển các phiên bản Home Assistant
- **Giao Thức MQTT**: Hỗ trợ message queue telemetry transport
- **Quản Lý WiFi & Bluetooth**: Quét, kết nối và quản lý các kết nối không dây
- **Điều Khiển Hệ Thống**: Âm lượng, độ sáng, chế độ máy bay, v.v.

### 🎬 Phát Lại Phương Tiện
- **Phát Video Streaming**: Phát video từ các nguồn khác nhau
- **Phát Nhạc**: Phát nhạc với trực quan hóa
- **Điều Khiển Phương Tiện**: Phát, tạm dừng, bài tiếp theo, bài trước
- **Hiển Thị Bìa Album**: Trực quan hóa tác phẩm album đẹp

### 🧠 AI & Giao Thức MCP
- **Model Context Protocol (MCP)**: Giao diện công cụ được chuẩn hóa cho các mô hình AI
- **Khung Công Cụ**: 50+ công cụ tích hợp để tương tác với hệ thống
- **Xử Lý Real-time**: Thời gian phản hồi nhanh với kiến trúc dựa trên coroutine
- **Dịch Vụ Trợ Năng**: Tích hợp hệ thống sâu để tự động hóa nâng cao

### 📱 Các Tính Năng Nâng Cao
- **Hợp Nhất Cảm Biến**: Kết hợp dữ liệu cảm biến đa lỗi để nhận thức tốt hơn
- **Chế Độ Overlay**: Giao diện truy cập nhanh có thể được kích hoạt bằng giọng nói
- **Đăng Nhập Google**: Tích hợp xác thực Firebase
- **Quản Lý File**: Duyệt, xóa và quản lý tệp thiết bị
- **Quản Lý Ứng Dụng**: Liệt kê và khởi chạy các ứng dụng được cài đặt
- **Cập Nhật OTA**: Hỗ trợ cập nhật over-the-air

## 🏗️ Kiến Trúc

```
Tieuchi/
├── app/
│   ├── build.gradle              # Cấu hình ứng dụng, phụ thuộc
│   ├── src/main/
│   │   ├── AndroidManifest.xml   # Quyền ứng dụng, hoạt động, dịch vụ
│   │   ├── kotlin/com/xiaozhi/   # Mã nguồn chính
│   │   │   ├── MainActivity.kt   # Điều phối chính & UI (90KB)
│   │   │   ├── McpHandler.kt     # Triển khai giao thức MCP (60KB)
│   │   │   ├── MyApplication.kt  # Khởi tạo ứng dụng
│   │   │   ├── WebSocketManager.kt    # Giao tiếp máy chủ
│   │   │   ├── XiaoZhiAudioManager.kt # Xử lý âm thanh
│   │   │   ├── EyeManager.kt          # Phát hiện khuôn mặt
│   │   │   ├── SystemController.kt    # Lệnh hệ thống
│   │   │   ├── SensorFusion.kt        # Tổng hợp dữ liệu cảm biến
│   │   │   ├── OverlayActivity.kt     # Giao diện overlay nổi
│   │   │   ├── SettingsActivity.kt    # Màn hình cấu hình
│   │   │   ├── HotwordForegroundService.kt  # Phát hiện từ đánh thức
│   │   │   ├── MusicPlaybackService.kt      # Phát lại âm thanh
│   │   │   ├── ai/                    # Thành phần AI & ML
│   │   │   ├── services/              # Dịch vụ hệ thống
│   │   │   ├── smarthome/             # Tích hợp Home Assistant
│   │   │   └── ui/                    # Thành phần UI Compose
│   │   ├── res/                  # Tài nguyên (drawable, layout, values)
│   │   └── assets/               # Tệp tài sản
│   ├── proguard-rules.pro         # Quy tắc che phủ mã
│   └── google-services.json       # Cấu hình Firebase
├── build.gradle                   # Cấu hình xây dựng gốc
├── settings.gradle                # Thiết lập dự án
└── gradle.properties              # Cấu hình Gradle

**Kiến Trúc Runtime:**
- **Giao Tiếp WebSocket**: Giao tiếp máy chủ hai chiều real-time
- **Trình Xử Lý Tin Nhắn MCP**: Xử lý gọi công cụ và phản hồi
- **Đường Ống Giọng Nói**: Đầu vào âm thanh → VAD → Máy chủ → Xử lý AI
- **Vòng Phát Hiện Khuôn Mặt**: Giám sát liên tục sự hiện diện khuôn mặt
- **Nghe Tự Động**: Ghi âm tự động khi phát hiện khuôn mặt
- **Hợp Nhất Cảm Biến**: Tổng hợp dữ liệu gia tốc kế, con quay hồi chuyển và cảm biến khác
```

### Luồng Dữ Liệu

```
┌─────────────────┐
│  Đầu Vào Người  │
│  (Giọng/Cảm Ứng)│
└────────┬────────┘
         │
         ▼
┌─────────────────────────┐
│  Xử Lý Âm Thanh /       │
│  Nhận Dạng Cử Chỉ      │
└────────┬────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Máy Chủ WebSocket      │
│  (Xử Lý AI Đám Mây)      │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Trình Xử Lý MCP        │
│  (Thực Thi Công Cụ)      │
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Bộ Điều Khiển Hệ Thống │
│ (Phương Tiện, Camera,...)│
└────────┬─────────────────┘
         │
         ▼
┌──────────────────────────┐
│   Cập Nhật UI / Phản Hồi │
└──────────────────────────┘
```

## 🛠️ Ngăn Xếp Công Nghệ

- **Ngôn Ngữ**: Kotlin
- **Framework**: Android (SDK 34), Jetpack Compose cho UI
- **Xây Dựng**: Gradle
- **Minimum SDK**: Android 8 (API 26)
- **Target SDK**: Android 14 (API 34)

### Các Phụ Thuộc Chính

| Thành Phần | Mục Đích |
|-----------|---------|
| **Jetpack Compose** | Framework UI khai báo hiện đại |
| **Media3 / ExoPlayer** | Phát lại video & âm thanh |
| **CameraX** | Truy cập và xử lý camera |
| **Room Database** | Lưu trữ dữ liệu cục bộ |
| **Firebase Auth** | Xác thực người dùng |
| **Retrofit 2** | HTTP client cho cuộc gọi API |
| **OkHttp3** | Giao tiếp mạng |
| **Gson** | Tuần tự hóa JSON |
| **Picovoice Porcupine** | Phát hiện từ đánh thức (4.0.0) |
| **WebRTC VAD** | Phát hiện hoạt động giọng nói |
| **Shizuku** | Tích hợp hệ thống nâng cao |
| **ML Kit** | Phát hiện khuôn mặt (Google ML Kit) |
| **TensorFlow Lite** | Suy luận ML trên thiết bị |

## ⚙️ Thiết Lập & Cấu Hình

### Yêu Cầu Trước Tiên

- Android Studio (phiên bản mới nhất)
- Android SDK API 34+
- Gradle 8.2.0+
- Kotlin 1.9.24+
- Java 17

### Xây Dựng & Chạy

```bash
# Clone kho lưu trữ
git clone https://github.com/Thanhdlpb/Tieuchi.git
cd Tieuchi

# Xây dựng dự án
./gradlew assembleDebug

# Cài đặt trên thiết bị/trình mô phỏng
./gradlew installDebug

# Chạy bài kiểm tra
./gradlew test

# Xây dựng phiên bản phát hành (yêu cầu cấu hình ký)
./gradlew assembleRelease
```

### Cấu Hình

1. **Thiết Lập Firebase**:
   - Thêm Google Services JSON của bạn vào `app/google-services.json`
   - Cấu hình Firebase Auth trong Firebase Console

2. **Khóa API Picovoice**:
   - Thêm vào `local.properties`:
     ```properties
     PICOVOICE_ACCESS_KEY=your_access_key_here
     ```

3. **Máy Chủ WebSocket**:
   - Cấu hình URL máy chủ và mã thông báo trong cài đặt ứng dụng
   - Kích hoạt thiết bị thông qua hệ thống kích hoạt mã PIN

4. **Home Assistant** (Tùy Chọn):
   - Đặt URL Home Assistant và mã thông báo xác thực
   - Cấu hình broker MQTT để giao tiếp thiết bị IoT

## 📋 Quyền Bắt Buộc

- `RECORD_AUDIO` - Ghi lại và xử lý giọng nói
- `CAMERA` - Phát hiện khuôn mặt và chụp ảnh
- `INTERNET` - Giao tiếp máy chủ
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` - Quét WiFi
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` - Hoạt động Bluetooth
- `WRITE_SETTINGS` - Điều khiển độ sáng/cài đặt hệ thống
- `POST_NOTIFICATIONS` - Thông báo người dùng
- `FOREGROUND_SERVICE` - Xử lý âm thanh nền

## 🎯 Giải Thích Các Thành Phần Chính

### MainActivity
- **Kích Thước**: ~90KB (thành phần lớn nhất)
- **Vai Trò**: Trung tâm điều phối, hiển thị UI, xử lý quyền
- **Tính Năng**: Vòng phát hiện khuôn mặt, logic nghe tự động, quản lý overlay

### McpHandler
- **Kích Thước**: ~60KB
- **Vai Trò**: Triển khai giao thức Model Context Protocol cho thực thi công cụ AI
- **Tính Năng**: 50+ công cụ hệ thống, phát nhạc/video qua yt-dlp, điều khiển camera, quản lý tệp

### WebSocketManager
- **Vai Trò**: Giao tiếp máy chủ hai chiều real-time
- **Tính Năng**: Kết nối lại tự động, xếp hàng tin nhắn, quản lý phiên

### EyeManager
- **Vai Trò**: Phát hiện và theo dõi khuôn mặt
- **Tính Năng**: Phát hiện sự hiện diện khuôn mặt, nhận dạng nhấp mắt, theo dõi mắt

### XiaoZhiAudioManager
- **Vai Trò**: Xử lý đầu vào/đầu ra âm thanh
- **Tính Năng**: Điều khiển micrô, định tuyến âm thanh, triệt tiêu tiếng ồn

### SystemController
- **Vai Trò**: Tương tác với các dịch vụ hệ thống Android
- **Tính Năng**: Điều khiển âm lượng, điều chỉnh độ sáng, quản lý WiFi/Bluetooth

## 🔧 Phát Triển

### Cấu Trúc Mã
- **Activities**: Màn hình UI và tương tác người dùng
- **Services**: Xử lý nền (âm thanh, hotword, giám sát tệp)
- **Managers**: Quản lý vòng đời thành phần
- **UI (Compose)**: Các thành phần UI khai báo hiện đại
- **Utils**: Các hàm và tiện ích trợ giúp

### Thêm Công Cụ MCP Mới

1. Thêm định nghĩa công cụ vào `buildToolsList()` trong `McpHandler.kt`
2. Triển khai trình xử lý trong hàm `handleToolsCall()`
3. Trả về kết quả qua `sendToolCallResult()`

### Xây Dựng & Gỡ Lỗi

```bash
# Xây dựng với nhật ký gỡ lỗi
./gradlew assembleDebug --stacktrace

# Chạy bài kiểm tra cụ thể
./gradlew testDebug -Dorg.gradle.project.testFilter=com.xiaozhi.*

# Hồ sơ hiệu suất
# Sử dụng Android Studio Profiler
```

## 📊 Đặc Tính Hiệu Suất

- **Thời Gian Khởi Động**: < 3 giây
- **Độ Trễ Phản Hồi Giọng Nói**: 1-2 giây (với máy chủ)
- **Phát Hiện Khuôn Mặt**: 30 FPS trên các thiết bị hiện đại
- **Sử Dụng Bộ Nhớ**: ~200-300 MB (bình thường), tăng đột biến trong quá trình phát phương tiện
- **Tác Động Pin**: ~5-10% mỗi giờ với phát hiện hotword hoạt động

## 🤝 Đóng Góp

Chúng tôi hoan nghênh các đóng góp! Vui lòng:

1. Tuân theo hướng dẫn kiểu Kotlin
2. Thêm bài kiểm tra cho các tính năng mới
3. Cập nhật tài liệu
4. Gửi yêu cầu kéo tới nhánh `main`

## 📄 Giấy Phép

Kho lưu trữ riêng. Liên hệ với người duy trì để biết thông tin cấp phép.

## 👨‍💻 Tác Giả

**Thanhdlpb**
- GitHub: [@Thanhdlpb](https://github.com/Thanhdlpb)

## 🐛 Khắc Phục Sự Cố

### Ứng dụng bị sập khi khởi động
- Kiểm tra tất cả quyền bắt buộc được cấp
- Xác minh cấu hình Firebase
- Xem lại nhật ký sập trong `getExternalFilesDir("crash_logs")`

### Giọng nói không hoạt động
- Đảm bảo quyền micrô được cấp
- Kiểm tra trạng thái kết nối WebSocket
- Xác minh đầu vào âm thanh không bị chiếm bởi các ứng dụng khác

### Phát hiện khuôn mặt không hoạt động
- Kiểm tra quyền camera và camera thiết bị
- Kiểm tra điều kiện ánh sáng
- Xem lại nhật ký phát hiện khuôn mặt

### Kết nối WebSocket không thành công
- Xác minh URL máy chủ và mã thông báo trong cài đặt
- Kiểm tra kết nối mạng
- Xem lại nhật ký máy chủ

## 📞 Hỗ Trợ

Để báo cáo vấn đề, đặt câu hỏi hoặc yêu cầu tính năng, vui lòng mở một issue trên GitHub.

---

**Phiên Bản**: 1.0  
**Cập Nhật Lần Cuối**: 2024  
**Trạng Thái**: Phát Triển Hoạt Động