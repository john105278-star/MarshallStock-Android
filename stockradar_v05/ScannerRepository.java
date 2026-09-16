package com.stockradar.app.data;

import com.stockradar.app.api.KiwoomClient;
import com.stockradar.app.core.*;
import java.util.*;

public final class ScannerRepository {
    private final KiwoomClient client;
    private final SignalEngine engine = new SignalEngine();
    public ScannerRepository(KiwoomClient client){this.client=client;}

    public record ScanBundle(String marketSummary, List<ScanResult> results) {}

    public ScanResult analyzeQuery(String query) throws Exception {
        KiwoomClient.Candidate c=client.resolveStock(query);
        return analyzeOne(c.code(),c.name());
    }
    public ScanResult analyzeOne(String code, String name) throws Exception { return engine.score(client.fetchStock(code,name)); }

    /**
     * 삼화콘덴서·DI·로보티즈에서 공통으로 보였던
     * '기관 축적 + 주봉 회복 + 장기선 접근 + 과열 전 거래량 증가' 패턴을 후보 생성에 반영.
     */
    public ScanBundle scanMarshallTop(int topN) throws Exception {
        String market;
        try{ market=client.fetchMarketSummary(); }catch(Exception e){ market="오늘 시황: 지수 데이터 확인 실패 (종목 패턴 스캔은 계속 진행)"; }

        Map<String,Seed> seeds=new HashMap<>();
        addSeeds(seeds,client.fetchInstitutionBuyCandidates(18),4,"기관순매수상위");
        Thread.sleep(250);
        addSeeds(seeds,client.fetchForeignBuyCandidates(14),2,"외국인순매수상위");
        Thread.sleep(250);
        addSeeds(seeds,client.fetchVolumeSurgeCandidates(18),3,"거래량증가");
        Thread.sleep(250);
        addSeeds(seeds,client.fetchTradingValueCandidates(14),1,"거래대금상위");

        List<Seed> ordered=new ArrayList<>(seeds.values());
        ordered.sort(Comparator.comparingInt(Seed::seedScore).reversed());
        int deep=Math.min(18,ordered.size());
        List<ScanResult> results=new ArrayList<>();
        for(int i=0;i<deep;i++){
            Seed s=ordered.get(i);
            try{ results.add(analyzeOne(s.code(),s.name())); }catch(Exception ignored){}
            Thread.sleep(280);
        }
        results.sort(Comparator.comparingInt(ScanResult::score).reversed());
        if(results.size()>topN) results=new ArrayList<>(results.subList(0,topN));
        return new ScanBundle(market,results);
    }

    public List<ScanResult> scanMarket(int candidateCount) throws Exception { return scanMarshallTop(candidateCount).results(); }

    private static void addSeeds(Map<String,Seed> map,List<KiwoomClient.Candidate> list,int weight,String source){
        int rank=0;
        for(KiwoomClient.Candidate c:list){
            int bonus=Math.max(0,3-rank/4);
            Seed old=map.get(c.code());
            if(old==null) map.put(c.code(),new Seed(c.code(),c.name(),weight+bonus,new LinkedHashSet<>(List.of(source))));
            else { old.sources().add(source); map.put(c.code(),new Seed(old.code(),old.name(),old.seedScore()+weight+bonus,old.sources())); }
            rank++;
        }
    }
    private record Seed(String code,String name,int seedScore,LinkedHashSet<String> sources){}
}
