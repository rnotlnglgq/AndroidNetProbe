#!/usr/bin/env python3
"""Check JNI marshaling with synthetic ifaddrs on Linux; does not test Android permissions."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import re
root=Path(__file__).resolve().parent.parent
java=shutil.which('java')
if not java: raise SystemExit('JDK required')
settings=subprocess.run([java,'-XshowSettings:properties','-version'],capture_output=True,text=True,check=True)
match=re.search(r'^\s*java.home = (.+)$',settings.stderr,re.M)
if not match: raise SystemExit('Cannot locate JDK headers')
java_root=Path(match.group(1).strip())
javac=java_root/'bin/javac'
include=java_root/'include'
if not (include/'jni.h').is_file(): raise SystemExit('A full JDK with JNI headers is required')
with tempfile.TemporaryDirectory(prefix='netprobe-native-') as work:
    work=Path(work); library=work/'libnetprobe_fixture.so'
    subprocess.run([os.environ.get('CC','cc'),'-shared','-fPIC','-std=c11','-D_GNU_SOURCE','-Wall','-Wextra','-Werror',
        '-I'+str(include),'-I'+str(include/'linux'),str(root/'app/src/testNative/fixture.c'),'-o',str(library)],check=True)
    subprocess.run([str(javac),'-d',str(work),str(root/'app/src/testNative/NativeSmoke.java')],check=True)
    for mode in ['normal','denied','overflow']:
        subprocess.run([str(javac.parent/'java'),'-Xcheck:jni','-cp',str(work),
            'com.rnotlnglgq.netprobe.probe.NativeSmoke',str(library)],env=dict(os.environ,NETPROBE_NATIVE_FIXTURE=mode),check=True)
