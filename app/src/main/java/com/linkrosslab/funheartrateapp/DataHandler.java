package com.linkrosslab.funheartrateapp;

import com.androidplot.xy.SimpleXYSeries;


public class DataHandler {
	private static DataHandler dd = new DataHandler();

	SimpleXYSeries series1;
	
	int pos=0;
	int val=0;
	int min=0;
	int max=0;
	
	//for the average
	int data=0;
	int total=0;

	private DataHandler(){
		
	}
	
	public static DataHandler getInstance(){
		return dd;
	}

	public void acqui(int i){
		if (i==254){
			pos=0;
		}
		else if (pos==5){
			cleanInput(i);
		}
		pos++;
	}
	
	public void cleanInput(int i){
		val=i;
		if(val!=0){
			data+=val;//Average maths
			total++;//Average maths
		}
		if(val<min||min==0)
			min=val;
		else if(val>max)
			max=val;
	}

	public void computeStats(int i){
		val=i;
		if(val!=0){
			data+=val; // for average
			total++; // for average
		}
		if(val<min||min==0)
			min=val;
		else if(val>max)
			max=val;
	}



    public int getLastIntValue(){

        return val;
    }
	
	public String getMin(){
		return "Min " + min + " BPM";
	}
	
	public String getMax(){

		return "Max " + max + " BPM";
	}
	
	public String getAvg(){
		if(total==0)
            return "Avg " + 0 + " BPM";
		return "Avg " + data/total + " BPM";
	}



	public SimpleXYSeries getSeries1() {
		return series1;
	}

	public void setSeries1(SimpleXYSeries series1) {
		this.series1 = series1;
	}
	

	
}
