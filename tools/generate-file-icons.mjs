#!/usr/bin/env node

import {
    existsSync,
    mkdirSync,
    readFileSync,
    rmSync,
    writeFileSync
} from "node:fs";

import { execFileSync } from "node:child_process";

import { join, resolve } from "node:path";
import { tmpdir } from "node:os";

const MATERIAL_FILE_ICONS =
        "https://github.com/simonnilsson/material-file-icons.git";

const MATERIAL_FILE_ICONS_TAG =
        "v2.4.0";

const MATERIAL_ICON_THEME =
        "https://github.com/PKief/vscode-material-icon-theme.git";

const TEMP_ROOT =
        join(
                tmpdir(),
                "s3-explorer-material-file-icons");

const MATERIAL_FILE_ICONS_DIR =
        join(
                TEMP_ROOT,
                "material-file-icons");

const MATERIAL_ICON_THEME_DIR =
        join(
                TEMP_ROOT,
                "material-icon-theme");

const PROJECT_ROOT =
        resolve(
                import.meta.dirname,
                "..");

const OUTPUT_ROOT =
        join(
                PROJECT_ROOT,
                "src",
                "main",
                "resources",
                "file-icons");

function gitClone(
        repository,
        destination,
        branch) {

    execFileSync(
            "git.exe",
            [
                "clone",
                "--depth",
                "1",
                "--branch",
                branch,
                repository,
                destination
            ],
            {
                stdio: "inherit"
            });
}

rmSync(
        TEMP_ROOT,
        {
            recursive: true,
            force: true
        });

mkdirSync(
        TEMP_ROOT,
        {
            recursive: true
        });

console.log(
        "Cloning material-file-icons...");

gitClone(
        MATERIAL_FILE_ICONS,
        MATERIAL_FILE_ICONS_DIR,
        MATERIAL_FILE_ICONS_TAG);

console.log(
        "Cloning material-icon-theme...");

gitClone(
        MATERIAL_ICON_THEME,
        MATERIAL_ICON_THEME_DIR,
        "main");

console.log("Sources downloaded.");

const themeIconsDir =
        join(
                MATERIAL_ICON_THEME_DIR,
                "icons");

const fileIconsSource =
        join(
                MATERIAL_ICON_THEME_DIR,
                "src",
                "core",
                "icons",
                "fileIcons.ts");

const languageIconsSource =
        join(
                MATERIAL_ICON_THEME_DIR,
                "src",
                "core",
                "icons",
                "languageIcons.ts");

console.log(
        `Theme icons: ${themeIconsDir}`);

console.log(
        `fileIcons.ts: ${fileIconsSource}`);

console.log(
        `languageIcons.ts: ${languageIconsSource}`);

if (!existsSync(themeIconsDir)) {
    throw new Error(
            `Icons directory not found: ${themeIconsDir}`);
}

if (!existsSync(fileIconsSource)) {
    throw new Error(
            `fileIcons.ts not found: ${fileIconsSource}`);
}

if (!existsSync(languageIconsSource)) {
    throw new Error(
            `languageIcons.ts not found: ${languageIconsSource}`);
}

console.log(
        "Required source files found.");
        
const fileIconsSourceText =
        readFileSync(
                fileIconsSource,
                "utf8");

const fileIcons =
        extractFileIcons(
                fileIconsSourceText);

console.log(
        `File icon definitions: ${fileIcons.size}`);
        
const ICON_ALIASES = {
    svelte_js: "svelte",
    svelte_ts: "svelte"
};

const DISPLAY_NAME_OVERRIDES = {
    nodejs: "Node.js",
    javascript: "JavaScript",
    typescript: "TypeScript",
    svelte_js: "Svelte",
    svelte_ts: "Svelte",
    json: "JSON",
    xml: "XML",
    html: "HTML",
    css: "CSS",
    yaml: "YAML",
    markdown: "Markdown",
    pdf: "PDF",
    docker: "Docker",
    maven: "Maven",
    gradle: "Gradle",
    npm: "npm",
    git: "Git"
};

const outputSvgRoot =
        join(
                OUTPUT_ROOT,
                "svg");

rmSync(
        OUTPUT_ROOT,
        {
            recursive: true,
            force: true
        });

mkdirSync(
        outputSvgRoot,
        {
            recursive: true
        });

const properties = [
        "# GENERATED FILE - DO NOT EDIT",
        `# Generated from material-file-icons ${MATERIAL_FILE_ICONS_TAG}`,
        ""
];

let svgCount = 0;
let missingSvgCount = 0;
let extensionCount = 0;
let filenameCount = 0;

for (const definition of
        fileIcons.values()) {

    const iconName =
            definition.name;

    const svgName =
            ICON_ALIASES[iconName]
                    ?? iconName;
    
    const sourceSvg =
            join(
                    themeIconsDir,
                    `${svgName}.svg`);

    if (!existsSync(sourceSvg)) {

        console.warn(
                `Missing SVG for icon '${iconName}'`
        );

        missingSvgCount++;

        continue;
    }

    const targetSvg =
            join(
                    outputSvgRoot,
                    `${iconName}.svg`);

    const svg =
            readFileSync(
                    sourceSvg,
                    "utf8");

    writeFileSync(
            targetSvg,
            svg,
            "utf8");

    svgCount++;

    const extensions =
            definition.extensions
                    .map(
                            value =>
                                    value
                                            .toLowerCase()
                                            .trim())
                    .filter(Boolean);

    const files =
            definition.files
                    .map(
                            value =>
                                    value
                                            .toLowerCase()
                                            .trim())
                    .filter(Boolean);

    extensionCount +=
            extensions.length;

    filenameCount +=
            files.length;

    properties.push(
            `icon.${iconName}.extensions=${extensions.join(",")}`);

    properties.push(
            `icon.${iconName}.files=${files.join(",")}`);

    const displayName =
            DISPLAY_NAME_OVERRIDES[iconName]
                    ?? createDisplayName(iconName);
    
    properties.push(
            `icon.${iconName}.displayName=${displayName}`
    );

    properties.push("");
}

const defaultIcon =
        "file";

if (!fileIcons.has(defaultIcon)) {

    const defaultSvg =
            join(
                    themeIconsDir,
                    `${defaultIcon}.svg`);

    if (existsSync(defaultSvg)) {

        writeFileSync(
                join(
                        outputSvgRoot,
                        `${defaultIcon}.svg`),
                readFileSync(
                        defaultSvg,
                        "utf8"),
                "utf8");

        svgCount++;
    }
}

properties.push(
        `# Icon definitions: ${fileIcons.size}`);

properties.push(
        `# SVG files: ${svgCount}`);

properties.push(
        `# Missing SVG files: ${missingSvgCount}`);

writeFileSync(
        join(
                OUTPUT_ROOT,
                "icons.properties"),
        properties.join("\n"),
        "utf8");

console.log("");
console.log(
        "File icon assets generated.");

console.log(
        `Definitions:       ${fileIcons.size}`);

console.log(
        `SVG files:         ${svgCount}`);

console.log(
        `Missing SVGs:      ${missingSvgCount}`);

console.log(
        `Extension mappings: ${extensionCount}`);

console.log(
        `Filename mappings:  ${filenameCount}`);

console.log(
        `Output: ${OUTPUT_ROOT}`);
        
function extractFileIcons(source) {

    const marker =
        "icons: parseByPattern([";

    const markerIndex =
        source.indexOf(marker);

    if (markerIndex < 0) {
        throw new Error(
            "parseByPattern icons array not found in fileIcons.ts");
    }

    const arrayStart =
        source.indexOf(
            "[",
            markerIndex);

    const arrayEnd =
        findMatchingBracket(
            source,
            arrayStart);

    if (arrayEnd < 0) {
        throw new Error(
            "Could not find end of file icon definitions");
    }

    const iconsSource =
        source.substring(
            arrayStart + 1,
            arrayEnd);

    const icons =
        new Map();

    const definitionPattern =
        /\{\s*name:\s*['"]([^'"]+)['"]([\s\S]*?)(?=\n\s*\{\s*name:|\s*$)/g;

    let match;

    while ((match =
            definitionPattern.exec(
                iconsSource))
            !== null) {

        const iconName =
            match[1];

        const body =
            match[2];

        const extensions =
            extractArray(
                body,
                "fileExtensions");

        const files =
            extractArray(
                body,
                "fileNames");

        if (extensions.length === 0
                && files.length === 0) {
            continue;
        }

        icons.set(
            iconName,
            {
                name: iconName,
                extensions,
                files
            });
    }

    return icons;
}

function findMatchingBracket(
        source,
        startIndex) {

    let depth = 0;
    let quote = null;
    let escaped = false;

    for (let i = startIndex;
         i < source.length;
         i++) {

        const character =
            source[i];

        if (quote !== null) {

            if (escaped) {
                escaped = false;
                continue;
            }

            if (character === "\\") {
                escaped = true;
                continue;
            }

            if (character === quote) {
                quote = null;
            }

            continue;
        }

        if (character === "'"
                || character === '"'
                || character === "`") {

            quote = character;
            continue;
        }

        if (character === "[") {
            depth++;
            continue;
        }

        if (character === "]") {

            depth--;

            if (depth === 0) {
                return i;
            }
        }
    }

    return -1;
}

function extractArray(
        source,
        propertyName) {

    const pattern =
        new RegExp(
            propertyName
                + "\\s*:\\s*\\[([\\s\\S]*?)\\]",
            "m");

    const match =
        pattern.exec(source);

    if (!match) {
        return [];
    }

    return [
        ...match[1].matchAll(
            /['"]([^'"]+)['"]/g)
    ].map(
        item => item[1]);
}

function createDisplayName(
        iconName) {

    return iconName
        .replaceAll("-", " ")
        .replaceAll("_", " ")
        .replace(
            /\b\w/g,
            character =>
                character.toUpperCase());
}