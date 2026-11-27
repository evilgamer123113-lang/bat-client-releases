package com.batclient.performance;

import java.util.ArrayList;
import java.util.List;

/** Bounded nearest-first selection without sorting/materializing the entire ready queue. */
public final class NearestReadyBatch<T> {
    private final Object[] values;
    private final long[] distances;
    private int size;
    public NearestReadyBatch(int limit){
        if(limit<1||limit>16)throw new IllegalArgumentException("batch limit");
        values=new Object[limit];distances=new long[limit];
    }
    public void offer(T value,long distance){
        if(value==null)return;
        if(size==values.length&&distance>=distances[size-1])return;
        int index=Math.min(size,values.length-1);
        while(index>0&&distance<distances[index-1]){
            values[index]=values[index-1];distances[index]=distances[index-1];index--;
        }
        values[index]=value;distances[index]=distance;
        if(size<values.length)size++;
    }
    @SuppressWarnings("unchecked")
    public List<T> result(){
        List<T> result=new ArrayList<T>(size);
        for(int i=0;i<size;i++)result.add((T)values[i]);
        return result;
    }
}
