document.addEventListener("DOMContentLoaded", () => {
    initChart();
    initModal();
});

/* =========================================================
   1) 体重推移グラフ (Chart.js)
   バックエンドで日付ごとに集約済みの window.weightChartData を描画する。
   ========================================================= */
let weightChartInstance = null;

function initChart() {
    const canvas = document.getElementById("weightChart");
    if (!canvas || typeof Chart === "undefined") return;

    const chartData = window.weightChartData || { labels: [], values: [] };
    if (!chartData.labels || chartData.labels.length === 0) return;

    const labels = chartData.labels.map(formatShortDate);
    const data = chartData.values.map((v) => parseFloat(v));

    const styles = getComputedStyle(document.documentElement);
    const primary = styles.getPropertyValue("--wp-primary").trim() || "#14b8a6";

    weightChartInstance = new Chart(canvas, {
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
                        label: (ctx) => `${ctx.parsed.y} kg`,
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
                    ticks: { color: "#6b7280", font: { size: 11 } },
                },
            },
        },
    });
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

/* =========================================================
   2) モーダル（新規登録 / 編集）— weight ページ専用
   （diff-badge / chart-toggle / delete-confirm は common.js 側で
   全画面共通として処理されるため、ここでは扱わない）
   ========================================================= */
function initModal() {
    const overlay = document.getElementById("weightModalOverlay");
    const openBtns = [
        document.getElementById("openAddModalBtn"),
        document.getElementById("openAddModalBtnEmpty"),
        document.getElementById("openAddModalBtnFiltered"),
    ].filter(Boolean);
    const closeBtn = document.getElementById("closeModalBtn");
    const cancelBtn = document.getElementById("cancelModalBtn");
    const modalTitle = document.getElementById("weightModalTitle");
    const form = document.getElementById("weightForm");

    const recordId = document.getElementById("recordId");
    const measuredAtInput = document.getElementById("measuredAt");
    const weightInput = document.getElementById("weight");
    const heightInput = document.getElementById("height");
    const memoInput = document.getElementById("memo");

    if (!overlay || !form) return;

    function openModal({ mode = "create", id = "", measuredAt = "", weight = "", height = "", memo = "" } = {}) {
        modalTitle.textContent = mode === "edit" ? "体重を編集する" : "体重を記録する";
        recordId.value = id;
        measuredAtInput.value = normalizeToDateTimeLocal(measuredAt) || todayIso();
        weightInput.value = weight;
        heightInput.value = height;
        memoInput.value = memo;
        clearAllErrors();
        overlay.classList.add("is-open");
        measuredAtInput.focus();
    }

    function closeModal() {
        overlay.classList.remove("is-open");
    }

    openBtns.forEach((btn) =>
        btn.addEventListener("click", () => openModal({ mode: "create", measuredAt: btn.dataset.measuredAt || "" }))
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
                measuredAt: btn.dataset.measuredAt || "",
                weight: btn.dataset.weight || "",
                height: btn.dataset.height || "",
                memo: btn.dataset.memo || "",
            });
        });
    });

    // 入力時にエラーをクリア
    [measuredAtInput, weightInput, heightInput].forEach((input) => {
        if (input) input.addEventListener("input", () => clearError(input.id));
    });

    // バリデーション
    function validateMeasuredAt() {
        if (!measuredAtInput.value) {
            showError("measuredAt", "日時を入力してください");
            return false;
        }
        clearError("measuredAt");
        return true;
    }

    function validateWeight() {
        const value = weightInput.value;
        if (!value) {
            showError("weight", "体重を入力してください");
            return false;
        }
        const num = parseFloat(value);
        if (Number.isNaN(num) || num < 1 || num > 500) {
            showError("weight", "体重は1〜500kgの範囲で入力してください");
            return false;
        }
        clearError("weight");
        return true;
    }

    function validateHeight() {
        const value = heightInput.value;
        if (!value) {
            clearError("height");
            return true;
        }
        const num = parseFloat(value);
        if (Number.isNaN(num) || num < 30 || num > 250) {
            showError("height", "身長は30〜250cmの範囲で入力してください");
            return false;
        }
        clearError("height");
        return true;
    }

    form.addEventListener("submit", (event) => {
        const isMeasuredAtValid = validateMeasuredAt();
        const isWeightValid = validateWeight();
        const isHeightValid = validateHeight();

        if (!isMeasuredAtValid || !isWeightValid || !isHeightValid) {
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
        ["measuredAt", "weight", "height"].forEach(clearError);
    }

    // datetime-local input は "yyyy-MM-ddTHH:mm" 形式のみ受け付ける。
    // filterStartDate 等、日付のみ（yyyy-MM-dd）で渡ってきた場合は 00:00 を補完する。
    function normalizeToDateTimeLocal(value) {
        if (!value) return "";
        if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
            return `${value}T00:00`;
        }
        // "yyyy-MM-ddTHH:mm:ss" など秒以下が付いている場合は切り詰める
        return value.slice(0, 16);
    }

    function todayIso() {
        const d = new Date();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        const hh = String(d.getHours()).padStart(2, "0");
        const mi = String(d.getMinutes()).padStart(2, "0");
        return `${d.getFullYear()}-${mm}-${dd}T${hh}:${mi}`;
    }
}