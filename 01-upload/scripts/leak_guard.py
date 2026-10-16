#!/usr/bin/env python3
"""Small pre-publication guard; reports paths/rules, never credential values.

With Git, scan index contents plus non-ignored untracked files. A clean working
copy cannot hide a staged secret. Without Git, scan the complete source tree.
This is a heuristic check, not a full security or provenance audit.
"""
from pathlib import Path
import argparse
import re
import subprocess

BUILD_DIRS = {'.git', 'node_modules', 'target', 'dist', '__pycache__', '.vite'}
SECRET_NAMES = {'credentials.txt', 'project.private.config.json', 'id_rsa', 'id_ed25519'}
SECRET_SUFFIXES = {'.pem', '.key', '.p12', '.pfx', '.jks', '.bson', '.dump'}
RULES = [
    ('private key', re.compile(r'-----BEGIN (?:[A-Z0-9]+ )*PRIVATE KEY-----')),
    ('provider token', re.compile(r'\b(?:sk-(?:proj-)?[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{30,})\b')),
    ('JWT value', re.compile(r'\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b')),
    ('URL credentials', re.compile(r'(?:mongodb(?:\+srv)?|https?|mqtts?)://[^\s/:@]+:[^\s/@]+@')),
    ('literal password hash input', re.compile(r'passwordEncoder\.encode\(\s*[\"\'][^\"\']+[\"\']')),
    ('literal secret assignment', re.compile(r'''(?im)^[ \t]*(?:(?:private|public|static|final|String|const|let|var)[ \t]+)*["']?(?:jwt[_-]?secret|secret|api[_-]?key|access[_-]?key|password|passwd|BOOTSTRAP_ADMIN_PASSWORD)["']?[ \t]*[:=][ \t]*["']([^"'\n]{8,})["']''')),
    ('unquoted secret setting', re.compile(r'(?im)^[ \t]*(?:JWT_SECRET|BOOTSTRAP_ADMIN_PASSWORD|BILLCHECK_AI_API_KEY|MQTT_PASSWORD|secret|api-key)[ \t]*[:=][ \t]*([^\s#\n]{8,})')),
]


def candidates(root: Path):
    try:
        top = subprocess.run(['git', 'rev-parse', '--show-toplevel'], cwd=root,
                             capture_output=True, check=True).stdout.decode().strip()
        # Do not accidentally scan a containing workspace repository.
        if Path(top).resolve() != root.resolve():
            raise ValueError('not the project repository')
        tracked = subprocess.run(['git', 'ls-files', '-z'], cwd=root,
                                 capture_output=True, check=True).stdout.split(b'\0')
        untracked = subprocess.run(['git', 'ls-files', '--others', '--exclude-standard', '-z'],
                                   cwd=root, capture_output=True, check=True).stdout.split(b'\0')
        for raw in tracked:
            if raw:
                name = raw.decode('utf-8')
                blob = subprocess.run(['git', 'show', ':' + name], cwd=root,
                                      capture_output=True, check=True).stdout
                yield name, blob
        for raw in untracked:
            if raw:
                name = raw.decode('utf-8')
                path = root / name
                if path.is_symlink():
                    yield name, None
                else:
                    yield name, path.read_bytes()
        return
    except (FileNotFoundError, subprocess.CalledProcessError, ValueError):
        pass
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if any(part in BUILD_DIRS for part in relative.parts):
            continue
        if path.is_symlink():
            yield relative.as_posix(), None
        elif path.is_file():
            yield relative.as_posix(), path.read_bytes()


def inspect(name: str, data: bytes | None):
    p = Path(name)
    findings = []
    if data is None:
        return [(name, 0, 'symlink requires manual review')]
    is_env = p.name == '.env' or (p.name.startswith('.env.') and p.name != '.env.example')
    if is_env or p.name in SECRET_NAMES or p.suffix.lower() in SECRET_SUFFIXES:
        findings.append((name, 0, 'private file must not be committed'))
    try:
        text = data.decode('utf-8-sig')
    except UnicodeDecodeError:
        return findings
    for label, pattern in RULES:
        for match in pattern.finditer(text):
            if label in {'literal secret assignment', 'unquoted secret setting'}:
                value = match.group(1)
                if value.startswith('${') or value in {'YOUR_API_KEY', 'YOUR_SECRET', 'REPLACE_ME'}:
                    continue
            findings.append((name, text.count('\n', 0, match.start()) + 1, label))
    return findings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    findings = []
    count = 0
    for name, data in candidates(args.root.resolve()):
        count += 1
        findings.extend(inspect(name, data))
    for name, line, label in findings:
        print(f'{name}:{line}: {label}')
    if findings:
        print(f'FAIL: {len(findings)} finding(s); values withheld.')
        return 1
    print(f'PASS: {count} files checked; no configured secret patterns found.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
