![Maven Central](https://img.shields.io/maven-central/v/io.synthesized/jdbcrest-core)


# JDBCRest

JDBCRest is a Java library and a standalone server that turns your JDBC-accessible database directly into a RESTful API. The structural constraints and permissions in the database determine the API endpoints and operations. This project is heavily inspired by [PostgREST](https://postgrest.org).

Conceptually, JDBCRest is:
- **[PostgREST](https://postgrest.org) reimplemented in Java**
- Generalized from **PostgreSQL-only** to **JDBC-backed databases**

## Comparison with PostgREST

JDBCRest aims to be compatible with PostgREST's API surface while extending its capabilities to any database with a JDBC driver.

To ensure behavioral parity, the project includes a suite of **comparison tests** that run against both PostgREST and JDBCRest. These tests use PostgREST as the behavioral reference, executing the same operations against multiple backends and implementations to verify consistency.

## Documentation

See [product documentation](https://docs.synthesized.io/jdbcrest/).

## Requirements

- Java 17 or higher

## Quick Start

### Build

```shell
mvn clean install -DskipTests
```

### Run standalone server

JDBCRest can be run as a standalone server. You can configure it using Spring Boot properties.

```shell
java -Dspring.datasource.url=jdbc:postgresql://localhost:5432/mydb \
     -Dspring.datasource.username=myuser \
     -Dspring.datasource.password=mypass \
     -Dspring.datasource.driver-class-name=org.postgresql.Driver \
     -Djdbcrest.database-type=JOOQ_POSTGRES \
     -jar cli/target/jbdcrest-cli.jar
```

### Usage as a Library

Add the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>io.synthesized</groupId>
  <artifactId>jdbcrest-core</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## jOOQ Dependency Policy

By default, this open-source project relies on the open-source version of jOOQ. However, if your organization uses jOOQ Pro, you can benefit from support for additional database types, such as SAP HANA.

The jOOQ dependency in the `core` module is marked as **optional**. This allows you to exclude the default jOOQ dependency and provide your own version (e.g., jOOQ Pro) in your project's `pom.xml`.

If you want to develop or test jOOQ Pro specific features in this library, use the `jooq-pro` Maven profile:

```shell
mvn clean install -Pjooq-pro
```

## Publishing a new version to Maven Central

```shell
mvn -Ppublish-central clean deploy
```
