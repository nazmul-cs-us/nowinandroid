#!/bin/bash

# Prayer Logs Pull Script
# This script pulls prayer/adhan debug logs from the Android device
#
# Usage:
#   ./pull_prayer_logs.sh              # Pull all logs
#   ./pull_prayer_logs.sh today        # Pull only today's logs
#   ./pull_prayer_logs.sh 2024-12-11   # Pull logs for specific date

PACKAGE="com.starception.submission.demo.debug"
LOG_DIR="prayer_logs"
OUTPUT_DIR="./prayer_logs_export"
DEVICE_ID="4B221FDAP002T6"  # Pixel 9 Pro device

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   Prayer Logs Pull Script${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check if device is connected
if ! adb devices | grep -q "$DEVICE_ID"; then
    echo -e "${RED}Error: Device $DEVICE_ID not connected${NC}"
    echo "Connected devices:"
    adb devices
    exit 1
fi

echo -e "${GREEN}Device connected: $DEVICE_ID${NC}"
echo ""

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Get the base path for logs on device
DEVICE_LOG_PATH="/data/data/$PACKAGE/files/$LOG_DIR"

echo -e "${YELLOW}Checking for logs at: $DEVICE_LOG_PATH${NC}"
echo ""

# List available log directories
echo -e "${GREEN}Available log directories:${NC}"
adb -s "$DEVICE_ID" shell "run-as $PACKAGE ls -la files/$LOG_DIR 2>/dev/null" || {
    echo -e "${RED}No logs found or permission denied${NC}"
    echo ""
    echo "Trying alternative method..."

    # Alternative: Use adb backup (requires debugging enabled)
    echo -e "${YELLOW}Attempting to read logs via run-as...${NC}"
    adb -s "$DEVICE_ID" shell "run-as $PACKAGE cat files/$LOG_DIR/*/prayer_log.txt 2>/dev/null"
    exit 1
}

echo ""

# Determine which logs to pull
DATE_FILTER="$1"

if [ -z "$DATE_FILTER" ]; then
    echo -e "${GREEN}Pulling all available logs...${NC}"
    # Get all log directories
    LOG_DIRS=$(adb -s "$DEVICE_ID" shell "run-as $PACKAGE ls files/$LOG_DIR 2>/dev/null")
elif [ "$DATE_FILTER" == "today" ]; then
    TODAY=$(date +"%Y-%m-%d")
    echo -e "${GREEN}Pulling today's logs ($TODAY)...${NC}"
    LOG_DIRS=$(adb -s "$DEVICE_ID" shell "run-as $PACKAGE ls files/$LOG_DIR 2>/dev/null" | grep "$TODAY")
else
    echo -e "${GREEN}Pulling logs for $DATE_FILTER...${NC}"
    LOG_DIRS=$(adb -s "$DEVICE_ID" shell "run-as $PACKAGE ls files/$LOG_DIR 2>/dev/null" | grep "$DATE_FILTER")
fi

if [ -z "$LOG_DIRS" ]; then
    echo -e "${RED}No logs found matching criteria${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Found log directories:${NC}"
echo "$LOG_DIRS"
echo ""

# Pull each log directory
for dir in $LOG_DIRS; do
    dir=$(echo "$dir" | tr -d '\r')  # Remove carriage returns
    if [ -n "$dir" ]; then
        echo -e "${YELLOW}Pulling: $dir${NC}"

        # Create local directory
        mkdir -p "$OUTPUT_DIR/$dir"

        # Read and save log file
        adb -s "$DEVICE_ID" shell "run-as $PACKAGE cat files/$LOG_DIR/$dir/prayer_log.txt 2>/dev/null" > "$OUTPUT_DIR/$dir/prayer_log.txt"

        # Check if file was pulled successfully
        if [ -s "$OUTPUT_DIR/$dir/prayer_log.txt" ]; then
            LINE_COUNT=$(wc -l < "$OUTPUT_DIR/$dir/prayer_log.txt")
            echo -e "  ${GREEN}✓ Saved: $OUTPUT_DIR/$dir/prayer_log.txt ($LINE_COUNT lines)${NC}"
        else
            echo -e "  ${RED}✗ Empty or failed: $dir${NC}"
            rm -f "$OUTPUT_DIR/$dir/prayer_log.txt"
            rmdir "$OUTPUT_DIR/$dir" 2>/dev/null
        fi
    fi
done

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}   Export Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Logs saved to: ${YELLOW}$OUTPUT_DIR${NC}"
echo ""

# Show summary
echo -e "${GREEN}Log Summary:${NC}"
for log_file in "$OUTPUT_DIR"/**/prayer_log.txt; do
    if [ -f "$log_file" ]; then
        DIR_NAME=$(dirname "$log_file" | xargs basename)
        LINE_COUNT=$(wc -l < "$log_file")
        SIZE=$(ls -lh "$log_file" | awk '{print $5}')
        echo "  $DIR_NAME: $LINE_COUNT lines, $SIZE"
    fi
done

echo ""
echo -e "${YELLOW}Quick Analysis Tips:${NC}"
echo "  - Search for ADHAN_SCHEDULED:  grep 'ADHAN_SCHEDULED' $OUTPUT_DIR/*/prayer_log.txt"
echo "  - Search for ADHAN_FIRED:      grep 'ADHAN_FIRED' $OUTPUT_DIR/*/prayer_log.txt"
echo "  - Search for errors:           grep 'ERROR' $OUTPUT_DIR/*/prayer_log.txt"
echo "  - Search for prayer detection: grep 'PRAYER_DETECTION' $OUTPUT_DIR/*/prayer_log.txt"
echo ""
