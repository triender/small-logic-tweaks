# Quy tắc ghi nhận Nhật ký thay đổi (Changelog Guidelines)

Để đảm bảo lịch sử cập nhật của dự án Small Logic Tweaks luôn rõ ràng, dễ theo dõi cho người chơi, Server Admins, và tương thích hoàn toàn với hệ thống trích xuất tự động (parser) của Gradle, các nguyên tắc sau đây phải được tuân thủ tuyệt đối:

---

## 1. Cấu trúc tiêu đề bắt buộc
Hàm parser trong Gradle sử dụng cấu trúc dòng tiêu đề để tìm và trích xuất changelog. Mỗi phiên bản phải bắt đầu bằng tiêu đề chuẩn hóa:

```markdown
# [Icon biểu thị] [Tên đợt update] (Version X.Y.Z)
```
* **Bắt buộc phải bắt đầu bằng `# ` (Heading 1)**.
* **Bắt buộc phải chứa từ khóa `Version X.Y.Z`** (trong đó X.Y.Z là số phiên bản tương ứng, ví dụ: `Version 1.4.1`).
* **Dấu phân cách phiên bản**: Phải kết thúc mỗi phiên bản bằng dấu phân cách `---` ở dòng trống tiếp theo để parser xác định giới hạn cuối của phiên bản đó.

---

## 2. Quản lý thay đổi chưa phát hành (Unreleased)
* Luôn duy trì phần `# 🛠️ Unreleased (Đang phát triển)` ở dòng đầu tiên của file changelog.
* Khi thêm tính năng mới hoặc sửa lỗi trong quá trình code, **hãy thêm ngay gạch đầu dòng tương ứng vào phần này**.
* Khi chuẩn bị phát hành phiên bản mới:
  1. Đổi tên tiêu đề `# 🛠️ Unreleased (Đang phát triển)` thành số phiên bản mới (ví dụ: `# 🚀 Version 1.4.2`).
  2. Ghi nhận ngày tháng phát hành.
  3. Tạo lại tiêu đề `# 🛠️ Unreleased (Đang phát triển)` trống ở trên cùng.

---

## 3. Phân loại nội dung cập nhật
Nội dung bên trong mỗi phiên bản phải được phân loại rõ ràng bằng các Heading 3 sau:

* **`### ✨ Added`**: Cho các tính năng hoặc cơ chế mới được thêm vào mod.
* **`### 🔧 Fixed`**: Cho các lỗi (bugs), crash game, hoặc sự cố chập chờn được khắc phục.
* **`### ⚙️ Changed`**: Cho các thay đổi về logic hiện có, tinh chỉnh cân bằng, hoặc tối ưu hóa hiệu suất.
* **`### 🛡️ Security`**: Cho các bản vá bảo mật, chống khai thác payload mạng, hoặc chống Dos/lỗi dữ liệu.

---

## 4. Ghi nhận thay đổi đặc thù theo phiên bản Minecraft
Do mod được build song song cho nhiều phiên bản Minecraft khác nhau (ví dụ: 1.21.1 và 1.21.2), nếu có các thay đổi chỉ áp dụng cho một phiên bản game nhất định, hãy ghi nhận trực tiếp vào changelog chung nhưng gắn nhãn ở đầu dòng:

* `* **[Chỉ dành cho bản 26.2]**: Sửa lỗi crash Render thread khi khởi động.`
* `* **[Chỉ dành cho bản 26.1.2]**: Điều chỉnh tương thích với Fabric API cũ.`

---

## 5. Ngôn ngữ và Phong cách
* Ghi nhận ngắn gọn, tập trung trực tiếp vào **trải nghiệm thực tế của người dùng**, không đưa log commit kỹ thuật hoặc code raw vào changelog.
* Sử dụng Markdown chuẩn (in đậm các từ khóa quan trọng để tăng tính dễ đọc).
