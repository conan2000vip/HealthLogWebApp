document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }

    const passwordResetForm = document.getElementById("passwordResetForm");
    const newPassword = document.getElementById("newPassword");
    const confirmPassword = document.getElementById("confirmPassword");
    const toggleNewBtn = document.getElementById("toggleNewPassword");
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
    bindToggle(toggleNewBtn, newPassword, "eyeNewPassword");
    bindToggle(toggleConfirmBtn, confirmPassword, "eyeConfirmPassword");

    const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#]).{8,100}$/;

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

	// エラーメッセージをクリアする関数
    function clearError(inputId) {
        const input = document.getElementById(inputId);
        const errorBox = document.getElementById(inputId + "Error");
        if (!input || !errorBox) return;
        input.classList.remove("invalid");
        const span = errorBox.querySelector("span");
        if (span) span.textContent = "";
        errorBox.classList.remove("show");
    }

    // 新しいパスワードのバリデーション
    function validateNewPassword() {
        if (!newPassword) return true;
        const value = newPassword.value;
        if (!value) {
            showError("newPassword", "新しいパスワードを入力してください");
            return false;
        }
        // vì input thực tế có id="newPassword"
        if (value.length < 8 || value.length > 100) {
            showError("newPassword", "パスワードは8〜100文字で入力してください");
            return false;
        }
        if (!/[A-Z]/.test(value)) {
            showError("newPassword", "英大文字を1文字以上含めてください");
            return false;
        }
        if (!/[a-z]/.test(value)) {
            showError("newPassword", "英小文字を1文字以上含めてください");
            return false;
        }
        if (!/\d/.test(value)) {
            showError("newPassword", "数字を1文字以上含めてください");
            return false;
        }
        if (!/[@$!%*?&#]/.test(value)) {
            showError("newPassword", "特殊記号（@$!%*?&#）を1文字以上含めてください");
            return false;
        }
        clearError("newPassword");
        return true;
    }

	// 確認用パスワードのバリデーション
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
        if (!PASSWORD_PATTERN.test(newPassword.value)) {
            showError("confirmPassword", "新しいパスワードの形式が正しくありません");
            return false;
        }
        clearError("confirmPassword");
        return true;
    }

    // フォーム送信時のバリデーション
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