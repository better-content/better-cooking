package com.bettercontent.bettercooking.drinks.placed;
import java.util.*;

/** Immutable heterogeneous FIFO. Empty Optional is explicitly UNKNOWN legacy, never a fabricated original. */
public final class CupQueue<T> {
    public static final int MAX=4;
    private final List<Optional<T>> entries;
    public CupQueue(List<Optional<T>> entries) {
        if(entries.size()>MAX)throw new IllegalStateException("Native drink FIFO exceeds four cups");
        this.entries=List.copyOf(entries);
    }
    public static <T> CupQueue<T> empty(){return new CupQueue<>(List.of());}
    public static <T> CupQueue<T> legacy(int count){
        if(count<0||count>MAX)throw new IllegalStateException("Invalid legacy native cup count");
        return new CupQueue<>(Collections.nCopies(count,Optional.empty()));
    }
    public int size(){return entries.size();}
    public List<Optional<T>> entries(){return entries;}
    public Optional<T> head(){if(entries.isEmpty())throw new IllegalStateException("Empty native drink FIFO");return entries.get(0);}
    public CupQueue<T> append(T original){
        Objects.requireNonNull(original);var next=new ArrayList<>(entries);next.add(Optional.of(original));return new CupQueue<>(next);
    }
    public CupQueue<T> tail(){head();return new CupQueue<>(entries.subList(1,entries.size()));}
    public CupQueue<T> requireCount(int count){
        if(count!=size())throw new IllegalStateException("Native drink count/paid FIFO mismatch: state="+count+", paid="+size());return this;
    }
}
