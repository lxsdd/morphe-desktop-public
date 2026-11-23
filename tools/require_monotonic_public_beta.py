#!/usr/bin/env python3
"""Fail closed when a proposed full Custom prerelease is not newer than published ones."""
import argparse
import json
import re

VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)-(beta|rc)\.(\d+)$")
FULL_TAG_RE = re.compile(r"^custom-v(\d+\.\d+\.\d+-(?:beta|rc)\.\d+)-[0-9a-f]{12}$")
FULL_TAG_PREFIX_RE = re.compile(r"^custom-v\d+\.\d+\.\d+-(?:beta|rc)\.\d+")


def version_key(raw):
    if not isinstance(raw, str):
        raise ValueError("Invalid full Custom version type")
    match = VERSION_RE.fullmatch(raw)
    if match is None:
        raise ValueError("Invalid full Custom version: " + raw)
    major, minor, patch, channel, sequence = match.groups()
    return (int(major), int(minor), int(patch), 0 if channel == "beta" else 1, int(sequence))


def require_newer_public_beta(candidate_version, published_releases):
    candidate = version_key(candidate_version)
    if not isinstance(published_releases, list):
        raise ValueError("Release inventory must be a list")
    if len(published_releases) >= 100:
        raise ValueError("Release inventory may be paginated: refuse incomplete history")
    for release in published_releases:
        if not isinstance(release, dict):
            raise ValueError("Malformed release inventory entry")
        if release.get("draft"):
            continue
        tag = release.get("tag_name")
        if not isinstance(tag, str):
            raise ValueError("Missing published release tag")
        matched = FULL_TAG_RE.fullmatch(tag)
        if matched is None:
            if FULL_TAG_PREFIX_RE.match(tag):
                raise ValueError("Ambiguous full Custom release tag: " + tag)
            continue  # Historical core previews/other release types are not full betas.
        previous_version = matched.group(1)
        if candidate <= version_key(previous_version):
            raise ValueError(
                "A new full Custom release must be strictly newer than "
                + previous_version + " (existing tag " + tag + ")"
            )
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--releases-json", required=True)
    args = parser.parse_args()
    try:
        with open(args.releases_json, encoding="utf-8-sig") as stream:
            releases = json.load(stream)
        require_newer_public_beta(args.version, releases)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        raise SystemExit("PUBLIC_RELEASE_VERSION=FAIL " + str(exc)) from exc
    print("PUBLIC_RELEASE_VERSION=PASS version=" + args.version)


if __name__ == "__main__":
    main()
