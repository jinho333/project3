// 관리자 대시보드 AI 비서: 인사이트 버튼과 질문창
const insightBtn = document.querySelector('#insightBtn');
const insightResult = document.querySelector('#insightResult');
const askInput = document.querySelector('#askInput');
const askBtn = document.querySelector('#askBtn');
const askResult = document.querySelector('#askResult');

// 서버를 부르고 결과를 칸에 표시 (답변은 textContent 로 넣어 글자 그대로 보여 줌)
async function callAi(url, data, button, resultEl, waitText) {
  const btnText = button.textContent;
  button.disabled = true;
  button.textContent = waitText;
  resultEl.hidden = false;
  resultEl.classList.remove('is-error');
  resultEl.textContent = 'AI가 답변을 만드는 중입니다. 몇 초 걸릴 수 있어요…';

  try {
    const res = await axios.post(url, data);
    resultEl.textContent = res.data.answer;
  } catch (err) {
    resultEl.classList.add('is-error');
    const msg = err.response && err.response.data && err.response.data.message;
    resultEl.textContent = msg || '요청에 실패했습니다. 잠시 후 다시 시도해 주세요.';
  } finally {
    button.disabled = false;
    button.textContent = btnText;
  }
}

insightBtn.addEventListener('click', () => {
  callAi('/admin/api/ai/insight', {}, insightBtn, insightResult, '분석 중…');
});

function ask() {
  const question = askInput.value.trim();
  if (!question) {
    askResult.hidden = false;
    askResult.classList.add('is-error');
    askResult.textContent = '질문을 입력해 주세요.';
    askInput.focus();
    return;
  }
  callAi('/admin/api/ai/ask', { question }, askBtn, askResult, '답변 중…');
}

askBtn.addEventListener('click', ask);
askInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    ask();
  }
});
