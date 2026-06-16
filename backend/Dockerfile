# ============================================================
#  Imagen del BACKEND (Spring Boot) — build multietapa.
#  Etapa 1: compila el .jar con Maven. Se descarta despues.
#  Etapa 2: solo el .jar + Java .
# ============================================================

# ---- Etapa 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Copia primero el pom para cachear la descarga de dependencias.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
# Copia el codigo y compila (sin tests para que el build sea rapido).
COPY src ./src
RUN mvn -B -DskipTests clean package

# ---- Etapa 2: runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /opt/aulahub
# Usuario sin privilegios para correr la app (no como root).
RUN useradd -r -s /usr/sbin/nologin aulahub
# Solo viaja el jar ya compilado (binario, no legible como codigo fuente).
COPY --from=build /app/target/*.jar app.jar
USER aulahub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
