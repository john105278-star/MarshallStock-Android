package com.marshall.stockai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class MarketHub {
    private static final String UA="Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36";

    public static class Quote {
        public String code="", name="", market="";
        public double price, changePct, volume, turnover;
        public String tradedAt="";
        public boolean ok;
    }

    public static class IndexQuote {
        public String code="", name="";
        public double price, changePct;
        public boolean ok;
    }

    public static class InvestorPulse {
        public double personal, foreigner, institution;
        public String date="";
        public boolean ok;
    }

    public static class Sector {
        public String name="";
        public double changeRate, turnover, score;
        public int newsHits;
        public String note="";
    }

    public Quote quote(String code) {
        Quote q=new Quote();q.code=code;
        try {
            Object root=getJson("https://m.stock.naver.com/api/stock/"+code+"/basic");
            if(root instanceof JSONObject){
                JSONObject j=(JSONObject)root;
                q.name=str(j,"stockName","itemName","name","stock_name");
                q.market=str(j,"marketName","marketType","market","stockExchangeType");
                q.price=num(j,"closePrice","nowPrice","currentPrice","price");
                q.changePct=num(j,"fluctuationsRatio","changeRate","changePct","compareToPreviousClosePriceRatio");
                q.volume=num(j,"accumulatedTradingVolume","accumulatedTradingVolumeKrx","tradingVolume","volume");
                q.turnover=num(j,"accumulatedTradingValue","accumulatedTradingValueKrx","tradingValue","turnover");
                q.tradedAt=str(j,"localTradedAt","tradedAt","dateTime");
            }
        } catch(Exception ignored){}
        if(q.price<=0){
            try{
                Object root=getJson("https://polling.finance.naver.com/api/realtime?query=SERVICE_ITEM:"+code);
                JSONObject d=findFirstObjectWithKey(root,"nv");
                if(d!=null){
                    q.price=number(d.opt("nv"));
                    q.changePct=number(d.opt("cr"));
                    q.volume=number(d.opt("aq"));
                    q.turnover=number(d.opt("aa"));
                    if(q.name.isEmpty())q.name=d.optString("nm",code);
                }
            }catch(Exception ignored){}
        }
        if(q.name.isEmpty())q.name=code;
        q.ok=q.price>0;
        return q;
    }

    public IndexQuote indexQuote(String code) {
        IndexQuote q=new IndexQuote();q.code=code;q.name=code;
        try{
            Object root=getJson("https://m.stock.naver.com/api/index/"+code+"/basic");
            if(root instanceof JSONObject){
                JSONObject j=(JSONObject)root;
                q.name=str(j,"stockName","indexName","name");
                q.price=num(j,"closePrice","nowPrice","currentPrice");
                q.changePct=num(j,"fluctuationsRatio","changeRate","changePct");
            }
        }catch(Exception ignored){}
        if(q.price<=0){
            try{
                Object root=getJson("https://polling.finance.naver.com/api/realtime?query=SERVICE_INDEX:"+code);
                JSONObject d=findFirstObjectWithKey(root,"nv");
                if(d!=null){q.price=number(d.opt("nv"));q.changePct=number(d.opt("cr"));}
            }catch(Exception ignored){}
        }
        q.ok=q.price>0;return q;
    }

    public InvestorPulse investorPulse(){
        InvestorPulse p=new InvestorPulse();
        Calendar cal=Calendar.getInstance();
        for(int tryDay=0;tryDay<8;tryDay++){
            String date=new SimpleDateFormat("yyyyMMdd",Locale.KOREA).format(cal.getTime());
            try{
                Object root=getJson("https://stock.naver.com/api/domestic/market/trend/daily?tradeType=KRX&marketType=ALL&bizdate="+date+"&startIdx=0&pageSize=5");
                double pe=findNumberByKey(root,new String[]{"individual","personal","private"});
                double fo=findNumberByKey(root,new String[]{"foreigner","foreign"});
                double in=findNumberByKey(root,new String[]{"institution","organization","organ"});
                if(pe!=0||fo!=0||in!=0){p.personal=pe;p.foreigner=fo;p.institution=in;p.date=date;p.ok=true;return p;}
            }catch(Exception ignored){}
            cal.add(Calendar.DAY_OF_MONTH,-1);
        }
        return p;
    }

    public List<Sector> sectors() throws Exception {
        Object root=getJson("https://stock.naver.com/api/domestic/home/upjongTheme/ranking?sortType=changeRate");
        ArrayList<Sector> out=new ArrayList<>();
        LinkedHashSet<String> seen=new LinkedHashSet<>();
        collectSectorObjects(root,out,seen);
        List<String> news=flashHeadlines();
        for(Sector s:out){
            int hits=0;
            String needle=s.name.replace(" ","");
            for(String h:news)if(h.replace(" ","").contains(needle))hits++;
            s.newsHits=hits;
            double liquidityBonus=s.turnover>0?Math.min(8,Math.log10(Math.max(10,s.turnover))/2.0):0;
            s.score=clamp(50+s.changeRate*8+hits*4+liquidityBonus,0,100);
            if(s.score>=78)s.note="강한 상승 섹터";
            else if(s.score>=65)s.note="상승 우위";
            else if(s.score>=52)s.note="관심 구간";
            else s.note="중립/약세";
        }
        out.sort((a,b)->Double.compare(b.score,a.score));
        if(out.size()>15)return new ArrayList<>(out.subList(0,15));
        return out;
    }

    public List<String> flashHeadlines(){
        ArrayList<String> out=new ArrayList<>();
        String[] urls={
                "https://m.stock.naver.com/front-api/news/category?category=flashnews&page=1&pageSize=80",
                "https://stock.naver.com/api/domestic/news/category?category=flashnews&page=1&pageSize=80"
        };
        for(String url:urls){
            try{Object root=getJson(url);collectStringsByKey(root,out,new String[]{"title","headline","articleTitle"});if(out.size()>10)break;}catch(Exception ignored){}
        }
        LinkedHashSet<String> uniq=new LinkedHashSet<>(out);return new ArrayList<>(uniq);
    }

    private void collectSectorObjects(Object node,List<Sector> out,Set<String> seen){
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;
            String name=str(j,"upjongName","themeName","sectorName","name");
            double rate=num(j,"changeRate","fluctuationsRatio","changePct","rate");
            double value=num(j,"tradingValue","turnover","accumulatedTradingValue");
            if(!name.isEmpty()&&name.length()<=40&&Math.abs(rate)<=100&&!seen.contains(name)){
                boolean looksSector=j.has("upjongCode")||j.has("themeCode")||j.has("sectorCode")||j.has("rank")||j.has("ranking");
                if(looksSector){Sector s=new Sector();s.name=name;s.changeRate=rate;s.turnover=value;out.add(s);seen.add(name);}
            }
            Iterator<String> it=j.keys();while(it.hasNext()){String k=it.next();collectSectorObjects(j.opt(k),out,seen);}
        }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectSectorObjects(a.opt(i),out,seen);}
    }

    private JSONObject findFirstObjectWithKey(Object node,String key){
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;if(j.has(key))return j;
            Iterator<String> it=j.keys();while(it.hasNext()){JSONObject x=findFirstObjectWithKey(j.opt(it.next()),key);if(x!=null)return x;}
        }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++){JSONObject x=findFirstObjectWithKey(a.opt(i),key);if(x!=null)return x;}}
        return null;
    }

    private double findNumberByKey(Object node,String[] aliases){
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;Iterator<String> it=j.keys();
            while(it.hasNext()){
                String k=it.next(),lk=k.toLowerCase(Locale.ROOT);
                for(String a:aliases)if(lk.contains(a)){double v=number(j.opt(k));if(v!=0)return v;}
            }
            it=j.keys();while(it.hasNext()){double v=findNumberByKey(j.opt(it.next()),aliases);if(v!=0)return v;}
        }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++){double v=findNumberByKey(a.opt(i),aliases);if(v!=0)return v;}}
        return 0;
    }

    private void collectStringsByKey(Object node,List<String> out,String[] keys){
        if(out.size()>=100)return;
        if(node instanceof JSONObject){
            JSONObject j=(JSONObject)node;
            for(String k:keys){String s=j.optString(k,"").trim();if(s.length()>4&&s.length()<180)out.add(s);}
            Iterator<String> it=j.keys();while(it.hasNext())collectStringsByKey(j.opt(it.next()),out,keys);
        }else if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)collectStringsByKey(a.opt(i),out,keys);}
    }

    private Object getJson(String u) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Accept","application/json,text/plain,*/*");
        int code=c.getResponseCode();InputStream in=code>=200&&code<400?c.getInputStream():c.getErrorStream();
        if(in==null)throw new Exception("HTTP "+code);
        BufferedReader r=new BufferedReader(new InputStreamReader(in));StringBuilder sb=new StringBuilder();String line;
        while((line=r.readLine())!=null)sb.append(line);r.close();c.disconnect();
        if(code<200||code>=400)throw new Exception("HTTP "+code);
        return new JSONTokener(sb.toString()).nextValue();
    }

    private String str(JSONObject j,String... keys){for(String k:keys){String s=j.optString(k,"");if(!s.isEmpty()&&!"null".equalsIgnoreCase(s))return s;}return "";}
    private double num(JSONObject j,String... keys){for(String k:keys){double v=number(j.opt(k));if(v!=0)return v;}return 0;}
    private double number(Object o){
        if(o==null||o==JSONObject.NULL)return 0;
        if(o instanceof Number)return ((Number)o).doubleValue();
        String s=String.valueOf(o).replace(",","").replace("%","").replace("+","").trim();
        if(s.isEmpty()||"-".equals(s))return 0;try{return Double.parseDouble(s);}catch(Exception e){return 0;}
    }
    private double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
