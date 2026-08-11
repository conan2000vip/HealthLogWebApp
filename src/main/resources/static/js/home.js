document.addEventListener("DOMContentLoaded", () => {
	// Initialize Lucide icons
	if (window.lucide) {
		lucide.createIcons();
	}

	// Show all feedback items
	const feedbackToggleBtn = document.getElementById("feedbackToggleBtn");
	if (feedbackToggleBtn) {
		feedbackToggleBtn.addEventListener("click", () => {
			document.querySelectorAll("#feedbackSection .feedback-card.is-hidden")
				.forEach((card) => card.classList.remove("is-hidden"));
			feedbackToggleBtn.remove();
		});
	}

	// Initialize clickable stat cards
	initStatCards();
});

function initStatCards() {
	document.querySelectorAll(".stat-card--clickable").forEach((card) => {
		const openCard = () => {
			const href = card.dataset.href;
			if (href) {
				window.location.href = href;
			}
		};

		card.addEventListener("click", openCard);

		card.addEventListener("keydown", (event) => {
			if (event.key === "Enter" || event.key === " ") {
				event.preventDefault();
				openCard();
			}
		});
	});
}