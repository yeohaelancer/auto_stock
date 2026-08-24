# AI 예측 서비스 Dockerfile (FastAPI)
# AI prediction service Dockerfile (FastAPI)

FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py train.py .
# 이미지에는 학습 스크립트만 포함하고 모델(model.pkl)은 포함하지 않는다 — 컨테이너 최초 기동 시 학습되지 않은
# 상태(placeholder 응답)로 시작하며, 실제 운영 전 별도 학습 파이프라인에서 만든 model.pkl을 볼륨/이미지로 주입할 것.
# The image ships the training script only, not model.pkl — it starts untrained (placeholder responses)
# until a model.pkl produced by a real training pipeline is mounted/baked in before production use.
EXPOSE 8001
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
