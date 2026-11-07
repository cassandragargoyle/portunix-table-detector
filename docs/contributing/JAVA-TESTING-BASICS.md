# Java Testing Basics

## Overview

This guide is designed for developers new to testing in Java. It covers the basics of writing tests using JUnit 5 and NetBeans IDE.

**For advanced topics** (mocking, integration testing, performance testing, etc.), see [JAVA-TESTING.md](JAVA-TESTING.md).

## Why Write Tests?

Testing is a fundamental practice in professional software development. Tests serve two critical purposes:

### 1. Protection Against Regression Bugs

When you modify code, tests ensure that existing functionality continues to work correctly. Without tests, changes to one part of the codebase can accidentally break functionality elsewhere - these are called **regression bugs**. Tests act as a safety net, catching these bugs before they reach production.

**Example scenario:**

- You refactor a file parsing method to improve performance
- Without tests, you might not notice that it now fails on empty files
- With tests, the test suite immediately alerts you to this regression

### 2. Documentation and Usage Examples

Tests demonstrate how code should be used. For other developers (and your future self), tests are executable documentation showing:

- What inputs are expected
- What outputs are produced
- How to handle edge cases
- How different components work together

**Reading a test is often faster than reading documentation**, because tests show actual working code examples.

## Testing Framework

Portunix Plugins uses **JUnit 5** (Jupiter) as the testing framework. You can see the dependencies configured in `pom.xml`:

```xml
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <version>5.10.1</version>
    <scope>test</scope>
</dependency>
```

## Creating Tests in NetBeans IDE

NetBeans provides excellent support for creating and running tests.

### Creating Test Classes

1. **Right-click on the source file** you want to test in the Projects panel
   - For example: `TextExtractorPlugin.java`

2. **Select `Tools → Create/Update Tests`** from the context menu

3. **Configure test generation dialog:**
   - Test Framework: JUnit 5 (should be default)
   - Test Class Name: NetBeans automatically suggests `<ClassName>Test`
   - Select methods you want to generate test stubs for
   - Click `OK`

4. **NetBeans creates the test class** in the correct location:
   - Source: `src/main/java/com/example/MyClass.java`
   - Test: `src/test/java/com/example/MyClassTest.java`

### Test File Structure

NetBeans generates a test skeleton like this:

```java
package org.cassandragargoyle.portunix.textextractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class TextExtractorPluginTest {

    private TextExtractorPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new TextExtractorPlugin();
    }

    @Test
    void testExtractTextFromFile() {
        // TODO: Write test implementation
        fail("Test not implemented yet");
    }
}
```

## Writing Your First Test

Let's write a real test for the `isFormatSupported` method:

```java
@Test
void testIsFormatSupported_PDF() {
    // Test that PDF format is supported
    boolean result = plugin.isFormatSupported("document.pdf");
    assertTrue(result, "PDF format should be supported");
}

@Test
void testIsFormatSupported_UnsupportedFormat() {
    // Test that unsupported format returns false
    boolean result = plugin.isFormatSupported("image.png");
    assertFalse(result, "PNG format should not be supported in Phase 1");
}

@Test
void testIsFormatSupported_NullInput() {
    // Test edge case: null input
    boolean result = plugin.isFormatSupported(null);
    assertFalse(result, "Null input should return false");
}
```

### Test Method Structure

A good test follows the **AAA pattern** (also known as **Given/When/Then** in BDD):

1. **Arrange** (Given) - Set up test data and preconditions
2. **Act** (When) - Execute the method being tested
3. **Assert** (Then) - Verify the result is correct

```java
@Test
void testGetFileExtension() {
    // Arrange - prepare test data
    String filePath = "/documents/report.pdf";

    // Act - call the method
    String extension = plugin.getFileExtension(filePath);

    // Assert - verify result
    assertEquals("pdf", extension, "Should extract 'pdf' extension");
}
```

## Running Tests in NetBeans

### Run All Tests

- Right-click on the project → `Test`
- Or press `Alt+F6`

### Run Single Test Class

- Right-click on test file → `Test File`
- Or press `Ctrl+F6` while editing the test

### Run Single Test Method

- Place cursor in test method → Right-click → `Run Focused Test Method`
- Or press `Ctrl+Shift+F6`

### View Test Results

- Test Results panel appears automatically
- Green checkmark ✓ = test passed
- Red X = test failed
- Click on failed test to see error details

## Common JUnit 5 Assertions

```java
// Equality
assertEquals(expected, actual, "Optional message");
assertNotEquals(unexpected, actual);

// Boolean conditions
assertTrue(condition, "Should be true");
assertFalse(condition, "Should be false");

// Null checks
assertNull(object);
assertNotNull(object);

// Exceptions
assertThrows(IOException.class, () -> {
    plugin.extractTextFromFile("nonexistent.pdf");
});

// Multiple assertions
assertAll("File info validation",
    () -> assertTrue(file.exists()),
    () -> assertTrue(file.canRead()),
    () -> assertEquals("pdf", getExtension(file))
);
```

**Alternative**: Advanced projects may use **AssertJ** library which provides fluent assertions like `assertThat(result).isTrue()`. See [JAVA-TESTING.md](JAVA-TESTING.md#mocking-with-mockito) for AssertJ examples.

## Best Practices

### 1. Test One Thing Per Test Method

❌ Bad:

```java
@Test
void testPlugin() {
    // Tests multiple things
    assertTrue(plugin.isFormatSupported("test.pdf"));
    assertEquals("1.0.0-SNAPSHOT", plugin.getVersion());
    assertNotNull(plugin.getFileInfo("test.pdf"));
}
```

✅ Good:

```java
@Test
void testIsFormatSupported_PDF() {
    assertTrue(plugin.isFormatSupported("test.pdf"));
}

@Test
void testGetVersion() {
    assertEquals("1.0.0-SNAPSHOT", plugin.getVersion());
}
```

### 2. Use Descriptive Test Names

Test names should describe what is being tested and the expected outcome:

```java
@Test
void testExtractTextFromFile_WhenFileDoesNotExist_ThrowsIOException() {
    // Clear from the name what scenario is being tested
}
```

**Naming Convention**: Use `test<MethodName>_<Scenario>` for basic tests. For more advanced naming patterns (like BDD-style `should_ExpectedBehavior_When_StateUnderTest`), see [JAVA-TESTING.md](JAVA-TESTING.md#test-naming-conventions).

### 3. Test Edge Cases

Don't just test the happy path:

```java
@Test
void testIsFormatSupported_EmptyString() {
    assertFalse(plugin.isFormatSupported(""));
}

@Test
void testIsFormatSupported_FileWithoutExtension() {
    assertFalse(plugin.isFormatSupported("README"));
}

@Test
void testIsFormatSupported_MultipleDotsInFilename() {
    assertTrue(plugin.isFormatSupported("report.draft.final.pdf"));
}
```

### 4. Use @BeforeEach for Common Setup

```java
private TextExtractorPlugin plugin;
private File testFile;

@BeforeEach
void setUp() {
    plugin = new TextExtractorPlugin();
    testFile = new File("src/test/resources/sample.pdf");
}
```

### 5. Clean Up Resources with @AfterEach

```java
@AfterEach
void tearDown() {
    // Clean up temporary files created during tests
    if (tempFile != null && tempFile.exists()) {
        tempFile.delete();
    }
}
```

## Test Resources

Place test files (sample documents, test data) in:

```text
src/test/resources/
```

Access them in tests:

```java
@Test
void testExtractTextFromPDF() throws IOException {
    String resourcePath = "src/test/resources/sample.pdf";
    String text = plugin.extractTextFromFile(resourcePath);
    assertNotNull(text);
    assertTrue(text.length() > 0);
}
```

## Running Tests from Command Line

Even though NetBeans is preferred, you can run tests via Maven:

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=TextExtractorPluginTest

# Run specific test method
mvn test -Dtest=TextExtractorPluginTest#testIsFormatSupported_PDF
```

## Tests and Project Build

### How Tests Affect Build Process

When you build a Maven project, tests are automatically executed as part of the build lifecycle:

```bash
# Full build (compile + test + package)
mvn package

# Or using clean before build
mvn clean package
```

**Important**: If any test fails, the build process will **stop** and the JAR file will **not be created**. This is a safety mechanism to prevent deploying broken code.

**Example of build failure due to test:**

```bash
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running org.cassandragargoyle.portunix.TextExtractorPluginTest
[ERROR] Tests run: 5, Failures: 1, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
```

### Skipping Tests During Build

Sometimes you need to build without running tests (e.g., for quick local testing). Maven provides parameters to skip tests:

#### Skip Test Execution (but compile test code)

```bash
mvn package -DskipTests
```

- Tests are **compiled** but **not executed**
- Faster than full build
- Test code is verified for compilation errors

#### Skip Tests Completely (don't even compile)

```bash
mvn package -Dmaven.test.skip=true
```

- Tests are **not compiled** and **not executed**
- Fastest option
- Use sparingly - only for emergency quick builds

#### Comparison Table

| Command | Compile Tests | Run Tests | Use Case |
| ------- | ------------- | --------- | -------- |
| `mvn package` | ✅ Yes | ✅ Yes | **Normal build** - always prefer this |
| `mvn package -DskipTests` | ✅ Yes | ❌ No | Quick local build, tests verified earlier |
| `mvn package -Dmaven.test.skip=true` | ❌ No | ❌ No | Emergency only, broken test code |

### Best Practices for Building

1. **Always run tests before committing**

```bash
mvn clean test
```

2. **Use full build for releases**

```bash
mvn clean package
```

Never skip tests for production builds!

3. **Skip tests only temporarily**

```bash
# Quick iteration during development
mvn package -DskipTests

# But verify tests pass before commit
mvn test
```

4. **NetBeans integration**

- NetBeans runs `mvn package` when you click "Clean and Build"
- Tests are always executed
- Check "Output" window for test results

### Common Build + Test Scenarios

**Scenario 1: Development iteration**

```bash
# 1. Make code changes
# 2. Quick build to check compilation
mvn package -DskipTests

# 3. Run tests separately when ready
mvn test

# 4. Full build before commit
mvn clean package
```

**Scenario 2: Continuous Integration (CI)**

```bash
# CI should NEVER skip tests
mvn clean verify
```

**Scenario 3: Broken test blocking development**

```bash
# Temporarily disable failing test with @Disabled
@Disabled("TODO: Fix after refactoring - Issue #123")
@Test
void testComplexFeature() {
    // ...
}

# Then run normal build
mvn package
```

**⚠️ Warning**: Never use `-DskipTests` to "solve" test failures. Fix the tests or temporarily disable them with `@Disabled` annotation.

## Next Steps

1. Start by writing tests for simple methods (getters, validation methods)
2. Practice the AAA pattern (Arrange, Act, Assert)
3. Read existing tests in the project to learn patterns
4. Always run tests before committing code
5. When you find a bug, write a test that reproduces it first, then fix it

## Advanced Topics

Once you're comfortable with the basics covered in this guide, explore advanced testing techniques:

- **Mocking with Mockito** - Isolate dependencies in tests
- **Integration Testing** - Test component interactions
- **Parameterized Tests** - Run same test with multiple inputs
- **Asynchronous Testing** - Test async/concurrent code
- **Performance Testing** - Measure and verify performance
- **Test Coverage** - Track code coverage with JaCoCo

See [JAVA-TESTING.md](JAVA-TESTING.md) for comprehensive coverage of these advanced topics.

## Further Reading

- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [JAVA-TESTING.md](JAVA-TESTING.md) - Advanced testing techniques and best practices
- [CODE-STYLE-JAVA.md](CODE-STYLE-JAVA.md) - Project coding standards
- [JAVA-DEVELOPMENT-ENVIRONMENT.md](JAVA-DEVELOPMENT-ENVIRONMENT.md) - NetBeans setup
