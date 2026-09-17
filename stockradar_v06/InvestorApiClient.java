package com.stockradar.app.api;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 수급 TOP20/손바뀜 전용 경량 REST 클라이언트. 주문 기능은 없다. */
public final class InvestorApiClient {
    private final String appKey, secret; private final boolean mock;
    private String token; private long tokenIssuedAt;
    public record InvestorRank(String code,String name,long netBuy,int rank){}
    public record FlowSnapshot(long totalVolume5,long personal5,long foreign5,long institution5,int foreignPositiveDays,int institutionPositiveDays,double latestChangePct){
        public double foreignRatioPct(){return totalVolume5<=0?0:foreign5*100.0/totalVolume5;}
        public double institutionRatioPct(){return totalVolume5<=0?0:institution5*100.0/totalVolume5;}
        public double personalSellRatioPct(){return totalVolume5<=0?0:(-personal5)*100.0/totalVolume5;}
    }
    private record Page(JSONObject body,String contYn,String nextKey){}
    private record FlowDay(String date,long volume,long personal,long foreign,long institution,double changePct){}

    public InvestorApiClient(String appKey,String secret,boolean mock){this.appKey=appKey;this.secret=secret;this.mock=mock;}
    private String host(){return mock?"https://mockapi.kiwoom.com":"https://api.kiwoom.com";}
    private synchronized String getToken() throws Exception{
        if(token!=null&&System.currentTimeMillis()-tokenIssuedAt<23L*60*60*1000)return token;
        JSONObject b=new JSONObject();b.put("grant_type","client_credentials");b.put("appkey",appKey);b.put("secretkey",secret);
        JSONObject r=rawPost(host()+"/oauth2/token",b,null,null,null).body;
        if(r.optInt("return_code",0)!=0&&!r.has("token"))throw new IOException(r.optString("return_msg","토큰 발급 실패"));
        token=r.getString("token");tokenIssuedAt=System.currentTimeMillis();return token;
    }

    /** market 001=KOSPI, 101=KOSDAQ / investor FOREIGN or INSTITUTION */
    public List<InvestorRank> fetchInvestorNetBuyRank(String market,String investor,int max) throws Exception{
        boolean foreign="FOREIGN".equalsIgnoreCase(investor);List<InvestorRank> out=new ArrayList<>();
        try{
            JSONObject b=new JSONObject();b.put("trde_tp","1");b.put("mrkt_tp",market);b.put("orgn_tp",foreign?"9000":"9999");b.put("amt_qty_tp","1");
            Page p=apiPost("ka10065","/api/dostk/rkinfo",b);
            JSONArray arr=p.body.optJSONArray("opmr_invsr_trde_upper");
            if(arr!=null)for(int i=0;i<arr.length()&&out.size()<max;i++){
                JSONObject x=arr.optJSONObject(i);if(x==null)continue;String code=x.optString("stk_cd","");if(code.isBlank())continue;
                out.add(new InvestorRank(code,x.optString("stk_nm",code),Math.abs(longNum(x.optString("netslmt"))),out.size()+1));
            }
        }catch(Exception ignored){out.clear();}
        if(!out.isEmpty())return out;
        JSONObject b=new JSONObject();b.put("mrkt_tp",market);b.put("amt_qty_tp","1");b.put("qry_dt_tp","0");b.put("stex_tp","3");
        Page p=apiPost("ka90009","/api/dostk/rkinfo",b);JSONArray arr=p.body.optJSONArray("frgnr_orgn_trde_upper");
        if(arr!=null){String ck=foreign?"for_netprps_stk_cd":"orgn_netprps_stk_cd",nk=foreign?"for_netprps_stk_nm":"orgn_netprps_stk_nm",ak=foreign?"for_netprps_amt":"orgn_netprps_amt";
            for(int i=0;i<arr.length()&&out.size()<max;i++){JSONObject x=arr.optJSONObject(i);if(x==null)continue;String code=x.optString(ck,"");if(code.isBlank())continue;out.add(new InvestorRank(code,x.optString(nk,code),Math.abs(longNum(x.optString(ak))),out.size()+1));}}
        return out;
    }

    public FlowSnapshot fetchRecentFlowSnapshot(String code) throws Exception{
        String today=LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);JSONObject b=new JSONObject();
        b.put("dt",today);b.put("stk_cd",code);b.put("amt_qty_tp","2");b.put("trde_tp","0");b.put("unit_tp","1");
        Page p=apiPost("ka10059","/api/dostk/stkinfo",b);JSONArray arr=p.body.optJSONArray("stk_invsr_orgn");List<FlowDay> flows=new ArrayList<>();
        if(arr!=null)for(int i=0;i<arr.length();i++){JSONObject x=arr.optJSONObject(i);if(x==null)continue;flows.add(new FlowDay(x.optString("dt"),longNum(x.optString("acc_trde_qty")),longNum(x.optString("ind_invsr")),longNum(x.optString("frgnr_invsr")),longNum(x.optString("orgn")),num(x.optString("flu_rt"))));}
        flows.sort(Comparator.comparing(FlowDay::date));int from=Math.max(0,flows.size()-5);long vol=0,pe=0,fr=0,in=0;int fp=0,ip=0;double ch=0;
        for(int i=from;i<flows.size();i++){FlowDay f=flows.get(i);vol+=Math.max(0,f.volume());pe+=f.personal();fr+=f.foreign();in+=f.institution();if(f.foreign()>0)fp++;if(f.institution()>0)ip++;ch=f.changePct();}
        return new FlowSnapshot(vol,pe,fr,in,fp,ip,ch);
    }

    private Page apiPost(String id,String path,JSONObject body)throws Exception{return rawPost(host()+path,body,"Bearer "+getToken(),id,null);}
    private Page rawPost(String url,JSONObject body,String authorization,String apiId,String[] cont)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(25000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json;charset=UTF-8");
        if(authorization!=null)c.setRequestProperty("authorization",authorization);if(apiId!=null)c.setRequestProperty("api-id",apiId);if(cont!=null){if(cont[0]!=null)c.setRequestProperty("cont-yn",cont[0]);if(cont[1]!=null)c.setRequestProperty("next-key",cont[1]);}
        try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}int st=c.getResponseCode();InputStream is=st>=200&&st<300?c.getInputStream():c.getErrorStream();String text="";
        if(is!=null)try(BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8))){StringBuilder sb=new StringBuilder();String line;while((line=br.readLine())!=null)sb.append(line);text=sb.toString();}
        JSONObject j=new JSONObject(text.isBlank()?"{}":text);if(st<200||st>=300)throw new IOException("HTTP "+st+": "+j.optString("return_msg",text));if(j.optInt("return_code",0)!=0&&j.has("return_msg"))throw new IOException(j.optString("return_msg","키움 API 오류"));return new Page(j,c.getHeaderField("cont-yn"),c.getHeaderField("next-key"));
    }
    private static double num(String s){if(s==null||s.isBlank())return 0;try{return Double.parseDouble(s.replace(",","").replace("+",""));}catch(Exception e){return 0;}}
    private static long longNum(String s){return Math.round(num(s));}
}
