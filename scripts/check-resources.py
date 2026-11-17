#!/usr/bin/env python3
"""Check bilingual resources and keep new UI prose in Android resources."""
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = ROOT / 'app/src/main/res'

def entries(folder):
    root = ET.parse(RESOURCE_ROOT / folder / 'strings.xml').getroot()
    result = {}
    for entry in root:
        if entry.attrib.get('translatable') == 'false':
            continue
        key = entry.attrib['name']
        assert key not in result, f'Duplicate resource: {key}'
        texts = [item.text or '' for item in entry] if entry.tag == 'plurals' else [entry.text or '']
        signatures = {tuple(sorted(set(re.findall(r'%\d+\$[sd]', text)))) for text in texts}
        assert len(signatures) == 1, f'Plural parameters differ: {key}'
        result[key] = signatures.pop()
    return result

def main():
    english = entries('values')
    chinese = entries('values-b+zh+Hans')
    assert english == chinese, 'Resource keys or format parameters differ'
    ui = ROOT / 'app/src/main/java/com/rnotlnglgq/netprobe/ui'
    for path in ui.glob('*.kt'):
        assert not re.search(r'[\u4e00-\u9fff]', path.read_text()), f'Move UI prose to resources: {path.name}'
    print(f'PASS bilingual keys and parameters: {len(english)} resources; UI prose uses resource references')

if __name__ == '__main__':
    main()
