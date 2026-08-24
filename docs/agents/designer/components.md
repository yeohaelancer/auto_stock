# 🎨 컴포넌트 명세 — 자동매매 위젯 (Vue 3)

> 최종 업데이트: 2026-08-24

## 컴포넌트 트리

```
AutoTradingWidget
 ├─ WidgetHeaderBar
 │   ├─ ModeBadge
 │   ├─ TradingToggleSwitch
 │   └─ EmergencyStopButton → EmergencyStopConfirmDialog
 ├─ PositionCardList
 │   └─ PositionCard (v-for)
 ├─ SignalFeed
 │   └─ SignalFeedItem (v-for)
 ├─ OrderLogTable
 │   └─ OrderLogRow (v-for)
 ├─ RiskGauge
 └─ PerformanceChart
```

## Props / State 상세

| 컴포넌트 | Props | 내부 State | 비고 |
|---|---|---|---|
| `AutoTradingWidget` | - | `loading`, `connectionError` | 최상위 컨테이너, API 폴링/구독 관리 |
| `ModeBadge` | `mode: 'MOCK'\|'LIVE'` | - | 순수 표시용 (읽기 전용) |
| `TradingToggleSwitch` | `enabled: boolean`, `mode: string` | `pending: boolean` | 토글 시 API 호출 중 로딩 표시 |
| `EmergencyStopButton` | `disabled: boolean` | `dialogOpen: boolean` | 클릭 시 `EmergencyStopConfirmDialog` 오픈 |
| `EmergencyStopConfirmDialog` | `open: boolean` | - | 확인 시 emit `confirm`, 취소 시 emit `cancel` |
| `PositionCard` | `stockCode, stockName, quantity, avgPrice, currentPrice, pnlRate, mode` | - | `pnlRate` 부호에 따라 색상(수익/손실) |
| `SignalFeedItem` | `stockCode, signalType, confidence, generatedAt` | - | `confidence`는 0~1 → % 변환 표시 |
| `OrderLogRow` | `orderType, stockCode, quantity, price, status, mode, blocked` | - | `blocked=true`면 배경 강조 |
| `RiskGauge` | `currentLossRate, dailyLimitRate` | - | 비율 = currentLossRate / dailyLimitRate |
| `PerformanceChart` | `series, benchmarkSeries` | - | 차트 라이브러리는 Dev 재량(기존 JD WORK 표준 라이브러리 사용 권장) |

## 빈 상태 / 로딩 / 에러 상태

| 컴포넌트 | 빈 상태 | 로딩 | 에러 |
|---|---|---|---|
| `PositionCardList` | "보유 종목 없음" | 스켈레톤 카드 | - |
| `SignalFeed` | "최근 생성된 신호 없음" | 스켈레톤 리스트 | - |
| `OrderLogTable` | "주문 이력 없음" | 스켈레톤 행 | - |
| `AutoTradingWidget` | - | 전체 스켈레톤 | 상단 경고 배너("시세 연결 끊김") |

## 📤 Dev에게 전달할 사항
- 상태 관리: 위젯 단위 로컬 상태(Pinia store 분리 여부는 기존 JD WORK 컨벤션을 따를 것 — 신규 컨벤션 도입 금지)
- API 폴링 주기 또는 WebSocket 구독 방식은 Dev 구현 재량이나, 리스크 게이지/긴급정지 상태는 지연 없이(실시간에 가깝게) 반영되어야 함
- 컴포넌트 파일명은 프로젝트 kebab-case 규칙(`dev.md`)을 따르되 Vue SFC 컴포넌트명은 PascalCase 유지(Vue 컨벤션)

## 📌 변경 이력 (2026-08-24, BUG-004 오케스트레이션 구현 단계)
이번 요청(신호→리스크→주문 백엔드 오케스트레이션)은 **순수 백엔드 파이프라인 연결**이며 화면/컴포넌트 명세 변경이 필요 없음을 확인했습니다. UI 변경 없이 Review로 진행합니다.

## 📌 변경 이력 (2026-08-24, BUG-006/BUG-003 일괄 수정 단계)
두 수정 모두 백엔드(계좌 스냅샷 배치)와 AI 서비스(모델 연동) 범위이며 화면/컴포넌트 변경 없음. 다만 `PerformanceChart`(누적수익률 vs KOSPI)가 이제 실제로 매일 쌓이는 `account_snapshot` 데이터를 받게 된다는 점, `SignalFeedItem`의 신뢰도(%)가 실제 모델 추론값(현재는 합성 라벨 기반)으로 채워지기 시작한다는 점은 참고할 것 — 기존 명세로 충분히 대응 가능하므로 추가 명세는 불필요.

## 📌 변경 이력 (2026-08-24, BUG-005 스케줄러 자동 연동 단계)
장중 스케줄러가 백그라운드에서 주기적으로 파이프라인을 호출하도록 만드는 작업이며 **순수 백엔드 배치 로직**입니다. 화면/컴포넌트 변경 없음. 다만 위젯의 "AI 신호 피드"/"주문 로그"가 이제 수동 트리거 없이도 실데이터로 채워지기 시작한다는 점은 참고할 것 — 이미 명세된 빈 상태/로딩 상태 처리로 충분히 대응 가능하므로 추가 명세는 불필요.
