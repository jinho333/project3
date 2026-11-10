// 삭제 버튼 → 확인 후 삭제 요청 (신고가 접수된 노선은 서버에서 막음)
document.querySelectorAll('.delete-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    if (confirm(`${btn.dataset.routeNo} 노선을 삭제하시겠습니까?`)) {
      btn.disabled = true; // 중복 요청 방지
      btn.closest('form').submit();
    }
  });
});

// 새로고침: 예측 서버와 울산 BIS 를 차례로 부르느라 몇 초 걸릴 수 있어서, 누르면 버튼을 잠가 중복 요청을 막음
const refreshForm = document.querySelector('#refreshForm');
if (refreshForm) {
  refreshForm.addEventListener('submit', () => {
    const btn = refreshForm.querySelector('.refresh-btn');
    btn.disabled = true;
    btn.textContent = '새로고침 중...';
  });
}
