document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }

    const loginForm = document.getElementById("loginForm");
    const email = document.getElementById("email");
    const password = document.getElementById("password");
    const toggleBtn = document.getElementById("togglePassword");

    // Thao tác xóa thông báo lỗi khi người dùng gõ phím
	if (email) email.addEventListener("input", validateEmail);
	if (password) password.addEventListener("input", validatePassword);

    // Tính năng ẩn / hiện mật khẩu
    if (toggleBtn && password) {
        toggleBtn.addEventListener("click", () => {
            const isHidden = password.type === "password";
            password.type = isHidden ? "text" : "password";
            toggleBtn.innerHTML = `<i data-lucide="${isHidden ? "eye-off" : "eye"}"></i>`;
            if (typeof lucide !== "undefined") {
                lucide.createIcons();
            }
        });
    }

    // username@domain.extension
    const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/;

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

	function validatePassword() {
	    if (!password) return true;
	    const value = password.value;
	    if (!value) {
	        showError("password", "パスワードを入力してください");
	        return false;
	    }
	    if (value.length < 8) {
	        showError("password", "パスワードは8文字以上で入力してください");
	        return false;
	    }
	    clearError("password");
	    return true;
	}

    // Xử lý sự kiện gửi form
    if (loginForm) {
        loginForm.addEventListener("submit", (event) => {
            const isEmailValid = validateEmail();
            const isPwdValid = validatePassword();

            if (!isEmailValid || !isPwdValid) {
                event.preventDefault();
            }
        });
    }
});