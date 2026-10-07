from pathlib import Path
import re
import yaml

directory = Path(__file__).resolve().parent
for path in directory.glob("*.yml"):
    document = yaml.safe_load(path.read_text(encoding="utf-8"))
    assert "on" in document and True not in document, path.name
    assert isinstance(document["on"], (dict, list, str)), path.name
for filename in ("build.yml", "release.yml"):
    document = yaml.safe_load((directory / filename).read_text(encoding="utf-8"))
    jobs = document["jobs"]
    for job_id, job in jobs.items():
        assert "macos" not in job_id.lower(), job_id
        for dependency in job.get("needs", []):
            assert dependency in jobs, (job_id, dependency)
        step_ids = {step["id"] for step in job.get("steps", []) if "id" in step}
        for step in job.get("steps", []):
            content = str(step)
            for referenced in re.findall(r"steps\.([\w-]+)\.", content):
                assert referenced in step_ids, (job_id, referenced)
            assert not any(value in content for value in ("assembleTv", "android-tv", "AppImage", ":app:ios", "src.main.kts")), (job_id, step.get("name"))
            if "ubuntu" in job_id:
                assert "createReleaseDistributable" not in content, job_id
    if filename == "release.yml":
        signing = jobs["create-release"]["steps"]
        validation = next(i for i, step in enumerate(signing) if step.get("name") == "Validate formal release signing configuration")
        publication = next(i for i, step in enumerate(signing) if step.get("name") == "Create Release")
        assert validation < publication
        assert "verify-release-assets" in jobs
    assert any("windows" in job_id for job_id in jobs)
    assert any("ubuntu" in job_id for job_id in jobs)
print("Android/Windows workflow configuration and release signing gate: PASS")
