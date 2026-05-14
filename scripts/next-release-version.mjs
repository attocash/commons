import { spawnSync } from "node:child_process";
import { appendFile, readFile } from "node:fs/promises";

const eventName = process.env.GITHUB_EVENT_NAME ?? "";
const gitRef = process.env.GITHUB_REF ?? "";
const isMainPush = eventName === "push" && gitRef === "refs/heads/main";

const semanticReleasePackages = [
  "semantic-release@24.2.7",
  "@semantic-release/commit-analyzer@13.0.1",
  "@semantic-release/release-notes-generator@14.1.0",
  "@semantic-release/github@11.0.6",
];

const semanticReleaseCommand = [
  "npx",
  "--yes",
  ...semanticReleasePackages.flatMap((dependency) => ["-p", dependency]),
  "semantic-release",
  "--dry-run",
  "--no-ci",
];

const semanticRelease = isMainPush ? runSemanticReleaseDryRun() : null;
const semanticOutput = `${semanticRelease?.stdout ?? ""}\n${semanticRelease?.stderr ?? ""}`;
const semanticVersion = semanticOutput.match(/The next release version is ([0-9A-Za-z.+-]+)/)?.[1];
const latestTag = getLatestSemverTag();
const latestVersion = latestTag?.version ?? "0.0.0";
const fallbackReleaseType = latestTag ? analyzeCommitsSince(latestTag.name) : "patch";
const fallbackVersion = fallbackReleaseType ? incrementVersion(latestVersion, fallbackReleaseType) : latestVersion;
const version = semanticVersion ?? fallbackVersion;
const hasRelease = isMainPush && Boolean(semanticVersion);
const shortSha = runGit(["rev-parse", "--short", "HEAD"]).trim();
const prNumber = await getPullRequestNumber();
const snapshotSuffix = prNumber ? `pr-${prNumber}-sha${shortSha}` : `sha${shortSha}`;
const npmSnapshotSuffix = prNumber ? `pr.${prNumber}.sha${shortSha}` : `snapshot.sha${shortSha}`;
const mavenSnapshotVersion = prNumber ? `${version}-${snapshotSuffix}-SNAPSHOT` : `${version}-SNAPSHOT`;
const npmSnapshotVersion = `${version}-${npmSnapshotSuffix}`;
const tag = `v${version}`;

if (process.env.GITHUB_OUTPUT) {
  await appendFile(
    process.env.GITHUB_OUTPUT,
    [
      `has_release=${hasRelease}`,
      `version=${version}`,
      `tag=${tag}`,
      `short_sha=${shortSha}`,
      `maven_snapshot_version=${mavenSnapshotVersion}`,
      `npm_snapshot_version=${npmSnapshotVersion}`,
      "",
    ].join("\n"),
  );
}

console.log(`Version: ${version}`);
console.log(`Tag: ${tag}`);
console.log(`Has release: ${hasRelease}`);
console.log(`Maven snapshot: ${mavenSnapshotVersion}`);
console.log(`npm snapshot: ${npmSnapshotVersion}`);

function runGit(args) {
  const result = spawnSync("git", args, {
    cwd: process.cwd(),
    encoding: "utf8",
  });

  if (result.status !== 0) {
    throw new Error(result.stderr || `git ${args.join(" ")} failed`);
  }

  return result.stdout;
}

function getLatestSemverTag() {
  const tags = runGit(["tag", "--list"]).split("\n").filter(Boolean);
  const versions =
    tags
      .map((name) => {
        const match = name.match(/^v?(\d+)\.(\d+)\.(\d+)$/);
        if (!match) {
          return null;
        }
        return {
          name,
          version: `${match[1]}.${match[2]}.${match[3]}`,
          major: Number(match[1]),
          minor: Number(match[2]),
          patch: Number(match[3]),
        };
      })
      .filter(Boolean)
      .sort((left, right) => {
        if (left.major !== right.major) {
          return right.major - left.major;
        }
        if (left.minor !== right.minor) {
          return right.minor - left.minor;
        }
        return right.patch - left.patch;
      });

  return versions[0] ?? null;
}

function analyzeCommitsSince(tag) {
  const log = runGit(["log", "--format=%B%x1e", `${tag}..HEAD`]);
  const commits = log.split("\x1e").map((commit) => commit.trim()).filter(Boolean);
  let releaseType = null;

  for (const commit of commits) {
    const header = commit.split("\n")[0] ?? "";
    if (header.match(/^[a-z]+(?:\([^)]+\))?!:/i) || commit.match(/^BREAKING[ -]CHANGE:/im)) {
      return "major";
    }
    if (header.match(/^feat(?:\([^)]+\))?:/i)) {
      releaseType = releaseType === "major" ? releaseType : "minor";
    } else if (header.match(/^(fix|perf|revert)(?:\([^)]+\))?:/i) && !releaseType) {
      releaseType = "patch";
    }
  }

  return releaseType;
}

function incrementVersion(version, releaseType) {
  const [major, minor, patch] = version.split(".").map(Number);

  if (releaseType === "major") {
    return `${major + 1}.0.0`;
  }
  if (releaseType === "minor") {
    return `${major}.${minor + 1}.0`;
  }
  return `${major}.${minor}.${patch + 1}`;
}

async function getPullRequestNumber() {
  if (eventName !== "pull_request" || !process.env.GITHUB_EVENT_PATH) {
    return null;
  }

  const event = JSON.parse(await readFile(process.env.GITHUB_EVENT_PATH, "utf8"));
  return event.pull_request?.number ?? null;
}

function runSemanticReleaseDryRun() {
  const result = spawnSync(semanticReleaseCommand[0], semanticReleaseCommand.slice(1), {
    cwd: process.cwd(),
    env: process.env,
    encoding: "utf8",
  });

  process.stdout.write(result.stdout ?? "");
  process.stderr.write(result.stderr ?? "");

  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }

  return result;
}
