# 🚀 DevOps Agent 산출물

> 최종 업데이트: 2026-08-24

## 인프라 아키텍처
- **배포 환경**: dev(로컬 docker-compose) → staging(모의투자 전용) → production(모의투자 기본, 실거래는 별도 승인 절차 후 전환)
- **주요 컴포넌트**: `backend`(Spring Boot), `ml-service`(FastAPI), `postgres`, `redis`(캐싱, 선택)
- REST API 방식이므로 기존 JD WORK Linux 서버에 그대로 배포 가능 — OCX 방식과 달리 별도 Windows 브릿지 서버 불필요 (설계 §10)

## Docker 설정
- 백엔드: [`Dockerfile`](../../../Dockerfile) (멀티스테이지, Gradle 빌드 → JRE 런타임)
- AI 서비스: [`ml-service/Dockerfile`](../../../ml-service/Dockerfile)
- 오케스트레이션: [`docker-compose.yml`](../../../docker-compose.yml) — `postgres`/`ml-service` 헬스체크 통과 후 `backend` 기동 (`depends_on: condition: service_healthy`)

## CI/CD 파이프라인
[`.github/workflows/ci.yml`](../../../.github/workflows/ci.yml)
- PR/main 푸시 시: 백엔드 빌드+테스트 → ml-service import 체크 → 두 Docker 이미지 빌드
- **실거래(LIVE) 배포용 워크플로우는 의도적으로 별도 구성하지 않음** — 로드맵 Phase 5 진입 시점에 수동 승인 게이트를 포함한 별도 워크플로우를 신설할 것 (QA `BUG_REPORT.md` 배포 영향 참고)

## 환경 변수 관리
[`.env.example`](../../../.env.example) 참고. 원칙:
- 앱키/시크릿/계좌번호/DB 비밀번호는 `.env`(레포 미포함, `.gitignore` 대상)에서만 주입, 이미지에 하드코딩 절대 금지
- `TRADING_MODE`는 MOCK/LIVE 전환의 유일한 스위치 — 이 값을 바꾸는 배포는 반드시 사람이 직접 확인 후 진행 (자동 파이프라인이 자동으로 LIVE로 전환하지 않음)

## 모니터링 설정
- **헬스체크 엔드포인트**: `backend`는 Spring Boot Actuator 도입 예정(TODO), `ml-service`는 `GET /health` 구현됨
- **알림 조건**:
  - 키움 API 연결 끊김(`KiwoomMarketClient.isConnected()=false`) → 즉시 알림 + 신규 주문 자동 중단 (설계 §10)
  - `risk_log`에 `DAILY_LOSS_LIMIT` 또는 `MANUAL_KILL_SWITCH` 이벤트 발생 → 즉시 알림
  - `ml-service` 헬스체크 실패 → 알림 (신호 생성이 자동으로 스킵되므로 매매 정지는 아니나 조기 인지 필요)

## 📌 변경 이력 (2026-08-24, BUG-004 오케스트레이션 구현 단계)
`OrderService` 오케스트레이션 서비스 추가는 **인프라 변경이 필요 없음**을 확인했습니다. 신규 REST 엔드포인트(`POST /api/trading/signals/{stockCode}/process`)는 기존 `backend` 컨테이너/포트(8080)에 포함되며, 신규 컨테이너·환경변수·시크릿 추가 없음. Docker/CI/CD 설정 변경 없이 기존 [docker-compose.yml](../../../docker-compose.yml), [.github/workflows/ci.yml](../../../.github/workflows/ci.yml)를 그대로 사용합니다.

## 📌 변경 이력 (2026-08-24, BUG-005 스케줄러 자동 연동 단계)
`TradingScheduler`에 `@Scheduled` 장중 스캔 잡이 추가되었으나 **인프라 변경 없음** — 기존 `backend` 프로세스 내부 스케줄러(Spring `@EnableScheduling`)이므로 별도 컨테이너/Quartz 클러스터 불필요. 단, QA가 발견한 **BUG-006**(초기 `account_snapshot` 부재 시 자동 스캔이 항상 스킵됨)은 배포 체크리스트에 반영 — 최초 배포 시 초기 스냅샷 시딩 단계를 배포 절차에 추가할 것을 권고.

## 📌 변경 이력 (2026-08-24, BUG-006/BUG-003 일괄 수정 단계)
- **BUG-006**: 인프라 변경 없음. `postMarketJob`은 기존 `backend` 프로세스 내 배치이며 `trading.mock.initial-capital` 환경변수(`MOCK_INITIAL_CAPITAL`)만 신규 추가 — [.env.example](../../../.env.example)에 반영 필요(아래 체크리스트).
- **BUG-003**: `ml-service`가 PostgreSQL에 **직접 읽기 접속**하도록 변경됨 — [docker-compose.yml](../../../docker-compose.yml)에 `ml-service`용 DB 접속 환경변수와 `depends_on: postgres(healthy)` 추가. 시크릿은 `backend`와 동일한 `.env`의 `DB_PASSWORD`를 재사용(신규 시크릿 발급 없음). `model.pkl`은 이미지에 포함하지 않으므로 **운영 배포 전 `python train.py` 실행 결과물을 볼륨/이미지로 주입하는 절차**를 배포 파이프라인에 추가할 것.

## 배포 체크리스트
- [ ] `.env` 환경 변수 설정 완료 (특히 `DB_PASSWORD`, 키움 credential)
- [ ] `docker-compose.yml`의 `postgres` 초기화 스크립트(`docs/agents/dba/schema.sql`)로 DB 마이그레이션 실행 확인
- [ ] `backend`, `ml-service` 헬스체크 통과
- [ ] QA `BUG_REPORT.md` BUG-001/BUG-002 수정 완료 확인 (Phase 1 완료 조건)
- [ ] `TRADING_MODE=MOCK` 상태로 최초 배포 (LIVE 전환은 별도 승인 절차)
- [ ] (선택) 배포 첫날부터 장중 스캔이 바로 동작하길 원하면 `backend/scripts/seed-initial-snapshot.sh` 실행 — 안 해도 둘째 날부터는 `postMarketJob`이 자동으로 스냅샷을 만들어 정상 동작함 (MOCK 전용, LIVE는 스크립트 자체가 거부)
- [ ] `.env`에 `MOCK_INITIAL_CAPITAL`(모의투자 초기 시드머니) 설정 확인
- [ ] `ml-service` 배포 전 `python train.py`로 `model.pkl` 생성 후 이미지/볼륨에 포함 (없으면 placeholder 응답만 반환)
- [ ] AI 예측은 합성 라벨 모델 상태임을 운영 담당자에게 명확히 공유 — 실거래 신호로 사용 금지
- [ ] 롤백 플랜: 이전 이미지 태그로 `docker compose up -d --no-build`(이미지 재사용) 재기동, DB는 마이그레이션 이력 기반 롤백 스크립트 별도 준비 필요(TODO)

## 장애 대응 Runbook
| 장애 유형 | 감지 방법 | 조치 |
|---|---|---|
| 키움 API 연결 끊김 | `KiwoomMarketClient.isConnected()=false` 또는 반복 API 에러 | 신규 주문 자동 중단, 기존 포지션 유지, 담당자 알림 (설계 §10) |
| AI 예측 서비스 응답 지연/실패 | `ml-service` 헬스체크 실패 또는 `PredictionClient` 타임아웃 | 해당 종목 신호 생성 스킵 (기본값: 매매 안 함), 절대 임의 값 대체 금지 |
| DB 장애 | `postgres` 헬스체크 실패 | 전체 시스템 안전 정지 (`backend` 재기동 차단), 담당자 알림 |
| 긴급정지(Kill Switch) 오탐 의심 | `risk_log` 이벤트 급증 | 수동 확인 후 `RiskEngine` 상태 리셋은 재배포(설정 재확인) 절차로만 수행 |
