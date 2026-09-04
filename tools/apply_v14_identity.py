from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
main_path = ROOT / "app/src/main/java/com/marshall/stockai/MainActivity.java"
manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
gradle_path = ROOT / "app/build.gradle.kts"

s = main_path.read_text(encoding="utf-8")
s = s.replace("Android v1.3 · 통합 투자분석 + 가상투자", "✅ 실행 중: MarshallStock v1.4 SECTOR FIX")
s = s.replace('page("상승 기대 섹터",true)', 'page("상승 기대 섹터 · v1.4",true)')
s = s.replace('showLoading("상승 기대 섹터",', 'showLoading("상승 기대 섹터 · v1.4",')
s = s.replace("현재 섹터 원본과 종목 후보 데이터가 모두 비어 있습니다.", "v1.4 FALLBACK ACTIVE · 현재 섹터 원본과 종목 후보 데이터가 모두 비어 있습니다.")
main_path.write_text(s, encoding="utf-8")

m = manifest_path.read_text(encoding="utf-8")
m = m.replace('android:label="마샬 주식분석 v1.2"', 'android:label="마샬 주식분석 v1.4 FIX"')
m = m.replace('android:label="마샬 주식분석 v1.3"', 'android:label="마샬 주식분석 v1.4 FIX"')
manifest_path.write_text(m, encoding="utf-8")

g = gradle_path.read_text(encoding="utf-8")
g = g.replace('applicationId = "com.marshall.stockai.v13"', 'applicationId = "com.marshall.stockai.v14"')
g = g.replace('versionCode = 13', 'versionCode = 14')
g = g.replace('versionName = "1.3.0"', 'versionName = "1.4.0"')
gradle_path.write_text(g, encoding="utf-8")

print("MarshallStock v1.4 identity applied")
