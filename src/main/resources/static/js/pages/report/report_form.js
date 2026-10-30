const typeModal = document.querySelector('#typeModal');
const reportForm = document.querySelector('#reportForm');
const reportMsg = document.querySelector('#reportMsg');

// 정규식 (서버의 ReportService와 같은 규칙)
const phoneRegex = /^01[016789]\d{7,8}$/;               // 숫자만, 01x로 시작 10~11자리
const routeRegex = /^[A-Za-z0-9가-힣\-]{1,20}$/;        // 노선번호 (예: 3600, M5443, 3600-1)

// 첨부 가능한 확장자
const imageExts = ['jpg', 'bmp', 'gif', 'png'];
const videoExts = ['avi', 'wmv', 'mpg', 'mov', 'mp4'];
const maxTotalSize = 200 * 1024 * 1024; // 총 200MB

const routeModal = document.querySelector('#routeModal');
const routeList = document.querySelector('#routeList');
const routeKeyword = document.querySelector('#routeKeyword');
const submitBtn = document.querySelector('#submitBtn');
const submitBtnText = submitBtn.textContent;

// 유형선택하기 버튼 → 모달 열기
document.querySelector('#typeOpenBtn').addEventListener('click', () => {
  typeModal.hidden = false;
});

// 닫기 버튼 → 모달 닫기
document.querySelector('#typeCloseBtn').addEventListener('click', () => {
  typeModal.hidden = true;
});

// 선택한 유형을 버튼 옆에 표시하고, 유형 번호를 폼에 담음
function applyType(btn) {
  const groupName = btn.closest('.type-group').querySelector('h4').textContent;
  document.querySelector('#selectedType').textContent = `${groupName} > ${btn.textContent}`;
  document.querySelector('#typeNo').value = btn.dataset.typeNo;
}

// 유형 버튼 클릭 → 적용한 뒤 모달 닫기
document.querySelectorAll('.type-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    applyType(btn);
    typeModal.hidden = true;
  });
});

// 수정 화면이거나 서버 오류로 다시 열린 경우: 이미 들어 있는 유형 번호에 맞는 이름을 표시
const initTypeNo = document.querySelector('#typeNo').value;
if (initTypeNo !== '') {
  const initBtn = document.querySelector(`.type-btn[data-type-no="${initTypeNo}"]`);
  if (initBtn) {
    applyType(initBtn);
  }
}

// 노선 목록을 서버에서 받아 모달에 표시
function loadRouteList() {
  axios.get('/api/route', { params: { keyword: routeKeyword.value.trim() } })
    .then(res => {
      routeList.textContent = '';
      if (res.data.length === 0) {
        routeList.textContent = '검색된 노선이 없습니다.';
        return;
      }
      res.data.forEach(route => {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'route-item';
        btn.textContent = `${route.routeNo} (${route.routeName})`;
        // 노선을 고르면 입력칸에 노선번호를 넣고 모달 닫기
        btn.addEventListener('click', () => {
          document.querySelector('#routeNo').value = route.routeNo;
          routeModal.hidden = true;
        });
        routeList.appendChild(btn);
      });
    })
    .catch(() => {
      routeList.textContent = '노선 목록을 불러오지 못했습니다.';
    });
}

// 노선선택하기 버튼 → 모달 열고 목록 불러오기
document.querySelector('#routeOpenBtn').addEventListener('click', () => {
  routeModal.hidden = false;
  routeKeyword.focus();
  loadRouteList();
});

document.querySelector('#routeCloseBtn').addEventListener('click', () => {
  routeModal.hidden = true;
});

document.querySelector('#routeSearchBtn').addEventListener('click', loadRouteList);
routeKeyword.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    loadRouteList();
  }
});

// 모달 바깥(어두운 부분)을 누르거나 Esc를 누르면 닫기
[typeModal, routeModal].forEach(modal => {
  modal.addEventListener('click', (e) => {
    if (e.target === modal) {
      modal.hidden = true;
    }
  });
});
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    typeModal.hidden = true;
    routeModal.hidden = true;
  }
});

// 내용 글자 수 표시
const content = document.querySelector('#content');
const contentCount = document.querySelector('#contentCount');
function updateContentCount() {
  contentCount.textContent = content.value.length;
}
content.addEventListener('input', updateContentCount);
updateContentCount();

// 뒤로 가기 등으로 이 화면이 다시 보일 때 보내기 버튼을 되살림
window.addEventListener('pageshow', (e) => {
  if (e.persisted) {
    submitBtn.disabled = false;
    submitBtn.textContent = submitBtnText;
  }
});

// 사진 칸에 파일을 고르면 이미지는 미리보기, 그 외(동영상)는 파일명을 칸 안에 표시
document.querySelectorAll('.photo-box input[type="file"]').forEach(input => {
  input.addEventListener('change', () => {
    const box = input.closest('.photo-box');
    const nameSpan = box.querySelector('.photo-name');
    const file = input.files[0];

    // 선택을 취소한 경우
    box.classList.toggle('has-file', !!file);
    if (!file) {
      box.style.backgroundImage = '';
      nameSpan.textContent = '';
      return;
    }

    if (file.type.startsWith('image/')) {
      box.style.backgroundImage = `url(${URL.createObjectURL(file)})`;
      nameSpan.textContent = '';
    } else {
      box.style.backgroundImage = '';
      nameSpan.textContent = file.name;
    }
  });
});

// 파일명에서 확장자(소문자, 점 없음)만 꺼냄
function getExt(fileName) {
  const idx = fileName.lastIndexOf('.');
  return idx === -1 ? '' : fileName.substring(idx + 1).toLowerCase();
}

// 보내기 버튼 → 입력값 검사 후 통과하면 전송
submitBtn.addEventListener('click', () => {
  const reporterName = document.querySelector('#reporterName').value.trim();
  const reporterPhone = document.querySelector('#reporterPhone').value.trim();
  const typeNo = document.querySelector('#typeNo').value;
  const routeNo = document.querySelector('#routeNo').value.trim();
  const contentValue = content.value.trim();

  if (reporterName === '') {
    reportMsg.textContent = '이름을 입력해주세요.';
    return;
  }
  if (!phoneRegex.test(reporterPhone)) {
    reportMsg.textContent = '휴대전화번호는 숫자만 입력해주세요. (예: 01012345678)';
    return;
  }
  if (typeNo === '') {
    reportMsg.textContent = '유형을 선택해주세요.';
    return;
  }
  if (!routeRegex.test(routeNo)) {
    reportMsg.textContent = '노선번호를 올바르게 입력해주세요. (예: 3600)';
    return;
  }
  if (contentValue === '') {
    reportMsg.textContent = '내용을 입력해주세요.';
    return;
  }

  // 첨부파일 검사: 확장자, 동영상 개수, 총 용량
  let videoCount = 0;
  let totalSize = 0;
  const fileInputs = document.querySelectorAll('.photo-box input[type="file"]');
  for (const input of fileInputs) {
    const file = input.files[0];
    if (!file) {
      continue;
    }
    const ext = getExt(file.name);
    if (videoExts.includes(ext)) {
      videoCount++;
    } else if (!imageExts.includes(ext)) {
      reportMsg.textContent = '등록 가능한 파일은 jpg, bmp, gif, png, avi, wmv, mpg, mov, mp4 입니다.';
      return;
    }
    totalSize += file.size;
  }
  if (videoCount > 1) {
    reportMsg.textContent = '동영상은 1개만 첨부할 수 있습니다.';
    return;
  }
  if (totalSize > maxTotalSize) {
    reportMsg.textContent = '첨부파일은 총 200MB 이하로 첨부해주세요.';
    return;
  }

  // 전송 중에 다시 누르지 못하도록 버튼을 잠금 (중복 접수 방지)
  submitBtn.disabled = true;
  submitBtn.textContent = '전송 중...';
  reportForm.submit();
});

// AI 문장 다듬기: 내용을 서버로 보내 정리된 문장을 받고, '적용하기'를 눌러야 내용 칸에 넣음
const aiRefineBtn = document.querySelector('#aiRefineBtn');
const aiResult = document.querySelector('#aiResult');
const aiResultText = document.querySelector('#aiResultText');
const aiResultTip = document.querySelector('#aiResultTip');
const aiError = document.querySelector('#aiError');
const MAX_CONTENT_LENGTH = 2000;

function showAiError(message) {
  aiError.textContent = message;
  aiError.hidden = false;
}

aiRefineBtn.addEventListener('click', () => {
  aiError.hidden = true;
  aiResult.hidden = true;

  if (content.value.trim().length < 5) {
    showAiError('내용을 5자 이상 적은 뒤에 사용해 주세요.');
    return;
  }

  const btnText = aiRefineBtn.textContent;
  aiRefineBtn.disabled = true;
  aiRefineBtn.textContent = '다듬는 중…';

  axios.post('/api/report/refine', {
    content: content.value,
    typeName: document.querySelector('#selectedType').textContent
  })
    .then(res => {
      aiResultText.textContent = res.data.refined;
      aiResultTip.textContent = res.data.tip;
      aiResult.hidden = false;
    })
    .catch(err => {
      const msg = err.response && err.response.data && err.response.data.message;
      showAiError(msg || '요청에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    })
    .finally(() => {
      aiRefineBtn.disabled = false;
      aiRefineBtn.textContent = btnText;
    });
});

document.querySelector('#aiApplyBtn').addEventListener('click', () => {
  content.value = aiResultText.textContent.substring(0, MAX_CONTENT_LENGTH);
  content.dispatchEvent(new Event('input'));    // 글자 수 표시 갱신
  aiResult.hidden = true;
});

document.querySelector('#aiCloseBtn').addEventListener('click', () => {
  aiResult.hidden = true;
});

// 문장 자동완성: 선택한 유형(과 노선)에 맞는 예시 문장을 받아 보여 주고, 고른 문장을 내용 칸에 넣음
const suggestBtn = document.querySelector('#suggestBtn');
const suggestList = document.querySelector('#suggestList');
const suggestError = document.querySelector('#suggestError');

function showSuggestError(message) {
  suggestError.textContent = message;
  suggestError.hidden = false;
}

suggestBtn.addEventListener('click', () => {
  suggestError.hidden = true;
  suggestList.hidden = true;

  const typeNo = document.querySelector('#typeNo').value;
  if (typeNo === '') {
    showSuggestError('먼저 유형을 선택해 주세요.');
    return;
  }

  const btnText = suggestBtn.textContent;
  suggestBtn.disabled = true;
  suggestBtn.textContent = '문장 만드는 중…';

  axios.post('/api/report/suggest', {
    typeNo: typeNo,
    routeNo: document.querySelector('#routeNo').value.trim()
  })
    .then(res => {
      suggestList.textContent = '';
      res.data.list.forEach(text => {
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'suggest-item';
        item.textContent = text;           // 글자 그대로 표시 (HTML 로 해석되지 않게 textContent 사용)

        const hint = document.createElement('small');
        hint.textContent = '이 문장 사용하기';
        item.appendChild(hint);

        item.addEventListener('click', () => applySuggest(text));
        suggestList.appendChild(item);
      });
      suggestList.hidden = false;
    })
    .catch(err => {
      const msg = err.response && err.response.data && err.response.data.message;
      showSuggestError(msg || '요청에 실패했습니다. 잠시 후 다시 시도해 주세요.');
    })
    .finally(() => {
      suggestBtn.disabled = false;
      suggestBtn.textContent = btnText;
    });
});

// 고른 예시를 내용 칸에 넣음: 이미 쓴 내용이 있으면 지우지 않고 아래 줄에 덧붙임
function applySuggest(text) {
  const base = content.value.trim();
  content.value = (base === '' ? text : content.value.replace(/\s+$/, '') + '\n' + text).substring(0, MAX_CONTENT_LENGTH);
  content.dispatchEvent(new Event('input'));    // 글자 수 표시 갱신
  suggestList.hidden = true;

  // 고쳐 써야 하는 첫 ○○ 부분을 바로 선택해 줌
  const pos = content.value.indexOf('○○');
  content.focus();
  if (pos !== -1) {
    content.setSelectionRange(pos, pos + 2);
  }
}
