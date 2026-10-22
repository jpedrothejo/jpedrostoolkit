import urllib.request
import re

try:
    css_url = 'https://jpedrothejo.github.io/scripts/style.css'
    req = urllib.request.urlopen(css_url)
    css = req.read().decode('utf-8')
    print('CSS length:', len(css))
    for match in re.findall(r'url\(([^)]+)\)', css):
        print('BG URL:', match)
    for line in css.splitlines():
        if 'background' in line or 'image' in line or 'url' in line:
            print(line)
except Exception as e:
    print('Error:', e)
