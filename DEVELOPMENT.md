# Development

## Setup

Setup Java development environment with OpenJDK 11 and Maven for MacOS:

```bash
brew install openjdk@11
brew install maven
```

### Switching between Java versions (Optional)

If you need to work with multiple Java versions:

```bash
# Install multiple JDK versions
brew install openjdk@11
brew install openjdk@17

# Option 1: Using jenv (recommended for managing multiple versions)
brew install jenv
jenv add /opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
jenv add /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
jenv global 11  # Set default to Java 11
jenv versions   # List all installed versions

# Option 2: Manual JAVA_HOME switching
export JAVA_HOME=/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
# Or for Java 17:
# export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

If you are using `jenv`, you may need to add the following to your `.zshrc` or `.bashrc` file:

```bash
export PATH="$HOME/.jenv/bin:$PATH"
eval "$(jenv init -)"
```

## Building

In the root directory, run:

```bash
mvn clean install
# or
mvn clean install -Dgpg.skip=true
```

## Running Examples

For the standalone example, in the root directory, run:

```bash
cd examples/standalone-example
mvn clean compile exec:java
```

For the spring-boot example, in the root directory, run:

```bash
cd examples/spring-boot-example
mvn spring-boot:run
```

## Formatting

To format the code, in the root directory run:

```bash
curl -L https://github.com/google/google-java-format/releases/download/v1.15.0/google-java-format-1.15.0-all-deps.jar -o google-java-format.jar
java -jar google-java-format.jar --replace src/**/*.java examples/**/*.java
```
