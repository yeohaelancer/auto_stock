# 프로젝트 멀티 에이전트 설정

## 에이전트 구조
이 프로젝트는 7개의 전문 에이전트 역할로 구성된 멀티 에이전트 시스템으로 운영된다.
모든 작업은 아래 에이전트 역할을 명확히 구분하여 진행한다.

```
Manager → (DBA + Designer 병렬) → Review → Dev → QA → DevOps
```

## 에이전트 역할 정의

| 에이전트   | 역할 파일                            | 담당 영역                      |
|-----------|-------------------------------------|-------------------------------|
| Manager   | `.claude/rules/manager.md`          | 요구사항 분석, 작업 조율, 승인  |
| DBA       | `.claude/rules/dba.md`              | DB 설계, ERD, DDL              |
| Designer  | `.claude/rules/designer.md`         | UI/UX, 화면 명세, 디자인 시스템 |
| Review    | `.claude/rules/review.md`           | 코드 리뷰, 품질 게이트          |
| Dev       | `.claude/rules/dev.md`              | 구현, API, 컴포넌트             |
| QA        | `.claude/rules/qa.md`               | 테스트, 버그 리포트             |
| DevOps    | `.claude/rules/devops.md`           | Docker, CI/CD, 배포             |

## 작업 시작 방법

### 방법 1: 슬래시 커맨드 사용 (권장)
```
/manager    → 새 요구사항 분석 시작
/dba        → DB 설계 작업 시작
/designer   → UI/UX 설계 작업 시작
/review     → 산출물 검토 요청
/dev        → 개발 작업 시작
/qa         → 테스트/버그 리포트
/devops     → 배포 설정 작업
/pipeline   → 전체 파이프라인 자동 실행
```

### 방법 2: 역할 명시 프롬프트
프롬프트 앞에 `[에이전트명]` 을 붙여 해당 에이전트 모드로 진행한다.
예: `[DBA] users 테이블과 orders 테이블 관계를 설계해줘`

## 산출물 저장 위치
- DB 스키마: `docs/agents/dba/`
- UI 명세: `docs/agents/designer/`
- 리뷰 결과: `docs/agents/review/`
- 테스트 케이스: `docs/agents/qa/`
- 인프라 설정: `docs/agents/devops/`

## 공통 규칙
- 모든 코드에 대한 주석은 한국어, 영어 2개 언어로 작성
- 모든 응답은 한국어로 작성 (코드는 영어)
- 산출물은 마크다운 또는 코드 파일로 저장
- 에이전트 간 산출물 공유 시 파일 경로를 명시할 것
- 다음 에이전트에게 전달할 내용은 반드시 요약 섹션을 포함할 것
