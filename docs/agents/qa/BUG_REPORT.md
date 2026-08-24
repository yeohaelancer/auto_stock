# 🐛 QA Agent 버그 리포트

> 최종 업데이트: 2026-08-24 (BUG-006/BUG-003 재검증 반영)
> 상세 재현 단계는 [TEST_CASES.md](TEST_CASES.md)의 버그 리포트 섹션 참고. 본 문서는 추적용 요약이다.

| ID | 제목 | 심각도 | 상태 | 담당 |
|---|---|---|---|---|
| BUG-001 | `AccountService.getPositions` 미구현 — 포지션 위젯 항상 빈 상태 | 🟡 Major | ✅ Resolved | Dev |
| BUG-002 | `RiskEngine` 종목당/전체 포지션 한도 검증 미구현 | 🟡 Major | ✅ Resolved | Dev |
| BUG-004 | 신호→리스크→주문 오케스트레이션 서비스 미구현 | 🟡 Major | ✅ Resolved | Dev |
| BUG-005 | 장중 스케줄러 ↔ OrderService 자동 연동 미구현 | 🟢 Minor | ✅ Resolved | Dev |
| BUG-006 | `postMarketJob` 미구현 — 계좌 스냅샷 미생성 | 🟡 Major | ✅ Resolved | Dev |
| BUG-003 | AI 예측 서비스 더미 값 고정 (모델/피처 미연동) | 🟢 Minor | ✅ Resolved (배선 완료, 아래 제약 참고) | Dev |

## 열려 있는 코드 버그: 없음
이번 재검증으로 등록된 모든 버그(BUG-001~006, BUG-003)의 코드 레벨 이슈가 해소되었습니다.

## 구조적 제약 (버그 아님, 별도 추적)
- **AI 모델 실전 사용 불가**: `ml-service`는 이제 실제로 `feature_daily`를 조회하고 학습된 모델로 추론하지만, 그 모델은 실제 시세 이력 기반 정답 라벨이 아닌 **합성(synthetic) 라벨**로 학습되어 있습니다(`modelVersion=lgbm-synthetic-0.1`). 이는 코드 결함이 아니라 **실데이터 부재**에 따른 로드맵상 Phase 2 과제이며, 실거래 신호로 사용하는 것은 여전히 금지됩니다.
- **LIVE 모드 계좌 잔고**: `postMarketJob`은 MOCK 모드에서만 스냅샷을 계산·저장합니다. LIVE 모드 잔고 산정은 실제 키움 계좌 조회 API 연동(TODO) 이후에나 가능합니다.

## 배포 영향
모든 등록 버그가 해소되어 **모의투자(MOCK) 배포에 대한 QA 관점의 블로커는 없습니다**. 실거래(LIVE) 전환은 버그 상태와 무관하게 설계 문서 §11 로드맵 원칙에 따라 Phase 5까지 별도 수동 승인 전까지 금지됩니다.
