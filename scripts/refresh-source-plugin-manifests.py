from pathlib import Path
import hashlib
import json
import zipfile

root = Path(__file__).resolve().parent.parent
directory = root / "source/plugins"
for plugin_id in ("eacg", "dm1", "next", "girigiri", "2rk", "dida", "dmbus", "dyttzy"):
    path = directory / "manifests" / f"{plugin_id}.json"
    manifest = json.loads(path.read_text(encoding="utf-8"))
    assert manifest["pluginApiVersion"] == 3
    assert manifest["version"] == "1.0.26"
    manifest["minHostVersion"] = "0.1.3"
    for platform, artifact in manifest["artifacts"].items():
        payload = directory / artifact["url"]
        assert payload.resolve().is_relative_to((directory / "artifacts").resolve())
        with zipfile.ZipFile(payload) as archive:
            names = archive.namelist()
            if platform == "android":
                assert names == ["classes.dex"]
                assert archive.read("classes.dex").startswith(b"dex\n")
            else:
                assert any(name.endswith(".class") for name in names)
                assert "classes.dex" not in names
        artifact["sha256"] = hashlib.sha256(payload.read_bytes()).hexdigest()
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print("Eight JVM/Dex manifests refreshed from validated local artifacts")
