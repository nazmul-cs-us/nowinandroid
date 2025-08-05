#!/bin/bash

# Script to update package name from com.google.samples.apps.nowinandroid to com.starception.dua

echo "Updating package structure..."

# Function to move files from old package structure to new one
move_package_files() {
    local module_path=$1
    local old_package="com/google/samples/apps/nowinandroid"
    local new_package="com/starception/dua"
    
    if [ -d "$module_path/src/main/kotlin/$old_package" ]; then
        echo "Moving files in $module_path"
        mkdir -p "$module_path/src/main/kotlin/$new_package"
        cp -r "$module_path/src/main/kotlin/$old_package/"* "$module_path/src/main/kotlin/$new_package/" 2>/dev/null || true
        rm -rf "$module_path/src/main/kotlin/$old_package"
    fi
    
    if [ -d "$module_path/src/test/kotlin/$old_package" ]; then
        echo "Moving test files in $module_path"
        mkdir -p "$module_path/src/test/kotlin/$new_package"
        cp -r "$module_path/src/test/kotlin/$old_package/"* "$module_path/src/test/kotlin/$new_package/" 2>/dev/null || true
        rm -rf "$module_path/src/test/kotlin/$old_package"
    fi
    
    if [ -d "$module_path/src/androidTest/kotlin/$old_package" ]; then
        echo "Moving androidTest files in $module_path"
        mkdir -p "$module_path/src/androidTest/kotlin/$new_package"
        cp -r "$module_path/src/androidTest/kotlin/$old_package/"* "$module_path/src/androidTest/kotlin/$new_package/" 2>/dev/null || true
        rm -rf "$module_path/src/androidTest/kotlin/$old_package"
    fi
}

# Move files in core modules
for module in core/*/; do
    if [ -d "$module" ]; then
        move_package_files "$module"
    fi
done

# Move files in feature modules
for module in feature/*/; do
    if [ -d "$module" ]; then
        move_package_files "$module"
    fi
done

# Move files in sync modules
for module in sync/*/; do
    if [ -d "$module" ]; then
        move_package_files "$module"
    fi
done

# Move files in other modules
for module in benchmarks/ ui-test-hilt-manifest/; do
    if [ -d "$module" ]; then
        move_package_files "$module"
    fi
done

echo "Package structure update complete!" 