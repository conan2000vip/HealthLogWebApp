//IIFE (Immediately Invoked Function Expression) dropdown menu
(function () {
    const toggle = document.getElementById("userMenuToggle");
    const dropdown = document.getElementById("userMenuDropdown");
    if (!toggle || !dropdown) return;

    toggle.addEventListener("click", function (e) {
        e.stopPropagation();
        const isOpen = dropdown.classList.toggle("is-open");
        toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
    });

    document.addEventListener("click", function (e) {
        if (!dropdown.contains(e.target) && !toggle.contains(e.target)) {
            dropdown.classList.remove("is-open");
            toggle.setAttribute("aria-expanded", "false");
        }
    });
})();

(function () {

    const menuToggle = document.getElementById("menuToggle");
    const nav = document.querySelector(".app-header__nav");

    if (!menuToggle || !nav) return;

    menuToggle.addEventListener("click", function (e) {
        e.stopPropagation();
        nav.classList.toggle("open");
    });

    document.addEventListener("click", function (e) {
        if (!nav.contains(e.target) && !menuToggle.contains(e.target)) {
            nav.classList.remove("open");
        }
    });

    document.querySelectorAll(".app-header__nav-item").forEach(item => {
        item.addEventListener("click", function () {
            nav.classList.remove("open");
        });
    });

    window.addEventListener("resize", function () {
        if (window.innerWidth > 1100) {
            nav.classList.remove("open");
        }
    });
})();

/* =========================================================
   汎用: 削除確認モーダル（全画面共通）
   使い方: HTML側で class="delete-form" を持つ form があれば自動的に有効化
   ========================================================= */
function initDeleteConfirm() {
    const overlay = document.getElementById("deleteModalOverlay");
    if (!overlay) return; // フラグメントを読み込んでいないページはスキップ

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
   HTML側で data-auto-hide="true" があれば10秒後にフェードアウト
   ========================================================= */
function initAutoHideAlerts() {
    document.querySelectorAll('.alert[data-auto-hide="true"]').forEach((alert) => {
        setTimeout(() => {
            alert.style.transition = "opacity 0.3s ease, transform 0.3s ease";
            alert.style.opacity = "0";
            alert.style.transform = "translateY(-6px)";
            setTimeout(() => alert.remove(), 300);
        }, 10000);
    });
}

/* =========================================================
   汎用: 期間フィルター（全画面共通）
   使い方: <form id="filterForm"> 内の <input type="date"> を
   選択/Enterで自動submit。weight/sleep/water/step 共通で使う。
   ========================================================= */
function initFilterForm() {
    const filterForm = document.getElementById("filterForm");
    if (!filterForm) return;
    filterForm.querySelectorAll('input[type="date"]').forEach((input) => {
        input.addEventListener("change", () => filterForm.submit());
        input.addEventListener("keydown", (e) => {
            if (e.key === "Enter") {
                e.preventDefault();
                filterForm.submit();
            }
        });
    });
}

/* =========================================================
   汎用: グラフの表示/非表示切り替え（全画面共通）
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
   汎用: 前回比バッジ（↘ -1.0 / ↗ +1.0）（全画面共通）
   使い方: 履歴テーブルに data-history-table 属性を付ける。
   各行は th:data-value="${record.xxx}" のような形で
   data-value 属性に比較したい数値を入れる（新しい日付が上＝降順前提）。
   ========================================================= */
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

document.addEventListener("DOMContentLoaded", () => {
    if (typeof lucide !== "undefined") lucide.createIcons();
    initDeleteConfirm();
    initAutoHideAlerts();
    initFilterForm();
    initChartToggle();
    initDiffBadges();
	initPositiveNumberInputs();
});

/* =========================================================
   汎用: 数値入力で負の値・不正な文字を入力させない（全画面共通）
   対象: min="0" が設定された <input type="number">
   ========================================================= */
function initPositiveNumberInputs() {
    const inputs = document.querySelectorAll('input[type="number"][min="0"]');
    inputs.forEach((input) => {
        const allowDecimal = input.hasAttribute("step") && input.step !== "1";
        // 許可するキー（数字 + 制御キー + 小数点があるフィールドのみ "."）
        input.addEventListener("keydown", (e) => {
            const key = e.key;
            // Backspace, Delete, Tab, 矢印キーなどはそのまま許可
            if (
                key === "Backspace" || key === "Delete" || key === "Tab" ||
                key === "ArrowLeft" || key === "ArrowRight" ||
                key === "ArrowUp" || key === "ArrowDown" ||
                key === "Home" || key === "End" ||
                (e.ctrlKey || e.metaKey) // Ctrl+C, Ctrl+V, Ctrl+A などは許可
            ) {
                return;
            }
            // 数字はOK
            if (/^[0-9]$/.test(key)) {
                return;
            }
            // 小数点はステップが小数対応のフィールドのみ、かつ1回だけ許可
            if (allowDecimal && key === "." && !input.value.includes(".")) {
                return;
            }
            // それ以外（文字、-、+、eなど）は全てブロック
            e.preventDefault();
        });

        // ペースト対策：数値以外が含まれる場合は除去
        input.addEventListener("input", () => {
            let value = input.value;
            if (allowDecimal) {
                // 数字と小数点のみ残す（先頭の小数点は除去、2個目以降の小数点も除去）
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
            if (input.value !== "" && Number(input.value) < 0) {
                input.value = 0;
            }
        });
    });
}