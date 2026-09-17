package com.stockradar.app.data;

import com.stockradar.app.api.InvestorApiClient;
import java.util.*;

public final class InvestorFlowRepository {
    private final InvestorApiClient client;
    public InvestorFlowRepository(InvestorApiClient client){this.client=client;}

    public record FlowRankRow(
            String code, String name, int score, double latestChangePct,
            long rankNetBuy, long personal5, long foreign5, long institution5,
            int foreignPositiveDays, int institutionPositiveDays, String note
    ) {}

    public List<FlowRankRow> analyzeTop(String market, String investor, int topN) throws Exception {
        boolean foreign="FOREIGN".equalsIgnoreCase(investor);
        List<InvestorApiClient.InvestorRank> ranks=client.fetchInvestorNetBuyRank(market,investor,36);
        List<FlowRankRow> out=new ArrayList<>();
        int checked=0;
        for(InvestorApiClient.InvestorRank r:ranks){
            if(checked++>=36)break;
            try{
                InvestorApiClient.FlowSnapshot f=client.fetchRecentFlowSnapshot(r.code());
                if(f.latestChangePct()<=0) { Thread.sleep(140); continue; }
                long chosen=foreign?f.foreign5():f.institution5();
                int days=foreign?f.foreignPositiveDays():f.institutionPositiveDays();
                long other=foreign?f.institution5():f.foreign5();
                double ratio=foreign?f.foreignRatioPct():f.institutionRatioPct();
                int score=scoreTop(r.rank(),f.latestChangePct(),ratio,days,other,f.personal5());
                String note=(foreign?"외국인":"기관")+" 최근5일 "+fmtSigned(chosen)+"주 · "+days+"/5일 순매수"
                        +(f.personal5()<0?" · 개인 순매도 물량 흡수":"")
                        +(other>0?" · 외인/기관 동반매수":"");
                out.add(new FlowRankRow(r.code(),r.name(),score,f.latestChangePct(),r.netBuy(),f.personal5(),f.foreign5(),f.institution5(),f.foreignPositiveDays(),f.institutionPositiveDays(),note));
            }catch(Exception ignored){}
            Thread.sleep(140);
        }
        out.sort(Comparator.comparingInt(FlowRankRow::score).reversed().thenComparing(Comparator.comparingDouble(FlowRankRow::latestChangePct).reversed()));
        if(out.size()>topN)return new ArrayList<>(out.subList(0,topN));
        return out;
    }

    public List<FlowRankRow> analyzeHandoff(String market, int topN) throws Exception {
        List<InvestorApiClient.InvestorRank> fr=client.fetchInvestorNetBuyRank(market,"FOREIGN",40);
        Thread.sleep(220);
        List<InvestorApiClient.InvestorRank> ir=client.fetchInvestorNetBuyRank(market,"INSTITUTION",40);
        Map<String,Seed> seeds=new LinkedHashMap<>();
        for(InvestorApiClient.InvestorRank r:fr){seeds.put(r.code(),new Seed(r.code(),r.name(),r.rank(),99,r.netBuy(),0));}
        for(InvestorApiClient.InvestorRank r:ir){
            Seed s=seeds.get(r.code());
            if(s==null)seeds.put(r.code(),new Seed(r.code(),r.name(),99,r.rank(),0,r.netBuy()));
            else seeds.put(r.code(),new Seed(s.code(),s.name(),s.foreignRank(),r.rank(),s.foreignAmt(),r.netBuy()));
        }
        List<Seed> ordered=new ArrayList<>(seeds.values());
        ordered.sort(Comparator.comparingInt(Seed::priority).reversed());
        List<FlowRankRow> out=new ArrayList<>();
        int checked=0;
        for(Seed s:ordered){
            if(checked++>=34)break;
            try{
                InvestorApiClient.FlowSnapshot f=client.fetchRecentFlowSnapshot(s.code());
                if(!(f.personal5()<0 && f.foreign5()>0 && f.institution5()>0)){Thread.sleep(140);continue;}
                int score=scoreHandoff(s,f);
                String type=f.latestChangePct()>=0?"상승 동반 손바뀜":(f.latestChangePct()>-2?"눌림 흡수형 손바뀜":"하락 중 수급교체");
                String note=type+" · 개인 "+fmtSigned(f.personal5())+"주 → 외국인 "+fmtSigned(f.foreign5())+"주 / 기관 "+fmtSigned(f.institution5())+"주";
                out.add(new FlowRankRow(s.code(),s.name(),score,f.latestChangePct(),s.foreignAmt()+s.institutionAmt(),f.personal5(),f.foreign5(),f.institution5(),f.foreignPositiveDays(),f.institutionPositiveDays(),note));
            }catch(Exception ignored){}
            Thread.sleep(140);
        }
        out.sort(Comparator.comparingInt(FlowRankRow::score).reversed());
        if(out.size()>topN)return new ArrayList<>(out.subList(0,topN));
        return out;
    }

    private static int scoreTop(int rank,double ch,double ratio,int days,long other,long personal){
        double rankScore=Math.max(0,30-(rank-1)*0.85);
        double changeScore=Math.min(15,Math.max(0,ch*3.0));
        double ratioScore=Math.min(25,Math.max(0,ratio*5.0));
        double daysScore=15.0*Math.min(5,days)/5.0;
        double otherScore=other>0?8:0;
        double handoff=personal<0?7:0;
        return clamp((int)Math.round(rankScore+changeScore+ratioScore+daysScore+otherScore+handoff));
    }

    private static int scoreHandoff(Seed s,InvestorApiClient.FlowSnapshot f){
        double p=Math.min(25,Math.max(0,f.personalSellRatioPct()*5.0));
        double fr=Math.min(20,Math.max(0,f.foreignRatioPct()*4.0));
        double in=Math.min(25,Math.max(0,f.institutionRatioPct()*5.0));
        double days=15.0*(Math.min(5,f.foreignPositiveDays())+Math.min(5,f.institutionPositiveDays()))/10.0;
        double price=f.latestChangePct()>=0?Math.min(10,5+f.latestChangePct()):f.latestChangePct()>-2?4:0;
        double overlap=(s.foreignRank()<99&&s.institutionRank()<99)?5:2;
        return clamp((int)Math.round(p+fr+in+days+price+overlap));
    }

    private static int clamp(int x){return Math.max(0,Math.min(100,x));}
    private static String fmtSigned(long v){return String.format(Locale.KOREA,"%+,d",v);}
    private record Seed(String code,String name,int foreignRank,int institutionRank,long foreignAmt,long institutionAmt){
        int priority(){int f=foreignRank>=99?0:41-foreignRank;int i=institutionRank>=99?0:41-institutionRank;return f+i+(foreignRank<99&&institutionRank<99?30:0);}
    }
}
