// 관리자 대시보드 지도
// 동작 규칙(임시):
//  1) 처음에는 미완료 신고가 많은 상위 5개 노선을 노선마다 다른 색으로 표시 (색은 노선 목록의 점과 같음)
//     (미완료 신고가 있는 노선이 하나도 없으면 목록의 앞 5개)
//  2) 노선 하나를 클릭하면 그 노선만 표시 (목록, 지도의 선, 검색 후 엔터 모두 같음). 선택한 노선을 다시 누르면 처음 화면으로 돌아감
//  3) 노선을 선택하면 다른 노선 항목에 "비교" 체크가 나타남. 하나를 체크하면 선택 노선 + 비교 노선 두 개만 표시 (최대 2개)
//  4) "상위 5개 노선 보기" 버튼으로 처음 상태로 돌아감
//  5) 방향: 노선마다 대표 1방향만 그림 (신고와 예측은 노선번호 단위라 양방향 합계)
//     목록은 미완료 신고가 있는 노선이 우선순위 순서로 위에 오고, 없는 노선은 "미완료 신고 없는 노선 N개"로 접어 둠 (검색하면 모두 검색됨)
//  6) 선에 마우스를 올리면 툴팁(버스번호, 방향, 미완료 신고 합계)이 뜨고 그 방향의 선 외에는(같은 노선의 반대 방향 포함) 흐려짐.
//     미완료 신고 = 신고 관리의 접수 + 처리중. 마지막으로 누른 노선 하나의 접수와 처리중을 오른쪽 패널에 나눠서 보여 줌
//     (노선 목록의 배지와 호버 툴팁은 모든 노선의 합계)
//  7) 정류장: 전체 보기에서는 표시 안 함 / 노선을 선택하면 기점·종점 표시 /
//     조금 확대(DIRECTION_ZOOM_LEVEL 이하)하면 선 위에 일정 간격으로 진행 방향 V 만 표시 /
//     더 확대(STOP_ZOOM_LEVEL 이하)하면 화면 안의 정류장을 작은 버스 아이콘(진한 회색 한 가지 색)으로 표시, 여러 노선이 지나는 정류장은 아이콘 하나 /
//     정류장에 마우스를 올리면 툴팁에 정류장 이름(여러 노선이면 지나는 노선들)도 표시
//  8) 노선을 선택하면 보이는 방향의 선을 실제 도로 모양으로 바꿈 (서버가 카카오 대중교통 경로를 그때그때 조회).
//     도로 선을 못 받으면(키 없음, 순환 노선, 직행 경로 없음 등) 정류장을 이은 직선 그대로 둠
//  9) 노선을 고르면 그 선이 두 번 깜빡이고, 지금 화면에 그 노선이 하나도 안 보이면 기점으로 화면만 옮김 (확대 수준 유지)
// 10) 노선을 선택한 상태에서 같은 도로를 같은 방향으로 지나는 선들은 화면 기준 몇 px 씩 옆으로 벌려 그림 (applyOffsets 참고).
//     정류장 점은 실제 위치 그대로, 선과 방향 V 만 벌어진 선을 따라감. 전체 보기에서는 벌리지 않음.
//     선마다 아래에 흰 테두리를 깔아서, 같은 색 선이 나란히 있어도 서로 구분됨
// 노선 목록은 /admin/api/routes, 노선 선은 /admin/api/routes/{노선번호}/geometry,
// 도로 선은 /admin/api/routes/{노선번호}/road?brtId= 에서 받음,
// 미완료 신고 수는 /admin/api/routes/report-counts (전체), /admin/api/routes/{노선번호}/report-counts (한 노선) 에서 받음
(function () {
  const mapEl = document.querySelector('#map');
  const messageEl = document.querySelector('#mapMessage');
  const listEl = document.querySelector('#routeList');
  const showAllBtn = document.querySelector('#showAllBtn');
  const searchEl = document.querySelector('#routeSearch');
  const countEl = document.querySelector('#routeCount');
  const emptyEl = document.querySelector('#routeEmpty');
  const moreToggleEl = document.querySelector('#moreToggle');
  const moreListEl = document.querySelector('#routeMoreList');
  const restToggleEl = document.querySelector('#restToggle');
  const restListEl = document.querySelector('#routeRestList');
  const listTopBtn = document.querySelector('#listTopBtn');
  const listScrollEl = document.querySelector('.dash-list-scroll');
  const forecastRouteEl = document.querySelector('#forecastRoute');
  const forecastTitleEl = document.querySelector('#forecastTitle');
  const forecastCompareTitleEl = document.querySelector('#forecastCompareTitle');
  const forecastEmptyEl = document.querySelector('#forecastEmpty');
  // 일별 이용자 수 예측 (예측 서버 결과)
  const ridershipSummaryEl = document.querySelector('#ridershipSummary');
  const ridershipChartBoxEl = document.querySelector('#ridershipChartBox');
  const ridershipCanvasEl = document.querySelector('#ridershipChart');
  const ridershipNoticeEl = document.querySelector('#ridershipNotice');
  const ridershipGapEl = document.querySelector('#ridershipGap');
  const ridershipNormBoxEl = document.querySelector('#ridershipNormBox');
  const ridershipNormEl = document.querySelector('#ridershipNorm');
  // 오른쪽 패널: 노선 하나를 선택하면 그 노선의 미완료 신고 수 (접수 / 처리중)
  const sideEmptyEl = document.querySelector('#sideEmpty');
  const sideRouteEl = document.querySelector('#sideRoute');
  const sideTitleEl = document.querySelector('#sideTitle');
  const sideReceivedEl = document.querySelector('#sideReceived');
  const sideProcessingEl = document.querySelector('#sideProcessing');
  const sideCountsEl = document.querySelector('#sideCounts');
  const sideCompareEl = document.querySelector('#sideCompare');
  const sideUnknownEl = document.querySelector('#sideUnknown');
  const sideAiBtn = document.querySelector('#sideAiBtn');
  const sideAiResultEl = document.querySelector('#sideAiResult');
  const sideAskInputEl = document.querySelector('#sideAskInput');
  const sideAskBtn = document.querySelector('#sideAskBtn');
  const sideAskResultEl = document.querySelector('#sideAskResult');

  const ROUTE_API = '/admin/api/routes';
  const DEFAULT_COUNT = 5;        // 처음 화면에 보여 주는 상위 노선 수
  const LIST_VISIBLE = 5;         // 노선 목록에 펼쳐 두는 우선순위 상위 노선 수 (나머지는 접어 둠)
  const OPEN_REFRESH_MS = 5000;   // 같은 노선을 다시 클릭해도 이 시간(ms) 안에는 신고 수를 다시 받지 않음
  // 노선마다 다른 선 색 (노선 목록 순서대로 돌려 씀). 흰 테두리가 깔려서 서로 구분되고, 지도의 도로 색과 겹치지 않게 진한 색으로 고름
  // 노선이 팔레트보다 많아져 같은 색이 겹치거나 비슷한 색(빨강과 분홍 등)이 함께 비교될 때는 비교 노선의 색만 자동으로 바꿈 (refreshRouteColors)
  const ROUTE_COLORS = ['#2563EB', '#DC2626', '#059669', '#7C3AED', '#EA580C', '#0891B2', '#DB2777', '#65A30D', '#475569', '#B45309', '#CA8A04', '#0F766E'];
  const SIMILAR_COLOR_DISTANCE = 50;   // 두 색의 차이(Lab 거리)가 이 값보다 작으면 비슷한 색으로 봄 (같은 색은 0, 빨강과 분홍 47, 파랑과 보라 34)
  const LINE_WEIGHT = 5;          // 선 굵기
  const LINE_WEIGHT_HOVER = 8;    // 마우스를 올린 선의 굵기
  const LINE_OPACITY = 0.85;      // 보통 선 투명도
  const LINE_OPACITY_HOVER = 1;   // 마우스를 올린 선 투명도 (가장 진하게)
  // 선마다 아래에 깔리는 흰 테두리: 같은 색 선이 나란히 있어도 선마다 경계가 보이게 함
  const OUTLINE_WEIGHT = LINE_WEIGHT + 2;   // 선 양쪽으로 1px 씩 흰 테두리가 보임
  const OUTLINE_OPACITY = 0.9;
  const OUTLINE_OPACITY_DIM = 0.4;          // 마우스를 올렸을 때 다른 노선의 테두리 투명도
  // 마우스를 올린 선 아래에 깔리는 부드러운 그림자 테두리: 검정을 아주 연하게 두 겹으로 깔아 가장자리가 번지듯 보이게 함
  // extra: 올린 선보다 굵은 만큼(px, 양쪽으로 절반씩), opacity: 투명도 (숫자가 작을수록 연함)
  const CASING_COLOR = '#000';
  const CASING_LAYERS = [
    { extra: 10, opacity: 0.12, z: 8 },   // 바깥쪽 (아주 연함)
    { extra: 5, opacity: 0.25, z: 9 }     // 안쪽 (조금 진함)
  ];
  const STOP_ICON_COLOR = '#555'; // 일반 정류장 아이콘 색 (노선과 상관없이 한 가지)
  const LINE_OPACITY_DIM = 0.5;  // 마우스를 올렸을 때 다른 노선의 투명도
  const FLASH_OPACITY = 0.15;     // 노선을 고른 직후 깜빡일 때 가장 흐려지는 투명도
  // 처음 들어왔을 때와 "전체 노선 보기" 때, 모든 노선이 화면에 딱 들어오는 확대 수준에서 이만큼(단계) 더 확대해서 보여 줌
  // (0 이면 모든 노선이 다 들어오게, 1 이면 한 단계 더 확대해서 바깥쪽 노선 끝은 화면 밖으로 조금 잘림)
  const FIT_ZOOM_IN = 2;
  // 처음 들어왔을 때와 "전체 노선 보기" 때 화면 가운데에 두는 곳: 울산광역시청 (위도, 경도)
  // 확대 수준은 위에서 정하고, 위치만 이 좌표로 고정해서 시작 화면이 늘 같은 자리에서 시작함
  const CITY_HALL = { lat: 35.5384, lng: 129.3114 };
  const ARROW_SPACING_PX = 130;   // 진행 방향 V 사이의 화면 간격(px), 너무 촘촘하지 않게
  const DIRECTION_ZOOM_LEVEL = 7; // 이 확대 수준 이하(더 확대)가 되면 선 위에 진행 방향 V 를 표시 (숫자가 작을수록 확대)
  const STOP_ZOOM_LEVEL = 5;  // 이 확대 수준 이하(더 확대)가 되면 기점·종점 외 정류장도 표시 (숫자가 작을수록 확대)
  // 겹치는 선 벌리기 (모두 화면 px 기준이라 확대/축소해도 벌어진 간격이 일정)
  const LANE_GAP_PX = LINE_WEIGHT + 9;  // 겹친 선들의 중심 사이 간격 (선 굵기보다 9px 넓어서 선 사이에 9px 가 비침)
  const OVERLAP_DIST_PX = 6;      // 두 선의 중심이 이 거리보다 가까우면 겹친 것으로 봄
  const SAME_DIR_DOT = 0.7;       // 두 선의 진행 방향이 이 정도(약 45도) 이내로 나란할 때만 겹친 것으로 봄 (교차, 반대 방향은 제외)
  const OFFSET_SAMPLE_PX = 6;     // 선을 이 간격(px)마다 쪼개서 겹침을 확인 (너무 긴 선은 간격을 늘림)
  const OFFSET_MAX_SAMPLES = 2500; // 화면 근처에 있는 부분에서 선 하나를 쪼갤 최대 개수
  const OFFSET_MARGIN_MIN_PX = 200; // 화면 바깥으로 이만큼(또는 화면의 절반 중 큰 쪽)까지만 계산, 더 멀리는 벌리지 않음
  const OFFSET_BRIDGE_SAMPLES = 20; // 겹침 판정이 이 개수(약 120px) 이내로 끊기면 앞뒤 값을 이어서 채움
  const OFFSET_NORMAL_REACH = 6;  // 선을 밀 방향을 정할 때 앞뒤로 이 개수(약 36px)까지 봄
  const OFFSET_SMOOTH = 5;     // 벌어지는 정도를 앞뒤 이만큼의 점으로 평균내서 부드럽게 이어 줌

  const SDK_MESSAGE = '카카오 지도를 불러오지 못했습니다. application.yaml 의 kakao.js.key, '
    + '카카오 개발자 콘솔의 사이트 도메인 등록(http://localhost:8080), 카카오맵 사용 설정을 확인하세요.';

  let map = null;                 // 카카오 지도
  let routes = [];                // 서버에서 받은 노선 목록 [{routeNo, label, directions}]
  // 노선번호 -> { dirs: [{ brtId, label, isLoop, lines: [Polyline...] }], rep: 대표 방향, bounds } (불러온 노선만)
  const layers = {};
  const buttons = {};             // 노선번호 -> 목록의 버튼
  const badges = {};              // 노선번호 -> 목록 버튼 오른쪽 위의 "미완료 N건" 배지
  const items = {};               // 노선번호 -> 목록의 항목(li), 검색으로 걸러낼 때 숨김/표시
  const compareBoxes = {};        // 노선번호 -> 목록 항목 아래의 "비교" 체크 { label, input }
  let mode = 'all';               // 'all': 처음 화면(상위 노선) / 'select': 선택한 노선(과 비교 노선)만
  let selected = new Set();       // select 모드에서 지도에 표시 중인 노선번호 (선택 노선 + 비교 노선, 최대 2개)
  let compareRoute = null;        // 비교 노선번호 (없으면 null)
  let priorityOrder = null;       // 미완료 신고가 있는 노선번호의 우선순위 순서 (서버에서 못 받았으면 null)
  let defaultRoutes = [];         // 처음 화면에 보여 줄 노선번호
  const moreNos = new Set();      // 목록에서 접어 둔 "미완료 신고가 있는 나머지 노선" (우선순위 상위 10개 밖)
  let moreOpen = false;           // 그 노선들을 펼쳤는지
  const restNos = new Set();      // 목록에서 접어 둔 "미완료 신고 없는 노선"
  let restOpen = false;           // 접어 둔 노선을 펼쳤는지
  let stopMarkers = [];           // 지도에 올라가 있는 정류장 표시 [{ routeNo, overlay, el }]
  // 노선번호 -> 미완료(접수 + 처리중) 신고 수 { received, processing, total }
  // 처음에 전체를 한 번 받아 두고(호버 툴팁이 바로 뜨게), 노선을 클릭하면 그 노선만 최신 값으로 갱신
  const openCounts = {};
  let openAllLoaded = false;      // 전체 신고 수를 받았는지 (받았는데 없는 노선이면 0건)
  const openFetchedAt = {};       // 노선번호 -> 마지막으로 갱신한 시각(ms)
  let focusRoute = null;          // 선택한 노선번호 (오른쪽 패널과 예측 영역이 이 노선을 보여 줌)
  let sideRoute = null;           // 오른쪽 패널에 지금 보여 주는 노선번호
  let sideKey = '';               // 오른쪽 패널의 노선 + 비교 노선 조합 (바뀌면 AI 답변을 지움)
  let forecastShown = null;       // 예측 영역에 이용자 수 예측을 불러 둔 노선번호
  let compareShown = null;        // 예측 영역에 불러 둔 비교 노선번호

  // ---------- 안내 문구 ----------
  function showMessage(text, type) {            // type: 'info'(파랑) 또는 'error'(주황)
    messageEl.textContent = text;
    messageEl.classList.toggle('is-info', type === 'info');
    messageEl.hidden = false;
  }
  function hideMessage() { messageEl.hidden = true; }

  function showError(status, routeNo) {
    if (status === 404) {
      showMessage(routeNo + '번 노선은 지도에 표시할 수 없습니다 (없는 노선이거나 데이터가 없습니다).', 'error');
    } else if (status === 400) {
      showMessage('잘못된 노선번호입니다.', 'error');
    } else if (status === 403) {
      showMessage('관리자로 로그인해야 지도를 볼 수 있습니다. 로그인 후 다시 시도하세요.', 'error');
    } else {
      showMessage('노선을 불러오지 못했습니다. 잠시 후 다시 시도하세요.', 'error');
    }
  }

  function findRoute(routeNo) {
    return routes.find(function (r) { return r.routeNo === routeNo; });
  }

  // ---------- 노선 색 ----------
  // 노선 하나의 색: 선, 정류장 방향 표시, 목록의 점과 체크, 툴팁의 점, 차트 막대가 모두 같은 색
  // 비교 중인 두 노선의 색이 같거나 비슷하면 비교 노선만 colorOverride 의 색을 씀
  const colorOverride = {};       // 노선번호 -> 비교 때문에 바꾼 색
  const appliedColor = {};        // 노선번호 -> 지금 목록과 지도에 적용되어 있는 색 (바뀐 노선만 다시 칠하려고)

  function baseColorOf(route) {
    return ROUTE_COLORS[routes.indexOf(route) % ROUTE_COLORS.length];
  }

  function colorOf(route) {
    return colorOverride[route.routeNo] || baseColorOf(route);
  }

  // 두 색이 눈으로 얼마나 다른지 (CIE Lab 거리, 클수록 다름)
  function hexToLab(hex) {
    const n = parseInt(hex.slice(1), 16);
    const lin = function (c) { c /= 255; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
    const r = lin((n >> 16) & 255), g = lin((n >> 8) & 255), b = lin(n & 255);
    const f = function (t) { return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116; };
    const x = (r * 0.4124 + g * 0.3576 + b * 0.1805) / 0.95047;
    const y = r * 0.2126 + g * 0.7152 + b * 0.0722;
    const z = (r * 0.0193 + g * 0.1192 + b * 0.9505) / 1.08883;
    return [116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z))];
  }
  function colorDistance(a, b) {
    const A = hexToLab(a), B = hexToLab(b);
    return Math.hypot(A[0] - B[0], A[1] - B[1], A[2] - B[2]);
  }

  // 선택한 노선과 비교 노선의 색이 같거나 비슷하면, 비교 노선을 팔레트에서 선택 노선과 가장 다른 색으로 바꿈
  function refreshRouteColors() {
    Object.keys(colorOverride).forEach(function (no) { delete colorOverride[no]; });
    const mainNo = focusedRoute(), cmpNo = comparedRoute();
    if (mainNo && cmpNo) {
      const mainColor = colorOf(findRoute(mainNo));
      const cmpColor = baseColorOf(findRoute(cmpNo));
      if (colorDistance(mainColor, cmpColor) < SIMILAR_COLOR_DISTANCE) {
        let best = cmpColor, bestDistance = -1;
        ROUTE_COLORS.forEach(function (c) {
          const d = colorDistance(mainColor, c);
          if (d > bestDistance) { bestDistance = d; best = c; }
        });
        colorOverride[cmpNo] = best;
      }
    }
    // 색이 바뀐 노선만 목록의 점, 체크, 지도의 선을 다시 칠함
    routes.forEach(function (route) {
      const no = route.routeNo, color = colorOf(route);
      if (appliedColor[no] === color) return;
      appliedColor[no] = color;
      if (buttons[no] && buttons[no].firstChild) buttons[no].firstChild.style.background = color;
      if (compareBoxes[no]) compareBoxes[no].input.style.accentColor = color;
      if (layers[no]) {
        layers[no].dirs.forEach(function (d) { d.lines.forEach(function (line) { line.setOptions({ strokeColor: color }); }); });
      }
    });
  }

  // ---------- 미완료 신고 수 (신고 관리의 접수 + 처리중, 완료는 제외) ----------
  // 모르면 null (아직 못 받았거나 받지 못함), 신고가 없는 노선은 0건
  function openCountOf(routeNo) {
    if (openCounts[routeNo]) return openCounts[routeNo];
    return openAllLoaded ? { received: 0, processing: 0, total: 0 } : null;
  }

  // 호버 툴팁에 쓰는 한 줄 글: 합계만
  function openTotalText(routeNo) {
    const c = openCountOf(routeNo);
    if (!c) return '확인 불가';
    return c.total === 0 ? '없음' : c.total + '건';
  }

  async function loadOpenCounts() {
    try {
      const res = await fetch(ROUTE_API + '/report-counts');
      if (!res.ok) throw new Error('report-counts ' + res.status);
      const all = await res.json();
      Object.keys(openCounts).forEach(function (no) { delete openCounts[no]; });
      Object.assign(openCounts, all);
      openAllLoaded = true;
    } catch (e) {
      openAllLoaded = false;      // 신고 수만 못 받은 것이라 지도는 그대로 쓰고, 툴팁에는 '확인 불가'로 보임
    }
  }

  // 미완료 신고가 있는 노선의 우선순위 순서 (임시 기준은 서버가 정함). 못 받으면 목록을 접지 않고 노선번호 순으로 보여 줌
  async function loadPriority() {
    try {
      const res = await fetch(ROUTE_API + '/priority');
      if (!res.ok) throw new Error('priority ' + res.status);
      priorityOrder = (await res.json()).map(function (x) { return x.routeNo; });
    } catch (e) {
      priorityOrder = null;
    }
  }

  // 노선 목록의 배지: 미완료 신고 합계를 한눈에 (모르면 배지를 숨김)
  function renderOpenBadges() {
    Object.keys(badges).forEach(function (no) {
      const c = openCountOf(no);
      const badge = badges[no];
      badge.hidden = !c;
      if (!c) return;
      badge.textContent = c.total === 0 ? '미완료 없음' : '미완료 ' + c.total + '건';
      badge.classList.toggle('is-zero', c.total === 0);
    });
  }

  // ---------- AI 분석 / 질문 (오른쪽 패널, 버튼을 눌렀을 때만 호출) ----------
  // 서버에는 노선번호(와 질문)만 보냄: 서버가 그 노선의 미완료 신고와 이용자 수 예측을 직접 모아서 AI 에 물어봄
  // 분석(네 줄)과 질문은 서로 답변 칸이 따로라서, 질문을 해도 분석 결과가 지워지지 않음
  const aiSlots = [
    { btn: sideAiBtn, result: sideAiResultEl, idle: 'AI 분석', wait: '분석 중...', n: 0 },
    { btn: sideAskBtn, result: sideAskResultEl, idle: '질문하기', wait: '답변 중...', n: 0 }
  ];

  // 노선이 바뀌면 이전 노선의 답변과 질문은 지움 (n 을 올려 두면 늦게 온 답변은 버려짐)
  function clearAi() {
    aiSlots.forEach(function (slot) {
      slot.n++;
      slot.btn.disabled = false;
      slot.btn.textContent = slot.idle;
      slot.result.hidden = true;
      slot.result.classList.remove('is-error');
      slot.result.textContent = '';
    });
    sideAskInputEl.value = '';
  }

  async function callAi(slot, extra) {
    const routeNo = sideRoute;
    if (!routeNo) return;
    const req = ++slot.n;
    slot.btn.disabled = true;
    slot.btn.textContent = slot.wait;
    slot.result.hidden = false;
    slot.result.classList.remove('is-error');
    slot.result.textContent = 'AI가 답변을 만드는 중입니다. 몇 초 걸릴 수 있어요...';

    const compareNo = comparedRoute();
    const body = Object.assign({ routeNo: routeNo }, compareNo ? { compareRouteNo: compareNo } : {}, extra);
    let text, isError = false;
    try {
      const res = await fetch('/admin/api/ai/route', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
      });
      const data = await res.json().catch(function () { return {}; });
      if (!res.ok) throw new Error(data.message || '요청에 실패했습니다. 잠시 후 다시 시도해 주세요.');
      text = data.answer;
    } catch (e) {
      isError = true;
      text = e.message || '요청에 실패했습니다. 잠시 후 다시 시도해 주세요.';
    }
    if (req !== slot.n) return;                   // 그사이 노선이 바뀌었으면 버림 (버튼은 clearAi 가 이미 되돌림)
    slot.result.classList.toggle('is-error', isError);
    slot.result.textContent = text;               // 답변은 textContent 로 넣어 글자 그대로 보여 줌
    slot.btn.disabled = false;
    slot.btn.textContent = slot.idle;
  }

  function askAi() {
    const question = sideAskInputEl.value.trim();
    if (!question) {
      const slot = aiSlots[1];
      slot.result.hidden = false;
      slot.result.classList.add('is-error');
      slot.result.textContent = '질문을 입력해 주세요.';
      sideAskInputEl.focus();
      return;
    }
    callAi(aiSlots[1], { question: question });
  }

  sideAiBtn.addEventListener('click', function () { callAi(aiSlots[0], {}); });
  sideAskBtn.addEventListener('click', askAi);
  sideAskInputEl.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') { e.preventDefault(); askAi(); }
  });

  // 오른쪽 패널의 노선: 선택한 노선 하나 (AI 답변도 이 노선 기준이라, 패널의 숫자와 답변이 항상 같은 노선을 가리킴)
  function focusedRoute() {
    return mode === 'select' && focusRoute && selected.has(focusRoute) ? focusRoute : null;
  }

  // 비교 노선: 선택한 노선이 있고 비교 노선이 체크되어 있을 때만
  function comparedRoute() {
    return focusedRoute() && compareRoute && selected.has(compareRoute) ? compareRoute : null;
  }

  function openText(c) {
    return c ? '접수 ' + c.received + '건 · 처리중 ' + c.processing + '건' : '신고 수를 불러오지 못했습니다';
  }

  // 오른쪽 패널: 그 노선의 미완료 신고 수 (접수와 처리중을 나눠서), 비교 노선이 있으면 한 줄 더, 노선이 없으면 안내 문구만
  function renderSideOpen(routeNo) {
    const compareNo = routeNo ? comparedRoute() : null;
    const key = routeNo ? routeNo + '|' + (compareNo || '') : '';
    if (sideKey !== key) clearAi();               // 노선이나 비교 노선이 바뀌면 이전 답변은 지움 (같은 노선의 갱신에서는 그대로 둠)
    sideKey = key;
    sideRoute = routeNo;
    sideEmptyEl.hidden = !!routeNo;
    sideRouteEl.hidden = !routeNo;
    if (!routeNo) return;

    const route = findRoute(routeNo);
    sideTitleEl.textContent = routeNo + '번 · ' + (route ? route.label : '');
    sideCompareEl.hidden = !compareNo;
    if (compareNo) sideCompareEl.textContent = '비교 노선 ' + compareNo + '번: ' + openText(openCountOf(compareNo));
    const c = openCountOf(routeNo);
    sideCountsEl.hidden = !c;
    sideUnknownEl.hidden = !!c;
    if (!c) return;
    sideReceivedEl.textContent = c.received + '건';
    sideProcessingEl.textContent = c.processing + '건';
    sideReceivedEl.classList.toggle('is-zero', c.received === 0);
    sideProcessingEl.classList.toggle('is-zero', c.processing === 0);
  }

  // 받아 둔 값을 먼저 보여 주고, 최신 값을 받아 오면 바꿈
  function updateSide() {
    const no = focusedRoute();
    renderSideOpen(no);
    if (no) {
      refreshOpenCount(no);
      const cmp = comparedRoute();
      if (cmp) refreshOpenCount(cmp);
    }
  }

  // 노선을 클릭했을 때 그 노선의 최신 신고 수를 받아 와서 갱신 (같은 노선은 OPEN_REFRESH_MS 안에는 다시 받지 않음)
  async function refreshOpenCount(routeNo) {
    const now = Date.now();
    if (openFetchedAt[routeNo] && now - openFetchedAt[routeNo] < OPEN_REFRESH_MS) return;
    openFetchedAt[routeNo] = now;
    try {
      const res = await fetch(ROUTE_API + '/' + encodeURIComponent(routeNo) + '/report-counts');
      if (!res.ok) throw new Error('report-counts ' + res.status);
      openCounts[routeNo] = await res.json();
    } catch (e) {
      delete openFetchedAt[routeNo];  // 실패하면 다음 클릭 때 다시 시도 (화면에는 받아 둔 값을 그대로 둠)
      return;
    }
    renderOpenBadges();
    if (sideRoute === routeNo || (sideRoute && comparedRoute() === routeNo)) renderSideOpen(sideRoute);   // 그사이 다른 노선으로 바뀌었으면 건드리지 않음
  }

  // ---------- 호버 툴팁 (선에 마우스를 올리면 버스번호, 방향, 미완료 신고를 보여 줌) ----------
  // 툴팁은 지도 좌표가 아니라 마우스의 화면 좌표를 직접 따라감 (확대/이동 후에도 어긋나지 않음)
  let tooltipEl = null;           // 툴팁 요소 (지도 영역 안에 둠)
  let mouseX = 0, mouseY = 0;     // 지도 영역 안에서의 마우스 위치

  function createTooltip() {
    tooltipEl = document.createElement('div');
    tooltipEl.className = 'route-tooltip';        // CSS: 절대 위치, 처음에는 숨김, pointer-events: none
    mapEl.parentElement.appendChild(tooltipEl);
    // 마우스 위치는 항상 기록해 두고, 툴팁이 보이는 동안에는 같이 따라다님
    mapEl.addEventListener('mousemove', function (e) {
      const rect = mapEl.getBoundingClientRect();
      mouseX = e.clientX - rect.left;
      mouseY = e.clientY - rect.top;
      if (tooltipEl.style.display === 'block') moveTooltip();
    });
  }

  function moveTooltip() {
    // 툴팁이 지도 밖으로 잘리지 않도록 가장자리에서는 안쪽으로 밀어 줌
    const half = tooltipEl.offsetWidth / 2;
    const maxX = mapEl.clientWidth - half - 8;
    tooltipEl.style.left = Math.max(half + 8, Math.min(mouseX, maxX)) + 'px';
    tooltipEl.style.top = Math.max(tooltipEl.offsetHeight + 20, mouseY) + 'px';
  }

  // stopText 가 있으면(정류장에 마우스를 올린 경우) 정류장 이름 줄을 함께 보여 줌
  function showTooltip(route, dir, stopText) {
    tooltipEl.textContent = '';

    const head = document.createElement('div');
    head.className = 'tt-head';
    const dot = document.createElement('span');
    dot.className = 'dot';
    dot.style.background = colorOf(route);
    head.appendChild(dot);
    head.appendChild(document.createTextNode(route.routeNo + '번'));
    tooltipEl.appendChild(head);

    if (stopText) {
      const stop = document.createElement('div');
      stop.className = 'tt-line';
      stop.textContent = '정류장: ' + stopText;
      tooltipEl.appendChild(stop);
    }

    const direction = document.createElement('div');
    direction.className = 'tt-line';
    direction.textContent = '방향: ' + dir.label;

    // 신고 관리의 미완료(접수 + 처리중) 합계, 접수와 처리중을 나눈 값은 노선을 클릭하면 오른쪽 패널에 보임
    const open = document.createElement('div');
    open.className = 'tt-line tt-open';
    open.textContent = '미완료 신고: ' + openTotalText(route.routeNo);

    tooltipEl.appendChild(direction);
    tooltipEl.appendChild(open);

    // 지금 그려진 선이 도로 기준인지 정류장을 이은 직선인지 (도로 선을 못 받은 노선을 알아볼 수 있게)
    const shape = document.createElement('div');
    shape.className = 'tt-line tt-muted';
    shape.textContent = dir.roadState !== 'ok' ? '선: 정류장을 직선으로 연결'
      : (dir.roadPartial ? '선: 도로 기준 (일부 구간은 직선)' : '선: 도로 기준');
    tooltipEl.appendChild(shape);
    tooltipEl.style.display = 'block';
    moveTooltip();
  }

  // 여러 노선이 지나는 정류장의 툴팁: 정류장 이름과 지나는 노선들
  function showStopTooltip(stopName, entries) {
    tooltipEl.textContent = '';
    const head = document.createElement('div');
    head.className = 'tt-head';
    head.textContent = stopName;
    const nos = [];
    entries.forEach(function (e) { if (nos.indexOf(e.route.routeNo) === -1) nos.push(e.route.routeNo); });
    const line = document.createElement('div');
    line.className = 'tt-line';
    line.textContent = '지나는 노선: ' + nos.map(function (no) { return no + '번'; }).join(', ');
    tooltipEl.appendChild(head);
    tooltipEl.appendChild(line);
    tooltipEl.style.display = 'block';
    moveTooltip();
  }

  function hideTooltip() {
    if (tooltipEl) tooltipEl.style.display = 'none';
  }

  // ---------- 호버 강조: 올린 방향의 선만 진하게, 나머지는 같은 노선의 다른 방향도 포함해 흐리게 ----------
  function eachLine(fn) {
    Object.keys(layers).forEach(function (no) {
      layers[no].dirs.forEach(function (d) {
        d.lines.forEach(function (line) { fn(no, line, d); });
      });
    });
  }

  // 올린 방향의 선: 가장 진하게(투명도 1.0), 굵게, 맨 위로, 연한 그림자 테두리를 깔아 다른 선과 분리해서 보여 줌.
  // 목록에서도 같은 노선 항목을 강조함.
  function applyHover(routeNo, brtId, hoverLine) {
    eachLine(function (no, line, dir) {
      const mine = no === routeNo && dir.brtId === brtId;
      line.setOptions({
        strokeOpacity: mine ? LINE_OPACITY_HOVER : LINE_OPACITY_DIM,
        strokeWeight: line === hoverLine ? LINE_WEIGHT_HOVER : LINE_WEIGHT,
        zIndex: mine ? 10 : 1                      // 올린 방향을 위로 올림
      });
      dir.casings.forEach(function (c) { c.setMap(mine ? map : null); });   // 그림자 테두리는 올린 방향에만
      dir.outlines.forEach(function (o) { o.setOptions({ strokeOpacity: mine ? OUTLINE_OPACITY : OUTLINE_OPACITY_DIM }); });
    });
    stopMarkers.forEach(function (s) {
      const mine = (s.members || [s]).some(function (m) { return m.routeNo === routeNo && m.brtId === brtId; });
      s.el.style.opacity = mine ? '' : LINE_OPACITY_DIM;   // 다른 방향·노선의 정류장도 같이 흐리게 (여러 노선이 지나는 정류장은 그중 하나라도 해당되면 그대로)
    });
    Object.keys(buttons).forEach(function (no) {
      buttons[no].classList.toggle('is-hover', no === routeNo);
    });
  }

  function clearHover() {
    eachLine(function (no, line, dir) {
      line.setOptions({ strokeOpacity: LINE_OPACITY, strokeWeight: LINE_WEIGHT, zIndex: 1 });
      dir.casings.forEach(function (c) { c.setMap(null); });
      dir.outlines.forEach(function (o) { o.setOptions({ strokeOpacity: OUTLINE_OPACITY }); });
    });
    stopMarkers.forEach(function (s) { s.el.style.opacity = ''; });
    Object.keys(buttons).forEach(function (no) { buttons[no].classList.remove('is-hover'); });
  }

  // ---------- 정류장 표시 ----------
  // 전체 보기: 표시 안 함 / 노선 선택: 기점·종점 / 더 확대: 지금 화면 안의 정류장도 표시.
  // 진행 방향 V 는 모든 정류장이 아니라 화면에서 일정 간격(ARROW_SPACING_PX)마다 하나씩만 놓음 (planArrows 참고).
  function clearStops() {
    stopMarkers.forEach(function (s) { s.overlay.setMap(null); });
    stopMarkers = [];
  }

  // 정류장 위치에 점을 보여 주는 상태인지 (노선을 선택한 상태에서 충분히 확대했을 때)
  function stopsDetailShown() {
    return !!map && mode === 'select' && map.getLevel() <= STOP_ZOOM_LEVEL;
  }

  function refreshStops() {
    clearStops();
    if (!map || mode !== 'select') return;
    const zoomedIn = stopsDetailShown();           // 정류장 점(동그라미)을 보여 줄 만큼 확대
    const arrowsShown = map.getLevel() <= DIRECTION_ZOOM_LEVEL;   // 진행 방향 V 를 보여 줄 만큼 확대
    const view = zoomedIn ? map.getBounds() : null;
    const proj = map.getProjection();
    const w = mapEl.clientWidth, h = mapEl.clientHeight;
    const labeled = new Set();                     // 같은 이름의 정류장 이름표는 한 번만 보여 줌 (겹침 방지)
    const shared = {};                             // 정류장 -> { stop, entries: [{ route, dir, color }] }, 여러 노선이 지나는 정류장은 아이콘 하나로 묶음

    visibleRouteNos().forEach(function (no) {
      const route = findRoute(no);
      shownDirs(no).forEach(function (dir) {
        const color = colorOf(route);
        const last = dir.stops.length - 1;
        const plan = planArrows(dir, proj);        // 진행 방향 V 를 놓을 자리 (기점 점 안, 나머지는 선 위)
        dir.stops.forEach(function (stop, i) {
          const role = i === 0 ? '기점' : (i === last ? '종점' : '');
          if (!role && !(zoomedIn && view.contain(stop.pos))) return;
          if (!role) {                             // 일반 정류장은 모았다가 아래에서 아이콘으로 그림
            const key = stop.stopId !== undefined ? String(stop.stopId) : stop.name + stop.pos.getLat() + stop.pos.getLng();
            (shared[key] = shared[key] || { stop: stop, entries: [] }).entries.push({ route: route, dir: dir, color: color });
            return;
          }
          const showLabel = !labeled.has(stop.name);
          labeled.add(stop.name);
          const heading = plan.stopAngles[i] === undefined ? null : plan.stopAngles[i];
          addStopMarker(route, dir, stop, color, role, showLabel, heading);
        });
        if (arrowsShown) {
          plan.mids.forEach(function (m) {
            if (m.x < -20 || m.x > w + 20 || m.y < -20 || m.y > h + 20) return;   // 화면 밖은 건너뜀
            addSegmentArrow(route, dir, m.pos, m.angle, color);
          });
        }
      });
    });

    Object.keys(shared).forEach(function (key) { addStopIcon(shared[key].stop, shared[key].entries); });
  }

  // 진행 방향 V 를 놓을 자리를 정함: 선을 따라 화면에서 약 ARROW_SPACING_PX 마다 하나씩, 모두 선 위에 놓음.
  // 정류장 아이콘에는 방향을 넣지 않음. 기점의 V 만 기점 점 안에 들어감.
  // 반환: { stopAngles: {정류장 순번: 각도}, mids: [{ pos, angle, x, y }] }
  function planArrows(dir, proj) {
    // V 는 지도에 그려진 선을 따라 놓음: 옆으로 벌린 선이 있으면 그 선, 아니면 도로 선, 아니면 정류장을 이은 직선
    const track = dir.shown || dir.track || dir.stops.map(function (s) { return s.pos; });
    const pts = track.map(function (p) { return proj.containerPointFromCoords(p); });
    const cum = [0];                               // 선의 시작에서 각 꼭짓점까지 화면상 누적 거리(px)
    for (let i = 1; i < pts.length; i++) {
      cum.push(cum[i - 1] + Math.hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y));
    }
    const total = cum[cum.length - 1];
    // 정류장이 선의 몇 번째 꼭짓점에 놓이는지 (직선이면 정류장이 곧 꼭짓점)
    const stopIdx = (dir.shown ? dir.shownStopIdx : dir.stopIdx) || dir.stops.map(function (s, j) { return j; });
    const reach = dir.shown || dir.track ? 3 : 1;  // 도로 선은 꼭짓점이 촘촘해서 몇 칸 앞에서부터 방향을 잼
    const stopAngles = {};
    const mids = [];
    const angleAt = function (j) {                 // 정류장으로 들어오는 방향, 기점은 앞으로 나가는 방향
      const k = stopIdx[j];
      const from = k > 0 ? Math.max(0, k - reach) : 0;
      const to = k > 0 ? k : Math.min(pts.length - 1, reach);
      return to === from ? 0 : Math.atan2(pts[to].y - pts[from].y, pts[to].x - pts[from].x);
    };

    stopAngles[0] = angleAt(0);                    // 기점 표시에는 항상 방향 V 가 들어감 (기점 자리에서 시작하지 않도록 아래에서 간격의 절반 뒤부터)
    for (let t = ARROW_SPACING_PX / 2; t <= total; t += ARROW_SPACING_PX) {
      let i = 1;                                  // t 가 속한 구간 (꼭짓점 i-1 ~ i)
      while (i < cum.length - 1 && cum[i] < t) i++;
      const len = cum[i] - cum[i - 1];
      if (len === 0) continue;
      const f = (t - cum[i - 1]) / len;
      const a = track[i - 1], b = track[i];
      mids.push({
        pos: new kakao.maps.LatLng(a.getLat() + (b.getLat() - a.getLat()) * f, a.getLng() + (b.getLng() - a.getLng()) * f),
        angle: Math.atan2(pts[i].y - pts[i - 1].y, pts[i].x - pts[i - 1].x),
        x: pts[i - 1].x + (pts[i].x - pts[i - 1].x) * f,
        y: pts[i - 1].y + (pts[i].y - pts[i - 1].y) * f
      });
    }
    return { stopAngles: stopAngles, mids: mids };
  }

  function addSegmentArrow(route, dir, pos, angle, color) {
    const el = document.createElement('div');
    el.className = 'seg-arrow';
    el.style.transform = 'rotate(' + angle + 'rad)';
    // 선 색 테두리 위에 흰 V: 선 위에서도 읽히도록 두 겹으로 그림
    el.innerHTML = '<svg viewBox="0 0 10 10"><path d="M3 1 L7 5 L3 9" fill="none" stroke="' + color
      + '" stroke-width="3.6" stroke-linecap="round" stroke-linejoin="round"/>'
      + '<path d="M3 1 L7 5 L3 9" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" '
      + 'stroke-linejoin="round"/></svg>';
    const overlay = new kakao.maps.CustomOverlay({ position: pos, content: el, xAnchor: 0.5, yAnchor: 0.5, zIndex: 2 });
    overlay.setMap(map);
    if (el.parentNode) el.parentNode.style.pointerEvents = 'none';   // 선의 호버/클릭을 가로채지 않게
    stopMarkers.push({ routeNo: route.routeNo, brtId: dir.brtId, overlay: overlay, el: el });
  }

  // 일반 정류장 아이콘: 진한 회색 한 가지 색의 작은 버스 아이콘. 진행 방향은 넣지 않음 (선 위의 V 가 보여 줌).
  // 여러 노선이 지나는 정류장은 아이콘 하나로 묶어서 그림
  function addStopIcon(stop, entries) {
    const color = STOP_ICON_COLOR;
    const el = document.createElement('div');
    el.className = 'stop-icon';
    el.style.borderColor = color;
    el.innerHTML = '<svg viewBox="0 0 16 16"><rect x="2" y="2" width="12" height="10" rx="2" fill="' + color + '"/>'
      + '<rect x="3.6" y="3.6" width="8.8" height="3.4" rx="0.8" fill="#fff"/>'
      + '<rect x="5" y="8.3" width="6" height="1.1" rx="0.55" fill="#fff"/>'
      + '<circle cx="5" cy="12.6" r="1.3" fill="' + color + '"/><circle cx="11" cy="12.6" r="1.3" fill="' + color + '"/></svg>';

    el.addEventListener('mouseenter', function () {
      if (entries.length === 1) {                  // 한 노선만 지나면 그 노선을 강조하고 툴팁에 정류장 이름도 보여 줌
        applyHover(entries[0].route.routeNo, entries[0].dir.brtId, entries[0].dir.lines[0]);
        showTooltip(entries[0].route, entries[0].dir, stop.name);
      } else {
        showStopTooltip(stop.name, entries);
      }
    });
    el.addEventListener('mouseleave', function () {
      clearHover();
      hideTooltip();
    });

    const overlay = new kakao.maps.CustomOverlay({ position: stop.pos, content: el, xAnchor: 0.5, yAnchor: 0.5, zIndex: 3 });
    overlay.setMap(map);
    stopMarkers.push({
      routeNo: entries[0].route.routeNo, brtId: entries[0].dir.brtId, overlay: overlay, el: el,
      members: entries.map(function (e) { return { routeNo: e.route.routeNo, brtId: e.dir.brtId }; })
    });
  }

  // 기점·종점 표시 (큰 점, 이름표). 기점에는 진행 방향 V 가 들어감
  function addStopMarker(route, dir, stop, color, role, showLabel, heading) {
    const el = document.createElement('div');
    el.className = 'stop-dot' + (role ? ' is-end' : '') + (heading !== null ? ' has-arrow' : '');
    el.style.borderColor = color;
    if (heading !== null) {
      // 정류장 점 한가운데에 진행 방향을 가리키는 V (점과 방향이 하나로 보이도록)
      const arrow = document.createElement('span');
      arrow.className = 'stop-arrow';
      arrow.style.transform = 'rotate(' + heading + 'rad)';
      arrow.innerHTML = '<svg viewBox="0 0 10 10"><path d="M3 1 L7 5 L3 9" fill="none" stroke="' + color
        + '" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>';
      el.appendChild(arrow);
    }
    if (showLabel) {
      const label = document.createElement('span');
      label.className = 'stop-label';
      label.textContent = role + ' ' + stop.name;
      el.appendChild(label);
    }
    // 정류장에 마우스를 올려도 그 노선을 강조하고, 툴팁에는 정류장 이름을 함께 보여 줌
    el.addEventListener('mouseenter', function () {
      applyHover(route.routeNo, dir.brtId, dir.lines[0]);
      showTooltip(route, dir, stop.name + (role ? ' (' + role + ')' : ''));
    });
    el.addEventListener('mouseleave', function () {
      clearHover();
      hideTooltip();
    });

    const overlay = new kakao.maps.CustomOverlay({
      position: stop.pos, content: el, xAnchor: 0.5, yAnchor: 0.5, zIndex: role ? 4 : 3
    });
    overlay.setMap(map);
    stopMarkers.push({ routeNo: route.routeNo, brtId: dir.brtId, overlay: overlay, el: el });
  }

  // ---------- 노선 선 만들기 (아직 지도에는 올리지 않음) ----------
  // geojson 의 name 은 "114(대왕암공원 방면)" 모양이라 괄호 안만 방향 이름으로 씀
  function directionLabel(name) {
    const m = /^[^(]*\((.*)\)$/.exec(name || '');
    return m ? m[1] : (name || '');
  }

  function buildLayer(route, geojson) {
    const bounds = new kakao.maps.LatLngBounds();
    const dirs = [];

    // 정류장은 방향별로 모아 순서대로 정렬해 둠 (기점 = 첫 정류장, 종점 = 마지막 정류장)
    const stopsByDir = {};
    geojson.features
      .filter(function (f) { return f.properties.kind === 'stop'; })
      .forEach(function (f) {
        const c = f.geometry.coordinates;
        (stopsByDir[f.properties.brt_id] = stopsByDir[f.properties.brt_id] || []).push({
          stopId: f.properties.stop_id,
          name: f.properties.name,
          order: f.properties.order,
          pos: new kakao.maps.LatLng(c[1], c[0])
        });
      });
    Object.keys(stopsByDir).forEach(function (id) {
      stopsByDir[id].sort(function (a, b) { return a.order - b.order; });
    });

    geojson.features
      .filter(function (f) { return f.properties.kind === 'line'; })
      .forEach(function (f) {
        // GeoJSON 은 [경도, 위도], 카카오 LatLng 은 (위도, 경도) 순서라서 뒤집어서 넣음
        const path = f.geometry.coordinates.map(function (c) {
          const p = new kakao.maps.LatLng(c[1], c[0]);
          bounds.extend(p);
          return p;
        });
        const label = directionLabel(f.properties.name);
        dirs.push({
          brtId: f.properties.brt_id,
          label: label,
          isLoop: /순환\s*$/.test(label),
          stops: stopsByDir[f.properties.brt_id] || [],
          path: path,
          lines: [],
          roadState: 'none',      // 도로 선 조회 상태: none(아직) / loading / ok(도로 선 사용) / fail(직선 유지)
          roadPartial: false,     // 도로 선이 일부 구간뿐이고 나머지는 직선인지
          track: null,            // 도로 선을 받았으면 그 좌표(LatLng 목록), 없으면 null
          stopIdx: null,          // 도로 선에서 각 정류장에 가장 가까운 꼭짓점 번호
          shown: null,            // 겹침 때문에 옆으로 벌려 그린 선의 좌표(LatLng 목록), 벌리지 않았으면 null
          shownStopIdx: null      // 벌린 선에서 각 정류장에 해당하는 꼭짓점 번호
        });
      });

    dirs.sort(function (a, b) { return a.brtId < b.brtId ? -1 : a.brtId > b.brtId ? 1 : 0; });

    // 이름이 같은 방향(같은 방면의 다른 운행 경로)은 뒤에 경로 번호를 붙여 구분
    const seen = {};
    dirs.forEach(function (d) {
      seen[d.label] = (seen[d.label] || 0) + 1;
      if (seen[d.label] > 1) d.label += ' (경로 ' + seen[d.label] + ')';
    });

    dirs.forEach(function (dir) {
      const line = new kakao.maps.Polyline({
        path: dir.path,
        strokeColor: colorOf(route),
        strokeWeight: LINE_WEIGHT,
        strokeOpacity: LINE_OPACITY,
        zIndex: 1
      });
      // 지도의 선을 클릭해도 목록에서 누른 것과 같이 동작
      kakao.maps.event.addListener(line, 'click', function () { selectRoute(route.routeNo, true); });
      // 마우스를 올리면 툴팁이 뜨고 그 노선 외에는 흐려짐, 벗어나면 원래대로
      kakao.maps.event.addListener(line, 'mouseover', function () {
        applyHover(route.routeNo, dir.brtId, line);
        showTooltip(route, dir);
      });
      kakao.maps.event.addListener(line, 'mouseout', function () {
        clearHover();
        hideTooltip();
      });
      dir.lines.push(line);
      // 흰 테두리: 선보다 조금 굵은 흰 선을 모든 선 아래(zIndex 0)에 깜
      dir.outlines = [new kakao.maps.Polyline({
        path: dir.path, strokeColor: '#fff', strokeWeight: OUTLINE_WEIGHT, strokeOpacity: OUTLINE_OPACITY, zIndex: 0
      })];
      // 호버 그림자 테두리: 같은 경로의 더 굵고 연한 선들. 평소에는 지도에 올리지 않고, 호버한 방향에만 선 바로 아래에 깖
      dir.casings = CASING_LAYERS.map(function (c) {
        return new kakao.maps.Polyline({
          path: dir.path, strokeColor: CASING_COLOR, strokeWeight: LINE_WEIGHT_HOVER + c.extra,
          strokeOpacity: c.opacity, zIndex: c.z
        });
      });
    });

    // 대표 방향: 순환이 아닌 방향 중 방향ID 가 가장 작은 것 (순환뿐이면 그중 첫째)
    const nonLoop = dirs.filter(function (d) { return !d.isLoop; });
    layers[route.routeNo] = { dirs: dirs, rep: (nonLoop.length ? nonLoop : dirs)[0], bounds: bounds };
  }

  // 노선 하나의 선 데이터를 서버에서 받아 만듦 (성공하면 true)
  async function loadRoute(route) {
    if (layers[route.routeNo]) return true;
    try {
      const res = await fetch(ROUTE_API + '/' + encodeURIComponent(route.routeNo) + '/geometry');
      if (!res.ok) {
        markMissing(route.routeNo, true);
        showError(res.status, route.routeNo);
        return false;
      }
      buildLayer(route, await res.json());
      markMissing(route.routeNo, false);
      return true;
    } catch (e) {
      console.error('노선 ' + route.routeNo + ' 처리 중 오류', e);
      markMissing(route.routeNo, true);
      showError(0, route.routeNo);
      return false;
    }
  }

  // ---------- 도로를 따라가는 선 (카카오 대중교통 경로, 서버가 그때그때 조회) ----------
  // 노선을 선택해서 지도에 보이는 방향만 요청함. 순환 노선, 직행 경로가 없거나 정류장과 맞지 않는 경우 등
  // 서버가 못 쓴다고 하면 정류장을 이은 직선을 그대로 씀.
  // 받은 좌표는 이 페이지 메모리에만 두고(새로고침하면 사라짐) 서버나 DB 에는 저장하지 않음.
  let roadDisabled = false;       // 서버에 REST 키가 없거나 카카오 연결이 안 되면(503) 이번 접속 동안은 더 요청하지 않음

  async function ensureRoad(route, dir) {
    if (roadDisabled || dir.isLoop || dir.roadState !== 'none') return;   // 순환 노선은 요청해도 결과가 없음
    dir.roadState = 'loading';
    try {
      const res = await fetch(ROUTE_API + '/' + encodeURIComponent(route.routeNo)
        + '/road?brtId=' + encodeURIComponent(dir.brtId));
      if (!res.ok) {
        dir.roadState = 'fail';
        if (res.status === 503 || res.status === 403) roadDisabled = true;   // 키·연결 문제나 로그인이 풀린 경우는 더 요청하지 않음
        return;
      }
      const body = await res.json();
      dir.roadPartial = !!body.partial;           // true: 카카오 경로와 맞는 구간만 도로 선, 나머지는 직선
      applyRoad(dir, body.points);
    } catch (e) {
      dir.roadState = 'fail';
      console.warn('노선 ' + route.routeNo + ' 도로 선을 불러오지 못해 직선을 유지합니다', e);
    }
  }

  // 받은 도로 선으로 바꿈: 선, 그림자 테두리의 경로를 교체하고 정류장 점과 방향 V 를 새 선 기준으로 다시 정함
  function applyRoad(dir, points) {
    // 서버 좌표는 [경도, 위도]
    dir.track = points.map(function (c) { return new kakao.maps.LatLng(c[1], c[0]); });
    dir.stopIdx = stopIndexOnTrack(dir.stops, dir.track);
    dir.shown = null;
    dir.shownStopIdx = null;
    setDirPath(dir, dir.track);
    dir.roadState = 'ok';
    applyOffsets();                               // 선 모양이 바뀌었으니 겹침을 다시 확인
    refreshStops();
  }

  // ---------- 겹치는 선 벌리기 ----------
  // 같은 도로를 같은 방향으로 지나는 선들이 서로를 가리지 않도록, 겹친 구간에서만 선을 옆으로 조금씩 밀어 나란히 그림.
  // 원리 (모두 화면 px 좌표에서 계산):
  //  1) 화면 안과 그 근처(여유 폭만큼)에 있는 선을 OFFSET_SAMPLE_PX 간격의 점으로 쪼갬 (원래 꼭짓점은 그대로 유지)
  //     (많이 확대하면 선이 수만 px 로 길어져서, 화면 근처만 쪼개야 간격이 촘촘하게 유지됨)
  //  2) 각 점에서 OVERLAP_DIST_PX 이내에 진행 방향이 나란한 다른 노선의 점이 있으면 "겹침", 겹친 선들을 한 그룹으로 봄
  //  3) 그룹 안에서 노선번호 순서로 차선(왼쪽부터)을 정하고, 차선마다 선의 수직 방향으로 LANE_GAP_PX 씩 밀 거리를 정함
  //     (LANE_GAP_PX 가 14 이면 2개는 -7, +7px / 3개는 -14, 0, +14px). 순서가 고정이라 같은 노선은 늘 같은 쪽에 그려짐
  //  4) 밀 거리를 앞뒤 점과 평균내서 겹침의 시작과 끝에서 선이 부드럽게 벌어지고 모이게 함
  //  5) 밀린 점을 다시 지도 좌표로 바꿔 선을 교체. 겹치지 않는 구간은 0px 이라 원래 선 그대로
  // 확대 수준이 바뀌면 px 간격이 달라지므로 다시 계산하고, 이동했을 때는 여유 폭의 절반 넘게 움직였을 때만 다시 계산함.
  // 노선을 선택한 상태(select 모드)에서만 벌림. 전체 보기에서는 선이 너무 많아서 원래 선 그대로 둠.
  let offsetSig = null;           // 마지막으로 계산한 입력(확대 수준, 보이는 방향, 선 모양), 같으면 다시 계산하지 않음
  let offsetAnchor = null;        // 마지막으로 계산할 때 울산 중심점이 화면에서 있던 위치(px), 지도를 얼마나 옮겼는지 재는 기준

  function basePath(dir) { return dir.track || dir.path; }

  // 선, 그림자 테두리, 흰 테두리의 경로를 한꺼번에 교체
  function setDirPath(dir, path) {
    dir.lines.concat(dir.casings, dir.outlines).forEach(function (line) { line.setPath(path); });
  }

  // 선 하나를 화면 px 좌표의 촘촘한 점들로 바꿈. x, y: 위치, tx, ty: 진행 방향(길이 1), vmap: 원래 꼭짓점 번호 -> 점 번호,
  // live: 화면 근처(win 안)에 있는 점인지 (아닌 점은 쪼개지 않고 겹침도 보지 않음)
  function sampleDir(dir, proj, win) {
    const base = basePath(dir).map(function (p) { return proj.containerPointFromCoords(p); });
    const segLive = [];                           // 꼭짓점 i ~ i+1 선분이 win 과 겹치는지
    let near = 0;                                 // 화면 근처 선분의 길이 합 (px)
    for (let i = 0; i < base.length - 1; i++) {
      const p = base[i], q = base[i + 1];
      const hit = Math.max(p.x, q.x) >= win.x0 && Math.min(p.x, q.x) <= win.x1
        && Math.max(p.y, q.y) >= win.y0 && Math.min(p.y, q.y) <= win.y1;
      segLive.push(hit);
      if (hit) near += Math.hypot(q.x - p.x, q.y - p.y);
    }
    const step = Math.max(OFFSET_SAMPLE_PX, near / OFFSET_MAX_SAMPLES);

    const x = [], y = [], vmap = [], live = [];
    base.forEach(function (p, i) {
      vmap.push(x.length);
      x.push(p.x);
      y.push(p.y);
      live.push(!!(segLive[i - 1] || segLive[i]));
      if (i === base.length - 1 || !segLive[i]) return;
      const q = base[i + 1];
      const len = Math.hypot(q.x - p.x, q.y - p.y);
      for (let d = step; d < len - 0.001; d += step) {   // 화면 근처 꼭짓점 사이를 일정 간격으로 쪼갬
        x.push(p.x + (q.x - p.x) * d / len);
        y.push(p.y + (q.y - p.y) * d / len);
        live.push(true);
      }
    });

    const n = x.length, tx = new Array(n), ty = new Array(n);
    for (let k = 0; k < n; k++) {
      const a = Math.max(0, k - 1), b = Math.min(n - 1, k + 1);
      const len = Math.hypot(x[b] - x[a], y[b] - y[a]);
      tx[k] = len ? (x[b] - x[a]) / len : 0;
      ty[k] = len ? (y[b] - y[a]) / len : 0;
    }

    // 선을 옆으로 밀 방향(왼쪽 수직, 길이 1): 앞뒤 OFFSET_NORMAL_REACH 개 점을 잇는 넓은 구간의 방향으로 구함.
    // 정류장 쪽으로 잠깐 갔다 돌아오는 뾰족한 꺾임에서는 점마다 방향이 뒤집혀 선이 고리 모양으로 꼬이므로,
    // 넓은 구간의 방향이 뚜렷하지 않은 점은 바로 앞 점의 방향을 그대로 씀
    const nx = new Array(n), ny = new Array(n);
    let px = 0, py = 0, have = false;
    for (let k = 0; k < n; k++) {
      const a = Math.max(0, k - OFFSET_NORMAL_REACH), b = Math.min(n - 1, k + OFFSET_NORMAL_REACH);
      let arc = 0;
      for (let j = a + 1; j <= b; j++) arc += Math.hypot(x[j] - x[j - 1], y[j] - y[j - 1]);
      const dx = x[b] - x[a], dy = y[b] - y[a], len = Math.hypot(dx, dy);
      if (len > 0 && len > 0.5 * arc) {
        px = dy / len;
        py = -dx / len;
        have = true;
      } else if (!have) {
        px = ty[k];
        py = -tx[k];
      }
      nx[k] = px;
      ny[k] = py;
    }
    return { n: n, x: x, y: y, tx: tx, ty: ty, nx: nx, ny: ny, vmap: vmap, live: live };
  }

  function applyOffsets() {
    if (!map) return;
    const entries = [];
    if (mode === 'select') {
      visibleRouteNos().forEach(function (no) {
        shownDirs(no).forEach(function (dir) { entries.push({ routeNo: no, dir: dir }); });
      });
    }
    // 차선 순서: 노선번호(숫자 순), 같은 노선이면 방향ID 순 (항상 같은 순서여야 선이 늘 같은 쪽에 그려짐)
    entries.sort(function (a, b) {
      const byNo = a.routeNo.localeCompare(b.routeNo, undefined, { numeric: true });
      return byNo || (a.dir.brtId < b.dir.brtId ? -1 : a.dir.brtId > b.dir.brtId ? 1 : 0);
    });

    const sig = map.getLevel() + '|' + entries.map(function (e) {
      return e.routeNo + ':' + e.dir.brtId + ':' + (e.dir.track ? e.dir.track.length : 'p');
    }).join(',');
    // 화면 근처(여유 폭 포함)만 계산하므로, 지도를 여유 폭의 절반 넘게 옮기면 새로 보이는 부분을 위해 다시 계산
    const proj = map.getProjection();
    const margin = Math.max(OFFSET_MARGIN_MIN_PX, Math.max(mapEl.clientWidth, mapEl.clientHeight) / 2);
    const anchor = proj.containerPointFromCoords(new kakao.maps.LatLng(35.5384, 129.3114));
    const panned = !!offsetAnchor && Math.hypot(anchor.x - offsetAnchor.x, anchor.y - offsetAnchor.y) > margin / 2;
    if (sig === offsetSig && !panned) return;
    offsetSig = sig;
    offsetAnchor = anchor;

    Object.keys(layers).forEach(function (no) {   // 전에 벌려 둔 선은 먼저 원래대로
      layers[no].dirs.forEach(function (d) {
        if (!d.shown) return;
        d.shown = null;
        d.shownStopIdx = null;
        setDirPath(d, basePath(d));
      });
    });
    if (entries.length < 2) return;

    const win = { x0: -margin, y0: -margin, x1: mapEl.clientWidth + margin, y1: mapEl.clientHeight + margin };
    const samples = entries.map(function (e) { return sampleDir(e.dir, proj, win); });

    // 점들을 OVERLAP_DIST_PX 칸의 격자에 넣어 두면 가까운 점을 주변 9칸만 보고 찾을 수 있음
    const cell = OVERLAP_DIST_PX;
    const grid = {};
    samples.forEach(function (s, i) {
      for (let k = 0; k < s.n; k++) {
        if (!s.live[k]) continue;
        const key = Math.floor(s.x[k] / cell) + ',' + Math.floor(s.y[k] / cell);
        (grid[key] = grid[key] || []).push(i * 65536 + k);
      }
    });

    const near2 = OVERLAP_DIST_PX * OVERLAP_DIST_PX;
    samples.forEach(function (s, i) {
      // 2~3) 점마다 겹친 선이 몇 개인지, 내가 몇 번째 차선인지 -> 밀 거리(px, 진행 방향의 왼쪽이 +)
      const raw = new Array(s.n).fill(0);
      const grouped = new Array(s.n).fill(false);  // 겹친 선이 있다고 판정된 점
      for (let k = 0; k < s.n; k++) {
        if (!s.live[k]) continue;
        const cx = Math.floor(s.x[k] / cell), cy = Math.floor(s.y[k] / cell);
        const found = {};
        let count = 0, before = 0;                // count: 겹친 다른 선의 수, before: 그중 나보다 순서가 앞인 선의 수
        for (let dx = -1; dx <= 1; dx++) {
          for (let dy = -1; dy <= 1; dy++) {
            const list = grid[(cx + dx) + ',' + (cy + dy)];
            if (!list) continue;
            list.forEach(function (code) {
              const j = Math.floor(code / 65536), m = code % 65536;
              if (j === i || found[j]) return;
              const o = samples[j];
              const ddx = o.x[m] - s.x[k], ddy = o.y[m] - s.y[k];
              if (ddx * ddx + ddy * ddy > near2) return;
              if (o.tx[m] * s.tx[k] + o.ty[m] * s.ty[k] < SAME_DIR_DOT) return;   // 교차하거나 반대 방향이면 겹침이 아님
              found[j] = true;
              count++;
              if (j < i) before++;
            });
          }
        }
        if (count) {
          raw[k] = (before - count / 2) * LANE_GAP_PX;
          grouped[k] = true;
        }
      }

      // 선이 정류장 쪽으로 잠깐 꺾이는 곳처럼 진행 방향이 달라져서 겹침 판정이 짧게 끊기면 선이 그 자리에서만 붙어 보임.
      // 양쪽 모두 겹친 구간이고 끊긴 길이가 짧으면(OFFSET_BRIDGE_SAMPLES 이내) 양쪽 값을 이어서 채움
      for (let k = 1; k < s.n;) {
        if (grouped[k] || !grouped[k - 1]) { k++; continue; }
        let e = k;
        while (e < s.n && !grouped[e]) e++;
        if (e < s.n && e - k <= OFFSET_BRIDGE_SAMPLES) {
          for (let j = k; j < e; j++) raw[j] = raw[k - 1] + (raw[e] - raw[k - 1]) * (j - k + 1) / (e - k + 1);
        }
        k = e;
      }

      // 4) 앞뒤 점과 평균 (가까운 점일수록 비중이 큼)
      const off = new Array(s.n);
      let biggest = 0;
      for (let k = 0; k < s.n; k++) {
        let sum = 0, weight = 0;
        for (let d = -OFFSET_SMOOTH; d <= OFFSET_SMOOTH; d++) {
          const m = k + d;
          if (m < 0 || m >= s.n) continue;
          const w = OFFSET_SMOOTH + 1 - Math.abs(d);
          sum += raw[m] * w;
          weight += w;
        }
        off[k] = sum / weight;
        biggest = Math.max(biggest, Math.abs(off[k]));
      }
      if (biggest < 0.5) return;                  // 겹치는 곳이 없으면 원래 선 그대로

      // 5) 선의 왼쪽 수직 방향(화면에서 (ty, -tx))으로 밀어서 지도 좌표로 되돌림
      const dir = entries[i].dir;
      const moved = new Array(s.n);
      for (let k = 0; k < s.n; k++) {
        moved[k] = proj.coordsFromContainerPoint(
          new kakao.maps.Point(s.x[k] + s.nx[k] * off[k], s.y[k] + s.ny[k] * off[k]));
      }
      const baseIdx = dir.stopIdx || dir.stops.map(function (st, j) { return j; });
      dir.shown = moved;
      dir.shownStopIdx = baseIdx.map(function (k) { return s.vmap[Math.min(k, s.vmap.length - 1)]; });
      setDirPath(dir, moved);
    });
  }

  // 각 정류장이 도로 선의 몇 번째 꼭짓점에 가장 가까운지: 앞 정류장보다 뒤에서만 찾아 순서가 뒤바뀌지 않게 함
  function stopIndexOnTrack(stops, track) {
    const idx = [];
    let from = 0;
    stops.forEach(function (s) {
      let best = from, bestD = Infinity;
      for (let k = from; k < track.length; k++) {
        const dLat = track[k].getLat() - s.pos.getLat();
        const dLng = (track[k].getLng() - s.pos.getLng()) * 0.82;   // 울산 위도에서 경도 1도는 위도 1도의 약 0.82배 길이
        const d = dLat * dLat + dLng * dLng;
        if (d < bestD) { bestD = d; best = k; }
      }
      idx.push(best);
      from = best;
    });
    return idx;
  }

  // 지금 지도에 보이는 방향들의 도로 선을 요청 (이미 받았거나 요청 중이거나 못 쓰는 방향은 다시 요청하지 않음)
  function ensureRoads() {
    if (mode !== 'select') return;
    visibleRouteNos().forEach(function (no) {
      shownDirs(no).forEach(function (dir) { ensureRoad(findRoute(no), dir); });
    });
  }

  // ---------- 화면에 반영 ----------
  function markMissing(routeNo, missing) {
    if (buttons[routeNo]) buttons[routeNo].classList.toggle('is-missing', missing);
  }

  function visibleRouteNos() {
    if (mode === 'all') return defaultRoutes.filter(function (no) { return layers[no]; });
    return Array.from(selected).filter(function (no) { return layers[no]; });
  }

  // 지도에 그릴 방향: 노선마다 대표 1방향 (신고와 예측은 노선 전체 기준이라 방향을 고르지 않음)
  function shownDirs(routeNo) {
    return [layers[routeNo].rep];
  }

  function fitToVisible() {
    const nos = visibleRouteNos();
    if (nos.length === 0) return;
    const all = new kakao.maps.LatLngBounds();
    nos.forEach(function (no) {
      all.extend(layers[no].bounds.getSouthWest());
      all.extend(layers[no].bounds.getNorthEast());
    });
    map.setBounds(all, 40, 40, 40, 40);          // 보이는 노선이 모두 화면에 들어오게 자동 확대
    if (FIT_ZOOM_IN > 0) {                       // 거기서 FIT_ZOOM_IN 단계 더 확대 (숫자가 작을수록 확대)
      map.setLevel(Math.max(1, map.getLevel() - FIT_ZOOM_IN), { animate: false });
    }
    map.setCenter(new kakao.maps.LatLng(CITY_HALL.lat, CITY_HALL.lng));   // 화면 가운데는 울산광역시청
  }

  // 클릭했을 때는 지금 보고 있는 화면(확대 수준, 위치)을 그대로 둠.
  // 단, 지금 보이는 방향의 선과 정류장이 화면 안에 하나도 없으면 아무것도 안 보이니까,
  // 확대 수준은 두고 그 방향의 기점이 화면 가운데 오도록 위치만 옮김.
  // (노선의 사각형 범위가 화면과 겹치는지로 보면, 선은 화면 밖인데 사각형만 걸쳐서 안 옮겨지는 경우가 생김)
  function panToStartIfHidden(routeNo) {
    const view = map.getBounds();
    const dirs = shownDirs(routeNo);
    const inView = dirs.some(function (d) {
      return d.stops.some(function (s) { return view.contain(s.pos); })
        || d.path.some(function (p) { return view.contain(p); });
    });
    if (inView || dirs.length === 0 || dirs[0].stops.length === 0) return;
    map.panTo(dirs[0].stops[0].pos);
  }

  // 방금 고른 노선의 선을 짧게 두 번 깜빡여서 "이게 방금 고른 노선"임을 알려 줌.
  // 굵기는 그대로 두고 투명도만 잠깐 낮췄다 되돌림 (마우스를 올려 둔 동안에는 건드리지 않음)
  function flashRoute(routeNo) {
    const lines = [];
    shownDirs(routeNo).forEach(function (d) { d.lines.forEach(function (l) { lines.push(l); }); });
    const set = function (dip) {
      if (tooltipEl && tooltipEl.style.display === 'block') return;
      lines.forEach(function (l) {
        l.setOptions({ strokeOpacity: dip ? FLASH_OPACITY : LINE_OPACITY, zIndex: dip ? 10 : 1 });
      });
    };
    [0, 260].forEach(function (t) {
      setTimeout(function () { set(true); }, t);
      setTimeout(function () { set(false); }, t + 130);
    });
  }

  function render() {
    hideTooltip();                                // 선이 바뀌면 남아 있을 수 있는 호버 표시를 정리
    clearHover();
    refreshRouteColors();                         // 비교 노선의 색이 선택 노선과 겹치면 다른 색으로 (아래 선, 정류장, 차트가 이 색을 씀)
    const visible = new Set(visibleRouteNos());
    Object.keys(layers).forEach(function (no) {
      const shown = visible.has(no) ? new Set(shownDirs(no)) : new Set();
      layers[no].dirs.forEach(function (d) {
        d.lines.concat(d.outlines).forEach(function (line) {
          line.setMap(shown.has(d) ? map : null);
        });
      });
    });
    // 목록의 버튼: 선택한 노선과 비교 노선만 강조 (처음 화면에서는 강조 없음)
    // 왼쪽 체크 칸: 선택한 노선이 있을 때만 보임(자리는 늘 차지해서 목록이 밀리지 않음). 선택한 노선과 비교 노선은 노선 색으로 체크,
    // 선택한 노선은 눌러도 바뀌지 않음
    const mainNo = mode === 'select' ? focusRoute : null;
    const cmpNo = comparedRoute();
    Object.keys(buttons).forEach(function (no) {
      buttons[no].classList.toggle('is-active', no === mainNo);
      buttons[no].classList.toggle('is-compare', no === cmpNo);
      const box = compareBoxes[no];
      box.label.classList.toggle('is-off', !mainNo);
      box.label.classList.toggle('is-main', no === mainNo);
      box.label.title = no === mainNo ? '선택한 노선' : '비교 노선으로 선택';
      box.input.checked = no === mainNo || no === cmpNo;
      box.input.tabIndex = no === mainNo ? -1 : 0;
    });
    applyFilter();                                // 검색 중에도 선택한 노선은 목록에 남겨 둠
    applyOffsets();                               // 겹치는 선은 옆으로 벌림 (정류장과 방향 V 가 벌어진 선을 따라가도록 먼저 함)
    refreshStops();
    updateFocusViews();
    ensureRoads();                               // 보이는 방향의 도로 선을 받아 오면 선과 방향 V 가 도로 모양으로 바뀜
  }

  // ---------- 일별 이용자 수 예측 (예측 영역 아래) ----------
  // /admin/api/routes/{노선번호}/ridership: 예측 서버(ai_service)가 만든 오늘부터 14일의 하루 이용자 수(명)
  // status 가 OK 가 아니면(서버 꺼짐, 모델이 모르는 노선, 예측 가능 기간 밖) 차트 대신 message 를 보여 줌
  // 두 노선을 비교할 때는 같은 차트에 같은 눈금으로 그림. 규모 차이가 크면 안내하고, 노선마다 자기 최대값 기준으로 볼 수도 있음
  const GAP_RATIO = 3;            // 두 노선의 하루 평균 이용자 수가 이 배수 이상 차이 나면 안내
  let ridershipChart = null;
  const ridershipReqNo = { main: 0, compare: 0 };       // 응답이 늦게 와도 지금 노선의 것만 그리도록 요청마다 번호를 매김
  const ridershipData = { main: null, compare: null };  // 받은 결과 (아직 못 받았으면 null)
  let ridershipState = 'idle';    // idle(아직 안 받음) / loading / ok / failed (선택한 노선이 failed 일 때만 같은 노선을 다시 눌러 재시도)

  function clearRidershipChart() {
    if (ridershipChart) { ridershipChart.destroy(); ridershipChart = null; }
  }

  function showRidershipMessage(text) {
    ridershipState = 'failed';
    clearRidershipChart();
    ridershipChartBoxEl.hidden = true;
    ridershipNoticeEl.textContent = '';
    ridershipGapEl.hidden = true;
    ridershipNormBoxEl.hidden = true;
    ridershipSummaryEl.textContent = text;
    ridershipSummaryEl.classList.add('is-error');
  }

  function usable(r) { return !!r && r.status === 'OK' && r.days.length > 0; }

  function dayAverage(r) {
    return r.days.reduce(function (sum, d) { return sum + d.predicted; }, 0) / r.days.length;
  }

  // 받아 둔 선택 노선(과 비교 노선)의 결과로 요약, 안내, 차트를 그림
  function drawRidership() {
    const main = ridershipData.main;
    const mainNo = focusedRoute();
    if (!main || !mainNo) return;                 // 선택 노선의 결과를 기다리는 중 (불러오는 중 문구가 이미 떠 있음)
    if (!usable(main)) {
      showRidershipMessage(main.message || '이용자 수 예측을 표시할 수 없습니다.');
      return;
    }
    ridershipState = 'ok';
    ridershipSummaryEl.classList.remove('is-error');

    const compareNo = comparedRoute();
    const cmp = compareNo ? ridershipData.compare : null;
    const sets = [{ no: mainNo, r: main }];
    const range = main.days[0].date + ' ~ ' + main.days[main.days.length - 1].date;
    const avgText = function (r) { return '약 ' + Math.round(dayAverage(r)).toLocaleString() + '명'; };
    let summary = range + ' 하루 평균 ' + avgText(main);
    if (compareNo) {
      summary = range + ' 하루 평균 · ' + mainNo + '번 ' + avgText(main);
      if (!cmp) {
        summary += ' · ' + compareNo + '번 불러오는 중...';
      } else if (!usable(cmp)) {
        summary += ' · ' + compareNo + '번은 표시할 수 없습니다 (' + (cmp.message || '예측 없음') + ')';
      } else {
        summary += ' · ' + compareNo + '번 ' + avgText(cmp);
        sets.push({ no: compareNo, r: cmp });
      }
    }
    ridershipSummaryEl.textContent = summary;
    ridershipNoticeEl.textContent = main.notice + ' (모델 학습 ' + main.trainedEnd + '까지)';

    // 두 노선을 같은 차트에 그릴 수 있을 때만: 규모 차이 안내와 최대값 기준 선택
    const both = sets.length > 1;
    ridershipNormBoxEl.hidden = !both;
    ridershipGapEl.hidden = true;
    if (both) {
      const hi = Math.max(dayAverage(sets[0].r), dayAverage(sets[1].r));
      const lo = Math.min(dayAverage(sets[0].r), dayAverage(sets[1].r));
      if (lo > 0 && hi / lo >= GAP_RATIO) {
        ridershipGapEl.hidden = false;
        ridershipGapEl.textContent = '두 노선의 하루 평균 이용자 수가 약 ' + (hi / lo).toFixed(1)
          + '배 차이 납니다. 같은 눈금이라 작은 노선의 변화가 잘 보이지 않을 수 있으니, 아래 "자기 최대값 기준"으로 모양을 비교해 보세요.';
      }
    }

    clearRidershipChart();
    if (typeof Chart === 'undefined') {
      ridershipChartBoxEl.hidden = true;
      ridershipSummaryEl.appendChild(document.createTextNode(' (차트를 불러오지 못했습니다)'));
      return;
    }
    const norm = both && ridershipNormEl.checked;
    const datasets = sets.map(function (x) {
      const byDate = {};
      x.r.days.forEach(function (d) { byDate[d.date] = d.predicted; });
      const raw = main.days.map(function (d) { return byDate[d.date] === undefined ? null : byDate[d.date]; });
      const max = Math.max.apply(null, raw.filter(function (v) { return v !== null; }));
      return {
        label: x.no + '번',
        data: norm ? raw.map(function (v) { return v === null ? null : Math.round(v / max * 1000) / 10; }) : raw,
        raw: raw,
        backgroundColor: colorOf(findRoute(x.no))
      };
    });
    ridershipChartBoxEl.hidden = false;
    ridershipChart = new Chart(ridershipCanvasEl, {
      type: 'bar',
      data: {
        labels: main.days.map(function (d) { return d.date.slice(5) + '(' + d.dayOfWeek + ')'; }),
        datasets: datasets
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        scales: { y: { beginAtZero: true, ticks: { callback: function (v) { return norm ? v + '%' : v.toLocaleString(); } } },
                  x: { ticks: { maxRotation: 60, autoSkip: false, font: { size: 10 } } } },
        plugins: {
          legend: { display: both, position: 'bottom', labels: { boxWidth: 12 } },
          tooltip: { callbacks: { label: function (c) {
            const v = c.dataset.raw[c.dataIndex];
            const text = v === null ? '-' : v.toLocaleString() + '명';
            return c.dataset.label + ': ' + text + (norm && v !== null ? ' (최대의 ' + c.parsed.y + '%)' : '');
          } } }
        }
      }
    });
  }

  // slot: 'main'(선택한 노선) / 'compare'(비교 노선)
  async function loadRidership(routeNo, slot) {
    const req = ++ridershipReqNo[slot];
    ridershipData[slot] = null;
    if (slot === 'main') {
      ridershipState = 'loading';
      clearRidershipChart();
      ridershipChartBoxEl.hidden = true;
      ridershipNoticeEl.textContent = '';
      ridershipGapEl.hidden = true;
      ridershipNormBoxEl.hidden = true;
      ridershipSummaryEl.classList.remove('is-error');
      ridershipSummaryEl.textContent = '이용자 수 예측을 불러오는 중...';
    }
    try {
      const res = await fetch(ROUTE_API + '/' + encodeURIComponent(routeNo) + '/ridership');
      if (!res.ok) throw new Error('ridership ' + res.status);
      const r = await res.json();
      if (req !== ridershipReqNo[slot]) return;   // 그사이 다른 노선으로 바뀌었으면 버림
      ridershipData[slot] = r;
    } catch (e) {
      if (req !== ridershipReqNo[slot]) return;
      ridershipData[slot] = { status: 'ERROR', message: '이용자 수 예측을 불러오지 못했습니다. 잠시 후 다시 시도하세요.', days: [] };
    }
    drawRidership();
  }

  // 선택한 노선(과 비교 노선)을 따라가는 두 곳: 예측 영역(이용자 수 예측)과 오른쪽 패널(미완료 신고 수, AI)
  // 노선을 고르는 모든 경로(목록, 지도의 선, 검색, 비교 체크)에서 render() 가 이 함수를 불러서 서로 다른 노선을 보여 주지 않게 함
  function updateFocusViews() {
    const focus = focusedRoute();
    const compareNo = comparedRoute();
    forecastRouteEl.hidden = !focus;
    forecastEmptyEl.hidden = !!focus;
    updateSide();                     // 오른쪽 패널: 선택한 노선의 미완료 신고 수 (처음 화면에서는 안내 문구만)
    if (!focus) {
      ridershipReqNo.main++; ridershipReqNo.compare++;
      ridershipData.main = null; ridershipData.compare = null;
      clearRidershipChart(); ridershipState = 'idle';
      if (window.HourlyRidership) window.HourlyRidership.clear();
      forecastShown = null; compareShown = null;
      return;
    }

    const titleOf = function (el, prefix, route) {
      el.textContent = '';
      const dot = document.createElement('span');
      dot.className = 'dot';
      dot.style.background = colorOf(route);
      el.appendChild(dot);
      el.appendChild(document.createTextNode(prefix + route.routeNo + '번 · ' + route.label));
    };
    titleOf(forecastTitleEl, '', findRoute(focus));
    forecastCompareTitleEl.hidden = !compareNo;
    if (compareNo) titleOf(forecastCompareTitleEl, '비교: ', findRoute(compareNo));

    if (forecastShown !== focus) {                // 노선이 바뀔 때만 다시 받음
      forecastShown = focus;
      loadRidership(focus, 'main');
      if (window.HourlyRidership) window.HourlyRidership.load(focus, colorOf(findRoute(focus)));   // 시간대별 예상 승차 인원 (hourly_ridership.js, 못 불러와도 다른 영역은 그대로)
    } else if (ridershipState === 'failed') {
      loadRidership(focus, 'main');               // 예측 서버 오류 등으로 실패한 뒤 같은 노선을 다시 누르면 다시 시도
    }
    if (compareShown !== compareNo) {             // 비교 노선이 생기거나 바뀌거나 없어졌을 때
      compareShown = compareNo;
      if (window.HourlyRidership) window.HourlyRidership.setCompare(compareNo, compareNo ? colorOf(findRoute(compareNo)) : null);   // 시간대별 쪽도 비교 노선을 같이 봄
      if (compareNo) {
        loadRidership(compareNo, 'compare');
      } else {
        ridershipReqNo.compare++;
        ridershipData.compare = null;
        drawRidership();
      }
    }
    if (window.HourlyRidership) window.HourlyRidership.retryIfFailed(focus);  // 시간대별 쪽도 실패한 뒤 같은 노선을 다시 누르면 다시 시도
  }

  // ---------- 동작 ----------
  // 처음 화면: 미완료 신고가 많은 상위 노선만 표시, 선택과 비교는 모두 해제
  function showAll() {
    mode = 'all';
    selected = new Set();
    focusRoute = null;
    compareRoute = null;
    render();
    fitToVisible();
    hideMessage();
  }

  // 노선을 눌렀을 때(목록, 지도의 선, 검색 후 엔터 모두 같음):
  //  - 그 노선만 표시하고 비교 노선은 해제 (선택 노선을 바꾸면 비교도 새로 고름)
  //  - 이미 선택한 노선을 목록에서 다시 누르면 처음 화면으로 돌아감
  //  - addOnly(지도의 선 클릭, 검색 후 엔터): 이미 선택한 노선이면 돌아가지 않고 그 노선을 찾아가서 깜빡임 (실수로 빠지는 것을 막음)
  async function selectRoute(routeNo, addOnly) {
    if (!map) { showMessage(SDK_MESSAGE, 'error'); return; }
    const route = findRoute(routeNo);
    if (!route) return;

    if (mode === 'select' && focusRoute === routeNo) {
      if (!addOnly) { showAll(); return; }
      items[routeNo].scrollIntoView({ block: 'nearest' });
      panToStartIfHidden(routeNo);
      flashRoute(routeNo);
      return;
    }

    const ok = await loadRoute(route);            // 처음에 못 불러왔던 노선이면 다시 시도
    if (!ok) return;                              // 실패하면 지금 화면은 그대로 두고 안내만 표시

    mode = 'select';
    selected = new Set([routeNo]);
    focusRoute = routeNo;
    compareRoute = null;
    openFoldOf(routeNo);                          // 접어 둔 노선을 고르면 한 번 펼쳐 보임 (그 뒤에 접는 건 사용자 마음)
    render();
    panToStartIfHidden(routeNo);                  // 확대 수준은 유지 (자동 확대하지 않음)
    flashRoute(routeNo);
    hideMessage();
    items[routeNo].scrollIntoView({ block: 'nearest' });
  }

  // 비교 노선 체크: 선택한 노선 + 이 노선, 두 개만 표시 (다른 노선을 체크하면 비교 노선이 바뀜)
  async function setCompare(routeNo) {
    if (!map || mode !== 'select' || !focusRoute || routeNo === focusRoute) return;
    const route = findRoute(routeNo);
    if (!route) return;
    const ok = await loadRoute(route);
    if (!ok) { compareBoxes[routeNo].input.checked = false; return; }
    if (mode !== 'select' || !focusRoute || routeNo === focusRoute) return;   // 그사이 선택이 바뀌었으면 버림
    compareRoute = routeNo;
    selected = new Set([focusRoute, routeNo]);
    openFoldOf(routeNo);
    render();
    panToStartIfHidden(routeNo);
    flashRoute(routeNo);
    hideMessage();
  }

  // 접어 둔 목록에 있는 노선을 고르면 그 목록을 펼침
  function openFoldOf(routeNo) {
    if (moreNos.has(routeNo)) moreOpen = true;
    if (restNos.has(routeNo)) restOpen = true;
  }

  function clearCompare() {
    if (!compareRoute) return;
    compareRoute = null;
    selected = new Set(focusRoute ? [focusRoute] : []);
    render();
  }

  // ---------- 목록 그리기 ----------
  // 세 덩어리로 나눔: ① 우선순위 상위 5개(펼침) ② 미완료 신고가 있는 나머지 노선(우선순위 순, 접음)
  // ③ 미완료 신고가 없는 노선(노선번호 순, 접음). 우선순위를 못 받았으면 접지 않고 노선번호 순 (routes 의 순서)
  function renderList() {
    listEl.textContent = '';
    moreListEl.textContent = '';
    restListEl.textContent = '';
    moreNos.clear();
    restNos.clear();
    let top = routes;
    let more = [];
    let rest = [];
    let open = routes;                            // 미완료 신고가 있는 노선 전체 (처음 화면의 기본 노선을 정할 때 씀)
    if (priorityOrder !== null && openAllLoaded) {
      open = priorityOrder.map(findRoute).filter(Boolean);
      top = open.slice(0, LIST_VISIBLE);
      more = open.slice(LIST_VISIBLE);
      rest = routes.filter(function (r) { return open.indexOf(r) === -1; });
    }
    defaultRoutes = (open.length > 0 ? open : routes).slice(0, DEFAULT_COUNT).map(function (r) { return r.routeNo; });

    top.forEach(function (route) { listEl.appendChild(makeListItem(route)); });
    more.forEach(function (route) {
      moreNos.add(route.routeNo);
      moreListEl.appendChild(makeListItem(route));
    });
    rest.forEach(function (route) {
      restNos.add(route.routeNo);
      restListEl.appendChild(makeListItem(route));
    });
    applyFilter();
  }

  function makeListItem(route) {
    const li = document.createElement('li');
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'route-btn';

    const dot = document.createElement('span');
    dot.className = 'dot';
    dot.style.background = colorOf(route);
    const title = document.createTextNode(route.routeNo + '번 ');
    const small = document.createElement('small');
    small.textContent = route.label;
    small.title = route.label;                 // 넘쳐서 잘려도 마우스를 올리면 전체 이름이 보임

    const badge = document.createElement('span');
    badge.className = 'open-badge';
    badge.hidden = true;                       // 신고 수를 받으면 renderOpenBadges 가 채움
    badges[route.routeNo] = badge;

    btn.appendChild(dot);
    btn.appendChild(title);
    btn.appendChild(badge);
    btn.appendChild(small);
    btn.addEventListener('click', function () { selectRoute(route.routeNo); });

    // 비교 체크: 노선 왼쪽 칸, 체크하면 그 노선 색 (render 가 보이고 숨김, 선택 상태를 맞춤)
    const label = document.createElement('label');
    label.className = 'compare-chk is-off';
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.style.accentColor = colorOf(route);
    input.setAttribute('aria-label', route.routeNo + '번을 비교 노선으로 선택');
    input.addEventListener('change', function () {
      if (input.checked) setCompare(route.routeNo); else clearCompare();
    });
    // 체크 칸이 버튼 위에 겹쳐 있어서, 선택한 노선의 체크 칸을 눌러도 체크가 풀리거나 노선 선택이 바뀌지 않게 막음
    label.addEventListener('click', function (e) {
      if (label.classList.contains('is-main')) e.preventDefault();
    });
    label.appendChild(input);

    li.className = 'route-item';
    li.appendChild(label);
    li.appendChild(btn);
    buttons[route.routeNo] = btn;
    items[route.routeNo] = li;
    compareBoxes[route.routeNo] = { label: label, input: input };
    return li;
  }

  // ---------- 노선 검색 (목록만 걸러 보여 줌, 지도에는 영향 없음) ----------
  // 노선번호나 노선 이름(종점)에 검색어가 들어 있으면 보여 줌. "134", "134번", "꽃바위" 모두 가능.
  // 검색 중에는 접어 둔 두 목록(나머지 노선, 미완료 신고 없는 노선)도 함께 검색하고 펼쳐 보임
  function matchedRoutes() {
    const q = searchEl.value.trim().toLowerCase().replace(/번$/, '');
    if (!q) return routes;
    return routes.filter(function (r) {
      return r.routeNo.toLowerCase().indexOf(q) !== -1 || r.label.toLowerCase().indexOf(q) !== -1;
    });
  }

  function applyFilter() {
    const matched = new Set(matchedRoutes().map(function (r) { return r.routeNo; }));
    routes.forEach(function (r) {
      const pinned = mode === 'select' && selected.has(r.routeNo);   // 지도에 표시 중인 노선은 검색과 상관없이 보임
      items[r.routeNo].hidden = !matched.has(r.routeNo) && !pinned;
    });

    const searching = searchEl.value.trim() !== '';
    const moreShown = moreNos.size > 0 && (moreOpen || searching);
    moreToggleEl.hidden = moreNos.size === 0;
    moreListEl.hidden = !moreShown;
    moreToggleEl.setAttribute('aria-expanded', moreShown ? 'true' : 'false');
    moreToggleEl.textContent = '미완료 신고 나머지 노선 ' + moreNos.size + '개 ' + (moreShown ? '▴' : '▾');

    const restShown = restNos.size > 0 && (restOpen || searching);
    restToggleEl.hidden = restNos.size === 0;
    restListEl.hidden = !restShown;
    restToggleEl.setAttribute('aria-expanded', restShown ? 'true' : 'false');
    restToggleEl.textContent = '미완료 신고 없는 노선 ' + restNos.size + '개 ' + (restShown ? '▴' : '▾');

    countEl.textContent = matched.size === routes.length
      ? '노선 ' + routes.length + '개' : '검색 결과 ' + matched.size + '개 / 전체 ' + routes.length + '개';
    emptyEl.hidden = matched.size > 0;
  }

  // ---------- 처음 불러오기 ----------
  async function init() {
    showMessage('노선을 불러오는 중...', 'info');
    try {
      const res = await fetch(ROUTE_API);
      if (!res.ok) throw new Error(String(res.status));
      routes = (await res.json()).routes;
    } catch (e) {
      showMessage(e.message === '403' ? '관리자로 로그인해야 지도를 볼 수 있습니다. 로그인 후 다시 시도하세요.'
        : '노선 목록을 불러오지 못했습니다. 잠시 후 다시 시도하세요.', 'error');
      return;
    }

    // 노선별 미완료 신고 수와 우선순위를 먼저 받아서 목록을 나눔 (못 받아도 지도는 그대로 뜸)
    await Promise.all([loadOpenCounts(), loadPriority()]);
    renderList();
    renderOpenBadges();

    // 처음 화면에 보여 줄 노선(상위 5개)의 선만 받음. 나머지 노선은 선택하거나 비교로 체크할 때 그때 받음
    // (노선이 수백 개여도 처음 화면이 느려지지 않게. 선택·비교는 selectRoute, setCompare 가 먼저 loadRoute 를 부름)
    const initial = defaultRoutes.map(findRoute).filter(Boolean);
    const results = await Promise.all(initial.map(loadRoute));
    showAll();
    const failed = initial.filter(function (r, i) { return !results[i]; });
    if (failed.length > 0) {
      showMessage(failed.map(function (r) { return r.routeNo + '번'; }).join(', ')
        + ' 노선은 지도에 표시할 수 없습니다 (없는 노선이거나 데이터가 없습니다).', 'error');
    } else if (priorityOrder !== null && openAllLoaded && routes.every(function (r) { return restNos.has(r.routeNo); })) {
      showMessage('미완료 신고가 있는 노선이 없어 목록의 앞 ' + DEFAULT_COUNT + '개를 보여 줍니다.', 'info');
    }

    // 대시보드 등에서 /admin/congestion?route=134 로 들어오면 그 노선을 선택한 채로 시작
    const want = new URLSearchParams(location.search).get('route');
    if (want && findRoute(want)) selectRoute(want, true);
  }

  showAllBtn.addEventListener('click', function () { if (map) showAll(); });
  moreToggleEl.addEventListener('click', function () { moreOpen = !moreOpen; applyFilter(); });
  restToggleEl.addEventListener('click', function () { restOpen = !restOpen; applyFilter(); });
  // 목록을 조금 내리면 "맨 위로" 버튼이 나타남. 누르면 목록 맨 위(검색창)로 올라감
  listScrollEl.addEventListener('scroll', function () { listTopBtn.hidden = listScrollEl.scrollTop < 200; });
  listTopBtn.addEventListener('click', function () { listScrollEl.scrollTo({ top: 0, behavior: 'smooth' }); });
  ridershipNormEl.addEventListener('change', drawRidership);
  searchEl.addEventListener('input', applyFilter);
  searchEl.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') {                      // 엔터: 검색 결과의 첫 노선을 선택
      const first = matchedRoutes()[0];
      if (first) selectRoute(first.routeNo, true);
      searchEl.select();                          // 다음 노선을 바로 이어서 검색할 수 있게 검색어를 선택해 둠
    } else if (e.key === 'Escape') {              // ESC: 검색어 지우기
      searchEl.value = '';
      applyFilter();
    }
  });

  // ---------- 지도 만들기 ----------
  // SDK 가 불러와지지 않은 경우: 키가 없거나, 도메인 등록이 안 됐거나, 카카오맵 사용 설정이 꺼져 있거나, 네트워크 문제
  if (typeof kakao === 'undefined' || !kakao.maps) {
    showMessage(SDK_MESSAGE, 'error');
    return;
  }

  kakao.maps.load(function () {
    map = new kakao.maps.Map(mapEl, {
      center: new kakao.maps.LatLng(CITY_HALL.lat, CITY_HALL.lng), // 울산광역시청 (위도, 경도 순서)
      level: 9                                          // 숫자가 작을수록 확대
    });
    map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
    window.addEventListener('resize', function () { map.relayout(); });  // 창 크기가 바뀌면 지도 크기를 다시 맞춤
    kakao.maps.event.addListener(map, 'idle', function () {              // 확대/이동이 끝나면
      applyOffsets();                                                    // 확대 수준이 바뀌었으면 겹친 선을 다시 벌리고
      refreshStops();                                                    // 보이는 정류장을 다시 정함
    });
    createTooltip();

    init();
  });
})();
