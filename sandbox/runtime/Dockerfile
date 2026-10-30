FROM node:22-bullseye-slim AS node
FROM eclipse-temurin:21-jdk-jammy
COPY --from=node /usr/local/bin/node /usr/local/bin/node
RUN apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/* && groupadd -g 10001 sandbox && useradd -u 10001 -g sandbox sandbox
COPY execute.mjs /runner/execute.mjs
USER 10001:10001
WORKDIR /work
ENTRYPOINT ["node","/runner/execute.mjs"]
