document.addEventListener("DOMContentLoaded", () => {
    initChart();
    initModal();
});

/* =========================================================
   モーダル（新規登録 / 編集）— sleep ページ専用
   （filter-bar / chart-toggle / diff-badge は common.js 側で
   ========================================================= */
function initModal() {
    const overlay = document.getElementById("sleepModalOverlay");
    const openBtns = [
        document.getElementById("openAddModalBtn"),
        document.getElementById("openAddModalBtnEmpty"),
        document.getElementById("openAddModalBtnFiltered"),
    ].filter(Boolean);
    const closeBtn = document.getElementById("closeModalBtn");
    const cancelBtn = document.getElementById("cancelModalBtn");
    const modalTitle = document.getElementById("sleepModalTitle");
    const form = document.getElementById("sleepForm");
    const recordId = document.getElementById("recordId");
    const dateInput = document.getElementById("recordedDate");
    const sleepTypeInput = document.getElementById("sleepType");
    const startTimeInput = document.getElementById("startTime");
    const endTimeInput = document.getElementById("endTime");
    const memoInput = document.getElementById("memo");

    if (!overlay || !form) return;

    function openModal({ mode = "create", id = "", date = "", sleepType = "NIGHT", startTime = "", endTime = "", memo = "", } = {}) {
        modalTitle.textContent = mode === "edit" ? "睡眠記録を編集する" : "睡眠を記録する";
        recordId.value = id;
        dateInput.value = date || todayIso();
        sleepTypeInput.value = sleepType || "NIGHT";
        startTimeInput.value = startTime;
        endTimeInput.value = endTime;
        memoInput.value = memo;
        clearAllErrors();
        overlay.classList.add("is-open");
        dateInput.focus();
        updateWakeDateHint();
    }

    function closeModal() {
        overlay.classList.remove("is-open");
    }

    openBtns.forEach((btn) =>
        btn.addEventListener("click", () => openModal({ mode: "create", date: btn.dataset.date || "" }))
    );
    if (closeBtn) closeBtn.addEventListener("click", closeModal);
    if (cancelBtn) cancelBtn.addEventListener("click", closeModal);
    overlay.addEventListener("click", (event) => {
        if (event.target === overlay) closeModal();
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && overlay.classList.contains("is-open")) closeModal();
    });

    // 編集ボタン：行のデータ属性からモーダルへ値を渡す
    document.querySelectorAll(".edit-btn").forEach((btn) => {
        btn.addEventListener("click", () => {
            openModal({
                mode: "edit",
                id: btn.dataset.id || "",
                date: btn.dataset.date || "",
                sleepType: btn.dataset.sleepType || "NIGHT",
                startTime: btn.dataset.startTime || "",
                endTime: btn.dataset.endTime || "",
                memo: btn.dataset.memo || "",
            });
        });
    });

    // 就寝時刻と起床時刻から睡眠時間（分）を計算する関数
    function computeSleepMinutes(startVal, endVal) {
        if (!startVal || !endVal) return null;
        const [sh, sm] = startVal.split(":").map(Number);
        const [eh, em] = endVal.split(":").map(Number);
        let startMinutes = sh * 60 + sm;
        let endMinutes = eh * 60 + em;
        if (endMinutes < startMinutes) {
            endMinutes += 24 * 60; // qua ngày hôm sau
        }
        return endMinutes - startMinutes;
    }

    // 入力時にエラーをクリア
    [dateInput, startTimeInput, endTimeInput].forEach((input) => {
        if (input) input.addEventListener("input", () => clearError(input.id));
    });

    [dateInput, startTimeInput, endTimeInput].forEach((input) => {
        if (input) input.addEventListener("input", () => {
            validateDuration();
            updateWakeDateHint();
        });
    });
    if (sleepTypeInput) {
        sleepTypeInput.addEventListener("change", validateDuration);
    }

    // バリデーション
    function validateDuration() {
        const minutes = computeSleepMinutes(startTimeInput.value, endTimeInput.value);
        if (minutes === null) {
            return true;
        }
        if (minutes <= 0) {
            showError("endTime", "起床時刻が正しくありません");
            return false;
        }

        const isNap = sleepTypeInput.value === "NAP";
        const maxMinutes = isNap ? 5 * 60 : 16 * 60;
        if (minutes > maxMinutes) {
            const h = Math.floor(minutes / 60);
            const m = minutes % 60;
            const maxHours = maxMinutes / 60;
            const message = isNap
                ? `昼寝が${maxHours}時間を超えています（現在 ${h}時間${m}分）。長時間の場合は「夜間睡眠」として記録してください`
                : `夜間睡眠の記録は${maxHours}時間以内で入力してください（現在 ${h}時間${m}分）`;
            showError("endTime", message);
            return false;
        }
        clearError("endTime");
        return true;
    }

    function validateDate() {
        if (!dateInput.value) {
            showError("recordedDate", "日付を選択してください");
            return false;
        }
        const selected = new Date(dateInput.value);
        const today = new Date();
        today.setHours(23, 59, 59, 999);
        if (selected > today) {
            showError("recordedDate", "未来の日付は選択できません。");
            return false;
        }
        clearError("recordedDate");
        return true;
    }

    function validateStartTime() {
        if (!startTimeInput.value) {
            showError("startTime", "就寝時刻を入力してください");
            return false;
        }
        clearError("startTime");
        return true;
    }

    function validateEndTime() {
        if (!endTimeInput.value) {
            showError("endTime", "起床時刻を入力してください");
            return false;
        }
        clearError("endTime");
        return true;
    }

    form.addEventListener("submit", (event) => {
        const isDateValid = validateDate();
        const isStartValid = validateStartTime();
        const isEndValid = validateEndTime();
        const isDurationValid = validateDuration();

        if (!isDateValid || !isStartValid || !isEndValid || !isDurationValid) {
            event.preventDefault();
        }
    });

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

    function clearAllErrors() {
        ["recordedDate", "startTime", "endTime"].forEach(clearError);
    }

    function todayIso() {
        const d = new Date();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        return `${d.getFullYear()}-${mm}-${dd}`;
    }

    // ===== 就寝日をまたぐ場合、起床日を自動計算してヒント表示 =====
    const wakeDateHint = document.getElementById("wakeDateHint");

    function updateWakeDateHint() {
        if (!wakeDateHint) return;

        const dateVal = dateInput.value;
        const startVal = startTimeInput.value;
        const endVal = endTimeInput.value;

        if (!dateVal || !startVal || !endVal) {
            wakeDateHint.style.display = "none";
            return;
        }

        const [sh, sm] = startVal.split(":").map(Number);
        const [eh, em] = endVal.split(":").map(Number);
        const startMinutes = sh * 60 + sm;
        const endMinutes = eh * 60 + em;

        if (endMinutes < startMinutes) {
            const sleepDate = new Date(dateVal + "T00:00:00");
            sleepDate.setDate(sleepDate.getDate() + 1);
            const wakeDateStr = formatDateJp(sleepDate);
            wakeDateHint.textContent = `📅 この場合、起床日は ${wakeDateStr} として記録されます`;
            wakeDateHint.style.display = "block";
        } else {
            wakeDateHint.style.display = "none";
        }
    }

    function formatDateJp(dateObj) {
        const y = dateObj.getFullYear();
        const m = String(dateObj.getMonth() + 1).padStart(2, "0");
        const d = String(dateObj.getDate()).padStart(2, "0");
        return `${y}/${m}/${d}`;
    }
}

/* =========================================================
   睡眠時間推移グラフ
   共通の HealthChart を使用する
   ========================================================= */
function initChart() {
    const isSearching = Boolean(
        document.getElementById("startDateInput")?.value ||
        document.getElementById("endDateInput")?.value
    );

    HealthChart.create({
        canvasId: "sleepChart",
        data: window.sleepChartData,
        unit: "時間",
        type: "bar",
        days: 7,
        isSearching: isSearching
    });
}
