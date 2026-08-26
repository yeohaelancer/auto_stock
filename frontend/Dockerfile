# 주식 자동매매 프론트엔드 Dockerfile (단독 실행, Vue 3 + Vite)
# Stock auto-trading frontend Dockerfile (standalone, Vue 3 + Vite)
# 멀티스테이지 빌드: 빌드 산출물만 Nginx 런타임 이미지에 포함
# Multi-stage build: only the build output ships in the Nginx runtime image

FROM node:20-alpine AS build
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
