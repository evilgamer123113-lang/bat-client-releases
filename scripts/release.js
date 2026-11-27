#!/usr/bin/env node

/**
 * Release script for Bat Client Launcher.
 *
 * Usage:
 *   node scripts/release.js [patch|minor|major]
 *
 * What it does:
 *   1. Bumps version in package.json (patch by default)
 *   2. Runs "npm run dist" to build the NSIS installer
 *   3. Creates a GitHub Release via "gh" CLI and uploads the installer
 *
 * Prerequisites:
 *   - "gh" CLI installed and authenticated: https://cli.github.com/
 *   - Git repo with a "main" branch
 *   - electron-builder producing dist/BatClient-{version}-Setup.exe
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const GITHUB_OWNER = 'evilgamer123113-lang';
const GITHUB_REPO = 'bat-client-releases';

const ROOT = path.resolve(__dirname, '..');
const PKG_PATH = path.join(ROOT, 'package.json');

function run(cmd, opts = {}) {
    console.log(`\n> ${cmd}`);
    execSync(cmd, { stdio: 'inherit', cwd: ROOT, ...opts });
}

function main() {
    const bumpType = process.argv[2] || 'patch';
    if (!['patch', 'minor', 'major'].includes(bumpType)) {
        console.error(`Usage: node scripts/release.js [patch|minor|major]`);
        process.exit(1);
    }

    // Step 1: Ensure working tree is clean
    console.log('--- Checking git status ---');
    const status = execSync('git status --porcelain', { cwd: ROOT }).toString().trim();
    if (status) {
        console.error('Working tree is not clean. Commit or stash changes first.');
        process.exit(1);
    }

    // Step 2: Ensure on main branch
    const branch = execSync('git branch --show-current', { cwd: ROOT }).toString().trim();
    if (branch !== 'main' && branch !== 'master') {
        console.error(`Not on main/master branch (currently on "${branch}"). Switch first.`);
        process.exit(1);
    }

    // Step 3: Bump version in package.json
    console.log(`\n--- Bumping ${bumpType} version ---`);
    run(`npm version ${bumpType} --no-git-tag-version`);

    const pkg = JSON.parse(fs.readFileSync(PKG_PATH, 'utf-8'));
    const version = pkg.version;
    const tag = `v${version}`;
    console.log(`New version: ${version}`);

    // Also update the GITHUB_OWNER/REPO in main.js to stay in sync
    const mainJsPath = path.join(ROOT, 'main.js');
    let mainJs = fs.readFileSync(mainJsPath, 'utf-8');
    if (!mainJs.includes(`'${GITHUB_OWNER}'`)) {
        console.warn(`[WARN] GITHUB_OWNER '${GITHUB_OWNER}' not found in main.js. Skipping update.`);
    }
    if (!mainJs.includes(`'${GITHUB_REPO}'`)) {
        console.warn(`[WARN] GITHUB_REPO '${GITHUB_REPO}' not found in main.js. Skipping update.`);
    }

    // Step 4: Commit the version bump
    console.log('\n--- Committing version bump ---');
    run('git add package.json');
    run(`git commit -m "chore: release ${tag}"`);

    // Step 5: Create git tag
    console.log(`\n--- Creating tag ${tag} ---`);
    run(`git tag -a ${tag} -m "Release ${tag}"`);

    // Step 6: Push commit + tag
    console.log('\n--- Pushing to origin ---');
    run(`git push origin ${branch}`);
    run(`git push origin ${tag}`);

    // Step 7: Build the installer
    console.log('\n--- Building installer ---');
    run('npm run dist');

    // Step 8: Find the built installer
    const distDir = path.join(ROOT, 'dist');
    const installerName = `BatClient-${version}-Setup.exe`;
    const installerPath = path.join(distDir, installerName);

    if (!fs.existsSync(installerPath)) {
        console.error(`Installer not found at: ${installerPath}`);
        console.error('Build may have failed. Check the output above.');
        process.exit(1);
    }

    const sizeMB = (fs.statSync(installerPath).size / 1024 / 1024).toFixed(1);
    console.log(`\nInstaller: ${installerName} (${sizeMB} MB)`);

    // Step 9: Create GitHub Release and upload installer
    console.log('\n--- Creating GitHub Release ---');
    const releaseBody = `## Bat Client ${version}\n\n### Changes\n- Updated to version ${version}\n\n### Installation\nDownload and run \`BatClient-${version}-Setup.exe\`.`;

    run(
        `gh release create ${tag} "${installerPath}#${installerName}" ` +
        `--repo ${GITHUB_OWNER}/${GITHUB_REPO} ` +
        `--title "Bat Client ${version}" ` +
        `--notes "${releaseBody}" ` +
        `--latest`
    );

    const releaseUrl = `https://github.com/${GITHUB_OWNER}/${GITHUB_REPO}/releases/tag/${tag}`;
    console.log(`\n========================================`);
    console.log(`  Release published!`);
    console.log(`  ${releaseUrl}`);
    console.log(`========================================\n`);
}

try {
    main();
} catch (err) {
    console.error('\nRelease failed:', err.message || err);
    process.exit(1);
}
