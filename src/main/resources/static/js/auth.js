document.addEventListener("DOMContentLoaded", () => {
    lucide.createIcons();

    document.getElementById("accountName").addEventListener("input", validateAccountName);
    document.getElementById("email").addEventListener("input", validateEmail);

    document.getElementById("password").addEventListener("input", () => {
        validatePassword();
        validateConfirmPassword();
    });

    document.getElementById("confirmPassword").addEventListener("input", validateConfirmPassword);

    document.getElementById("registerForm").addEventListener("submit", (event) => {
        const isValid =
            validateAccountName() &
            validateEmail() &
            validatePassword() &
            validateConfirmPassword();

        if (!isValid) {
            event.preventDefault();
        }
    });
});

const password = document.getElementById("password");
const toggleBtn = document.getElementById("togglePassword");

toggleBtn.addEventListener("click", () => {
    const isHidden = password.type === "password";
    password.type = isHidden ? "text" : "password";
    toggleBtn.innerHTML = `<i data-lucide="${isHidden ? "eye-off" : "eye"}"></i>`;
    lucide.createIcons();
});

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#]).{8,100}$/;

function showError(inputId, message) {
    const input = document.getElementById(inputId);
    const errorBox = document.getElementById(inputId + "Error");

    input.classList.add("invalid");
    errorBox.querySelector("span").textContent = message;
    errorBox.classList.add("show");
}

function clearError(inputId) {
    const input = document.getElementById(inputId);
    const errorBox = document.getElementById(inputId + "Error");

    input.classList.remove("invalid");
    errorBox.querySelector("span").textContent = "";
    errorBox.classList.remove("show");
}

function validateAccountName() {
    const value = document.getElementById("accountName").value.trim();

    if (!value) {
        showError("accountName", "名前を入力してください");
        return false;
    }

    if (value.length > 50) {
        showError("accountName", "名前は50文字以内で入力してください");
        return false;
    }

    clearError("accountName");
    return true;
}

function validateEmail() {
    const value = document.getElementById("email").value.trim();

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
    const value = document.getElementById("password").value;

    if (!value) {
        showError("password", "パスワードを入力してください");
        return false;
    }

    if (!PASSWORD_PATTERN.test(value)) {
        showError("password",
            "パスワードは8〜100文字で、大文字・小文字・数字・特殊記号(@$!%*?&#)をそれぞれ1文字以上含めてください");
        return false;
    }

    clearError("password");
    return true;
}

function validateConfirmPassword() {
    const password = document.getElementById("password").value;
    const confirm = document.getElementById("confirmPassword").value;

    if (!confirm) {
        showError("confirmPassword", "確認用パスワードを入力してください");
        return false;
    }

    if (password !== confirm) {
        showError("confirmPassword", "パスワードが一致しません");
        return false;
    }

    clearError("confirmPassword");
    return true;
}