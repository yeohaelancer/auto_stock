전체 멀티 에이전트 파이프라인을 순서대로 실행합니다.

요구사항: $ARGUMENTS

다음 순서로 각 에이전트 역할을 수행하세요:

1. **[Manager]** `.claude/rules/manager.md` 규칙으로 요구사항 분석 및 작업 지시 작성
2. **[DBA]** `.claude/rules/dba.md` 규칙으로 DB 스키마 설계 → `docs/agents/dba/` 저장
3. **[Designer]** `.claude/rules/designer.md` 규칙으로 UI/UX 설계 → `docs/agents/designer/` 저장
   - (DBA와 병렬 진행 — 두 산출물 모두 완성 후 Review로 이동)
4. **[Review]** `.claude/rules/review.md` 규칙으로 DBA + Designer 산출물 검토 → `docs/agents/review/` 저장
5. **[Dev]** `.claude/rules/dev.md` 규칙으로 핵심 기능 구현 → 소스 + `docs/agents/dev/` 저장
6. **[QA]** `.claude/rules/qa.md` 규칙으로 테스트 케이스 설계 + 버그 리포트 → `docs/agents/qa/` 저장
7. **[DevOps]** `.claude/rules/devops.md` 규칙으로 Docker/CI/CD 설정 → `docs/agents/devops/` 저장

각 단계 완료 시 "✅ [에이전트명] 완료" 를 출력하고 다음 단계로 진행하세요.
