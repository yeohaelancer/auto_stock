# 🎨 Designer Agent 산출물 — 디자인 시스템

> 최종 업데이트: 2026-08-24
> 대상: JD WORK 대시보드 신규 위젯 (기존 pastel/glassmorphism 디자인 언어 계승)

## 1. 화면 목록 및 플로우

```
[대시보드 홈] → [자동매매 위젯] ─┬─ [포지션 상세 모달]
                                ├─ [AI 신호 상세 모달]
                                ├─ [주문 로그 전체보기]
                                └─ [긴급정지 확인 다이얼로그]
```

위젯은 JD WORK 대시보드 내 하나의 카드형 모듈로 삽입되며, 별도 페이지 이동 없이 위젯 내부에서 대부분의 정보를 확인한다. 상세 보기만 모달로 확장.

## 2. 디자인 시스템

기존 JD WORK 디자인 언어(pastel/glassmorphism)를 그대로 계승한다. 신규 토큰은 "모드 구분", "리스크 경고" 두 가지 의미론적 색상만 추가한다.

### 컬러 토큰 (신규 추가분)
| 토큰 | 값(예시) | 용도 |
|---|---|---|
| `--mode-mock-bg` | pastel blue 계열, 반투명 (기존 팔레트 기준) | 모의투자 모드 배경/배지 |
| `--mode-live-bg` | pastel red/orange 계열, 반투명 | 실거래 모드 배경/배지 — **경고성 색상으로 명확히 구분** |
| `--risk-safe` | 기존 success 톤 | 리스크 게이지 정상 구간 |
| `--risk-warning` | 기존 warning 톤 | 리스크 게이지 70~90% 구간 |
| `--risk-critical` | 기존 error 톤 | 리스크 게이지 90%+ / Circuit Breaker 발동 |

> 실제 HEX 값은 기존 JD WORK 디자인 시스템 토큰 파일을 참조해 매핑할 것 (본 위젯에서 신규 팔레트를 만들지 않음). 해당 파일 경로를 찾지 못한 경우 Review 단계에서 확인 요청.

### 타이포그래피
- 기존 JD WORK 타이포 스케일 그대로 사용 (Heading/Body/Caption)
- 예외: 긴급정지 버튼 라벨은 Heading 급 굵기로 강조 (오조작 방지를 위한 시인성 우선)

### 간격 시스템
- 기존 8px 기본 단위 유지

## 3. 컴포넌트 목록

- [ ] `ModeBadge`: props `{ mode: 'MOCK' | 'LIVE' }` — 모의/실전 배지, 색상 자동 전환
- [ ] `EmergencyStopButton`: props `{ onConfirm: fn }`, state `{ confirming: boolean }` — 클릭 시 확인 다이얼로그 필수 (오조작 방지), 항상 위젯 최상단 고정
- [ ] `TradingToggleSwitch`: props `{ enabled: boolean, mode: string }` — 전체 자동매매 On/Off
- [ ] `PositionCard`: props `{ stockCode, stockName, quantity, avgPrice, currentPrice, pnlRate, mode }`
- [ ] `SignalFeedItem`: props `{ stockCode, signalType, confidence, generatedAt }` — 신뢰도는 진행바(%) 형태
- [ ] `OrderLogRow`: props `{ orderType, stockCode, quantity, price, status, mode, blocked }` — `blocked=true`면 "리스크 차단됨" 표시
- [ ] `RiskGauge`: props `{ currentLossRate, dailyLimitRate }` — 반원형 게이지, `--risk-safe/warning/critical` 3단계 색상
- [ ] `PerformanceChart`: props `{ series: [{date, cumulativeReturn}], benchmarkSeries }` — 누적수익률 vs KOSPI 비교 라인 차트

## 4. 화면별 UI 명세

### 자동매매 위젯 (메인)
- **레이아웃**: 상단 고정 바(모드 배지 + On/Off 스위치 + 긴급정지 버튼) → 포지션 카드 리스트(가로 스크롤 또는 그리드) → AI 신호 피드(타임라인, 최근 N건) → 주문 로그(최근 N건, 전체보기 링크) → 리스크 게이지 → 성과 차트
- **주요 인터랙션**:
  - 긴급정지 버튼 클릭 → 확인 다이얼로그("정말 모든 자동매매를 중단하시겠습니까?") → 확인 시 즉시 API 호출 및 낙관적 UI 반영
  - 모드 배지는 읽기 전용 표시만 (전환은 대시보드 위젯에서 하지 않음 — 설계 원칙 §3.3에 따라 설정값 변경 + 수동 승인 절차이므로 위젯 UI에서 실거래 전환 버튼 자체를 두지 않는다)
- **엣지 케이스**:
  - 포지션 없음 → "보유 종목 없음" 빈 상태 문구
  - AI 신호/주문 로그 없음 → 빈 상태 문구, 스켈레톤 아님(실데이터 없음을 명확히)
  - 키움 API 연결 끊김 → 위젯 상단에 경고 배너("시세 연결 끊김 — 신규 주문 중단됨") 노출
  - 리스크 한도 도달(Circuit Breaker 발동) → `RiskGauge`가 `--risk-critical`로 전환 + 위젯 전체에 경고 테두리

### 긴급정지 확인 다이얼로그
- 레이아웃: 중앙 모달, 배경 딤 처리
- 주요 인터랙션: "취소" / "중단하기(위험 강조 스타일)" 2버튼, 기본 포커스는 "취소"(오조작 방지)
- 엣지 케이스: API 호출 실패 시 재시도 안내 문구, 성공 시 토스트 확인 메시지

## 📤 Dev에게 전달할 사항
- 컴포넌트 구현 우선순위: `EmergencyStopButton` → `ModeBadge`/`TradingToggleSwitch` → `RiskGauge` → `PositionCard`/`OrderLogRow` → `SignalFeedItem` → `PerformanceChart`
- 반응형 분기점: 360px(모바일) / 768px(태블릿) / 1280px(데스크톱) — 위젯은 JD WORK 그리드 시스템 내 카드이므로 최소 360px에서도 긴급정지 버튼과 모드 배지는 항상 노출되어야 함
- 접근성: 긴급정지 버튼은 색상뿐 아니라 아이콘+텍스트 병행, 키보드 포커스 가능(Tab), `aria-label="자동매매 긴급 정지"` 명시. 리스크 게이지는 색상 외 텍스트(%) 병행 표기 (색맹 대응)
- 다크모드: 기존 JD WORK 다크모드 토큰 매핑을 그대로 따름 (신규 대응 불필요, 토큰 상속만 확인)
