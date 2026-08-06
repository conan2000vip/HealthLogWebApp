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

    const chartData = window.weightChartData || { labels: [], values: [], chartMode: "DAY" };
    if (!chartData.labels || chartData.labels.length === 0) return;

    const CHART_DAYS = 7;
    const isSearching =
        document.getElementById("startDateInput")?.value ||
        document.getElementById("endDateInput")?.value;
    let labels = [];
    let data = [];

    if (!isSearching) {
        const dateRange = buildLastNDaysRange(CHART_DAYS, chartData.labels);
        const valueMap = {};
        chartData.labels.forEach((d, i) => {
            valueMap[d] = chartData.values[i];
        });
        labels = dateRange.map(d => formatLabel(d, "DAY"));
        data = dateRange.map(d => {
            const value = valueMap[d];
            return value == null ? null : parseFloat(value);
        });
    } else {
        labels = chartData.labels.map(d => formatLabel(d, chartData.chartMode));
        data = chartData.values.map(v => v == null ? null : parseFloat(v));
    }
    // --- 直近N日間の日付配列（yyyy-MM-dd）を生成する ---
    function buildLastNDaysRange(n, existingLabels) {
        let endDate = new Date();
        endDate.setHours(0, 0, 0, 0);
        if (existingLabels.length) {
            const latest = existingLabels.reduce((a, b) => a > b ? a : b);
            endDate = new Date(latest + "T00:00:00");
        }
        const result = [];
        for (let i = n - 1;i >= 0;i--) {
            const d = new Date(endDate);
            d.setDate(d.getDate() - i);
            result.push(toIsoDate(d));
        }
        return result;
    }

    const styles = getComputedStyle(document.documentElement);
    const primary = styles.getPropertyValue("--wp-primary").trim() || "#14b8a6";
    weightChartInstance = new Chart(canvas, {
        type: "bar",
        data: {
            labels,
            datasets: [
                {
                    data,
                    backgroundColor: hexToRgba(primary, 0.75),
                    borderColor: primary,
                    borderWidth: 1,
                    borderRadius: 8,
                    maxBarThickness: 40,
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

// 直近N日間の日付配列（yyyy-MM-dd）を生成する。
function toIsoDate(d) {
    const yyyy = d.getFullYear();
    const mm = String(d.getMonth() + 1).padStart(2, "0");
    const dd = String(d.getDate()).padStart(2, "0");
    return `${yyyy}-${mm}-${dd}`;
}

function formatLabel(value, mode) {
    if (!value) return "";
    if (mode === "DAY") {
        const d = new Date(value + "T00:00:00");
        const weekdays = ["日", "月", "火", "水", "木", "金", "土"];
        return `${d.getDate()}(${weekdays[d.getDay()]})`;
    }
    if (mode === "WEEK") {
        const d = new Date(value + "T00:00:00");
        return `${d.getMonth() + 1}/${d.getDate()}`;
    }
    if (mode === "MONTH") {
        const month = value.substring(5, 7);
        return `${Number(month)}月`;
    }
    return value;
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
        measuredAtInput.value = measuredAt || currentDateTimeLocal();
        measuredAtInput.max = currentDateTimeLocal();
        weightInput.value = weight;
        // 新規登録の場合はprofileの身長を初期値にする
        if (mode === "create") {
            heightInput.value = window.profileHeight ?? "";
        } else {
            // 編集の場合は登録時の身長をそのまま表示
            heightInput.value = height;
        }
        memoInput.value = memo;
        clearAllErrors();
        overlay.classList.add("is-open");
        measuredAtInput.focus();
    }

    function closeModal() {
        overlay.classList.remove("is-open");
    }

    openBtns.forEach((btn) => btn.addEventListener("click", () => openModal({ mode: "create", measuredAt: btn.dataset.measuredAt || "" })));
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

        const selected = new Date(measuredAtInput.value);
        const now = new Date();
        if (selected > now) {
            showError("measuredAt", "未来の日時は入力できません");
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

    // 現在日時を YYYY-MM-DDTHH:MM 形式で返す（datetime-local 用）
    function currentDateTimeLocal() {
        const d = new Date();
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        const hh = String(d.getHours()).padStart(2, "0");
        const min = String(d.getMinutes()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}T${hh}:${min}`;
    }
}