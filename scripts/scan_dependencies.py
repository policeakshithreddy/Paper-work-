#!/usr/bin/env python3
"""Query OSV using only Maven coordinates; no project content or credentials are sent."""
import json
import pathlib
import sys
import urllib.request

inventory = pathlib.Path("app/build/reports/runtime-dependencies.tsv")
packages = [line.split("\t") for line in inventory.read_text().splitlines() if line.strip()]
findings = []
for offset in range(0, len(packages), 100):
    batch = packages[offset:offset + 100]
    query = {"queries": [{"package": {"ecosystem": "Maven", "name": name}, "version": version}
                         for name, version in batch]}
    request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
                                     data=json.dumps(query).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        results = json.load(response)["results"]
    for package, result in zip(batch, results):
        if result.get("vulns"):
            findings.append({"package": package[0], "version": package[1],
                             "advisories": [item["id"] for item in result["vulns"]]})
print(json.dumps({"packages_checked": len(packages), "findings": findings}, indent=2))
sys.exit(1 if findings else 0)
