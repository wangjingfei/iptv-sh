FROM python:3.13-slim-bookworm

RUN apt-get update \
    && apt-get install --no-install-recommends -y ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 iptv \
    && useradd --uid 10001 --gid 10001 --no-create-home --home-dir /nonexistent iptv

WORKDIR /app
COPY --chown=10001:10001 --chmod=0555 app.py /app/app.py

USER 10001:10001
EXPOSE 8090

ENTRYPOINT ["python", "/app/app.py"]
