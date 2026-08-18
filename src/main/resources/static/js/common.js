//IIFE (Immediately Invoked Function Expression) dropdown menu
(function() {
    const toggle = document.getElementById("userMenuToggle");
    const dropdown = document.getElementById("userMenuDropdown");
    if (!toggle || !dropdown) return;

    toggle.addEventListener("click", function(e) {
        e.stopPropagation();
        const isOpen = dropdown.classList.toggle("is-open");
        toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
    });

    document.addEventListener("click", function(e) {
        if (!dropdown.contains(e.target) && !toggle.contains(e.target)) {
            dropdown.classList.remove("is-open");
            toggle.setAttribute("aria-expanded", "false");
        }
    });
})();

(function() {
    const menuToggle = document.getElementById("menuToggle");
    const nav = document.querySelector(".app-header__nav");

    if (!menuToggle || !nav) return;

    menuToggle.addEventListener("click", function(e) {
        e.stopPropagation();
        nav.classList.toggle("open");
    });

    document.addEventListener("click", function(e) {
        if (!nav.contains(e.target) && !menuToggle.contains(e.target)) {
            nav.classList.remove("open");
        }
    });

    document.querySelectorAll(".app-header__nav-item").forEach((item) => {
        item.addEventListener("click", function() {
            nav.classList.remove("open");
        });
    });

    window.addEventListener("resize", function() {
        if (window.innerWidth > 1280) {
            nav.classList.remove("open");
        }
    });
})();

/* =========================================================
   汎用: フィードバック「すべて見る」トグル（全画面共通）
   ========================================================= */
function initFeedbackToggle() {
    const toggleBtn = document.getElementById("feedbackToggleBtn");
    const section = document.getElementById("feedbackSection");
    if (!toggleBtn || !section) return;

    let expanded = false;
    toggleBtn.addEventListener("click", () => {
        expanded = !expanded;
        section
            .querySelectorAll(".feedback-card.is-hidden, .feedback-card")
            .forEach((card, index) => {
                if (index >= 3) {
                    card.classList.toggle("is-hidden", !expanded);
                }
            });
        toggleBtn.textContent = expanded
            ? "閉じる"
            : `すべて見る (${section.querySelectorAll(".feedback-card").length}件)`;
    });
}

/* =========================================================
   汎用: 削除確認モーダル（全画面共通）
   使い方: HTML側で class="delete-form" を持つ form があれば自動的に有効化
   ========================================================= */
function initDeleteConfirm() {
    const overlay = document.getElementById("deleteModalOverlay");
    if (!overlay) return;

    const closeBtn = document.getElementById("closeDeleteModalBtn");
    const cancelBtn = document.getElementById("cancelDeleteBtn");
    const confirmBtn = document.getElementById("confirmDeleteBtn");
    const messageEl = overlay.querySelector(".modal__message");

    let pendingForm = null;

    function openDeleteModal(form, customMessage) {
        pendingForm = form;
        if (messageEl && customMessage) {
            messageEl.textContent = customMessage;
        }
        overlay.classList.add("is-open");
    }

    function closeDeleteModal() {
        overlay.classList.remove("is-open");
        pendingForm = null;
    }

    document.querySelectorAll(".delete-form").forEach((form) => {
        form.addEventListener("submit", (event) => {
            event.preventDefault();
            const msg = form.dataset.confirmMessage || null;
            openDeleteModal(form, msg);
        });
    });

    confirmBtn?.addEventListener("click", () => {
        if (pendingForm) pendingForm.submit();
        closeDeleteModal();
    });

    closeBtn?.addEventListener("click", closeDeleteModal);
    cancelBtn?.addEventListener("click", closeDeleteModal);

    overlay.addEventListener("click", (event) => {
        if (event.target === overlay) closeDeleteModal();
    });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && overlay.classList.contains("is-open")) {
            closeDeleteModal();
        }
    });
}

/* =========================================================
   汎用: Alert 自動非表示（全画面共通）
   ========================================================= */
function initAutoHideAlerts() {
    document
        .querySelectorAll('.alert[data-auto-hide="true"]')
        .forEach((alert) => {
            setTimeout(() => {
                alert.style.transition = "opacity 0.3s ease, transform 0.3s ease";
                alert.style.opacity = "0";
                alert.style.transform = "translateY(-6px)";
                setTimeout(() => alert.remove(), 300);
            }, 10000);
        });
}

window.HealthChart = (() => {
    const DEFAULT_DAYS = 7;
    function create({
        canvasId,
        data,
        unit = "",
        type = "bar",
        days = DEFAULT_DAYS,
        isSearching = false,
    }) {
        const canvas = document.getElementById(canvasId);
        if (!canvas || typeof Chart === "undefined") {
            return;
        }
        if (!data || !data.labels || data.labels.length === 0) {
            return;
        }
        let labels = [];
        let values = [];
        if (!isSearching) {
            const dateRange = buildLastNDaysRange(days);
            const valueMap = {};
            data.labels.forEach((date, index) => {
                valueMap[date] = data.values[index];
            });
            labels = dateRange.map((date) => formatLabel(date, "DAY"));
            values = dateRange.map((date) => {
                const value = valueMap[date];
                return value == null ? null : parseFloat(value);
            });
        } else {
            labels = data.labels.map((date) => formatLabel(date, data.chartMode));
            values = data.values.map((value) =>
                value == null ? null : parseFloat(value)
            );
        }
        const styles = getComputedStyle(document.documentElement);
        const primary = styles.getPropertyValue("--chart-primary").trim() || "#4caf50";
        return new Chart(canvas, {
            type: type,
            data: {
                labels: labels,
                datasets: [
                    {
                        data: values,
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
                            label: (ctx) => `${ctx.parsed.y} ${unit}`,
                        },
                    },
                },
                scales: {
                    x: { grid: { display: false } },
                    y: { grid: { color: "#f1f5f9" } },
                },
            },
        });
    }

    function buildLastNDaysRange(n) {
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        const result = [];
        for (let i = n - 1;i >= 0;i--) {
            const date = new Date(today);
            date.setDate(date.getDate() - i);
            result.push(toIsoDate(date));
        }
        return result;
    }

    function toIsoDate(date) {
        const yyyy = date.getFullYear();
        const mm = String(date.getMonth() + 1).padStart(2, "0");
        const dd = String(date.getDate()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}`;
    }

    function formatLabel(value, mode) {
        if (!value) return "";
        if (mode === "DAY") {
            const date = new Date(value + "T00:00:00");
            const weekdays = ["日", "月", "火", "水", "木", "金", "土"];
            return `${date.getDate()}(${weekdays[date.getDay()]})`;
        }
        if (mode === "WEEK") {
            const date = new Date(value + "T00:00:00");
            return `${date.getMonth() + 1}/${date.getDate()}`;
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
    return { create };
})();

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

function initChartSwipe({ chart, wrapperEl, chartUrl, initialFrom, initialTo }) {
    if (!chart || !wrapperEl || !chartUrl || !initialFrom || !initialTo) return;

    let currentFrom = new Date(initialFrom + "T00:00:00");
    let currentTo = new Date(initialTo + "T00:00:00");

    function toIso(d) {
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, "0");
        const dd = String(d.getDate()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}`;
    }

    async function loadRange(from, to) {
        const sep = chartUrl.includes("?") ? "&" : "?";
        const url = `${chartUrl}${sep}startDate=${toIso(from)}&endDate=${toIso(to)}`;
        let res;
        try {
            res = await fetch(url, { headers: { Accept: "application/json" } });
        } catch (e) {
            return;
        }
        if (!res.ok) return;
        const data = await res.json();
        currentFrom = from;
        currentTo = to;
        chart.data.labels = data.labels.map((l) => HealthChart.formatLabel(l, data.chartMode));
        chart.data.datasets[0].data = data.values.map((v) => (v == null ? null : parseFloat(v)));
        chart.update();
    }

    let touchStartX = null;
    wrapperEl.addEventListener("touchstart", (e) => {
        touchStartX = e.touches[0].clientX;
    }, { passive: true });

    wrapperEl.addEventListener("touchend", (e) => {
        if (touchStartX === null) return;
        const dx = e.changedTouches[0].clientX - touchStartX;
        touchStartX = null;
        if (Math.abs(dx) < 40) return;

        const spanDays = Math.round((currentTo - currentFrom) / 86400000) + 1;
        const from = new Date(currentFrom);
        const to = new Date(currentTo);

        if (dx < 0) {
            from.setDate(from.getDate() + spanDays);
            to.setDate(to.getDate() + spanDays);
            if (from > new Date()) return;
        } else {
            from.setDate(from.getDate() - spanDays);
            to.setDate(to.getDate() - spanDays);
        }
        loadRange(from, to);
    }, { passive: true });
}

function initDiffBadges() {
    const table = document.querySelector("[data-history-table]");
    if (!table) return;
    const rows = Array.from(table.querySelectorAll("tbody tr"));
    rows.forEach((row, index) => {
        const olderRow = rows[index + 1];
        if (!olderRow) return;
        const current = parseFloat(row.dataset.value);
        const previous = parseFloat(olderRow.dataset.value);
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

function initMemoExpand() {
    document.querySelectorAll(".memo-cell").forEach((cell) => {
        if (!cell.textContent.trim()) return;
        cell.addEventListener("click", () => {
            cell.classList.toggle("is-expanded");
        });
    });

    document.addEventListener("click", (e) => {
        if (!e.target.closest(".memo-cell")) {
            document.querySelectorAll(".memo-cell.is-expanded").forEach((cell) => {
                cell.classList.remove("is-expanded");
            });
        }
    });
}

document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") lucide.createIcons();
    initDeleteConfirm();
    initAutoHideAlerts();
    initChartToggle();
    initDiffBadges();
    initPositiveNumberInputs();
    initFeedbackToggle();
    initMemoExpand();
    initFilterForm();
});

/* =========================================================
   汎用: 数値入力で負の値・不正な文字を入力させない（全画面共通）
   対象: すべての <input type="number">（＋ #height）

   ★修正履歴：
   以前は `input[type="number"][min="0"]` のみを対象にしていたため、
   min="0.1"（目標体重）/ min="1"（水分目標・歩数目標・水分記録・歩数記録）/
   min="0.5"（睡眠目標）など、min="0" 以外の項目がすべて対象から漏れており、
   これらの欄にマイナス値・文字（e, +, - 等）が入力できてしまっていた。
   → type="number" の入力欄はすべて対象にするよう修正。
   ========================================================= */
function initPositiveNumberInputs() {
    const inputs = document.querySelectorAll(
        'input[type="number"], #height'
    );

    inputs.forEach((input) => {
        const isHeight = input.id === "height";

        const allowDecimal =
            isHeight ||
            (input.hasAttribute("step") && input.step !== "1");

        input.addEventListener("keydown", (e) => {
            const key = e.key;

            if (
                key === "Backspace" ||
                key === "Delete" ||
                key === "Tab" ||
                key === "ArrowLeft" ||
                key === "ArrowRight" ||
                key === "ArrowUp" ||
                key === "ArrowDown" ||
                key === "Home" ||
                key === "End" ||
                e.ctrlKey ||
                e.metaKey
            ) {
                return;
            }

            if (/^[0-9]$/.test(key)) {
                return;
            }

            if (
                allowDecimal &&
                key === "." &&
                !input.value.includes(".")
            ) {
                return;
            }

            e.preventDefault();
        });

        input.addEventListener("input", () => {
            let value = input.value;

            if (allowDecimal) {
                value = value.replace(/[^0-9.]/g, "");
                const parts = value.split(".");
                if (parts.length > 2) {
                    value = parts[0] + "." + parts.slice(1).join("");
                }
            } else {
                value = value.replace(/[^0-9]/g, "");
            }

            if (value !== input.value) {
                input.value = value;
            }
        });

        input.addEventListener("paste", (e) => {
            e.preventDefault();
            const pasted = e.clipboardData.getData("text");
            let value;

            if (allowDecimal) {
                value = pasted.replace(/[^0-9.]/g, "");
                const parts = value.split(".");
                if (parts.length > 2) {
                    value = parts[0] + "." + parts.slice(1).join("");
                }
            } else {
                value = pasted.replace(/[^0-9]/g, "");
            }

            input.value = value;
            input.dispatchEvent(new Event("input", { bubbles: true }));
        });

        if (isHeight) {
            const form = input.closest("form");

            // ★変更：範囲を 30〜250 → 10〜350 に統一（weight.js側と合わせる）
            const HEIGHT_MIN = 10;
            const HEIGHT_MAX = 350;

            if (form) {
                form.addEventListener("submit", (e) => {
                    const value = input.value.trim();

                    if (value === "") {
                        return;
                    }

                    const height = Number(value);

                    if (
                        !Number.isFinite(height) ||
                        height < HEIGHT_MIN ||
                        height > HEIGHT_MAX
                    ) {
                        e.preventDefault();
                        showHeightError(`身長は${HEIGHT_MIN}～${HEIGHT_MAX}cmの範囲で入力してください。`);
                        input.focus();
                        return;
                    }

                    if (!/^\d+(\.\d)?$/.test(value)) {
                        e.preventDefault();
                        showHeightError("身長は小数点以下1桁まで入力してください。");
                        input.focus();
                    }
                });
            }
        }
    });
}

function showHeightError(message) {
    const input = document.getElementById("height");
    if (!input) return;

    const group = input.closest(".input-group");
    if (!group) return;

    let error = group.querySelector(".js-height-error");
    if (!error) {
        error = document.createElement("span");
        error.className = "field-error show js-height-error";
        group.appendChild(error);
    }

    error.textContent = message;
}

//Business Error + FieldError
function clearBusinessError() {
    document.querySelectorAll(".error-message").forEach((error) => {
        error.remove();
    });
}
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("input, textarea").forEach((el) => {
        el.addEventListener("input", clearBusinessError);
    });
    document.querySelectorAll("select").forEach((el) => {
        el.addEventListener("change", clearBusinessError);
    });
});

function clearFieldError(event) {
    const group = event.target.closest(".input-group");
    if (!group) return;

    const error = group.querySelector(".field-error");
    if (error) {
        error.classList.remove("show");
        const span = error.querySelector("span");
        if (span) span.textContent = "";
    }
}
document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("input, textarea, select").forEach((el) => {
        el.addEventListener("input", clearFieldError);
        el.addEventListener("change", clearFieldError);
    });
});

/* =========================================================
   汎用: クイック期間フィルター（1日/1週/1ヶ月/6ヶ月/1年）
   ========================================================= */
function initFilterForm() {
    const bar = document.getElementById("quickRangeBar");
    const form = document.getElementById("filterForm");
    const startInput = document.getElementById("startDateInput");
    const endInput = document.getElementById("endDateInput");
    if (!bar || !form || !startInput || !endInput) return;

    function toIso(date) {
        const yyyy = date.getFullYear();
        const mm = String(date.getMonth() + 1).padStart(2, "0");
        const dd = String(date.getDate()).padStart(2, "0");
        return `${yyyy}-${mm}-${dd}`;
    }

    function computeRange(code) {
        const to = new Date();
        to.setHours(0, 0, 0, 0);
        const from = new Date(to);
        switch (code) {
            case "1D": break;
            case "1W": from.setDate(from.getDate() - 6); break;
            case "1M": from.setMonth(from.getMonth() - 1); from.setDate(from.getDate() + 1); break;
            case "6M": from.setMonth(from.getMonth() - 6); from.setDate(from.getDate() + 1); break;
            case "1Y": from.setFullYear(from.getFullYear() - 1); from.setDate(from.getDate() + 1); break;
        }
        return [from, to];
    }

    function syncActiveButton() {
        bar.querySelectorAll(".quick-btn").forEach((b) => b.classList.remove("is-active"));
        if (!startInput.value || !endInput.value) return;
        bar.querySelectorAll(".quick-btn").forEach((btn) => {
            const [from, to] = computeRange(btn.dataset.range);
            if (toIso(from) === startInput.value && toIso(to) === endInput.value) {
                btn.classList.add("is-active");
            }
        });
    }
    bar.querySelectorAll(".quick-btn").forEach((btn) => {
        btn.addEventListener("click", () => {
            const [from, to] = computeRange(btn.dataset.range);
            startInput.value = toIso(from);
            endInput.value = toIso(to);
            form.submit();
        });
    });
    syncActiveButton();
}