from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
stock_path = ROOT / "app/src/main/java/com/marshall/stockai/StockEngine.java"
market_path = ROOT / "app/src/main/java/com/marshall/stockai/MarketHub.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"patch target not found: {label}")
    return text.replace(old, new, 1)


s = stock_path.read_text(encoding="utf-8")

# 1) current/basic endpoint fallback
s = replace_once(s,
'''            JSONObject basic = getJson(M_NAVER + "/api/stock/" + query + "/basic");''',
'''            JSONObject basic = fetchBasicJson(query);''',
"resolve basic")
s = replace_once(s,
'''        JSONObject basic = getJson(M_NAVER + "/api/stock/" + code + "/basic");''',
'''        JSONObject basic = fetchBasicJson(code);''',
"analyze basic")

# 2) ranking collection should not die when one upstream source changes
old = '''    private List<Candidate> fetchRanking(String orderType,int size) throws Exception {
        String url=STOCK_NAVER+"/api/domestic/market/stock/default?tradeType=KRX&marketType=ALL&orderType="+
                orderType+"&startIdx=0&pageSize="+size;
        JSONObject j=getJson(url);
        ArrayList<Candidate> raw=new ArrayList<>();
        collectCandidates(j,raw);
        LinkedHashMap<String,Candidate> uniq=new LinkedHashMap<>();
        for(Candidate c:raw){
            if(c.code.matches("\\\\d{6}")&&!c.name.isEmpty()&&!blocked(c.name))uniq.putIfAbsent(c.code,c);
        }
        return new ArrayList<>(uniq.values());
    }
'''
new = '''    private List<Candidate> fetchRanking(String orderType,int size) {
        LinkedHashMap<String,Candidate> uniq=new LinkedHashMap<>();
        ArrayList<Candidate> raw=new ArrayList<>();

        // Npay Stock current web ranking API
        try{
            String url=STOCK_NAVER+"/api/domestic/market/stock/default?tradeType=KRX&marketType=ALL&orderType="+
                    orderType+"&startIdx=0&pageSize="+size;
            collectCandidates(getJson(url),raw);
        }catch(Exception ignored){}
        addUniqueCandidates(uniq,raw);

        // Mobile front-api fallback. Current response uses itemcode/itemname/nowPrice/prevChangeRate.
        if(uniq.size()<Math.min(20,size)){
            String sort=orderType;
            if("marketSum".equals(orderType))sort="marketValue";
            if("upperQuantTop".equals(orderType))sort="quantTop";
            for(String category:new String[]{"KOSPI","KOSDAQ"}){
                try{
                    raw.clear();
                    String u=M_NAVER+"/front-api/stock/domestic/stockList?sortType="+sort+
                            "&category="+category+"&page=1&pageSize="+size;
                    collectCandidates(getJson(u),raw);
                    addUniqueCandidates(uniq,raw);
                }catch(Exception ignored){}
            }
        }

        // Last fallback: public popular-stock widget. Never fail the whole scanner for one changed endpoint.
        if(uniq.size()<10){
            try{
                raw.clear();
                collectCandidates(getJson(M_NAVER+"/front-api/market/popularStock"),raw);
                addUniqueCandidates(uniq,raw);
            }catch(Exception ignored){}
        }
        return new ArrayList<>(uniq.values());
    }

    private void addUniqueCandidates(LinkedHashMap<String,Candidate> uniq,List<Candidate> raw){
        for(Candidate c:raw){
            if(c.code.matches("\\\\d{6}")&&!c.name.isEmpty()&&!blocked(c.name))uniq.putIfAbsent(c.code,c);
        }
    }
'''
s = replace_once(s, old, new, "fetchRanking")

# 3) basic/parser aliases: current Naver responses mix camelCase and lowercase names
old = '''    private Candidate candidateFromBasic(String code,JSONObject j){
        Candidate c=new Candidate();c.code=code;
        c.name=firstString(j,"stockName","itemName","name");
        c.current=num(first(j,"closePrice","currentPrice","nowVal"));
        c.changePct=num(first(j,"fluctuationsRatio","changeRate","compareToPreviousPrice"));
        c.volume=num(first(j,"accumulatedTradingVolume","volume"));
        c.turnover=num(first(j,"accumulatedTradingValue","tradingValue","amount"));
        JSONObject ex=j.optJSONObject("stockExchangeType");
        if(ex!=null)c.market=firstString(ex,"name","typeName","code");
        if(c.market.isEmpty())c.market=firstString(j,"marketType","typeName");
        return c;
    }
'''
new = '''    private Candidate candidateFromBasic(String code,JSONObject j){
        Candidate c=new Candidate();c.code=code;
        c.name=firstString(j,"stockName","itemName","itemname","name","stock_name");
        c.current=num(first(j,"closePrice","nowPrice","currentPrice","nowVal","price","nv"));
        c.changePct=num(first(j,"fluctuationsRatio","prevChangeRate","changeRate","changeRatio","changePct","cr","compareToPreviousPrice"));
        c.volume=num(first(j,"accumulatedTradingVolume","tradingVolume","volume","quant","aq"));
        c.turnover=num(first(j,"accumulatedTradingValue","tradingValue","amount","accAmount","turnover","aa"));
        JSONObject ex=j.optJSONObject("stockExchangeType");
        if(ex!=null)c.market=firstString(ex,"name","typeName","code");
        if(c.market.isEmpty())c.market=firstString(j,"marketType","marketName","typeName","category");
        return c;
    }
'''
s = replace_once(s, old, new, "candidateFromBasic")

old = '''    private void collectCandidates(Object node,List<Candidate> out){
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;
            String code=firstString(j,"itemCode","code","stockCode","symbol");
            if(code.matches("\\\\d{6}")){
                Candidate c=new Candidate();c.code=code;
                c.name=firstString(j,"stockName","itemName","name","korName");
                c.market=firstString(j,"marketType","typeName","marketName");
                JSONObject ex=j.optJSONObject("stockExchangeType");
                if(c.market.isEmpty()&&ex!=null)c.market=firstString(ex,"name","typeName","code");
                c.current=num(first(j,"closePrice","currentPrice","nowVal","price"));
                c.changePct=num(first(j,"fluctuationsRatio","changeRate","changeRatio"));
                c.volume=num(first(j,"accumulatedTradingVolume","tradingVolume","volume","quant"));
                c.turnover=num(first(j,"accumulatedTradingValue","tradingValue","amount","accAmount"));
                out.add(c);
            }
            Iterator<String> it=j.keys();
            while(it.hasNext()){
                String k=it.next();
                Object v=j.opt(k);
                if(v instanceof JSONObject||v instanceof JSONArray)collectCandidates(v,out);
            }
        }else if(node instanceof JSONArray){
            JSONArray a=(JSONArray)node;
            for(int i=0;i<a.length();i++)collectCandidates(a.opt(i),out);
        }
    }
'''
new = '''    private void collectCandidates(Object node,List<Candidate> out){
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;
            String code=firstString(j,"itemCode","itemcode","code","stockCode","stock_code","symbol");
            if(code.matches("\\\\d{6}")){
                Candidate c=new Candidate();c.code=code;
                c.name=firstString(j,"stockName","itemName","itemname","name","korName","stock_name");
                c.market=firstString(j,"marketType","typeName","marketName","category");
                JSONObject ex=j.optJSONObject("stockExchangeType");
                if(c.market.isEmpty()&&ex!=null)c.market=firstString(ex,"name","typeName","code");
                c.current=num(first(j,"closePrice","nowPrice","currentPrice","nowVal","price","nv"));
                c.changePct=num(first(j,"fluctuationsRatio","prevChangeRate","changeRate","changeRatio","changePct","cr"));
                c.volume=num(first(j,"accumulatedTradingVolume","tradingVolume","volume","quant","aq"));
                c.turnover=num(first(j,"accumulatedTradingValue","tradingValue","amount","accAmount","turnover","aa"));
                out.add(c);
            }
            Iterator<String> it=j.keys();
            while(it.hasNext()){
                Object v=j.opt(it.next());
                if(v instanceof JSONObject||v instanceof JSONArray)collectCandidates(v,out);
            }
        }else if(node instanceof JSONArray){
            JSONArray a=(JSONArray)node;
            for(int i=0;i<a.length();i++)collectCandidates(a.opt(i),out);
        }
    }
'''
s = replace_once(s, old, new, "collectCandidates")

# 4) chart: legacy fchart first, then two current JSON fallbacks
old = '''    public ArrayList<Bar> fetchBars(String code,String timeframe,int count) throws Exception {
        String url=FCHART+"?symbol="+code+"&timeframe="+timeframe+"&count="+count+"&requestType=0";
        String xml=getText(url);
        Pattern p=Pattern.compile("<item\\\\s+data=\\\"([^\\\"]+)\\\"");
        Matcher m=p.matcher(xml);
        ArrayList<Bar> bars=new ArrayList<>();
        while(m.find()){
            String[] x=m.group(1).split("\\\\|");
            if(x.length<6)continue;
            try{
                double o=Double.parseDouble(x[1]),h=Double.parseDouble(x[2]),l=Double.parseDouble(x[3]),c=Double.parseDouble(x[4]),v=Double.parseDouble(x[5]);
                if(c>0)bars.add(new Bar(x[0],o,h,l,c,v));
            }catch(Exception ignored){}
        }
        bars.sort(Comparator.comparing(a->a.date));
        return bars;
    }
'''
new = '''    public ArrayList<Bar> fetchBars(String code,String timeframe,int count) throws Exception {
        ArrayList<Bar> bars=new ArrayList<>();
        try{
            String url=FCHART+"?symbol="+code+"&timeframe="+timeframe+"&count="+count+"&requestType=0";
            String xml=getText(url);
            Pattern p=Pattern.compile("<item\\\\s+data=\\\"([^\\\"]+)\\\"");
            Matcher m=p.matcher(xml);
            while(m.find()){
                String[] x=m.group(1).split("\\\\|");
                if(x.length<6)continue;
                try{
                    double o=Double.parseDouble(x[1]),h=Double.parseDouble(x[2]),l=Double.parseDouble(x[3]),c=Double.parseDouble(x[4]),v=Double.parseDouble(x[5]);
                    if(c>0)bars.add(new Bar(x[0],o,h,l,c,v));
                }catch(Exception ignored){}
            }
        }catch(Exception ignored){}

        if(bars.size()<40){
            String script="day".equals(timeframe)?"candleDay":"week".equals(timeframe)?"candleWeek":"candleMonth";
            try{
                collectBarsJson(getJson(M_NAVER+"/front-api/chart/domestic/stock/end?code="+code+
                        "&chartInfoType=item&scriptChartType="+script),bars);
            }catch(Exception ignored){}
        }
        if(bars.size()<40){
            String period="day".equals(timeframe)?"dayCandle":"week".equals(timeframe)?"weekCandle":"monthCandle";
            try{
                collectBarsJson(getJson("https://api.stock.naver.com/chart/domestic/item/"+code+"?periodType="+period),bars);
            }catch(Exception ignored){}
        }

        LinkedHashMap<String,Bar> uniq=new LinkedHashMap<>();
        for(Bar b:bars)if(b.date!=null&&!b.date.isEmpty()&&b.close>0)uniq.put(b.date,b);
        bars=new ArrayList<>(uniq.values());
        bars.sort(Comparator.comparing(a->a.date));
        if(bars.size()>count)bars=new ArrayList<>(bars.subList(bars.size()-count,bars.size()));
        return bars;
    }

    private void collectBarsJson(Object node,List<Bar> out){
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;
            String date=firstString(j,"localDate","date","businessDate","x");
            double close=num(first(j,"closePrice","close","y"));
            if(!date.isEmpty()&&close>0){
                double open=num(first(j,"openPrice","open"));
                double high=num(first(j,"highPrice","high"));
                double low=num(first(j,"lowPrice","low"));
                double volume=num(first(j,"accumulatedTradingVolume","volume"));
                if(open<=0)open=close;if(high<=0)high=close;if(low<=0)low=close;
                out.add(new Bar(date.replace("-",""),open,high,low,close,volume));
            }
            Iterator<String> it=j.keys();while(it.hasNext()){
                Object v=j.opt(it.next());if(v instanceof JSONObject||v instanceof JSONArray)collectBarsJson(v,out);
            }
        }else if(node instanceof JSONArray){
            JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectBarsJson(a.opt(i),out);
        }
    }
'''
s = replace_once(s, old, new, "fetchBars")

# 5) basic helper before candidateFromBasic
marker = '''    private Candidate candidateFromBasic(String code,JSONObject j){'''
helper = '''    private JSONObject fetchBasicJson(String code) throws Exception {
        Exception last=null;
        String[] urls={
                M_NAVER+"/api/stock/"+code+"/basic",
                M_NAVER+"/front-api/stock/domestic/basic?code="+code+"&endType=stock"
        };
        for(String u:urls){try{return getJson(u);}catch(Exception e){last=e;}}
        if(last!=null)throw last;
        throw new Exception("현재가 조회 실패: "+code);
    }

'''
if helper not in s:
    s = s.replace(marker, helper + marker, 1)

# 6) news current + mobile fallback
old = '''        try{
            JSONObject j=getJson(STOCK_NAVER+"/api/domestic/detail/news?itemCode="+code+"&page=1&pageSize=20");
            LinkedHashSet<String> titles=new LinkedHashSet<>();
            collectTitles(j,titles);
            n.naverCount=Math.min(20,titles.size());
            n.headlines.addAll(titles);
        }catch(Exception ignored){}
'''
new = '''        LinkedHashSet<String> titles=new LinkedHashSet<>();
        String[] naverUrls={
                STOCK_NAVER+"/api/domestic/detail/news?itemCode="+code+"&page=1&pageSize=20",
                M_NAVER+"/front-api/news/list/integration?itemCode="+code+"&page=1&pageSize=20"
        };
        for(String u:naverUrls){
            try{collectTitles(getJson(u),titles);if(titles.size()>=8)break;}catch(Exception ignored){}
        }
        n.naverCount=Math.min(20,titles.size());
        n.headlines.addAll(titles);
'''
s = replace_once(s, old, new, "news fallback")

# 7) don't discard the entire scanner because strict filters leave too few rows
s = s.replace('''        if (pool.size() < 10) throw new Exception("시장 후보가 충분하지 않습니다. 잠시 후 다시 시도해 주세요.");\n''', '')
s = s.replace('''        candidates.removeIf(c -> blocked(c.name) || c.current < 1000 || c.turnover < 100_000_000);''',
              '''        candidates.removeIf(c -> blocked(c.name) || c.current < 500);''')
s = s.replace('''        if(candidates.size()>70)candidates=new ArrayList<>(candidates.subList(0,70));\n        if(candidates.size()<20)throw new Exception("상승준비 후보가 충분하지 않습니다.");''',
              '''        if(candidates.size()>80)candidates=new ArrayList<>(candidates.subList(0,80));''')
s = s.replace('''        candidates.removeIf(c -> blocked(c.name) || c.current<1000 || c.turnover<80_000_000 || c.changePct>9.0 || c.changePct<-5.0);''',
              '''        candidates.removeIf(c -> blocked(c.name) || c.current<500 || c.changePct>20.0 || c.changePct<-10.0);''')
s = s.replace('''        if(tech.size()<10) throw new Exception("차트 분석 가능한 후보가 부족합니다.");\n''', '')
s = s.replace('''        if(tech.size()<20)throw new Exception("주봉 준비패턴을 충족한 종목이 부족합니다.");\n''', '')

# investor ranks are optional enrichment, never a fatal dependency
s = s.replace('''        Map<String,Integer> foreign = fetchInvestorRanks("FOREIGNER");
        Map<String,Integer> institution = fetchInstitutionRanks();''',
'''        Map<String,Integer> foreign;
        try{foreign=fetchInvestorRanks("FOREIGNER");}catch(Exception e){foreign=new HashMap<>();}
        Map<String,Integer> institution = fetchInstitutionRanks();''', 1)
s = s.replace('''        Map<String,Integer> foreign=fetchInvestorRanks("FOREIGNER");
        Map<String,Integer> institution=fetchInstitutionRanks();''',
'''        Map<String,Integer> foreign;
        try{foreign=fetchInvestorRanks("FOREIGNER");}catch(Exception e){foreign=new HashMap<>();}
        Map<String,Integer> institution=fetchInstitutionRanks();''', 1)

# if one chart endpoint fails, keep the candidate with a neutral technical score
s = s.replace('''                } catch(Exception e) { return null; }
            }));''',
'''                } catch(Exception e) {
                    c.secondaryScore=50;
                    FlowInfo f=flowFromRanks(c.code,foreign,institution);
                    c.flowScore=f.score;c.foreignRank=f.foreignRank;c.institutionRank=f.institutionRank;
                    c.preScore=50*0.62+c.flowScore*0.28+c.liquidity*0.10;
                    return c;
                }
            }));''', 1)
s = s.replace('''                }catch(Exception e){return null;}
            }));''',
'''                }catch(Exception e){
                    c.secondaryScore=45;
                    FlowInfo fi=flowFromRanks(c.code,foreign,institution);
                    c.flowScore=fi.score;c.foreignRank=fi.foreignRank;c.institutionRank=fi.institutionRank;
                    c.preScore=45*0.70+c.flowScore*0.20+c.liquidity*0.10;
                    return c;
                }
            }));''', 1)
# include neutral-fallback weekly candidates too
s = s.replace('''if(c!=null&&c.secondaryScore>=34)tech.add(c);''','''if(c!=null&&c.secondaryScore>=30)tech.add(c);''')

# 8) richer Marshall-style explanation in detail panels
s = s.replace('''        b.append("[오늘의 TOP10] ").append(c.name).append(" (").append(c.code).append(")\\n");''',
'''        b.append("[마샬 종합분석 · 당일 이슈] ").append(c.name).append(" (").append(c.code).append(")\\n");
        b.append("섹터 ").append(sectorHint(c)).append(" · 강도 ").append(strengthLabel(c.finalScore))
                .append(" · 지속성 ").append(persistenceLabel(c)).append("\\n");''')
s = s.replace('''        b.append("[상승준비 TOP20] ").append(c.name).append(" (").append(c.code).append(")\\n");''',
'''        b.append("[마샬 종합분석 · 상승가능성] ").append(c.name).append(" (").append(c.code).append(")\\n");
        b.append("섹터 ").append(sectorHint(c)).append(" · 강도 ").append(strengthLabel(c.finalScore))
                .append(" · 지속성 ").append(persistenceLabel(c)).append("\\n");''')

insert_marker='''    private void levels(ArrayList<Bar>b,double cur,ArrayList<Double> sup,ArrayList<Double> res){'''
extra='''    private String strengthLabel(double score){
        if(score>=88)return "최강";
        if(score>=80)return "강";
        if(score>=70)return "중상";
        if(score>=60)return "보통";
        return "관찰";
    }

    private String persistenceLabel(Candidate c){
        double v=c.newsScore*0.42+c.flowScore*0.28+c.secondaryScore*0.30-c.overheat*2.2;
        if(v>=75)return "높음";
        if(v>=62)return "중상";
        if(v>=50)return "보통";
        return "단기 변동성 주의";
    }

    private String sectorHint(Candidate c){
        String text=(c.name+" "+(c.news==null?"":String.join(" ",c.news.headlines))).toLowerCase(Locale.ROOT);
        if(hasAny(text,"로봇","휴머노이드","피지컬ai","액추에이터","감속기"))return "로봇·피지컬AI";
        if(hasAny(text,"hbm","반도체","파운드리","패키징","dram","낸드"))return "반도체·HBM";
        if(hasAny(text,"정유","유가","원유","정제마진","석유"))return "정유·에너지";
        if(hasAny(text,"비트코인","가상자산","스테이블코인","토큰증권","sto","핀테크","결제"))return "디지털자산·핀테크";
        if(hasAny(text,"바이오","신약","임상","의료","헬스케어","제약"))return "바이오·헬스케어";
        if(hasAny(text,"방산","미사일","항공우주","무기"))return "방산·우주";
        if(hasAny(text,"원전","원자력","smr","전력기기","변압기"))return "원전·전력";
        if(hasAny(text,"조선","lng선","선박","해운"))return "조선·해운";
        if(hasAny(text,"2차전지","배터리","양극재","전고체"))return "2차전지";
        if(hasAny(text,"게임","콘텐츠","엔터","웹툰"))return "게임·콘텐츠";
        if(hasAny(text,"자동차","전기차","자율주행"))return "자동차·모빌리티";
        return "개별 모멘텀";
    }

    private boolean hasAny(String text,String...keys){for(String k:keys)if(text.contains(k.toLowerCase(Locale.ROOT)))return true;return false;}

'''
if extra not in s:
    s=s.replace(insert_marker,extra+insert_marker,1)

stock_path.write_text(s,encoding="utf-8")

# MarketHub: sector and quote fallbacks should also survive endpoint changes
m = market_path.read_text(encoding="utf-8")
old='''    public List<Sector> sectors() throws Exception {
        Object root=getJson("https://stock.naver.com/api/domestic/home/upjongTheme/ranking?sortType=changeRate");
        ArrayList<Sector> out=new ArrayList<>();
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        collectSectorObjects(root,out,seen);
        List<String> news=flashHeadlines();'''
new='''    public List<Sector> sectors() throws Exception {
        ArrayList<Sector> out=new ArrayList<>();
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        String[] urls={
                "https://stock.naver.com/api/domestic/home/upjongTheme/ranking?sortType=changeRate",
                "https://m.stock.naver.com/front-api/stock/sectors/all?nationType=domestic&sectorType=upjong",
                "https://m.stock.naver.com/front-api/stock/sectors/all?nationType=domestic&sectorType=theme"
        };
        for(String u:urls){try{collectSectorObjects(getJson(u),out,seen);}catch(Exception ignored){}}
        List<String> news=flashHeadlines();'''
m=replace_once(m,old,new,"MarketHub sectors")
m=m.replace('''            double rate=num(j,"changeRate","fluctuationsRatio","changePct","rate");''',
            '''            double rate=num(j,"changeRate","prevChangeRate","fluctuationsRatio","changePct","rate");''')
m=m.replace('''                boolean looksSector=j.has("upjongCode")||j.has("themeCode")||j.has("sectorCode")||j.has("rank")||j.has("ranking");''',
            '''                boolean looksSector=j.has("upjongCode")||j.has("themeCode")||j.has("sectorCode")||j.has("code")||j.has("rank")||j.has("ranking");''')
market_path.write_text(m,encoding="utf-8")

print("MarshallStock v1.2 runtime fixes applied")
