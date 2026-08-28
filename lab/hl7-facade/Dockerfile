FROM python:3.12-alpine
WORKDIR /app
COPY server.py patients.json ./
ENV HL7_FACADE_PORT=8090
EXPOSE 8090
CMD ["python", "-u", "server.py"]
