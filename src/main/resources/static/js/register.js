document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }

    const registerForm = document.getElementById("registerForm");
    const accountName = document.getElementById("accountName");
    const email = document.getElementById("email");
    const password = document.getElementById("password");
    const confirmPassword = document.getElementById("confirmPassword");
    const toggleBtn = document.getElementById("togglePassword");
	const toggleConfirmBtn = document.getElementById("toggleConfirmPassword");

    // Thao tác xóa thông báo lỗi khi người dùng gõ phím
    if (accountName) accountName.addEventListener("input", () => clearError("accountName"));
    if (email) email.addEventListener("input", () => clearError("email"));
    if (password) {
        password.addEventListener("input", () => {
            clearError("password");
            clearError("confirmPassword");
        });
    }
    if (confirmPassword) confirmPassword.addEventListener("input", () => clearError("confirmPassword"));

	// Tính năng ẩn / hiện mật khẩu (dùng chung cho cả password và confirmPassword)
	function bindToggle(toggleBtn, input, iconId) {
	    if (!toggleBtn || !input) return;
	    toggleBtn.addEventListener("click", () => {
	        const isHidden = input.type === "password";
	        input.type = isHidden ? "text" : "password";
	        toggleBtn.innerHTML = `<i data-lucide="${isHidden ? "eye-off" : "eye"}" id="${iconId}"></i>`;
	        if (typeof lucide !== "undefined") {
	            lucide.createIcons();
	        }
	    });
	}
	bindToggle(toggleBtn, password, "eye");
	bindToggle(toggleConfirmBtn, confirmPassword, "eyeConfirmPassword");

    // username@domain.extension
	//*@, $, !, %, , ?, &, #
    const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/;
    const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#]).{8,100}$/;

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
    function validateAccountName() {
        if (!accountName) return true;
        const value = accountName.value.trim();
        if (!value) {
            showError("accountName", "名前を入力してください");
            return false;
        }
        if (value.length < 3 || value.length > 50) {
            showError(
                "accountName",
                "名前は3〜50文字で入力してください"
            );
            return false;
        }
        if (!/^[a-zA-Z0-9_ぁ-んァ-ヶー一-龯]+$/.test(value)) {
            showError(
                "accountName",
                "名前は英数字、アンダースコア、日本語のみ使用できます"
            );
            return false;
        }
        clearError("accountName");
        return true;
    }

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
		if (value.length < 8 || value.length > 100) {
			        showError("password", "パスワードは8〜100文字で入力してください");
			        return false;
			    }
			    if (!/[A-Z]/.test(value)) {
			        showError("password", "英大文字を1文字以上含めてください");
			        return false;
			    }
			    if (!/[a-z]/.test(value)) {
			        showError("password", "英小文字を1文字以上含めてください");
			        return false;
			    }
			    if (!/\d/.test(value)) {
			        showError("password", "数字を1文字以上含めてください");
			        return false;
			    }
			    if (!/[@$!%*?&#]/.test(value)) {
			        showError("password", "特殊記号（@$!%*?&#）を1文字以上含めてください");
			        return false;
			    }
        clearError("password");
        return true;
    }

    function validateConfirmPassword() {
        if (!confirmPassword || !password) return true;
        const value = confirmPassword.value;
        if (!value) {
            showError("confirmPassword", "確認用パスワードを入力してください");
            return false;
        }
        if (password.value !== value) {
            showError("confirmPassword", "パスワードが一致しません");
            return false;
        }
        clearError("confirmPassword");
        return true;
    }

    // Xử lý sự kiện gửi form (Nằm trọn bên trong DOMContentLoaded)
    if (registerForm) {
        registerForm.addEventListener("submit", (event) => {
            // Chạy kiểm tra tất cả các hàm
            const isNameValid = validateAccountName();
            const isEmailValid = validateEmail();
            const isPwdValid = validatePassword();
            const isConfirmValid = validateConfirmPassword();

            // Nếu có bất kỳ ô nào sai, chặn không cho submit form lên server
            if (!isNameValid || !isEmailValid || !isPwdValid || !isConfirmValid) {
                event.preventDefault();
            }
        });
    }
});

