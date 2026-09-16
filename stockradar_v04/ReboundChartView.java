package com.stockradar.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import com.stockradar.app.core.*;
import java.util.*;

public final class ReboundChartView extends View {
    private StockData data;
    private int visibleDays = 60;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public ReboundChartView(Context c){
        super(c);
        setBackgroundColor(Color.WHITE);
        text.setColor(Color.rgb(65,72,82));
        text.setTextSize(dp(11));
    }

    public void setData(StockData d){ data=d; invalidate(); }
    public void setVisibleDays(int n){ visibleDays=Math.max(30,Math.min(120,n)); invalidate(); }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(data==null || data.daily()==null || data.daily().size()<20){
            text.setTextSize(dp(15)); c.drawText("차트 데이터가 없습니다.",dp(18),dp(40),text); return;
        }
        List<MarketBar> bars=data.daily();
        int end=bars.size();
        int start=Math.max(0,end-visibleDays);
        float left=dp(10), right=getWidth()-dp(8), top=dp(18);
        float w=Math.max(1,right-left);
        float h=getHeight()-dp(22);
        float priceH=h*0.47f, volH=h*0.12f, cmoH=h*0.13f, dmiH=h*0.15f, flowH=h*0.13f;
        float priceTop=top;
        float volTop=priceTop+priceH;
        float cmoTop=volTop+volH;
        float dmiTop=cmoTop+cmoH;
        float flowTop=dmiTop+dmiH;

        drawSectionBg(c,left,priceTop,right,priceTop+priceH);
        drawSectionBg(c,left,volTop,right,volTop+volH);
        drawSectionBg(c,left,cmoTop,right,cmoTop+cmoH);
        drawSectionBg(c,left,dmiTop,right,dmiTop+dmiH);
        drawSectionBg(c,left,flowTop,right,flowTop+flowH);

        double min=Double.POSITIVE_INFINITY,max=0,maxVol=1;
        for(int i=start;i<end;i++){ MarketBar b=bars.get(i); min=Math.min(min,b.low()); max=Math.max(max,b.high()); maxVol=Math.max(maxVol,b.volume()); }
        int[] periods={5,20,60,120};
        for(int per:periods) for(int i=start;i<end;i++){ double ma=IndicatorMath.sma(bars,per,i+1); if(!Double.isNaN(ma)){min=Math.min(min,ma);max=Math.max(max,ma);} }
        double pad=(max-min)*0.06; if(pad<=0)pad=Math.max(1,max*0.02); min-=pad;max+=pad;
        float step=w/Math.max(1,end-start), body=Math.max(dp(2),step*0.56f);

        p.setStrokeWidth(dp(0.7f)); p.setColor(Color.rgb(232,235,239));
        for(int k=0;k<=4;k++){float y=priceTop+priceH*k/4f;c.drawLine(left,y,right,y,p);double v=max-(max-min)*k/4.0;drawRightLabel(c,String.format(Locale.KOREA,"%,.0f",v),right,y+dp(4));}

        for(int i=start;i<end;i++){
            MarketBar b=bars.get(i); float x=left+(i-start+0.5f)*step;
            float yo=map(b.open(),min,max,priceTop+priceH,priceTop);
            float yc=map(b.close(),min,max,priceTop+priceH,priceTop);
            float yh=map(b.high(),min,max,priceTop+priceH,priceTop);
            float yl=map(b.low(),min,max,priceTop+priceH,priceTop);
            boolean up=b.close()>=b.open(); p.setColor(up?Color.rgb(214,52,58):Color.rgb(42,100,210)); p.setStrokeWidth(dp(1));
            c.drawLine(x,yh,x,yl,p); r.set(x-body/2,Math.min(yo,yc),x+body/2,Math.max(yo,yc)+dp(1)); c.drawRect(r,p);
        }

        drawMa(c,bars,start,end,5,Color.rgb(231,145,34),left,step,min,max,priceTop,priceH);
        drawMa(c,bars,start,end,20,Color.rgb(154,63,186),left,step,min,max,priceTop,priceH);
        drawMa(c,bars,start,end,60,Color.rgb(39,139,94),left,step,min,max,priceTop,priceH);
        drawMa(c,bars,start,end,120,Color.rgb(90,90,90),left,step,min,max,priceTop,priceH);
        drawLegend(c,"PRICE  MA5  MA20  MA60  MA120",left+dp(6),priceTop+dp(15));

        drawLegend(c,"VOLUME",left+dp(6),volTop+dp(15));
        for(int i=start;i<end;i++){MarketBar b=bars.get(i);float x=left+(i-start+0.5f)*step;float bh=(float)(b.volume()/maxVol*(volH-dp(18)));p.setColor(b.close()>=b.open()?Color.argb(145,214,52,58):Color.argb(145,42,100,210));r.set(x-body/2,volTop+volH-bh,x+body/2,volTop+volH);c.drawRect(r,p);}

        drawLegend(c,"CMO(14) · 0선/±40",left+dp(6),cmoTop+dp(15));
        horizontal(c,left,right,map(0,-100,100,cmoTop+cmoH,cmoTop),Color.rgb(165,165,165));
        horizontal(c,left,right,map(40,-100,100,cmoTop+cmoH,cmoTop),Color.rgb(225,225,225));
        horizontal(c,left,right,map(-40,-100,100,cmoTop+cmoH,cmoTop),Color.rgb(225,225,225));
        drawIndicatorLine(c,bars,start,end,left,step,cmoTop,cmoH,-100,100,Color.rgb(189,91,34),(idx)->IndicatorMath.cmo(bars,14,idx+1));

        drawLegend(c,"DMI(14)  +DI / -DI / ADX",left+dp(6),dmiTop+dp(15));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.4f));
        drawDmiLine(c,bars,start,end,left,step,dmiTop,dmiH,0,60,0);
        drawDmiLine(c,bars,start,end,left,step,dmiTop,dmiH,0,60,1);
        drawDmiLine(c,bars,start,end,left,step,dmiTop,dmiH,0,60,2);
        p.setStyle(Paint.Style.FILL);

        drawLegend(c,"FLOW  기관 / 외국인 순매수",left+dp(6),flowTop+dp(15));
        Map<String,InvestorFlow> fm=new HashMap<>(); long flowMax=1;
        for(InvestorFlow f:data.flows()){fm.put(f.date(),f);flowMax=Math.max(flowMax,Math.max(Math.abs(f.institution()),Math.abs(f.foreign())));}
        float zero=flowTop+flowH*0.55f; horizontal(c,left,right,zero,Color.rgb(190,190,190));
        for(int i=start;i<end;i++){
            InvestorFlow f=fm.get(bars.get(i).date()); if(f==null)continue; float x=left+(i-start+0.5f)*step; float bw=Math.max(dp(1.2f),body*0.42f);
            float inst=(float)(f.institution()/(double)flowMax*(flowH*0.34f)); float fr=(float)(f.foreign()/(double)flowMax*(flowH*0.34f));
            p.setColor(Color.rgb(180,65,65)); r.set(x-bw-0.5f,Math.min(zero,zero-inst),x-0.5f,Math.max(zero,zero-inst));c.drawRect(r,p);
            p.setColor(Color.rgb(49,115,177)); r.set(x+0.5f,Math.min(zero,zero-fr),x+bw+0.5f,Math.max(zero,zero-fr));c.drawRect(r,p);
        }

        text.setTextSize(dp(9));text.setColor(Color.rgb(115,120,128));
        int every=Math.max(10,(end-start)/5);
        for(int i=start;i<end;i+=every){String d=bars.get(i).date();if(d.length()>=8)d=d.substring(4,6)+"/"+d.substring(6,8);float x=left+(i-start+0.5f)*step;c.drawText(d,x-dp(10),flowTop+flowH-dp(2),text);}
    }

    private interface ValueAt{double get(int idx);}
    private void drawIndicatorLine(Canvas c,List<MarketBar> bars,int start,int end,float left,float step,float top,float height,double lo,double hi,int color,ValueAt v){
        p.setColor(color);p.setStrokeWidth(dp(1.5f));p.setStyle(Paint.Style.STROKE);Path path=new Path();boolean have=false;
        for(int i=start;i<end;i++){double val=v.get(i);if(Double.isNaN(val))continue;val=Math.max(lo,Math.min(hi,val));float x=left+(i-start+0.5f)*step;float y=map(val,lo,hi,top+height,top);if(!have){path.moveTo(x,y);have=true;}else path.lineTo(x,y);}if(have)c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void drawDmiLine(Canvas c,List<MarketBar> bars,int start,int end,float left,float step,float top,float height,double lo,double hi,int which){
        int color=which==0?Color.rgb(198,60,60):which==1?Color.rgb(45,93,198):Color.rgb(70,70,70);p.setColor(color);p.setStrokeWidth(dp(1.25f));Path path=new Path();boolean have=false;
        for(int i=start;i<end;i++){if(i<16)continue;IndicatorMath.Dmi d=IndicatorMath.dmi(bars.subList(0,i+1),14);double val=which==0?d.plusDI():which==1?d.minusDI():d.adx();if(Double.isNaN(val))continue;val=Math.max(lo,Math.min(hi,val));float x=left+(i-start+0.5f)*step;float y=map(val,lo,hi,top+height,top);if(!have){path.moveTo(x,y);have=true;}else path.lineTo(x,y);}if(have)c.drawPath(path,p);
    }
    private void drawMa(Canvas c,List<MarketBar> bars,int start,int end,int period,int color,float left,float step,double min,double max,float top,float height){
        p.setColor(color);p.setStrokeWidth(dp(1.25f));p.setStyle(Paint.Style.STROKE);Path path=new Path();boolean have=false;
        for(int i=start;i<end;i++){double ma=IndicatorMath.sma(bars,period,i+1);if(Double.isNaN(ma))continue;float x=left+(i-start+0.5f)*step,y=map(ma,min,max,top+height,top);if(!have){path.moveTo(x,y);have=true;}else path.lineTo(x,y);}if(have)c.drawPath(path,p);p.setStyle(Paint.Style.FILL);
    }
    private void drawSectionBg(Canvas c,float l,float t,float rr,float b){p.setColor(Color.rgb(251,252,253));c.drawRect(l,t,rr,b,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(.7f));p.setColor(Color.rgb(226,229,233));c.drawRect(l,t,rr,b,p);p.setStyle(Paint.Style.FILL);}
    private void horizontal(Canvas c,float l,float rr,float y,int color){p.setColor(color);p.setStrokeWidth(dp(.7f));c.drawLine(l,y,rr,y,p);}
    private void drawLegend(Canvas c,String s,float x,float y){text.setTextSize(dp(10));text.setColor(Color.rgb(78,84,93));text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));c.drawText(s,x,y,text);text.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.NORMAL));}
    private void drawRightLabel(Canvas c,String s,float x,float y){text.setTextSize(dp(9));text.setColor(Color.rgb(115,120,128));float tw=text.measureText(s);c.drawText(s,x-tw-dp(3),y,text);}
    private static float map(double v,double lo,double hi,float yLo,float yHi){if(hi==lo)return (yLo+yHi)/2f;return (float)(yLo+(v-lo)/(hi-lo)*(yHi-yLo));}
}
