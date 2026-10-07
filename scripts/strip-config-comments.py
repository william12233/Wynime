import io
import json
import re
import subprocess
import sys
import tokenize
import tomllib
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.parsers import expat
import yaml

ROOT = Path(__file__).resolve().parent.parent
CHECK = '--check' in sys.argv


def blank(text):
    return ''.join(value if value in '\r\n' else ' ' for value in text)


def protected_gaps(text, spans):
    end = 0
    for start, stop in sorted(spans) + [(len(text), len(text))]:
        if start > end:
            yield end, start
        end = max(end, stop)


def yaml_comments(text):
    tokens = list(yaml.scan(text))
    spans = [(token.start_mark.index, token.end_mark.index) for token in tokens]
    ranges = []
    for start, stop in protected_gaps(text, spans):
        for match in re.finditer(r'#[^\r\n]*', text[start:stop]):
            comment_start, comment_stop = start + match.start(), start + match.end()
            line_start = text.rfind('\n', 0, comment_start) + 1
            if not text[line_start:comment_start].strip():
                comment_start = line_start
                comment_stop = min(len(text), comment_stop + (2 if text[comment_stop:comment_stop + 2] == '\r\n' else 1))
            ranges.append((comment_start, comment_stop))
    return ranges, lambda value: yaml.safe_load(value)


def toml_comments(text):
    ranges = []
    position = 0
    while position < len(text):
        current = text[position]
        if current in '\"\'':
            delimiter = current * (3 if text.startswith(current * 3, position) else 1)
            position += len(delimiter)
            while position < len(text):
                if current == '\"' and text[position] == '\\':
                    position += 2
                elif text.startswith(delimiter, position):
                    position += len(delimiter)
                    break
                else:
                    position += 1
        elif current == '#':
            stop = text.find('\n', position)
            stop = len(text) if stop < 0 else stop
            ranges.append((position, stop))
            position = stop
        else:
            position += 1
    return ranges, tomllib.loads


def python_comments(text):
    lines = text.splitlines(keepends=True)
    offsets = [0]
    for line in lines:
        offsets.append(offsets[-1] + len(line))
    ranges = []
    for token in tokenize.generate_tokens(io.StringIO(text).readline):
        if token.type == tokenize.COMMENT and not (token.start == (1, 0) and token.string.startswith('#!')):
            ranges.append((offsets[token.start[0] - 1] + token.start[1], offsets[token.end[0] - 1] + token.end[1]))
    def signature(value):
        return [(token.type, token.string) for token in tokenize.generate_tokens(io.StringIO(value).readline)
                if token.type not in (tokenize.COMMENT, tokenize.NL, tokenize.ENCODING)]
    return ranges, signature


def xml_comments(text):
    raw = text.encode('utf-8')
    parser = expat.ParserCreate()
    ranges = []
    def comment(value):
        start = parser.CurrentByteIndex
        stop = raw.index(b'-->', start) + 3
        ranges.append((len(raw[:start].decode('utf-8')), len(raw[:stop].decode('utf-8'))))
    parser.CommentHandler = comment
    parser.Parse(text, True)
    def signature(value):
        def node(element):
            content = element.text or ''
            tail = element.tail or ''
            return (element.tag, sorted(element.attrib.items()), content if content.strip() else '',
                    tail if tail.strip() else '', [node(child) for child in element])
        return node(ET.fromstring(value))
    return ranges, signature


def line_comments(text, gitignore=False):
    ranges = []
    position = 0
    continued = False
    for line in text.splitlines(keepends=True):
        content = line if gitignore else line.lstrip(' \t\f')
        if not continued and content.startswith(('#',) if gitignore else ('#', '!')):
            ranges.append((position, position + len(line.rstrip('\r\n'))))
        continued = not gitignore and len(line.rstrip('\r\n')) - len(line.rstrip('\r\n').rstrip('\\')) & 1 == 1
        position += len(line)
    def signature(value):
        hidden = set()
        for start, stop in ranges:
            hidden.update(range(start, stop))
        return ''.join(character for index, character in enumerate(value) if index not in hidden)
    return ranges, signature


def proguard_comments(text):
    ranges = []
    quote = None
    position = 0
    while position < len(text):
        current = text[position]
        if quote:
            if current == '\\':
                position += 2
                continue
            if current == quote:
                quote = None
        elif current in '\"\'':
            quote = current
        elif current == '#':
            stop = text.find('\n', position)
            stop = len(text) if stop < 0 else stop
            ranges.append((position, stop))
            position = stop
            continue
        position += 1
    def signature(value):
        cleaned = value
        for start, stop in reversed(ranges):
            cleaned = cleaned[:start] + blank(cleaned[start:stop]) + cleaned[stop:]
        return re.findall(r'\S+', cleaned)
    return ranges, signature


selected = subprocess.check_output(['git', '-c', 'core.quotePath=false', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT).decode('utf-8').split('\0')
inventory = []
for relative in sorted(set(selected)):
    parts = Path(relative).parts
    if not relative or any(part in ('build', 'node_modules', '.agents', '.claude', 'licenses') or part.startswith('.tmp') for part in parts):
        continue
    target = (ROOT / relative).resolve()
    if not target.is_file():
        continue
    target.relative_to(ROOT)
    extension = target.suffix
    readers = {'.py': python_comments, '.xml': xml_comments, '.svg': xml_comments, '.toml': toml_comments,
               '.yml': yaml_comments, '.yaml': yaml_comments, '.properties': line_comments, '.pro': proguard_comments}
    if extension not in readers and target.name not in ('.gitignore', '.editorconfig', '.gitattributes'):
        continue
    original = target.read_text(encoding='utf-8')
    ranges, signature = line_comments(original, True) if target.name in ('.gitignore', '.editorconfig', '.gitattributes') else readers[extension](original)
    result = original
    for start, stop in reversed(ranges):
        value = original[start:stop]
        remove_line = extension in ('.yaml', '.yml') and value.lstrip().startswith('#') and value.endswith('\n')
        result = result[:start] + ('' if extension in ('.xml', '.svg') or remove_line else blank(value)) + result[stop:]
    if extension not in ('.properties',) and target.name != '.gitignore':
        if signature(original) != signature(result):
            (ROOT / '.tmp-code-cleanup/config-comment-diagnostic.json').write_text(json.dumps(dict(path=relative, ranges=[original[start:stop] for start, stop in ranges], before=signature(original), after=signature(result)), default=str, indent=2), encoding='utf-8')
            raise AssertionError(f'Semantic change: {relative}')
    notices = [original[start:stop] for start, stop in ranges if re.search(r'Copyright|SPDX-License-Identifier', original[start:stop], re.I)]
    if not CHECK and notices:
        notice = ROOT / 'licenses/source-notices' / (relative + '.license')
        notice.parent.mkdir(parents=True, exist_ok=True)
        if not notice.exists():
            notice.write_text('\n\n'.join(notices) + '\n', encoding='utf-8')
    if not CHECK and result != original:
        target.write_text(result, encoding='utf-8', newline='\n')
    inventory.append(dict(path=relative, comments=len(ranges)))
print('Configuration/Python/XML:', len(inventory), 'files,', sum(item['comments'] for item in inventory), 'comments, semantic equality=PASS')
if CHECK and any(item['comments'] for item in inventory):
    sys.exit(1)
