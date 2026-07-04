# Startup and Authentication

## Entry flow

```text
Launcher intent
  -> SplashActivity
  -> AuthManager.isAuthenticated()
  -> MainActivity when authenticated
  -> LoginActivity otherwise
```

`SplashActivity` is the exported launcher activity. It keeps the splash visible for at least one second while checking the injected `AuthManager` and current user.

## Authentication selection

`AuthModule` binds one implementation according to generated build flags:

- `GoogleAuthManager` for normal builds.
- `MockAuthManager` when `BuildConfig.MOCK_AUTH` is enabled.

Current debug and release Gradle configurations both set mock authentication to false.

## Google sign-in

1. `LoginActivity` starts the Google sign-in intent through `AuthManager.signIn()`.
2. The activity receives the result in `onActivityResult()`.
3. `GoogleAuthManager.handleSignInResult()` maps the Google account to `UserEntity`.
4. `UserDao` stores the authenticated user in Room.
5. Login clears the activity stack and opens `MainActivity`.

## Session lookup

`GoogleAuthManager.isAuthenticated()` first checks the locally stored user, then checks the last Google account. This makes startup fast but means local and provider state must remain synchronized during logout and token invalidation.

## Key sources

- `ui/auth/SplashActivity.kt`
- `ui/auth/LoginActivity.kt`
- `auth/AuthManager.kt`
- `auth/GoogleAuthManager.kt`
- `auth/MockAuthManager.kt`
- `di/AuthModule.kt`
- `data/entities/UserEntity.kt`
