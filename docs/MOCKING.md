# MutKt Mocking Reference

Quick reference for MockK and Mockito usage with MutKt's mutation test runner.

## TL;DR

- ✅ Use **manual init** in `@BeforeEach` (JUnit 5) or `@Before` (JUnit 4 / Robolectric) for annotations
- ✅ Regular mocks work (interfaces, open classes, subclass mock maker)
- ✅ Inline mocks work (`mockkStatic`, `mockkConstructor`, Mockito 5+ inline) — JUnit Platform Launcher has first-class classloader + agent integration
- ✅ Robolectric + inline mocks work
- ℹ️ Inline-mocking Java agents require the test runtime classpath to include the agent jars plus a matching `-javaagent` JVM argument; the Gradle plugin inherits both from the regular `test` task, so copy them through if you override `classpath` or `jvmArgs` on `mutationTest`.

## Compatibility matrix

| Feature | Works? | Notes |
|---------|--------|-------|
| MockK regular mocks (`mockk<T>()`) | ✅ | Standard ByteBuddy subclass |
| MockK final-class inline | ✅ | Java agent attach works under JUnit Platform Launcher |
| MockK `mockkStatic()` | ✅ | Works via JUnit Platform Launcher |
| MockK `@MockK` annotation | ⚠️ Manual | Call `MockKAnnotations.init(this)` in `@BeforeEach` / `@Before` |
| Mockito subclass mock maker | ✅ | Default before Mockito 5 |
| Mockito 5+ inline mock maker | ✅ | `MockMethodDispatcher` works under JUnit Platform Launcher |
| Mockito `mockStatic()` | ✅ | Works via JUnit Platform Launcher |
| Mockito `@Mock` annotation | ⚠️ Manual | Call `MockitoAnnotations.openMocks(this)` in `@BeforeEach` / `@Before` |
| Robolectric + mocks | ✅ | Works under JUnit Platform Launcher |

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

### What requires manual init (annotation patterns)

```kotlin
// ✅ Final class inline mock — works under JUnit Platform Launcher
mockk<MyFinalClass>()

// ✅ Static mock — works under JUnit Platform Launcher
mockkStatic(KotlinXCoroutines::class)
mockkStatic("com.example.Foo")

// ✅ Constructor mock — works under JUnit Platform Launcher
mockkConstructor(MyClass::class)

// ℹ️ JUnit 5 extensions are executed; just remember to call init manually if you don't use the extension
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

### What requires manual init (annotation patterns)

```kotlin
// ✅ Static mock — works under JUnit Platform Launcher
mockStatic(Files::class)

// ✅ Final class mock with inline maker enabled
mock(MyFinalClass::class.java)

// ℹ️ JUnit 5 extensions are executed; just remember to call init manually if you don't use the extension
@ExtendWith(MockitoExtension::class)
class UserServiceTest { ... }
```

## Workarounds

| Need | Workaround |
|------|------------|
| Mock a final class | Use MockK's `mockk<T>()` with `relaxed = true` (works for some) or extract an interface |
| Mock static methods | Refactor to inject the dependency, or run via `./gradlew test` |
| Mock constructors | Refactor to factory pattern, or run via `./gradlew test` |
| Use JUnit 5 extensions | Refactor to manual init in `@BeforeEach` |
