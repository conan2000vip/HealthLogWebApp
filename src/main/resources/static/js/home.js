document.addEventListener("DOMContentLoaded", () => {
  // Initialize Lucide icons
  if (window.lucide) {
    lucide.createIcons();
  }

  // Show all feedback items
  const feedbackToggleBtn = document.getElementById("feedbackToggleBtn");
  if (feedbackToggleBtn) {
    const collapsedText = feedbackToggleBtn.textContent; // "今日のフィードバックをすべて見る（N件）"
    let expanded = false;

    feedbackToggleBtn.addEventListener("click", () => {
      expanded = !expanded;
      document
        .querySelectorAll("#feedbackSection .feedback-card")
        .forEach((card, index) => {
          if (index >= 3) {
            card.classList.toggle("is-hidden", !expanded);
          }
        });

      feedbackToggleBtn.textContent = expanded ? "閉じる" : collapsedText;
    });
  }

  // Initialize clickable stat cards
  initStatCards();

  // Initialize Health Streak Achievement
  initStreakAchievement();

  // Initialize Health Streak View Button
  initStreakViewButton();
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

/* =========================================================
   Health Streak Achievement
   ========================================================= */

function initStreakAchievement() {
  const streakModal = document.getElementById("streakModal");
  if (!streakModal) {
    return;
  }

  const streakModalButton = document.getElementById("streakModalButton");
  const streakViewButton = document.getElementById("streakViewButton");
  streakModalButton?.addEventListener("click", closeStreakAchievement);
  streakViewButton?.addEventListener("click", () => {
  	const streak = Number(
  		document.querySelector("#streakViewButton strong")?.textContent || 0
  	);
  	showCurrentStreakAchievement(streak);
  });
  streakModal
    .querySelector(".streak-modal__overlay")
    ?.addEventListener("click", closeStreakAchievement);

  document.addEventListener("keydown", (event) => {
    if (
      event.key === "Escape" &&
      streakModal.classList.contains("is-visible")
    ) {
      closeStreakAchievement();
    }
  });

  /*
   * Backendから渡されたFeedbackから
   * HEALTH_STREAKを探す
   */
  findAndShowStreakFeedback();
}

/* =========================================================
   ★変更：localStorageキーを "profileId + マイルストーン日数 + 達成日" で
   分離する。達成日を含めることで、一度ストリークが途切れて
   再度同じ日数（例：7日）を達成した「新しいサイクル」では
   再度ポップアップが表示されるようにする。
   （同じ日に何度リロードしても重複表示はしない）
   ========================================================= */
function getStreakStorageKey(days, occurredAt) {
  // window.currentProfileId は Thymeleaf 側で埋め込む
  const profileId = window.currentProfileId ?? "unknown";
  // occurredAt は "yyyy-MM-ddTHH:mm:ss" 形式想定 → 日付部分のみ抽出
  const dateOnly =
    typeof occurredAt === "string" && occurredAt.length >= 10
      ? occurredAt.slice(0, 10)
      : "unknown-date";
  return `healthStreakModalShown_${profileId}_${days}_${dateOnly}`;
}

/* =========================================================
   Find HEALTH_STREAK feedback
   ========================================================= */

function findAndShowStreakFeedback() {
  if (!Array.isArray(window.homeFeedback)) {
    return;
  }

  const streakFeedback = window.homeFeedback.find(
    (feedback) => feedback.type === "HEALTH_STREAK",
  );

  if (!streakFeedback) {
    return;
  }

  // ★変更：タイトルから日数、feedbackItemからoccurredAt（達成日）を取り出し、
  //   「profileId + 日数 + 達成日」込みのキーで表示済みか判定する。
  //   これにより、ストリークが途切れて再度同じ日数を達成した新サイクルでは
  //   別キー扱いとなり、再度ポップアップが表示される。
  const match = (streakFeedback.title || "").match(/\d+/);
  if (!match) {
    return;
  }
  const days = match[0];
  const storageKey = getStreakStorageKey(days, streakFeedback.occurredAt);

  try {
    if (localStorage.getItem(storageKey)) {
      // このプロファイル・このマイルストーンでは既に表示済み
      return;
    }
  } catch (e) {
    // localStorageが使えない環境（プライベートモード等）はそのまま表示させる
  }
  try {
    localStorage.setItem(storageKey, "true");
  } catch (e) {
    // 保存に失敗しても表示自体は成功しているので握りつぶす
  }
}

/* =========================================================
   Show Achievement
   ========================================================= */
   function showCurrentStreakAchievement(days) {
   	const streakModal = document.getElementById("streakModal");
   	const streakTitle = document.getElementById("streakTitle");
   	const streakMessage = document.getElementById("streakMessage");
   	const streakIcon = document.getElementById("streakIcon");

   	if (!streakModal) {
   		return;
   	}
   	streakTitle.textContent = `${days}日連続達成！`;
   	if (days <= 0) {
   		streakMessage.textContent =
   			"まだ連続記録はありません。今日から健康記録を始めましょう！";
   		streakIcon.textContent = "🌱";
   	} else if (days < 7) {
   		streakMessage.textContent =
   			"毎日の記録を続けて、7日連続達成を目指しましょう！";
   		streakIcon.textContent = "🎯";
   	} else {
   		streakMessage.textContent =
   			"毎日の積み重ねが、あなたの未来の健康をつくります。";
   		if (days >= 365) {
   			streakIcon.textContent = "👑";
   		} else if (days >= 90) {
   			streakIcon.textContent = "🏆";
   		} else if (days >= 30) {
   			streakIcon.textContent = "⭐";
   		} else {
   			streakIcon.textContent = "🎉";
   		}
   	}

   	if (days > 0) {
   		createConfetti();
   	} else {
   		const streakConfetti = document.getElementById("streakConfetti");
   		if (streakConfetti) {
   			streakConfetti.innerHTML = "";
   		}
   	}
   	streakModal.classList.add("is-visible");
   	streakModal.setAttribute("aria-hidden", "false");
   	document.body.classList.add("modal-open");
   }
   
/* =========================================================
   Close Achievement
   ========================================================= */
   function closeStreakAchievement() {
     const streakModal = document.getElementById("streakModal");
     if (!streakModal) {
       return;
     }
     if (streakModal.contains(document.activeElement)) {
       document.activeElement.blur();
     }
     streakModal.classList.remove("is-visible");
     streakModal.setAttribute("aria-hidden", "true");
     document.body.classList.remove("modal-open");
     document.getElementById("streakViewButton")?.focus();
   }

/* =========================================================
   Confetti
   ========================================================= */
function createConfetti() {
  const streakConfetti = document.getElementById("streakConfetti");
  if (!streakConfetti) {
    return;
  }
  streakConfetti.innerHTML = "";
  const pieces = 70;
  for (let i = 0; i < pieces; i++) {
    const piece = document.createElement("span");
    piece.className = "streak-confetti-piece";
    const x = (Math.random() - 0.5) * 650;
    const y = 300 + Math.random() * 350;
    const rotate = Math.random() * 720 - 360;
    piece.style.left = `${50 + (Math.random() - 0.5) * 20}%`;
    piece.style.setProperty("--x", `${x}px`);
    piece.style.setProperty("--y", `${y}px`);
    piece.style.setProperty("--rotate", `${rotate}deg`);
    piece.style.animationDelay = `${Math.random() * 0.25}s`;
    piece.style.background = `hsl(${Math.random() * 360}, 80%, 60%)`;
    streakConfetti.appendChild(piece);
  }
}

/* =========================================================
   Health Streak View Button
   ========================================================= */
function initStreakViewButton() {
  const streakViewBtn = document.getElementById("streakViewBtn");
  const streakViewText = document.getElementById("streakViewText");
  if (!streakViewBtn) {
    return;
  }
  if (!Array.isArray(window.homeFeedback)) {
    streakViewBtn.style.display = "none";
    return;
  }
  
  const streakFeedback = window.homeFeedback.find(
    (feedback) => feedback.type === "HEALTH_STREAK"
  );
  if (!streakFeedback) {
    streakViewBtn.style.display = "none";
    return;
  }

  const match = (streakFeedback.title || "").match(/\d+/);
  if (!match) {
    streakViewBtn.style.display = "none";
    return;
  }

  const days = Number(match[0]);
  streakViewText.textContent = `現在の連続記録：${days}日`;
  streakViewBtn.addEventListener("click", () => {
    showStreakAchievement(streakFeedback);
  });
}