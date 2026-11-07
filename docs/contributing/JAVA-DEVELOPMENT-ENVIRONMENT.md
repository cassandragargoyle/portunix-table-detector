# Java Development Environment

## Preferred IDE

The preferred Integrated Development Environment (IDE) for Java plugin development in Portunix Plugins is **Apache NetBeans**.

### Why NetBeans?

- **Maven Integration**: Excellent built-in support for Maven projects
- **Cross-Platform**: Available on Windows, Linux, and macOS
- **Lightweight**: Efficient resource usage compared to other Java IDEs
- **Free and Open Source**: No licensing costs or restrictions
- **Java Standards**: Strong adherence to Java standards and best practices

### Installation

Download NetBeans from the official website:

- <https://netbeans.apache.org/download/>

Select the version that includes Java SE support.

### Project Setup

1. Open NetBeans
2. Select `File → Open Project`
3. Navigate to the Java plugin directory (e.g., `plugins/java/example-plugin`)
4. NetBeans will automatically recognize the Maven `pom.xml` and configure the project

### Building in NetBeans

- **Clean and Build**: Right-click project → `Clean and Build`
- **Build**: Right-click project → `Build`
- **Run Tests**: Right-click project → `Test`

The compiled `.jar` file will be available in the `target/` directory.

### Alternative IDEs

While NetBeans is preferred, you can use other IDEs if needed:

- **IntelliJ IDEA**: Good Maven support, heavier resource usage
- **Eclipse**: Requires Maven plugin (m2e), additional configuration needed
- **VS Code**: Requires Java Extension Pack, suitable for lightweight editing

However, when contributing to the project or reporting issues, please ensure your code builds correctly in NetBeans.

## Command Line Build

You can always build without an IDE using Maven:

```bash
mvn clean package
```

This ensures consistency across all development environments.
