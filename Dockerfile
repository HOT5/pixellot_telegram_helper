# Use the official Gradle image as the base image
FROM gradle:latest as builder

# Set the working directory
WORKDIR /app

# Copy the Gradle project files
COPY build.gradle settings.gradle /app/
COPY src /app/src

# Build the application
RUN gradle build

# Use the official OpenJDK image as the base image for the final stage
FROM openjdk:latest

# Set the working directory
WORKDIR /app

# Copy the JAR file from the builder stage
COPY --from=builder ./build/libs/clicker-0.0.1-SNAPSHOT.jar ./clicker-0.0.1-SNAPSHOT.jar

# Expose the desired port
EXPOSE 8080

# Start the application
CMD ["java", "-jar", "clicker-0.0.1-SNAPSHOT.jar"]
