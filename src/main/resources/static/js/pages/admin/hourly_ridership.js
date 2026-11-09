// 시간대별 예상 승차 인원 (일별 이용자 수 예측 박스 아래)
// /admin/api/routes/{노선번호}/ridership/hourly?date=YYYY-MM-DD : 예측 서버(ai_service)가 일별 예측값에 시각 비율을 곱해 만든 0~23시 승차 인원(명)
// 기존 dashboard_map.js 를 거의 건드리지 않으려고 따로 둠: dashboard_map.js 는 노선이 바뀔 때 window.HourlyRidership.load(노선, 색),
// 비교 노선이 생기거나 바뀌거나 없어지면 setCompare(노선 또는 null, 색), 선택이 풀리면 clear() 만 부름
// status 가 OK 가 아니면(서버 꺼짐, 모델이 모르는 노선, 예측 가능 기간 밖) 차트 대신 message 를 보여 줌
// 비교 노선이 있으면 두 노선을 한 차트에 겹쳐 그림 (노선마다 자기 최대 시간대를 100%로 맞춰 모양만 비교).
// 모양이 비슷하다는 것도 비교 결과라서 비슷해도 그림은 그리고, 차트 위 한 줄로 피크 시간과 두 모양이 겹치는 비율을 알려 줌.
// 문구는 겹치는 비율(전체 모양)을 기준으로 정함: 실제 자료는 노선 대부분이 80~95% 겹치고, 피크 시간대는 비슷한 높이의 두 봉우리 중 어느 쪽이 근소하게 높은지로 바뀌기 때문
//  - 겹침이 SIMILAR_OVERLAP_PCT 이상이면 "모양이 비슷합니다" (피크 시간대가 달라도 전체 모양은 비슷하다고 알림), 그보다 낮으면 "모양에 차이가 있습니다"
(function () {
  const DAYS = 7;                    // 오늘부터 7일 (서버 규칙과 같음: 그 밖의 날짜는 400)
  const FIRST_HOUR = 5;              // 첫차 시각 근처인 5시부터 그림: 0~4시는 비중이 매우 작아 막대로 그리지 않음(서버 응답과 하루 합계에는 그대로 들어 있음)
  const SMALL_ROUTE_DAILY = 400;    // 하루 예측 승차 인원이 이보다 적으면 소규모 노선 안내 (평일 노선×날짜의 하위 3분의 1 경계, 2026-11 자료 기준)
  const PEAK_GAP_HOURS = 3;          // 두 노선의 피크 시작이 이 시간 이상 다르면 "피크 시간대가 다르다"고 안내 (시간대별 예측 오차가 약 13~16%라 1~2시간 차이는 의미 있는 차이로 보지 않음)
  const SIMILAR_OVERLAP_PCT = 85;    // 두 모양이 이 비율(%) 이상 겹치면 비슷하다고 안내 (큰 노선끼리 겹침은 76~96%, 중앙 약 90%)
  const MAIN_COLOR = '#5b8def';
  const PEAK_COLOR = '#e07a1f';
  const COMPARE_COLOR = '#e07a1f';
  const API = '/admin/api/routes';

  const boxEl = document.querySelector('#hourlyBox');
  if (!boxEl) {
    window.HourlyRidership = { load: function () {}, setCompare: function () {}, clear: function () {}, retryIfFailed: function () {} };
    return;
  }
  const datesEl = document.querySelector('#hourlyDates');
  const summaryEl = document.querySelector('#hourlySummary');
  const compareEl = document.querySelector('#hourlyCompare');
  const chartBoxEl = document.querySelector('#hourlyChartBox');
  const canvasEl = document.querySelector('#hourlyChart');
  const noticeEl = document.querySelector('#hourlyNotice');

  let chart = null;
  let reqNo = 0;                     // 응답이 늦게 와도 지금 노선·날짜의 것만 그리도록 요청마다 번호를 매김
  let state = 'idle';                // idle / loading / ok / failed (failed 일 때만 같은 노선을 다시 눌러 재시도)
  let routeNo = null;
  let compareNo = null;              // 비교 노선 (없으면 null)
  let mainColor = MAIN_COLOR;
  let compareColor = COMPARE_COLOR;
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

  function clearCompareLine() {
    compareEl.textContent = '';
    compareEl.hidden = true;
  }

  function showMessage(text) {
    state = 'failed';
    clearChart();
    chartBoxEl.hidden = true;
    noticeEl.textContent = '';
    clearCompareLine();
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

  function usable(r) { return !!r && r.status === 'OK' && !!r.hours && r.hours.length === 24; }

  // 가장 붐비는 연속 2시간의 시작 시각 (FIRST_HOUR 이후, 23시 끝까지)
  function peakStart(hours) {
    let best = -1, bestSum = -1;
    hours.forEach(function (h, i) {
      if (h.hour < FIRST_HOUR || i + 1 >= hours.length) return;
      const sum = h.predicted + hours[i + 1].predicted;
      if (sum > bestSum) { bestSum = sum; best = h.hour; }
    });
    return best;
  }
  function peakText(start) { return start + '~' + (start + 2) + '시'; }

  // 노선마다 자기 최대 시간대를 100 으로 맞춘 값 (규모가 달라도 모양을 비교할 수 있게)
  function normalized(shown) {
    const max = Math.max.apply(null, shown.map(function (h) { return h.predicted; }));
    return shown.map(function (h) { return max > 0 ? Math.round(h.predicted / max * 1000) / 10 : 0; });
  }

  // 선택한 노선 하나의 막대 차트 (피크 시간대는 주황색)
  function drawSingle(shown, peak) {
    chart = new Chart(canvasEl, {
      type: 'bar',
      data: {
        labels: shown.map(function (h) { return h.hour + '시'; }),
        datasets: [{ label: '예상 승차 인원', data: shown.map(function (h) { return h.predicted; }),
          backgroundColor: shown.map(function (h) { return h.hour === peak.hour ? PEAK_COLOR : mainColor; }) }]
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

  // 두 노선을 겹친 막대 차트: 노선마다 자기 최대 시간대를 100%로 맞춤, 마우스를 올리면 실제 인원도 보임
  function drawOverlay(mainShown, cmpShown) {
    const sets = [
      { label: routeNo + '번', shown: mainShown, color: mainColor },
      { label: compareNo + '번', shown: cmpShown, color: compareColor }
    ];
    chart = new Chart(canvasEl, {
      type: 'bar',
      data: {
        labels: mainShown.map(function (h) { return h.hour + '시'; }),
        datasets: sets.map(function (x) {
          return { label: x.label, data: normalized(x.shown), raw: x.shown.map(function (h) { return h.predicted; }), backgroundColor: x.color };
        })
      },
      options: {
        responsive: true, maintainAspectRatio: false, animation: false,
        scales: { y: { beginAtZero: true, max: 100, ticks: { callback: function (v) { return v + '%'; } } },
                  x: { ticks: { autoSkip: true, maxRotation: 0, font: { size: 9 } } } },
        plugins: { legend: { display: true, position: 'bottom', labels: { boxWidth: 12 } },
                   tooltip: { callbacks: { label: function (c) {
                     return c.dataset.label + ': ' + c.dataset.raw[c.dataIndex].toLocaleString() + '명 (최대 시간대의 ' + c.parsed.y + '%)';
                   } } } }
      }
    });
  }

  // 두 모양이 겹치는 비율(0~100): 노선마다 시간대 비중(합 100%)을 만들어, 시간대마다 둘 중 작은 쪽을 더함. 완전히 같으면 100, 전혀 다르면 0 에 가까움
  function overlapPercent(shownA, shownB) {
    const sumA = shownA.reduce(function (t, h) { return t + h.predicted; }, 0);
    const sumB = shownB.reduce(function (t, h) { return t + h.predicted; }, 0);
    if (sumA <= 0 || sumB <= 0) return null;
    let overlap = 0;
    shownA.forEach(function (h, i) { overlap += Math.min(h.predicted / sumA, shownB[i].predicted / sumB); });
    return Math.round(overlap * 100);
  }

  // 비교 안내 한 줄. 두 노선을 겹쳐 그릴 수 있으면 true
  function renderCompare(main, cmp) {
    clearCompareLine();
    if (!compareNo) return false;
    compareEl.hidden = false;
    if (!usable(cmp)) {
      compareEl.textContent = '비교: ' + compareNo + '번은 시간대별 예측을 표시할 수 없습니다'
        + (cmp && cmp.message ? '(' + cmp.message.replace(/[.。]\s*$/, '') + ')' : '') + '.';
      return false;
    }
    const a = peakStart(main.hours), b = peakStart(cmp.hours);
    const shownA = main.hours.filter(function (h) { return h.hour >= FIRST_HOUR; });
    const shownB = cmp.hours.filter(function (h) { return h.hour >= FIRST_HOUR; });
    const pct = overlapPercent(shownA, shownB);
    const peaksDiffer = Math.abs(a - b) >= PEAK_GAP_HOURS;
    const similar = pct !== null && pct >= SIMILAR_OVERLAP_PCT;
    const pctText = pct === null ? '' : '(두 모양이 약 ' + pct + '% 겹칩니다)';
    let text = '비교: ' + routeNo + '번 피크 ' + peakText(a) + ', ' + compareNo + '번 피크 ' + peakText(b) + '. ';
    if (similar && peaksDiffer) {
      text += '피크 시간대는 다르지만 전체 모양은 비슷합니다' + pctText + '. 예측 오차를 감안하면 의미 있는 차이로 보기 어렵습니다.';
    } else if (similar) {
      text += '두 노선의 시간대 모양이 비슷합니다' + pctText + '. 예측 오차를 감안하면 의미 있는 차이로 보기 어렵습니다.';
    } else {
      text += '두 노선의 시간대 모양에 차이가 있습니다' + pctText + '.';
    }
    text += ' 각 노선의 가장 붐비는 시간대를 100%로 맞춰 모양만 비교한 그림입니다.';
    if (cmp.provisional || cmp.limited) text += ' ' + compareNo + '번은 시간대 자료가 적어 근거가 약합니다.';
    compareEl.appendChild(document.createTextNode(text));
    return true;
  }

  function render(main, cmp) {
    if (!usable(main)) {
      showMessage((main && main.message) || '시간대별 예측을 표시할 수 없습니다.');
      return;
    }
    state = 'ok';
    summaryEl.classList.remove('is-error');
    const shown = main.hours.filter(function (h) { return h.hour >= FIRST_HOUR; });
    let peak = shown[0];
    shown.forEach(function (h) { if (h.predicted > peak.predicted) peak = h; });
    summaryEl.textContent = main.date + '(' + main.dayOfWeek + ') ' + (compareNo ? routeNo + '번 ' : '') + '하루 약 '
      + main.dailyPredicted.toLocaleString() + '명, 피크 ' + peak.hour + '시 약 ' + peak.predicted.toLocaleString() + '명';
    noticeEl.textContent = noticeText(main);
    noticeEl.classList.toggle('is-warn', !!(main.provisional || main.limited || isSmall(main)));
    const overlay = renderCompare(main, cmp);

    clearChart();
    if (typeof Chart === 'undefined') {
      chartBoxEl.hidden = true;
      summaryEl.appendChild(document.createTextNode(' (차트를 불러오지 못했습니다)'));
      return;
    }
    chartBoxEl.hidden = false;
    // 캔버스는 스크린리더가 읽지 못하므로, 요약 문장(위)과 비교 안내를 대신 설명으로 연결
    canvasEl.setAttribute('role', 'img');
    canvasEl.setAttribute('aria-label', '시간대별 예상 승차 인원 막대 그래프. ' + summaryEl.textContent + ' ' + compareEl.textContent);
    if (overlay) {
      drawOverlay(shown, cmp.hours.filter(function (h) { return h.hour >= FIRST_HOUR; }));
    } else {
      drawSingle(shown, peak);
    }
  }

  // 노선 하나의 하루치 응답 (정상 응답은 화면 안에서 기억해 둠)
  async function fetchOne(no, date) {
    const key = no + '|' + date;
    if (cache.has(key)) return cache.get(key);
    const res = await fetch(API + '/' + encodeURIComponent(no) + '/ridership/hourly?date=' + encodeURIComponent(date));
    if (!res.ok) throw new Error('hourly ' + res.status);
    const r = await res.json();
    if (r.status === 'OK') cache.set(key, r);
    return r;
  }

  async function request() {
    const req = ++reqNo;
    const date = days[dateIdx].ymd;
    const cached = cache.has(routeNo + '|' + date) && (!compareNo || cache.has(compareNo + '|' + date));
    if (!cached) {
      state = 'loading';
      clearChart();
      chartBoxEl.hidden = true;
      noticeEl.textContent = '';
      clearCompareLine();
      summaryEl.classList.remove('is-error');
      summaryEl.textContent = '시간대별 예측을 불러오는 중...';
    }
    try {
      // 비교 노선은 못 받아도 선택한 노선은 그대로 보여 주려고 따로 실패를 받음
      const cmpPromise = compareNo
        ? fetchOne(compareNo, date).catch(function () { return { status: 'ERROR', message: '불러오지 못했습니다' }; })
        : Promise.resolve(null);
      const results = await Promise.all([fetchOne(routeNo, date), cmpPromise]);
      if (req !== reqNo) return;                 // 그사이 다른 노선·날짜로 바뀌었으면 버림
      render(results[0], results[1]);
    } catch (e) {
      if (req !== reqNo) return;
      showMessage('시간대별 예측을 불러오지 못했습니다. 잠시 후 다시 시도하세요.');
    }
  }

  window.HourlyRidership = {
    // 노선이 바뀌면 오늘부터 다시 (비교 노선은 풀림: dashboard_map.js 가 필요하면 setCompare 를 이어서 부름)
    load: function (no, color) { routeNo = no; compareNo = null; mainColor = color || MAIN_COLOR; dateIdx = 0; buildButtons(); request(); },
    // 비교 노선이 생기거나 바뀌거나 없어졌을 때 (null 이면 비교 해제)
    setCompare: function (no, color) {
      if ((no || null) === compareNo) return;
      compareNo = no || null;
      compareColor = color || COMPARE_COLOR;
      if (routeNo) request();
    },
    retryIfFailed: function (no) { if (state === 'failed' && routeNo === no) request(); },
    clear: function () { reqNo++; clearChart(); clearCompareLine(); state = 'idle'; routeNo = null; compareNo = null; }
  };
})();
