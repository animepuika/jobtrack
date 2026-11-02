# Build stage: compile and package JobTrack with the Maven Wrapper. Tests run in CI
# before the image is built, so they are skipped here.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh mvnw -B -q dependency:go-offline
COPY src/ src/
RUN sh mvnw -B -q package -DskipTests

# Run stage: only the Java runtime and the packaged app, running as a normal user.
# Uploaded CVs go to /app/data; mount a volume there so they survive restarts.
FROM eclipse-temurin:25-jre-noble
RUN groupadd --system jobtrack \
	&& useradd --system --gid jobtrack --uid 10001 --no-create-home jobtrack \
	&& mkdir -p /app/data/documents \
	&& chown -R jobtrack:jobtrack /app/data
WORKDIR /app
COPY --from=build /src/target/jobtrack-*.jar app.jar
USER jobtrack
EXPOSE 8080
VOLUME /app/data
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
