const joinForm = document.querySelector('#joinForm');
const memberId = document.querySelector('#memberId');
const memberPw = document.querySelector('#memberPw');
const memberPwCheck = document.querySelector('#memberPwCheck');
const memberName = document.querySelector('#memberName');
const memberPhone = document.querySelector('#memberPhone');

const idMsg = document.querySelector('#idMsg');
const pwMsg = document.querySelector('#pwMsg');
const pwCheckMsg = document.querySelector('#pwCheckMsg');
const nameMsg = document.querySelector('#nameMsg');
const phoneMsg = document.querySelector('#phoneMsg');

// 정규식
const idRegex = /^[a-z0-9]{4,20}$/;                          // 영문 소문자/숫자 4~20자
const pwRegex = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,20}$/;  // 영문+숫자 포함 8~20자
const nameRegex = /^[가-힣a-zA-Z]{2,20}$/;                   // 한글/영문 2~20자
const phoneRegex = /^01[016789]\d{7,8}$/;                    // 숫자만, 01x로 시작 10~11자리

// 중복확인 통과 여부 (아이디를 바꾸면 다시 false)
let idChecked = false;

memberId.addEventListener('input', () => {
  idChecked = false;
  idMsg.className = 'error-msg';
  idMsg.textContent = '';
});

// 중복확인 버튼 → 형식 검사 후 서버에 물어봄
document.querySelector('#dupCheckBtn').addEventListener('click', () => {
  const id = memberId.value.trim();

  if (!idRegex.test(id)) {
    idMsg.className = 'error-msg';
    idMsg.textContent = '아이디는 영문 소문자/숫자 4~20자로 입력해주세요.';
    return;
  }

  axios.get('/api/member/dup', { params: { memberId: id } })
    .then(res => {
      if (res.data) {
        idChecked = false;
        idMsg.className = 'error-msg';
        idMsg.textContent = '이미 사용 중인 아이디입니다.';
      } else {
        idChecked = true;
        idMsg.className = 'ok-msg';
        idMsg.textContent = '사용 가능한 아이디입니다.';
      }
    })
    .catch(() => {
      idMsg.className = 'error-msg';
      idMsg.textContent = '중복 확인 중 오류가 발생했습니다.';
    });
});

// 가입하기 → 항목별 검사, 모두 통과하면 전송
joinForm.addEventListener('submit', (e) => {
  e.preventDefault();

  let isValid = true;

  // 아이디
  if (!idRegex.test(memberId.value.trim())) {
    idMsg.className = 'error-msg';
    idMsg.textContent = '아이디는 영문 소문자/숫자 4~20자로 입력해주세요.';
    isValid = false;
  } else if (!idChecked) {
    idMsg.className = 'error-msg';
    idMsg.textContent = '아이디 중복확인을 해주세요.';
    isValid = false;
  }

  // 비밀번호
  if (!pwRegex.test(memberPw.value)) {
    pwMsg.textContent = '비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.';
    isValid = false;
  } else {
    pwMsg.textContent = '';
  }

  // 비밀번호 확인
  if (memberPw.value !== memberPwCheck.value) {
    pwCheckMsg.textContent = '비밀번호가 일치하지 않습니다.';
    isValid = false;
  } else {
    pwCheckMsg.textContent = '';
  }

  // 이름
  if (!nameRegex.test(memberName.value.trim())) {
    nameMsg.textContent = '이름은 한글 또는 영문 2~20자로 입력해주세요.';
    isValid = false;
  } else {
    nameMsg.textContent = '';
  }

  // 휴대전화번호
  if (!phoneRegex.test(memberPhone.value.trim())) {
    phoneMsg.textContent = '휴대전화번호는 숫자만 입력해주세요. (예: 01012345678)';
    isValid = false;
  } else {
    phoneMsg.textContent = '';
  }

  if (isValid) {
    joinForm.submit();
  }
});
