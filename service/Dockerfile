FROM eclipse-temurin:8-jre-jammy

WORKDIR /opt/zorth-web-sql-service
COPY target/zorth-web-sql-service-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
USER 65532:65532

ENTRYPOINT ["java", "-jar", "/opt/zorth-web-sql-service/app.jar"]
