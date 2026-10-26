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

// 유형선택하기 버튼 → 모달 열기
document.querySelector('#typeOpenBtn').addEventListener('click', () => {
  typeModal.hidden = false;
});

// 닫기 버튼 → 모달 닫기
document.querySelector('#typeCloseBtn').addEventListener('click', () => {
  typeModal.hidden = true;
});

// 유형 버튼 클릭 → 선택한 유형을 버튼 옆에 표시하고, 유형 번호를 폼에 담은 뒤 모달 닫기
document.querySelectorAll('.type-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    const groupName = btn.closest('.type-group').querySelector('h4').textContent;
    const typeName = btn.textContent;

    document.querySelector('#selectedType').textContent = `${groupName} > ${typeName}`;
    document.querySelector('#typeNo').value = btn.dataset.typeNo;
    typeModal.hidden = true;
  });
});

// 사진 칸에 파일을 고르면 이미지는 미리보기, 그 외(동영상)는 파일명을 칸 안에 표시
document.querySelectorAll('.photo-box input[type="file"]').forEach(input => {
  input.addEventListener('change', () => {
    const box = input.closest('.photo-box');
    const nameSpan = box.querySelector('.photo-name');
    const file = input.files[0];

    // 선택을 취소한 경우
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
document.querySelector('#submitBtn').addEventListener('click', () => {
  const reporterName = document.querySelector('#reporterName').value.trim();
  const reporterPhone = document.querySelector('#reporterPhone').value.trim();
  const typeNo = document.querySelector('#typeNo').value;
  const routeNo = document.querySelector('#routeNo').value.trim();
  const content = document.querySelector('#content').value.trim();

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
  if (content === '') {
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

  reportForm.submit();
});
