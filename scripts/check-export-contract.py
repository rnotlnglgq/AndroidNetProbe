#!/usr/bin/env python3
"""Validate generated exports and the checked-in synthetic examples with JSON Schema."""
import json
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[1]

def main():
    schema = json.loads((ROOT / 'samples/schema-1.0.json').read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    generated = ROOT / 'app/build/contract-exports'
    for name in ('example-snapshot.json', 'example-redacted.json'):
        for folder in (generated, ROOT / 'samples'):
            path = folder / name
            document = json.loads(path.read_text())
            validator.validate(document)
            snapshots = [document['snapshot']]
            if document['permissionComparison']:
                snapshots += [document['permissionComparison'][key] for key in ('before', 'after')]
            for snapshot in snapshots:
                fields = snapshot['fieldResults']
                assert len({field['path'] for field in fields}) == len(fields), path
                if document['exportMode'] == 'redacted':
                    assert all(field['value'] is None for field in fields if field['sensitive']), path
            if folder == generated:
                assert document == json.loads((ROOT / 'samples' / name).read_text()), f'Update synthetic fixture: {name}'
            print(f'PASS {path.relative_to(ROOT)}')

if __name__ == '__main__':
    main()
