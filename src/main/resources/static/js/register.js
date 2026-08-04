document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }

    const registerForm = document.getElementById("registerForm");
    const email = document.getElementById("email");
    const password = document.getElementById("password");
    const confirmPassword = document.getElementById("confirmPassword");
    const toggleBtn = document.getElementById("togglePassword");
	const toggleConfirmBtn = document.getElementById("toggleConfirmPassword");

	// パスワードの表示/非表示切り替え
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
    const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;

    // エラーメッセージを表示する関数
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

    // バリデーション関数
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

    //　フォーム送信時のバリデーション
	registerForm.addEventListener("submit", (event) => {
	    const isEmailValid = validateEmail();
	    const isPwdValid = validatePassword();
	    const isConfirmValid = validateConfirmPassword();

	    if (!isEmailValid || !isPwdValid || !isConfirmValid) {
	        event.preventDefault();
	    }
	});
});
