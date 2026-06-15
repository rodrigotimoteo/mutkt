# MutKt Mocking Reference

Quick reference for MockK and Mockito usage with MutKt's mutation test runner.

## TL;DR

- ✅ Use **manual init** in `@BeforeEach` — `@ExtendWith(MockKExtension::class)` and `@ExtendWith(MockitoExtension::class)` are **ignored**
- ✅ Regular mocks work (interfaces, open classes, subclass mock maker)
- ❌ Inline mocks (`mockkStatic`, `mockkConstructor`, Mockito 5+ inline) don't work — classloader conflict
- ❌ Robolectric + inline mocks — `SandboxClassLoader` shadows dispatcher classes

## Compatibility matrix

| Feature | Works? | Notes |
|---------|--------|-------|
| MockK regular mocks (`mockk<T>()`) | ✅ | Standard ByteBuddy subclass |
| MockK final-class inline | ❌ | Agent attach + bootstrap dispatcher conflict |
| MockK `mockkStatic()` | ❌ | Same agent issue + state leaks across mutations |
| MockK `@MockK` annotation | ⚠️ Manual | Call `MockKAnnotations.init(this)` in `@BeforeEach` |
| Mockito subclass mock maker | ✅ | Default before Mockito 5 |
| Mockito 5+ inline mock maker | ❌ | `MockMethodDispatcher` bootstrap conflict |
| Mockito `mockStatic()` | ❌ | Same inline-maker issue |
| Mockito `@Mock` annotation | ⚠️ Manual | Call `MockitoAnnotations.openMocks(this)` in `@BeforeEach` |
| Robolectric + mocks | ❌ | `SandboxClassLoader` shadows dispatcher classes |

## MockK patterns

### What works — interface/open class mocks

```kotlin
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

class UserServiceTest {
    @Before
    fun setUp() {
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

### What works — `@MockK` field annotation

```kotlin
class UserServiceTest {
    @MockK
    lateinit var prefs: SharedPreferences
    
    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { prefs.getString("user", null) } returns "alice"
    }
}
```

### What doesn't work

```kotlin
// ❌ Final class inline mock — requires Java agent
mockk<MyFinalClass>()

// ❌ Static mock — global state conflict
mockkStatic(KotlinXCoroutines::class)
mockkStatic("com.example.Foo")

// ❌ Constructor mock — same agent issue
mockkConstructor(MyClass::class)

// ❌ JUnit 5 extension ignored
@ExtendWith(MockKExtension::class)
class UserServiceTest { ... }
```

## Mockito patterns

### What works — regular subclass mocks

```kotlin
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations

class UserServiceTest {
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }
    
    @Test
    fun `mocks work for classes`() {
        val mockPrefs = mock<SharedPreferences>()
        `when`(mockPrefs.getString("user", null)).thenReturn("bob")
        assertEquals("bob", mockPrefs.getString("user", null))
    }
}
```

### What works — `@Mock` field annotation

```kotlin
class UserServiceTest {
    @Mock
    lateinit var prefs: SharedPreferences
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(prefs.getString("user", null)).thenReturn("bob")
    }
}
```

### What doesn't work

```kotlin
// ❌ Static mock — requires inline mock maker (Mockito 5+)
mockStatic(Files::class)

// ❌ Final class mock with inline maker enabled
mock(MyFinalClass::class.java)

// ❌ JUnit 5 extension ignored
@ExtendWith(MockitoExtension::class)
class UserServiceTest { ... }
```

If you need any unsupported pattern, run `./gradlew test` instead of `./gradlew mutationTest`.

## Why limitations exist

MutKt loads test classes in a custom `URLClassLoader` parented to `ClassLoader.getPlatformClassLoader()`. This isolates mutated bytecode from the engine's own dependencies but prevents Java instrumentation agents (used by inline mocking) from installing their dispatchers correctly.

MockK's inline mode and Mockito 5+ inline mock maker both rely on ByteBuddy attaching a `MockMethodDispatcher` via the Java instrumentation API. The custom classloader breaks the `MethodHandle` lookup chain during method interception, so the dispatcher is never called.

Robolectric compounds the issue with its own `SandboxClassLoader` that shadows the dispatcher classes entirely.

## Workarounds

| Need | Workaround |
|------|------------|
| Mock a final class | Use MockK's `mockk<T>()` with `relaxed = true` (works for some) or extract an interface |
| Mock static methods | Refactor to inject the dependency, or run via `./gradlew test` |
| Mock constructors | Refactor to factory pattern, or run via `./gradlew test` |
| Use JUnit 5 extensions | Refactor to manual init in `@BeforeEach` |

## Roadmap

The next major MutKt release (1.0) will switch from reflection-based to **JUnit Platform Launcher** based test execution. This API has first-class classloader + agent integration, which will unblock all inline mocking patterns and Robolectric integration.
