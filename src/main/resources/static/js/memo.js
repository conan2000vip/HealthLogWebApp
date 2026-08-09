document.addEventListener("DOMContentLoaded", () => {
	initModal();
});

/* =========================================================
   モーダル（新規登録 / 編集）— memo ページ専用
   （filter-bar / memo-cell / delete-confirm は common.js 側で
   全画面共通として処理されるため、ここでは扱わない）
========================================================= */
function initModal() {
	const overlay = document.getElementById("memoModalOverlay");
	const openBtns = [
		document.getElementById("openAddModalBtn"),
		document.getElementById("openAddModalBtnEmpty"),
		document.getElementById("openAddModalBtnFiltered")
	].filter(Boolean);
	const closeBtn = document.getElementById("closeModalBtn");
	const cancelBtn = document.getElementById("cancelModalBtn");
	const modalTitle = document.getElementById("memoModalTitle");
	const form = document.getElementById("memoForm");

	const recordId = document.getElementById("recordId");
	const recordedDateInput = document.getElementById("recordedDate");
	const titleInput = document.getElementById("title");
	const contentInput = document.getElementById("content");

	if (!overlay || !form) return;

	function openModal({ mode = "create", id = "", date = "", title = "", content = "" } = {}) {
		modalTitle.textContent = mode === "edit" ? "メモを編集する" : "メモを記録する";
		recordId.value = id;
		recordedDateInput.value = date || todayIso();
		recordedDateInput.max = todayIso();
		titleInput.value = title;
		contentInput.value = content;
		clearAllErrors();
		overlay.classList.add("is-open");
		recordedDateInput.focus();
	}

	function closeModal() {
		overlay.classList.remove("is-open");
	}

	openBtns.forEach((btn) => btn.addEventListener("click", () => openModal({ mode: "create", date: btn.dataset.date || "" })));

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
				title: btn.dataset.title || "",
				content: btn.dataset.content || ""
			});
		});
	});

	// 入力時にエラーをクリア
	[recordedDateInput, titleInput, contentInput].forEach((input) => {
		if (input) input.addEventListener("input", () => clearError(input.id));
	});

	// バリデーション
	function validateRecordedDate() {
		if (!recordedDateInput.value) {
			showError("recordedDate", "日付を入力してください");
			return false;
		}

		const selected = new Date(recordedDateInput.value + "T00:00:00");
		const today = new Date();
		today.setHours(0, 0, 0, 0);

		if (selected > today) {
			showError("recordedDate", "未来の日付は指定できません");
			return false;
		}

		clearError("recordedDate");
		return true;
	}
	function validateTitle() {
		const value = titleInput.value.trim();

		if (value.length > 100) {
			showError("title", "タイトルは100文字以内で入力してください");
			return false;
		}

		clearError("title");
		return true;
	}

	function validateContent() {
		const value = contentInput.value.trim();

		if (!value) {
			showError("content", "メモ内容を入力してください");
			return false;
		}

		if (value.length > 2000) {
			showError("content", "メモ内容は2000文字以内で入力してください");
			return false;
		}

		clearError("content");
		return true;
	}

	form.addEventListener("submit", (event) => {
		const isRecordedDateValid = validateRecordedDate();
		const isTitleValid = validateTitle();
		const isContentValid = validateContent();

		if (!isRecordedDateValid || !isTitleValid || !isContentValid) {
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
		["recordedDate", "title", "content"].forEach(clearError);
	}

	// 今日の日付をYYYY-MM-DD形式で返す
	function todayIso() {
		const date = new Date();
		const yyyy = date.getFullYear();
		const mm = String(date.getMonth() + 1).padStart(2, "0");
		const dd = String(date.getDate()).padStart(2, "0");
		return `${yyyy}-${mm}-${dd}`;
	}
}