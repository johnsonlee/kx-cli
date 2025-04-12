## Overview

`exec` is a **flexible and extensible command-line framework**, designed to help developers build and manage their own CLI tools under a unified entry point.

In addition to providing a solid foundation for custom commands, `exec` also comes with several **built-in subcommands** for common automation and data processing tasks, such as **web data fetching, cookie management, and JSON-to-CSV conversion**.

## Who is it for?

- Developers who need a **lightweight, Kotlin-based CLI framework** to organize internal tools.
- Teams looking for a **unified way to expose multiple automation tasks** under a single entry point.
- Data engineers and automation specialists who need **ready-to-use commands** for fetching, transforming, and processing data.
- Anyone who wants a **flexible, extensible CLI framework**.

## Built-in Subcommands

| Subcommand       | Description                                 |
|------------------|---------------------------------------------|
| `fetch`          | Fetch webpage content and save it to a file |
| `html2csv`       | Convert HTML into CSV files                 |
| `json2csv`       | Convert JSON into CSV files                 |
| `save-cookies`   | Save browser cookies to a file for reuse    |

## Quick Start

### 1. Download the All-in-One JAR

The latest **all-in-one executable JAR** can be downloaded from [Maven Central](https://search.maven.org/artifact/io.johnsonlee/exec). Save it as `exec-all.jar`.

### 2. Run

With **Java 17+** installed, you can run:

```sh
java -jar exec-all.jar exec <subcommand> [options]
```

### 3. Example Usage

#### Fetch a Webpage

```sh
java -jar exec-all.jar exec fetch --input https://example.com --output page.html
```

#### Save Browser Cookies

```sh
java -jar exec-all.jar exec save-cookies --input https://example.com/login --output cookies.json
```

#### Convert JSON to CSV

```sh
java -jar exec-all.jar exec json2csv --input data.json --output data.csv
```

## Extending `exec`

`exec` is designed to be **easily extensible**. You can define your own subcommands by implementing a simple Kotlin interface, making it ideal for:

- Custom automation pipelines
- Internal developer tools
- Workflow automation across teams

The framework handles **command parsing, argument validation, and command dispatching**, so you can focus entirely on your business logic.

## Documentation & Help

To see available subcommands:

```sh
java -jar exec-all.jar exec --help
```

To see options for a specific subcommand:

```sh
java -jar exec-all.jar exec <subcommand> --help
```

For example:

```sh
java -jar exec-all.jar exec fetch --help
```

## System Requirements

- Java 17 or later
- Works on Windows, macOS, and Linux

## Highlights

- ✅ **Extensible CLI Framework**: Clean structure for organizing your commands.
- ✅ **Batteries Included**: Useful subcommands provided out of the box.
- ✅ **Kotlin-Powered**: Modern and developer-friendly.
- ✅ **Production-Ready**: Designed for real-world data processing and automation tasks.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
