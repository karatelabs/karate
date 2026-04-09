# Contributing to Karate

Thank you for your interest in contributing to this project!

## Before You Start

- If a [relevant issue](https://github.com/karatelabs/karate/issues) already exists, discuss your approach within that issue before starting work
- If no relevant issue exists, [open a new issue](https://github.com/karatelabs/karate/issues/new/choose) to start a discussion
- Please proceed with a Pull Request only **after** the project maintainers are okay with your approach

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.8+
- Git

### Building the Project

```bash
git clone https://github.com/karatelabs/karate.git
cd karate
mvn clean install
```

### Running Tests

```bash
mvn test
```

## Submitting Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Make your changes
4. Write or update tests as needed
5. Ensure all tests pass (`mvn test`)
6. Commit with clear, descriptive messages
7. Push to your fork
8. Open a Pull Request against the `main` branch

### Pull Request Guidelines

- Keep changes focused and atomic
- Update documentation if needed
- Add tests for new functionality
- Follow existing code style
- Reference related issues in the PR description

## Code Style

- Follow standard Java conventions
- Use meaningful variable and method names
- Keep methods focused and reasonably sized
- Add comments for complex logic (but prefer self-documenting code)

## Project Structure

```
karate/
├── karate-js/      # JavaScript engine and Gherkin parser
├── karate-core/    # Core framework (HTTP, templating, matching)
├── docs/           # Design principles and roadmap
└── pom.xml         # Parent Maven configuration
```

## Areas for Contribution

See [TODOS.md](docs/TODOS.md) for current priorities and open tasks. Good areas for new contributors:

- Documentation improvements
- Test coverage expansion
- Bug fixes
- Performance optimizations

If you're using karate-js in your own project and find opportunities to improve decoupling or reusability, those contributions are especially welcome.

## Communication

- **[Issues](https://github.com/karatelabs/karate/issues)** - For bugs and feature requests
- **[Pull Requests](https://github.com/karatelabs/karate/pulls)** - For code contributions
- **[Discussions](https://github.com/karatelabs/karate/discussions)** - For general questions and ideas

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

## Code of Conduct

Please read our [Code of Conduct](.github/CODE_OF_CONDUCT.md). Be respectful and constructive in all interactions.
