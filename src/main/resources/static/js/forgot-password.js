document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }

    const forgotPasswordForm = document.getElementById("forgotPasswordForm");
    const email = document.getElementById("email");

    // Thao tác xóa thông báo lỗi khi người dùng gõ phím
    if (email) email.addEventListener("input", () => clearError("email"));

    // username@domain.extension
    const EMAIL_PATTERN = 	/^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;

    // Các hàm hiển thị và xóa thông báo lỗi
    function showError(inputId, message) {
        const input = document.getElementById(inputId);
        const errorBox = document.getElementById(inputId + "Error");
        if (!input || !errorBox) return;
        input.classList.add("invalid");
        const span = errorBox.querySelector("span");
        if (span) span.textContent = message;
        errorBox.classList.add("show");
    }

    function clearError(inputId) {
        const input = document.getElementById(inputId);
        const errorBox = document.getElementById(inputId + "Error");
        if (!input || !errorBox) return;
        input.classList.remove("invalid");
        const span = errorBox.querySelector("span");
        if (span) span.textContent = "";
        errorBox.classList.remove("show");
    }

    // Các hàm Logic Validation từng ô dữ liệu
    function validateEmail() {
        if (!email) return true;
        const value = email.value.trim();
        if (!value) {
            showError("email", "メールアドレスを入力してください");
            return false;
        }
        if (value.length > 255) {
            showError("email", "メールアドレスは255文字以内で入力してください");
            return false;
        }
        if (!EMAIL_PATTERN.test(value)) {
            showError("email", "メールアドレスの形式が正しくありません");
            return false;
        }
        clearError("email");
        return true;
    }

    // Xử lý sự kiện gửi form
    if (forgotPasswordForm) {
        forgotPasswordForm.addEventListener("submit", (event) => {
            const isEmailValid = validateEmail();

            if (!isEmailValid) {
                event.preventDefault();
            }
        });
    }
});