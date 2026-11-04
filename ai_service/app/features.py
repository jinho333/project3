"""예측용 입력 변수 (노선번호 x 날짜): 요일·공휴일 + 배차 횟수·정류장 수.

국토부 노선별 이용량 API(합성데이터) 수집분(정답 값)을 (노선번호, 날짜)로 이 표에 붙여 학습한다.
예측 단위는 route_id(방향·변형별)가 아니라 route_no(노선번호)다: 이용량 쪽이 노선번호로만 BIS와 연결되기 때문.
변환은 DB·네트워크 없이 테스트 가능한 순수 함수로 두고, DB 조회는 load_* / main 에서만 한다.

사용 예 (ai_service 디렉터리에서):
    python -m app.features --start 2026-10-01 --end 2026-10-14
    python -m app.features --start 2026-10-01 --end 2026-10-14 --out data/features.csv
"""
import argparse
import logging
from functools import lru_cache
import sys
from datetime import date
from pathlib import Path

import holidays
import pandas as pd
from sqlalchemy.engine import Engine

log = logging.getLogger("features")

# BIS 시간표 dayOfWeek 중 날짜로 정해지는 3종. 방학(3~5)은 방학 기간 정보가 없어 쓰지 않는다.
WEEKDAY, SATURDAY, SUNDAY_HOLIDAY = 0, 1, 2
DAY_TYPES = (WEEKDAY, SATURDAY, SUNDAY_HOLIDAY)
FESTIVAL = 3                              # day_kind 전용 값(시간표 구분이 아님). 시간표는 day_type으로 고른다
FESTIVAL_WORDS = ("설날", "추석")

# 시간표와 노선은 web_service(TransitService)와 같은 키로 연결한다
TIMETABLE_KEYS = ["route_no", "route_class", "direction"]


@lru_cache(maxsize=16)
def _kr_holidays(years: tuple[int, ...]):
    """연도별 한국 공휴일 달력(만드는 데 약 0.1초라 캐시). 읽기만 한다."""
    return holidays.KR(years=list(years))


def calendar_features(start: date, end: date, extra_holidays: dict[date, str] | None = None) -> pd.DataFrame:
    """날짜별 요일·공휴일. 공휴일은 holidays 라이브러리(대체·임시공휴일, 선거일 포함) + extra_holidays."""
    days = pd.date_range(start, end, freq="D")
    years = tuple(sorted({d.year for d in days}))
    if extra_holidays:
        kr = holidays.KR(years=years)          # 추가 휴일은 달력 객체를 고치므로 캐시된 것을 쓰지 않는다
        for d, name in extra_holidays.items():
            kr[d] = name
    else:
        kr = _kr_holidays(years)
    df = pd.DataFrame({"basic_day": days.date})
    df["dow"] = days.dayofweek            # 0월 ~ 6일
    df["month"] = days.month
    df["is_weekend"] = (df["dow"] >= 5).astype(int)
    df["holiday_name"] = pd.Series([kr.get(d) for d in df["basic_day"]], dtype=object)
    df["is_holiday"] = df["holiday_name"].notna().astype(int)
    df["day_type"] = [day_type(dow, hol) for dow, hol in zip(df["dow"], df["is_holiday"])]
    df["day_kind"] = [day_kind(t, name) for t, name in zip(df["day_type"], df["holiday_name"])]
    return df


def day_type(dow: int, is_holiday: bool) -> int:
    """그날 적용되는 시간표 구분: 일요일·공휴일 2, 토요일 1, 나머지 0 (토요일 공휴일도 2)."""
    if is_holiday or dow == 6:
        return SUNDAY_HOLIDAY
    return SATURDAY if dow == 5 else WEEKDAY


def day_kind(dtype: int, holiday_name) -> int:
    """예측용 날 유형: day_type에 명절 연휴(설날·추석의 전날·당일·다음날·대체휴일)를 3으로 따로 둔다.

    `day_type`은 시간표 배차(trips)를 고르는 열쇠라 값을 늘릴 수 없어 별도 열로 둔다. 명절은 평소 일요일·공휴일의
    약 0.2~0.4배라 같은 유형으로 평균하면 크게 틀린다(storyline 1-27·1-28)."""
    if isinstance(holiday_name, str) and any(w in holiday_name for w in FESTIVAL_WORDS):
        return FESTIVAL
    return int(dtype)


def supply_features(routes: pd.DataFrame, route_stops: pd.DataFrame, timetable: pd.DataFrame) -> pd.DataFrame:
    """노선별 정류장 수, 시간표 구분별 배차 횟수(trips_0/1/2).

    routes: route_id, route_no, route_class, direction
    route_stops: route_id (행 하나가 정류장 하나)
    timetable: route_no, route_class, direction, day_of_week (행 하나가 출발 1회)

    - 시간표가 하나라도 있는 노선은 없는 구분을 0회로 본다 (예: 주말 미운행).
    - 시간표가 전혀 없는 노선은 운행 안 함과 구분할 수 없어 결측(NA)으로 둔다. 정류장 수도 같다.
    """
    out = routes[["route_id", "route_no", "route_class", "direction"]].copy()

    stop_cnt = route_stops.groupby("route_id").size().rename("stop_count")
    out = out.merge(stop_cnt, how="left", left_on="route_id", right_index=True)

    tt = timetable[timetable["day_of_week"].isin(DAY_TYPES)]
    trips = (tt.groupby(TIMETABLE_KEYS + ["day_of_week"]).size()
             .unstack("day_of_week").reindex(columns=list(DAY_TYPES)).fillna(0))
    trips.columns = [f"trips_{c}" for c in trips.columns]
    out = out.merge(trips, how="left", left_on=TIMETABLE_KEYS, right_index=True)

    for c in ["stop_count"] + [f"trips_{d}" for d in DAY_TYPES]:
        out[c] = out[c].astype("Int64")
    return out.reset_index(drop=True)


def supply_by_route_no(supply: pd.DataFrame) -> pd.DataFrame:
    """route_id 단위 공급 변수를 노선번호 단위로 합친다 (이용량 정답이 노선번호 단위이므로).

    - trips_*: 방향·변형을 모두 더한 하루 배차 횟수. 한 변형이라도 값이 있으면 합, 전부 결측이면 결측.
    - stop_count: 변형 중 가장 긴 노선의 정류장 수(변형끼리 더하면 겹치는 정류장이 중복되므로 최댓값).
    - route_count: 합친 route_id 수 (방향·변형 수).
    """
    trip_cols = [f"trips_{d}" for d in DAY_TYPES]
    g = supply.groupby("route_no")
    out = g[trip_cols].sum(min_count=1)
    out["stop_count"] = g["stop_count"].max()
    out["route_count"] = g["route_id"].nunique().astype("Int64")
    for c in trip_cols + ["stop_count"]:
        out[c] = out[c].astype("Int64")
    return out.reset_index()


def daily_features(calendar: pd.DataFrame, supply: pd.DataFrame) -> pd.DataFrame:
    """노선번호 x 날짜 표. supply 는 supply_by_route_no 결과. trips 는 그날 day_type 의 배차 횟수."""
    df = supply.merge(calendar, how="cross")
    trips = pd.Series(pd.NA, index=df.index, dtype="Int64")
    for d in DAY_TYPES:
        mask = df["day_type"] == d
        trips[mask] = df.loc[mask, f"trips_{d}"]
    df["trips"] = trips
    cols = ["route_no", "basic_day", "dow", "month", "is_weekend", "is_holiday", "holiday_name",
            "day_type", "day_kind", "trips", "stop_count", "route_count"]
    return df[cols].sort_values(["route_no", "basic_day"]).reset_index(drop=True)


def load_supply(engine: Engine) -> pd.DataFrame:
    with engine.connect() as conn:
        routes = pd.read_sql("SELECT route_id, route_no, route_class, direction FROM bus_route", conn)
        route_stops = pd.read_sql("SELECT route_id FROM route_stop", conn)
        timetable = pd.read_sql("SELECT route_no, route_class, direction, day_of_week FROM bus_timetable", conn)
    return supply_features(routes, route_stops, timetable)


def build(engine: Engine, start: date, end: date) -> pd.DataFrame:
    return daily_features(calendar_features(start, end), supply_by_route_no(load_supply(engine)))


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description="예측용 입력 변수 생성 (노선번호 x 날짜)")
    p.add_argument("--start", required=True, type=date.fromisoformat, help="YYYY-MM-DD")
    p.add_argument("--end", required=True, type=date.fromisoformat, help="YYYY-MM-DD")
    p.add_argument("--out", type=Path, help="CSV 저장 경로 (생략하면 요약만 출력)")
    args = p.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    if args.end < args.start:
        p.error("--end가 --start보다 앞섭니다.")

    from app.db import get_engine
    df = build(get_engine(), args.start, args.end)
    if df.empty:
        log.error("bus_route가 비어 있습니다. 먼저 수집기(app.collect)를 실행하세요.")
        return 1
    routes = df.drop_duplicates("route_no")
    log.info("노선번호 %d개 x %d일 = %d행", len(routes), df["basic_day"].nunique(), len(df))
    log.info("정류장 수 없는 노선 %d개, 시간표 없는 노선 %d개 (결측으로 둠)",
             int(routes["stop_count"].isna().sum()), int(df.groupby("route_no")["trips"].count().eq(0).sum()))
    hol = df.drop_duplicates("basic_day").query("is_holiday == 1")
    log.info("기간 내 공휴일: %s", ", ".join(f"{d}({n})" for d, n in zip(hol["basic_day"], hol["holiday_name"])) or "없음")
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        df.to_csv(args.out, index=False, encoding="utf-8-sig")   # 엑셀에서 한글이 깨지지 않게 BOM 포함
        log.info("저장: %s", args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
