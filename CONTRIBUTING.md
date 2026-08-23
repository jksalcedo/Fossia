# Contributing to LibreFind

First off, thank you for considering contributing to LibreFind! It's people like you that make LibreFind a great tool to discover and replace proprietary apps with FOSS alternatives.

Following these guidelines helps to communicate that you respect the time of the developers managing and developing this open source project. In return, they should reciprocate that respect in addressing your issue, assessing changes, and helping you finalize your pull requests.

> [!TIP]
> If possible, we kindly encourage opening issues and pull requests on our [Codeberg repository](https://codeberg.org/jksalcedo/librefind).

## AI Usage Guidelines

With the increasing use of AI coding assistants, we welcome contributions made with the help of AI, but we have strict guidelines to ensure code quality and maintainability:

1. **Mandatory Hand-Review**: Do not blindly copy-paste AI-generated code. You are strictly responsible for every line of code in your pull request. You must read, understand, and manually review all AI-generated code before submitting.
2. **Architectural Adherence**: AI tools often suggest generic solutions. You must ensure that any code generated adheres to LibreFind's specific architecture (Jetpack Compose, MVVM, Koin DI, Supabase, and Room). If the AI deviates, correct it before submitting.
3. **Responsibility**: You are fully responsible for the functionality, security, and performance of your contribution, regardless of whether an AI assisted in writing it.

## Ground Rules

- **Privacy First**: User data is stored locally. Never introduce analytics, tracking, or network calls that leak user data.
- **Testing**: Ensure that your changes do not break existing functionality. Let users manually test the changes and do not run automated test commands like `gradlew test` unless absolutely necessary in your local environment, as the maintainer prefers manual testing.
- **Commit Messages**: Write clear and descriptive commit messages.

## Reporting Bugs

Before creating bug reports, please check the existing issues as you might find out that you don't need to create one. When you are creating a bug report, please include as many details as possible:

- **Use a clear and descriptive title** for the issue to identify the problem.
- **Describe the exact steps which reproduce the problem** in as many details as possible.
- **Describe the behavior you observed after following the steps** and point out what exactly is the problem with that behavior.
- **Explain which behavior you expected to see instead and why.**

## Feature Suggestions

Feature requests are welcome. But take a moment to find out whether your idea fits with the scope and aims of the project. It's up to you to make a strong case to convince the project's developers of the merits of this feature. Please provide as much detail and context as possible.

## Pull Requests

1. **Branching**: Create a separate branch for every feature or fix (e.g., `feature/add-search-bar` or `fix/note-saving`). Do not push directly to `main`.
2. **Review**: All pull requests must be reviewed by a maintainer before being merged.

## Community

Be welcoming, inclusive, and respectful to all community members. We want to foster a positive environment where everyone feels comfortable contributing.
