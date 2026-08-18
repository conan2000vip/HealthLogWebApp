document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }

    const loginForm = document.getElementById("loginForm");
    const email = document.getElementById("email");
    const password = document.getElementById("password");
    const toggleBtn = document.getElementById("togglePassword");

    // ★追加：確認メール再送フォーム関連
    const backendError = document.getElementById("backendError");
    const resendForm = document.getElementById("resendVerificationForm");

    const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;

    // ★変更：メール・パスワードいずれかが編集されたら、
    //   直前のログイン失敗時の状態（エラーバナー＋確認メール再送ボタン）は
    //   もう現在の入力内容と対応しなくなるため、まとめて非表示にする。
    //   （再度ログインを試みて失敗すれば、サーバー側から正しい状態で再表示される）
    function hideStaleLoginFailureState() {
        if (backendError) {
            backendError.style.display = "none";
        }
        if (resendForm) {
            resendForm.style.display = "none";
        }
    }

    if (email) {
        email.addEventListener("input", () => {
            validateEmail();
            hideStaleLoginFailureState(); // ★変更
        });
    }
    if (password) {
        password.addEventListener("input", () => {
            validatePassword();
            hideStaleLoginFailureState(); // ★変更
        });
    }

    // パスワードの表示/非表示切り替え
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