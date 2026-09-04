from pathlib import Path

p = Path(__file__).resolve().parents[1] / "app/src/main/java/com/marshall/stockai/StockEngine.java"
s = p.read_text(encoding="utf-8")

old1 = '''        Map<String,Integer> foreign;
        try{foreign=fetchInvestorRanks("FOREIGNER");}catch(Exception e){foreign=new HashMap<>();}
        Map<String,Integer> institution = fetchInstitutionRanks();'''
new1 = '''        Map<String,Integer> foreignTmp;
        try{foreignTmp=fetchInvestorRanks("FOREIGNER");}catch(Exception e){foreignTmp=new HashMap<>();}
        final Map<String,Integer> foreign = foreignTmp;
        final Map<String,Integer> institution = fetchInstitutionRanks();'''

old2 = '''        Map<String,Integer> foreign;
        try{foreign=fetchInvestorRanks("FOREIGNER");}catch(Exception e){foreign=new HashMap<>();}
        Map<String,Integer> institution=fetchInstitutionRanks();'''
new2 = '''        Map<String,Integer> foreignTmp;
        try{foreignTmp=fetchInvestorRanks("FOREIGNER");}catch(Exception e){foreignTmp=new HashMap<>();}
        final Map<String,Integer> foreign=foreignTmp;
        final Map<String,Integer> institution=fetchInstitutionRanks();'''

if old1 not in s:
    raise SystemExit("TOP10 foreign-rank block not found")
s = s.replace(old1, new1, 1)
if old2 not in s:
    raise SystemExit("TOP20 foreign-rank block not found")
s = s.replace(old2, new2, 1)

p.write_text(s, encoding="utf-8")
print("Java lambda final-variable fix applied")
