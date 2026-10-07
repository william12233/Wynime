from pathlib import Path
import json
import sys
import yaml

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / '.tmp-code-cleanup/comment-tool'))
from pygments.lexers import BashLexer, BatchLexer, PowerShellLexer, PythonLexer
from pygments.token import Comment, Text

CHECK = '--check' in sys.argv


def code_tokens(source, lexer):
    return [(kind, value) for _, kind, value in lexer.get_tokens_unprocessed(source)
            if kind not in Comment and not (kind in Text and not value.strip())]


files = 0
comments = 0
for path in sorted((ROOT / '.github/workflows').glob('*.yml')):
    source = path.read_text(encoding='utf-8')
    document = yaml.safe_load(source)
    if True in document:
        document['on'] = document.pop(True)
    changed = False
    for job in document.get('jobs', {}).values():
        runner = str(job.get('runs-on', '')).lower()
        default_shell = job.get('defaults', {}).get('run', {}).get('shell',
            document.get('defaults', {}).get('run', {}).get('shell', 'pwsh' if 'windows' in runner else 'bash'))
        for step in job.get('steps', []):
            if 'run' not in step:
                continue
            script = step['run']
            shell = step.get('shell', default_shell).split()[0]
            lexer = {'pwsh': PowerShellLexer, 'powershell': PowerShellLexer,
                     'cmd': BatchLexer, 'python': PythonLexer}.get(shell, BashLexer)()
            ranges = [(start, start + len(value)) for start, kind, value in lexer.get_tokens_unprocessed(script)
                      if kind in Comment and not value.startswith('#!')]
            result = script
            for start, stop in reversed(ranges):
                result = result[:start] + ''.join(value if value in '\r\n' else ' ' for value in script[start:stop]) + result[stop:]
            if code_tokens(script, lexer) != code_tokens(result, lexer):
                raise RuntimeError(f'Embedded token change: {path.name}: {step.get("name")}')
            notices = [script[start:stop] for start, stop in ranges if 'Copyright' in script[start:stop] or 'SPDX' in script[start:stop]]
            if not CHECK and notices:
                target = ROOT / 'licenses/source-notices' / '.github/workflows' / (path.name + '.embedded.license')
                target.parent.mkdir(parents=True, exist_ok=True)
                if not target.exists():
                    target.write_text('\n\n'.join(notices) + '\n', encoding='utf-8')
            comments += len(ranges)
            if result != script:
                step['run'] = result
                changed = True
    if not CHECK and changed:
        temporary = path.with_suffix('.yml.cleanup-tmp')
        temporary.write_text(yaml.safe_dump(document, allow_unicode=True, sort_keys=False, width=120), encoding='utf-8')
        temporary.replace(path)
    files += 1
print(f'Embedded workflow scripts: files={files} comments={comments} token equality=PASS')
if CHECK and comments:
    sys.exit(1)
