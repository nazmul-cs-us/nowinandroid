#!/bin/bash
#
# Upload assets to Cloudflare R2 bucket.
#
# Prerequisites:
#   1. Install wrangler: npm install -g wrangler
#   2. Login: wrangler login
#   3. Create bucket: wrangler r2 bucket create starception-assets
#   4. Enable public access in Cloudflare dashboard:
#      - Go to R2 > starception-assets > Settings
#      - Enable "Public access" (r2.dev subdomain)
#      - Note the public URL: https://pub-XXXXXXXX.r2.dev
#   5. Generate manifest first: python3 scripts/generate_manifest.py --base-url <your-url>
#
# Usage:
#   ./scripts/upload_to_r2.sh                    # Upload all assets
#   ./scripts/upload_to_r2.sh --category hadith  # Upload only hadith
#   ./scripts/upload_to_r2.sh --dry-run          # Preview what would be uploaded
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets"
MANIFEST_FILE="$SCRIPT_DIR/manifest.json"
BUCKET_NAME="starception-assets"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

DRY_RUN=false
CATEGORY_FILTER=""
VERBOSE=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --category)
            CATEGORY_FILTER="$2"
            shift 2
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --bucket)
            BUCKET_NAME="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo "  --dry-run         Preview uploads without executing"
            echo "  --category NAME   Upload only assets in this category"
            echo "  --bucket NAME     R2 bucket name (default: starception-assets)"
            echo "  --verbose         Show detailed output"
            echo "  --help            Show this help"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            exit 1
            ;;
    esac
done

# Check prerequisites
if ! command -v wrangler &> /dev/null; then
    echo -e "${RED}Error: wrangler not found. Install with: npm install -g wrangler${NC}"
    exit 1
fi

if ! command -v python3 &> /dev/null; then
    echo -e "${RED}Error: python3 not found${NC}"
    exit 1
fi

if [ ! -f "$MANIFEST_FILE" ]; then
    echo -e "${YELLOW}Manifest not found. Generating...${NC}"
    python3 "$SCRIPT_DIR/generate_manifest.py" --output "$MANIFEST_FILE"
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Cloudflare R2 Asset Upload${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo -e "Bucket:    ${GREEN}$BUCKET_NAME${NC}"
echo -e "Assets:    ${GREEN}$ASSETS_DIR${NC}"
echo -e "Manifest:  ${GREEN}$MANIFEST_FILE${NC}"
if [ "$DRY_RUN" = true ]; then
    echo -e "Mode:      ${YELLOW}DRY RUN${NC}"
fi
if [ -n "$CATEGORY_FILTER" ]; then
    echo -e "Category:  ${YELLOW}$CATEGORY_FILTER${NC}"
fi
echo ""

# Read manifest and extract assets to upload
# Uses python3 to parse JSON manifest and output upload commands
UPLOAD_LIST=$(python3 -c "
import json, sys

with open('$MANIFEST_FILE') as f:
    manifest = json.load(f)

category_filter = '$CATEGORY_FILTER'
total_size = 0
count = 0

for cdn_key, info in sorted(manifest['assets'].items()):
    if category_filter and info['category'] != category_filter:
        continue
    # Find the local file path by searching for the asset
    local_path = info.get('local_path', cdn_key)
    total_size += info['size']
    count += 1
    size_mb = info['size'] / (1024*1024)
    print(f'{cdn_key}|{info[\"category\"]}|{info[\"size\"]}|{size_mb:.1f}')

print(f'TOTAL|{count}|{total_size}|{total_size/(1024*1024):.1f}', file=sys.stderr)
" 2>&1)

# Separate stderr (totals) from stdout (file list)
TOTAL_LINE=$(echo "$UPLOAD_LIST" | grep "^TOTAL|")
FILE_LIST=$(echo "$UPLOAD_LIST" | grep -v "^TOTAL|")

TOTAL_COUNT=$(echo "$TOTAL_LINE" | cut -d'|' -f2)
TOTAL_SIZE_MB=$(echo "$TOTAL_LINE" | cut -d'|' -f4)

echo -e "Files to upload: ${GREEN}$TOTAL_COUNT${NC}"
echo -e "Total size:      ${GREEN}${TOTAL_SIZE_MB} MB${NC}"
echo ""

if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}DRY RUN - Listing files that would be uploaded:${NC}"
    echo ""
    echo "$FILE_LIST" | while IFS='|' read -r cdn_key category size size_mb; do
        echo -e "  ${GREEN}$cdn_key${NC} (${size_mb} MB) [${BLUE}$category${NC}]"
    done
    echo ""
    echo -e "${YELLOW}Run without --dry-run to upload.${NC}"
    exit 0
fi

# Confirm upload
echo -e "${YELLOW}This will upload $TOTAL_COUNT files ($TOTAL_SIZE_MB MB) to R2.${NC}"
read -p "Continue? (y/N) " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Aborted."
    exit 0
fi

# Upload assets
UPLOADED=0
FAILED=0
SKIPPED=0

# Build reverse mapping from cdn_key to local path
LOCAL_PATHS=$(python3 -c "
import json, os

with open('$MANIFEST_FILE') as f:
    manifest = json.load(f)

assets_dir = '$ASSETS_DIR'
category_filter = '$CATEGORY_FILTER'

for cdn_key, info in sorted(manifest['assets'].items()):
    if category_filter and info['category'] != category_filter:
        continue
    # Reverse the CDN key mapping to find local file
    local = cdn_key
    # json/ prefix -> root
    if local.startswith('json/'):
        local = local[5:]  # Remove json/ prefix
    # models/sherpa/ -> sherpa/
    elif local.startswith('models/sherpa/'):
        local = local[7:]  # Remove models/ prefix
    elif local.startswith('models/kws/'):
        local = local[7:]
    elif local.startswith('models/whisper/'):
        local = local[7:]
    elif local.startswith('models/tts/'):
        local = local[7:]
    elif local.startswith('models/ggml'):
        local = local[0:]  # Keep as-is
    # databases/quran/file.db -> databases/file.db
    elif local.startswith('databases/quran/'):
        local = 'databases/' + os.path.basename(local)

    filepath = os.path.join(assets_dir, local)
    if os.path.exists(filepath):
        print(f'{cdn_key}|{filepath}')
    else:
        print(f'MISSING|{local}|{cdn_key}', __import__('sys').stderr)
")

echo ""
echo -e "${BLUE}Starting upload...${NC}"
echo ""

echo "$LOCAL_PATHS" | while IFS='|' read -r cdn_key local_path; do
    if [ -z "$cdn_key" ] || [ -z "$local_path" ]; then
        continue
    fi

    if [ ! -f "$local_path" ]; then
        echo -e "  ${RED}SKIP${NC} $cdn_key (file not found: $local_path)"
        SKIPPED=$((SKIPPED + 1))
        continue
    fi

    SIZE=$(stat -f%z "$local_path" 2>/dev/null || stat -c%s "$local_path" 2>/dev/null)
    SIZE_MB=$(echo "scale=1; $SIZE / 1048576" | bc)

    echo -ne "  Uploading ${GREEN}$cdn_key${NC} (${SIZE_MB} MB)... "

    # --remote is REQUIRED: without it wrangler (v3+) writes to the LOCAL simulator, not R2.
    if wrangler r2 object put "$BUCKET_NAME/$cdn_key" --file="$local_path" --content-type="application/octet-stream" --remote 2>/dev/null; then
        echo -e "${GREEN}OK${NC}"
        UPLOADED=$((UPLOADED + 1))
    else
        echo -e "${RED}FAILED${NC}"
        FAILED=$((FAILED + 1))
    fi
done

# Upload manifest itself
echo -ne "  Uploading ${GREEN}manifest.json${NC}... "
if wrangler r2 object put "$BUCKET_NAME/manifest.json" --file="$MANIFEST_FILE" --content-type="application/json" --remote 2>/dev/null; then
    echo -e "${GREEN}OK${NC}"
else
    echo -e "${RED}FAILED${NC}"
fi

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Upload Complete${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "  Uploaded: ${GREEN}$UPLOADED${NC}"
echo -e "  Failed:   ${RED}$FAILED${NC}"
echo -e "  Skipped:  ${YELLOW}$SKIPPED${NC}"
echo ""
echo -e "${YELLOW}Next steps:${NC}"
echo "  1. Verify public access at your R2 URL"
echo "  2. Update base_url in manifest.json with your actual R2 public URL"
echo "  3. Re-run: python3 scripts/generate_manifest.py --base-url https://pub-XXXXX.r2.dev"
echo "  4. Re-upload manifest: wrangler r2 object put $BUCKET_NAME/manifest.json --file=$MANIFEST_FILE"
