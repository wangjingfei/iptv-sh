FROM python:3.13-slim-bookworm

RUN groupadd --gid 1001 epg \
    && useradd --uid 1000 --gid 1001 --no-create-home --home-dir /app epg

WORKDIR /app
COPY --chown=1000:1001 --chmod=0555 app.py /app/app.py

USER 1000:1001
EXPOSE 8091
ENTRYPOINT ["python", "/app/app.py"]
