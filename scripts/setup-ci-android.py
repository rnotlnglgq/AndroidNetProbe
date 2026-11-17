#!/usr/bin/env python3
"""Install the project's fixed SDK components on a GitHub Ubuntu runner."""
import os
from pathlib import Path
import subprocess

PACKAGES = ['platform-tools', 'platforms;android-37.0', 'build-tools;36.0.0',
            'ndk;25.1.8937393', 'cmake;3.22.1']

if __name__ == '__main__':
    sdk = Path(os.environ['ANDROID_HOME'])
    manager = sdk / 'cmdline-tools/latest/bin/sdkmanager'
    if not manager.is_file():
        raise SystemExit(f'Android command-line tools required: {manager}')
    subprocess.run([str(manager), '--licenses'], input='y\n' * 100, text=True, check=True)
    subprocess.run([str(manager), '--install', *PACKAGES], check=True)
