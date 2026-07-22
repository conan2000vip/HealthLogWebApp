document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }

    const passwordResetForm = document.getElementById("passwordResetForm");
    const newPassword = document.getElementById("newPassword");
    const confirmPassword = document.getElementById("confirmPassword");
    const toggleNewBtn = document.getElementById("toggleNewPassword");
    const toggleConfirmBtn = document.getElementById("toggleConfirmPassword");

    // Thao tác xóa thông báo lỗi khi người dùng gõ phím
    if (newPassword) {
        newPassword.addEventListener("input", () => {
            clearError("newPassword");
            clearError("confirmPassword");
        });
    }
    if (confirmPassword) confirmPassword.addEventListener("input", () => clearError("confirmPassword"));

    // Tính năng ẩn / hiện mật khẩu (dùng chung cho cả 2 ô)
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
    bindToggle(toggleNewBtn, newPassword, "eyeNewPassword");
    bindToggle(toggleConfirmBtn, confirmPassword, "eyeConfirmPassword");

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
    function validateNewPassword() {
        if (!newPassword) return true;
        const value = newPassword.value;
        if (!value) {
            showError("newPassword", "新しいパスワードを入力してください");
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
        clearError("newPassword");
        return true;
    }

	function validateConfirmPassword() {
	    if (!confirmPassword || !newPassword) return true;
	    const value = confirmPassword.value;
	    if (!value) {
	        showError("confirmPassword", "確認用パスワードを入力してください");
	        return false;
	    }
	    if (newPassword.value !== value) {
	        showError("confirmPassword", "パスワードが一致しません");
	        return false;
	    }
	    clearError("confirmPassword");
	    return true;
	}

    // Xử lý sự kiện gửi form
    if (passwordResetForm) {
        passwordResetForm.addEventListener("submit", (event) => {
            const isNewPwdValid = validateNewPassword();
            const isConfirmValid = validateConfirmPassword();

            if (!isNewPwdValid || !isConfirmValid) {
                event.preventDefault();
            }
        });
    }
});