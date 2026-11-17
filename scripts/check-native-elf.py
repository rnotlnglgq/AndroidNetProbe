#!/usr/bin/env python3
"""Check our APK's native LOAD/RELRO layout for both 4KiB and 16KiB pages. No device needed."""
from pathlib import Path
import argparse
import os
import re
import subprocess
import tempfile
import zipfile

def covered(start, end, intervals):
    cursor=start
    for lo,hi in sorted(intervals):
        if hi <= cursor: continue
        if lo > cursor: return False
        cursor=max(cursor,hi)
        if cursor >= end: return True
    return cursor >= end

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('--readelf',type=Path)
    parser.add_argument('--distribution',choices=['universal','arm64-v8a','armeabi-v7a'],default='universal')
    args=parser.parse_args()
    root=Path(__file__).resolve().parent.parent
    sdk=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if not sdk:
        text=(root/'local.properties').read_text()
        sdk=re.search(r'^sdk.dir=(.+)$',text,re.M).group(1).strip()
    readelf=args.readelf or Path(sdk)/'ndk/25.1.8937393/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf'
    # Known crash regression: RELRO crossed two unmapped 4KiB pages although LOAD alignment was 16KiB.
    assert not covered(0x5000,0x8000,[(0,0x2000),(0x5000,0x6000)])
    assert covered(0x5000,0x6000,[(0,0x2000),(0x5000,0x6000)])
    with zipfile.ZipFile(args.apk) as apk,tempfile.TemporaryDirectory(prefix='netprobe-elf-') as tmp:
        names=[n for n in apk.namelist() if re.fullmatch(r'lib/(armeabi-v7a|arm64-v8a|x86|x86_64)/libnetprobe_bionic.so',n)]
        expected={'armeabi-v7a','arm64-v8a','x86','x86_64'} if args.distribution=='universal' else {args.distribution}
        assert {name.split('/')[1] for name in names}==expected,'Unexpected native ABI set'
        for name in names:
            abi=name.split('/')[1]; path=Path(tmp)/f'{abi}.so';path.write_bytes(apk.read(name))
            text=subprocess.run([str(readelf),'-lW',str(path)],capture_output=True,text=True,check=True).stdout
            rows=[line.split() for line in text.splitlines()]
            loads=[r for r in rows if r and r[0]=='LOAD'];relro=[r for r in rows if r and r[0]=='GNU_RELRO']
            assert loads and relro, f'{abi}: missing LOAD/RELRO'
            assert all(int(r[-1],16)>=16384 for r in loads),f'{abi}: LOAD alignment below16KiB'
            for page in [4096,16384]:
                floor=lambda x:x//page*page
                ceil=lambda x:(x+page-1)//page*page
                intervals=[(floor(int(r[2],16)),ceil(int(r[2],16)+int(r[5],16))) for r in loads]
                for r in relro:
                    lo=floor(int(r[2],16));hi=ceil(int(r[2],16)+int(r[5],16))
                    assert covered(lo,hi,intervals),f'{abi}: RELRO extends outside LOAD on {page}-byte pages'
            print(f'PASS {abi}: 16KiB LOAD alignment, RELRO mapped at 4KiB/16KiB')

if __name__=='__main__':main()
