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
