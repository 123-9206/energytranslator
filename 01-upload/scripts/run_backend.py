#!/usr/bin/env python3
"""Run Maven with a local .env file. Values are data, never shell commands."""
from pathlib import Path
import os
import re
import shutil
import subprocess


def read_env(path: Path) -> dict:
    values = {}
    for number, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition('=')
        if not separator or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
            raise ValueError(f'Invalid configuration at line {number}')
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
            value = value[1:-1]
        values[key] = value
    return values


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    env_path = root / '.env'
    if not env_path.is_file():
        raise SystemExit('Run python scripts/init_local_env.py first.')
    env = {**read_env(env_path), **os.environ}
    if len(env.get('JWT_SECRET', '').encode('utf-8')) < 32:
        raise SystemExit('JWT_SECRET must contain at least 32 bytes.')
    maven = shutil.which('mvn.cmd' if os.name == 'nt' else 'mvn')
    if not maven:
        raise SystemExit('Maven is required. Install Java 17 and Maven 3.9+, then retry.')
    return subprocess.call([maven, 'spring-boot:run'], cwd=root / 'backend', env=env)


if __name__ == '__main__':
    raise SystemExit(main())
