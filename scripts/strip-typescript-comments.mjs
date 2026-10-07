import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(import.meta.url);
const ts = require(path.join(root, "backend/wynime-bangumi-broker/node_modules/typescript"));
const checkOnly = process.argv.includes("--check");
const selected = execFileSync("git", ["-c", "core.quotePath=false", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], { cwd: root }).toString().split("\0");

function parse(text, filename) {
  const source = ts.createSourceFile(filename, text, ts.ScriptTarget.Latest, true,
    filename.endsWith(".jsonc") ? ts.ScriptKind.JSON : ts.ScriptKind.TS);
  if (source.parseDiagnostics.length) throw new Error(`Parse errors: ${filename}`);
  const leaves = [];
  function visit(node) {
    if (node.kind >= ts.SyntaxKind.FirstJSDocNode && node.kind <= ts.SyntaxKind.LastJSDocNode) return;
    const children = node.getChildren(source);
    if (children.length) children.forEach(visit);
    else if (node.end > node.getStart(source)) leaves.push([node.getStart(source), node.end, node.kind]);
  }
  visit(source);
  return leaves.sort((a, b) => a[0] - b[0]);
}

let files = 0, comments = 0;
for (const relative of new Set(selected)) {
  if (!/\.(ts|jsonc)$/.test(relative) || /(^|\/)(\.tmp[^/]*|\.agents|\.claude|licenses|build|node_modules)\//.test(relative)) continue;
  if (relative.endsWith(".ts") && !relative.startsWith("backend/")) continue;
  const filename = path.resolve(root, relative);
  if (!filename.startsWith(root + path.sep) || !fs.existsSync(filename)) continue;
  const original = fs.readFileSync(filename, "utf8");
  const leaves = parse(original, filename);
  const ranges = [];
  let end = 0;
  for (const [start, stop] of [...leaves, [original.length, original.length]]) {
    const gap = original.slice(end, start);
    const scanner = ts.createScanner(ts.ScriptTarget.Latest, false, ts.LanguageVariant.Standard, gap);
    for (let token = scanner.scan(); token !== ts.SyntaxKind.EndOfFileToken; token = scanner.scan()) {
      if (token === ts.SyntaxKind.SingleLineCommentTrivia || token === ts.SyntaxKind.MultiLineCommentTrivia) {
        ranges.push([end + scanner.getTokenPos(), end + scanner.getTextPos()]);
      }
    }
    end = Math.max(end, stop);
  }
  let cleaned = original;
  for (const [start, stop] of ranges.toReversed()) cleaned = cleaned.slice(0, start) + original.slice(start, stop).replace(/[^\r\n]/g, " ") + cleaned.slice(stop);
  const before = leaves.map(([start, stop, kind]) => [kind, original.slice(start, stop)]);
  const after = parse(cleaned, filename).map(([start, stop, kind]) => [kind, cleaned.slice(start, stop)]);
  if (JSON.stringify(before) !== JSON.stringify(after)) throw new Error(`Token change: ${relative}`);
  const notices = ranges.map(([start, stop]) => original.slice(start, stop)).filter(value => /Copyright|SPDX-License-Identifier/i.test(value));
  if (!checkOnly && notices.length) {
    const notice = path.join(root, "licenses/source-notices", relative + ".license");
    fs.mkdirSync(path.dirname(notice), { recursive: true });
    if (!fs.existsSync(notice)) fs.writeFileSync(notice, notices.join("\n\n") + "\n");
  }
  if (!checkOnly && cleaned !== original) fs.writeFileSync(filename, cleaned);
  files++; comments += ranges.length;
}
console.log(`TypeScript/JSONC: files=${files} comments=${comments} non-comment token equality=PASS`);
if (checkOnly && comments) process.exitCode = 1;
