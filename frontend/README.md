# 자동매매 프론트엔드 (단독 실행)

JD WORK 등 외부 호스트 대시보드 없이 이 디렉터리만으로 독립 실행되는 Vue 3 + Vite 앱입니다.

## 개발 서버로 실행

```bash
npm install
npm run dev
```

`http://localhost:5170` 에서 접속합니다. `/api` 요청은 `vite.config.js` 프록시를 통해
백엔드(`BACKEND_URL`, 기본 `http://localhost:8081`)로 전달되므로 별도 CORS 설정이 필요 없습니다.

## 운영 빌드

```bash
npm run build   # dist/ 생성
npm run preview # 빌드 결과 미리보기
```

## Docker Compose로 전체 스택 실행

루트 디렉터리에서 `docker-compose up` 실행 시 `frontend` 서비스가 Nginx로 빌드 산출물을 서빙하며
`/api` 요청을 백엔드 컨테이너로 프록시합니다.

---

# Auto-trading Frontend (Standalone)

A Vue 3 + Vite app that runs independently from this directory alone, with no external host
dashboard (e.g. JD WORK) required.

## Run the dev server

```bash
npm install
npm run dev
```

Open `http://localhost:5170`. `/api` requests are forwarded to the backend
(`BACKEND_URL`, default `http://localhost:8081`) via the `vite.config.js` proxy, so no CORS
setup is needed.

## Production build

```bash
npm run build   # outputs dist/
npm run preview # preview the build
```

## Run the full stack with Docker Compose

Running `docker-compose up` from the repo root starts a `frontend` service that serves the
built app via Nginx and proxies `/api` requests to the backend container.
