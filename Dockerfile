FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests


FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/url-shortener-0.0.1-SNAPSHOT.jar ./shortener.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx320m", "-jar", "shortener.jar"]