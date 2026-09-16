package com.stockradar.app.core;

import java.util.*;

/**
 * 장기 상승 신호 점수(0~100).
 * 실제 상승확률이 아니라 가격추세·주봉모멘텀·DMI·기관/외국인 수급을 합성한 규칙 기반 점수다.
 */
public final class LongTermScoreEngine {
    public record Result(int score, String label, List<String> positives, List<String> risks) {}

    public Result score(StockData data){
        List<MarketBar> d=data.daily(), w=data.weekly();
        List<InvestorFlow> f=data.flows();
        if(d.size()<125 || w.size()<20) throw new IllegalArgumentException("장기 점수 계산에 필요한 차트 데이터가 부족합니다.");

        int s=0;
        List<String> good=new ArrayList<>(), risk=new ArrayList<>();
        double close=d.get(d.size()-1).close();
        double ma20=IndicatorMath.sma(d,20,d.size());
        double ma60=IndicatorMath.sma(d,60,d.size());
        double ma60p=IndicatorMath.sma(d,60,d.size()-5);
        double ma120=IndicatorMath.sma(d,120,d.size());
        double ma120p=IndicatorMath.sma(d,120,d.size()-5);

        // 장기 일봉 추세 30
        if(close>ma60){s+=8;good.add("60일선 위");}
        if(close>ma120){s+=7;good.add("120일선 위");}
        if(ma60>ma60p){s+=8;good.add("60일선 상승");}
        if(ma120>ma120p){s+=7;good.add("120일선 상승");}

        // 주봉 구조 25
        double wc=w.get(w.size()-1).close();
        double w5=IndicatorMath.sma(w,5,w.size()), w5p=IndicatorMath.sma(w,5,w.size()-1);
        double w10=IndicatorMath.sma(w,10,w.size()), w10p=IndicatorMath.sma(w,10,w.size()-1);
        double cmo=IndicatorMath.cmo(w,14,w.size()), cmoP=IndicatorMath.cmo(w,14,w.size()-1);
        if(wc>w5){s+=6;good.add("주봉 5주선 위");}
        if(w5>w5p){s+=6;good.add("5주선 상승");}
        if(w10>w10p){s+=5;good.add("10주선 상승");}
        if(!Double.isNaN(cmo) && cmo>0){s+=5;good.add("주봉 CMO 양수");}
        else if(!Double.isNaN(cmo) && cmo>cmoP+4){s+=3;good.add("주봉 CMO 회복");}
        if(w5>w10){s+=3;}

        // DMI 15
        IndicatorMath.Dmi dmi=IndicatorMath.dmi(w,14);
        IndicatorMath.Dmi prev=IndicatorMath.dmi(w.subList(0,w.size()-1),14);
        if(dmi.plusDI()>dmi.minusDI()){s+=7;good.add("+DI 우위");}
        if(dmi.plusDI()>prev.plusDI() && dmi.minusDI()<prev.minusDI()){s+=4;good.add("DMI 개선");}
        if(dmi.adx()>=20){s+=4;good.add("ADX 추세강도 확보");}

        // 수급 25
        List<InvestorFlow> r10=f.size()>10?f.subList(f.size()-10,f.size()):f;
        long inst=r10.stream().mapToLong(InvestorFlow::institution).sum();
        long foreign=r10.stream().mapToLong(InvestorFlow::foreign).sum();
        long personal=r10.stream().mapToLong(InvestorFlow::personal).sum();
        int instDays=(int)r10.stream().filter(x->x.institution()>0).count();
        long subtype=r10.stream().mapToLong(x->x.trust()+x.privateFund()+x.pension()).sum();
        if(inst>0){s+=8;good.add("10일 기관 누적 순매수");} else {risk.add("10일 기관 누적 순매도");}
        if(instDays>=6){s+=5;good.add("기관 매수일 6일 이상");}
        if(inst+foreign>0){s+=5;good.add("외국인+기관 합산 순매수");}
        if(personal<0 && inst>0){s+=4;good.add("개인 매도 물량 기관 흡수");}
        if(subtype>0){s+=3;good.add("투신·사모·연기금 합산 플러스");}

        // 과열/리스크 보정 5 + 감점
        double dist20=ma20==0?0:(close/ma20-1)*100.0;
        double r20=IndicatorMath.returnPct(d,20);
        if(Math.abs(dist20)<=12){s+=5;good.add("20일선 이격 안정");}
        if(dist20>25){s-=10;risk.add("20일선 이격 과다");}
        if(r20>45){s-=8;risk.add("최근 20일 급등 과열");}
        if(close<ma120 && ma120<ma120p){s-=8;risk.add("120일선 아래·하락 추세");}

        s=Math.max(0,Math.min(100,s));
        String label=s>=80?"강한 장기 상승 신호":s>=65?"양호":s>=50?"중립 이상":s>=35?"관찰":"약함";
        return new Result(s,label,good,risk);
    }
}
