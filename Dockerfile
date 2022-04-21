FROM eclipse-temurin:11-alpine
VOLUME /tmp
COPY target/smartclide-external-project-importer-0.0.1-SNAPSHOT.jar app.jar
ENV SMARTCLIDE_API_URL=""
ENTRYPOINT java -jar /app.jar -Dspring-boot.run.arguments=--smartclide.api.url=${SMARTCLIDE_API_URL:-NOT_SET}