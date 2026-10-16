#!/usr/bin/env python3
"""Generate private local credentials without displaying or overwriting them."""
from pathlib import Path
import os
import secrets


def initialize(root: Path) -> Path:
    destination = root / '.env'
    template = (root / '.env.example').read_text(encoding='utf-8')
    values = {
        'JWT_SECRET': secrets.token_hex(32),
        'BOOTSTRAP_ADMIN_PASSWORD': secrets.token_urlsafe(24),
    }
    lines = []
    for line in template.splitlines():
        key, separator, _ = line.partition('=')
        lines.append(f'{key}={values[key]}' if separator and key in values else line)
    fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as file:
        file.write('\n'.join(lines) + '\n')
    return destination


if __name__ == '__main__':
    try:
        initialize(Path(__file__).resolve().parents[1])
    except FileExistsError:
        raise SystemExit('.env already exists; it was not changed.')
    print('Created .env. Read the admin credentials locally; never upload this file.')
