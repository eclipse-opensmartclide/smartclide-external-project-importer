FROM eclipse-temurin:17-alpine
VOLUME /tmp
COPY target/smartclide-external-project-importer-0.0.1-SNAPSHOT.jar app.jar
ENV PROJECT_CREATION_SERVICE_URL=""
ENTRYPOINT java -jar /app.jar -Dspring-boot.run.arguments=--projectCreationServiceUrl=${PROJECT_CREATION_SERVICE_URL:-NOT_SET}