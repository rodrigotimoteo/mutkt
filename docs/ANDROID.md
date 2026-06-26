# MutKt for Android

## Quick start (5 min)

### Prerequisites

- Android Gradle Plugin 8.0+ (older versions not supported)
- Kotlin 2.1.0+
- Robolectric 4.13+ (required for unit tests that use Android framework)

### Setup

In your `app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    repositories {
        mavenLocal()  // for local dev, or remove for Maven Central
        google()
        mavenCentral()
    }
    dependencies {
        classpath("io.github.rodrigotimoteo:mutation-gradle-plugin:0.3.0")
    }
}

apply(plugin = "io.github.rodrigotimoteo.mutation-kotlin")

android {
    // ... your existing config ...
    testOptions {
        unitTests.isIncludeAndroidResources = true  // required for Robolectric
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

mutationTest {
    targetClasses.from("com.example.app.*")
    testClasses.from("com.example.app.*Test")
    // Optional: pick a specific variant (default: debug)
    androidVariant.set("debug")
}
```

Run:

```bash
./gradlew mutationTest
```

Report at `app/build/reports/mutation/mutation-report.html`.

## How it works

When MutKt detects an Android plugin (`com.android.application` or `com.android.library`):

1. **Variant resolution** — queries AGP for the runtime classpath, classes dirs, test classes dirs, and the matching `android.jar` from your SDK install
2. **AAR extraction** — extracts `classes.jar` from any `.aar` dependencies in your runtime classpath
3. **Generated class filtering** — excludes framework-generated classes from mutation: `R`, `R$*`, `BuildConfig`, `ComposableSingletons$*`, `*_Impl` (Room), `*_Factory` (Dagger), `*Hilt*`, etc.
4. **Compile task wiring** — automatically depends on the variant's `compile<Variant>Kotlin` and `compile<Variant>UnitTestKotlin` tasks
5. **Robolectric execution** — runs your unit tests in a JVM with shadowed Android framework, so they can load `android.*` classes without a device

## Configuration

### DSL properties (Android-specific)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `isAndroid` | `Property<Boolean>` | auto-detected | Set to true if MutKt failed to detect AGP |
| `androidPluginType` | `Property<String>` | auto-detected | `"application"` or `"library"` |
| `androidVariant` | `Property<String>` | `"debug"` | Which build variant to test |
| `excludeGeneratedClasses` | `SetProperty<String>` | 20 patterns | Classes to skip during mutation (R, BuildConfig, etc.) |

### Customizing generated class patterns

```kotlin
mutationTest {
    excludeGeneratedClasses.set(setOf(
        "R", "R$*", "BuildConfig",
        "*_Impl",  // Room
        "*_Factory",  // Dagger
        "*MyCustom*"
    ))
}
```

## Known limitations

- **Instrumented tests not supported** — MutKt only runs JVM unit tests. Instrumented tests (on device/emulator) are out of scope.
- **No Hilt test execution** — Hilt's test rules use a custom test runner that doesn't work with MutKt's JUnit Platform Launcher-based runner. Use Robolectric for now.
- **Compose runtime classes skipped** — Composable functions, lambdas, and `@ComposableSingletons$*` are excluded from mutation.
- **AGP 7.x not supported** — Requires AGP 8.0+ for the modern `AndroidComponentsExtension` API.

## Troubleshooting

### `NoClassDefFoundError: android/os/Parcelable`

Your test classpath is missing `android.jar`. MutKt should auto-detect this from `$ANDROID_HOME/platforms/`. Verify:

```bash
echo $ANDROID_HOME
ls $ANDROID_HOME/platforms/
```

If missing, set `ANDROID_HOME` in `local.properties`:

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

### `Task with name 'compileKotlin' not found`

This happens on Android because AGP uses variant-specific task names (`compileDebugKotlin`, `compileReleaseKotlin`). MutKt auto-detects the active variant via AGP's `AndroidComponentsExtension` and wires the right compile tasks for you. If auto-detection fails (e.g. unusual variant configuration), set `androidVariant` explicitly:

```kotlin
mutationTest {
    androidVariant.set("release")
}
```

### Mutations all show as `NO_COVERAGE`

The JUnit Platform Launcher-based test runner isn't executing your `@Test` methods. Common causes:

1. Tests use Hilt's `@HiltAndroidTest` — not supported, use Robolectric instead
2. Tests use JUnit 5 (`@Test` from `org.junit.jupiter.api`) — MutKt supports both, but verify
3. Test class doesn't follow `*Test` / `*Tests` naming pattern

### `AGP attribute conflict` errors

Your AAR dependencies don't have matching variant attributes. This is rare. Workaround: add the AAR's `classes.jar` to your classpath manually.

## Mocking support (MockK + Mockito)

MutKt's JUnit Platform Launcher-based test runner does NOT execute JUnit 5 extensions. This means `@ExtendWith(MockKExtension::class)` and `@ExtendWith(MockitoExtension::class)` are **ignored**. You must initialize mocks manually in `@BeforeEach`.

### Compatibility matrix

| Feature | Works? | Notes |
|---------|--------|-------|
| **MockK** regular mocks (`mockk<T>()` on interfaces/open classes) | ✅ Yes | Standard ByteBuddy subclass |
| **MockK** final-class inline mocks | ❌ No | Requires Java agent attach + bootstrap dispatcher; conflicts with MutKt's classloader |
| **MockK** `mockkStatic()` | ❌ No | Same agent issue + global state leaks across parallel mutations |
| **MockK** `@MockK` annotation | ⚠️ Manual | Don't use `MockKExtension`; call `MockKAnnotations.init(this)` in `@BeforeEach` |
| **Mockito** (subclass mock maker) regular mocks | ✅ Yes | Default before Mockito 5 |
| **Mockito** 5+ (inline mock maker) | ❌ No | `MockMethodDispatcher` bootstrap conflict with MutKt's classloader |
| **Mockito** `mockStatic()` | ❌ No | Same inline-maker issue |
| **Mockito** `@Mock` annotation | ⚠️ Manual | Don't use `MockitoExtension`; call `MockitoAnnotations.openMocks(this)` in `@BeforeEach` |
| **Robolectric** + mocks | ❌ No | `SandboxClassLoader` shadows dispatcher classes |

### MockK — what works

```kotlin
@RunWith(RobolectricTestRunner::class)
class UserServiceTest {
    @Before
    fun setUp() {
        // DO use @Before (JUnit 4) + manual init, NOT @ExtendWith
        MockKAnnotations.init(this)
    }
    
    @Test
    fun `mocks work for interfaces`() {
        val mockPrefs = mockk<SharedPreferences>()
        every { mockPrefs.getString("user", null) } returns "alice"
        assertEquals("alice", mockPrefs.getString("user", null))
    }
}
```

### Mockito — what works

```kotlin
@RunWith(RobolectricTestRunner::class)
class UserServiceTest {
    @Before
    fun setUp() {
        // DO use @Before (JUnit 4) + manual init, NOT @ExtendWith
        MockitoAnnotations.openMocks(this)
    }
    
    @Test
    fun `mocks work for classes`() {
        val mockPrefs = mock<SharedPreferences>()
        whenever(mockPrefs.getString("user", null)).thenReturn("bob")
        assertEquals("bob", mockPrefs.getString("user", null))
    }
}
```

### What doesn't work

Avoid these patterns — they require classloader-level agent support that MutKt doesn't provide:

- `mockkStatic(KotlinXCoroutines::class)` — MockK
- `Mockito.mockStatic(Files::class)` — Mockito 5+
- `mockkConstructor(MyClass::class)` — MockK
- `@ExtendWith(MockKExtension::class)` — JUnit 5 extension
- `@ExtendWith(MockitoExtension::class)` — JUnit 5 extension

If you need any of these, run `./gradlew test` (the regular JUnit test task) instead of `./gradlew mutationTest`.

### Why limitations exist

MutKt loads test classes in a custom `URLClassLoader` parented to `ClassLoader.getPlatformClassLoader()`. This isolates mutated bytecode from the engine's own dependencies but prevents Java instrumentation agents (used by inline mocking) from installing their dispatchers correctly.

For full mocking support, future MutKt releases will improve classloader + agent integration under the JUnit Platform Launcher.

## Example

See `mutation-sample-android/` in the MutKt project for a working example with Robolectric.
