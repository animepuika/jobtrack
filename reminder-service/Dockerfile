# Build stage: compile and package the reminder service with the Maven Wrapper.
# Tests run in CI before the image is built, so they are skipped here.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh mvnw -B -q dependency:go-offline
COPY src/ src/
RUN sh mvnw -B -q package -DskipTests

# Run stage: only the Java runtime and the packaged service, running as a normal user.
FROM eclipse-temurin:25-jre-noble
RUN groupadd --system reminders \
	&& useradd --system --gid reminders --uid 10001 --no-create-home reminders
WORKDIR /app
COPY --from=build /src/target/reminder-service-*.jar app.jar
USER reminders
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
