## Overview

`kx` is a **flexible and extensible command-line framework**, designed to help developers build and manage their own CLI tools under a unified entry point.

In addition to providing a solid foundation for custom commands, `kx` also comes with several **built-in commands** for common automation and data processing tasks, such as **web data fetching, cookie management, and JSON-to-CSV conversion**.

## Who is it for?

- Developers who need a **lightweight, Kotlin-based CLI framework** to organize internal tools.
- Teams looking for a **unified way to expose multiple automation tasks** under a single entry point.
- Data engineers and automation specialists who need **ready-to-use commands** for fetching, transforming, and processing data.
- Anyone who wants a **flexible, extensible CLI framework**.

## Built-in Subcommands

| Subcommand | Description                                 |
|------------|---------------------------------------------|
| `fetch`    | Fetch webpage content and save it to a file |
| `browser`  | Open browser for automation                 |
| `dom2json` | Convert XML/HTML DOM to JSON                |
| `json2csv` | Convert JSON into CSV files                 |

## Quick Start

### 1. Download

The latest self-contained executables can be downloaded from [Latest Release](https://github.com/johnsonlee/kx/releases/latest). You can choose to download the all-in-one executable or the individual command executable.

### 2. Run

With **Java 17+** installed, you can run:

```sh
./kx <command> <parameters> [options]
```

### 3. Example Usage

#### Fetch a Webpage

```sh
./kx fetch https://example.com --output page.html
```

#### Save Browser Cookies

```sh
./kx browser https://example.com/login save-cookie --cookie-file cookies.json
```

#### Convert JSON to CSV

```sh
./kx json2csv data.json -r '$.data[*]' --output data.csv
```

#### Convert XML/HTML DOM to JSON and Query with JSONPath

```sh
./kx dom2json --json-path '$..a[?(@.@href =~ /.*\.xml$/ && @.#text =~ /.*\.xml$/)].@abs:href https://example.com/index.html'
```

## Extending `kx`

`kx` is designed to be **easily extensible**. You can define your own commands by implementing a simple Kotlin interface, making it ideal for:

- Custom automation pipelines
- Internal developer tools
- Workflow automation across teams

The framework handles **command parsing, argument validation, and command dispatching**, so you can focus entirely on your business logic.

## Documentation & Help

To see available commands:

```sh
./kx --help
```

To see options for a specific command:

```sh
./kx <command> --help
```

For example:

```sh
./kx fetch --help
```

## System Requirements

- Java 17 or later
- Works on Windows, macOS, and Linux

## Highlights

- ✅ **Extensible CLI Framework**: Clean structure for organizing your commands.
- ✅ **Batteries Included**: Useful commands provided out of the box.
- ✅ **Kotlin-Powered**: Modern and developer-friendly.
- ✅ **Production-Ready**: Designed for real-world data processing and automation tasks.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
