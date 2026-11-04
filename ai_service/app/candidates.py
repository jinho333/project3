"""예측 후보 모델 목록(기준선 + 학습 모델). 모델을 추가하려면 `available_candidates()`에 Candidate 한 줄만 더하면 된다.

모든 후보는 같은 모양이다: `fit(학습 표, seed) -> 학습된 모델`, 학습된 모델은 `predict(시험 표) -> 예측 Series`.
그래서 평가(model.py)는 후보가 무엇이든 같은 분할·같은 시드·같은 지표로 비교한다.
- 학습 모델은 정답을 log1p로 바꿔 학습하고(노선별 규모 차이가 큼) 예측 뒤 되돌린다.
- 배차 횟수·정류장 수 같은 입력 변수는 비어 있을 수 있다(시간표가 없는 노선). 트리 계열은 결측을 그대로 다루고 선형 모델은 채워서 쓴다.
- 학습에 없던 노선은 모델마다 정해진 방식으로 대신한다(트리: 결측 처리, 선형: 무시, 기준선: 노선 평균 → 전체 평균).
- XGBoost·LightGBM은 설치되어 있을 때만 후보에 들어간다(없으면 건너뜀).
"""
from dataclasses import dataclass
from typing import Callable

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.ensemble import ExtraTreesRegressor, HistGradientBoostingRegressor, RandomForestRegressor
from sklearn.linear_model import Ridge
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

TARGET = "ride_cnt"
KEYS = ["route_no", "basic_day"]
NUM_FEATURES = ["dow", "month", "is_holiday", "day_kind", "trips", "stop_count", "route_count"]
MAX_CATEGORIES = 255         # HistGradientBoosting 범주형 한도
HORIZON_DAYS = 56            # 검증한 예측 거리 = 최종 시험 기본 일수 = 예측 허용 한도(학습 마지막 날 + 56일, 대장 #2). 이보다 먼 날짜는 검증 근거가 없다


def with_kind(df: pd.DataFrame) -> pd.DataFrame:
    """`day_kind`(명절을 따로 둔 날 유형, features.day_kind)가 없는 표에는 `day_type`을 그대로 복사해 채운다.
    옛 표(명절 구분 이전)로 만든 학습 표·시험 표도 같은 길로 쓸 수 있게 한다."""
    if "day_kind" in df.columns:
        return df
    out = df.copy()
    out["day_kind"] = out["day_type"]
    return out


def zero_if_no_service(test: pd.DataFrame, pred: pd.Series) -> pd.Series:
    """그날 배차가 0회인 노선·날짜는 예측을 0으로 둔다(규칙).

    정답 데이터에는 운행 없는 날의 행이 없어(0이 아님) 모델이 '쉬는 날'을 배우지 못하므로, 시간표가 말해 주는 만큼은
    규칙으로 보완한다. `trips`가 정확히 0인 경우만 해당한다: 시간표가 전혀 없는 노선은 결측이고, 운행 안 함과 구분할 수
    없어 건드리지 않는다. 배차는 수집 시점의 현재 시간표라 과거에 실제 운행하던 노선이 지금 쉬면 어긋날 수 있다(대장 #1).
    모든 후보가 같은 규칙을 쓰도록 predict 안에서 적용한다(평가·저장 모델·REST 예측이 모두 이 길을 지난다)."""
    if "trips" not in test.columns:
        return pred
    no_service = test["trips"].eq(0).fillna(False).to_numpy(dtype=bool)
    if not no_service.any():
        return pred
    out = pred.copy()
    out[no_service] = 0.0
    return out


def _baseline_table(train: pd.DataFrame, key: str, weeks: int = 4) -> dict:
    """기준선에 필요한 값만 미리 계산해 둔다(학습 표 전체를 들고 다니지 않아 파일로 저장해도 작다)."""
    t = with_kind(train).sort_values("basic_day")
    recent = t.groupby(["route_no", key])[TARGET].apply(lambda s: s.tail(weeks).mean()).rename("pred").reset_index()
    route_mean = t.groupby("route_no")[TARGET].mean().rename("route_mean").reset_index()
    return {"key": key, "recent": recent, "route_mean": route_mean, "overall": float(t[TARGET].mean())}


def _apply_baseline(table: dict, test: pd.DataFrame) -> pd.Series:
    key = table["key"]
    out = with_kind(test)[["route_no", key]].merge(table["recent"], how="left", on=["route_no", key])
    out = out.merge(table["route_mean"], how="left", on="route_no")
    pred = out["pred"].fillna(out["route_mean"]).fillna(table["overall"])
    pred.index = test.index
    return pred


def baseline_predict(train: pd.DataFrame, test: pd.DataFrame, key: str = "dow", weeks: int = 4) -> pd.Series:
    """기준선: 학습 기간 마지막 `weeks`번의 같은 노선·같은 key(요일 또는 day_kind) 평균.

    그 조합이 학습에 없으면 노선 평균 -> 전체 평균 순으로 대신한다.
    """
    return _apply_baseline(_baseline_table(train, key, weeks), test)


class Fitted:
    def predict(self, test: pd.DataFrame) -> pd.Series:   # pragma: no cover - 인터페이스
        raise NotImplementedError


VAC_WINDOW = (727, 809)     # 하계휴가 창(월*100+일): 07-27 ~ 08-09. 달력 상수라 날짜만 알면 미래에도 적용된다
VAC_PRE_DAYS = 28            # 휴가 영향을 재는 기준: 창 시작 직전 이 일수의 같은 유형 평균


def vac_flag(days) -> np.ndarray:
    """날짜가 하계휴가 창 안이면 True."""
    s = pd.to_datetime(pd.Series(list(days)))
    md = (s.dt.month * 100 + s.dt.day).to_numpy()
    return (md >= VAC_WINDOW[0]) & (md <= VAC_WINDOW[1])


class _VacBaseline(Fitted):
    """휴가 보정 기준선: 휴가 창 밖의 최근 4번 같은 유형 평균에, 학습에 있는 지난 휴가 창의 비율을 곱한다.

    비율 = (창 안 평균 / 창 시작 직전 28일 평균)을 연도·날 유형별로 구해 연도 평균. 창이 학습에 없으면 1(= 보정 없음).
    고정 기준값이 8월 초 휴가 바닥에 걸려 그 뒤 평일을 25% 넘게 낮게 예측하던 약점을 줄이려는 후보다(storyline 1-36, 1-38).
    같은 이유로 기준값은 휴가 창 밖 날짜만으로 만든다. 노선별 비율은 전체 비율보다 나아지지 않아 쓰지 않는다(1-38)."""

    def __init__(self, train: pd.DataFrame):
        t = with_kind(train).copy()
        t["vac"] = vac_flag(t["basic_day"])
        self.table = _baseline_table(t[~t["vac"]], "day_kind")
        bd = pd.to_datetime(t["basic_day"])
        t["year"] = bd.dt.year
        start = pd.to_datetime(bd.dt.year.astype(str) + f"-{VAC_WINDOW[0] // 100:02d}-{VAC_WINDOW[0] % 100:02d}")
        pre = (bd >= start - pd.Timedelta(days=VAC_PRE_DAYS)) & (bd < start)
        t["period"] = np.where(t["vac"], "win", np.where(pre, "pre", ""))
        t = t[t["period"] != ""]
        g = t.groupby(["year", "route_no", "day_kind", "period"])[TARGET].mean().unstack("period")
        self.ratio: dict[int, float] = {}
        if {"win", "pre"} <= set(g.columns):
            s = g.dropna().groupby(["year", "day_kind"]).sum()
            s = s[s["pre"] > 0]
            self.ratio = (s["win"] / s["pre"]).groupby("day_kind").mean().to_dict()

    def predict(self, test: pd.DataFrame) -> pd.Series:
        t = with_kind(test)
        base = _apply_baseline(self.table, test)
        factor = t["day_kind"].astype(int).map(self.ratio).fillna(1.0).to_numpy()
        factor = np.where(vac_flag(t["basic_day"]), factor, 1.0)
        return zero_if_no_service(test, base * pd.Series(factor, index=test.index))


@dataclass(frozen=True)
class Candidate:
    name: str                                  # DB model_name 으로 저장되는 이름 (30자 이하)
    label: str                                 # 사람이 읽는 이름
    kind: str                                  # 'baseline' | 'model'
    rank: int                                  # 오차가 비슷할 때 더 단순한 쪽(작은 값)을 고르는 순서
    fit: Callable[[pd.DataFrame, int], "Fitted"]


class _Baseline(Fitted):
    def __init__(self, train: pd.DataFrame, key: str):
        self.table = _baseline_table(train, key)

    def predict(self, test: pd.DataFrame) -> pd.Series:
        return zero_if_no_service(test, _apply_baseline(self.table, test))


class _Model(Fitted):
    """학습된 scikit-learn 방식 모델 + 입력 표 만드는 함수. 정답은 log1p 로 학습했으므로 되돌려 돌려준다."""

    def __init__(self, estimator, design: Callable[[pd.DataFrame], pd.DataFrame]):
        self.estimator, self.design = estimator, design

    def predict(self, test: pd.DataFrame) -> pd.Series:
        pred = np.expm1(self.estimator.predict(self.design(test))).clip(min=0)
        return zero_if_no_service(test, pd.Series(np.asarray(pred, dtype="float64"), index=test.index))


def _target(train: pd.DataFrame) -> np.ndarray:
    return np.log1p(train[TARGET].astype("float64")).to_numpy()


def _codes(train: pd.DataFrame) -> dict[str, int]:
    return {r: i for i, r in enumerate(sorted(train["route_no"].unique()))}


class _CodeDesign:
    """트리용 입력 표 만들기: 노선 코드(학습에 없던 노선은 결측) + 숫자 변수 (+ 노선 평균 규모).
    클래스로 둔 이유: 학습된 모델을 파일로 저장(pickle)할 수 있어야 해서(함수 안의 함수는 저장되지 않음)."""

    def __init__(self, codes: dict[str, int], level: dict[str, float] | None = None, level_default: float = 0.0):
        self.codes, self.level, self.level_default = codes, level, level_default

    def __call__(self, df: pd.DataFrame) -> pd.DataFrame:
        df = with_kind(df)
        x = df[NUM_FEATURES].astype("float64")
        x.insert(0, "route_code", df["route_no"].map(self.codes).astype("float64"))
        if self.level is not None:
            x["route_level"] = df["route_no"].map(self.level).astype("float64").fillna(self.level_default)
        return x


def _code_design(codes: dict[str, int], level: dict[str, float] | None = None, level_default: float = 0.0):
    return _CodeDesign(codes, level, level_default)


def _route_level(train: pd.DataFrame) -> tuple[dict[str, float], float]:
    """노선별 평균 log1p 이용량(노선 규모). 범주형을 못 쓰는 모델이 노선 크기를 알 수 있게 하는 숫자 변수."""
    logged = pd.Series(_target(train), index=train.index)
    level = logged.groupby(train["route_no"]).mean()
    return level.to_dict(), float(logged.mean())


# ---- 후보별 학습 함수 ----

def _fit_hist_gbm(train: pd.DataFrame, seed: int) -> Fitted:
    codes = _codes(train)
    design = _code_design(codes)
    model = HistGradientBoostingRegressor(
        categorical_features=[0] if len(codes) <= MAX_CATEGORIES else None,
        max_iter=300, learning_rate=0.08, random_state=seed)
    model.fit(design(train), _target(train))
    return _Model(model, design)


def _fit_level_model(make) -> Callable[[pd.DataFrame, int], Fitted]:
    """노선 코드 + 숫자 변수 + 노선 규모를 입력으로 쓰는 모델(랜덤 포레스트·엑스트라 트리·XGBoost·LightGBM 공통)."""
    def fit(train: pd.DataFrame, seed: int) -> Fitted:
        level, default = _route_level(train)
        design = _code_design(_codes(train), level, default)
        model = make(seed)
        model.fit(design(train), _target(train))
        return _Model(model, design)
    return fit


def _ridge_design(df: pd.DataFrame) -> pd.DataFrame:
    df = with_kind(df)
    x = df[NUM_FEATURES].astype("float64").copy()
    x["route"] = df["route_no"].astype(str).to_numpy()
    x["route_daytype"] = (df["route_no"].astype(str) + "|" + df["day_kind"].astype(int).astype(str)).to_numpy()
    return x


def _fit_ridge(train: pd.DataFrame, seed: int) -> Fitted:
    """선형 모델: 노선 x 요일 유형까지 원-핫으로 넣어 선형 모델에도 공정한 기회를 준다(희소 행렬)."""
    prep = ColumnTransformer([
        ("cat", OneHotEncoder(handle_unknown="ignore"), ["route", "route_daytype"]),
        # 시간표가 없는 노선 등은 배차 횟수·정류장 수가 비어 있다 → 중앙값으로 채우고 "비어 있었음" 표시를 함께 넣는다
        ("num", make_pipeline(SimpleImputer(strategy="median", add_indicator=True), StandardScaler()), NUM_FEATURES),
    ])
    model = make_pipeline(prep, Ridge(alpha=1.0, random_state=seed))
    model.fit(_ridge_design(train), _target(train))
    return _Model(model, _ridge_design)


def _random_forest(seed: int):
    return RandomForestRegressor(n_estimators=150, min_samples_leaf=3, max_features=0.7, n_jobs=-1, random_state=seed)


def _extra_trees(seed: int):
    return ExtraTreesRegressor(n_estimators=200, min_samples_leaf=3, max_features=0.8, n_jobs=-1, random_state=seed)


def _xgboost(seed: int):
    from xgboost import XGBRegressor
    return XGBRegressor(n_estimators=400, learning_rate=0.06, max_depth=6, subsample=0.8, colsample_bytree=0.8,
                        tree_method="hist", n_jobs=-1, random_state=seed, verbosity=0)


def _lightgbm(seed: int):
    from lightgbm import LGBMRegressor
    return LGBMRegressor(n_estimators=400, learning_rate=0.06, num_leaves=31, subsample=0.8, subsample_freq=1,
                         colsample_bytree=0.8, n_jobs=-1, random_state=seed, verbose=-1)


def _installed(module: str) -> bool:
    try:
        __import__(module)
        return True
    except Exception:                                  # noqa: BLE001 - 설치돼 있어도 시스템 라이브러리 문제로 실패할 수 있음
        return False


def available_candidates() -> list[Candidate]:
    """후보 전체(오차가 비슷하면 rank가 작은 쪽을 고름). XGBoost·LightGBM은 설치돼 있을 때만."""
    cands = [
        Candidate("baseline_dow", "기준선: 같은 요일 평균", "baseline", 0, lambda tr, seed: _Baseline(tr, "dow")),
        Candidate("baseline_day_type", "기준선: 같은 유형(평일·토·일공휴일·명절) 평균", "baseline", 1,
                  lambda tr, seed: _Baseline(tr, "day_kind")),
        Candidate("baseline_vac", "기준선: 같은 유형 평균 x 하계휴가 보정", "baseline", 2, lambda tr, seed: _VacBaseline(tr)),
        Candidate("ridge", "선형 모델(Ridge)", "model", 3, _fit_ridge),
        Candidate("hist_gbm", "HistGradientBoosting", "model", 4, _fit_hist_gbm),
    ]
    if _installed("lightgbm"):
        cands.append(Candidate("lightgbm", "LightGBM", "model", 5, _fit_level_model(_lightgbm)))
    if _installed("xgboost"):
        cands.append(Candidate("xgboost", "XGBoost", "model", 6, _fit_level_model(_xgboost)))
    cands += [
        Candidate("extra_trees", "엑스트라 트리", "model", 7, _fit_level_model(_extra_trees)),
        Candidate("random_forest", "랜덤 포레스트", "model", 8, _fit_level_model(_random_forest)),
    ]
    return cands


def candidate_map() -> dict[str, Candidate]:
    return {c.name: c for c in available_candidates()}
