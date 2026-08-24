# 📋 프로젝트 진행 상태 (Manager)

> 최종 업데이트: 2026-08-24

## 프로젝트 개요
- **프로젝트명**: 주식 자동매매 프로그램 (AI/ML 예측 기반)
- **대상 시장**: 국내 주식 KOSPI/KOSDAQ (해외주식·파생상품·야간거래는 1차 제외)
- **증권사 API**: 키움증권 REST API (신규, 채택) / OpenAPI+ (레거시, 비교·백업용)
- **운영 모드**: 모의투자 · 실거래 겸용 구조 (`TRADING_MODE=MOCK|LIVE`)
- **연계 프로젝트**: JD WORK 대시보드 신규 위젯 모듈 (pastel/glassmorphism 디자인 언어 계승)
- **기획 문서**: 2026-08-20 작성, 사용자 제공 (요약은 아래 참고)

## 🔑 핵심 요구사항 요약
| 항목 | 내용 |
|---|---|
| 전략 방식 | AI/ML 기반 예측 매매 (1차 MVP: LightGBM 등 트리 기반, 2단계: LSTM/Transformer/앙상블) |
| 기술 스택 | Spring Boot 3.x, Vue 3, MyBatis, PostgreSQL, Gradle + Python(FastAPI) ML 서빙 |
| AI 서비스 | Spring Boot와 분리된 별도 프로세스 (`POST /predict`) |
| 리스크 관리 | 손절/익절, 일일 최대 손실 한도(Circuit Breaker), 종목/전체 포지션 한도, 이상매매 감지(Kill Switch), 수동 긴급정지 — **전략 엔진과 완전 독립 모듈** |
| 배포 | Docker 컨테이너, Linux 서버(OCX 불필요) |
| 예상 복잡도 | **높음** (외부 금융 API 연동 + AI 서빙 분리 + 실거래 리스크 관리 + 대시보드 통합) |

## ⚠️ 안전/전제 사항 (전 에이전트 공통 준수)
- 본 문서는 시스템 설계 문서이며 **투자자문이 아님**. AI 예측은 확률적 추정치이고 원금 손실 가능성이 있음을 모든 산출물에 명시할 것.
- 실거래 전환은 **코드 배포가 아닌 설정값 변경**으로만 이루어지며, 전환 시 수동 승인 절차 필수.
- Phase 4(대시보드)까지는 **모의투자로만 검증**, Phase 5에서 소액 실거래로 점진 확대.
- 키움증권 API 이용약관·알고리즘 매매 규정, REST API 지원 TR 목록은 실제 구현 착수 전 공식 문서로 최종 확인 필요 (본 프로젝트에서 임의로 단정하지 않음).
- 장애 시 기본값은 항상 "매매 안 함" (임의 값 대체 금지), API 연결 끊김 시 신규 주문 자동 중단.

## 🗂️ 에이전트별 작업 지시

### DBA Agent
- [ ] 설계 대상 테이블: `stock_master`, `price_history`(일봉/분봉), `feature_daily`, `prediction_log`, `strategy_signal`, `order_log`, `position`, `risk_log`, `account_snapshot`
- [ ] `prediction_log`, `feature_daily`는 데이터량 급증 예상 → 월 단위 파티셔닝 검토
- [ ] `order_log`, `position`에 모드(모의/실전) 구분 컬럼 필수, 실거래·모의투자 데이터 혼동 방지 제약조건 명시
- [ ] pgvector 확장은 1차 필수 아님 — 확장 여지만 문서화 (2단계 유사 패턴 검색용)
- [ ] 민감정보(앱키/시크릿/계좌번호)는 DB 저장 시 암호화 컬럼으로 명시하고, 평문 저장 금지 원칙 문서화

### Designer Agent (DBA와 병렬 진행)
- [ ] 대상: JD WORK 대시보드 내 자동매매 신규 위젯 (기존 pastel/glassmorphism 디자인 언어 계승 — 신규 디자인 시스템 아님)
- [ ] 필수 화면 요소: 모드 표시(모의/실전) + 전체 On/Off 스위치 + **긴급 정지 버튼(최상단 고정, 최우선)**, 포지션 현황, AI 신호 피드(신뢰도 표시), 주문 로그(모의/실전 배지 구분), 리스크 상태 패널(게이지), 성과 차트(vs KOSPI)
- [ ] UX 원칙: 실거래 모드는 시각적으로 명확히 구분(배경/배지 색상 차등)하여 모의투자와의 오조작 방지
- [ ] 기존 JD WORK 디자인 시스템 문서가 있다면 참조 경로를 Review 단계에 명시

### Review Agent
- [ ] DBA 스키마와 Designer 화면 명세 간 데이터 정합성 (예: 리스크 상태 패널이 참조하는 컬럼이 `risk_log`/`account_snapshot`에 실제 존재하는지)
- [ ] 긴급 정지 버튼 → 실제 Kill Switch 동작 경로가 리스크 엔진 설계와 일치하는지
- [ ] 민감정보 저장/전달 경로에 보안 취약점 없는지 (앱키/시크릿/계좌번호)

### Dev Agent
- [ ] 구현 우선순위(로드맵 Phase 1~3 우선): 키움 REST API 인증/시세 수집 → DB 스키마 → 모의투자 주문 → AI 예측 연동(FastAPI) → 신호/리스크/주문 파이프라인
- [ ] 모듈 구조: `config / market / prediction / strategy / risk / order / account / scheduler / api` (`risk`는 `strategy`와 완전 독립)
- [ ] `OrderExecutor` 인터페이스로 모의/실전 전환을 Strategy 패턴으로 분리, credential은 모드별 분리 저장
- [ ] 키움 TR 호출 제한 대응: 레이트 리미터(Bucket4j) + WebSocket 우선, REST 폴링은 보조 수단
- [ ] AI 예측 서비스는 Spring Boot와 별도 프로세스로 구현 (FastAPI, `POST /predict`)

### QA Agent
- [ ] 테스트 범위: 모의투자 주문 End-to-End, 긴급정지 버튼 동작, 일일 손실 한도 Circuit Breaker, 이상매매 감지, API 연결 끊김 시 신규 주문 자동 중단
- [ ] 백테스트 프레임워크 검증: 누적수익률/MDD/승률/샤프비율/거래비용 반영 여부
- [ ] 모의/실전 모드 오조작 방지 UI 검증 (색상 구분이 실제로 명확한지)
- [ ] 실거래 전환은 Phase 5 이전에는 테스트 대상 아님 (모의투자 검증에 집중)

### DevOps Agent
- [ ] 배포 환경: Docker 컨테이너 (`backend`, `ml-service`, `postgres`, 선택 `redis`), Linux 서버(OCX 제약 없음)
- [ ] 시크릿(앱키/시크릿키/계좌번호)은 코드/이미지에 하드코딩 금지, 모드별(MOCK/LIVE) 분리 관리
- [ ] 스케줄러: 장전(08:00~09:00) 예측 배치, 장중 실시간 구독, 장마감 정산, 야간 재학습 배치
- [ ] 장애 대응 runbook: 키움 API 연결 끊김 / AI 서비스 응답 지연 / DB 장애 각각의 안전 정지 절차 포함

## ✅ 현재 단계
- 완료: 요구사항 분석 → DBA → Designer → Review → Dev → QA → DevOps → (BUG-001/002) → (BUG-004 오케스트레이션) → (BUG-005 스케줄러) → (BUG-006 계좌스냅샷 + BUG-003 AI 배선) — 등록된 모든 버그의 코드 레벨 이슈 해소
- 진행 중: 없음
- 다음: 실데이터 축적(시세/피처/라벨) 후 AI 모델 재학습(Phase 2), 키움 REST API 실연동(인증/시세/주문)

## 승인 체크리스트
- [x] 요구사항 확정
- [x] DBA + Designer 완료 → Review 진행 승인
- [x] Review 통과 (조건부 통과) → Dev 진행 승인
- [x] Dev 완료 → QA 진행 승인
- [x] QA 1차 검증 (조건부 통과 12/14, BUG-001/BUG-002 Open) → Dev 재작업 승인
- [x] Dev 버그 수정 완료 (BUG-001, BUG-002) → QA 재검증 승인 (18/18)
- [x] Dev BUG-004 구현 완료 (`OrderService` 오케스트레이션) → QA 재검증 승인 (23/23)
- [x] Dev BUG-005 구현 완료 (`TradingScheduler` 장중 자동 스캔) → QA 재검증 승인 (28/28)
- [x] Dev BUG-006 구현 완료 (`postMarketJob` 계좌 스냅샷, MOCK 전용) → QA 재검증 승인
- [x] Dev BUG-003 구현 완료 (AI 예측 실모델/실피처 배선, 합성 라벨 명시) → QA 재검증 승인 (36/36)
- [x] DevOps 확인 완료 (ml-service DB 접속·시드머니 환경변수 반영) → **모의투자(MOCK) 릴리즈 승인 유지**. 실거래(LIVE) 전환은 Phase 5까지 별도 수동 승인 필요 (설계 §3.3, §11)

## 남은 작업 (버그 아님 — 구조적 전제조건)
- **AI 모델 실전 사용 불가**: 현재 모델은 합성(synthetic) 라벨로 학습됨(`modelVersion=lgbm-synthetic-0.1`). 실제 시세 이력이 쌓이고 정답 라벨을 만들 수 있어야 재학습 가능 (Phase 2)
- **LIVE 모드 잔고/시세/주문**: 키움증권 REST API 실제 인증·시세·주문 연동은 전 구간 TODO — 공식 개발자 문서 확인 후 구현 필요 (Phase 5 이전 필수)

## ✅ 추가 구현 (2026-08-24) — 실서비스 착수를 위한 사전 준비 항목
사용자 요청에 따라 "실제 서비스 실행 전 필요 작업" 중 코드로 바로 착수 가능한 2건을 구현:
- **종목 유니버스 갱신 배치** (`TradingScheduler.preMarketJob`): `price_history` 기준 거래정지 후보 자동 마킹/해제 (휴리스틱, 키움 정식 통지 연동 전 임시 안전판). `is_managed`(관리종목)는 외부 지정 정보라 이번 범위에서 제외 — KRX/공시 API 연동 필요
- **백테스트 프레임워크** (`BacktestEngine`/`BacktestService`, `GET /api/backtest/performance`): 설계 §4.5 필수 지표(누적수익률/MDD/승률/샤프비율/거래비용 반영) 전부 구현. `order_log` 체결 이력을 FIFO 매매 쌍으로 구성해 사후 분석 — 실거래 전환 승인의 판단 근거로 사용 가능. 체결 이력이 쌓이기 전(Phase 1 초기)에는 빈 결과가 정상

### 실서비스 착수까지 남은 항목 (요약)
1. 키움증권 앱키/시크릿 발급 + REST API 실연동(인증/시세/주문/잔고) — 가장 큰 블로커
2. 실데이터 축적 → 정답 라벨 생성 → AI 모델 재학습 (현재는 배선 검증용 합성 모델)
3. 실계좌 연동 후 위 백테스트로 전략 유효성 검증 → 기준 미달 시 실거래 보류
4. 알림 연동(Slack/이메일), 인프라 배포, 키움 약관/신고 의무 확인, 모의투자 최소 검증 기간 운영

## 산출물 인덱스
| 에이전트 | 경로 |
|---|---|
| DBA | [`docs/agents/dba/`](../dba/) |
| Designer | [`docs/agents/designer/`](../designer/) |
| Review | [`docs/agents/review/REVIEW_RESULT.md`](../review/REVIEW_RESULT.md) |
| Dev | [`docs/agents/dev/IMPLEMENTATION.md`](../dev/IMPLEMENTATION.md), 소스: `backend/`, `ml-service/`, `frontend/` |
| QA | [`docs/agents/qa/TEST_CASES.md`](../qa/TEST_CASES.md), [`docs/agents/qa/BUG_REPORT.md`](../qa/BUG_REPORT.md) |
| DevOps | [`docs/agents/devops/INFRA.md`](../devops/INFRA.md), `Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml` |
