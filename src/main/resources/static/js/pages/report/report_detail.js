// 신고 취소 버튼 → 확인 후 삭제 요청 (접수 상태가 아니면 버튼 자체가 없음)
const deleteBtn = document.querySelector('#deleteBtn');

if (deleteBtn) {
  deleteBtn.addEventListener('click', () => {
    if (confirm('신고를 취소하면 첨부파일과 함께 삭제되며 되돌릴 수 없습니다.\n정말 취소하시겠습니까?')) {
      deleteBtn.disabled = true; // 중복 요청 방지
      document.querySelector('#deleteForm').submit();
    }
  });
}
