from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
market_path = ROOT / "app/src/main/java/com/marshall/stockai/MarketHub.java"
main_path = ROOT / "app/src/main/java/com/marshall/stockai/MainActivity.java"


def replace_block(text: str, start_marker: str, end_marker: str, replacement: str, label: str) -> str:
    a = text.find(start_marker)
    if a < 0:
        raise SystemExit(f"start marker not found: {label}")
    b = text.find(end_marker, a)
    if b < 0:
        raise SystemExit(f"end marker not found: {label}")
    return text[:a] + replacement + text[b:]


# --- MarketHub: make sector discovery multi-source and non-empty whenever market candidates/news exist ---
m = market_path.read_text(encoding="utf-8")

sector_method = '''    public List<Sector> sectors() throws Exception {
        ArrayList<Sector> out=new ArrayList<>();
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        String[] urls={
                "https://stock.naver.com/api/domestic/home/upjongTheme/ranking?sortType=changeRate",
                "https://m.stock.naver.com/front-api/stock/sectors/all?nationType=domestic&sectorType=upjong",
                "https://m.stock.naver.com/front-api/stock/sectors/all?nationType=domestic&sectorType=theme",
                "https://m.stock.naver.com/front-api/market/sector?marketType=ALL",
                "https://m.stock.naver.com/front-api/market/theme?marketType=ALL"
        };
        for(String u:urls){
            try{collectSectorObjects(getJson(u),out,seen);}catch(Exception ignored){}
            if(out.size()>=12)break;
        }

        List<String> news=flashHeadlines();
        if(out.isEmpty())out.addAll(keywordSectors(news));

        for(Sector s:out){
            if(s.newsHits<=0){
                int hits=0;
                String needle=s.name.replace(" ","").toLowerCase(Locale.ROOT);
                for(String h:news){
                    String x=h.replace(" ","").toLowerCase(Locale.ROOT);
                    if(!needle.isEmpty()&&x.contains(needle))hits++;
                }
                s.newsHits=hits;
            }
            double liquidityBonus=s.turnover>0?Math.min(8,Math.log10(Math.max(10,s.turnover))/2.0):0;
            double base=50+s.changeRate*8+s.newsHits*5+liquidityBonus;
            if(s.changeRate==0&&s.newsHits>0)base=Math.max(base,55+s.newsHits*6);
            s.score=clamp(Math.max(s.score,base),0,100);
            if(s.score>=82)s.note=s.note.isEmpty()?"최강 상승 섹터":s.note;
            else if(s.score>=70)s.note=s.note.isEmpty()?"강한 상승 섹터":s.note;
            else if(s.score>=58)s.note=s.note.isEmpty()?"상승 우위":s.note;
            else s.note=s.note.isEmpty()?"관심 구간":s.note;
        }
        out.sort((a,b)->Double.compare(b.score,a.score));
        if(out.size()>15)return new ArrayList<>(out.subList(0,15));
        return out;
    }

'''

m = replace_block(
    m,
    "    public List<Sector> sectors() throws Exception {",
    "    public List<String> flashHeadlines()",
    sector_method,
    "MarketHub.sectors"
)

collect_and_keyword = '''    private List<Sector> keywordSectors(List<String> news){
        ArrayList<Sector> out=new ArrayList<>();
        String[][] groups={
                {"로봇·피지컬AI","로봇","휴머노이드","피지컬ai","액추에이터","감속기"},
                {"반도체·HBM","반도체","hbm","dram","낸드","파운드리","패키징","후공정"},
                {"정유·에너지","정유","원유","유가","정제마진","석유","에너지"},
                {"디지털자산·핀테크","비트코인","가상자산","스테이블코인","토큰증권","sto","핀테크","결제"},
                {"원전·전력","원전","원자력","smr","전력기기","변압기","전력망"},
                {"방산·우주","방산","미사일","항공우주","우주","위성"},
                {"조선·해운","조선","lng선","선박","해운","수주"},
                {"바이오·헬스케어","바이오","신약","임상","제약","의료ai","헬스케어"},
                {"2차전지","2차전지","배터리","양극재","음극재","전고체"},
                {"자동차·모빌리티","자동차","전기차","자율주행","모빌리티"},
                {"게임·콘텐츠","게임","콘텐츠","엔터","웹툰","신작"}
        };
        for(String[] g:groups){
            int hits=0;
            for(String h:news){
                String x=h.toLowerCase(Locale.ROOT);
                boolean matched=false;
                for(int i=1;i<g.length;i++)if(x.contains(g[i].toLowerCase(Locale.ROOT))){matched=true;break;}
                if(matched)hits++;
            }
            if(hits>0){
                Sector s=new Sector();s.name=g[0];s.newsHits=hits;s.changeRate=0;
                s.score=clamp(55+hits*6,0,95);s.note="실시간 뉴스 이슈 기반";out.add(s);
            }
        }
        out.sort((a,b)->Double.compare(b.score,a.score));
        return out;
    }

    private void collectSectorObjects(Object node,List<Sector> out,Set<String> seen){
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;
            String name=str(j,"upjongName","themeName","sectorName","groupName","categoryName","name");
            double rate=num(j,"changeRate","prevChangeRate","fluctuationsRatio","changePct","rate");
            double value=num(j,"tradingValue","turnover","accumulatedTradingValue","accumulatedTradingValueKrx");
            boolean looksStock=j.has("itemCode")||j.has("itemcode")||j.has("stockCode")||j.has("stock_code");
            boolean explicitName=j.has("upjongName")||j.has("themeName")||j.has("sectorName")||j.has("groupName")||j.has("categoryName");
            boolean looksSector=explicitName||j.has("upjongCode")||j.has("themeCode")||j.has("sectorCode")||j.has("groupCode")||j.has("categoryCode")||j.has("rank")||j.has("ranking");
            if(!looksStock&&!name.isEmpty()&&name.length()<=40&&Math.abs(rate)<=100&&!seen.contains(name)&&looksSector){
                Sector s=new Sector();s.name=name;s.changeRate=rate;s.turnover=value;out.add(s);seen.add(name);
            }
            Iterator<String> it=j.keys();while(it.hasNext())collectSectorObjects(j.opt(it.next()),out,seen);
        }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectSectorObjects(a.opt(i),out,seen);}
    }

'''

m = replace_block(
    m,
    "    private void collectSectorObjects(Object node,List<Sector> out,Set<String> seen){",
    "    private JSONObject findFirstObjectWithKey",
    collect_and_keyword,
    "MarketHub.collectSectorObjects"
)
market_path.write_text(m,encoding="utf-8")


# --- MainActivity: if sector API is empty, rebuild sectors from TOP10/TOP20 candidates ---
a = main_path.read_text(encoding="utf-8")
a = a.replace("Android v1.0 · 통합 투자분석 + 가상투자","Android v1.3 · 통합 투자분석 + 가상투자")

show_sector = '''    private void showSectors(){
        LoadingUi u=showLoading("상승 기대 섹터","업종/테마 · 뉴스 · TOP10/TOP20을 교차 분석합니다...");
        executor.submit(()->{
            List<MarketHub.Sector> sectors=new ArrayList<>();
            boolean fallback=false;
            try{sectors=market.sectors();}catch(Exception ignored){}

            if(sectors==null||sectors.isEmpty()){
                fallback=true;
                List<StockEngine.Candidate> top=loadCache("top10");
                List<StockEngine.Candidate> setup=loadCache("setup20");
                if(top==null||top.isEmpty()){
                    try{top=engine.top10(progress(u));saveCache("top10",top);}catch(Exception ignored){}
                }
                if(setup==null||setup.isEmpty()){
                    try{setup=engine.setup20(progress(u));saveCache("setup20",setup);}catch(Exception ignored){}
                }
                sectors=buildSectorsFromCandidates(top,setup);
            }

            final List<MarketHub.Sector> result=sectors==null?new ArrayList<>():sectors;
            final boolean usedFallback=fallback;
            runOnUiThread(()->{
                if(u.token!=screenToken)return;
                LinearLayout root=page("상승 기대 섹터",true);
                String guide=usedFallback
                        ?"네이버 섹터 원본이 비어 TOP10·TOP20과 종목 뉴스에서 강세 섹터를 재구성했습니다. 대표 종목과 강도는 현재 후보군 기준입니다."
                        :"섹터점수 = 업종/테마 등락강도 + 거래대금 신호 + 실시간 뉴스 노출. 개별 종목은 TOP10/TOP20과 상세 차트를 함께 확인하세요.";
                addCard(root,label(guide,13,MUTED,false));
                if(result.isEmpty()){
                    addCard(root,label("현재 섹터 원본과 종목 후보 데이터가 모두 비어 있습니다. 잠시 후 다시 시도하거나 TOP10/TOP20에서 먼저 새로 분석을 실행해 주세요.",14,MUTED,false));
                    LinearLayout buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);
                    Button t=smallButton("TOP10 새로분석",GREEN);t.setOnClickListener(v->loadRadar(false,true));buttons.addView(t,new LinearLayout.LayoutParams(0,dp(50),1));
                    Button p=smallButton("TOP20 새로분석",AMBER);p.setOnClickListener(v->loadRadar(true,true));buttons.addView(p,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(buttons);
                    return;
                }
                for(int i=0;i<result.size();i++){
                    MarketHub.Sector x=result.get(i);
                    String t=(i+1)+"위  "+x.name+"  "+f1(x.score)+"점\n"+
                            (Math.abs(x.changeRate)>0.001?"등락 "+String.format(Locale.KOREA,"%+.2f%%",x.changeRate)+" · ":"")+"뉴스/이슈 "+x.newsHits+"건\n"+x.note;
                    addCard(root,label(t,15,x.changeRate>=0?Color.rgb(248,113,113):Color.rgb(96,165,250),true));
                }
            });
        });
    }

    private List<MarketHub.Sector> buildSectorsFromCandidates(List<StockEngine.Candidate> top,List<StockEngine.Candidate> setup){
        LinkedHashMap<String,StockEngine.Candidate> uniq=new LinkedHashMap<>();
        if(top!=null)for(StockEngine.Candidate c:top)if(c!=null&&!c.code.isEmpty())uniq.put(c.code,c);
        if(setup!=null)for(StockEngine.Candidate c:setup)if(c!=null&&!c.code.isEmpty()){
            StockEngine.Candidate old=uniq.get(c.code);
            if(old==null||c.finalScore>old.finalScore)uniq.put(c.code,c);
        }
        ArrayList<StockEngine.Candidate> all=new ArrayList<>(uniq.values());
        all.sort((x,y)->Double.compare(y.finalScore,x.finalScore));
        if(all.isEmpty())return new ArrayList<>();

        String[][] groups={
                {"로봇·피지컬AI","로봇","로보","휴머노이드","피지컬ai","액추에이터","감속기","에스피지"},
                {"반도체·HBM","반도체","hbm","dram","낸드","파운드리","패키징","한미반도체","원익","피에스케이","인텍플러스","hpsp","isc"},
                {"정유·에너지","정유","원유","유가","정제마진","석유","s-oil","에쓰오일","sk이노베이션","gs"},
                {"디지털자산·핀테크","비트코인","가상자산","스테이블코인","토큰증권","sto","핀테크","결제","쿠콘","헥토","kcp","우리기술투자","갤럭시아","다날"},
                {"원전·전력","원전","원자력","smr","전력기기","변압기","전력망","두산에너빌리티","한전기술"},
                {"방산·우주","방산","미사일","항공우주","위성","한화에어로","lig넥스원"},
                {"조선·해운","조선","lng선","선박","해운","hd현대중공업","한화오션","삼성중공업"},
                {"바이오·헬스케어","바이오","신약","임상","제약","의료ai","헬스케어"},
                {"2차전지","2차전지","배터리","양극재","음극재","전고체"},
                {"자동차·모빌리티","자동차","전기차","자율주행","모빌리티","현대차","현대모비스"},
                {"게임·콘텐츠","게임","콘텐츠","엔터","웹툰","신작"}
        };

        ArrayList<MarketHub.Sector> out=new ArrayList<>();
        for(String[] g:groups){
            ArrayList<StockEngine.Candidate> members=new ArrayList<>();
            int newsHits=0;
            for(StockEngine.Candidate c:all){
                String text=candidateSectorText(c);
                if(sectorMatches(text,g)){
                    members.add(c);
                    if(c.news!=null)newsHits+=Math.min(5,c.news.headlines.size());
                }
            }
            if(members.isEmpty())continue;
            members.sort((x,y)->Double.compare(y.finalScore,x.finalScore));
            double sumScore=0,sumChange=0;
            for(StockEngine.Candidate c:members){sumScore+=c.finalScore;sumChange+=c.changePct;}
            MarketHub.Sector s=new MarketHub.Sector();s.name=g[0];s.newsHits=newsHits;
            s.changeRate=sumChange/members.size();
            s.score=clamp(sumScore/members.size()+Math.min(12,members.size()*2.5),0,100);
            StringBuilder leaders=new StringBuilder("대표: ");
            for(int i=0;i<Math.min(3,members.size());i++){if(i>0)leaders.append(" · ");leaders.append(members.get(i).name);}
            s.note=leaders+" · TOP10/TOP20 기반";out.add(s);
        }

        if(out.isEmpty()){
            int n=Math.min(5,all.size());double sumScore=0,sumChange=0;StringBuilder leaders=new StringBuilder("대표: ");
            for(int i=0;i<n;i++){StockEngine.Candidate c=all.get(i);sumScore+=c.finalScore;sumChange+=c.changePct;if(i>0)leaders.append(" · ");leaders.append(c.name);}
            MarketHub.Sector s=new MarketHub.Sector();s.name="당일 시장 주도주";s.changeRate=sumChange/Math.max(1,n);s.score=clamp(sumScore/Math.max(1,n),0,100);s.newsHits=0;s.note=leaders+" · 섹터 자동분류 대기";out.add(s);
        }
        out.sort((x,y)->Double.compare(y.score,x.score));
        if(out.size()>12)return new ArrayList<>(out.subList(0,12));
        return out;
    }

    private String candidateSectorText(StockEngine.Candidate c){
        StringBuilder b=new StringBuilder();b.append(c.name).append(' ').append(c.reason).append(' ').append(c.detail);
        if(c.news!=null)for(String h:c.news.headlines)b.append(' ').append(h);
        return b.toString().toLowerCase(Locale.ROOT);
    }

    private boolean sectorMatches(String text,String[] group){
        for(int i=1;i<group.length;i++)if(text.contains(group[i].toLowerCase(Locale.ROOT)))return true;
        return false;
    }

'''

a = replace_block(
    a,
    "    private void showSectors(){",
    "    private void showWatchlist(){",
    show_sector,
    "MainActivity.showSectors"
)
main_path.write_text(a,encoding="utf-8")

print("MarshallStock v1.3 sector fallbacks applied")
