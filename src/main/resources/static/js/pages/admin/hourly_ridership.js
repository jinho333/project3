// 시간대별 예상 승차 인원 (일별 이용자 수 예측 박스 아래)
// /admin/api/routes/{노선번호}/ridership/hourly?date=YYYY-MM-DD : 예측 서버(ai_service)가 일별 예측값에 시각 비율을 곱해 만든 0~23시 승차 인원(명)
// 기존 dashboard_map.js 를 거의 건드리지 않으려고 따로 둠: dashboard_map.js 는 노선이 바뀔 때 window.HourlyRidership.load(노선), 선택이 풀리면 clear() 만 부름
// status 가 OK 가 아니면(서버 꺼짐, 모델이 모르는 노선, 예측 가능 기간 밖) 차트 대신 message 를 보여 줌
(function () {
  const DAYS = 7;                    // 오늘부터 7일 (서버 규칙과 같음: 그 밖의 날짜는 400)
  const FIRST_HOUR = 5;              // 첫차 시각 근처인 5시부터 그림: 0~4시는 비중이 매우 작아 막대로 그리지 않음(서버 응답과 하루 합계에는 그대로 들어 있음)
  const SMALL_ROUTE_DAILY = 400;    // 하루 예측 승차 인원이 이보다 적으면 소규모 노선 안내 (평일 노선×날짜의 하위 3분의 1 경계, 2026-11 자료 기준)
  const API = '/admin/api/routes';

  const boxEl = document.querySelector('#hourlyBox');
  if (!boxEl) { window.HourlyRidership = { load: function () {}, clear: function () {}, retryIfFailed: function () {} }; return; }
  const datesEl = document.querySelector('#hourlyDates');
  const summaryEl = document.querySelector('#hourlySummary');
  const chartBoxEl = document.querySelector('#hourlyChartBox');
  const canvasEl = document.querySelector('#hourlyChart');
  const noticeEl = document.querySelector('#hourlyNotice');

  let chart = null;
  let reqNo = 0;                     // 응답이 늦게 와도 지금 노선·날짜의 것만 그리도록 요청마다 번호를 매김
  let state = 'idle';                // idle / loading / ok / failed (failed 일 때만 같은 노선을 다시 눌러 재시도)
  let routeNo = null;
  let dateIdx = 0;
  const cache = new Map();           // '노선|날짜' -> 응답 (정상 응답만, 화면 안에서만)

  function pad(n) { return String(n).padStart(2, '0'); }
  function ymd(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
  function dayList() {
    const names = '일월화수목금토';
    const out = [];
    for (let i = 0; i < DAYS; i++) {
      const d = new Date(); d.setDate(d.getDate() + i);
      out.push({ ymd: ymd(d), label: (i === 0 ? '오늘 ' : '') + pad(d.getMonth() + 1) + '/' + pad(d.getDate()) + '(' + names[d.getDay()] + ')' });
    }
    return out;
  }

  let days = dayList();
  function buildButtons() {
    days = dayList();
    datesEl.textContent = '';
    days.forEach(function (d, i) {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'hourly-date';
      b.textContent = d.label;
      b.setAttribute('aria-pressed', i === dateIdx ? 'true' : 'false');
      // 다른 날짜를 누르면 그 날짜를 불러옴. 실패한 상태면 같은 날짜를 다시 눌러도 재시도
      b.addEventListener('click', function () { if (routeNo && (i !== dateIdx || state === 'failed')) { dateIdx = i; syncButtons(); request(); } });
      datesEl.appendChild(b);
    });
  }
  function syncButtons() {
    Array.prototype.forEach.call(datesEl.children, function (b, i) { b.setAttribute('aria-pressed', i === dateIdx ? 'true' : 'false'); });
  }

  function clearChart() { if (chart) { chart.destroy(); chart = null; } }

  function showMessage(text) {
    state = 'failed';
    clearChart();
    chartBoxEl.hidden = true;
    noticeEl.textContent = '';
    summaryEl.textContent = text;
    summaryEl.classList.add('is-error');
  }

  // 소규모 노선 기준(400명)은 평일 자료로 정했으므로 평일(shareDayKind 0)에만 적용: 주말은 하루 합이 달라 이 기준이 맞지 않음(주말 안내는 limited 가 맡음)
  function isSmall(r) { return r.shareDayKind === 0 && r.dailyPredicted < SMALL_ROUTE_DAILY; }

  // 안내 문구: 항상 합성데이터 안내 + 해당할 때만 임시·자료 부족·소규모 노선
  function noticeText(r) {
    const parts = [];
    if (r.provisional) parts.push('자료가 적은 임시 비율입니다.');
    if (r.limited) parts.push('이 요일은 시간대 자료가 적어 근거가 약합니다(비슷한 요일의 비율로 대신했을 수 있습니다).');
    if (isSmall(r)) parts.push('소규모 노선은 오차가 큽니다.');
    parts.push('0~4시는 이용이 매우 적어 그리지 않았습니다(하루 합계에는 포함).');
    parts.push(r.notice || '합성데이터 기반 통계적 경향 예측입니다.');
    return parts.join(' ');
  }

  function render(r) {
    if (r.status !== 'OK' || !r.hours || r.hours.length !== 24) {
      showMessage(r.message || '시간대별 예측을 표시할 수 없습니다.');
      return;
    }
    state = 'ok';
    summaryEl.classList.remove('is-error');
    const shown = r.hours.filter(function (h) { return h.hour >= FIRST_HOUR; });
    let peak = shown[0];
    shown.forEach(function (h) { if (h.predicted > peak.predicted) peak = h; });
    summaryEl.textContent = r.date + '(' + r.dayOfWeek + ') 하루 약 ' + r.dailyPredicted.toLocaleString() + '명, 피크 '
      + peak.hour + '시 약 ' + peak.predicted.toLocaleString() + '명';
    noticeEl.textContent = noticeText(r);
    noticeEl.classList.toggle('is-warn', !!(r.provisional || r.limited || isSmall(r)));

    clearChart();
    if (typeof Chart === 'undefined') {
      chartBoxEl.hidden = true;
      summaryEl.appendChild(document.createTextNode(' (차트를 불러오지 못했습니다)'));
      return;
    }
    chartBoxEl.hidden = false;
    // 캔버스는 스크린리더가 읽지 못하므로, 요약 문장(위)을 대신 설명으로 연결
    canvasEl.setAttribute('role', 'img');
    canvasEl.setAttribute('aria-label', '시간대별 예상 승차 인원 막대 그래프. ' + summaryEl.textContent);
    chart = new Chart(canvasEl, {
      type: 'bar',
      data: {
        labels: shown.map(function (h) { return h.hour + '시'; }),
        datasets: [{ label: '예상 승차 인원', data: shown.map(function (h) { return h.predicted; }),
          backgroundColor: shown.map(function (h) { return h.hour === peak.hour ? '#e07a1f' : '#5b8def'; }) }]
      },
      options: {
        responsive: true, maintainAspectRatio: false, animation: false,
        scales: { y: { beginAtZero: true, ticks: { callback: function (v) { return v.toLocaleString(); } } },
                  x: { ticks: { autoSkip: true, maxRotation: 0, font: { size: 9 } } } },     // 좁은 화면에서는 라벨이 겹치지 않게 Chart.js 가 일부를 건너뜀
        plugins: { legend: { display: false },
                   tooltip: { callbacks: { label: function (c) { return c.parsed.y.toLocaleString() + '명'; } } } }
      }
    });
  }

  async function request() {
    const req = ++reqNo;
    const date = days[dateIdx].ymd;
    const key = routeNo + '|' + date;
    if (cache.has(key)) { render(cache.get(key)); return; }
    state = 'loading';
    clearChart();
    chartBoxEl.hidden = true;
    noticeEl.textContent = '';
    summaryEl.classList.remove('is-error');
    summaryEl.textContent = '시간대별 예측을 불러오는 중...';
    try {
      const res = await fetch(API + '/' + encodeURIComponent(routeNo) + '/ridership/hourly?date=' + encodeURIComponent(date));
      if (!res.ok) throw new Error('hourly ' + res.status);
      const r = await res.json();
      if (req !== reqNo) return;                 // 그사이 다른 노선·날짜로 바뀌었으면 버림
      if (r.status === 'OK') cache.set(key, r);
      render(r);
    } catch (e) {
      if (req !== reqNo) return;
      showMessage('시간대별 예측을 불러오지 못했습니다. 잠시 후 다시 시도하세요.');
    }
  }

  window.HourlyRidership = {
    load: function (no) { routeNo = no; dateIdx = 0; buildButtons(); request(); },     // 노선이 바뀌면 오늘부터 다시
    retryIfFailed: function (no) { if (state === 'failed' && routeNo === no) request(); },
    clear: function () { reqNo++; clearChart(); state = 'idle'; routeNo = null; }
  };
})();
