const loginForm = document.querySelector('#loginForm');
const errorMsg = document.querySelector('#errorMsg');

// 로그인이 필요한 페이지(신고 접수 등)에서 넘어온 경우 안내
if (document.querySelector('#needLogin')) {
  alert('로그인 후 이용 가능합니다.');
}

// 로그인 버튼 → 빈 값 검사 후 통과하면 전송
loginForm.addEventListener('submit', (e) => {
  e.preventDefault();

  const memberId = document.querySelector('#memberId').value.trim();
  const memberPw = document.querySelector('#memberPw').value;

  if (memberId === '') {
    errorMsg.textContent = '아이디를 입력해주세요.';
    return;
  }
  if (memberPw === '') {
    errorMsg.textContent = '비밀번호를 입력해주세요.';
    return;
  }

  loginForm.submit();
});
