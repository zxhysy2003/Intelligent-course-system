# syntax=docker/dockerfile:1.7

FROM continuumio/miniconda3:24.9.2-0

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY recommend-service/environment.yml ./environment.yml
RUN conda env create -f environment.yml \
    && conda clean -afy

COPY recommend-service/ ./

EXPOSE 8000

CMD ["conda", "run", "--no-capture-output", "-n", "lab_autumn", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
