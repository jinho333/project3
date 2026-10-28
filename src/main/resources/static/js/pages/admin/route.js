// 삭제 버튼 → 확인 후 삭제 요청 (신고가 접수된 노선은 서버에서 막음)
document.querySelectorAll('.delete-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    if (confirm(`${btn.dataset.routeNo} 노선을 삭제하시겠습니까?`)) {
      btn.disabled = true; // 중복 요청 방지
      btn.closest('form').submit();
    }
  });
});
