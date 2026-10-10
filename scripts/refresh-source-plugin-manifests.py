from pathlib import Path
import hashlib
import json
import re
import zipfile

root = Path(__file__).resolve().parent.parent
directory = root / "source/plugins"
for plugin_id in (
    "eacg",
    "dm1",
    "next",
    "girigiri",
    "2rk",
    "dmbus",
    "dyttzy",
    "baimao",
    "akianime",
    "mxdm",
):
    path = directory / "manifests" / f"{plugin_id}.json"
    manifest = json.loads(path.read_text(encoding="utf-8"))
    version_file = directory / "versions" / f"{plugin_id}.properties"
    version = next(
        line.split("=", 1)[1].strip()
        for line in version_file.read_text(encoding="utf-8").splitlines()
        if line.startswith("version=")
    )
    assert manifest["version"] == version
    assert manifest["pluginApiVersion"] == 3
    assert re.fullmatch(
        r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?",
        manifest["version"],
    )
    manifest["minHostVersion"] = "0.1.3"
    for platform, artifact in manifest["artifacts"].items():
        suffix = "-android" if platform == "android" else ""
        payload = directory / "artifacts" / f"source-{plugin_id}{suffix}.jar"
        assert payload.is_file(), payload
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
print("Ten JVM/Dex manifests refreshed from validated local artifacts")
