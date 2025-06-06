# SafePath: Fluent and Null-Safe Object Graph Navigation for Java

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/pourquoipas/SafePath)
[![Maven Central](https://img.shields.io/maven-central/v/net.gnius/safepath.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:net.gnius%20AND%20a:safepath)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**SafePath** is a zero-dependency Java utility that allows you to navigate complex object graphs using a simple, powerful path expression. It eliminates verbose `if (obj != null && obj.get...() != null)` checks and prevents `NullPointerException`s with an elegant, fluent syntax inspired by modern language features.

---

## Features

* **Null-Safe Navigation (`?.`)**: Traverse object paths without fear of `NullPointerException`. If any part of the chain is `null`, the evaluation gracefully stops and returns an empty `Optional`.
* **Unsafe Navigation (`.`)**: For paths where you expect non-null objects, the standard dot operator will throw a `NullPointerException` with a descriptive message if an intermediate value is `null`.
* **Null-Coalescing Operator (`??`)**: Provide a default value on-the-fly if a navigation path results in `null`.
* **Positional Parameters (`#n`)**: Call methods with arguments by referencing a zero-indexed parameter list.
* **Unified Access**: Access both public fields and methods using the same syntax.
* **Intelligent Exception Handling**: Differentiates between a `null` value in a path (a predictable outcome) and an actual exception thrown by an invoked method (an exceptional outcome), which is re-thrown for proper handling.
* **Zero Dependencies**: A lightweight utility written in pure Java 8, using only standard reflection APIs.

---

## Installation

### Maven

SafePath is hosted on GitHub Packages. To add it to your project, first add the repository to your `pom.xml` or `settings.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <name>GitHub pourquoipas Apache Maven Packages</name>
        <url>[https://maven.pkg.github.com/pourquoipas/SafePath](https://maven.pkg.github.com/pourquoipas/SafePath)</url>
    </repository>
</repositories>
```

Then, add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>net.gnius</groupId>
    <artifactId>safepath</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Note:** You will need to [authenticate to GitHub Packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-to-github-packages) by adding a server configuration to your `~/.m2/settings.xml` file.

---

## Usage Guide

The core of the library is the static method `SafePath.invoke()`.

```java
public static <T> Optional<T> invoke(Object root, String path, Object... params)
```

* `root`: The starting object of your navigation.
* `path`: The expression string to evaluate.
* `params`: A varargs array of objects used as parameters (`#0`, `#1`, etc.) in the path.

### 1. Basic Safe Navigation

Avoid `NullPointerException` when an intermediate object might be `null`.

```java
// Given a user where getAddress() might return null.
User userWithNullAddress = new User(null);

// Path: "?.getAddress()?.getStreet()"
// The ?. operator ensures that if getAddress() returns null,
// the chain stops and returns Optional.empty() instead of throwing an NPE.
Optional<String> street = SafePath.invoke(userWithNullAddress, "?.getAddress()?.getStreet()");

assertFalse(street.isPresent()); // The result is an empty Optional, as expected.
```

### 2. Using a Default Value (`??`)

Provide a fallback value if the navigation results in `null`.

```java
User userWithNullAddress = new User(null);
String defaultStreet = "Unknown Street";

// Path: "?.getAddress()?.getStreet() ?? #0"
// If the path before '??' is null, the value of parameter #0 is used.
Optional<String> street = SafePath.invoke(
    userWithNullAddress,
    "?.getAddress()?.getStreet() ?? #0",
    defaultStreet // This corresponds to #0
);

assertEquals("Unknown Street", street.get());
```

### 3. Calling Methods with Parameters (`#n`)

Invoke methods and provide arguments from the parameter list.

```java
User user = new User(new Address("123 Main St"));

// Path: "?.getAddress()?.formatAddress(#0, #1)"
Optional<String> formattedAddress = SafePath.invoke(
    user,
    "?.getAddress()?.formatAddress(#0, #1)",
    "Springfield", // Parameter #0
    "12345"        // Parameter #1
);

assertEquals("123 Main St, Springfield, 12345", formattedAddress.get());
```

Parameters can also be reused:

```java
// Path: "?.getAddress()?.formatAddress(#0, #0)"
Optional<String> result = SafePath.invoke(user, "?.getAddress().formatAddress(#0, #0)", "TestCity");
assertEquals("123 Main St, TestCity, TestCity", result.get());
```

### 4. Mixing Safe (`?.`) and Unsafe (`.`) Navigation

Use the unsafe dot operator when you want to enforce that a value must not be `null`.

```java
User userWithNullAddress = new User(null);

// Path: "?.getAddress().getStreet()"
// ?.getAddress() evaluates to null.
// .getStreet() is then called on null, which triggers an NPE.
assertThrows(NullPointerException.class, () -> {
    SafePath.invoke(userWithNullAddress, "?.getAddress().getStreet()");
});
```

The exception message is descriptive: `Cannot invoke 'getStreet' because 'getAddress' is null`.

### 5. Exception Handling

If an invoked method throws an exception itself, `SafePath` re-throws the original `RuntimeException` or wraps a checked exception in a `SafePathException`.

```java
User user = new User(new Address("123 Main St"));

// The User.throwError() method internally throws an IllegalStateException.
// SafePath re-throws this specific exception.
assertThrows(IllegalStateException.class, () -> {
    SafePath.invoke(user, "?.throwError()");
});
```

---

## Building From Source

To build the library locally, clone the repository and run the Maven `install` command:

```sh
git clone [https://github.com/pourquoipas/SafePath.git](https://github.com/pourquoipas/SafePath.git)
cd SafePath
mvn clean install
```

---

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue for bugs, feature requests, or improvements.

---

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
