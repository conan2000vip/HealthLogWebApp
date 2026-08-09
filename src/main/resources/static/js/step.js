document.addEventListener("DOMContentLoaded", () => {
	initChart();
	initModal();
});

/* =========================================================
1. Step Chart / 歩数推移グラフ
   Uses shared HealthChart / 共通のHealthChartを使用
========================================================= */
function initChart() {
	const isSearching = Boolean(document.getElementById("startDateInput")?.value || document.getElementById("endDateInput")?.value);

	HealthChart.create({
		canvasId: "stepChart",
		data: window.stepChartData,
		unit: "歩",
		type: "bar",
		days: 7,
		isSearching: isSearching
	});
}

/* =========================================================
2. Add / Edit Modal / 新規登録・編集モーダル
========================================================= */
function initModal() {
	const overlay = document.getElementById("stepModalOverlay");
	const openBtns = [
		document.getElementById("openAddModalBtn"),
		document.getElementById("openAddModalBtnEmpty"),
		document.getElementById("openAddModalBtnFiltered")
	].filter(Boolean);
	const closeBtn = document.getElementById("closeModalBtn");
	const cancelBtn = document.getElementById("cancelModalBtn");
	const modalTitle = document.getElementById("stepModalTitle");
	const form = document.getElementById("stepForm");

	const recordId = document.getElementById("recordId");
	const recordedDateInput = document.getElementById("recordedDate");
	const stepsInput = document.getElementById("steps");
	const memoInput = document.getElementById("memo");

	if (!overlay || !form) return;

	function openModal({ mode = "create", id = "", date = "", steps = "", memo = "" } = {}) {
		modalTitle.textContent = mode === "edit" ? "歩数を編集する" : "歩数を記録する";
		recordId.value = id;
		recordedDateInput.value = date || currentDate();
		recordedDateInput.max = currentDate();
		stepsInput.value = steps;
		memoInput.value = memo;
		clearAllErrors();
		overlay.classList.add("is-open");
		recordedDateInput.focus();
	}

	function closeModal() {
		overlay.classList.remove("is-open");
	}

	openBtns.forEach((btn) => {
		btn.addEventListener("click", () => {
			openModal({ mode: "create", date: btn.dataset.date || "" });
		});
	});

	if (closeBtn) closeBtn.addEventListener("click", closeModal);
	if (cancelBtn) cancelBtn.addEventListener("click", closeModal);

	overlay.addEventListener("click", (event) => {
		if (event.target === overlay) closeModal();
	});

	document.addEventListener("keydown", (event) => {
		if (event.key === "Escape" && overlay.classList.contains("is-open")) closeModal();
	});

	// Edit record / 記録編集
	document.querySelectorAll(".edit-btn").forEach((btn) => {
		btn.addEventListener("click", () => {
			openModal({
				mode: "edit",
				id: btn.dataset.id || "",
				date: btn.dataset.date || "",
				steps: btn.dataset.steps || "",
				memo: btn.dataset.memo || ""
			});
		});
	});

	// Clear errors while typing / 入力時にエラーをクリア
	[recordedDateInput, stepsInput].forEach((input) => {
		if (input) input.addEventListener("input", () => clearError(input.id));
	});

	// Date validation / 日付チェック
	function validateRecordedDate() {
		if (!recordedDateInput.value) {
			showError("recordedDate", "日付を入力してください");
			return false;
		}

		const selectedDate = new Date(recordedDateInput.value + "T00:00:00");
		const today = new Date();
		today.setHours(0, 0, 0, 0);

		if (selectedDate > today) {
			showError("recordedDate", "未来の日付は指定できません");
			return false;
		}

		clearError("recordedDate");
		return true;
	}

	// Step validation / 歩数チェック
	function validateSteps() {
		const value = stepsInput.value;

		if (value === "") {
			showError("steps", "歩数を入力してください");
			return false;
		}

		const steps = Number(value);

		if (!Number.isInteger(steps) || steps < 0 || steps > 100000) {
			showError("steps", "歩数は0〜100000歩の範囲で入力してください");
			return false;
		}

		clearError("steps");
		return true;
	}

	form.addEventListener("submit", (event) => {
		const isRecordedDateValid = validateRecordedDate();
		const isStepsValid = validateSteps();

		if (!isRecordedDateValid || !isStepsValid) {
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
		["recordedDate", "steps"].forEach(clearError);
	}

	// Current date in YYYY-MM-DD format / 現在日付をYYYY-MM-DD形式で返す
	function currentDate() {
		const date = new Date();
		const yyyy = date.getFullYear();
		const mm = String(date.getMonth() + 1).padStart(2, "0");
		const dd = String(date.getDate()).padStart(2, "0");
		return `${yyyy}-${mm}-${dd}`;
	}
}