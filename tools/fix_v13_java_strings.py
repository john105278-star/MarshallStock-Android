from pathlib import Path

p = Path(__file__).resolve().parents[1] / "app/src/main/java/com/marshall/stockai/MainActivity.java"
s = p.read_text(encoding="utf-8")

bad = '''                    String t=(i+1)+"위  "+x.name+"  "+f1(x.score)+"점
"+
                            (Math.abs(x.changeRate)>0.001?"등락 "+String.format(Locale.KOREA,"%+.2f%%",x.changeRate)+" · ":"")+"뉴스/이슈 "+x.newsHits+"건
"+x.note;'''

good = '''                    String nl=System.lineSeparator();
                    String t=(i+1)+"위  "+x.name+"  "+f1(x.score)+"점"+nl+
                            (Math.abs(x.changeRate)>0.001?"등락 "+String.format(Locale.KOREA,"%+.2f%%",x.changeRate)+" · ":"")+"뉴스/이슈 "+x.newsHits+"건"+nl+x.note;'''

if bad not in s:
    raise SystemExit("v1.3 generated sector string block not found")

p.write_text(s.replace(bad, good, 1), encoding="utf-8")
print("v1.3 Java sector string literal fix applied")
