const typeModal = document.querySelector('#typeModal');

// 유형선택하기 버튼 → 모달 열기
document.querySelector('#typeOpenBtn').addEventListener('click', () => {
  typeModal.hidden = false;
});

// 닫기 버튼 → 모달 닫기
document.querySelector('#typeCloseBtn').addEventListener('click', () => {
  typeModal.hidden = true;
});

// 유형 버튼 클릭 → 모달 닫기 (선택값 반영은 추후 작성)
document.querySelectorAll('.type-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    typeModal.hidden = true;
  });
});
