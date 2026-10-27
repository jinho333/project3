// 관리자 대시보드 지도
// 동작 규칙(임시):
//  1) 처음에는 노선 10개를 혼잡 등급 색으로 모두 표시
//  2) 노선 하나를 클릭하면 그 노선만 표시 (목록, 지도의 선, 검색 후 엔터 모두 같음)
//  3) 이어서 다른 노선을 클릭하거나 검색해서 고르면 그 노선이 추가로 표시 (쌓임), 표시 중인 노선을 다시 클릭하면 빠짐
//  4) "전체 노선 보기" 버튼으로 처음 상태로 돌아감
//  5) 방향: 기본은 노선당 대표 1방향, 노선 목록의 "양방향" 토글로 모든 방향 표시.
//     노선 하나를 선택하면 예측 영역의 방향 칩으로 그 노선의 방향을 고름
//  6) 선에 마우스를 올리면 툴팁(버스번호, 방향, 혼잡도)이 뜨고 그 방향의 선 외에는(같은 노선의 반대 방향 포함) 흐려짐
//  7) 정류장: 전체 보기에서는 표시 안 함 / 노선을 선택하면 기점·종점 표시 /
//     조금 확대(DIRECTION_ZOOM_LEVEL 이하)하면 선 위에 진행 방향 V 만 표시 (동그라미 없이) /
//     더 확대(STOP_ZOOM_LEVEL 이하)하면 화면 안의 정류장 점도 표시 / 정류장에 마우스를 올리면 툴팁에 정류장 이름도 표시
//     진행 방향은 정류장 점 바로 앞에 붙는 V 화살표로 표시 (선 위에 일정 간격으로 그리지 않음)
// 노선 목록은 /admin/api/routes, 노선 선은 /admin/api/routes/{노선번호}/geometry 에서 받음
(function () {
  const mapEl = document.querySelector('#map');
  const messageEl = document.querySelector('#mapMessage');
  const listEl = document.querySelector('#routeList');
  const showAllBtn = document.querySelector('#showAllBtn');
  const searchEl = document.querySelector('#routeSearch');
  const countEl = document.querySelector('#routeCount');
  const emptyEl = document.querySelector('#routeEmpty');
  const dirOneBtn = document.querySelector('#dirOneBtn');
  const dirBothBtn = document.querySelector('#dirBothBtn');
  const forecastRouteEl = document.querySelector('#forecastRoute');
  const forecastTitleEl = document.querySelector('#forecastTitle');
  const forecastEmptyEl = document.querySelector('#forecastEmpty');

  const ROUTE_API = '/admin/api/routes';
  const GRADE_COLOR = { HIGH: '#E53935', MID: '#F9A825', LOW: '#43A047' }; // 혼잡 / 보통 / 여유
  const GRADE_LABEL = { HIGH: '혼잡', MID: '보통', LOW: '여유' };
  const LINE_WEIGHT = 5;          // 선 굵기
  const LINE_WEIGHT_HOVER = 8;    // 마우스를 올린 선의 굵기
  const LINE_OPACITY = 0.85;      // 보통 선 투명도
  const LINE_OPACITY_DIM = 0.5;   // 마우스를 올렸을 때 다른 노선의 투명도
  const ARROW_SPACING_PX = 130;   // 진행 방향 V 사이의 화면 간격(px), 너무 촘촘하지 않게
  const ARROW_SNAP_PX = 35;       // V 자리에서 이 거리 안에 정류장이 있으면 그 정류장 점 안에 넣음
  const DIRECTION_ZOOM_LEVEL = 7; // 이 확대 수준 이하(더 확대)가 되면 선 위에 진행 방향 V 를 표시 (숫자가 작을수록 확대)
  const STOP_ZOOM_LEVEL = 5;  // 이 확대 수준 이하(더 확대)가 되면 기점·종점 외 정류장도 표시 (숫자가 작을수록 확대)

  const SDK_MESSAGE = '카카오 지도를 불러오지 못했습니다. application.yaml 의 kakao.js.key, '
    + '카카오 개발자 콘솔의 사이트 도메인 등록(http://localhost:8080), 카카오맵 사용 설정을 확인하세요.';

  let map = null;                 // 카카오 지도
  let routes = [];                // 서버에서 받은 노선 목록 [{routeNo, rank, grade, gradeLabel, label}]
  // 노선번호 -> { dirs: [{ brtId, label, isLoop, lines: [Polyline...] }], rep: 대표 방향, bounds } (불러온 노선만)
  const layers = {};
  const buttons = {};             // 노선번호 -> 목록의 버튼
  const items = {};               // 노선번호 -> 목록의 항목(li), 검색으로 걸러낼 때 숨김/표시
  const details = {};             // 노선번호 -> 목록 항목 안의 방향 선택 영역 (펼침)
  const dirLines = {};            // 노선번호 -> 목록 항목 아래의 한 줄 (지금 보고 있는 방향)
  let mode = 'all';               // 'all': 전체 표시 / 'select': 선택한 노선만 (선택할수록 늘어남)
  let selected = new Set();       // select 모드에서 지도에 표시 중인 노선번호
  let dirMode = 'one';            // 'one': 노선당 대표 1방향 / 'both': 모든 방향
  let dirChoice = {};             // 노선번호 -> 방향 칩으로 고른 값 (방향ID 또는 'all'), 없으면 dirMode 를 따름
  let openRoute = null;           // 목록에서 방향 칩을 펼쳐 둔 노선번호 (한 번에 하나만, 없으면 모두 접힘)
  let stopMarkers = [];           // 지도에 올라가 있는 정류장 표시 [{ routeNo, overlay, el }]

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
    } else {
      showMessage('노선을 불러오지 못했습니다. 잠시 후 다시 시도하세요.', 'error');
    }
  }

  // ---------- 혼잡 등급 ----------
  // 노선번호 + 방향ID -> 혼잡 등급. 선 색과 툴팁이 모두 여기서 정함.
  // 지금은 노선 등급 하나를 쓰고, 예측에 방향별 값(route.directionGrades: [{brtId, grade}])이 오면 그 값을 먼저 씀.
  // (route.directions 는 방향 개수라서 이름을 겹치지 않게 함)
  function gradeOf(route, brtId) {
    const d = (route.directionGrades || []).find(function (x) { return x.brtId === brtId; });
    return d ? d.grade : route.grade;
  }

  function findRoute(routeNo) {
    return routes.find(function (r) { return r.routeNo === routeNo; });
  }

  // ---------- 호버 툴팁 (선에 마우스를 올리면 버스번호, 방향, 혼잡도를 보여 줌) ----------
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
    const grade = gradeOf(route, dir.brtId);
    tooltipEl.textContent = '';

    const head = document.createElement('div');
    head.className = 'tt-head';
    const dot = document.createElement('span');
    dot.className = 'dot grade-' + grade;
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

    const level = document.createElement('div');
    level.className = 'tt-line';
    level.textContent = '혼잡도: ' + GRADE_LABEL[grade];

    // 예측 수치가 연결되면 이 줄에 예상 승차 인원 등을 넣을 자리
    const forecast = document.createElement('div');
    forecast.className = 'tt-line tt-muted';
    forecast.textContent = '혼잡 순위 ' + route.rank + '위 (임시 데이터)';

    tooltipEl.appendChild(direction);
    tooltipEl.appendChild(level);
    tooltipEl.appendChild(forecast);
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

  function applyHover(routeNo, brtId, hoverLine) {
    eachLine(function (no, line, dir) {
      const mine = no === routeNo && dir.brtId === brtId;
      line.setOptions({
        strokeOpacity: mine ? LINE_OPACITY : LINE_OPACITY_DIM,
        strokeWeight: line === hoverLine ? LINE_WEIGHT_HOVER : LINE_WEIGHT,
        zIndex: mine ? 10 : 1                      // 올린 방향을 위로 올림
      });
    });
    stopMarkers.forEach(function (s) {
      const mine = s.routeNo === routeNo && s.brtId === brtId;
      s.el.style.opacity = mine ? '' : LINE_OPACITY_DIM;   // 다른 방향·노선의 정류장도 같이 흐리게
    });
  }

  function clearHover() {
    eachLine(function (no, line) {
      line.setOptions({ strokeOpacity: LINE_OPACITY, strokeWeight: LINE_WEIGHT, zIndex: 1 });
    });
    stopMarkers.forEach(function (s) { s.el.style.opacity = ''; });
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

    visibleRouteNos().forEach(function (no) {
      const route = findRoute(no);
      shownDirs(no).forEach(function (dir) {
        const color = GRADE_COLOR[gradeOf(route, dir.brtId)] || '#888';
        const last = dir.stops.length - 1;
        const plan = planArrows(dir, proj, zoomedIn);   // 진행 방향 V 를 놓을 자리 (정류장 점 안 또는 선 위)
        dir.stops.forEach(function (stop, i) {
          const role = i === 0 ? '기점' : (i === last ? '종점' : '');
          if (!role && !(zoomedIn && view.contain(stop.pos))) return;
          const showLabel = !!role && !labeled.has(stop.name);
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
  }

  // 진행 방향 V 를 놓을 자리를 정함: 선을 따라 화면에서 약 ARROW_SPACING_PX 마다 하나씩.
  // snap 이 true(정류장 점을 보여 주는 확대 수준)이면, 그 자리 근처(ARROW_SNAP_PX 이내)에 정류장이 있을 때 그 정류장 점 안에 넣음.
  // 정류장 점이 없는 확대 수준이거나 근처에 정류장이 없으면 선 위에 V 만 놓음 (동그라미 없이).
  // 그래서 V 가 너무 촘촘하지 않고, 정류장 사이가 넓어도 방향이 끊기지 않음. 기점의 V 는 항상 기점 점 안에 들어감.
  // 반환: { stopAngles: {정류장 순번: 각도}, mids: [{ pos, angle, x, y }] }
  function planArrows(dir, proj, snap) {
    const pts = dir.stops.map(function (s) { return proj.containerPointFromCoords(s.pos); });
    const cum = [0];                               // 기점에서 각 정류장까지 화면상 누적 거리(px)
    for (let i = 1; i < pts.length; i++) {
      cum.push(cum[i - 1] + Math.hypot(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y));
    }
    const total = cum[cum.length - 1];
    const stopAngles = {};
    const mids = [];
    const angleAt = function (j) {                 // 이전 정류장에서 들어오는 방향, 기점은 다음 정류장으로 나가는 방향
      if (j > 0) return Math.atan2(pts[j].y - pts[j - 1].y, pts[j].x - pts[j - 1].x);
      return pts.length > 1 ? Math.atan2(pts[1].y - pts[0].y, pts[1].x - pts[0].x) : 0;
    };

    if (!snap) stopAngles[0] = angleAt(0);         // 기점 표시에는 항상 방향 V 가 들어감 (기점 자리에서 시작하지 않도록 아래에서 간격의 절반 뒤부터)
    for (let t = snap ? 0 : ARROW_SPACING_PX / 2; t <= total; t += ARROW_SPACING_PX) {
      let near = -1, best = ARROW_SNAP_PX + 1;
      if (snap) {
        for (let j = 0; j < cum.length; j++) {
          const d = Math.abs(cum[j] - t);
          if (d < best && stopAngles[j] === undefined) { best = d; near = j; }
        }
      }
      if (near >= 0) { stopAngles[near] = angleAt(near); continue; }

      let i = 1;                                   // t 가 속한 구간 (정류장 i-1 ~ i)
      while (i < cum.length - 1 && cum[i] < t) i++;
      const len = cum[i] - cum[i - 1];
      if (len === 0) continue;
      const f = (t - cum[i - 1]) / len;
      const a = dir.stops[i - 1].pos, b = dir.stops[i].pos;
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
          lines: []
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
        strokeColor: GRADE_COLOR[gradeOf(route, dir.brtId)] || '#888',
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

  // ---------- 화면에 반영 ----------
  function markMissing(routeNo, missing) {
    if (buttons[routeNo]) buttons[routeNo].classList.toggle('is-missing', missing);
  }

  function visibleRouteNos() {
    if (mode === 'all') return Object.keys(layers);
    return Array.from(selected).filter(function (no) { return layers[no]; });
  }

  // 노선 하나에서 지금 선택된 방향 값: 방향ID 또는 'all'
  function currentChoice(routeNo) {
    return dirChoice[routeNo] || (dirMode === 'both' ? 'all' : layers[routeNo].rep.brtId);
  }

  // 지도에 그릴 방향들
  function shownDirs(routeNo) {
    const layer = layers[routeNo];
    const choice = currentChoice(routeNo);
    return choice === 'all' ? layer.dirs : layer.dirs.filter(function (d) { return d.brtId === choice; });
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
  }

  // 클릭했을 때는 지금 보고 있는 화면(확대 수준, 위치)을 그대로 둠.
  // 단, 그 노선이 지금 화면에 전혀 안 들어와 있으면 아무것도 안 보이니까, 확대 수준은 두고 위치만 옮김.
  function panIfOutOfView(routeNo) {
    const view = map.getBounds();
    const b = layers[routeNo].bounds;
    const sw = b.getSouthWest(), ne = b.getNorthEast();
    const outside = ne.getLat() < view.getSouthWest().getLat() || sw.getLat() > view.getNorthEast().getLat()
      || ne.getLng() < view.getSouthWest().getLng() || sw.getLng() > view.getNorthEast().getLng();
    if (outside) {
      map.panTo(new kakao.maps.LatLng((sw.getLat() + ne.getLat()) / 2, (sw.getLng() + ne.getLng()) / 2));
    }
  }

  // 노선 하나만 선택했을 때의 노선번호 (아니면 null)
  function singleSelected() {
    const nos = visibleRouteNos();
    return mode === 'select' && nos.length === 1 ? nos[0] : null;
  }

  function render() {
    hideTooltip();                                // 선이 바뀌면 남아 있을 수 있는 호버 표시를 정리
    clearHover();
    const visible = new Set(visibleRouteNos());
    const single = singleSelected();
    Object.keys(layers).forEach(function (no) {
      const shown = visible.has(no) ? new Set(shownDirs(no)) : new Set();
      layers[no].dirs.forEach(function (d) {
        d.lines.forEach(function (line) {
          line.setMap(shown.has(d) ? map : null);
        });
      });
    });
    // 목록의 버튼: 직접 선택해서 표시 중인 노선만 강조 (전체 보기 상태에서는 강조 없음)
    Object.keys(buttons).forEach(function (no) {
      buttons[no].classList.toggle('is-active', mode === 'select' && selected.has(no));
    });
    applyFilter();                                // 검색 중에도 선택한 노선은 목록에 남겨 둠
    refreshStops();
    renderDirControls(single);
  }

  // ---------- 방향 컨트롤: 노선 목록 위의 토글(전체 기본값) / 방금 누른 노선 항목 안의 방향 칩 ----------
  function renderDirControls(single) {
    dirOneBtn.classList.toggle('is-on', dirMode === 'one');
    dirBothBtn.classList.toggle('is-on', dirMode === 'both');
    renderRouteDetails();

    // 예측 영역: 노선 하나만 볼 때 그 노선 정보를 보여 줌 (예측 차트가 들어올 자리)
    forecastRouteEl.hidden = !single;
    forecastEmptyEl.hidden = !!single;
    if (!single) return;

    const route = findRoute(single);
    forecastTitleEl.textContent = '';
    const dot = document.createElement('span');
    dot.className = 'dot grade-' + route.grade;
    forecastTitleEl.appendChild(dot);
    forecastTitleEl.appendChild(document.createTextNode(
      route.routeNo + '번 · ' + route.gradeLabel + ' · ' + route.label));
  }

  // 노선 목록의 항목 아래:
  //  - 지도에 표시 중인 노선은 지금 보고 있는 방향을 작은 한 줄로 보여 줌 (누르면 방향 선택이 펼쳐짐)
  //  - 방금 누른 노선 하나만 방향 칩을 펼쳐 보여 줌 (나머지는 접힘)
  function renderRouteDetails() {
    Object.keys(details).forEach(function (no) {
      const box = details[no];
      const line = dirLines[no];
      const shown = mode === 'select' && selected.has(no);
      const open = shown && openRoute === no;

      box.hidden = !open;
      box.textContent = '';
      buttons[no].classList.toggle('is-open', open);
      buttons[no].setAttribute('aria-expanded', open ? 'true' : 'false');

      line.hidden = !shown || open;
      if (shown) {
        const l = layers[no];
        const choice = currentChoice(no);
        const chosen = l.dirs.find(function (d) { return d.brtId === choice; });
        line.textContent = '방향: ' + (chosen ? chosen.label : (l.dirs.length > 2 ? '전체' : '양방향')) + ' ▾';
      }
      if (!open) return;

      const layer = layers[no];
      const choice = currentChoice(no);
      const chips = document.createElement('div');
      chips.className = 'dir-chips';
      const caption = document.createElement('span');
      caption.className = 'chips-caption';
      caption.textContent = '방향';
      chips.appendChild(caption);
      layer.dirs.forEach(function (d) { chips.appendChild(makeChip(no, d.brtId, d.label, choice)); });
      chips.appendChild(makeChip(no, 'all', layer.dirs.length > 2 ? '전체' : '양방향', choice));

      box.appendChild(chips);
    });
  }

  function makeChip(routeNo, value, text, choice) {
    const chip = document.createElement('button');
    chip.type = 'button';
    chip.className = 'chip' + (value === choice ? ' is-on' : '');
    chip.textContent = text;
    chip.addEventListener('click', function () {
      dirChoice[routeNo] = value;
      openRoute = null;                           // 방향을 고르면 칩은 자동으로 접힘 (결과는 지도의 선과 화살표로 확인)
      render();
    });
    return chip;
  }

  // 지도에서 이 노선만 뺌 (마지막 노선이 빠지면 전체 보기, 화면 위치와 확대 수준은 그대로)
  function removeRoute(routeNo) {
    selected.delete(routeNo);
    if (selected.size === 0) mode = 'all';
    if (openRoute === routeNo) openRoute = null;
    render();
  }

  function setDirMode(next) {
    if (!map) return;
    dirMode = next;
    dirChoice = {};                               // 전체 토글을 누르면 노선별로 고른 방향은 초기화
    render();
  }

  // ---------- 동작 ----------
  function showAll() {
    mode = 'all';
    selected = new Set();
    dirChoice = {};
    openRoute = null;
    render();
    fitToVisible();
    hideMessage();
  }

  // 노선을 눌렀을 때(목록, 지도의 선, 검색 후 엔터 모두 같음):
  //  - 전체 보기 상태에서 처음 누르면 그 노선만 표시
  //  - 이어서 다른 노선을 누르면(검색해서 고른 경우 포함) 표시 중인 노선에 쌓임
  //  - 새로 표시한 노선은 목록 항목 안에 방향 칩이 펼쳐짐 (한 번에 한 노선만)
  //  - 목록에서 이미 표시 중인 노선을 다시 누르면 지도에서 빠짐 (마지막 노선이 빠지면 전체 보기)
  //  - addOnly(지도의 선 클릭, 검색 후 엔터): 이미 표시 중이면 빼지 않고 방향 칩만 펼침 (실수로 빠지는 것을 막음)
  async function selectRoute(routeNo, addOnly) {
    if (!map) { showMessage(SDK_MESSAGE, 'error'); return; }
    const route = findRoute(routeNo);
    if (!route) return;

    if (mode === 'select' && selected.has(routeNo)) {
      if (!addOnly) { removeRoute(routeNo); return; }
      openRoute = routeNo;
      renderRouteDetails();
      items[routeNo].scrollIntoView({ block: 'nearest' });
      return;
    }

    const ok = await loadRoute(route);            // 처음에 못 불러왔던 노선이면 다시 시도
    if (!ok) return;                              // 실패하면 지금 화면은 그대로 두고 안내만 표시

    if (mode === 'all') {                         // 처음 누름: 그 노선만 표시
      mode = 'select';
      selected = new Set([routeNo]);
    } else {                                      // 이어서 누름: 표시 중인 노선에 추가
      selected.add(routeNo);
    }
    openRoute = routeNo;
    render();
    panIfOutOfView(routeNo);                      // 확대 수준은 유지 (자동 확대하지 않음)
    hideMessage();
    items[routeNo].scrollIntoView({ block: 'nearest' });
  }

  // ---------- 목록 그리기 ----------
  function renderList() {
    listEl.textContent = '';
    routes.forEach(function (route) {
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'route-btn';

      const dot = document.createElement('span');
      dot.className = 'dot grade-' + route.grade;
      const title = document.createTextNode(route.rank + '. ' + route.routeNo + '번 ');
      const small = document.createElement('small');
      small.textContent = route.gradeLabel + ' · ' + route.label;

      btn.appendChild(dot);
      btn.appendChild(title);
      btn.appendChild(small);
      btn.addEventListener('click', function () { selectRoute(route.routeNo); });

      // 지도에 표시 중인 노선 아래에 작게 보이는 한 줄: 지금 보고 있는 방향 (누르면 방향 선택이 펼쳐짐)
      const dirLine = document.createElement('button');
      dirLine.type = 'button';
      dirLine.className = 'route-dir-line';
      dirLine.hidden = true;
      dirLine.addEventListener('click', function () {
        openRoute = openRoute === route.routeNo ? null : route.routeNo;
        renderRouteDetails();
      });

      // 방금 누른 노선일 때만 펼쳐지는 방향 선택 영역 (JS 가 채움)
      const box = document.createElement('div');
      box.className = 'route-detail';
      box.hidden = true;

      li.appendChild(btn);
      li.appendChild(dirLine);
      li.appendChild(box);
      listEl.appendChild(li);
      buttons[route.routeNo] = btn;
      items[route.routeNo] = li;
      dirLines[route.routeNo] = dirLine;
      details[route.routeNo] = box;
    });
    applyFilter();
  }

  // ---------- 노선 검색 (목록만 걸러 보여 줌, 지도에는 영향 없음) ----------
  // 노선번호나 노선 이름(종점)에 검색어가 들어 있으면 보여 줌. "134", "134번", "꽃바위" 모두 가능.
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
    countEl.textContent = matched.size === routes.length
      ? '노선 ' + routes.length + '개' : '검색 결과 ' + matched.size + '개 / 전체 ' + routes.length + '개';
    emptyEl.hidden = matched.size > 0;
  }

  // ---------- 처음 불러오기 ----------
  async function init() {
    showMessage('노선을 불러오는 중...', 'info');
    try {
      const res = await fetch(ROUTE_API);
      if (!res.ok) throw new Error('list ' + res.status);
      routes = (await res.json()).routes;
    } catch (e) {
      showMessage('노선 목록을 불러오지 못했습니다. 잠시 후 다시 시도하세요.', 'error');
      return;
    }
    renderList();

    const results = await Promise.all(routes.map(loadRoute));   // 모든 노선의 선을 한꺼번에 받음
    showAll();
    const failed = routes.filter(function (r, i) { return !results[i]; });
    if (failed.length > 0) {
      showMessage(failed.map(function (r) { return r.routeNo + '번'; }).join(', ')
        + ' 노선은 지도에 표시할 수 없습니다 (없는 노선이거나 데이터가 없습니다).', 'error');
    }
  }

  showAllBtn.addEventListener('click', function () { if (map) showAll(); });
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
  dirOneBtn.addEventListener('click', function () { setDirMode('one'); });
  dirBothBtn.addEventListener('click', function () { setDirMode('both'); });

  // ---------- 지도 만들기 ----------
  // SDK 가 불러와지지 않은 경우: 키가 없거나, 도메인 등록이 안 됐거나, 카카오맵 사용 설정이 꺼져 있거나, 네트워크 문제
  if (typeof kakao === 'undefined' || !kakao.maps) {
    showMessage(SDK_MESSAGE, 'error');
    return;
  }

  kakao.maps.load(function () {
    map = new kakao.maps.Map(mapEl, {
      center: new kakao.maps.LatLng(35.5384, 129.3114), // 울산 중심 부근 (위도, 경도 순서)
      level: 9                                          // 숫자가 작을수록 확대
    });
    map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
    window.addEventListener('resize', function () { map.relayout(); });  // 창 크기가 바뀌면 지도 크기를 다시 맞춤
    kakao.maps.event.addListener(map, 'idle', refreshStops);             // 확대/이동이 끝나면 보이는 정류장을 다시 정함
    createTooltip();

    init();
  });
})();
