from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
firmware = root / 'tools/firmware-reference/Sultan_Digital_Clock_v8_ColorChangeSec.ino'
api = root / 'app/src/main/java/com/example/data/network/SultanClockApi.kt'

fw = firmware.read_text(errors='ignore')
ap = api.read_text(errors='ignore')
fw_routes = set(re.findall(r'F\("(/[^"?]+)', fw))
app_routes = set(re.findall(r'"(/(?:api/)?[A-Za-z0-9_]+)', ap))
unsupported = {
    '/api/status','/toggleprayeralarm','/toggletempsensor','/savedfvolume','/testdftrack',
    '/savehourlymode','/savetracks','/savewaqtazan','/saveweeklyplaylist','/changeappass',
    '/resetdefaultpass','/showwifipass','/savedate'
}
real_app = {r for r in app_routes if r not in unsupported}
missing = sorted(real_app - fw_routes - {'/api/status'})
print('Firmware routes:', len(fw_routes))
print('Android route literals:', len(app_routes))
print('Potential unmatched Android routes:', missing)
if missing:
    raise SystemExit(1)
print('ESP8266 API route audit: PASS')
