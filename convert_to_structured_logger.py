#!/usr/bin/env python3
"""
Convert Timber/Android Log to StructuredLogger across the entire project.
Removes unnecessary verbose/debug logs.
"""

import re
import os
import sys
from pathlib import Path
from typing import List, Tuple

# Patterns to identify unnecessary logs
UNNECESSARY_PATTERNS = [
    r'============.*============',  # Decorative separators
    r'\[DEBUG\]',  # Debug markers
    r'println',  # Print statements
    r'Log\.v\(',  # Verbose logs (usually too noisy)
    r'TODO|FIXME',  # Development markers
    r'Testing|Debugging',  # Test logs
    r'All keys in',  # Internal debug info
    r'Inclusion states JSON',  # Internal state dumps
]

class LogConverter:
    def __init__(self, file_path: str):
        self.file_path = file_path
        self.content = ""
        self.class_name = ""
        self.feature_tag = ""
        self.changes_made = []

    def read_file(self):
        """Read file content"""
        with open(self.file_path, 'r', encoding='utf-8') as f:
            self.content = f.read()

    def extract_class_info(self):
        """Extract class name and determine feature tag"""
        # Extract class name
        class_match = re.search(r'class\s+(\w+)', self.content)
        if class_match:
            self.class_name = class_match.group(1)
        else:
            # Use filename if no class found
            self.class_name = Path(self.file_path).stem

        # Determine feature tag based on package/class name
        path = self.file_path.lower()
        if 'dashboard' in path:
            self.feature_tag = 'LogConfig.FeatureTags.DASHBOARD'
        elif 'message' in path or 'sms' in path:
            self.feature_tag = 'LogConfig.FeatureTags.SMS'
        elif 'transaction' in path:
            self.feature_tag = 'LogConfig.FeatureTags.TRANSACTION'
        elif 'categor' in path:
            self.feature_tag = 'LogConfig.FeatureTags.CATEGORIES'
        elif 'merchant' in path:
            self.feature_tag = 'LogConfig.FeatureTags.MERCHANT'
        elif 'insight' in path:
            self.feature_tag = 'LogConfig.FeatureTags.INSIGHTS'
        elif 'database' in path or 'dao' in path or 'repository' in path:
            self.feature_tag = 'LogConfig.FeatureTags.DATABASE'
        elif 'network' in path or 'api' in path:
            self.feature_tag = 'LogConfig.FeatureTags.NETWORK'
        elif 'migration' in path:
            self.feature_tag = 'LogConfig.FeatureTags.MIGRATION'
        else:
            self.feature_tag = 'LogConfig.FeatureTags.APP'

    def should_remove_log(self, log_line: str) -> bool:
        """Check if log should be removed (unnecessary)"""
        for pattern in UNNECESSARY_PATTERNS:
            if re.search(pattern, log_line, re.IGNORECASE):
                return True
        return False

    def extract_method_name(self, line_num: int) -> str:
        """Find the method name for a given line number"""
        lines = self.content.split('\n')

        # Search backwards from current line to find method definition
        for i in range(line_num - 1, max(0, line_num - 50), -1):
            line = lines[i]
            # Match: fun methodName( or private fun methodName(
            match = re.search(r'fun\s+(\w+)\s*\(', line)
            if match:
                return match.group(1)

        return "unknown"

    def convert_log_call(self, match_obj, line_num: int) -> str:
        """Convert a single log call to StructuredLogger format"""
        full_match = match_obj.group(0)

        # Check if this log should be removed
        if self.should_remove_log(full_match):
            self.changes_made.append(f"Line {line_num}: REMOVED unnecessary log")
            return ""  # Remove this log

        # Extract log level and message
        # Handle Timber.tag().d/e/i/w calls
        timber_match = re.search(r'Timber\.tag\([^)]+\)\.([deiwv])\((.*)\)', full_match)
        if timber_match:
            level = timber_match.group(1)
            message = timber_match.group(2)

            # Extract method name
            method_name = self.extract_method_name(line_num)

            # Convert level to method name
            level_map = {'d': 'debug', 'e': 'error', 'i': 'info', 'w': 'warn', 'v': 'debug'}
            method = level_map.get(level, 'debug')

            # Handle exception in error logs
            if method == 'error' and ',' in message:
                parts = message.split(',', 1)
                exception = parts[0].strip()
                msg = parts[1].strip() if len(parts) > 1 else '""'
                converted = f'logger.{method}("{method_name}", {msg}, {exception})'
            else:
                converted = f'logger.{method}("{method_name}", {message})'

            self.changes_made.append(f"Line {line_num}: Converted Timber.{level}() to logger.{method}()")
            return converted

        # Handle Log.d/e/i/w calls
        log_match = re.search(r'Log\.([deiwv])\("([^"]+)",\s*(.*)\)', full_match)
        if log_match:
            level = log_match.group(1)
            tag = log_match.group(2)
            message = log_match.group(3)

            # Extract method name
            method_name = self.extract_method_name(line_num)

            # Convert level to method name
            level_map = {'d': 'debug', 'e': 'error', 'i': 'info', 'w': 'warn', 'v': 'debug'}
            method = level_map.get(level, 'debug')

            # Handle exception parameter
            if method == 'error' and message.count(',') >= 1:
                parts = message.rsplit(',', 1)
                msg = parts[0].strip()
                exception = parts[1].strip()
                converted = f'logger.{method}("{method_name}", {msg}, {exception})'
            else:
                converted = f'logger.{method}("{method_name}", {message})'

            self.changes_made.append(f"Line {line_num}: Converted Log.{level}() to logger.{method}()")
            return converted

        return full_match  # No conversion

    def process_content(self):
        """Process and convert all log statements"""
        lines = self.content.split('\n')
        new_lines = []

        for line_num, line in enumerate(lines, 1):
            # Check for Timber or Log calls
            if 'Timber.tag' in line or re.search(r'Log\.[deiwv]\(', line):
                # Replace the log call
                new_line = re.sub(
                    r'(Timber\.tag\([^)]+\)\.[deiwv]\([^)]*\)|Log\.[deiwv]\([^)]*\))',
                    lambda m: self.convert_log_call(m, line_num),
                    line
                )

                # Skip empty lines (removed logs)
                if new_line.strip():
                    new_lines.append(new_line)
            else:
                new_lines.append(line)

        self.content = '\n'.join(new_lines)

    def update_imports(self):
        """Update imports to use StructuredLogger"""
        # Remove Timber import
        self.content = re.sub(r'import timber\.log\.Timber\n', '', self.content)

        # Remove Android Log import
        self.content = re.sub(r'import android\.util\.Log\n', '', self.content)

        # Add StructuredLogger import if not present
        if 'import com.expensemanager.app.utils.logging.StructuredLogger' not in self.content:
            # Find first import and add before it
            import_match = re.search(r'^import ', self.content, re.MULTILINE)
            if import_match:
                pos = import_match.start()
                self.content = (
                    self.content[:pos] +
                    'import com.expensemanager.app.utils.logging.StructuredLogger\n' +
                    self.content[pos:]
                )

    def add_logger_instance(self):
        """Add logger instance to class if not present"""
        if 'private val logger = StructuredLogger' in self.content or 'private val logger:' in self.content:
            return  # Already has logger

        # Find class body start
        class_match = re.search(r'class\s+\w+[^{]*\{', self.content)
        if not class_match:
            return

        insert_pos = class_match.end()

        # Create logger instance
        logger_code = f'\n    private val logger = StructuredLogger({self.feature_tag}, "{self.class_name}")\n'

        self.content = self.content[:insert_pos] + logger_code + self.content[insert_pos:]
        self.changes_made.append(f"Added logger instance to {self.class_name}")

    def write_file(self):
        """Write converted content back to file"""
        with open(self.file_path, 'w', encoding='utf-8') as f:
            f.write(self.content)

    def convert(self) -> bool:
        """Main conversion method"""
        try:
            self.read_file()

            # Skip if already using StructuredLogger
            if 'private val logger = StructuredLogger' in self.content:
                return False

            # Skip if no logging
            if 'Timber' not in self.content and 'Log.d' not in self.content and 'Log.e' not in self.content:
                return False

            self.extract_class_info()
            self.process_content()
            self.update_imports()
            self.add_logger_instance()
            self.write_file()

            return True
        except Exception as e:
            print(f"Error processing {self.file_path}: {e}")
            return False


def find_kotlin_files(root_dir: str) -> List[str]:
    """Find all Kotlin files in directory"""
    kotlin_files = []
    for root, dirs, files in os.walk(root_dir):
        for file in files:
            if file.endswith('.kt'):
                kotlin_files.append(os.path.join(root, file))
    return kotlin_files


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 convert_to_structured_logger.py <project_directory>")
        sys.exit(1)

    project_dir = sys.argv[1]

    if not os.path.isdir(project_dir):
        print(f"Error: Directory not found: {project_dir}")
        sys.exit(1)

    print(f"Converting logging in: {project_dir}")
    print("=" * 80)

    kotlin_files = find_kotlin_files(project_dir)
    print(f"Found {len(kotlin_files)} Kotlin files")

    converted_count = 0
    skipped_count = 0

    for file_path in kotlin_files:
        converter = LogConverter(file_path)
        if converter.convert():
            converted_count += 1
            print(f"\n✓ Converted: {os.path.relpath(file_path, project_dir)}")
            for change in converter.changes_made:
                print(f"    {change}")
        else:
            skipped_count += 1

    print("\n" + "=" * 80)
    print(f"Conversion complete!")
    print(f"  - Converted: {converted_count} files")
    print(f"  - Skipped: {skipped_count} files")


if __name__ == "__main__":
    main()
