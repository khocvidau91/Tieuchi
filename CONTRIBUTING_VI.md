# Đóng Góp Vào Dự Án Tieuchi

Cảm ơn bạn đã quan tâm đến việc đóng góp cho Tieuchi! Chúng tôi hoan nghênh các đóng góp từ cộng đồng. Tài liệu này cung cấp các hướng dẫn và hướng dẫn để đóng góp cho dự án.

## Mục Lục

- [Quy Tắc Ứng Xử](#quy-tắc-ứng-xử)
- [Bắt Đầu](#bắt-đầu)
- [Quy Trình Phát Triển](#quy-trình-phát-triển)
- [Tiêu Chuẩn Mã](#tiêu-chuẩn-mã)
- [Hướng Dẫn Commit](#hướng-dẫn-commit)
- [Quy Trình Pull Request](#quy-trình-pull-request)
- [Kiểm Tra](#kiểm-tra)
- [Tài Liệu](#tài-liệu)
- [Báo Cáo Vấn Đề](#báo-cáo-vấn-đề)
- [Các Vấn Đề Bảo Mật](#các-vấn-đề-bảo-mật)

## Quy Tắc Ứng Xử

Vui lòng xem lại [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) của chúng tôi trước khi tham gia. Chúng tôi cam kết cung cấp môi trường hỗ trợ và bao gồm cho tất cả những người đóng góp.

## Bắt Đầu

### Yêu Cầu Trước Tiên

- Android Studio (phiên bản mới nhất)
- Android SDK API 34+
- Gradle 8.2.0+
- Kotlin 1.9.24+
- Java 17+
- Git

### Thiết Lập Môi Trường Phát Triển

1. **Fork kho lưu trữ**
   ```bash
   # Đi tới https://github.com/Thanhdlpb/Tieuchi
   # Nhấp vào nút "Fork"
   ```

2. **Sao chép fork của bạn**
   ```bash
   git clone https://github.com/<tên-người-dùng>/Tieuchi.git
   cd Tieuchi
   ```

3. **Thêm upstream remote**
   ```bash
   git remote add upstream https://github.com/Thanhdlpb/Tieuchi.git
   ```

4. **Cấu hình IDE của bạn**
   - Nhập dự án vào Android Studio
   - Để Gradle đồng bộ hóa và tải xuống các phụ thuộc
   - Cấu hình đường dẫn SDK nếu cần thiết

5. **Thiết lập cấu hình cục bộ**
   ```bash
   # Tạo local.properties cho các khóa API
   echo "PICOVOICE_ACCESS_KEY=your_key_here" > local.properties
   ```

## Quy Trình Phát Triển

### 1. Tạo Chi Nhánh Tính Năng

```bash
# Cập nhật chi nhánh main
git fetch upstream
git checkout main
git merge upstream/main

# Tạo chi nhánh tính năng có tên mô tả
git checkout -b feature/mô-tả-tính-năng
# hoặc để sửa lỗi
git checkout -b fix/mô-tả-lỗi
```

### 2. Thực Hiện Các Thay Đổi Của Bạn

- Thực hiện các commit tập trung, nguyên tử
- Giữ các thay đổi liên quan đến vấn đề/tính năng
- Tránh các sửa đổi không liên quan
- Kiểm tra kỹ lưỡng các thay đổi của bạn

### 3. Giữ Chi Nhánh Của Bạn Cập Nhật

```bash
git fetch upstream
git rebase upstream/main
```

### 4. Đẩy Lên Fork Của Bạn

```bash
git push origin feature/mô-tả-tính-năng
```

### 5. Tạo Yêu Cầu Kéo

- Đi tới GitHub và tạo PR từ fork của bạn sang kho lưu trữ chính
- Điền vào mẫu PR với thông tin chi tiết
- Liên kết các vấn đề liên quan
- Chờ xem xét và phản hồi

## Tiêu Chuẩn Mã

### Hướng Dẫn Kiểu Kotlin

Chúng tôi tuân theo [Quy Ước Mã Hóa Kotlin](https://kotlinlang.org/docs/coding-conventions.html) chính thức:

```kotlin
// ✅ Tốt: Đặt tên rõ ràng và cấu trúc
class AudioManager(context: Context) {
    private var isRecording = false
    
    fun startRecording(callback: (ByteArray) -> Unit): Boolean {
        if (isRecording) return false
        
        isRecording = true
        // Triển khai
        return true
    }
    
    fun stopRecording() {
        isRecording = false
        // Triển khai
    }
}

// ❌ Tránh: Đặt tên không rõ ràng và cấu trúc
class AM(c: Context) {
    private var rec = false
    
    fun start(cb: (ByteArray) -> Unit): Boolean {
        if (rec) return false
        rec = true
        return true
    }
}
```

### Quy Ước Đặt Tên

- **Các Lớp**: PascalCase (ví dụ: `MainActivity`, `WebSocketManager`)
- **Các Hàm**: camelCase (ví dụ: `startRecording`, `onFaceDetected`)
- **Các Biến**: camelCase (ví dụ: `isRecording`, `audioBuffer`)
- **Hằng Số**: UPPER_SNAKE_CASE (ví dụ: `MAX_AUDIO_SIZE`, `TIMEOUT_MS`)
- **Các Thành Viên Riêng Tư**: Tiền Tố Gạch Dưới (ví dụ: `_waveformAmplitudes`)

## Hướng Dẫn Commit

### Định Dạng Thông Báo Commit

```
<loại>(<phạm-vi>): <chủ-đề>

<nội-dung>

<chân-trang>
```

### Các Loại

- **feat**: Tính năng mới
- **fix**: Sửa lỗi
- **docs**: Thay đổi tài liệu
- **style**: Định dạng mã (không thay đổi chức năng)
- **refactor**: Tổ chức lại mã (không thay đổi chức năng)
- **perf**: Cải thiện hiệu suất
- **test**: Thêm hoặc thay đổi bài kiểm tra
- **chore**: Xây dựng, phụ thuộc hoặc thay đổi công cụ

### Ví Dụ

```bash
# Commits tốt
git commit -m "feat(audio): thêm hỗ trợ VAD cho phát hiện giọng nói"
git commit -m "fix(face-detection): cải thiện độ chính xác bằng hợp nhất cảm biến"
git commit -m "docs(readme): thêm phần khắc phục sự cố"
git commit -m "refactor(mcp-handler): đơn giản hóa định tuyến lệnh công cụ"

# Tránh
git commit -m "Đã sửa những thứ"
git commit -m "WIP: các thay đổi khác nhau"
```

## Quy Trình Pull Request

### Trước Khi Gửi

1. **Kiểm tra kỹ lưỡng**
   ```bash
   ./gradlew test
   ./gradlew assembleDebug
   ```

2. **Chạy linting**
   ```bash
   ./gradlew lint
   ```

3. **Cập nhật tài liệu**
   - Cập nhật README nếu thêm tính năng
   - Cập nhật CHANGELOG.md
   - Thêm nhận xét mã nội tuyến

4. **Đảm bảo chất lượng mã**
   - Tuân theo hướng dẫn kiểu Kotlin
   - Giữ các phương thức tập trung và nhỏ
   - Xóa mã gỡ lỗi và ghi nhật ký

## Kiểm Tra

### Bài Kiểm Tra Đơn Vị

```bash
# Chạy tất cả bài kiểm tra
./gradlew test

# Chạy lớp bài kiểm tra cụ thể
./gradlew test --tests com.xiaozhi.AudioManagerTest

# Chạy các bài kiểm tra với phạm vi bảo hiểm
./gradlew test --coverage
```

## Tài Liệu

### Cập Nhật README

Khi thêm tính năng, hãy cập nhật các phần liên quan trong README.md

## Báo Cáo Vấn Đề

### Trước Khi Tạo Vấn Đề

- Tìm kiếm các vấn đề hiện có để tránh trùng lặp
- Kiểm tra tài liệu và Câu Hỏi Thường Gặp
- Hãy thử các bước khắc phục sự cố

## Các Vấn Đề Bảo Mật

**KHÔNG mở các vấn đề công khai cho các lỗ hổng bảo mật.**

Vui lòng email các mối quan tâm bảo mật trực tiếp đến những người duy trì dự án hoặc sử dụng tính năng tư vấn bảo mật của GitHub.

---

Cảm ơn vì đã giúp làm cho Tieuchi tốt hơn! 🚀
