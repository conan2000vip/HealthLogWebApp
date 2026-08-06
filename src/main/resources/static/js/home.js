document.addEventListener("DOMContentLoaded", () => {

	/* ---------- Render icon Lucide (data-lucide trong fragment feedbackList) ---------- */
	if (window.lucide) {
		lucide.createIcons();
	}

	/* ---------- Nút "すべて見る" trong fragment feedbackList ---------- */
	const feedbackToggleBtn = document.getElementById("feedbackToggleBtn");
	if (feedbackToggleBtn) {
		feedbackToggleBtn.addEventListener("click", () => {
			document.querySelectorAll("#feedbackSection .feedback-card.is-hidden")
				.forEach(card => card.classList.remove("is-hidden"));
			feedbackToggleBtn.remove();
		});
	}

	/* ---------- Mini sparkline: 体重 ---------- */
	const miniWeightEl = document.getElementById("miniWeightChart");
	if (miniWeightEl && typeof miniWeightData !== "undefined") {
		new Chart(miniWeightEl, {
			type: "line",
			data: {
				labels: miniWeightData.map(() => ""),
				datasets: [{
					data: miniWeightData,
					borderColor: "#7c3aed",
					backgroundColor: "rgba(124,58,237,0.08)",
					borderWidth: 2,
					pointRadius: 3,
					pointBackgroundColor: "#7c3aed",
					tension: 0.35,
					fill: true
				}]
			},
			options: sparklineOptions()
		});
	}

	/* ---------- Mini sparkline: 睡眠 ---------- */
	const miniSleepEl = document.getElementById("miniSleepChart");
	if (miniSleepEl && typeof miniSleepData !== "undefined") {
		new Chart(miniSleepEl, {
			type: "bar",
			data: {
				labels: miniSleepData.map(() => ""),
				datasets: [{
					data: miniSleepData,
					backgroundColor: "#bfdbfe",
					borderRadius: 3,
					barPercentage: 0.6
				}]
			},
			options: sparklineOptions()
		});
	}

	/* ---------- Biểu đồ tổng hợp 7 ngày (fragment chartCard -> #trendChart) ---------- */
	const trendEl = document.getElementById("trendChart");
	if (trendEl && typeof trendChartData !== "undefined") {
		new Chart(trendEl, {
			type: "line",
			data: {
				labels: trendChartData.labels,
				datasets: [
					{
						label: "体重 (kg)",
						data: trendChartData.weight,
						borderColor: "#7c3aed",
						backgroundColor: "#7c3aed",
						yAxisID: "yLeftWeight",
						tension: 0.3,
						pointRadius: 4
					},
					{
						label: "睡眠 (時間)",
						data: trendChartData.sleep,
						borderColor: "#3b82f6",
						backgroundColor: "#3b82f6",
						yAxisID: "yRightCount",
						tension: 0.3,
						pointRadius: 4
					},
					{
						label: "水分 (ml)",
						data: trendChartData.water,
						borderColor: "#06b6d4",
						backgroundColor: "#06b6d4",
						yAxisID: "yRightCount",
						tension: 0.3,
						pointRadius: 4
					},
					{
						label: "歩数 (千歩)",
						data: trendChartData.step.map(v => v / 1000),
						borderColor: "#22c55e",
						backgroundColor: "#22c55e",
						yAxisID: "yRightCount",
						tension: 0.3,
						pointRadius: 4
					}
				]
			},
			options: {
				responsive: true,
				maintainAspectRatio: false,
				interaction: { mode: "index", intersect: false },
				plugins: {
					legend: {
						position: "top",
						align: "start",
						labels: { boxWidth: 10, boxHeight: 10, usePointStyle: true, font: { size: 12 } }
					},
					tooltip: { mode: "index", intersect: false }
				},
				scales: {
					yLeftWeight: {
						type: "linear",
						position: "left",
						min: 50,
						max: 60,
						grid: { color: "#eef1f7" },
						title: { display: false }
					},
					yRightCount: {
						type: "linear",
						position: "right",
						min: 0,
						max: 10,
						grid: { drawOnChartArea: false },
						ticks: {
							callback: (v) => v === 0 ? "0" : v + "k"
						}
					}
				}
			}
		});
	}

	/* ---------- Toggle ẩn/hiện biểu đồ (nút trong fragment chartCard) ---------- */
	const toggleBtn = document.getElementById("toggleChartBtn");
	const chartWrapper = document.getElementById("chartWrapper");
	if (toggleBtn && chartWrapper) {
		toggleBtn.addEventListener("click", () => {
			const hidden = chartWrapper.style.display === "none";
			chartWrapper.style.display = hidden ? "" : "none";
			toggleBtn.textContent = hidden ? "非表示" : "表示";
		});
	}

	function sparklineOptions() {
		return {
			responsive: true,
			maintainAspectRatio: false,
			plugins: { legend: { display: false }, tooltip: { enabled: false } },
			scales: {
				x: { display: false },
				y: { display: false }
			},
			elements: { point: { radius: 0 } }
		};
	}
});