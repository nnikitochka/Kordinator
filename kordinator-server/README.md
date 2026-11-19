# Kordinator music server

This module builds the new Ktor backend as a plain JAR with runtime dependencies in `target/lib`. It has no Spring or Servlet dependencies.

```bash
mvn -pl kordinator-server -DskipTests package
java -jar kordinator-server/target/kordinator-server.jar \
  --airsonic.home=/var/lib/kordinator \
  --airsonic.http.host=0.0.0.0 \
  --airsonic.http.port=8080
```

On first startup, create the administrator interactively:

```bash
java -jar kordinator-server/target/kordinator-server.jar \
  --airsonic.home=/var/lib/kordinator \
  --airsonic.create-admin
```

The server reads command-line options, JVM properties, environment variables, `application.properties`, then `$airsonic.home/airsonic.properties` in descending priority. It uses `$airsonic.home/db/kordinator` by default. Database connection settings use `airsonic.database.*`; listener settings use `airsonic.http.*`.
