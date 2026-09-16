package com.stockradar.app.api;

import com.stockradar.app.core.*;
import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class KiwoomClient {
    private final String appKey;
    private final String secret;
    private final boolean mock;
    private String token;
    private long tokenIssuedAt;
    private List<Candidate> stockMasterCache;

    public record Candidate(String code, String name) {}
    public record MarketSnapshot(String label, double changePct, int rising, int falling) {
        public String summary(){
            String breadth = rising + falling == 0 ? "" : String.format(Locale.KOREA," · 상승 %d / 하락 %d", rising, falling);
            return String.format(Locale.KOREA,"%s %+.2f%%%s", label, changePct, breadth);
        }
    }
    private record Page(JSONObject body, String contYn, String nextKey) {}

    public KiwoomClient(String appKey, String secret, boolean mock) {
        this.appKey = appKey;
        this.secret = secret;
        this.mock = mock;
    }

    private String host() { return mock ? "https://mockapi.kiwoom.com" : "https://api.kiwoom.com"; }

    public synchronized String getToken() throws IOException, JSONException {
        if (token != null && System.currentTimeMillis() - tokenIssuedAt < 23L*60*60*1000) return token;
        JSONObject body = new JSONObject();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("secretkey", secret);
        JSONObject res = rawPost(host()+"/oauth2/token", body, null, null, null).body;
        if (res.optInt("return_code",0) != 0 && !res.has("token")) throw new IOException(res.optString("return_msg","토큰 발급 실패"));
        token = res.getString("token");
        tokenIssuedAt = System.currentTimeMillis();
        return token;
    }

    public Candidate resolveStock(String query) throws Exception {
        String q=query==null?"":query.trim();
        if(q.isBlank()) throw new IllegalArgumentException("종목명 또는 종목코드를 입력하세요.");
        if(q.matches("\\d{6}")) return new Candidate(q,q);
        List<Candidate> all=loadStockMaster();
        for(Candidate c: all) if(c.name().equalsIgnoreCase(q)) return c;
        List<Candidate> contains=new ArrayList<>();
        String nq=normalize(q);
        for(Candidate c: all) if(normalize(c.name()).contains(nq)) contains.add(c);
        if(contains.isEmpty()) throw new IllegalArgumentException("'"+q+"' 종목을 찾지 못했습니다.");
        if(contains.size()==1) return contains.get(0);
        StringBuilder sb=new StringBuilder("여러 종목이 검색됩니다: ");
        for(int i=0;i<Math.min(5,contains.size());i++){
            if(i>0) sb.append(", ");
            sb.append(contains.get(i).name()).append("(").append(contains.get(i).code()).append(")");
        }
        throw new IllegalArgumentException(sb.toString());
    }

    private static String normalize(String s){return s==null?"":s.replace(" ","").toLowerCase(Locale.KOREA);}

    private synchronized List<Candidate> loadStockMaster() throws Exception {
        if(stockMasterCache!=null) return stockMasterCache;
        LinkedHashMap<String,Candidate> map=new LinkedHashMap<>();
        fetchMasterMarket("0",map);
        Thread.sleep(220);
        fetchMasterMarket("10",map);
        stockMasterCache=new ArrayList<>(map.values());
        return stockMasterCache;
    }

    private void fetchMasterMarket(String market, Map<String,Candidate> out) throws Exception {
        JSONObject b=new JSONObject(); b.put("mrkt_tp",market);
        String cont=null,next=null;
        for(int page=0;page<10;page++){
            Page p=apiPost("ka10099","/api/dostk/stkinfo",b,cont,next);
            JSONArray arr=p.body.optJSONArray("list");
            if(arr!=null) for(int i=0;i<arr.length();i++){
                JSONObject x=arr.optJSONObject(i); if(x==null) continue;
                String code=x.optString("code",""); String name=x.optString("name",code);
                if(!code.isBlank()) out.putIfAbsent(code,new Candidate(code,name));
            }
            if(!"Y".equalsIgnoreCase(p.contYn)) break;
            cont=p.contYn; next=p.nextKey; Thread.sleep(220);
        }
    }

    public List<Candidate> fetchVolumeSurgeCandidates(int max) throws Exception {
        JSONObject b = new JSONObject();
        b.put("mrkt_tp","000"); b.put("sort_tp","2"); b.put("tm_tp","2");
        b.put("trde_qty_tp","50"); b.put("stk_cnd","4"); b.put("pric_tp","8"); b.put("stex_tp","3"); b.put("tm","");
        Page p = apiPost("ka10023","/api/dostk/rkinfo", b, null, null);
        JSONArray arr = p.body.optJSONArray("trde_qty_sdnin");
        List<Candidate> out = new ArrayList<>();
        if (arr != null) for (int i=0;i<arr.length() && out.size()<max;i++) {
            JSONObject x=arr.optJSONObject(i); if (x==null) continue;
            String code=x.optString("stk_cd",""); String name=x.optString("stk_nm",code);
            if (!code.isBlank()) out.add(new Candidate(code,name));
        }
        return out;
    }

    public List<Candidate> fetchInstitutionBuyCandidates(int max) throws Exception {
        JSONObject b=new JSONObject();
        b.put("mrkt_tp","000"); b.put("amt_qty_tp","1"); b.put("qry_dt_tp","0"); b.put("stex_tp","3");
        Page p=apiPost("ka90009","/api/dostk/rkinfo",b,null,null);
        JSONArray arr=p.body.optJSONArray("frgnr_orgn_trde_upper");
        List<Candidate> out=new ArrayList<>();
        if(arr!=null) for(int i=0;i<arr.length() && out.size()<max;i++){
            JSONObject x=arr.optJSONObject(i); if(x==null) continue;
            String code=x.optString("orgn_netprps_stk_cd",""); String name=x.optString("orgn_netprps_stk_nm",code);
            if(!code.isBlank()) out.add(new Candidate(code,name));
        }
        return out;
    }

    public List<Candidate> fetchForeignBuyCandidates(int max) throws Exception {
        JSONObject b=new JSONObject();
        b.put("mrkt_tp","000"); b.put("amt_qty_tp","1"); b.put("qry_dt_tp","0"); b.put("stex_tp","3");
        Page p=apiPost("ka90009","/api/dostk/rkinfo",b,null,null);
        JSONArray arr=p.body.optJSONArray("frgnr_orgn_trde_upper");
        List<Candidate> out=new ArrayList<>();
        if(arr!=null) for(int i=0;i<arr.length() && out.size()<max;i++){
            JSONObject x=arr.optJSONObject(i); if(x==null) continue;
            String code=x.optString("for_netprps_stk_cd",""); String name=x.optString("for_netprps_stk_nm",code);
            if(!code.isBlank()) out.add(new Candidate(code,name));
        }
        return out;
    }

    public List<Candidate> fetchTradingValueCandidates(int max) throws Exception {
        JSONObject b=new JSONObject(); b.put("mrkt_tp","000"); b.put("mang_stk_incls","0"); b.put("stex_tp","3");
        Page p=apiPost("ka10032","/api/dostk/rkinfo",b,null,null);
        JSONArray arr=p.body.optJSONArray("trde_prica_upper");
        List<Candidate> out=new ArrayList<>();
        if(arr!=null) for(int i=0;i<arr.length() && out.size()<max;i++){
            JSONObject x=arr.optJSONObject(i); if(x==null) continue;
            String code=x.optString("stk_cd",""); String name=x.optString("stk_nm",code);
            if(!code.isBlank()) out.add(new Candidate(code,name));
        }
        return out;
    }

    public String fetchMarketSummary() throws Exception {
        MarketSnapshot k=fetchMarketSnapshot("0","001","KOSPI");
        Thread.sleep(220);
        MarketSnapshot q=fetchMarketSnapshot("1","101","KOSDAQ");
        String regime;
        double avg=(k.changePct()+q.changePct())/2.0;
        if(avg>=1.0) regime="강세장";
        else if(avg>=0.2) regime="우호적";
        else if(avg<=-1.0) regime="약세장";
        else if(avg<=-0.2) regime="주의";
        else regime="중립";
        return "오늘 시황: "+regime+"\n"+k.summary()+"\n"+q.summary();
    }

    private MarketSnapshot fetchMarketSnapshot(String market,String inds,String label) throws Exception {
        JSONObject b=new JSONObject(); b.put("mrkt_tp",market); b.put("inds_cd",inds);
        Page p=apiPost("ka20001","/api/dostk/sect",b,null,null);
        JSONObject x=p.body;
        return new MarketSnapshot(label,num(x.optString("flu_rt")),(int)num(x.optString("rising")),(int)num(x.optString("fall")));
    }

    public StockData fetchStock(String code, String name) throws Exception {
        String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<MarketBar> daily = fetchBars("ka10081","stk_dt_pole_chart_qry", code, today);
        List<MarketBar> weekly = fetchBars("ka10082","stk_stk_pole_chart_qry", code, today);
        List<InvestorFlow> flows = fetchFlows(code,today);
        return new StockData(code,name,daily,weekly,flows);
    }

    private List<MarketBar> fetchBars(String apiId, String arrayKey, String code, String today) throws Exception {
        JSONObject b = new JSONObject(); b.put("stk_cd",code); b.put("base_dt",today); b.put("upd_stkpc_tp","1");
        List<MarketBar> out = new ArrayList<>(); String cont=null, next=null;
        for (int page=0; page<4; page++) {
            Page p=apiPost(apiId,"/api/dostk/chart",b,cont,next);
            JSONArray arr=p.body.optJSONArray(arrayKey);
            if (arr!=null) for(int i=0;i<arr.length();i++) {
                JSONObject x=arr.optJSONObject(i); if(x==null) continue;
                out.add(new MarketBar(x.optString("dt"), absNum(x.optString("open_pric")), absNum(x.optString("high_pric")), absNum(x.optString("low_pric")), absNum(x.optString("cur_prc")), (long)absNum(x.optString("trde_qty"))));
            }
            if (!"Y".equalsIgnoreCase(p.contYn)) break; cont=p.contYn; next=p.nextKey;
            if (out.size() >= 180) break;
            Thread.sleep(220);
        }
        out.sort(Comparator.comparing(MarketBar::date));
        return out;
    }

    private List<InvestorFlow> fetchFlows(String code, String today) throws Exception {
        JSONObject b=new JSONObject(); b.put("dt",today); b.put("stk_cd",code); b.put("amt_qty_tp","2"); b.put("trde_tp","0"); b.put("unit_tp","1");
        List<InvestorFlow> out=new ArrayList<>(); String cont=null,next=null;
        for(int page=0;page<3;page++) {
            Page p=apiPost("ka10059","/api/dostk/stkinfo",b,cont,next);
            JSONArray arr=p.body.optJSONArray("stk_invsr_orgn");
            if(arr!=null) for(int i=0;i<arr.length();i++) {
                JSONObject x=arr.optJSONObject(i); if(x==null) continue;
                out.add(new InvestorFlow(x.optString("dt"), longNum(x.optString("acc_trde_qty")), longNum(x.optString("ind_invsr")), longNum(x.optString("frgnr_invsr")), longNum(x.optString("orgn")), longNum(x.optString("fnnc_invt")), longNum(x.optString("insrnc")), longNum(x.optString("invtrt")), longNum(x.optString("etc_fnnc")), longNum(x.optString("bank")), longNum(x.optString("penfnd_etc")), longNum(x.optString("samo_fund")), longNum(x.optString("natn")), longNum(x.optString("etc_corp")), longNum(x.optString("natfor")), num(x.optString("flu_rt"))));
            }
            if(!"Y".equalsIgnoreCase(p.contYn)) break; cont=p.contYn; next=p.nextKey;
            if(out.size()>=30) break;
            Thread.sleep(220);
        }
        out.sort(Comparator.comparing(InvestorFlow::date));
        return out;
    }

    private Page apiPost(String apiId, String path, JSONObject body, String cont, String next) throws Exception {
        return rawPost(host()+path, body, "Bearer "+getToken(), apiId, cont==null?null:new String[]{cont,next});
    }

    private Page rawPost(String url, JSONObject body, String authorization, String apiId, String[] continuation) throws IOException, JSONException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(12000); c.setReadTimeout(25000); c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json;charset=UTF-8");
        if(authorization!=null)c.setRequestProperty("authorization",authorization);
        if(apiId!=null)c.setRequestProperty("api-id",apiId);
        if(continuation!=null){ if(continuation[0]!=null)c.setRequestProperty("cont-yn",continuation[0]); if(continuation[1]!=null)c.setRequestProperty("next-key",continuation[1]); }
        try(OutputStream os=c.getOutputStream()){ os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int status=c.getResponseCode(); InputStream is=status>=200&&status<300?c.getInputStream():c.getErrorStream();
        String text="";
        if(is!=null) try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){ StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null)sb.append(line); text=sb.toString(); }
        JSONObject json=new JSONObject(text.isBlank()?"{}":text);
        if(status<200||status>=300){
            String msg=json.optString("return_msg",text);
            String code=String.valueOf(json.optInt("return_code",0));
            if(msg.contains("8030") || "8030".equals(code)) msg="오류 8030: App Key의 실전/모의 구분과 접속 서버가 다릅니다.";
            throw new IOException("HTTP "+status+": "+msg);
        }
        if(json.optInt("return_code",0)!=0 && json.has("return_msg")) throw new IOException(json.optString("return_msg","키움 API 오류"));
        return new Page(json,c.getHeaderField("cont-yn"),c.getHeaderField("next-key"));
    }

    private static double absNum(String s){ return Math.abs(num(s)); }
    private static double num(String s){ if(s==null||s.isBlank())return 0; try{return Double.parseDouble(s.replace(",","").replace("+",""));}catch(Exception e){return 0;} }
    private static long longNum(String s){ return Math.round(num(s)); }
}
