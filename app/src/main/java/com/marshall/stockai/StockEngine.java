package com.marshall.stockai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StockEngine {
    private static final String M_NAVER = "https://m.stock.naver.com";
    private static final String STOCK_NAVER = "https://stock.naver.com";
    private static final String FCHART = "https://fchart.stock.naver.com/sise.nhn";
    private static final String UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36";

    public interface Progress {
        void onProgress(int pct, String message);
    }

    public static class Bar {
        public String date;
        public double open, high, low, close, volume;
        public Bar(String date, double open, double high, double low, double close, double volume) {
            this.date=date; this.open=open; this.high=high; this.low=low; this.close=close; this.volume=volume;
        }
    }

    public static class Score {
        public double trend, momentum, volume, overheat, attractiveness;
        public double technical100;
        public String grade, regime;
        public double rsi, cmo, macd, signal, stoch, adx, plusDi, minusDi;
        public double ma5, ma10, ma20, ma60, ma120, volRatio, ret20;
    }

    public static class NewsInfo {
        public int naverCount, daumCount, nateCount, globalCount;
        public int positiveHits, negativeHits;
        public double score = 50;
        public final ArrayList<String> headlines = new ArrayList<>();
        public String summary = "";
    }

    public static class FlowInfo {
        public int foreignRank = 0;
        public int institutionRank = 0;
        public double score = 50;
        public String summary = "외국인·기관 상위 순매수 랭킹 중립";
    }

    public static class Analysis {
        public String code="", name="", market="", period="";
        public double current, changePct;
        public Score score;
        public ArrayList<Double> supports = new ArrayList<>();
        public ArrayList<Double> resistances = new ArrayList<>();
        public ArrayList<Bar> bars = new ArrayList<>();
        public NewsInfo news;
        public FlowInfo flow;
        public String report="";
    }

    public static class Candidate {
        public String code="", name="", market="";
        public double current, changePct, volume, turnover;
        public int sourceHits=1;
        public double liquidity, preScore, secondaryScore, flowScore=50, newsScore=50, finalScore;
        public double weeklyRsi, breakoutGap, weeklyVolRatio, ma20Slope, ret4w, overheat;
        public int foreignRank, institutionRank;
        public String reason="", detail="";
        public NewsInfo news;
        public Score score;
    }

    private static final String[] BLOCKED = {
            "KODEX","TIGER","RISE","KOSEF","HANARO","TIMEFOLIO","ARIRANG","KBSTAR",
            "KINDEX","ETF","ETN","인버스","레버리지","스팩","SPAC","선물"
    };

    private static final String[] POSITIVE = {
            "수주","계약","공급","협력","파트너","승인","허가","특허","증설","투자","신규",
            "개발","출시","성장","호조","개선","흑자","턴어라운드","최대","상향","목표가",
            "점유율","수출","양산","채택","선정","국산화","데이터센터","AI","인공지능",
            "원전","방산","반도체","로봇","ESS"
    };
    private static final String[] NEGATIVE = {
            "유상증자","감자","적자","하향","급락","횡령","배임","소송","과징금","조사",
            "리콜","중단","취소","실패","감소","부진","상장폐지","관리종목","전환사채",
            "CB 발행","불성실"
    };

    public Candidate resolve(String query) throws Exception {
        query = query == null ? "" : query.trim();
        if (query.matches("\\d{6}")) {
            JSONObject basic = getJson(M_NAVER + "/api/stock/" + query + "/basic");
            Candidate c = candidateFromBasic(query, basic);
            if (c.name.isEmpty()) c.name = query;
            return c;
        }

        String url = M_NAVER + "/front-api/search/autoComplete?query=" +
                enc(query) + "&target=stock,index,marketindicator,coin,ipo";
        JSONObject j = getJson(url);
        ArrayList<Candidate> found = new ArrayList<>();
        collectCandidates(j, found);
        LinkedHashMap<String,Candidate> uniq = new LinkedHashMap<>();
        for (Candidate c: found) {
            if (c.code.matches("\\d{6}") && !c.name.isEmpty() && !blocked(c.name)) {
                uniq.putIfAbsent(c.code, c);
            }
        }
        if (uniq.isEmpty()) {
            // legacy autocomplete fallback
            JSONObject legacy = getJson("https://ac.stock.naver.com/ac?q=" + enc(query) + "&q_enc=utf-8&target=stock");
            found.clear();
            collectCandidates(legacy, found);
            for (Candidate c: found) if(c.code.matches("\\d{6}") && !c.name.isEmpty() && !blocked(c.name)) uniq.putIfAbsent(c.code,c);
        }
        if (uniq.isEmpty()) throw new Exception("종목명을 찾지 못했습니다: " + query);
        return uniq.values().iterator().next();
    }

    public Analysis analyze(String query, String period, Progress progress) throws Exception {
        if (progress != null) progress.onProgress(5, "종목 확인 중");
        Candidate resolved = resolve(query);
        String code = resolved.code;

        if (progress != null) progress.onProgress(15, "현재가 조회 중");
        JSONObject basic = getJson(M_NAVER + "/api/stock/" + code + "/basic");
        Candidate live = candidateFromBasic(code, basic);
        if (!live.name.isEmpty()) resolved.name = live.name;
        if (!live.market.isEmpty()) resolved.market = live.market;
        if (live.current > 0) resolved.current = live.current;
        resolved.changePct = live.changePct;

        String tf = "day";
        int count = 280;
        String koreanPeriod = "일봉";
        if ("주봉".equals(period)) { tf="week"; count=180; koreanPeriod="주봉"; }
        else if ("월봉".equals(period)) { tf="month"; count=140; koreanPeriod="월봉"; }

        if (progress != null) progress.onProgress(28, koreanPeriod + " 가격 데이터 조회 중");
        ArrayList<Bar> bars = fetchBars(code, tf, count);
        if (bars.size() < 40) throw new Exception("가격 데이터가 부족합니다.");

        Analysis a = analyzeBars(code, resolved.name, resolved.market, koreanPeriod, bars);
        if (resolved.current > 0 && "일봉".equals(koreanPeriod)) a.current = resolved.current;
        a.changePct = resolved.changePct != 0 ? resolved.changePct : pctChange(bars.get(bars.size()-1).close, bars.get(bars.size()-2).close);

        if (progress != null) progress.onProgress(70, "외국인·기관 수급 확인 중");
        a.flow = flowForCode(code);

        if (progress != null) progress.onProgress(82, "최근 뉴스 확인 중");
        a.news = fetchNews(code, a.name, a.market, false);

        a.report = buildReport(a);
        if (progress != null) progress.onProgress(100, "분석 완료");
        return a;
    }

    public List<Candidate> top10(Progress progress) throws Exception {
        if (progress != null) progress.onProgress(2, "시장 후보 불러오는 중");
        LinkedHashMap<String,Candidate> pool = new LinkedHashMap<>();
        mergeRanking(pool, fetchRanking("priceTop", 40), 3);
        mergeRanking(pool, fetchRanking("up", 40), 2);
        mergeRanking(pool, fetchRanking("upperQuantTop", 35), 3);
        mergeRanking(pool, fetchRanking("quantTop", 35), 1);

        if (pool.size() < 10) throw new Exception("시장 후보가 충분하지 않습니다. 잠시 후 다시 시도해 주세요.");
        ArrayList<Candidate> candidates = new ArrayList<>(pool.values());
        prepareLiquidity(candidates);
        candidates.removeIf(c -> blocked(c.name) || c.current < 1000 || c.turnover < 100_000_000);
        candidates.sort((a,b)->Double.compare(b.preScore,a.preScore));
        if (candidates.size() > 36) candidates = new ArrayList<>(candidates.subList(0,36));

        if (progress != null) progress.onProgress(10, "외국인·기관 랭킹 확인 중");
        Map<String,Integer> foreign = fetchInvestorRanks("FOREIGNER");
        Map<String,Integer> institution = fetchInstitutionRanks();

        ExecutorService ex = Executors.newFixedThreadPool(6);
        ArrayList<Future<Candidate>> futures = new ArrayList<>();
        final int total = candidates.size();
        for (Candidate c: candidates) {
            futures.add(ex.submit(() -> {
                try {
                    ArrayList<Bar> bars = fetchBars(c.code, "day", 220);
                    if (bars.size() < 80) return null;
                    Analysis a = analyzeBars(c.code,c.name,c.market,"일봉",bars);
                    c.score = a.score;
                    c.secondaryScore = a.score.technical100;
                    c.current = a.current;
                    c.overheat = a.score.overheat;
                    FlowInfo f = flowFromRanks(c.code, foreign, institution);
                    c.flowScore = f.score; c.foreignRank=f.foreignRank; c.institutionRank=f.institutionRank;
                    c.preScore = c.secondaryScore * 0.62 + c.flowScore * 0.28 + c.liquidity * 0.10;
                    return c;
                } catch(Exception e) { return null; }
            }));
        }
        ex.shutdown();

        ArrayList<Candidate> tech = new ArrayList<>();
        int done=0;
        for(Future<Candidate> f:futures) {
            try { Candidate c=f.get(); if(c!=null) tech.add(c); } catch(Exception ignored){}
            done++;
            if(progress!=null) progress.onProgress(12 + (int)(52.0*done/Math.max(1,total)), "차트 분석 " + done + "/" + total);
        }
        if(tech.size()<10) throw new Exception("차트 분석 가능한 후보가 부족합니다.");
        tech.sort((a,b)->Double.compare(b.preScore,a.preScore));
        if(tech.size()>15) tech = new ArrayList<>(tech.subList(0,15));

        if(progress!=null) progress.onProgress(66, "국내·해외 뉴스 분석 중");
        ExecutorService nx = Executors.newFixedThreadPool(4);
        ArrayList<Future<Candidate>> nf = new ArrayList<>();
        for(Candidate c:tech) {
            nf.add(nx.submit(() -> {
                try {
                    c.news=fetchNews(c.code,c.name,c.market,true);
                    c.newsScore=c.news.score;
                } catch(Exception e){ c.newsScore=50; }
                c.finalScore = c.secondaryScore*0.45 + c.flowScore*0.25 + c.newsScore*0.20 + c.liquidity*0.10;
                if(c.overheat>=9.0) c.finalScore-=4;
                c.finalScore=clamp(c.finalScore,0,100);
                c.reason=topReason(c);
                c.detail=topDetail(c);
                return c;
            }));
        }
        nx.shutdown();
        ArrayList<Candidate> enriched=new ArrayList<>();
        done=0;
        for(Future<Candidate> f:nf){
            try{Candidate c=f.get();if(c!=null)enriched.add(c);}catch(Exception ignored){}
            done++;
            if(progress!=null)progress.onProgress(68+(int)(30.0*done/Math.max(1,nf.size())),"뉴스 종합 "+done+"/"+nf.size());
        }
        enriched.sort((a,b)->Double.compare(b.finalScore,a.finalScore));
        if(enriched.size()>10) enriched=new ArrayList<>(enriched.subList(0,10));
        if(progress!=null)progress.onProgress(100,"오늘의 TOP10 완료");
        return enriched;
    }

    public List<Candidate> setup20(Progress progress) throws Exception {
        if(progress!=null)progress.onProgress(2,"상승준비 후보 불러오는 중");
        LinkedHashMap<String,Candidate> pool=new LinkedHashMap<>();
        mergeRanking(pool,fetchRanking("marketSum",100),1);
        mergeRanking(pool,fetchRanking("priceTop",50),2);
        mergeRanking(pool,fetchRanking("quantTop",50),2);
        mergeRanking(pool,fetchRanking("up",40),1);

        ArrayList<Candidate> candidates=new ArrayList<>(pool.values());
        prepareLiquidity(candidates);
        candidates.removeIf(c -> blocked(c.name) || c.current<1000 || c.turnover<80_000_000 || c.changePct>9.0 || c.changePct<-5.0);
        candidates.sort((a,b)->Double.compare(b.preScore,a.preScore));
        if(candidates.size()>70)candidates=new ArrayList<>(candidates.subList(0,70));
        if(candidates.size()<20)throw new Exception("상승준비 후보가 충분하지 않습니다.");

        if(progress!=null)progress.onProgress(8,"외국인·기관 랭킹 확인 중");
        Map<String,Integer> foreign=fetchInvestorRanks("FOREIGNER");
        Map<String,Integer> institution=fetchInstitutionRanks();

        ExecutorService ex=Executors.newFixedThreadPool(6);
        ArrayList<Future<Candidate>> futures=new ArrayList<>();
        final int total=candidates.size();
        for(Candidate c:candidates){
            futures.add(ex.submit(()->{
                try{
                    ArrayList<Bar> weekly=fetchBars(c.code,"week",150);
                    if(weekly.size()<45)return null;
                    setupScore(c,weekly);
                    FlowInfo fi=flowFromRanks(c.code,foreign,institution);
                    c.flowScore=fi.score;c.foreignRank=fi.foreignRank;c.institutionRank=fi.institutionRank;
                    c.preScore=c.secondaryScore*0.70+c.flowScore*0.20+c.liquidity*0.10;
                    return c;
                }catch(Exception e){return null;}
            }));
        }
        ex.shutdown();
        ArrayList<Candidate> tech=new ArrayList<>();
        int done=0;
        for(Future<Candidate> f:futures){
            try{Candidate c=f.get();if(c!=null&&c.secondaryScore>=34)tech.add(c);}catch(Exception ignored){}
            done++;
            if(progress!=null)progress.onProgress(10+(int)(55.0*done/Math.max(1,total)),"주봉 준비패턴 "+done+"/"+total);
        }
        if(tech.size()<20)throw new Exception("주봉 준비패턴을 충족한 종목이 부족합니다.");
        tech.sort((a,b)->Double.compare(b.preScore,a.preScore));
        if(tech.size()>30)tech=new ArrayList<>(tech.subList(0,30));

        if(progress!=null)progress.onProgress(67,"뉴스 촉매 분석 중");
        ExecutorService nx=Executors.newFixedThreadPool(4);
        ArrayList<Future<Candidate>> nf=new ArrayList<>();
        for(Candidate c:tech){
            nf.add(nx.submit(()->{
                try{
                    c.news=fetchNews(c.code,c.name,c.market,true);
                    c.newsScore=c.news.score;
                }catch(Exception e){c.newsScore=50;}
                c.finalScore=c.secondaryScore*0.55+c.flowScore*0.20+c.newsScore*0.15+c.liquidity*0.10;
                if(c.overheat>=8)c.finalScore-=5;
                if(c.ret4w>18)c.finalScore-=5;
                if(c.breakoutGap< -2)c.finalScore-=4;
                c.finalScore=clamp(c.finalScore,0,100);
                c.reason=setupReason(c);
                c.detail=setupDetail(c);
                return c;
            }));
        }
        nx.shutdown();
        ArrayList<Candidate> enriched=new ArrayList<>();
        done=0;
        for(Future<Candidate> f:nf){
            try{Candidate c=f.get();if(c!=null)enriched.add(c);}catch(Exception ignored){}
            done++;
            if(progress!=null)progress.onProgress(68+(int)(30.0*done/Math.max(1,nf.size())),"수급·뉴스 종합 "+done+"/"+nf.size());
        }
        enriched.sort((a,b)->Double.compare(b.finalScore,a.finalScore));
        if(enriched.size()>20)enriched=new ArrayList<>(enriched.subList(0,20));
        if(progress!=null)progress.onProgress(100,"상승준비 TOP20 완료");
        return enriched;
    }

    private Analysis analyzeBars(String code,String name,String market,String period,ArrayList<Bar> bars){
        Analysis a=new Analysis();
        a.code=code;a.name=name;a.market=market;a.period=period;a.bars=bars;
        a.current=bars.get(bars.size()-1).close;
        a.changePct=pctChange(a.current,bars.get(bars.size()-2).close);
        a.score=score(bars);
        levels(bars,a.current,a.supports,a.resistances);
        return a;
    }

    private Score score(ArrayList<Bar> b){
        int n=b.size();
        Score s=new Score();
        s.ma5=sma(b,5);s.ma10=sma(b,10);s.ma20=sma(b,20);s.ma60=sma(b,60);s.ma120=sma(b,120);
        double cur=b.get(n-1).close;
        s.rsi=rsi(b,14);s.cmo=cmo(b,9);
        double[][] macd=macd(b);s.macd=macd[0][n-1];s.signal=macd[1][n-1];
        s.stoch=stoch(b,14);
        double[] di=diAdx(b,14);s.plusDi=di[0];s.minusDi=di[1];s.adx=di[2];
        s.volRatio=avgVolume(b,20)>0?b.get(n-1).volume/avgVolume(b,20):1;
        s.ret20=n>20?pctChange(cur,b.get(n-21).close):0;

        double trend=0;
        if(cur>s.ma20)trend+=2;
        if(s.ma5>s.ma20)trend+=1.5;
        if(s.ma10>s.ma20)trend+=1;
        if(s.ma20>s.ma60)trend+=2;
        if(n>30 && smaAt(b,20,n-1)>smaAt(b,20,n-6))trend+=1.5;
        if(s.ma120>0 && cur>s.ma120)trend+=1;
        if(s.plusDi>s.minusDi)trend+=1;
        s.trend=clamp(trend,0,10);

        double mom=0;
        if(s.rsi>=50&&s.rsi<=68)mom+=3; else if(s.rsi>=43&&s.rsi<50)mom+=1.5; else if(s.rsi>68&&s.rsi<=74)mom+=1;
        if(s.macd>s.signal)mom+=2.5;
        if(s.cmo>0&&s.cmo<55)mom+=2; else if(s.cmo>=-15)mom+=1;
        if(s.stoch>=35&&s.stoch<=82)mom+=1.5;
        if(s.adx>=18&&s.plusDi>s.minusDi)mom+=1;
        s.momentum=clamp(mom,0,10);

        double vol=4.5;
        if(s.volRatio>=1.1&&s.volRatio<=2.4)vol=7.5;
        if(s.volRatio>1.4&&s.volRatio<=2.1)vol=9;
        if(s.volRatio>2.8)vol=6.5;
        if(s.volRatio<0.55)vol=3;
        if(n>10 && avgVolumeRange(b,n-5,n)>avgVolumeRange(b,n-10,n-5)*1.15)vol+=1;
        s.volume=clamp(vol,0,10);

        double heat=0;
        if(s.rsi>=70)heat+=2.5;if(s.rsi>=78)heat+=2;
        if(s.stoch>=85)heat+=1.5;
        if(s.ret20>=15)heat+=2;if(s.ret20>=25)heat+=1.5;
        double dist20=s.ma20>0?(cur/s.ma20-1)*100:0;
        if(dist20>=12)heat+=1.5;if(dist20>=20)heat+=1;
        s.overheat=clamp(heat,0,10);

        double attr=4.0+s.trend*0.35+s.momentum*0.18+s.volume*0.10-s.overheat*0.28;
        if(s.rsi>=48&&s.rsi<=64)attr+=0.7;
        if(dist20>=0&&dist20<=7)attr+=0.8;
        s.attractiveness=clamp(attr,0,10);

        s.technical100=clamp((s.trend*0.40+s.momentum*0.25+s.volume*0.15+s.attractiveness*0.20)*10,0,100);
        double grade=s.trend*0.45+s.momentum*0.25+s.volume*0.15+s.attractiveness*0.15;
        if(grade>=8.6)s.grade="A+";else if(grade>=7.8)s.grade="A";else if(grade>=7.0)s.grade="B+";
        else if(grade>=6.2)s.grade="B";else if(grade>=5.0)s.grade="C";else s.grade="D";
        if(s.trend>=8)s.regime="강한 상승추세";else if(s.trend>=6.3)s.regime="상승 우위";else if(s.trend>=4.5)s.regime="중립/전환구간";else s.regime="약세";
        return s;
    }

    private void setupScore(Candidate c,ArrayList<Bar> w){
        int n=w.size(); double cur=w.get(n-1).close;
        double ma5=sma(w,5),ma10=sma(w,10),ma20=sma(w,20),ma60=sma(w,60);
        double ma20Prev=smaAt(w,20,n-6);
        double rsi=rsi(w,14), cmo=cmo(w,9), vr=avgVolume(w,20)>0?w.get(n-1).volume/avgVolume(w,20):1;
        double[][] mm=macd(w); double mg=mm[0][n-1]-mm[1][n-1], old=mm[0][n-3]-mm[1][n-3];
        double high20=0;for(int i=Math.max(0,n-20);i<n;i++)high20=Math.max(high20,w.get(i).high);
        double gap=cur>0?(high20/cur-1)*100:99;
        double ret4=n>4?pctChange(cur,w.get(n-5).close):0;
        double slope=ma20Prev>0?(ma20/ma20Prev-1)*100:0;

        double sc=0;
        if(cur>=ma20)sc+=12;else if(cur>=ma20*0.97)sc+=8;
        if(ma5>ma10)sc+=8;else if(ma5>=ma10*0.98)sc+=5;
        if(slope>0.3)sc+=10;else if(slope>-0.2)sc+=6;
        if(ma20>ma60)sc+=5;else if(ma20>=ma60*0.94)sc+=3;
        sc+=12*band(rsi,48,64,38,73);
        if(mg>0)sc+=8;else if(mg>old)sc+=5;
        if(cmo>=-10&&cmo<=40)sc+=6;else if(cmo>=-25&&cmo<=55)sc+=3;
        sc+=12*band(gap,3,13,-2,25);
        sc+=7*band(ret4,0,11,-7,20);
        sc+=7*band(vr,0.8,1.7,0.35,2.6);
        double heat=0;if(rsi>72)heat+=4;if(ret4>18)heat+=4;if(gap<-2)heat+=3;
        sc-=heat*2;

        c.secondaryScore=clamp(sc,0,100);
        c.weeklyRsi=rsi;c.breakoutGap=gap;c.weeklyVolRatio=vr;c.ma20Slope=slope;c.ret4w=ret4;c.overheat=clamp(heat,0,10);
        c.current=cur;
    }

    private FlowInfo flowForCode(String code){
        try {
            Map<String,Integer> f=fetchInvestorRanks("FOREIGNER");
            Map<String,Integer> i=fetchInstitutionRanks();
            return flowFromRanks(code,f,i);
        } catch(Exception e){ return new FlowInfo(); }
    }

    private FlowInfo flowFromRanks(String code,Map<String,Integer> foreign,Map<String,Integer> institution){
        FlowInfo x=new FlowInfo();
        x.foreignRank=foreign.getOrDefault(code,0);x.institutionRank=institution.getOrDefault(code,0);
        double sc=50;
        sc+=rankBonus(x.foreignRank,26);
        sc+=rankBonus(x.institutionRank,24);
        x.score=clamp(sc,20,100);
        ArrayList<String> p=new ArrayList<>();
        if(x.foreignRank>0)p.add("외국인 순매수 상위 "+x.foreignRank+"위");
        if(x.institutionRank>0)p.add("기관 순매수 상위 "+x.institutionRank+"위");
        if(p.isEmpty())x.summary="외국인·기관 순매수 상위권 미포착(중립)";
        else x.summary=String.join(" · ",p);
        return x;
    }

    private double rankBonus(int rank,double max){
        if(rank<=0)return 0;
        if(rank<=5)return max;
        if(rank<=10)return max*0.78;
        if(rank<=20)return max*0.55;
        if(rank<=30)return max*0.32;
        return max*0.15;
    }

    private Map<String,Integer> fetchInstitutionRanks() {
        String[] types={"ORGANIZATION","INSTITUTION","ORGAN"};
        for(String t:types){
            try{
                Map<String,Integer> m=fetchInvestorRanks(t);
                if(!m.isEmpty())return m;
            }catch(Exception ignored){}
        }
        return new HashMap<>();
    }

    private Map<String,Integer> fetchInvestorRanks(String type) throws Exception {
        String url=STOCK_NAVER+"/api/domestic/market/trend/trendForeignOrg?investorType="+type+
                "&tradeType=KRX&marketType=ALL&startIdx=0&pageSize=40&periodType=DAY";
        JSONObject j=getJson(url);
        ArrayList<Candidate> arr=new ArrayList<>();
        collectCandidates(j,arr);
        LinkedHashMap<String,Integer> out=new LinkedHashMap<>();
        int rank=1;
        for(Candidate c:arr)if(c.code.matches("\\d{6}")&&!out.containsKey(c.code))out.put(c.code,rank++);
        return out;
    }

    private NewsInfo fetchNews(String code,String name,String market,boolean portals){
        NewsInfo n=new NewsInfo();
        try{
            JSONObject j=getJson(STOCK_NAVER+"/api/domestic/detail/news?itemCode="+code+"&page=1&pageSize=20");
            LinkedHashSet<String> titles=new LinkedHashSet<>();
            collectTitles(j,titles);
            n.naverCount=Math.min(20,titles.size());
            n.headlines.addAll(titles);
        }catch(Exception ignored){}

        if(portals){
            try{
                String html=getText("https://search.daum.net/search?w=news&q="+enc(name+" 주식"));
                n.daumCount=countUnique(html,Pattern.compile("https?://(?:v|news)\\.daum\\.net/[^\"'<> ]+"),12);
            }catch(Exception ignored){}
            try{
                String html=getText("https://search.nate.com/search/all.html?q="+enc(name+" 주식"));
                n.nateCount=countUnique(html,Pattern.compile("https?://news\\.nate\\.com/(?:view|view/[^\"'<> ]*)[^\"'<> ]*"),12);
            }catch(Exception ignored){}
        }

        try{
            String suffix=market.toUpperCase(Locale.ROOT).contains("KOSDAQ")||market.contains("코스닥")?".KQ":".KS";
            JSONObject y=getJson("https://query1.finance.yahoo.com/v1/finance/search?q="+enc(code+suffix)+"&quotesCount=1&newsCount=8");
            JSONArray news=y.optJSONArray("news");
            if(news!=null){
                n.globalCount=news.length();
                for(int i=0;i<Math.min(5,news.length());i++){
                    JSONObject x=news.optJSONObject(i);
                    if(x!=null){
                        String t=x.optString("title","");
                        if(!t.isEmpty())n.headlines.add("[해외] "+t);
                    }
                }
            }
        }catch(Exception ignored){}

        String all=String.join(" ",n.headlines).toLowerCase(Locale.ROOT);
        for(String k:POSITIVE)if(all.contains(k.toLowerCase(Locale.ROOT)))n.positiveHits++;
        for(String k:NEGATIVE)if(all.contains(k.toLowerCase(Locale.ROOT)))n.negativeHits++;

        int freq=n.naverCount+n.daumCount+n.nateCount;
        double fscore;
        if(freq==0)fscore=42;else if(freq<=3)fscore=52;else if(freq<=10)fscore=68;else if(freq<=24)fscore=80;else fscore=74;
        double sentiment=clamp(50+n.positiveHits*6-n.negativeHits*10,0,100);
        double global=clamp(45+n.globalCount*6,45,90);
        n.score=clamp(fscore*0.48+sentiment*0.37+global*0.15,0,100);
        n.summary="NAVER "+n.naverCount+" · DAUM "+n.daumCount+" · NATE "+n.nateCount+
                " · 해외 "+n.globalCount+" · 긍정키워드 "+n.positiveHits+" · 위험키워드 "+n.negativeHits;
        return n;
    }

    private List<Candidate> fetchRanking(String orderType,int size) throws Exception {
        String url=STOCK_NAVER+"/api/domestic/market/stock/default?tradeType=KRX&marketType=ALL&orderType="+
                orderType+"&startIdx=0&pageSize="+size;
        JSONObject j=getJson(url);
        ArrayList<Candidate> raw=new ArrayList<>();
        collectCandidates(j,raw);
        LinkedHashMap<String,Candidate> uniq=new LinkedHashMap<>();
        for(Candidate c:raw){
            if(c.code.matches("\\d{6}")&&!c.name.isEmpty()&&!blocked(c.name))uniq.putIfAbsent(c.code,c);
        }
        return new ArrayList<>(uniq.values());
    }

    private void mergeRanking(LinkedHashMap<String,Candidate> pool,List<Candidate> list,int bonus){
        for(Candidate x:list){
            Candidate c=pool.get(x.code);
            if(c==null){x.sourceHits=bonus;pool.put(x.code,x);}
            else{
                c.sourceHits+=bonus;
                if(x.turnover>c.turnover)c.turnover=x.turnover;
                if(x.volume>c.volume)c.volume=x.volume;
                if(x.current>0)c.current=x.current;
                if(x.changePct!=0)c.changePct=x.changePct;
                if(c.name.isEmpty())c.name=x.name;
                if(c.market.isEmpty())c.market=x.market;
            }
        }
    }

    private void prepareLiquidity(ArrayList<Candidate> list){
        double maxTurn=1;
        for(Candidate c:list)maxTurn=Math.max(maxTurn,c.turnover);
        for(Candidate c:list){
            double liq=c.turnover>0?100*Math.log1p(c.turnover)/Math.log1p(maxTurn):50;
            c.liquidity=clamp(liq,0,100);
            double day=clamp(50+c.changePct*5,20,90);
            c.preScore=c.liquidity*0.72+day*0.20+Math.min(8,c.sourceHits*2);
        }
    }

    private Candidate candidateFromBasic(String code,JSONObject j){
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

    private void collectCandidates(Object node,List<Candidate> out){
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;
            String code=firstString(j,"itemCode","code","stockCode","symbol");
            if(code.matches("\\d{6}")){
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

    private void collectTitles(Object node,LinkedHashSet<String> out){
        if(out.size()>=20)return;
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;
            String t=firstString(j,"title","newsTitle","headline","articleTitle");
            if(t.length()>=6&&t.length()<250)out.add(stripHtml(t));
            Iterator<String> it=j.keys();
            while(it.hasNext()&&out.size()<20){
                Object v=j.opt(it.next());
                if(v instanceof JSONObject||v instanceof JSONArray)collectTitles(v,out);
            }
        }else if(node instanceof JSONArray){
            JSONArray a=(JSONArray)node;
            for(int i=0;i<a.length()&&out.size()<20;i++)collectTitles(a.opt(i),out);
        }
    }

    public ArrayList<Bar> fetchBars(String code,String timeframe,int count) throws Exception {
        String url=FCHART+"?symbol="+code+"&timeframe="+timeframe+"&count="+count+"&requestType=0";
        String xml=getText(url);
        Pattern p=Pattern.compile("<item\\s+data=\"([^\"]+)\"");
        Matcher m=p.matcher(xml);
        ArrayList<Bar> bars=new ArrayList<>();
        while(m.find()){
            String[] x=m.group(1).split("\\|");
            if(x.length<6)continue;
            try{
                double o=Double.parseDouble(x[1]),h=Double.parseDouble(x[2]),l=Double.parseDouble(x[3]),c=Double.parseDouble(x[4]),v=Double.parseDouble(x[5]);
                if(c>0)bars.add(new Bar(x[0],o,h,l,c,v));
            }catch(Exception ignored){}
        }
        bars.sort(Comparator.comparing(a->a.date));
        return bars;
    }

    private String buildReport(Analysis a){
        Score s=a.score;
        StringBuilder b=new StringBuilder();
        b.append("[종합 판단]\n");
        b.append(a.name).append("은(는) ").append(s.regime).append(" 구간으로 계산됩니다. ");
        if(s.overheat>=8)b.append("다만 단기 과열도가 높아 추격 접근은 주의가 필요합니다.\n");
        else if(s.attractiveness>=7)b.append("과열 부담이 상대적으로 낮아 눌림 또는 돌파 확인 구간을 살펴볼 수 있습니다.\n");
        else b.append("추세 확인과 지지선 관리가 우선입니다.\n");

        b.append("\n[기술지표]\n");
        b.append(String.format(Locale.KOREA,"RSI %.1f · CMO %.1f · MACD %.2f / Signal %.2f · ADX %.1f\n",
                s.rsi,s.cmo,s.macd,s.signal,s.adx));
        b.append(String.format(Locale.KOREA,"MA5 %,.0f · MA20 %,.0f · MA60 %,.0f · 거래량 20평균 대비 %.2f배\n",
                s.ma5,s.ma20,s.ma60,s.volRatio));
        b.append("20기간 수익률 ").append(String.format(Locale.KOREA,"%+.1f%%",s.ret20)).append("\n");

        b.append("\n[지지·저항]\n지지 ");
        b.append(priceList(a.supports)).append("\n저항 ").append(priceList(a.resistances)).append("\n");

        if(a.flow!=null)b.append("\n[외국인·기관]\n").append(a.flow.summary).append(" · 수급점수 ").append(f1(a.flow.score)).append("/100\n");
        if(a.news!=null){
            b.append("\n[뉴스]\n").append(a.news.summary).append(" · 뉴스점수 ").append(f1(a.news.score)).append("/100\n");
            int lim=Math.min(6,a.news.headlines.size());
            for(int i=0;i<lim;i++)b.append("- ").append(a.news.headlines.get(i)).append("\n");
        }
        b.append("\n※ 스마트폰에서 공개 시세·시장·뉴스 데이터를 직접 조회해 계산한 참고 분석입니다. 매수·수익을 보장하지 않습니다.");
        return b.toString();
    }

    private String topReason(Candidate c){
        ArrayList<String> r=new ArrayList<>();
        if(c.score!=null&&c.score.trend>=7.5)r.add("강한 상승추세");
        if(c.score!=null&&c.score.volRatio>=1.4)r.add("거래량 증가");
        if(c.foreignRank>0)r.add("외국인 상위");
        if(c.institutionRank>0)r.add("기관 상위");
        if(c.newsScore>=65)r.add("뉴스 관심 증가");
        if(c.overheat>=8.5)r.add("과열 주의");
        if(r.isEmpty())r.add("차트·수급·뉴스 종합점수 상위");
        return String.join(" · ",r);
    }

    private String topDetail(Candidate c){
        StringBuilder b=new StringBuilder();
        b.append("[오늘의 TOP10] ").append(c.name).append(" (").append(c.code).append(")\n");
        b.append("종합 ").append(f1(c.finalScore)).append(" · 기술 ").append(f1(c.secondaryScore))
                .append(" · 수급 ").append(f1(c.flowScore)).append(" · 뉴스 ").append(f1(c.newsScore)).append("\n\n");
        if(c.score!=null)b.append("추세 ").append(f1(c.score.trend)).append("/10 · 모멘텀 ").append(f1(c.score.momentum))
                .append("/10 · 거래량 ").append(f1(c.score.volume)).append("/10 · 과열 ").append(f1(c.score.overheat)).append("/10\n");
        b.append("현재가 ").append(money(c.current)).append("원 · 당일 ").append(String.format(Locale.KOREA,"%+.2f%%",c.changePct)).append("\n");
        b.append("외국인 ").append(c.foreignRank>0?c.foreignRank+"위":"상위권 미포착")
                .append(" · 기관 ").append(c.institutionRank>0?c.institutionRank+"위":"상위권 미포착").append("\n");
        if(c.news!=null){
            b.append(c.news.summary).append("\n\n");
            int lim=Math.min(8,c.news.headlines.size());
            for(int i=0;i<lim;i++)b.append("- ").append(c.news.headlines.get(i)).append("\n");
        }
        b.append("\n선정: ").append(c.reason).append("\n\n※ 투자 보조용 순위입니다.");
        return b.toString();
    }

    private String setupReason(Candidate c){
        ArrayList<String> r=new ArrayList<>();
        if(c.ma20Slope>0)r.add("20주선 상승전환");
        if(c.weeklyRsi>=48&&c.weeklyRsi<=65)r.add("주봉 RSI 건강");
        if(c.breakoutGap>=2&&c.breakoutGap<=15)r.add("20주 고점 접근");
        if(c.weeklyVolRatio>=1.0&&c.weeklyVolRatio<=2.0)r.add("주봉 거래량 회복");
        if(c.foreignRank>0||c.institutionRank>0)r.add("수급 포착");
        if(c.newsScore>=65)r.add("뉴스 촉매");
        if(r.isEmpty())r.add("주봉 전환 준비점수 상위");
        return String.join(" · ",r);
    }

    private String setupDetail(Candidate c){
        StringBuilder b=new StringBuilder();
        b.append("[상승준비 TOP20] ").append(c.name).append(" (").append(c.code).append(")\n");
        b.append("준비종합 ").append(f1(c.finalScore)).append(" · 주봉셋업 ").append(f1(c.secondaryScore))
                .append(" · 수급 ").append(f1(c.flowScore)).append(" · 뉴스 ").append(f1(c.newsScore)).append("\n\n");
        b.append(String.format(Locale.KOREA,"주봉 RSI %.1f · 20주선 기울기 %+.2f%% · 4주 %+.1f%%\n",
                c.weeklyRsi,c.ma20Slope,c.ret4w));
        b.append(String.format(Locale.KOREA,"20주 고점까지 %.1f%% · 주봉 거래량 %.2f배\n",c.breakoutGap,c.weeklyVolRatio));
        b.append("현재가 ").append(money(c.current)).append("원\n");
        b.append("외국인 ").append(c.foreignRank>0?c.foreignRank+"위":"상위권 미포착")
                .append(" · 기관 ").append(c.institutionRank>0?c.institutionRank+"위":"상위권 미포착").append("\n");
        if(c.news!=null){
            b.append(c.news.summary).append("\n\n");
            int lim=Math.min(8,c.news.headlines.size());
            for(int i=0;i<lim;i++)b.append("- ").append(c.news.headlines.get(i)).append("\n");
        }
        b.append("\n선정: ").append(c.reason).append("\n\n※ '상승준비'는 미래 상승 보장이 아니라 초기 후보 탐색용입니다.");
        return b.toString();
    }

    private void levels(ArrayList<Bar>b,double cur,ArrayList<Double> sup,ArrayList<Double> res){
        ArrayList<Double> lows=new ArrayList<>(), highs=new ArrayList<>();
        int n=b.size(), start=Math.max(2,n-80);
        for(int i=start;i<n-2;i++){
            double l=b.get(i).low,h=b.get(i).high;
            if(l<=b.get(i-1).low&&l<=b.get(i+1).low&&l<cur)lows.add(l);
            if(h>=b.get(i-1).high&&h>=b.get(i+1).high&&h>cur)highs.add(h);
        }
        double ma20=sma(b,20),ma60=sma(b,60);
        if(ma20>0){if(ma20<cur)lows.add(ma20);else highs.add(ma20);}
        if(ma60>0){if(ma60<cur)lows.add(ma60);else highs.add(ma60);}
        lows.sort((a,c)->Double.compare(c,a));highs.sort(Double::compare);
        cluster(lows,sup,cur,3);cluster(highs,res,cur,3);
    }

    private void cluster(List<Double> input,List<Double> out,double cur,int limit){
        for(double v:input){
            boolean close=false;for(double x:out)if(Math.abs(v/x-1)<0.018){close=true;break;}
            if(!close&&v>0){out.add(v);if(out.size()>=limit)break;}
        }
    }

    private static double sma(ArrayList<Bar>b,int p){return smaAt(b,p,b.size()-1);}
    private static double smaAt(ArrayList<Bar>b,int p,int end){
        if(end<0)return 0;int s=Math.max(0,end-p+1);if(end-s+1<p)return 0;
        double x=0;for(int i=s;i<=end;i++)x+=b.get(i).close;return x/p;
    }
    private static double avgVolume(ArrayList<Bar>b,int p){return avgVolumeRange(b,Math.max(0,b.size()-p),b.size());}
    private static double avgVolumeRange(ArrayList<Bar>b,int start,int end){
        start=Math.max(0,start);end=Math.min(b.size(),end);if(end<=start)return 0;
        double x=0;for(int i=start;i<end;i++)x+=b.get(i).volume;return x/(end-start);
    }
    private static double rsi(ArrayList<Bar>b,int p){
        if(b.size()<=p)return 50;double g=0,l=0;
        for(int i=b.size()-p;i<b.size();i++){double d=b.get(i).close-b.get(i-1).close;if(d>0)g+=d;else l-=d;}
        if(l==0)return g>0?100:50;double rs=(g/p)/(l/p);return 100-100/(1+rs);
    }
    private static double cmo(ArrayList<Bar>b,int p){
        if(b.size()<=p)return 0;double g=0,l=0;
        for(int i=b.size()-p;i<b.size();i++){double d=b.get(i).close-b.get(i-1).close;if(d>0)g+=d;else l-=d;}
        return g+l==0?0:100*(g-l)/(g+l);
    }
    private static double stoch(ArrayList<Bar>b,int p){
        int n=b.size(),s=Math.max(0,n-p);double hi=-Double.MAX_VALUE,lo=Double.MAX_VALUE;
        for(int i=s;i<n;i++){hi=Math.max(hi,b.get(i).high);lo=Math.min(lo,b.get(i).low);}
        return hi==lo?50:100*(b.get(n-1).close-lo)/(hi-lo);
    }
    private static double[][] macd(ArrayList<Bar>b){
        int n=b.size();double[] close=new double[n];for(int i=0;i<n;i++)close[i]=b.get(i).close;
        double[] e12=ema(close,12),e26=ema(close,26),m=new double[n];for(int i=0;i<n;i++)m[i]=e12[i]-e26[i];
        double[] sig=ema(m,9);return new double[][]{m,sig};
    }
    private static double[] ema(double[]x,int p){
        double[]o=new double[x.length];if(x.length==0)return o;double a=2.0/(p+1);o[0]=x[0];
        for(int i=1;i<x.length;i++)o[i]=a*x[i]+(1-a)*o[i-1];return o;
    }
    private static double[] diAdx(ArrayList<Bar>b,int p){
        if(b.size()<=p+1)return new double[]{20,20,20};double tr=0,plus=0,minus=0;
        for(int i=b.size()-p;i<b.size();i++){
            Bar c=b.get(i),pr=b.get(i-1);double up=c.high-pr.high,down=pr.low-c.low;
            plus+=up>down&&up>0?up:0;minus+=down>up&&down>0?down:0;
            tr+=Math.max(c.high-c.low,Math.max(Math.abs(c.high-pr.close),Math.abs(c.low-pr.close)));
        }
        if(tr==0)return new double[]{0,0,0};double pdi=100*plus/tr,mdi=100*minus/tr;
        double adx=(pdi+mdi)==0?0:100*Math.abs(pdi-mdi)/(pdi+mdi);
        return new double[]{pdi,mdi,adx};
    }
    private static double band(double v,double bl,double bh,double ol,double oh){
        if(v>=bl&&v<=bh)return 1;if(v<ol||v>oh)return 0;
        if(v<bl)return (v-ol)/(bl-ol);return (oh-v)/(oh-bh);
    }

    private static boolean blocked(String name){
        String u=name==null?"":name.toUpperCase(Locale.ROOT);
        for(String k:BLOCKED)if(u.contains(k.toUpperCase(Locale.ROOT)))return true;
        return false;
    }

    private static Object first(JSONObject j,String...keys){for(String k:keys)if(j.has(k)&&!j.isNull(k))return j.opt(k);return null;}
    private static String firstString(JSONObject j,String...keys){
        Object v=first(j,keys);if(v==null)return "";if(v instanceof JSONObject||v instanceof JSONArray)return "";
        return String.valueOf(v).trim();
    }
    private static double num(Object v){
        if(v==null||v==JSONObject.NULL)return 0;
        if(v instanceof Number)return ((Number)v).doubleValue();
        String s=String.valueOf(v).replace(",","").replace("%","").replace("+","").trim();
        boolean neg=s.startsWith("-");s=s.replaceAll("[^0-9.\\-]","");
        if(s.isEmpty()||"-".equals(s))return 0;
        try{return Double.parseDouble(s);}catch(Exception e){return 0;}
    }
    private static double pctChange(double a,double b){return b==0?0:(a/b-1)*100;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static String f1(double v){return String.format(Locale.KOREA,"%.1f",v);}
    private static String money(double v){return String.format(Locale.KOREA,"%,.0f",v);}
    private static String priceList(List<Double>x){if(x==null||x.isEmpty())return "-";ArrayList<String>a=new ArrayList<>();for(double v:x)a.add(money(v)+"원");return String.join(" / ",a);}
    private static String stripHtml(String s){return s.replaceAll("<[^>]+>","").replace("&quot;","\"").replace("&amp;","&").replace("&#39;","'").trim();}
    private static String enc(String s){try{return URLEncoder.encode(s,StandardCharsets.UTF_8.toString());}catch(Exception e){return s;}}
    private static int countUnique(String text,Pattern p,int limit){
        LinkedHashSet<String>s=new LinkedHashSet<>();Matcher m=p.matcher(text);while(m.find()&&s.size()<limit)s.add(m.group());return s.size();
    }

    private JSONObject getJson(String url) throws Exception {
        String text=getText(url).trim();
        if(text.startsWith("[")){
            JSONObject wrap=new JSONObject();
            wrap.put("items",new JSONArray(text));
            return wrap;
        }
        return new JSONObject(text);
    }

    private String getText(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(12000);c.setReadTimeout(25000);
        c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept","application/json,text/plain,*/*");
        c.setRequestProperty("Referer","https://stock.naver.com/");
        int status=c.getResponseCode();
        InputStream in=status>=200&&status<300?c.getInputStream():c.getErrorStream();
        if(in==null){c.disconnect();throw new Exception("응답이 비어 있습니다: "+url);}
        BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line).append('\n');r.close();c.disconnect();
        if(status<200||status>=300)throw new Exception("HTTP "+status+" · "+url);
        return b.toString();
    }
}
