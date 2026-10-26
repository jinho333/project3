// 관리자 대시보드 지도 (1단계: 울산 지도 띄우기)
(function () {
  const mapEl = document.querySelector('#map');
  const messageEl = document.querySelector('#mapMessage');

  function showMessage(text) {
    messageEl.textContent = text;
    messageEl.hidden = false;
  }

  // SDK 가 불러와지지 않은 경우: 키가 없거나, 도메인 등록이 안 됐거나, 네트워크 문제
  if (typeof kakao === 'undefined' || !kakao.maps) {
    showMessage('카카오 지도를 불러오지 못했습니다. application.yaml 의 kakao.js.key 와 '
      + '카카오 개발자 콘솔의 사이트 도메인 등록(http://localhost:8080)을 확인하세요.');
    return;
  }

  kakao.maps.load(function () {
    const map = new kakao.maps.Map(mapEl, {
      center: new kakao.maps.LatLng(35.5384, 129.3114), // 울산 중심 부근 (위도, 경도 순서)
      level: 9                                          // 숫자가 작을수록 확대
    });

    // 오른쪽에 확대/축소 버튼
    map.addControl(new kakao.maps.ZoomControl(), kakao.maps.ControlPosition.RIGHT);
  });
})();
