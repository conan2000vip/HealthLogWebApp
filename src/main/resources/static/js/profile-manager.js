document.addEventListener('DOMContentLoaded', () => {
    lucide.createIcons();

    const overlay = document.getElementById('deleteModalOverlay');
    if (!overlay) return; // trang không có modal (vd: empty state) thì bỏ qua

    const modalMessage = overlay.querySelector('.modal__message');
    const closeBtn = document.getElementById('closeDeleteModalBtn');
    const cancelBtn = document.getElementById('cancelDeleteBtn');
    const confirmBtn = document.getElementById('confirmDeleteBtn');
    const hiddenIdInput = document.getElementById('deleteProfileId');
    const deleteForm = document.getElementById('deleteProfileForm');

    function openModal(id, name) {
        hiddenIdInput.value = id;
        modalMessage.textContent = `「${name}」のプロフィールを削除しますか？`;
        overlay.classList.add('is-open');
    }

    function closeModal() {
        overlay.classList.remove('is-open');
    }

    document.querySelectorAll('.delete-profile-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            openModal(btn.dataset.profileId, btn.dataset.profileName);
        });
    });

    closeBtn.addEventListener('click', closeModal);
    cancelBtn.addEventListener('click', closeModal);
    overlay.addEventListener('click', (e) => {
        if (e.target === overlay) closeModal();
    });

    confirmBtn.addEventListener('click', () => {
        deleteForm.submit();
    });
});