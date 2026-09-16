package com.stockradar.app.core;

import java.util.*;

/** 삼화콘덴서·DI·로보티즈의 상승 전 공통 특징을 일반화한 규칙 기반 스코어러. */
public final class SignalEngine {
    public ScanResult score(StockData data) {
        List<MarketBar> d=data.daily(), w=data.weekly();
        List<InvestorFlow> f=data.flows();
        if(d.size()<120 || w.size()<20) throw new IllegalArgumentException("일봉 120개, 주봉 20개 이상이 필요합니다.");

        int score=0; List<String> good=new ArrayList<>(), warn=new ArrayList<>();
        double close=d.get(d.size()-1).close();
        double ma5=IndicatorMath.sma(d,5,d.size()), ma10=IndicatorMath.sma(d,10,d.size()), ma20=IndicatorMath.sma(d,20,d.size());
        double ma60=IndicatorMath.sma(d,60,d.size()), ma120=IndicatorMath.sma(d,120,d.size());
        double wma5=IndicatorMath.sma(w,5,w.size()), wma5Prev=IndicatorMath.sma(w,5,w.size()-1);
        double cmo=IndicatorMath.cmo(w,14,w.size()), cmoPrev=IndicatorMath.cmo(w,14,w.size()-1);
        IndicatorMath.Dmi dmi=IndicatorMath.dmi(w,14);
        IndicatorMath.Dmi dmiPrev=IndicatorMath.dmi(w.subList(0,w.size()-1),14);

        double min12=w.subList(Math.max(0,w.size()-12),w.size()).stream().mapToDouble(MarketBar::low).min().orElse(close);
        double max12=w.subList(Math.max(0,w.size()-12),w.size()).stream().mapToDouble(MarketBar::high).max().orElse(close);
        boolean baseZone=(max12-min12)/Math.max(1,min12) < 0.65;
        boolean weeklyTurn=wma5>wma5Prev && close>wma5 && close>min12*1.06;
        if(weeklyTurn){score+=12;good.add("세 종목 공통패턴: 주봉 5주선 상승전환");}
        if(baseZone && close<max12*0.92){score+=5;good.add("고점 추격보다 바닥·중단 회복 구간");}

        boolean cmoEarly=!Double.isNaN(cmo) && cmo>-35 && cmo<20 && cmo>cmoPrev+4;
        boolean cmoCross=!Double.isNaN(cmo) && cmo>=0 && cmoPrev<0;
        if(cmoEarly){score+=7;good.add(String.format(Locale.KOREA,"주봉 CMO 음수권 회복 %.1f",cmo));}
        if(cmoCross){score+=4;good.add("CMO 0선 돌파");}

        boolean dmiImproving=dmi.plusDI()>dmiPrev.plusDI() && dmi.minusDI()<dmiPrev.minusDI();
        boolean dmiBull=dmi.plusDI()>dmi.minusDI();
        if(dmiImproving){score+=7;good.add("+DI 상승 / -DI 하락");}
        if(dmiBull){score+=5;good.add("+DI가 -DI 우위");}

        boolean aligned=ma5>ma10 && ma10>ma20;
        boolean turning=ma5>ma10 && close>ma20;
        if(aligned){score+=8;good.add("일봉 5·10·20일선 정배열");}
        else if(turning){score+=5;good.add("단기 이평선 상승 전환");}
        double longMax=Math.max(ma60,ma120), longMin=Math.min(ma60,ma120);
        boolean compressed=Math.abs(ma60-ma120)/Math.max(1,close)<0.09 && close>=longMin*0.95;
        boolean longBreak=close>longMax && d.get(d.size()-2).close()<=longMax*1.03;
        if(compressed){score+=7;good.add("60·120일선 접근·압축");}
        if(longBreak){score+=4;good.add("장기 이평선 돌파 시작");}

        double avg20=0; for(int i=d.size()-21;i<d.size()-1;i++)avg20+=d.get(i).volume(); avg20/=20.0;
        double volRatio=avg20<=0?0:d.get(d.size()-1).volume()/avg20;
        if(volRatio>=1.15){score+=3;good.add(String.format(Locale.KOREA,"거래량 증가 %.1f배",volRatio));}
        if(volRatio>=1.5){score+=3;}

        List<InvestorFlow> r5=f.size()>5?f.subList(f.size()-5,f.size()):f;
        List<InvestorFlow> r10=f.size()>10?f.subList(f.size()-10,f.size()):f;
        int instDays=(int)r5.stream().filter(x->x.institution()>0).count();
        if(instDays>=3){score+=8;good.add("최근 5일 기관 3일 이상 순매수");}
        boolean downInst=r5.stream().anyMatch(x->x.changePct()<0 && x.institution()>0);
        if(downInst){score+=8;good.add("하락일에도 기관이 물량 흡수");}

        int subtypeDays=0;
        for(InvestorFlow x:r5){int cnt=0;if(x.trust()>0)cnt++;if(x.privateFund()>0)cnt++;if(x.pension()>0)cnt++;if(cnt>=2)subtypeDays++;}
        if(subtypeDays>=2){score+=6;good.add("투신·사모·연기금 중 복수 주체 동반");}

        long inst10=r10.stream().mapToLong(InvestorFlow::institution).sum();
        long foreign10=r10.stream().mapToLong(InvestorFlow::foreign).sum();
        long personal10=r10.stream().mapToLong(InvestorFlow::personal).sum();
        long vol10=r10.stream().mapToLong(InvestorFlow::volume).sum();
        double cumulativeBig=vol10<=0?0:(inst10+foreign10)*100.0/vol10;
        if(inst10>0 && cumulativeBig>=2){score+=5;good.add(String.format(Locale.KOREA,"10일 큰손 누적순매수율 %.1f%%",cumulativeBig));}
        if(personal10<0 && inst10>0){score+=4;good.add("개인 매도 물량을 기관이 흡수");}

        InvestorFlow latest=f.isEmpty()?null:f.get(f.size()-1);
        double bigRatio=latest==null?0:latest.bigMoneyRatioPct();
        boolean firstPullback=isFirstPullback(d,f,ma20);
        if(firstPullback){score+=8;good.add("첫 눌림에서도 기관 매수 지속");}

        double ret5=IndicatorMath.returnPct(d,5), dist20=ma20==0?0:(close/ma20-1)*100;
        if(ret5>30){score-=12;warn.add(String.format(Locale.KOREA,"최근 5일 +%.1f%%: 이미 급등한 구간",ret5));}
        else if(ret5>20){score-=6;warn.add("단기 상승폭이 커 추격 주의");}
        if(dist20>25){score-=8;warn.add("20일선 이격 과다");}
        if(latest!=null && latest.personal()>0 && latest.foreign()<0 && latest.institution()<0){score-=10;warn.add("개인이 받고 외국인·기관 동시 매도");}
        if(inst10<0){score-=7;warn.add("최근 10일 기관 누적 순매도");}

        score=Math.max(0,Math.min(100,score));
        String grade=score>=85?"S":score>=75?"A":score>=65?"B":score>=52?"C":"WATCH";
        String stage;
        if(firstPullback)stage="FIRST PULLBACK";
        else if(volRatio>=1.5 && aligned && close>longMax)stage="BREAKOUT";
        else if(weeklyTurn && (cmoEarly||dmiImproving))stage="EARLY";
        else stage="PRE-RADAR";

        return new ScanResult(data.code(),data.name(),score,grade,stage,close,cmo,dmi.plusDI(),dmi.minusDI(),volRatio,bigRatio,good,warn);
    }

    private boolean isFirstPullback(List<MarketBar>d,List<InvestorFlow>flows,double ma20){
        if(d.size()<12||flows.isEmpty())return false;
        boolean breakout=false;
        for(int i=d.size()-10;i<d.size()-1;i++){
            double prev=d.get(i-1).close(); if(prev==0)continue;
            double pct=(d.get(i).close()/prev-1)*100;
            if(pct>=6 && d.get(i).volume()>d.get(i-1).volume()*1.25) breakout=true;
        }
        MarketBar last=d.get(d.size()-1); InvestorFlow flow=flows.get(flows.size()-1);
        double pct=(last.close()/d.get(d.size()-2).close()-1)*100;
        return breakout && pct>=-7 && pct<=2.5 && last.close()>ma20 && flow.institution()>0;
    }
}
