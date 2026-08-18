document.addEventListener("DOMContentLoaded", () => {
    initChart();
    initModal();
});

/* =========================================================
   1) 体重推移グラフ
   共通の health-chart.js を使用する
   （★変更なし。元のコードのまま）
   ========================================================= */
function initChart() {
    const isSearching = Boolean(
        document.getElementById("startDateInput")?.value ||
        document.getElementById("endDateInput")?.value
    );

    const chart = HealthChart.create({
        canvasId: "weightChart",
        data: window.weightChartData,
        unit: "kg",
        type: "bar",
        days: 7,
        isSearching: isSearching
    });

    const wrapper = document.getElementById("chartWrapper");
    const chartUrl = wrapper?.closest(".chart-card")?.dataset.chartUrl;
    const from = window.weightChartData?.from;
    const to = window.weightChartData?.to;

    if (chart && wrapper && chartUrl && from && to) {
        initChartSwipe({ chart, wrapperEl: wrapper, chartUrl, initialFrom: from, initialTo: to });
    }
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

    // ★変更：表示用は日付のみのinput、送信用hiddenは従来通りdatetime-local文字列
    const measuredAtDateInput = document.getElementById("measuredAtDate");
    const measuredAtInput = document.getElementById("measuredAt"); // hidden

    const weightInput = document.getElementById("weight");
    const heightInput = document.getElementById("height");
    const memoInput = document.getElementById("memo");

    if (!overlay || !form) return;

    // ★追加：現在開いているモーダルが create か edit かを保持
    let currentMode = "create";

    // 現在日時を YYYY-MM-DDTHH:MM 形式で返す（hidden input送信用）
    function currentDateTimeLocal() {
        const d = new Date();
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        const hh = String(d.getHours()).padStart(2, "0");
        const min = String(d.getMinutes()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}T${hh}:${min}`;
    }

    // 現在日付を YYYY-MM-DD 形式で返す（date input用）
    function currentDateOnly() {
        return currentDateTimeLocal().slice(0, 10);
    }

    // date input の値 + 現在時刻 を合成して hidden(measuredAt) に反映
    function syncMeasuredAtFromDate() {
        if (!measuredAtDateInput.value) {
            measuredAtInput.value = "";
            return;
        }
        const now = new Date();
        const hh = String(now.getHours()).padStart(2, "0");
        const min = String(now.getMinutes()).padStart(2, "0");
        measuredAtInput.value = `${measuredAtDateInput.value}T${hh}:${min}`;
    }

    function openModal({ mode = "create", id = "", measuredAt = "", weight = "", height = "", memo = "" } = {}) {
        modalTitle.textContent = mode === "edit" ? "体重を編集する" : "体重を記録する";
        currentMode = mode; // ★追加
        recordId.value = id;

        // ★変更：日付部分だけを date input にセット。未来日は選べないようmaxを設定
        const initialDateTime = measuredAt || currentDateTimeLocal();
        measuredAtDateInput.value = initialDateTime.slice(0, 10);
        measuredAtDateInput.max = currentDateOnly();

        // hidden側は「編集時は元の時刻を保持／新規時はその場で現在時刻」を初期セット
        measuredAtInput.value = initialDateTime;

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
        measuredAtDateInput.focus();
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

    // ★変更：日付が変更されたら、その時点の現在時刻と合成してhiddenへ反映
    measuredAtDateInput.addEventListener("change", () => {
        syncMeasuredAtFromDate();
        clearError("measuredAt");
    });

    // 入力時にエラーをクリア（体重・身長は従来通り）
    [weightInput, heightInput].forEach((input) => {
        if (input) input.addEventListener("input", () => clearError(input.id));
    });

    // ★変更：バリデーション対象は「日付」のみ。時刻は自動付与のため未来チェック対象外
    function validateMeasuredAt() {
        if (!measuredAtDateInput.value) {
            showError("measuredAt", "日付を入力してください");
            return false;
        }
        const selectedDate = new Date(measuredAtDateInput.value + "T00:00:00");
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        if (selectedDate > today) {
            showError("measuredAt", "未来の日付は入力できません");
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
            return;
        }
        if (currentMode === "create") {
            syncMeasuredAtFromDate();
        }
    });

    function showError(inputId, message) {
        // ★変更：measuredAtのエラーは実際に見えている#measuredAtDateに付与する
        const errorBox = document.getElementById(inputId + "Error");
        const targetInput = inputId === "measuredAt" ? measuredAtDateInput : document.getElementById(inputId);
        if (!targetInput || !errorBox) return;
        targetInput.classList.add("invalid");
        const span = errorBox.querySelector("span");
        if (span) span.textContent = message;
        errorBox.classList.add("show");
    }

    function clearError(inputId) {
        const errorBox = document.getElementById(inputId + "Error");
        const targetInput = inputId === "measuredAt" ? measuredAtDateInput : document.getElementById(inputId);
        if (!targetInput || !errorBox) return;
        targetInput.classList.remove("invalid");
        const span = errorBox.querySelector("span");
        if (span) span.textContent = "";
        errorBox.classList.remove("show");
    }

    function clearAllErrors() {
        ["measuredAt", "weight", "height"].forEach(clearError);
    }
}