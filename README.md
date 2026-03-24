# Get401 Auth Core

The **Get401 Auth Core** is a foundational Java library designed to seamlessly integrate authentication and authorization for applications using the Get401 identity platform. It provides the core abstractions, security annotations, and dynamic public key provisioning necessary to build framework-specific Get401 integrations (such as for Spring Boot).

---

## 🚀 Features

- **Dynamic Key Provisioning:** Automatically fetches the required Ed25519 public key from the Get401 platform to verify JSON Web Tokens (JWTs).
- **Core Security Annotations:** Declarative, easy-to-use annotations for enforcing authentication, role-based access control (RBAC), and scope-based authorization.
- **Backend Management Client:** A typed HTTP client (`Get401Client`) for server-to-server user management — list, retrieve, and disable users via the Get401 backend API.
- **Lightweight & Modern:** Built for Java 21+ using the built-in HTTP Client with HTTP/2 and modern fast Ed25519 cryptography.

## 📦 Requirements

- **Java:** Version 21 or higher.

## 🛠️ Installation

Add the dependency to your project. (If you are working with a snapshot or local module, ensure you have your local/internal repositories configured properly).

### Gradle

```groovy
dependencies {
    implementation 'com.get401:auth-core:0.0.1'
}
```

### Maven

```xml
<dependency>
    <groupId>com.get401</groupId>
    <artifactId>auth-core</artifactId>
    <version>0.0.1</version>
</dependency>
```

---

## 🧩 Core Components

### 1. Security Annotations

Get401 Auth Core provides intuitive annotations that can be applied to both methods and classes to strictly enforce token-based security policies. 

- **`@AuthGet401`**
  Requires the request to be authenticated via a valid, verifiable JWT token (by default expected via an `aact` cookie). Used as the base line requirement.
  
- **`@VerifyRoles`**
  Specifies the roles required to access the annotated target. The user's validated JWT must contain **at least one** of the specified roles to grant access.
  
- **`@VerifyScope`**
  Specifies the scopes required to access the annotated target. The user's validated JWT scope string must contain **all** of the specified scopes to grant access.

*Example structural usage (framework processing logic is handled downstream):*

```java
import com.get401.auth.core.annotation.AuthGet401;
import com.get401.auth.core.annotation.VerifyRoles;
import com.get401.auth.core.annotation.VerifyScope;

@AuthGet401
public class SecureController {

    @VerifyRoles({"admin", "editor"})
    public String editArticle() {
        return "Editing authorized.";
    }

    @VerifyScope({"read:billing", "write:billing"})
    public String updateBilling() {
        return "Billing updated.";
    }
}
```

### 2. `JwtPublicKeyProvider`

The `JwtPublicKeyProvider` is a thread-safe provisioner responsible for fetching and caching your application's Ed25519 public key dynamically over the network from the Get401 API (`https://app.get401.com/v1/apps/auth/public-key`).

It communicates with the platform using your unique authenticated `appId` and `origin` headers to securely pull the exact verified public key required for verifying the localized JWT tokens.

#### Initialization & Fetching Example

```java
import com.get401.auth.core.JwtPublicKeyProvider;
import java.security.PublicKey;

// 1. Initialize the provider with your App ID and Origin
String appId = "your-get401-app-id";
String origin = "https://yourdomain.com";
String get401BaseUrl = "https://app.get401.com"; // Uses default if null is passed

JwtPublicKeyProvider keyProvider = new JwtPublicKeyProvider(appId, origin, get401BaseUrl);

// 2. Fetch the public key for JWT signature validation
PublicKey publicKey = keyProvider.getPublicKey();
```

> **Note:** The `getPublicKey()` method caches the parsed Ed25519 key in memory after the first successful network fetch, reducing latency to virtually zero on all subsequent calls. The key is automatically refreshed when its server-issued expiry time is reached.

---

### 3. `Get401Client`

`Get401Client` is a typed HTTP client for server-to-server communication with the Get401 backend API. It is intended for use in trusted backend environments only — never in client-facing code. All requests are authenticated with an API key and are automatically scoped to the tenant that owns the key.

> **Obtaining an API key:** Keys are created through the Get401 platform by an authenticated admin user. The key value (`sk_live_...`) is returned only at creation time — store it securely.

#### Initialization

```java
import com.get401.auth.core.client.Get401Client;

// Use the default base URL (https://app.get401.com)
Get401Client client = new Get401Client("sk_live_your_api_key");

// Or specify a custom base URL (e.g. for local/staging environments)
Get401Client client = new Get401Client("sk_live_your_api_key", "https://staging.get401.com");
```

#### List Users

Users are returned in cursor-paginated pages. Each response contains an opaque `next` cursor — pass it to the next call to advance through the result set. When `next` is `null`, you have reached the last page.

```java
import com.get401.auth.core.model.UsersPage;
import com.get401.auth.core.model.User;

// First page — default server page size
UsersPage page = client.listUsers();

// First page — explicit page size (1–100)
UsersPage page = client.listUsers(50);

// Subsequent pages — pass the cursor from the previous response
while (page.getNext() != null) {
    page = client.listUsers(page.getNext());
    // process page.getItems() ...
}
```

#### Get a Single User

```java
User user = client.getUserById("usr_abc123");

System.out.println(user.getName());   // "Jane Doe"
System.out.println(user.getEmail());  // "jane@example.com"
System.out.println(user.isActive());  // true
```

#### Disable a User

Disabling a user sets their `is_active` flag to `false`, preventing future logins. The user record is retained and can be re-enabled through the platform UI.

```java
client.disableUser("usr_abc123");
```

#### Error Handling

All methods throw `Get401ApiException` (a `RuntimeException`) on non-2xx responses. The exception exposes the HTTP status code and the error code string from the response body, making it easy to handle specific failure cases.

```java
import com.get401.auth.core.client.Get401ApiException;

try {
    User user = client.getUserById("usr_unknown");
} catch (Get401ApiException e) {
    switch (e.getStatus()) {
        case 401 -> // invalid or expired API key
        case 404 -> // user not found or belongs to a different tenant
        case 500 -> // unexpected server error
    }
    // e.getErrorCode() returns the raw string, e.g. "not_found"
}
```

---

### 4. Model Entities

#### `User`

Represents a single user belonging to the tenant associated with your API key.

| Field | Type | Description |
|---|---|---|
| `id` | `String` | Unique public identifier (e.g. `usr_abc123`). Use this in all API calls. |
| `name` | `String` | Display name of the user. |
| `email` | `String` | Email address of the user. |
| `active` | `boolean` | `true` if the user is allowed to authenticate. `false` means the account is disabled. |
| `createdAt` | `String` | ISO 8601 UTC timestamp of when the record was created. |
| `updatedAt` | `String` | ISO 8601 UTC timestamp of the last modification. |

> The JSON fields `is_active`, `created_at`, and `updated_at` are mapped automatically. Use the standard Java getters (`isActive()`, `getCreatedAt()`, `getUpdatedAt()`).

#### `UsersPage`

Wraps a single page of results from the list-users endpoint.

| Field | Type | Description |
|---|---|---|
| `items` | `List<User>` | The users on this page. May be empty on the last page, never `null`. |
| `next` | `String` | Opaque cursor for the next page. `null` when this is the last page. |

---

## 🔑 Technical Details

- **Cryptographic Algorithm:** Supports and strictly verifies keys configured for standard **Ed25519** elliptic curves dynamically generated by Java `KeyFactory`.
- **API Fetch Headers:** `X-App-Id` and `Origin` headers are required for querying the Get401 API.
- **Dependencies Structure:** Keeps transitives lean. Exposes `jackson-databind`, `jjwt-api`, and `slf4j-api` to dependent applications.

## 📤 Publishing

To publish new versions of this library to Maven Central, please refer to the [Publishing Guide](PUBLISHING.md).

## ⚖️ License

All rights reserved by Get401.
