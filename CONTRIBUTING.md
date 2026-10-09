# Contributing

Contributions are welcome. Useful contributions include correcting an error,
improving an explanation, adding a working example, or documenting a system
design trade-off.

## Content

- Fix factual errors, broken links, and unclear explanations.
- Add examples that make a concept easier to understand.
- Include the relevant assumptions and trade-offs in system designs.
- Keep new sections focused on the topic.

## Code

- Add implementations in an existing language where possible.
- Make examples runnable and include setup instructions when needed.
- Add tests for behavior that is not obvious from the code.
- Keep dependencies and configuration limited to what the example requires.

## Diagrams

- Use diagrams to clarify structure, data flow, or a sequence of operations.
- Keep labels readable and consistent with the surrounding content.
- Prefer Mermaid for simple diagrams and PlantUML for detailed class or
  sequence diagrams.

## Before Opening a Pull Request

1. Make the change in a fork or feature branch.
2. Check links and examples affected by the change.
3. Run the relevant tests or commands.
4. Describe what changed and how it was verified.

For documentation changes, build the site locally:

```bash
make site-build
```

For implementation changes, follow the instructions in the relevant directory.

## What to Avoid

- Copying material without permission or attribution.
- Adding incomplete examples presented as working code.
- Introducing broad rewrites unrelated to the change.
- Adding decorative content that does not improve understanding.

If an issue is not clear, open a discussion before starting a large change.
