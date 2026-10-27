// 관리자 대시보드 지도
// 동작 규칙(임시):
//  1) 처음에는 노선 10개를 혼잡 등급 색으로 모두 표시
//  2) 노선 하나를 클릭하면 그 노선만 표시
//  3) 이어서 다른 노선을 클릭하면 그 노선이 추가로 표시 (쌓임)
//  4) "전체 노선 보기" 버튼으로 처음 상태로 돌아감
//  5) 방향: 기본은 노선당 대표 1방향, "양방향" 토글로 모든 방향 표시.
//     노선 하나를 선택하면 예측 영역의 방향 칩으로 그 노선의 방향을 고름
//  6) 선에 마우스를 올리면 툴팁(버스번호, 방향, 혼잡도)이 뜨고 그 노선 외의 선은 흐려짐
//  7) 정류장: 전체 보기에서는 표시 안 함 / 노선을 선택하면 기점·종점 표시 /
//     더 확대하면 화면 안의 정류장도 표시 / 정류장에 마우스를 올리면 툴팁에 정류장 이름도 표시
// 노선 목록은 /admin/api/routes, 노선 선은 /admin/api/routes/{노선번호}/geometry 에서 받음
(function () {
  const mapEl = document.querySelector('#map');
  const messageEl = document.querySelector('#mapMessage');
  const listEl = document.querySelector('#routeList');
  const showAllBtn = document.querySelector('#showAllBtn');
  const searchEl = document.querySelector('#routeSearch');
  const countEl = document.querySelector('#routeCount');
  const emptyEl = document.querySelector('#routeEmpty');
  const dirToggleEl = document.querySelector('#dirToggle');
  const dirOneBtn = document.querySelector('#dirOneBtn');
  const dirBothBtn = document.querySelector('#dirBothBtn');
  const forecastRouteEl = document.querySelector('#forecastRoute');
  const forecastTitleEl = document.querySelector('#forecastTitle');
  const dirChipsEl = document.querySelector('#dirChips');
  const forecastEmptyEl = document.querySelector('#forecastEmpty');

  const ROUTE_API = '/admin/api/routes';
  const GRADE_COLOR = { HIGH: '#E53935', MID: '#F9A825', LOW: '#43A047' }; // 혼잡 / 보통 / 여유
  const GRADE_LABEL = { HIGH: '혼잡', MID: '보통', LOW: '여유' };
  const LINE_WEIGHT = 5;          // 선 굵기
  const LINE_WEIGHT_HOVER = 8;    // 마우스를 올린 선의 굵기
  const LINE_OPACITY = 0.85;      // 보통 선 투명도
  const LINE_OPACITY_DIM = 0.5;   // 마우스를 올렸을 때 다른 노선의 투명도
  const ARROW_GAP_PX = 90;        // 진행 방향 화살표 사이의 화면 간격(px), 확대해도 이 간격을 유지
  const STOP_ZOOM_LEVEL = 7;    // 이 확대 수준 이하(더 확대)가 되면 기점·종점 외 정류장도 표시 (숫자가 작을수록 확대)

  const SDK_MESSAGE = '카카오 지도를 불러오지 못했습니다. application.yaml 의 kakao.js.key, '
    + '카카오 개발자 콘솔의 사이트 도메인 등록(http://localhost:8080), 카카오맵 사용 설정을 확인하세요.';

  let map = null;                 // 카카오 지도
  let routes = [];                // 서버에서 받은 노선 목록 [{routeNo, rank, grade, gradeLabel, label}]
  // 노선번호 -> { dirs: [{ brtId, label, isLoop, lines: [Polyline...] }], rep: 대표 방향, bounds } (불러온 노선만)
  const layers = {};
  const buttons = {};             // 노선번호 -> 목록의 버튼
  const items = {};               // 노선번호 -> 목록의 항목(li), 검색으로 걸러낼 때 숨김/표시
  let mode = 'all';               // 'all': 전체 표시 / 'select': 선택한 노선만 (선택할수록 늘어남)
  let selected = new Set();       // select 모드에서 지도에 표시 중인 노선번호
  let dirMode = 'one';            // 'one': 노선당 대표 1방향 / 'both': 모든 방향
  let dirChoice = {};             // 노선번호 -> 방향 칩으로 고른 값 (방향ID 또는 'all'), 없으면 dirMode 를 따름
  let stopMarkers = [];           // 지도에 올라가 있는 정류장 표시 [{ routeNo, overlay, el }]
  let arrowMarkers = [];          // 선 위에 일정 간격으로 올라가 있는 진행 방향 화살표 [{ routeNo, overlay, el }]

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

  // ---------- 호버 강조: 올린 노선만 진하게, 나머지 노선은 흐리게 ----------
  function eachLine(fn) {
    Object.keys(layers).forEach(function (no) {
      layers[no].dirs.forEach(function (d) {
        d.lines.forEach(function (line) { fn(no, line); });
      });
    });
  }

  function applyHover(routeNo, hoverLine) {
    eachLine(function (no, line) {
      const mine = no === routeNo;
      line.setOptions({
        strokeOpacity: mine ? LINE_OPACITY : LINE_OPACITY_DIM,
        strokeWeight: line === hoverLine ? LINE_WEIGHT_HOVER : LINE_WEIGHT,
        zIndex: mine ? 10 : 1                      // 올린 노선을 위로 올림
      });
    });
    stopMarkers.concat(arrowMarkers).forEach(function (s) {
      s.el.style.opacity = s.routeNo === routeNo ? '' : LINE_OPACITY_DIM;   // 다른 노선의 정류장·화살표도 같이 흐리게
    });
  }

  function clearHover() {
    eachLine(function (no, line) {
      line.setOptions({ strokeOpacity: LINE_OPACITY, strokeWeight: LINE_WEIGHT, zIndex: 1 });
    });
    stopMarkers.concat(arrowMarkers).forEach(function (s) { s.el.style.opacity = ''; });
  }

  // ---------- 진행 방향 화살표 ----------
  // 선을 따라 화면에서 ARROW_GAP_PX 마다 화살표(>)를 놓음. 확대/이동이 끝날 때마다 다시 계산해서
  // 확대 수준과 관계없이 간격이 비슷하고, 지금 화면 안에 있는 것만 그림.
  function clearArrows() {
    arrowMarkers.forEach(function (a) { a.overlay.setMap(null); });
    arrowMarkers = [];
  }

  function refreshArrows() {
    clearArrows();
    if (!map) return;
    const proj = map.getProjection();
    const w = mapEl.clientWidth, h = mapEl.clientHeight;

    visibleRouteNos().forEach(function (no) {
      const route = findRoute(no);
      shownDirs(no).forEach(function (dir) {
        const color = GRADE_COLOR[gradeOf(route, dir.brtId)] || '#888';
        const pts = dir.path.map(function (ll) { return proj.containerPointFromCoords(ll); });
        let next = ARROW_GAP_PX / 2;                 // 다음 화살표까지 남은 거리
        for (let i = 1; i < pts.length; i++) {
          const a = pts[i - 1], b = pts[i];
          const dx = b.x - a.x, dy = b.y - a.y;
          const len = Math.hypot(dx, dy);
          const angle = Math.atan2(dy, dx);          // 화면에서 진행하는 각도
          for (; next <= len; next += ARROW_GAP_PX) {
            const t = next / len;
            const x = a.x + dx * t, y = a.y + dy * t;
            if (x < -20 || x > w + 20 || y < -20 || y > h + 20) continue;   // 화면 밖은 건너뜀
            const la = dir.path[i - 1], lb = dir.path[i];
            const pos = new kakao.maps.LatLng(
              la.getLat() + (lb.getLat() - la.getLat()) * t,
              la.getLng() + (lb.getLng() - la.getLng()) * t);
            addArrow(no, pos, angle, color);
          }
          next -= len;
        }
      });
    });
  }

  function addArrow(routeNo, pos, angle, color) {
    const el = document.createElement('div');
    el.className = 'dir-arrow';
    el.style.transform = 'rotate(' + angle + 'rad)';
    // 선 색 테두리 위에 흰 화살표: 선 위에서도 지도 위에서도 보이도록 두 겹으로 그림
    el.innerHTML = '<svg viewBox="0 0 10 10"><path d="M3 1 L7 5 L3 9" fill="none" stroke="' + color
      + '" stroke-width="3.6" stroke-linecap="round" stroke-linejoin="round"/>'
      + '<path d="M3 1 L7 5 L3 9" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round" '
      + 'stroke-linejoin="round"/></svg>';
    const overlay = new kakao.maps.CustomOverlay({ position: pos, content: el, xAnchor: 0.5, yAnchor: 0.5, zIndex: 2 });
    overlay.setMap(map);
    if (el.parentNode) el.parentNode.style.pointerEvents = 'none';   // 선의 호버/클릭을 가로채지 않게
    arrowMarkers.push({ routeNo: routeNo, overlay: overlay, el: el });
  }

  // 화살표와 정류장 표시를 지금 상태에 맞게 다시 만듦
  function refreshMarkers() {
    refreshArrows();
    refreshStops();
  }

  // ---------- 정류장 표시 ----------
  // 전체 보기: 표시 안 함 / 노선 선택: 기점·종점 / 더 확대: 지금 화면 안의 정류장도 표시
  function clearStops() {
    stopMarkers.forEach(function (s) { s.overlay.setMap(null); });
    stopMarkers = [];
  }

  function refreshStops() {
    clearStops();
    if (!map || mode !== 'select') return;
    const zoomedIn = map.getLevel() <= STOP_ZOOM_LEVEL;
    const view = zoomedIn ? map.getBounds() : null;
    const labeled = new Set();                     // 같은 이름의 정류장 이름표는 한 번만 보여 줌 (겹침 방지)

    visibleRouteNos().forEach(function (no) {
      const route = findRoute(no);
      shownDirs(no).forEach(function (dir) {
        const color = GRADE_COLOR[gradeOf(route, dir.brtId)] || '#888';
        const last = dir.stops.length - 1;
        dir.stops.forEach(function (stop, i) {
          const role = i === 0 ? '기점' : (i === last ? '종점' : '');
          if (!role && !(zoomedIn && view.contain(stop.pos))) return;
          const showLabel = !!role && !labeled.has(stop.name);
          labeled.add(stop.name);
          addStopMarker(route, dir, stop, color, role, showLabel);
        });
      });
    });
  }

  function addStopMarker(route, dir, stop, color, role, showLabel) {
    const el = document.createElement('div');
    el.className = 'stop-dot' + (role ? ' is-end' : '');
    el.style.borderColor = color;
    if (showLabel) {
      const label = document.createElement('span');
      label.className = 'stop-label';
      label.textContent = role + ' ' + stop.name;
      el.appendChild(label);
    }
    // 정류장에 마우스를 올려도 그 노선을 강조하고, 툴팁에는 정류장 이름을 함께 보여 줌
    el.addEventListener('mouseenter', function () {
      applyHover(route.routeNo, dir.lines[0]);
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
    stopMarkers.push({ routeNo: route.routeNo, overlay: overlay, el: el });
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
      kakao.maps.event.addListener(line, 'click', function () { selectRoute(route.routeNo); });
      // 마우스를 올리면 툴팁이 뜨고 그 노선 외에는 흐려짐, 벗어나면 원래대로
      kakao.maps.event.addListener(line, 'mouseover', function () {
        applyHover(route.routeNo, line);
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
    refreshMarkers();
    renderDirControls(single);
  }

  // ---------- 방향 컨트롤: 지도 위 토글 / 노선 하나를 골랐을 때 예측 영역의 방향 칩 ----------
  function renderDirControls(single) {
    dirToggleEl.hidden = !!single;                // 노선 하나를 볼 때는 칩이 대신함
    dirOneBtn.classList.toggle('is-on', dirMode === 'one');
    dirBothBtn.classList.toggle('is-on', dirMode === 'both');

    forecastRouteEl.hidden = !single;
    forecastEmptyEl.hidden = !!single;
    if (!single) return;

    const route = findRoute(single);
    const layer = layers[single];
    const choice = currentChoice(single);
    const grade = route.grade;

    forecastTitleEl.textContent = '';
    const dot = document.createElement('span');
    dot.className = 'dot grade-' + grade;
    forecastTitleEl.appendChild(dot);
    forecastTitleEl.appendChild(document.createTextNode(
      route.routeNo + '번 · ' + route.gradeLabel + ' · ' + route.label));

    dirChipsEl.textContent = '';
    const caption = document.createElement('span');
    caption.className = 'chips-caption';
    caption.textContent = '방향';
    dirChipsEl.appendChild(caption);

    layer.dirs.forEach(function (d) { dirChipsEl.appendChild(makeChip(single, d.brtId, d.label, choice)); });
    dirChipsEl.appendChild(makeChip(single, 'all', layer.dirs.length > 2 ? '전체' : '양방향', choice));
  }

  function makeChip(routeNo, value, text, choice) {
    const chip = document.createElement('button');
    chip.type = 'button';
    chip.className = 'chip' + (value === choice ? ' is-on' : '');
    chip.textContent = text;
    chip.addEventListener('click', function () {
      dirChoice[routeNo] = value;
      render();
    });
    return chip;
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
    render();
    fitToVisible();
    hideMessage();
  }

  async function selectRoute(routeNo) {
    if (!map) { showMessage(SDK_MESSAGE, 'error'); return; }
    const route = findRoute(routeNo);
    if (!route) return;

    const ok = await loadRoute(route);            // 처음에 못 불러왔던 노선이면 다시 시도
    if (!ok) return;                              // 실패하면 지금 화면은 그대로 두고 안내만 표시

    if (mode === 'all') {                         // 처음 클릭: 그 노선만 표시
      mode = 'select';
      selected = new Set([routeNo]);
    } else {                                      // 이어서 클릭: 표시 중인 노선에 추가
      selected.add(routeNo);
    }
    render();
    panIfOutOfView(routeNo);                      // 확대 수준은 유지 (자동 확대하지 않음)
    hideMessage();
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

      li.appendChild(btn);
      listEl.appendChild(li);
      buttons[route.routeNo] = btn;
      items[route.routeNo] = li;
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
    routes.forEach(function (r) { items[r.routeNo].hidden = !matched.has(r.routeNo); });
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
      if (first) selectRoute(first.routeNo);
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
    kakao.maps.event.addListener(map, 'idle', refreshMarkers);           // 확대/이동이 끝나면 화살표와 정류장을 다시 정함
    createTooltip();

    init();
  });
})();
