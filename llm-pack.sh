#!/bin/bash

# llm-pack (Nexus Pulse Edition)
# Combines project structure and file contents into a single text file for LLMs.
# Optimized for: Spring Boot 3, Angular 17, AWS CDK.

set -euo pipefail

# --- CONFIGURATION ---
# Output to Desktop by default, easier to find
OUTPUT_DIR="$HOME/Desktop"
PROJECT_NAME="$(basename "$PWD")"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT_FILE="${OUTPUT_DIR}/${PROJECT_NAME}_Context_${TIMESTAMP}.txt"
MAX_FILE_SIZE_KB=100

# STRICT IGNORE PATTERNS
# These files are explicitly excluded to reduce noise and token usage.
IGNORE_PATTERNS=(
    # Version Control & IDEs
    ".git" ".DS_Store" ".idea" ".vscode"
    
    # Java / Spring Boot (Backend)
    "target" ".mvn" "mvnw" "mvnw.cmd" "*.jar" "*.class" "*.war"
    
    # Node / Angular / CDK (Frontend & Infra)
    "node_modules" "dist" "cdk.out" ".angular"
    
    # Secrets & Environment
    ".env" ".env.local" ".env.*"
    
    # Logs
    "*.log" "npm-debug.log*" "yarn-debug.log*"
    
    # Assets (Binary files)
    "*.png" "*.jpg" "*.jpeg" "*.gif" "*.svg" "*.pdf" "*.ico"
    
    # Lock Files (Too verbose for LLM context, usually not needed)
    "package-lock.json" "yarn.lock" "pnpm-lock.yaml"
)

# --- FUNCTIONS ---

is_binary() {
    # Check mime type to prevent printing garbage characters
    # If 'file' command is missing (some Windows git bash), assume text if not in ignore list
    if ! command -v file &> /dev/null; then
        return 0
    fi
    
    local mime
    mime=$(file -b --mime-type "$1")
    [[ "$mime" == text/* || "$mime" == application/json || "$mime" == application/xml || "$mime" == inode/x-empty ]] && return 0
    return 1
}

generate_tree() {
    echo "=========================================" >> "$OUTPUT_FILE"
    echo "           PROJECT STRUCTURE             " >> "$OUTPUT_FILE"
    echo "=========================================" >> "$OUTPUT_FILE"
    
    # Use 'tree' if available (brew install tree / sudo apt install tree)
    if command -v tree &> /dev/null; then
        local ignore_str
        ignore_str=$(IFS='|'; echo "${IGNORE_PATTERNS[*]}")
        # -I ignores patterns, --prune makes empty dirs disappear
        tree -I "$ignore_str" --prune >> "$OUTPUT_FILE"
    else
        # Fallback to 'find' (Windows Git Bash friendly)
        # Excludes .git and uses slightly cleaner formatting
        find . -maxdepth 4 -not -path '*/.*' -not -path './target*' -not -path './node_modules*' -not -path './cdk.out*' | sed 's|[^/]*/|- |g' >> "$OUTPUT_FILE"
    fi
    echo -e "\n" >> "$OUTPUT_FILE"
}

process_file() {
    local file="$1"
    
    [[ ! -f "$file" ]] && return

    local size_kb
    size_kb=$(($(wc -c < "$file") / 1024))
    if [[ $size_kb -gt $MAX_FILE_SIZE_KB ]]; then
        echo "Skipping large file: $file (${size_kb}KB)"
        return
    fi

    if ! is_binary "$file"; then
        echo "Skipping binary file: $file"
        return
    fi

    echo "-----------------------------------------" >> "$OUTPUT_FILE"
    echo "FILE: $file" >> "$OUTPUT_FILE"
    echo "-----------------------------------------" >> "$OUTPUT_FILE"
    cat "$file" >> "$OUTPUT_FILE"
    echo -e "\n\n" >> "$OUTPUT_FILE"
}

# --- EXECUTION ---

echo "📦 Packing Nexus Pulse context..."

# Ensure output dir exists
mkdir -p "$OUTPUT_DIR"

{
    echo "# Nexus Pulse Context Pack"
    echo "# Date: $(date)"
    echo "# Content: Spring Boot (Backend), Angular (Frontend), AWS CDK (Infra)"
    echo "# Ignored: target/, dist/, cdk.out/, node_modules/, binary assets"
    echo ""
} > "$OUTPUT_FILE"

generate_tree

echo "=========================================" >> "$OUTPUT_FILE"
echo "             FILE CONTENTS               " >> "$OUTPUT_FILE"
echo "=========================================" >> "$OUTPUT_FILE"

# STRATEGY: Use git ls-files to respect .gitignore, then filter with our custom list
if git rev-parse --is-inside-work-tree > /dev/null 2>&1; then
    echo "✅ Using Git to track relevant files..."
    git ls-files | while read -r file; do
        should_process=true
        
        # Check against manual ignore patterns
        for pattern in "${IGNORE_PATTERNS[@]}"; do
            # Wildcard matching (support *.jar etc)
            if [[ "$file" == $pattern || "$file" == *"/$pattern" ]]; then
                should_process=false
                break
            fi
        done
        
        if $should_process; then
             process_file "$file"
        fi
    done
else
    echo "⚠️ Not a git repo. Using standard find (might include unwanted files)..."
    find . -type f -not -path '*/.*' | while read -r file; do
        clean_file="${file#./}"
        process_file "$clean_file"
    done
fi

echo "✅ Done! Context saved to: $OUTPUT_FILE"