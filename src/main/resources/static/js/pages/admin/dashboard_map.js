// 관리자 대시보드 지도 (1단계: 울산 지도 띄우기)
(function () {
  const mapEl = document.querySelector('#map');
  const messageEl = document.querySelector('#mapMessage');

  function showMessage(text) {
    messageEl.textContent = text;
    messageEl.hidden = false;
  }

  // SDK 가 불러와지지 않은 경우: 키가 없거나, 도메인 등록이 안 됐거나, 카카오맵 사용 설정이 꺼져 있거나, 네트워크 문제
  if (typeof kakao === 'undefined' || !kakao.maps) {
    showMessage('카카오 지도를 불러오지 못했습니다. application.yaml 의 kakao.js.key, '
      + '카카오 개발자 콘솔의 사이트 도메인 등록(http://localhost:8080), 카카오맵 사용 설정을 확인하세요.');
    return;
  }

  kakao.maps.load(function () {
    const center = new kakao.maps.LatLng(35.5384, 129.3114); // 울산 중심 부근 (위도, 경도 순서)
    const map = new kakao.maps.Map(mapEl, {
      center: center,
      level: 9                                               // 숫자가 작을수록 확대
    });

    // 오른쪽에 확대/축소 버튼
    map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);

    // 창 크기가 바뀌면 지도 크기를 다시 맞춤
    window.addEventListener('resize', function () {
      map.relayout();
      map.setCenter(center);
    });
  });
})();
