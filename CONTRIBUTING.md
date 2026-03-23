# Contributing to Karate v2

Thank you for your interest in contributing to Karate v2! This document provides guidelines for contributing to the project.

## Getting Started

### Prerequisites

- Java 21 or higher
- Maven 3.8+
- Git

### Building the Project

```bash
git clone https://github.com/karatelabs/karate-v2.git
cd karate-v2
mvn clean install
```

### Running Tests

```bash
mvn test
```

## How to Contribute

### Reporting Issues

- Use GitHub Issues to report bugs or request features
- Search existing issues before creating a new one
- Include reproduction steps for bugs
- Be specific about expected vs actual behavior

### Submitting Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Make your changes
4. Write or update tests as needed
5. Ensure all tests pass (`mvn test`)
6. Commit with clear, descriptive messages
7. Push to your fork
8. Open a Pull Request against `main`

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
karate-v2/
├── karate-js/      # JavaScript engine and Gherkin parser
├── karate-core/    # Core framework (HTTP, templating, matching)
├── PRINCIPLES.md   # Design principles
├── ROADMAP.md      # Development roadmap
└── pom.xml         # Parent Maven configuration
```

## Areas for Contribution

See [ROADMAP.md](./ROADMAP.md) for current priorities and open tasks. Good areas for new contributors:

- Documentation improvements
- Test coverage expansion
- Bug fixes
- Performance optimizations

### Third-Party Integration

If you're using karate-js in your own project and find opportunities to improve decoupling or reusability, those contributions are especially welcome.

## Communication

- **Issues** - For bugs, features, and technical discussions
- **Pull Requests** - For code contributions
- **Discussions** - For general questions and ideas

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

## Code of Conduct

Be respectful and constructive in all interactions. We're building something together.

---

Thank you for contributing to Karate v2!
