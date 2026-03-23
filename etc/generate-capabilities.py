#!/usr/bin/env python3
"""
Generate CAPABILITIES.md from CAPABILITIES.yaml

Usage:
    python etc/generate-capabilities.py
    # or
    ./etc/generate-capabilities.sh

IMPORTANT: CAPABILITIES.yaml is the source of truth.
           CAPABILITIES.md is generated - do not edit directly.
"""

import yaml
from pathlib import Path
from collections import defaultdict
from datetime import date

# v2 status symbols (shown after v1 indicator)
V2_STATUS_SYMBOLS = {
    'implemented': '✓',
    'in_progress': '~',
    'planned': ' ',
    'future': '-',
}

V2_STATUS_LABELS = {
    'implemented': 'Implemented in v2',
    'in_progress': 'In progress',
    'planned': 'Planned',
    'future': 'Future / Wish list',
}

LICENSE_SYMBOLS = {
    'oss': '',
    'commercial': '$',
}


def load_capabilities(yaml_path: Path) -> dict:
    """Load and parse the CAPABILITIES.yaml file."""
    with open(yaml_path, 'r') as f:
        return yaml.safe_load(f)


def count_by_status(items: list, counts: dict) -> None:
    """Recursively count items by v1/v2 status combinations."""
    for item in items:
        v1 = item.get('v1', False)
        # Support both old 'status' field and new 'v2' field for backwards compatibility
        v2 = item.get('v2', item.get('status', 'future'))
        # Map old 'existing' status to v1=True, v2=planned
        if v2 == 'existing':
            v1 = True
            v2 = 'planned'

        if v1 and v2 == 'implemented':
            counts['v1_implemented'] += 1
        elif v1 and v2 == 'in_progress':
            counts['v1_in_progress'] += 1
        elif v1:
            counts['v1_pending'] += 1
        elif v2 == 'implemented':
            counts['new_implemented'] += 1
        elif v2 == 'in_progress':
            counts['new_in_progress'] += 1
        elif v2 == 'planned':
            counts['planned'] += 1
        else:
            counts['future'] += 1

        if 'items' in item:
            count_by_status(item['items'], counts)


def render_item(item: dict, level: int = 0) -> str:
    """Render a single capability item as markdown."""
    indent = '  ' * level
    name = item['name']
    license_type = item.get('license', 'oss')

    # Get v1 and v2 status
    v1 = item.get('v1', False)
    # Support both old 'status' field and new 'v2' field
    v2 = item.get('v2', item.get('status', 'future'))

    # Map old 'existing' status to v1=True, v2=planned
    if v2 == 'existing':
        v1 = True
        v2 = 'planned'

    # Build status indicator: [1.x ✓] or [1.x ~] or [1.x  ] or [v2] or [~] or [ ] or [-]
    v2_sym = V2_STATUS_SYMBOLS.get(v2, '-')
    if v1:
        status_str = f"1.x {v2_sym}"
    else:
        # New capability (not in v1)
        if v2 == 'implemented':
            status_str = "v2"
        elif v2 == 'in_progress':
            status_str = "~"
        elif v2 == 'planned':
            status_str = " "
        else:
            status_str = "-"

    license_sym = LICENSE_SYMBOLS.get(license_type, '')

    # Format: - [status] Name (license)
    if license_sym:
        line = f"{indent}- `[{status_str}]` {name} `{license_sym}`"
    else:
        line = f"{indent}- `[{status_str}]` {name}"

    lines = [line]

    # Render sub-items
    if 'items' in item:
        for sub_item in item['items']:
            lines.append(render_item(sub_item, level + 1))

    return '\n'.join(lines)


def render_category(category: dict) -> str:
    """Render a category as markdown."""
    lines = []
    lines.append(f"### {category['name']}")
    if 'description' in category:
        lines.append(f"\n{category['description']}\n")
    else:
        lines.append("")

    for item in category.get('items', []):
        lines.append(render_item(item))
        lines.append("")  # Blank line between top-level items

    return '\n'.join(lines)


def generate_markdown(data: dict) -> str:
    """Generate the full CAPABILITIES.md content."""
    lines = []

    # Header
    lines.append("# Karate v2 Capabilities")
    lines.append("")
    lines.append("Complete taxonomy of Karate capabilities - current, in-progress, and planned.")
    lines.append("")
    lines.append(f"> **Generated:** {date.today().isoformat()} from `CAPABILITIES.yaml`")
    lines.append(">")
    lines.append("> See also: [Design Principles](./PRINCIPLES.md) | [Roadmap](./ROADMAP.md)")
    lines.append("")

    # Legend
    lines.append("## Legend")
    lines.append("")
    lines.append("| Symbol | Meaning |")
    lines.append("|--------|---------|")
    lines.append("| `[1.x ✓]` | Was in v1, now implemented in v2 |")
    lines.append("| `[1.x ~]` | Was in v1, in progress for v2 |")
    lines.append("| `[1.x  ]` | Was in v1, not yet ported to v2 |")
    lines.append("| `[v2]` | New in v2 (not in v1), implemented |")
    lines.append("| `[~]` | New, in progress |")
    lines.append("| `[ ]` | Planned |")
    lines.append("| `[-]` | Future / Wish list |")
    lines.append("| `$` | Commercial / Enterprise |")
    lines.append("")

    # Summary statistics
    counts = defaultdict(int)
    for category in data['categories']:
        count_by_status(category.get('items', []), counts)

    total = sum(counts.values())
    lines.append("## Summary")
    lines.append("")
    lines.append(f"**Total capabilities: {total}**")
    lines.append("")
    lines.append("| Category | Count |")
    lines.append("|----------|-------|")
    if counts['v1_implemented'] > 0:
        lines.append(f"| v1 features ported to v2 | {counts['v1_implemented']} |")
    if counts['v1_in_progress'] > 0:
        lines.append(f"| v1 features in progress | {counts['v1_in_progress']} |")
    if counts['v1_pending'] > 0:
        lines.append(f"| v1 features pending | {counts['v1_pending']} |")
    if counts['new_implemented'] > 0:
        lines.append(f"| New in v2 (implemented) | {counts['new_implemented']} |")
    if counts['new_in_progress'] > 0:
        lines.append(f"| New in v2 (in progress) | {counts['new_in_progress']} |")
    if counts['planned'] > 0:
        lines.append(f"| Planned | {counts['planned']} |")
    if counts['future'] > 0:
        lines.append(f"| Future / Wish list | {counts['future']} |")
    lines.append("")

    # Table of contents
    lines.append("## Categories")
    lines.append("")
    for category in data['categories']:
        anchor = category['name'].lower().replace(' ', '-').replace('&', '').replace('/', '-')
        lines.append(f"- [{category['name']}](#{anchor})")
    lines.append("")
    lines.append("---")
    lines.append("")

    # Categories
    for category in data['categories']:
        lines.append(render_category(category))
        lines.append("---")
        lines.append("")

    return '\n'.join(lines)


def main():
    """Main entry point."""
    repo_root = Path(__file__).parent.parent
    docs_path = repo_root / 'docs'
    yaml_path = docs_path / 'CAPABILITIES.yaml'
    md_path = docs_path / 'CAPABILITIES.md'

    if not yaml_path.exists():
        print(f"Error: {yaml_path} not found")
        return 1

    print(f"Loading {yaml_path}...")
    data = load_capabilities(yaml_path)

    print("Generating markdown...")
    markdown = generate_markdown(data)

    print(f"Writing {md_path}...")
    with open(md_path, 'w') as f:
        f.write(markdown)

    print("Done!")
    return 0


if __name__ == '__main__':
    exit(main())
