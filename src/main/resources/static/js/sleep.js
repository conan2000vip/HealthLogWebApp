document.addEventListener("DOMContentLoaded", () => {
    initModal();
});

/* =========================================================
   モーダル（新規登録 / 編集）— sleep ページ専用
   （filter-bar / chart-toggle / diff-badge は common.js 側で
   全画面共通として処理されるため、ここでは扱わない）
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
    const dateInput = document.getElementById("date");
    const sleepTypeInput = document.getElementById("sleepType");
    const startTimeInput = document.getElementById("startTime");
    const endTimeInput = document.getElementById("endTime");
    const memoInput = document.getElementById("memo");

    if (!overlay || !form) return;

    function openModal({
        mode = "create",
        id = "",
        date = "",
        sleepType = "NIGHT",
        startTime = "",
        endTime = "",
        memo = "",
    } = {}) {
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

    // 入力時にエラーをクリア
    [dateInput, startTimeInput, endTimeInput].forEach((input) => {
        if (input) input.addEventListener("input", () => clearError(input.id));
    });

    // バリデーション
    function validateDate() {
        if (!dateInput.value) {
            showError("date", "日付を選択してください");
            return false;
        }
        const selected = new Date(dateInput.value);
        const today = new Date();
        today.setHours(23, 59, 59, 999);
        if (selected > today) {
            showError("date", "未来の日付は選択できません。");
            return false;
        }
        clearError("date");
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

        if (!isDateValid || !isStartValid || !isEndValid) {
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
        ["date", "startTime", "endTime"].forEach(clearError);
    }

    function todayIso() {
        const d = new Date();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        return `${d.getFullYear()}-${mm}-${dd}`;
    }
}

/* =========================================================
   Chart.js — sleep ページ専用（睡眠時間推移データを描画）
   values は分単位（sleepMinutes）で渡ってくる想定
   ========================================================= */
let sleepChartInstance = null;
document.addEventListener("DOMContentLoaded", initChart);
function initChart() {
    const canvas = document.getElementById("sleepChart");
    if (!canvas || typeof Chart === "undefined") return;
    const chartData = window.sleepChartData || { labels: [], values: [] };
    if (!chartData.labels || chartData.labels.length === 0) return;
    const labels = chartData.labels.map(formatShortDate);
    // 分 → 時間（小数）に変換してグラフのY軸を見やすくする
    const data = chartData.values.map((v) => Math.round((parseFloat(v) / 60) * 10) / 10);
    const styles = getComputedStyle(document.documentElement);
    const primary = styles.getPropertyValue("--wp-primary").trim() || "#4caf50";
    sleepChartInstance = new Chart(canvas, {
        type: "line",
        data: {
            labels,
            datasets: [
                {
                    data,
                    borderColor: primary,
                    backgroundColor: hexToRgba(primary, 0.12),
                    borderWidth: 2.5,
                    pointRadius: 4,
                    pointBackgroundColor: primary,
                    pointBorderColor: "#ffffff",
                    pointBorderWidth: 2,
                    tension: 0.35,
                    fill: true,
                },
            ],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (ctx) => formatHoursMinutes(ctx.parsed.y),
                    },
                },
            },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: { color: "#6b7280", font: { size: 11 } },
                },
                y: {
                    grid: { color: "#f1f5f9" },
                    ticks: {
                        color: "#6b7280",
                        font: { size: 11 },
                        callback: (value) => `${value}h`,
                    },
                },
            },
        },
    });
}

function formatHoursMinutes(hoursDecimal) {
    const totalMinutes = Math.round(hoursDecimal * 60);
    const h = Math.floor(totalMinutes / 60);
    const m = totalMinutes % 60;
    return `${h}時間${m}分`;
}

function formatShortDate(isoDate) {
    if (!isoDate) return "";
    const parts = isoDate.split("-");
    if (parts.length < 3) return isoDate;
    return `${parts[1]}-${parts[2]}`;
}

function hexToRgba(hex, alpha) {
    const clean = hex.replace("#", "");
    const bigint = parseInt(clean, 16);
    const r = (bigint >> 16) & 255;
    const g = (bigint >> 8) & 255;
    const b = bigint & 255;
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}