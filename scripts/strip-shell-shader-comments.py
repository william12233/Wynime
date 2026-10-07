import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / '.tmp-code-cleanup/comment-tool'))
from pygments.lexers import BashLexer, BatchLexer, JavaLexer, GLShaderLexer
from pygments.token import Comment, Text

class ShaderLexer(GLShaderLexer):
    tokens = dict(GLShaderLexer.tokens)
    tokens['root'] = [(r'#[ \t]*[A-Za-z_]\w*', Comment.Preproc) if item[1] == Comment.Preproc else item
                      for item in GLShaderLexer.tokens['root']]

CHECK = '--check' in sys.argv
LEXERS = {'.sh': BashLexer, '.bat': BatchLexer, '.cmd': BatchLexer, '.java': JavaLexer, '.glsl': ShaderLexer, '.frag': ShaderLexer, '.vert': ShaderLexer}


def is_comment(kind, value):
    return kind in Comment and kind not in Comment.Preproc and not value.startswith('#!')


def tokens(text, lexer):
    return [(kind, value) for position, kind, value in lexer.get_tokens_unprocessed(text)
            if not is_comment(kind, value) and not (kind in Text and not value.strip())]


selected = subprocess.check_output(['git', '-c', 'core.quotePath=false', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=ROOT).decode('utf-8').split('\0')
inventory = []
for relative in sorted(set(selected)):
    if not relative or any(part in ('build', 'node_modules', '.agents', '.claude', 'licenses') or part.startswith('.tmp') for part in Path(relative).parts):
        continue
    target = ROOT / relative
    if (target.suffix not in LEXERS and target.name not in ('gradlew', 'launcher')) or not target.is_file():
        continue
    target.resolve().relative_to(ROOT)
    lexer = (BashLexer if target.name in ('gradlew', 'launcher') else LEXERS[target.suffix])()
    text = target.read_text(encoding='utf-8')
    ranges = [(position, position + len(value)) for position, kind, value in lexer.get_tokens_unprocessed(text) if is_comment(kind, value)]
    result = text
    for start, stop in reversed(ranges):
        result = result[:start] + ''.join(value if value in '\r\n' else ' ' for value in text[start:stop]) + result[stop:]
    assert tokens(text, lexer) == tokens(result, lexer), f'Token change: {relative}'
    notices = [text[start:stop] for start, stop in ranges if re.search(r'Copyright|SPDX-License-Identifier', text[start:stop], re.I)]
    if not CHECK and notices:
        notice = ROOT / 'licenses/source-notices' / (relative + '.license')
        notice.parent.mkdir(parents=True, exist_ok=True)
        if not notice.exists():
            notice.write_text('\n\n'.join(notices) + '\n', encoding='utf-8')
    if not CHECK and result != text:
        target.write_text(result, encoding='utf-8', newline='\n')
    inventory.append(dict(path=relative, comments=len(ranges)))
print('Shell/Java/GLSL/ANTLR:', len(inventory), 'files,', sum(item['comments'] for item in inventory), 'comments, token equality=PASS')
if CHECK and any(item['comments'] for item in inventory):
    sys.exit(1)
