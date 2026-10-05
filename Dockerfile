FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

EXPOSE 8000

ENTRYPOINT ["java", "-jar", "target/team-a-os-engine-1.0.0.jar"]
