# jadX HzhuAi Plugin

hzhuAi is a plugin for the [JadX](https://github.com/skylot/jadx) decompiler that aims to integrate with Large Language Models (LLMs) to provide code analysis directly in the JAdX. This plugin allows you to analyze Java code for functionality, security issues, and notable patterns using LLMs like GPT-4, Claude or any custom local LLMs.

## Features

- **LLM Integration**: Supports Ai.
- **Code Analysis**: Analyze Java code for functionality, security issues, and notable patterns.
- **Custom Prompts**: Configure custom prompts for analysis.
- **GUI Integration**: Integrates with JadX GUI for easy access.

## Screenshots

## Installation

### Prerequisites

- JadX (CLI or GUI)
- Java Development Kit (JDK) 11 or higher

### Build locally

1. **Clone the repository**:
    ```sh
    git clone https://github.com/dooker520/Jadx-hzhuAi-Plugin.git
    cd Jadx-hzhuAi-Plugin
    ```

2. **Build the plugin**:
    ```sh
    ./gradlew clean build
    ```

3. **Install the plugin**:
    - For `jadx-cli`:
        ```sh
        jadx plugins --install-jar build/libs/jadx-hzhuAi-plugin-dev.jar
        ```
    - For `jadx-gui`:
        - Open JadX GUI.
        - Go to **Plugins** > **Install plugin**.
        - Select the `jadx-hzhuAi-plugin-dev.jar` file from the `build/libs` directory.

## Usage

### JadX GUI

1. **Open JadX GUI**.
2. **Load a Java archive (JAR) file**.
3. **Right-click on a class or method** and select **Analyze with hzhuAi Plugin**.



## unresolved issues

1. **The temporary variable list cannot be obtained through the function because I don’t know how to modify the variable name.**

## Contributing

Contributions are welcome! Please open an issue or submit a pull request on GitHub.
