document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }

    initDiffBadges();
    initChart();
    initChartToggle();
    initModal();
    initDeleteConfirm();
});

/* =========================================================
   1) 前日比バッジ（↘ -1.0 / ↗ +1.0）
   テーブルは新しい日付が上（降順）に並んでいる前提。
   各行の体重と、1つ下（＝1つ前の記録）の体重を比較する。
   ========================================================= */
function initDiffBadges() {
    const rows = Array.from(document.querySelectorAll(".weight-table tbody tr"));
    rows.forEach((row, index) => {
        const olderRow = rows[index + 1];
        if (!olderRow) return;

        const current = parseFloat(row.dataset.weight);
        const previous = parseFloat(olderRow.dataset.weight);
        if (Number.isNaN(current) || Number.isNaN(previous)) return;

        const diff = Math.round((current - previous) * 10) / 10;
        if (diff === 0) return;

        const badge = row.querySelector(".diff-badge");
        if (!badge) return;

        const isDown = diff < 0;
        const icon = isDown ? "trending-down" : "trending-up";
        const sign = isDown ? "" : "+";
        badge.classList.add("is-visible", isDown ? "is-down" : "is-up");
        badge.innerHTML = `<i data-lucide="${icon}"></i>${sign}${diff.toFixed(1)}`;
    });

    if (typeof lucide !== "undefined") {
        lucide.createIcons();
    }
}

/* =========================================================
   2) 体重推移グラフ (Chart.js)
   バックエンドで日付ごとに集約済み（同日複数回計測時は最新値）の
   window.weightChartData を描画する。
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
   3) グラフの表示 / 非表示切り替え
   ========================================================= */
function initChartToggle() {
    const toggleBtn = document.getElementById("toggleChartBtn");
    const wrapper = document.getElementById("chartWrapper");
    if (!toggleBtn || !wrapper) return;

    toggleBtn.addEventListener("click", () => {
        const isHidden = wrapper.style.display === "none";
        wrapper.style.display = isHidden ? "" : "none";
        toggleBtn.textContent = isHidden ? "非表示" : "表示";
    });
}

/* =========================================================
   4) モーダル（新規登録 / 編集）
   ========================================================= */
function initModal() {
    const overlay = document.getElementById("weightModalOverlay");
    const openBtns = [
        document.getElementById("openAddModalBtn"),
        document.getElementById("openAddModalBtnEmpty"),
    ].filter(Boolean);
    const closeBtn = document.getElementById("closeModalBtn");
    const cancelBtn = document.getElementById("cancelModalBtn");
    const modalTitle = document.getElementById("weightModalTitle");
    const form = document.getElementById("weightForm");

    const recordId = document.getElementById("recordId");
    const dateInput = document.getElementById("date");
    const weightInput = document.getElementById("weight");
    const heightInput = document.getElementById("height");
    const memoInput = document.getElementById("memo");

    if (!overlay || !form) return;

    function openModal({ mode = "create", id = "", date = "", weight = "", height = "", memo = "" } = {}) {
        modalTitle.textContent = mode === "edit" ? "体重を編集する" : "体重を記録する";
        recordId.value = id;
        dateInput.value = date || todayIso();
        weightInput.value = weight;
        heightInput.value = height;
        memoInput.value = memo;
        clearAllErrors();
        overlay.classList.add("is-open");
        dateInput.focus();
    }

    function closeModal() {
        overlay.classList.remove("is-open");
    }

    openBtns.forEach((btn) => btn.addEventListener("click", () => openModal({ mode: "create" })));
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
                weight: btn.dataset.weight || "",
                height: btn.dataset.height || "",
                memo: btn.dataset.memo || "",
            });
        });
    });

    // 入力時にエラーをクリア
    [dateInput, weightInput, heightInput].forEach((input) => {
        if (input) input.addEventListener("input", () => clearError(input.id));
    });

    // バリデーション
    function validateDate() {
        if (!dateInput.value) {
            showError("date", "日付を入力してください");
            return false;
        }
        clearError("date");
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
        const isDateValid = validateDate();
        const isWeightValid = validateWeight();
        const isHeightValid = validateHeight();

        if (!isDateValid || !isWeightValid || !isHeightValid) {
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
        ["date", "weight", "height"].forEach(clearError);
    }

    function todayIso() {
        const d = new Date();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        return `${d.getFullYear()}-${mm}-${dd}`;
    }
}

/* =========================================================
   5) 削除確認
   ========================================================= */
function initDeleteConfirm() {
    document.querySelectorAll(".delete-form").forEach((form) => {
        form.addEventListener("submit", (event) => {
            const ok = window.confirm("この記録を削除しますか？この操作は取り消せません。");
            if (!ok) event.preventDefault();
        });
    });
}