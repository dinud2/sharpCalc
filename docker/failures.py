#!/usr/bin/env python3
"""Print failing tests from the Gradle XML reports (engine module)."""
import glob, re, sys
import xml.etree.ElementTree as ET
n = 0
for f in glob.glob('engine/build/test-results/test/*.xml'):
    for tc in ET.parse(f).getroot().iter('testcase'):
        for fl in list(tc.iter('failure')) + list(tc.iter('error')):
            n += 1
            txt = (fl.get('message') or '') + '\n' + '\n'.join(l for l in (fl.text or '').split('\n')[1:12] if 'sharpcalc' in l)
            print('FAIL', tc.get('name'), '::', txt[:int(sys.argv[1]) if len(sys.argv) > 1 else 700])
print(n, 'failures')
