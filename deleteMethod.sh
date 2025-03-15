#!/bin/bash

# This script provides functionality to delete a method or class from a source code file
# by identifying its start and end lines based on brace matching.
#
# Usage: ./deleteMethod.sh <method_name> <file_name>
# Example: ./deleteMethod.sh "myMethod" "MyClass.java"
#
# The script handles:
# - Methods with multi-line arguments
# - Nested braces within method bodies
# - Methods with annotations (removes the annotation line as well)
# - Both method and class definitions

# Function to count braces and determine the start and end lines of a method/class
# Args:
#   $1: The method or class name to search for
#   $2: The file to search in
# Returns:
#   Prints a line in format "START:line_number | END:line_number"
count_braces() {
    local method="$1"
    local file="$2"

    perl -ne '
        # Initialize variables for tracking state
        BEGIN { $count = 0; $found = 0; $in_method = 0; $start_line = 0; }

        # Find the method or class name and ignore leading spaces
        # Matches the pattern at the start of line (ignoring whitespace)
        # Also handles cases where there might be parentheses after the name
        if (!$in_method && /^\s*\b'"$method"'\s*(\(\s*\))?/) {
            $in_method = 1;
            $start_line = $.;  # Store current line number
        }

        # Handle multi-line method signatures by waiting for opening brace
        if ($in_method) {
            if (/.*\{/) {
                $found = 1;
                $in_method = 0;
            }
        }

        # Track brace count to handle nested braces
        # When count returns to 0, we have found the end of the method/class
        if ($found) {
            $count += tr/{/{/;  # Count opening braces
            $count -= tr/}/}/;  # Count closing braces
            if ($count == 0) {
                print "START:$start_line | END:$.\n";
                exit;
            }
        }
    ' "$file"
}

# Function to delete a method or class from a file
# Args:
#   $1: The method or class name to delete
#   $2: The file to modify
# Side effects:
#   Modifies the input file by removing the specified method/class
delete_method() {
    local method="$1"
    local file="$2"

    local line_info
    local start_line
    local end_line

    # Get the line numbers for the method/class
    line_info=$(count_braces "$method" "$file")
    start_line=$(echo "$line_info" | cut -d':' -f2 | cut -d' ' -f1)
    end_line=$(echo "$line_info" | cut -d':' -f3)

    # Only proceed if we found valid line numbers
    if [ -n "$start_line" ] && [ -n "$end_line" ]; then
        # Subtract 1 from start_line to include any annotation that comes before
        start_line=$((start_line - 1))
        # Use sed to delete the lines from the file
        sed -i '' "${start_line},${end_line}d" "$file"
        echo "Deleted method/class '$method' from $file (Lines: $start_line - $end_line)"
    else
        echo "Method/Class '$method' not found in $file"
    fi
}

# Script entry point
# Verify correct number of arguments
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <method_name> <file_name>"
    exit 1
fi

# Execute the delete_method function with provided arguments
delete_method "$1" "$2"