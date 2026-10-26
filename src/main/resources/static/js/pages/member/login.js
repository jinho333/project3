const loginForm = document.querySelector('#loginForm');
const errorMsg = document.querySelector('#errorMsg');

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
