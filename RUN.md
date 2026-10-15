# How to Run the DPIP Application

This document outlines the commands needed to build, run, and interact with the Developer Project Intelligence Platform (DPIP) Spring Boot application.

## Prerequisites

- **Java JDK 21** or higher.
- **Apache Maven 3.9** or higher.

---

## Running the Application

### 1. Run using Maven (Spring Boot Plugin)
To compile and run the application directly from the source directory using the default `local` profile (which uses an in-memory H2 database):

```bash
mvn spring-boot:run
```

To run with a specific active profile (e.g., `prod` or `local` explicitly):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

### 2. Packaging and Running the JAR
To package the application into a runnable JAR file and then run it:

```bash
# Package the application
mvn clean package

# Run the packaged JAR
java -jar target/platform-0.0.1-SNAPSHOT.jar
```

To specify an active profile with the packaged JAR:

```bash
java -jar target/platform-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

---

## Running Tests

To run the automated unit and integration tests:

```bash
mvn test
```
