# The M[iage].byte project :: Store

This module is the file store service of the Mbyte platform, built with Quarkus.

## Discord Bot Integration

The store exposes a dedicated endpoint for the [Mbyte-bot](https://github.com/LucieLaury/Mbyte-bot) Discord bot.

### Endpoint

```
POST /api/bot/discord
```

The bot must send the `X-Bot-Token` header with the secret token configured in `store.bot.token`.

#### Request body (JSON array of messages)

```json
[
  {
    "channel": "general",
    "author": "username",
    "content": "Hello world!",
    "timestamp": 1700000000000
  }
]
```

#### Response

- `201 Created` with `{ "file": "discord-<timestamp>.txt" }` on success.
- `401 Unauthorized` if the token is missing or invalid.
- `400 Bad Request` if the messages list is empty.

### Configuration

In `application.properties`, set a strong secret for the bot token:

```properties
store.bot.token=your-secret-token
```

The Discord bot must be configured with the same token value and point to the store's URL, e.g.:

```
POST https://<store-host>/api/bot/discord
X-Bot-Token: your-secret-token
```

### How messages are stored

Each call to `POST /api/bot/discord` creates a plain text file named `discord-<yyyyMMdd-HHmmss-SSS>.txt` inside a `discord/` folder at the root of the store. Each line of the file has the format:

```
[<ISO-timestamp>] #<channel> <<author>> <content>
```

### Retrieving stored Discord messages

All stored Discord message files are accessible through the standard store REST API (OIDC authentication required):

1. **List the `discord` folder contents** — find the folder node ID first:

   ```
   GET /api/nodes           → redirects to the root node ID
   GET /api/nodes/<root-id>/children
   ```

   Locate the node with `"name": "discord"` and note its `"id"`.

2. **List messages in the discord folder**:

   ```
   GET /api/nodes/<discord-folder-id>/children
   ```

3. **Download a specific message file**:

   ```
   GET /api/nodes/<file-node-id>/content
   ```

   Or to force a download:

   ```
   GET /api/nodes/<file-node-id>/content?download=true
   ```

4. **Search across all stored Discord content** using the full-text search endpoint:

   ```
   GET /api/search?q=<your-query>
   ```

> **Which store?** Documents are stored in the **Mbyte Store** service (this module), running at port `8089`. File metadata is persisted in **PostgreSQL** (database `store`) and the actual file bytes are stored on the **filesystem** under `${store.data.home}` (default: `~/.mbyte/data`).

---

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it's not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:
```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:
```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/code-with-quarkus-1.0.0-SNAPSHOT-runner`

You can learn more about building native executables by consulting https://quarkus.io/guides/maven-tooling.
